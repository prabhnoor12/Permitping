package com.permitping.ui;

import com.permitping.application.ProfileService;
import com.permitping.domain.*;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

public final class ProfileManager {
    private final ProfileService profiles; private final NotificationService notifications;
    public ProfileManager(ProfileService profiles, NotificationService notifications) { this.profiles = profiles; this.notifications = notifications; }
    public void show() {
        Dialog<Void> dialog = new Dialog<>(); dialog.setTitle("Profiles"); dialog.setHeaderText("People and companies connected to your compliance register"); dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        ListView<Profile> list = new ListView<>(FXCollections.observableArrayList(profiles.list())); list.setPrefSize(600, 340);
        list.setCellFactory(ignored -> new ListCell<>() { @Override protected void updateItem(Profile item, boolean empty) { super.updateItem(item, empty); setText(empty || item == null ? null : item.name() + " · " + item.type().label() + (item.email() == null || item.email().isBlank() ? "" : " · " + item.email())); } });
        Button add = new Button("+ Add profile"); add.getStyleClass().add("primary"); add.setOnAction(event -> addProfile(list));
        Label hint = new Label("Profiles can be reused as document holders and project assignees."); hint.getStyleClass().add("helper-text");
        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(12, hint, list, add); content.setPadding(new Insets(10)); dialog.getDialogPane().setContent(content); dialog.showAndWait();
    }
    private void addProfile(ListView<Profile> list) {
        Dialog<Profile> dialog = new Dialog<>(); dialog.setTitle("Add profile"); dialog.setHeaderText("Create a reusable holder or assignee"); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        TextField name = new TextField(); name.setPromptText("Company or worker name"); ComboBox<ProfileType> type = new ComboBox<>(FXCollections.observableArrayList(ProfileType.values())); type.setValue(ProfileType.COMPANY); TextField email = new TextField(); email.setPromptText("Email (optional)"); TextField phone = new TextField(); phone.setPromptText("Phone (optional)");
        GridPane grid = new GridPane(); grid.setHgap(14); grid.setVgap(12); grid.setPadding(new Insets(12)); grid.addRow(0, new Label("Name"), name); grid.addRow(1, new Label("Type"), type); grid.addRow(2, new Label("Email"), email); grid.addRow(3, new Label("Phone"), phone); GridPane.setHgrow(name, javafx.scene.layout.Priority.ALWAYS); GridPane.setHgrow(email, javafx.scene.layout.Priority.ALWAYS); GridPane.setHgrow(phone, javafx.scene.layout.Priority.ALWAYS); dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(button -> button == ButtonType.OK ? new Profile(0, name.getText(), type.getValue(), email.getText(), phone.getText(), "") : null);
        dialog.showAndWait().ifPresent(profile -> { try { profiles.save(profile); list.getItems().setAll(profiles.list()); } catch (IllegalArgumentException ex) { notifications.error(ex.getMessage()); } });
    }
}
