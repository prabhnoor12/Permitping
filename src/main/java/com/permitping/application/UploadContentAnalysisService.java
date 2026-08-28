package com.permitping.application;

import com.permitping.domain.UploadAnalysis;
import com.permitping.domain.UploadSubmission;
import com.permitping.infrastructure.FileStorage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts conservative date hints for a reviewer; it never validates document truth. */
public final class UploadContentAnalysisService {
    private static final long MAX_UNCOMPRESSED_DOCX_BYTES = 20L * 1024 * 1024;
    private static final int MAX_DOCX_ENTRIES = 100;
    private static final Pattern ISO_DATE = Pattern.compile("\\b(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})\\b");
    private static final Pattern NUMERIC_DATE = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})\\b");
    private static final Pattern MONTH_DATE = Pattern.compile("\\b(\\d{1,2})\\s+([A-Za-z]{3,9})\\s+(\\d{4})\\b|\\b([A-Za-z]{3,9})\\s+(\\d{1,2}),?\\s+(\\d{4})\\b");
    private static final Pattern EXPIRY_HINT = Pattern.compile("(?i)\\b(expir(?:es|y|ation)|valid\\s+(?:through|until|to)|good\\s+through)\\b");
    private static final DateTimeFormatter[] MONTH_FORMATS = {
        DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MMMM d uuuu", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MMM d uuuu", Locale.ENGLISH)
    };

    private final FileStorage files;
    private final Clock clock;

    public UploadContentAnalysisService(FileStorage files) { this(files, Clock.systemDefaultZone()); }
    public UploadContentAnalysisService(FileStorage files, Clock clock) { this.files = files; this.clock = clock; }

    public UploadAnalysis analyze(UploadSubmission submission) {
        List<String> findings = new ArrayList<>();
        if (submission == null) return result(0, List.of(), null, findings, "Upload submission is missing.");
        Path path;
        try {
            path = Path.of(submission.filePath());
        } catch (InvalidPathException | NullPointerException ex) {
            return result(submission.id(), List.of(), null, findings, "The stored upload path is invalid.");
        }
        if (!files.isManaged(path.toString()) || !Files.isRegularFile(path)) {
            return result(submission.id(), List.of(), null, findings, "The upload is missing or outside managed storage.");
        }

        String extension = extension(submission.originalFilename());
        String text;
        try {
            text = switch (extension) {
                case "pdf" -> printableText(path);
                case "docx" -> docxText(path);
                default -> "";
            };
        } catch (IOException ex) {
            return result(submission.id(), List.of(), null, findings, "Text could not be read safely; reviewer input is required.");
        }
        if (text.isBlank()) {
            String format = extension.equals("pdf") || extension.equals("docx") ? extension.toUpperCase(Locale.ROOT) : extension.isBlank() ? "file" : "." + extension;
            return result(submission.id(), List.of(), null, findings, "No readable text was found in this " + format + "; reviewer input is required.");
        }

        List<Candidate> candidates = dates(text);
        List<LocalDate> dates = candidates.stream().map(Candidate::date).distinct().sorted().toList();
        if (dates.isEmpty()) return result(submission.id(), dates, null, findings, "No unambiguous calendar dates were found; reviewer input is required.");
        findings.add("Extracted dates: " + dates + ".");
        LocalDate suggested = candidates.stream().filter(candidate -> expiryContext(text, candidate.start(), candidate.end()))
            .map(Candidate::date).max(Comparator.naturalOrder()).orElse(null);
        if (suggested == null) findings.add("No date was explicitly associated with expiry or validity; reviewer must choose the expiration date.");
        else findings.add("Suggested expiration date: " + suggested + "; reviewer confirmation is required.");
        return new UploadAnalysis(submission.id(), dates, suggested, findings, LocalDateTime.now(clock));
    }

    private UploadAnalysis result(long id, List<LocalDate> dates, LocalDate suggested, List<String> findings, String finding) {
        findings.add(finding);
        return new UploadAnalysis(id, dates, suggested, findings, LocalDateTime.now(clock));
    }

    private String printableText(Path path) throws IOException {
        StringBuilder text = new StringBuilder();
        try (InputStream input = Files.newInputStream(path)) {
            int value;
            boolean separated = true;
            while ((value = input.read()) != -1) {
                if (value >= 0x20 && value <= 0x7E) {
                    text.append((char) value);
                    separated = false;
                } else if (!separated) {
                    text.append(' ');
                    separated = true;
                }
            }
        }
        return text.toString();
    }

    private String docxText(Path path) throws IOException {
        StringBuilder text = new StringBuilder();
        byte[] buffer = new byte[8192];
        long uncompressed = 0;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(path), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_DOCX_ENTRIES) throw new IOException("Too many archive entries");
                if (!entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(".xml")) {
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        uncompressed += read;
                        if (uncompressed > MAX_UNCOMPRESSED_DOCX_BYTES) throw new IOException("Archive is too large");
                        text.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                    }
                }
                zip.closeEntry();
            }
        }
        return text.toString().replaceAll("<[^>]+>", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">");
    }

    private List<Candidate> dates(String text) {
        List<Candidate> result = new ArrayList<>();
        Matcher iso = ISO_DATE.matcher(text);
        while (iso.find()) add(result, parseIso(iso), iso.start(), iso.end());
        Matcher numeric = NUMERIC_DATE.matcher(text);
        while (numeric.find()) add(result, parseNumeric(numeric), numeric.start(), numeric.end());
        Matcher month = MONTH_DATE.matcher(text);
        while (month.find()) add(result, parseMonth(month), month.start(), month.end());
        return result;
    }

    private void add(List<Candidate> result, LocalDate date, int start, int end) { if (date != null) result.add(new Candidate(date, start, end)); }
    private LocalDate parseIso(Matcher matcher) { try { return LocalDate.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3))); } catch (RuntimeException ex) { return null; } }
    private LocalDate parseNumeric(Matcher matcher) {
        int first = Integer.parseInt(matcher.group(1)), second = Integer.parseInt(matcher.group(2)), year = Integer.parseInt(matcher.group(3));
        if (first <= 12 && second <= 12) return null;
        try { return first > 12 ? LocalDate.of(year, second, first) : LocalDate.of(year, first, second); } catch (RuntimeException ex) { return null; }
    }
    private LocalDate parseMonth(Matcher matcher) {
        String value = matcher.group(1) != null ? matcher.group(1) + " " + matcher.group(2) + " " + matcher.group(3) : matcher.group(4) + " " + matcher.group(5) + " " + matcher.group(6);
        for (DateTimeFormatter format : MONTH_FORMATS) try { return LocalDate.parse(value, format); } catch (DateTimeParseException ignored) { }
        return null;
    }
    private boolean expiryContext(String text, int start, int end) { int from = Math.max(0, start - 80), to = Math.min(text.length(), end + 80); return EXPIRY_HINT.matcher(text.substring(from, to)).find(); }
    private String extension(String filename) { if (filename == null) return ""; int dot = filename.lastIndexOf('.'); return dot <= 0 || dot == filename.length() - 1 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT); }
    private record Candidate(LocalDate date, int start, int end) { }
}
