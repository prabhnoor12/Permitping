package com.permitping.ui;

import com.permitping.application.ReminderDeliveryRepository;
import com.permitping.domain.ReminderDelivery;
import com.permitping.domain.DeliveryStatus;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.format.DateTimeFormatter;

public final class DeliveryHistoryPage extends BorderPane {
    private final ReminderDeliveryRepository deliveries;
    private final ListView<ReminderDelivery> list = new ListView<>();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final DateTimeFormatter format = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    public DeliveryHistoryPage(ReminderDeliveryRepository deliveries) {
        this.deliveries = deliveries; getStyleClass().add("page-shell"); setPadding(new Insets(30,34,30,34));
        setTop(new PageHeader("Reminder delivery history", "Track when contractor notifications were sent, skipped, or failed."));
        list.setPlaceholder(new Label("No delivery attempts recorded yet."));
        list.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(ReminderDelivery item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                Label status = new Label(item.status().name());
                status.getStyleClass().addAll("status", "delivery-" + item.status().name().toLowerCase());
                Label message = new Label(format.format(item.attemptedAt()) + "  ·  " + item.channel() + "  ·  " + item.recipient() + "  ·  document #" + item.documentId());
                message.getStyleClass().add("delivery-message");
                Label detail = new Label(item.errorMessage() == null || item.errorMessage().isBlank() ? item.channel() + " delivery completed." : item.errorMessage());
                detail.getStyleClass().add("helper-text");
                VBox copy = new VBox(3, message, detail); Region push = new Region(); HBox.setHgrow(push, Priority.ALWAYS);
                HBox row = new HBox(12, copy, push, status); setGraphic(row); setText(null);
            }
        });
        Label title = new Label("Recent attempts"); title.getStyleClass().add("section-title");
        Label hint = new Label("Failed attempts remain visible and can be retried from the Reminders page."); hint.getStyleClass().add("helper-text");
        statusFilter.getItems().addAll("All statuses", "Sent", "Failed", "Skipped"); statusFilter.setValue("All statuses"); statusFilter.setOnAction(e -> refresh());
        Button refresh = new Button("Refresh"); refresh.getStyleClass().add("secondary"); refresh.setOnAction(e -> refresh());
        FlowPane actions = new FlowPane(10, 8, statusFilter, refresh); actions.getStyleClass().add("toolbar");
        VBox card = new VBox(12, title, hint, actions, list); card.getStyleClass().addAll("card", "delivery-history-card"); setCenter(card); refresh();
    }
    public void refresh() { var items = deliveries.recent(100); String selected = statusFilter.getValue(); if (selected != null && !selected.equals("All statuses")) { DeliveryStatus status = DeliveryStatus.valueOf(selected.toUpperCase()); items = items.stream().filter(item -> item.status() == status).toList(); } list.setItems(FXCollections.observableArrayList(items)); }
}
