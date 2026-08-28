package com.permitping.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class BackupService {
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final long MAX_BACKUP_ENTRY_BYTES = 256L * 1024 * 1024;
    private static final long MAX_BACKUP_TOTAL_BYTES = 2L * 1024 * 1024 * 1024;
    private static final int MAX_BACKUP_ENTRIES = 10_000;
    private final Path databasePath;
    private final Path documentDirectory;

    public BackupService(Path databasePath) {
        this(databasePath, databasePath.toAbsolutePath().normalize().resolveSibling("documents"));
    }

    public BackupService(Path databasePath, Path documentDirectory) {
        this.databasePath = databasePath.toAbsolutePath().normalize();
        this.documentDirectory = documentDirectory.toAbsolutePath().normalize();
    }

    public Path createAutomaticBackup(Path directory, int keep) throws Exception {
        if (keep < 1) throw new IllegalArgumentException("Backup retention must be at least one file");
        Path backupDirectory = directory.toAbsolutePath().normalize();
        Files.createDirectories(backupDirectory);

        Path databaseSnapshot = Files.createTempFile(backupDirectory, ".permitping-db-", ".tmp");
        Path bundleTemp = Files.createTempFile(backupDirectory, ".permitping-backup-", ".zip.tmp");
        Path destination = backupDirectory.resolve("permitping-backup-" + LocalDateTime.now().format(BACKUP_TIME) + "-" + shortId() + ".zip");
        try {
            Files.deleteIfExists(databaseSnapshot);
            vacuumInto(databaseSnapshot);
            verifyDatabase(databaseSnapshot);
            writeBundle(bundleTemp, databaseSnapshot);
            verifyBundle(bundleTemp);
            Files.move(bundleTemp, destination, StandardCopyOption.REPLACE_EXISTING);
            rotate(backupDirectory, keep);
            return destination;
        } finally {
            Files.deleteIfExists(databaseSnapshot);
            Files.deleteIfExists(bundleTemp);
        }
    }

    public void verify(Path backup) throws Exception {
        Path candidate = backup.toAbsolutePath().normalize();
        try (ZipFile ignored = new ZipFile(candidate.toFile())) {
            verifyBundle(candidate);
        } catch (ZipException ex) {
            verifyDatabase(candidate);
        }
    }

    /** Restores after validating the backup. Callers must stop concurrent database/file work and reload application state after success. */
    public Path restoreBackup(Path backup) throws Exception {
        Path candidate = backup.toAbsolutePath().normalize();
        verify(candidate);
        if (isZip(candidate)) return restoreBundle(candidate);
        return restoreLegacyDatabase(candidate);
    }

    private void vacuumInto(Path destination) throws Exception {
        String escaped = destination.toString().replace("'", "''");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
             Statement statement = connection.createStatement()) {
            statement.execute("VACUUM INTO '" + escaped + "'");
        }
    }

    private void writeBundle(Path bundle, Path databaseSnapshot) throws Exception {
        try (OutputStream output = Files.newOutputStream(bundle);
             ZipOutputStream zip = new ZipOutputStream(output)) {
            addFile(zip, databaseSnapshot, "permitping.db");
            if (Files.isDirectory(documentDirectory, LinkOption.NOFOLLOW_LINKS)) {
                List<Path> files;
                try (Stream<Path> paths = Files.walk(documentDirectory)) {
                    files = paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                            .sorted()
                            .toList();
                }
                for (Path file : files) {
                    Path relative = documentDirectory.relativize(file).normalize();
                    if (relative.isAbsolute() || relative.startsWith("..")) {
                        throw new IOException("Managed document is outside the document directory");
                    }
                    addFile(zip, file, "documents/" + relative.toString().replace(FileSystems.getDefault().getSeparator(), "/"));
                }
            }
        }
    }

    private void addFile(ZipOutputStream zip, Path file, String entryName) throws Exception {
        zip.putNextEntry(new ZipEntry(entryName));
        try (InputStream input = Files.newInputStream(file)) {
            copyLimited(input, zip, MAX_BACKUP_ENTRY_BYTES, entryName);
        } finally {
            zip.closeEntry();
        }
    }

    private void verifyBundle(Path bundle) throws Exception {
        try (ZipFile zip = new ZipFile(bundle.toFile())) {
            ZipEntry database = validateBundleEntries(zip);
            Path extracted = Files.createTempFile("permitping-verify-", ".db");
            long totalBytes;
            try {
                try (InputStream input = zip.getInputStream(database); OutputStream output = Files.newOutputStream(extracted)) {
                    totalBytes = copyLimited(input, output, MAX_BACKUP_ENTRY_BYTES, database.getName());
                }
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory() || !entry.getName().startsWith("documents/")) continue;
                    try (InputStream input = zip.getInputStream(entry)) {
                        totalBytes = addWithinLimit(totalBytes, copyLimited(input, OutputStream.nullOutputStream(), MAX_BACKUP_ENTRY_BYTES, entry.getName()), MAX_BACKUP_TOTAL_BYTES, entry.getName());
                    }
                }
                verifyDatabase(extracted);
            } finally {
                Files.deleteIfExists(extracted);
            }
        }
    }

    private ZipEntry validateBundleEntries(ZipFile zip) throws Exception {
        ZipEntry database = null;
        Set<String> names = new HashSet<>();
        long declaredBytes = 0;
        int entryCount = 0;
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (++entryCount > MAX_BACKUP_ENTRIES) throw new IllegalStateException("Backup contains too many entries");
            if (!names.add(entry.getName())) throw new IllegalStateException("Backup contains duplicate ZIP entries");
            if (entry.getSize() > MAX_BACKUP_ENTRY_BYTES) throw new IllegalStateException("Backup entry is too large: " + entry.getName());
            if (entry.getSize() > 0) declaredBytes = addWithinLimit(declaredBytes, entry.getSize(), MAX_BACKUP_TOTAL_BYTES, entry.getName());
            if ("permitping.db".equals(entry.getName())) {
                if (entry.isDirectory()) throw new IllegalStateException("Backup database entry is a directory");
                database = entry;
            } else if (entry.getName().equals("documents/") || entry.getName().startsWith("documents/")) {
                validateDocumentEntry(entry.getName());
            } else {
                throw new IllegalStateException("Backup contains an unsupported ZIP entry");
            }
        }
        if (database == null) throw new IllegalStateException("Backup does not contain a database");
        return database;
    }

    private void validateDocumentEntry(String entryName) {
        if (entryName.equals("documents/")) return;
        String rawRelative = entryName.substring("documents/".length());
        Path relative = Path.of(rawRelative).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new IllegalStateException("Backup contains an unsafe document path");
        }
    }

    private Path restoreBundle(Path bundle) throws Exception {
        String token = shortId();
        Path stagedDatabase = databasePath.resolveSibling(databasePath.getFileName() + ".restore-" + token + ".db");
        Path stagedDocuments = documentDirectory.resolveSibling(documentDirectory.getFileName() + ".restore-" + token);
        Files.createDirectories(stagedDocuments);
        try (ZipFile zip = new ZipFile(bundle.toFile())) {
            ZipEntry database = validateBundleEntries(zip);
            try (InputStream input = zip.getInputStream(database); OutputStream output = Files.newOutputStream(stagedDatabase)) {
                copyLimited(input, output, MAX_BACKUP_ENTRY_BYTES, database.getName());
            }
            Enumeration<? extends ZipEntry> entries = zip.entries();
            long totalBytes = Files.size(stagedDatabase);
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().startsWith("documents/") || entry.isDirectory()) continue;
                Path relative = safeDocumentPath(entry.getName());
                Path target = stagedDocuments.resolve(relative).normalize();
                if (!target.startsWith(stagedDocuments)) throw new IllegalStateException("Backup contains an unsafe document path");
                Files.createDirectories(target.getParent());
                try (InputStream input = zip.getInputStream(entry); OutputStream output = Files.newOutputStream(target)) {
                    long copied = copyLimited(input, output, MAX_BACKUP_ENTRY_BYTES, entry.getName());
                    totalBytes = addWithinLimit(totalBytes, copied, MAX_BACKUP_TOTAL_BYTES, entry.getName());
                }
            }
        }
        verifyDatabase(stagedDatabase);
        return installBundle(stagedDatabase, stagedDocuments, token);
    }

    private Path installBundle(Path stagedDatabase, Path stagedDocuments, String token) throws Exception {
        Path previousDatabase = databasePath.resolveSibling(databasePath.getFileName() + ".before-restore-" + timestamp() + ".db");
        Path previousDocuments = documentDirectory.resolveSibling(documentDirectory.getFileName() + ".before-restore-" + timestamp() + "-" + token);
        List<MovedArtifact> databaseSidecars = new ArrayList<>();
        boolean databaseMoved = false;
        boolean documentsMoved = false;
        boolean databaseInstalled = false;
        boolean documentsInstalled = false;
        try {
            moveDatabaseSidecars(previousDatabase, databaseSidecars);
            if (Files.exists(databasePath)) {
                Files.move(databasePath, previousDatabase);
                databaseMoved = true;
            }
            if (Files.exists(documentDirectory)) {
                Files.move(documentDirectory, previousDocuments);
                documentsMoved = true;
            }
            Files.move(stagedDatabase, databasePath);
            databaseInstalled = true;
            Files.move(stagedDocuments, documentDirectory);
            documentsInstalled = true;
            return previousDatabase;
        } catch (Exception failure) {
            rollbackInstall(previousDatabase, previousDocuments, stagedDatabase, stagedDocuments,
                    databaseMoved, documentsMoved, databaseInstalled, documentsInstalled, databaseSidecars);
            throw failure;
        } finally {
            Files.deleteIfExists(stagedDatabase);
            deleteTree(stagedDocuments);
        }
    }

    private void rollbackInstall(Path previousDatabase, Path previousDocuments, Path stagedDatabase, Path stagedDocuments,
                                 boolean databaseMoved, boolean documentsMoved, boolean databaseInstalled, boolean documentsInstalled,
                                 List<MovedArtifact> databaseSidecars) {
        try {
            if (documentsInstalled && Files.exists(documentDirectory)) Files.move(documentDirectory, stagedDocuments);
            if (databaseInstalled && Files.exists(databasePath)) Files.move(databasePath, stagedDatabase);
            if (documentsMoved && Files.exists(previousDocuments) && !Files.exists(documentDirectory)) Files.move(previousDocuments, documentDirectory);
            if (databaseMoved && Files.exists(previousDatabase) && !Files.exists(databasePath)) Files.move(previousDatabase, databasePath);
            for (int i = databaseSidecars.size() - 1; i >= 0; i--) {
                MovedArtifact sidecar = databaseSidecars.get(i);
                if (Files.exists(sidecar.safetyCopy()) && !Files.exists(sidecar.original())) Files.move(sidecar.safetyCopy(), sidecar.original());
            }
        } catch (Exception ignored) {
            // The original failure is more useful to the caller; staged/safety paths remain for recovery.
        }
    }

    private Path restoreLegacyDatabase(Path backup) throws Exception {
        Path staged = databasePath.resolveSibling(databasePath.getFileName() + ".restore-" + shortId() + ".db");
        Files.copy(backup, staged, StandardCopyOption.REPLACE_EXISTING);
        verifyDatabase(staged);
        Path previous = databasePath.resolveSibling(databasePath.getFileName() + ".before-restore-" + timestamp() + ".db");
        List<MovedArtifact> databaseSidecars = new ArrayList<>();
        try {
            moveDatabaseSidecars(previous, databaseSidecars);
            if (Files.exists(databasePath)) Files.move(databasePath, previous);
            Files.move(staged, databasePath);
            return previous;
        } catch (Exception failure) {
            if (Files.exists(previous) && !Files.exists(databasePath)) {
                try { Files.move(previous, databasePath); } catch (Exception ignored) { }
            }
            for (int i = databaseSidecars.size() - 1; i >= 0; i--) {
                MovedArtifact sidecar = databaseSidecars.get(i);
                if (Files.exists(sidecar.safetyCopy()) && !Files.exists(sidecar.original())) {
                    try { Files.move(sidecar.safetyCopy(), sidecar.original()); } catch (Exception ignored) { }
                }
            }
            throw failure;
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private void verifyDatabase(Path backup) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + backup.toAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA integrity_check")) {
            if (!result.next() || !"ok".equalsIgnoreCase(result.getString(1))) {
                throw new IllegalStateException("Backup integrity check failed");
            }
        }
    }

    private boolean isZip(Path candidate) {
        try (ZipFile ignored = new ZipFile(candidate.toFile())) {
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private Path safeDocumentPath(String entryName) {
        validateDocumentEntry(entryName);
        return Path.of(entryName.substring("documents/".length())).normalize();
    }

    private void rotate(Path directory, int keep) throws Exception {
        try (var paths = Files.list(directory)) {
            List<Path> backups = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("permitping-backup-")
                            && (path.getFileName().toString().endsWith(".zip") || path.getFileName().toString().endsWith(".db")))
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (Path old : backups.subList(Math.min(keep, backups.size()), backups.size())) Files.deleteIfExists(old);
        }
    }

    private void moveDatabaseSidecars(Path previousDatabase, List<MovedArtifact> moved) throws IOException {
        for (String suffix : List.of("-wal", "-shm")) {
            Path original = Path.of(databasePath + suffix);
            if (!Files.exists(original)) continue;
            Path safetyCopy = Path.of(previousDatabase + suffix);
            Files.move(original, safetyCopy);
            moved.add(new MovedArtifact(original, safetyCopy));
        }
    }

    private void deleteTree(Path directory) throws Exception {
        if (!Files.exists(directory)) return;
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private String timestamp() { return LocalDateTime.now().format(BACKUP_TIME); }
    private String shortId() { return UUID.randomUUID().toString().substring(0, 8); }
    private long copyLimited(InputStream input, OutputStream output, long limit, String name) throws IOException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) throw new IOException("Backup entry exceeds the permitted size: " + name);
            output.write(buffer, 0, read);
        }
        return total;
    }
    private long addWithinLimit(long current, long additional, long limit, String name) throws IOException {
        if (additional < 0 || current > limit - additional) throw new IOException("Backup contents exceed the permitted size near: " + name);
        return current + additional;
    }
    private record MovedArtifact(Path original, Path safetyCopy) { }
}
