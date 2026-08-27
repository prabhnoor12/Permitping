package com.permitping.application;

import com.permitping.domain.Document;
import com.permitping.domain.AssignmentClearance;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

public final class ExportService {
    public Path exportCsv(List<Document> documents, Path destination) throws IOException {
        Files.createDirectories(destination.toAbsolutePath().getParent());
        try (BufferedWriter out=Files.newBufferedWriter(destination, StandardCharsets.UTF_8)) {
            out.write("Document,Type,Holder,Project,Expires,File\n");
            for (Document d:documents) out.write(String.join(",", csv(d.name()),csv(d.type()),csv(d.holder()),csv(d.project()),d.expiresOn().toString(),csv(d.filePath()))+"\n");
        }
        return destination;
    }
    public Path exportAssignmentsCsv(List<AssignmentClearance> assignments, Path destination) throws IOException {
        Files.createDirectories(destination.toAbsolutePath().getParent());
        try (BufferedWriter out=Files.newBufferedWriter(destination, StandardCharsets.UTF_8)) {
            out.write("Profile,Project,Assignment status,Clearance,Issues\n");
            for (AssignmentClearance a:assignments) out.write(String.join(",", csv(a.profile()==null?"Unknown":a.profile().name()),csv(a.assignment().project()),csv(a.assignment().status().label()),csv(a.clearance().label()),csv(a.issueSummary()))+"\n");
        }
        return destination;
    }
    private String csv(String value) { String s=value==null?"":value.replace("\"","\"\""); return "\""+s+"\""; }
}
