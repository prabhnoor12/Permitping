package com.permitping;

import com.permitping.infrastructure.FileStorage;
import com.permitping.ui.DocumentDetailView;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DocumentDetailViewUiTest {
    @Test void displaysASelectionSummaryForBulkSelection() throws Exception {
        FxTestSupport.runAndWait(() -> {
            try {
                DocumentDetailView details = new DocumentDetailView(new FileStorage(Files.createTempDirectory("permitping-detail")), null, new com.permitping.ui.NotificationService(), null, ignored -> { }, ignored -> { }, ignored -> { });
                Scene scene = new Scene(details, 420, 400); scene.getRoot().applyCss(); scene.getRoot().layout();
                details.showSelectionSummary(3);
                scene.getRoot().applyCss(); scene.getRoot().layout();
                Label title = (Label) details.lookup(".detail-title");
                assertEquals("3 documents selected", title.getText());
            } catch (Exception ex) { throw new RuntimeException(ex); }
        });
    }
}
