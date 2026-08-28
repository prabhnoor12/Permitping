package com.permitping.application;

import com.permitping.domain.UploadSubmission;
import com.permitping.domain.UploadVerification;
import com.permitping.domain.UploadVerificationStatus;
import com.permitping.infrastructure.FileStorage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Performs conservative byte-level checks on an upload before it becomes evidence. */
public final class UploadVerificationService {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg", "doc", "docx");
    private static final Map<String, byte[]> SIGNATURES = Map.of(
        "pdf", new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D},
        "png", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A},
        "jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
        "jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF},
        "doc", new byte[] {(byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1},
        "docx", new byte[] {0x50, 0x4B, 0x03, 0x04}
    );

    private final FileStorage files;
    private final Clock clock;

    public UploadVerificationService(FileStorage files) { this(files, Clock.systemDefaultZone()); }
    public UploadVerificationService(FileStorage files, Clock clock) { this.files = files; this.clock = clock; }

    public UploadVerification verify(UploadSubmission submission) {
        List<String> checks = new ArrayList<>();
        if (submission == null) return result(0, UploadVerificationStatus.REJECTED, checks, "", "Upload submission is missing.");

        Path path;
        try {
            path = Path.of(submission.filePath());
        } catch (InvalidPathException | NullPointerException ex) {
            return result(submission.id(), UploadVerificationStatus.REJECTED, checks, "", "The stored upload path is invalid.");
        }
        if (!files.isManaged(path.toString()) || !Files.isRegularFile(path)) {
            return result(submission.id(), UploadVerificationStatus.REJECTED, checks, "", "The upload is missing or is outside managed storage.");
        }

        long size;
        try {
            size = Files.size(path);
        } catch (IOException ex) {
            return result(submission.id(), UploadVerificationStatus.REJECTED, checks, "", "The upload size could not be read.");
        }
        if (size <= 0 || size > UploadRequestService.MAX_UPLOAD_BYTES) {
            return result(submission.id(), UploadVerificationStatus.REJECTED, checks, "", "The upload size is outside the permitted 1 byte to 10 MB range.");
        }
        if (size != submission.sizeBytes()) {
            return result(submission.id(), UploadVerificationStatus.REJECTED, checks, "", "The upload changed after submission; its recorded size does not match.");
        }
        checks.add("Managed file exists and recorded size matches.");

        String extension = extension(submission.originalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return result(submission.id(), UploadVerificationStatus.REJECTED, checks, "", "The submitted filename has an unsupported extension.");
        }
        checks.add("Allowed file extension: ." + extension + ".");

        byte[] prefix = new byte[8];
        long bytesRead = 0;
        String sha256;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                int prefixRead = 0;
                while (prefixRead < prefix.length) {
                    int read = input.read(prefix, prefixRead, prefix.length - prefixRead);
                    if (read < 0) break;
                    if (read == 0) continue;
                    digest.update(prefix, prefixRead, read);
                    prefixRead += read;
                    bytesRead += read;
                }
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (read == 0) continue;
                    digest.update(buffer, 0, read);
                    bytesRead += read;
                }
            }
            sha256 = HexFormat.of().formatHex(digest.digest());
        } catch (IOException ex) {
            return result(submission.id(), UploadVerificationStatus.REJECTED, checks, "", "The upload could not be read for verification.");
        } catch (NoSuchAlgorithmException ex) {
            return result(submission.id(), UploadVerificationStatus.REJECTED, checks, "", "SHA-256 verification is unavailable.");
        }
        if (bytesRead != size) {
            return result(submission.id(), UploadVerificationStatus.REJECTED, checks, sha256, "The upload changed while it was being verified.");
        }
        checks.add("SHA-256: " + sha256 + ".");

        boolean signatureMatches = startsWith(prefix, SIGNATURES.get(extension));
        if (!signatureMatches) {
            checks.add("File bytes do not match the claimed ." + extension + " format.");
            return result(submission.id(), UploadVerificationStatus.NEEDS_REVIEW, checks, sha256, "The file type claim needs manual review.");
        }
        checks.add("File signature matches the claimed ." + extension + " format.");
        if (!contentTypeMatches(extension, submission.contentType())) {
            checks.add("The supplied content type is inconsistent with the filename; confirm it manually.");
            return result(submission.id(), UploadVerificationStatus.NEEDS_REVIEW, checks, sha256, "The content type claim needs manual review.");
        }
        checks.add("The supplied content type is consistent with the filename.");
        return new UploadVerification(submission.id(), UploadVerificationStatus.VERIFIED, checks, sha256, LocalDateTime.now(clock));
    }

    private UploadVerification result(long id, UploadVerificationStatus status, List<String> checks, String sha256, String finding) {
        checks.add(finding);
        return new UploadVerification(id, status, checks, sha256, LocalDateTime.now(clock));
    }

    private String extension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot <= 0 || dot == filename.length() - 1 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean startsWith(byte[] actual, byte[] expected) {
        if (expected == null || actual.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) if (actual[i] != expected[i]) return false;
        return true;
    }

    private boolean contentTypeMatches(String extension, String contentType) {
        if (contentType == null || contentType.isBlank()) return true;
        String value = contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        return switch (extension) {
            case "pdf" -> value.equals("application/pdf");
            case "png" -> value.equals("image/png");
            case "jpg", "jpeg" -> value.equals("image/jpeg") || value.equals("image/jpg");
            case "doc" -> value.equals("application/msword");
            case "docx" -> value.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            default -> false;
        };
    }
}
