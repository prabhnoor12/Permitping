package com.permitping.ui;

import com.permitping.application.ReminderService;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.util.Set;
import java.util.stream.Collectors;

public final class ReminderManager {
    private final ReminderService reminders;
    public ReminderManager(ReminderService reminders) { this.reminders = reminders; }
    public void show() {
        Dialog<Void> dialog = new Dialog<>(); dialog.setTitle("Reminder settings"); dialog.setHeaderText("Choose when PermitPing should flag upcoming expirations"); dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Set<Integer> enabled = reminders.enabledThresholds().stream().collect(Collectors.toSet());
        VBox content = new VBox(10); content.setPadding(new Insets(16)); content.getChildren().add(new Label("Notify me before a document expires:"));
        for (int days : new int[]{90, 60, 30, 14, 7}) { CheckBox check = new CheckBox(days + " days before expiry"); check.setSelected(enabled.contains(days)); check.setOnAction(event -> reminders.setThresholdEnabled(days, check.isSelected())); content.getChildren().add(check); }
        Label note = new Label("Each threshold is sent once per document and recorded in reminder history."); note.getStyleClass().add("helper-text"); content.getChildren().add(note); dialog.getDialogPane().setContent(content); dialog.showAndWait();
    }
}
