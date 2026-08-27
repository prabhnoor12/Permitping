package com.permitping;

import com.permitping.application.ExportService;
import com.permitping.domain.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class ExportServiceTest {
    @TempDir Path temp;

    @Test void exportsCommaQuotesAndNewlinesAsCsvFields() throws Exception {
        Document document = new Document(1, "Insurance, General", "Insurance certificate", "Acme \"North\"", "Job\n42", LocalDate.of(2026, 8, 26), "C:\\docs\\policy.pdf", "");
        Path output = new ExportService().exportCsv(List.of(document), temp.resolve("exports/register.csv"));

        String csv = Files.readString(output);
        assertTrue(csv.contains("\"Insurance, General\""));
        assertTrue(csv.contains("\"Acme \"\"North\"\"\""));
        assertTrue(csv.contains("\"Job\n42\""));
    }
}
