package com.permitping.application;

import com.permitping.domain.DocumentVersion;
import java.time.*;
import java.util.List;

public final class DocumentVersionService {
    private final DocumentVersionRepository repository; private final Clock clock;
    public DocumentVersionService(DocumentVersionRepository repository) { this(repository, Clock.systemDefaultZone()); }
    public DocumentVersionService(DocumentVersionRepository repository, Clock clock) { this.repository=repository;this.clock=clock; }
    public List<DocumentVersion> list(long documentId) { return repository.findByDocument(documentId); }
    public void record(long documentId, String filePath) { if(documentId>0&&filePath!=null&&!filePath.isBlank()) repository.save(new DocumentVersion(0,documentId,list(documentId).size()+1,filePath,LocalDateTime.now(clock))); }
}
