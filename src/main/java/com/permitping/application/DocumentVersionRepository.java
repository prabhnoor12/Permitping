package com.permitping.application;

import com.permitping.domain.DocumentVersion;
import java.util.List;
public interface DocumentVersionRepository { List<DocumentVersion> findByDocument(long documentId); void save(DocumentVersion version); }
