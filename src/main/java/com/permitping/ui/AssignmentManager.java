package com.permitping.ui;

import com.permitping.application.*;
import com.permitping.domain.*;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import java.util.*;

public final class AssignmentManager {
    private final AssignmentService assignments; private final ProfileService profiles; private final ClearanceService clearance; private final List<String> projects; private final NotificationService notifications;
    public AssignmentManager(AssignmentService assignments, ProfileService profiles, ClearanceService clearance, List<String> projects, NotificationService notifications) { this.assignments=assignments;this.profiles=profiles;this.clearance=clearance;this.projects=projects;this.notifications=notifications; }
    public void show() {
        Dialog<Void> dialog = new Dialog<>(); dialog.setTitle("Project assignments"); dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        ListView<AssignmentClearance> list = new ListView<>(); list.setPrefSize(720, 340); refresh(list);
        list.setCellFactory(ignored -> new ListCell<>() { @Override protected void updateItem(AssignmentClearance item, boolean empty) { super.updateItem(item, empty); setText(empty || item == null ? null : (item.profile() == null ? "Unknown profile" : item.profile().name()) + " → " + item.assignment().project() + " · " + item.assignment().status().label() + " · " + item.clearance().label() + " — " + item.issueSummary()); } });
        Button add = new Button("+ Add assignment"); add.getStyleClass().add("primary"); add.setOnAction(event -> edit(null, list)); list.setOnMouseClicked(event -> { if (event.getClickCount() == 2 && list.getSelectionModel().getSelectedItem() != null) edit(list.getSelectionModel().getSelectedItem().assignment(), list); });
        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(10, list, add); content.setPadding(new Insets(10)); dialog.getDialogPane().setContent(content); dialog.showAndWait();
    }
    private void refresh(ListView<AssignmentClearance> list) { list.getItems().setAll(clearance.list()); }
    private void edit(ProjectAssignment existing, ListView<AssignmentClearance> list) {
        if (profiles.list().isEmpty()) { notifications.error("Create a profile before assigning someone to a project."); return; }
        Dialog<ProjectAssignment> dialog = new Dialog<>(); dialog.setTitle(existing == null ? "Add assignment" : "Edit assignment"); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        ComboBox<String> project = new ComboBox<>(FXCollections.observableArrayList(projects)); project.setEditable(true); project.setValue(existing == null ? null : existing.project());
        Map<String, Profile> byName = new LinkedHashMap<>(); profiles.list().forEach(p -> byName.put(p.name(), p)); ComboBox<String> profile = new ComboBox<>(FXCollections.observableArrayList(byName.keySet())); if (existing != null) byName.values().stream().filter(p -> p.id() == existing.profileId()).findFirst().ifPresent(p -> profile.setValue(p.name()));
        ComboBox<AssignmentStatus> status = new ComboBox<>(FXCollections.observableArrayList(AssignmentStatus.values())); status.setValue(existing == null ? AssignmentStatus.PENDING : existing.status()); TextField notes = new TextField(existing == null ? "" : existing.notes());
        GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(12)); grid.addRow(0, new Label("Project"), project); grid.addRow(1, new Label("Profile"), profile); grid.addRow(2, new Label("Review status"), status); grid.addRow(3, new Label("Notes"), notes); dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(button -> { Profile selected = byName.get(profile.getValue()); return button == ButtonType.OK && selected != null ? new ProjectAssignment(existing == null ? 0 : existing.id(), project.getEditor().getText(), selected.id(), status.getValue(), notes.getText()) : null; });
        dialog.showAndWait().ifPresent(value -> { try { assignments.save(value); refresh(list); } catch (IllegalArgumentException ex) { notifications.error(ex.getMessage()); } });
    }
}
