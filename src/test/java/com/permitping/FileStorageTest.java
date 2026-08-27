package com.permitping;

import com.permitping.infrastructure.FileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class FileStorageTest {
    @TempDir Path temp;

    @Test void importsFilesIntoManagedStorage() throws Exception {
        Path source = temp.resolve("license.pdf");
        Files.writeString(source, "license contents");
        FileStorage storage = new FileStorage(temp.resolve("documents"));

        Path imported = storage.importFile(source);

        assertTrue(storage.isManaged(imported.toString()));
        assertTrue(Files.exists(imported));
        assertEquals("license contents", Files.readString(imported));
    }

    @Test void rejectsMissingSourceFile() {
        FileStorage storage = new FileStorage(temp.resolve("documents"));
        assertThrows(Exception.class, () -> storage.importFile(temp.resolve("missing.pdf")));
    }

    @Test void rejectsInvalidDocumentIdsWhenCopying() throws Exception {
        Path source = temp.resolve("license.pdf"); Files.writeString(source, "license contents");
        FileStorage storage = new FileStorage(temp.resolve("documents"));
        assertThrows(Exception.class, () -> storage.copyIntoStorage(0, source));
    }

    @Test void detectsDuplicateContentButCanExcludeCurrentPath() throws Exception {
        Path source = temp.resolve("source.pdf");
        Files.writeString(source, "same content");
        FileStorage storage = new FileStorage(temp.resolve("documents"));
        Path imported = storage.importFile(source);

        assertTrue(storage.isDuplicate(source, null));
        assertFalse(storage.isDuplicate(source, imported.toString()));
    }
}
