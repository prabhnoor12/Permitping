package com.permitping.ui;

import com.permitping.application.AuditService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import java.time.format.DateTimeFormatter;

public final class AuditPage extends BorderPane {
    private final AuditService audit;
    private final ListView<String> events = new ListView<>();
    private final TextField filter = new TextField();
    private final Label summary = new Label();
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    public AuditPage(AuditService audit) {
        this.audit = audit;
        getStyleClass().add("page-shell"); setPadding(new Insets(30, 34, 30, 34));
        setTop(new PageHeader("Activity history", "Review important changes made to documents, profiles, assignments, and backups."));
        Label title = new Label("Recent activity"); title.getStyleClass().add("section-title");
        Label subtitle = new Label("A chronological record of important workspace actions."); subtitle.getStyleClass().add("helper-text");
        filter.setPromptText("Filter activity by action or subject..."); filter.setAccessibleText("Filter activity history"); filter.textProperty().addListener((obs, oldValue, newValue) -> refresh());
        Button clear = new Button("Clear filter"); clear.getStyleClass().add("secondary"); clear.setOnAction(event -> filter.clear());
        FlowPane toolbar = new FlowPane(10, 10, filter, clear); toolbar.getStyleClass().add("toolbar"); toolbar.setPrefWrapLength(700); filter.setPrefWidth(360); filter.setMinWidth(220);
        summary.getStyleClass().add("helper-text"); events.setPlaceholder(new Label("No activity recorded yet.")); events.setAccessibleText("Activity history");
        VBox card = new VBox(12, title, subtitle, toolbar, summary, events); VBox.setVgrow(events, Priority.ALWAYS); card.getStyleClass().addAll("card", "audit-card"); setCenter(card); refresh();
    }

    public void refresh() {
        String q = filter.getText() == null ? "" : filter.getText().trim().toLowerCase();
        var items = audit.recent().stream().filter(entry -> q.isBlank() || entry.action().toLowerCase().contains(q) || entry.subject().toLowerCase().contains(q))
                .map(entry -> FORMAT.format(entry.occurredAt()) + "  |  " + entry.action() + (entry.subject().isBlank() ? "" : "  |  " + entry.subject())).toList();
        events.setItems(FXCollections.observableArrayList(items)); summary.setText(items.size() + " entr" + (items.size() == 1 ? "y" : "ies"));
    }
}
