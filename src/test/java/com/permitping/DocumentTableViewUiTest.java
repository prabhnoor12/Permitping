package com.permitping;

import com.permitping.application.*;
import com.permitping.domain.Document;
import com.permitping.domain.Profile;
import com.permitping.domain.ProfileType;
import com.permitping.infrastructure.FileStorage;
import com.permitping.ui.DocumentTableView;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class DocumentTableViewUiTest {
    @Test void showsDifferentEmptyStateWhenRecordsExistButFiltersMatchNothing() throws Exception {
        FxTestSupport.runAndWait(() -> {
            DocumentTableView table = table(List.of(document(1, "License", LocalDate.now().plusDays(60))), null);
            table.setQuickFilter("Expired");
            assertEquals(0, table.getItems().size());
            assertTrue(((Label) table.getPlaceholder()).getText().contains("current search and filters"));
        });
    }

    @Test void supportsMultipleSelectionForBulkActions() throws Exception {
        FxTestSupport.runAndWait(() -> {
            DocumentTableView table = table(List.of(document(1, "One", LocalDate.now().plusDays(60)), document(2, "Two", LocalDate.now().plusDays(60))), null);
            table.getSelectionModel().selectAll();
            assertEquals(List.of(1L, 2L), table.selectedDocuments().stream().map(Document::id).toList());
        });
    }

    @Test void sortsExpirationColumnChronologically() throws Exception {
        FxTestSupport.runAndWait(() -> {
            Document first = document(1, "Later", LocalDate.of(2027, 1, 2));
            Document second = document(2, "Earlier", LocalDate.of(2026, 12, 15));
            DocumentTableView table = table(List.of(first, second), null);
            TableColumn<Document, ?> expires = table.getColumns().get(4);
            table.getSortOrder().add(expires);
            assertEquals(2L, table.getItems().get(0).id());
        });
    }

    @Test void rendersCurrentProfileNameAfterRename() throws Exception {
        FxTestSupport.runAndWait(() -> {
            List<Profile> profiles = new ArrayList<>(List.of(new Profile(7, "Old Name", ProfileType.COMPANY, "", "", "")));
            ProfileService profileService = new ProfileService(new ProfileRepository() { public List<Profile> findAll() { return profiles; } public void save(Profile value) { profiles.set(0, value); } });
            DocumentTableView table = table(List.of(documentWithProfile(1, 7)), profileService);
            assertEquals("Old Name", table.getColumns().get(2).getCellData(0));
            profiles.set(0, new Profile(7, "New Name", ProfileType.COMPANY, "", "", ""));
            table.refresh();
            assertEquals("New Name", table.getColumns().get(2).getCellData(0));
        });
    }

    private DocumentTableView table(List<Document> documents, ProfileService profiles) {
        DocumentService service = new DocumentService(new DocumentRepository() { public List<Document> findAll() { return documents; } public Document save(Document value) { return value; } public void delete(long id) { } });
        try { DocumentTableView view = new DocumentTableView(service, new FileStorage(Files.createTempDirectory("permitping-ui")), profiles, ignored -> { }); view.refresh(); return view; }
        catch (Exception ex) { throw new RuntimeException(ex); }
    }
    private Document document(long id, String name, LocalDate expiry) { return new Document(id, name, "License", "Holder", "Project", expiry, "", ""); }
    private Document documentWithProfile(long id, long profileId) { return new Document(id, "License", "License", "Old Name", "Project", LocalDate.now().plusDays(60), "", "", profileId); }
}
