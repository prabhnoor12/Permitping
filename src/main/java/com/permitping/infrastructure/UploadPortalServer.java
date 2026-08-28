package com.permitping.infrastructure;

import com.permitping.application.UploadRequestService;
import com.permitping.domain.UploadRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small embedded intake endpoint for local/network deployments. Put it behind HTTPS before exposing it publicly. */
public final class UploadPortalServer implements AutoCloseable {
    private static final int MAX_REQUEST_BYTES = (int) UploadRequestService.MAX_UPLOAD_BYTES + 1024 * 1024;
    private static final Pattern FILENAME = Pattern.compile("filename=\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
    private final UploadRequestService requests;
    private final HttpServer server;
    private final ExecutorService executor;

    public UploadPortalServer(UploadRequestService requests, String bindAddress, int port) throws IOException {
        if (requests == null) throw new IllegalArgumentException("Upload request service is required");
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Upload portal port is invalid");
        this.requests = requests;
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getByName(bindAddress == null || bindAddress.isBlank() ? "127.0.0.1" : bindAddress), port), 20);
        server.createContext("/upload", this::handle);
        executor = Executors.newFixedThreadPool(4, task -> { Thread thread = new Thread(task, "permitping-upload-portal"); thread.setDaemon(true); return thread; });
        server.setExecutor(executor);
    }

    public void start() { server.start(); }
    public int port() { return server.getAddress().getPort(); }
    @Override public void close() { server.stop(0); executor.shutdownNow(); }

    private void handle(HttpExchange exchange) {
        try {
            String token = token(exchange.getRequestURI().getPath());
            UploadRequest request = requests.findByToken(token).orElse(null);
            if (request == null) { respond(exchange, 404, "This upload link is invalid, expired, or revoked."); return; }
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) { respond(exchange, 200, page(request)); return; }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { exchange.getResponseHeaders().set("Allow", "GET, POST"); respond(exchange, 405, "Method not allowed."); return; }
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data")) { respond(exchange, 400, "Upload must use a multipart form."); return; }
            long length = exchange.getRequestBody() == null ? 0 : parseLength(exchange.getRequestHeaders().getFirst("Content-Length"));
            if (length > MAX_REQUEST_BYTES) { respond(exchange, 413, "Upload is too large. The maximum is 10 MB."); return; }
            byte[] body = readLimited(exchange.getRequestBody(), MAX_REQUEST_BYTES);
            Multipart file = parseMultipart(body, contentType);
            if (file == null) { respond(exchange, 400, "Choose a file before submitting."); return; }
            Path temp = Files.createTempFile("permitping-upload-", ".tmp");
            try { Files.write(temp, file.bytes(), StandardOpenOption.TRUNCATE_EXISTING); requests.submit(token, file.filename(), file.contentType(), temp); }
            finally { Files.deleteIfExists(temp); }
            respond(exchange, 200, "<h1>Upload received</h1><p>Your document was submitted for review. You may close this page.</p>");
        } catch (IllegalArgumentException | IOException ex) {
            respondQuietly(exchange, 400, ex.getMessage() == null ? "The upload could not be processed." : ex.getMessage());
        } catch (RuntimeException ex) {
            respondQuietly(exchange, 500, "The upload service is temporarily unavailable.");
        } finally { exchange.close(); }
    }

    private Multipart parseMultipart(byte[] body, String contentType) {
        int boundaryStart = contentType.toLowerCase(Locale.ROOT).indexOf("boundary=");
        if (boundaryStart < 0) return null;
        String boundary = contentType.substring(boundaryStart + 9).trim(); if (boundary.startsWith("\"")) boundary = boundary.substring(1, boundary.length() - 1);
        String raw = new String(body, StandardCharsets.ISO_8859_1); String marker = "\r\n--" + boundary;
        int headersEnd = raw.indexOf("\r\n\r\n"); if (headersEnd < 0) return null;
        int end = raw.indexOf(marker, headersEnd + 4); if (end < 0) return null;
        String headers = raw.substring(0, headersEnd); Matcher filename = FILENAME.matcher(headers); if (!filename.find()) return null;
        byte[] bytes = Arrays.copyOfRange(body, headersEnd + 4, end);
        String partType = ""; for (String line : headers.split("\r\n")) if (line.toLowerCase(Locale.ROOT).startsWith("content-type:")) partType = line.substring(line.indexOf(':') + 1).trim();
        return new Multipart(filename.group(1), partType, bytes);
    }
    private String token(String path) { if (path == null || !path.startsWith("/upload/")) return ""; String token = path.substring("/upload/".length()); return token.contains("/") || token.length() > 100 ? "" : token; }
    private long parseLength(String value) { try { return value == null ? 0 : Long.parseLong(value); } catch (NumberFormatException ex) { return MAX_REQUEST_BYTES + 1L; } }
    private byte[] readLimited(InputStream input, int max) throws IOException { ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int read; int total = 0; while ((read = input.read(buffer)) != -1) { total += read; if (total > max) throw new IOException("Upload is too large. The maximum is 10 MB."); output.write(buffer, 0, read); } return output.toByteArray(); }
    private String page(UploadRequest request) { return "<h1>PermitPing document upload</h1><p>Upload a " + html(request.documentType()) + " for " + html(request.project()) + ". Your upload will be reviewed before it is accepted.</p><form method=\"post\" enctype=\"multipart/form-data\"><input type=\"file\" name=\"document\" accept=\".pdf,.png,.jpg,.jpeg,.doc,.docx\" required><button type=\"submit\">Submit document</button></form><p>Maximum file size: 10 MB.</p>"; }
    private String html(String value) { return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
    private void respond(HttpExchange exchange, int status, String body) throws IOException { byte[] bytes = ("<html><body>" + body + "</body></html>").getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8"); exchange.getResponseHeaders().set("Cache-Control", "no-store"); exchange.sendResponseHeaders(status, bytes.length); try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); } }
    private void respondQuietly(HttpExchange exchange, int status, String body) { try { respond(exchange, status, "<p>" + html(body) + "</p>"); } catch (IOException ignored) { } }
    private record Multipart(String filename, String contentType, byte[] bytes) { }
}
