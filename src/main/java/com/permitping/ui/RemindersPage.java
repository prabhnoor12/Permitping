package com.permitping.ui;

import com.permitping.application.ReminderService;
import com.permitping.application.ReminderDeliveryService;
import com.permitping.domain.ReminderNotice;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.Set;
import java.util.stream.Collectors;

public final class RemindersPage extends VBox {
    private final ReminderService reminders;
    private final ReminderDeliveryService delivery;
    private final Runnable openHistory;
    public RemindersPage(ReminderService reminders) {
        this(reminders, null, null, null);
    }
    public RemindersPage(ReminderService reminders, ReminderDeliveryService delivery, NotificationService notifications) {
        this(reminders, delivery, notifications, null);
    }
    public RemindersPage(ReminderService reminders, ReminderDeliveryService delivery, NotificationService notifications, Runnable openHistory) {
        this.reminders = reminders; this.delivery = delivery; this.openHistory = openHistory;
        getStyleClass().add("page-shell");
        setSpacing(18); setPadding(new Insets(30, 34, 30, 34));
        getChildren().add(new PageHeader("Reminders", "Choose the lead time that keeps expiring documents visible during your weekly check."));
        Set<Integer> enabled = reminders.enabledThresholds().stream().collect(Collectors.toSet());
        VBox card = new VBox(14); card.getStyleClass().addAll("card", "reminder-settings-card");
        Label title = new Label("Expiration thresholds"); title.getStyleClass().add("section-title");
        Label intro = new Label("Choose when PermitPing should bring an expiring document back to your attention.");
        intro.getStyleClass().add("helper-text");
        Label summary = new Label(); summary.getStyleClass().add("helper-text"); card.getChildren().addAll(title, summary);
        card.getChildren().add(intro);
        VBox pending = new VBox(12); pending.getStyleClass().addAll("card", "reminder-pending-card");
        for (int days : new int[]{90, 60, 30, 14, 7}) {
            CheckBox check = new CheckBox(days + " days before expiry");
            check.getStyleClass().add("reminder-threshold");
            check.setSelected(enabled.contains(days));
            check.setOnAction(event -> { reminders.setThresholdEnabled(days, check.isSelected()); refreshSummary(summary, reminders); refreshPending(pending, reminders); });
            card.getChildren().add(check);
        }
        refreshSummary(summary, reminders);
        if (delivery != null) {
            Button send = new Button("Send pending reminders"); send.getStyleClass().add("primary"); send.setMaxWidth(Double.MAX_VALUE);
            send.setOnAction(event -> { send.setDisable(true); send.setText("Sending reminders..."); javafx.concurrent.Task<java.util.List<com.permitping.domain.ReminderDelivery>> task = new javafx.concurrent.Task<>() { @Override protected java.util.List<com.permitping.domain.ReminderDelivery> call() { return delivery.sendPending(); } }; task.setOnSucceeded(done -> { long successful = task.getValue().stream().filter(d -> d.status() == com.permitping.domain.DeliveryStatus.SENT).count(); send.setDisable(false); send.setText("Send pending reminders"); refreshPending(pending, reminders); if (notifications != null) notifications.info(successful == 0 ? "No reminders were sent. Check contractor preferences and email configuration." : successful + " reminder(s) sent successfully."); }); task.setOnFailed(done -> { send.setDisable(false); send.setText("Send pending reminders"); if (notifications != null) notifications.error("Reminder delivery failed: " + task.getException().getMessage()); }); Thread worker = new Thread(task, "permitping-reminder-delivery"); worker.setDaemon(true); worker.start(); });
            card.getChildren().add(send);
            Button history = new Button("View delivery history"); history.getStyleClass().add("secondary");
            history.setOnAction(event -> { if (openHistory != null) openHistory.run(); else if (notifications != null) notifications.info("Delivery history is not available in this workspace."); });
            card.getChildren().add(history);
        }
        Label note = new Label("Notifications are reviewed at startup, and configured email and SMS deliveries remain available in delivery history.");
        note.getStyleClass().add("helper-text"); card.getChildren().add(note);
        refreshPending(pending, reminders); getChildren().addAll(card, pending);
    }

    private void refreshSummary(Label summary, ReminderService reminders) {
        var enabled = reminders.enabledThresholds();
        summary.setText(enabled.isEmpty() ? "No reminder thresholds are enabled." : "Active thresholds: " + enabled.stream().sorted().map(days -> days + " days").collect(Collectors.joining(", ")));
    }
    private void refreshPending(VBox card, ReminderService reminders) {
        card.getChildren().clear();
        Label title = new Label("Pending reminders"); title.getStyleClass().add("section-title");
        Label subtitle = new Label("Documents that need attention based on your active thresholds."); subtitle.getStyleClass().add("helper-text");
        card.getChildren().addAll(title, subtitle);
        var pending = reminders.pending();
        if (pending.isEmpty()) {
            VBox empty = new VBox(6); empty.getStyleClass().add("empty-state");
            Label icon = new Label("✓"); icon.getStyleClass().add("reminder-empty-icon");
            Label message = new Label("You’re all caught up"); message.getStyleClass().add("empty-title");
            Label detail = new Label("Nothing is due under the current thresholds."); detail.getStyleClass().add("helper-text");
            empty.getChildren().addAll(icon, message, detail); card.getChildren().add(empty); return;
        }
        for (ReminderNotice notice : pending) {
            Label marker = new Label("!"); marker.getStyleClass().add("reminder-marker");
            Label text = new Label(notice.message()); text.setWrapText(true); text.getStyleClass().add("reminder-copy");
            Button snooze = new Button("Snooze 7 days"); snooze.getStyleClass().add("secondary");
            snooze.setOnAction(event -> { reminders.snooze(notice, 7); refreshPending(card, reminders); });
            HBox row = new HBox(12, marker, text, snooze); row.getStyleClass().add("reminder-row");
            HBox.setHgrow(text, Priority.ALWAYS); card.getChildren().add(row);
        }
    }
}
