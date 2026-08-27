package com.permitping.application;

import com.permitping.domain.Document;
import java.util.List;

public interface DocumentRepository {
    List<Document> findAll();
    default List<Document> findArchived() { return List.of(); }
    Document save(Document document);
    void delete(long id);
    default void archive(long id) { delete(id); }
    default void restore(long id) { }
}
