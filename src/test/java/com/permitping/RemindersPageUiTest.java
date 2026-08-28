package com.permitping;

import com.permitping.application.DocumentRepository;
import com.permitping.application.DocumentService;
import com.permitping.application.ReminderRepository;
import com.permitping.application.ReminderService;
import com.permitping.domain.Document;
import com.permitping.ui.RemindersPage;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class RemindersPageUiTest {
    @Test void refreshesPendingCardWhenAThresholdIsDisabled() throws Exception {
        FxTestSupport.runAndWait(() -> {
            MutableReminderRepository repository = new MutableReminderRepository();
            Document document = new Document(1, "License", "License", "Acme", "Job", LocalDate.now().plusDays(1), "", "");
            ReminderService reminders = new ReminderService(
                new DocumentService(new DocumentRepository() {
                    public List<Document> findAll() { return List.of(document); }
                    public Document save(Document value) { return value; }
                    public void delete(long id) { }
                }), repository);
            RemindersPage page = new RemindersPage(reminders);

            CheckBox sevenDays = page.getChildren().stream()
                .flatMap(node -> node instanceof VBox box ? box.getChildren().stream() : java.util.stream.Stream.<Node>empty())
                .filter(node -> node instanceof CheckBox check && check.getText().equals("7 days before expiry"))
                .map(CheckBox.class::cast)
                .findFirst().orElseThrow();
            sevenDays.fire();

            VBox pending = page.getChildren().stream()
                .filter(node -> node instanceof VBox box && box.getStyleClass().contains("reminder-pending-card"))
                .map(VBox.class::cast)
                .findFirst().orElseThrow();
            assertTrue(pending.getChildren().get(2).getStyleClass().contains("empty-state"));
        });
    }

    private static final class MutableReminderRepository implements ReminderRepository {
        private final List<Integer> enabled = new ArrayList<>(List.of(7));
        public List<Integer> enabledThresholds() { return List.copyOf(enabled); }
        public void setThresholdEnabled(int days, boolean value) { if (value && !enabled.contains(days)) enabled.add(days); if (!value) enabled.remove(Integer.valueOf(days)); }
        public boolean wasSent(long documentId, int days) { return false; }
        public void markSent(long documentId, int days, java.time.LocalDateTime at) { }
    }
}
