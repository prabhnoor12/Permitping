package com.permitping.ui;

import com.permitping.application.AuthService;
import com.permitping.domain.AuthUser;
import com.permitping.domain.Permission;
import com.permitping.domain.Role;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/** Administrator-only account and role management backed by AuthService authorization. */
public final class UserManagementPage extends BorderPane {
    private final AuthService auth;
    private final AuthUser actor;
    private final NotificationService notifications;
    private final ListView<AuthUser> users = new ListView<>();
    private final ListView<Role> roles = new ListView<>();
    private final Button toggleActive = new Button();
    private final Button changeRole = new Button("Change role");
    private final Button resetPassword = new Button("Reset selected password");
    private final Button changePassword = new Button("Change my password");
    private final Button editRole = new Button("Edit selected role");
    private final Button deleteRole = new Button("Delete selected role");

    public UserManagementPage(AuthService auth, AuthUser actor, NotificationService notifications) {
        this.auth = auth;
        this.actor = actor;
        this.notifications = notifications;
        getStyleClass().add("page-shell");
        setPadding(new Insets(30, 34, 30, 34));
        setTop(new PageHeader("Users and roles", "Control who can access PermitPing and what each account can change."));

        users.setPlaceholder(new Label("No users found."));
        users.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(AuthUser item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item.displayName() + "  ·  " + item.username() + "  ·  " + item.role().name() + (item.active() ? "" : "  ·  Deactivated"));
            }
        });
        users.getSelectionModel().selectedItemProperty().addListener((observable, old, value) -> updateUserActions(value));

        roles.setPlaceholder(new Label("No roles found."));
        roles.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(Role item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item.name() + "  ·  " + item.permissions().size() + " permissions" + (item.system() ? "  ·  Built in" : ""));
            }
        });
        roles.getSelectionModel().selectedItemProperty().addListener((observable, old, value) -> updateRoleActions(value));

        Button addUser = new Button("+ Add user"); addUser.getStyleClass().add("primary"); addUser.setOnAction(event -> addUser());
        toggleActive.getStyleClass().add("secondary"); toggleActive.setDisable(true); toggleActive.setOnAction(event -> toggleActive());
        changeRole.getStyleClass().add("secondary"); changeRole.setDisable(true); changeRole.setOnAction(event -> changeRole());
        resetPassword.getStyleClass().add("secondary"); resetPassword.setDisable(true); resetPassword.setOnAction(event -> resetPassword(users.getSelectionModel().getSelectedItem()));
        changePassword.getStyleClass().add("secondary"); changePassword.setOnAction(event -> changePassword());
        FlowPane userActions = new FlowPane(10, 8, addUser, toggleActive, changeRole, resetPassword, changePassword); userActions.getStyleClass().add("toolbar");
        VBox userCard = new VBox(12, sectionTitle("Users"), users, userActions); userCard.getStyleClass().add("card"); VBox.setVgrow(users, Priority.ALWAYS);

        Button addRole = new Button("+ Add role"); addRole.getStyleClass().add("primary"); addRole.setOnAction(event -> editRole(null));
        editRole.getStyleClass().add("secondary"); editRole.setDisable(true); editRole.setOnAction(event -> editRole(roles.getSelectionModel().getSelectedItem()));
        deleteRole.getStyleClass().add("danger"); deleteRole.setDisable(true); deleteRole.setOnAction(event -> deleteRole(roles.getSelectionModel().getSelectedItem()));
        FlowPane roleActions = new FlowPane(10, 8, addRole, editRole, deleteRole); roleActions.getStyleClass().add("toolbar");
        VBox roleCard = new VBox(12, sectionTitle("Roles"), roles, roleActions, helper("Built-in roles cannot be changed. Reassign users before deleting a custom role.")); roleCard.getStyleClass().add("card"); VBox.setVgrow(roles, Priority.ALWAYS);

        SplitPane split = new SplitPane(userCard, roleCard); split.setDividerPositions(.55); split.getStyleClass().add("document-split");
        setCenter(split);
        refresh();
    }

    private Label sectionTitle(String text) { Label label = new Label(text); label.getStyleClass().add("section-title"); return label; }
    private Label helper(String text) { Label label = new Label(text); label.getStyleClass().add("helper-text"); label.setWrapText(true); return label; }

    private void refresh() {
        try {
            users.setItems(FXCollections.observableArrayList(auth.users(actor)));
            roles.setItems(FXCollections.observableArrayList(auth.roles(actor)));
            updateUserActions(users.getSelectionModel().getSelectedItem());
            updateRoleActions(roles.getSelectionModel().getSelectedItem());
        } catch (RuntimeException ex) { notifications.error(message(ex, "Could not load users and roles.")); }
    }

    private void updateUserActions(AuthUser selected) {
        boolean present = selected != null;
        toggleActive.setDisable(!present);
        toggleActive.setText(present && selected.active() ? "Deactivate selected" : "Activate selected");
        changeRole.setDisable(!present);
        resetPassword.setDisable(!present);
    }

    private void updateRoleActions(Role selected) {
        boolean custom = selected != null && !selected.system();
        editRole.setDisable(!custom);
        deleteRole.setDisable(!custom);
    }

    private void addUser() {
        List<Role> available;
        try { available = auth.roles(actor); } catch (RuntimeException ex) { notifications.error(message(ex, "Could not load roles.")); return; }
        Dialog<AuthUser> dialog = new Dialog<>(); dialog.setTitle("Add user"); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        TextField username = new TextField(); username.setPromptText("Username"); TextField display = new TextField(); display.setPromptText("Display name"); PasswordField password = new PasswordField(); PasswordField confirmation = new PasswordField();
        ComboBox<Role> role = new ComboBox<>(FXCollections.observableArrayList(available)); if (!available.isEmpty()) role.setValue(available.get(0));
        GridPane grid = new GridPane(); grid.setHgap(14); grid.setVgap(12); grid.setPadding(new Insets(18)); grid.addRow(0, new Label("Username"), username); grid.addRow(1, new Label("Display name"), display); grid.addRow(2, new Label("Password"), password); grid.addRow(3, new Label("Confirm password"), confirmation); grid.addRow(4, new Label("Role"), role); Label error = new Label(); error.getStyleClass().add("form-error"); grid.add(error, 1, 5); dialog.getDialogPane().setContent(grid);
        final AuthUser[] created = {null};
        final boolean[] running = {false};
        dialog.setOnCloseRequest(event -> { if (running[0]) event.consume(); });
        dialog.setOnShown(event -> dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, action -> {
            action.consume(); if (running[0]) return;
            if (!password.getText().equals(confirmation.getText())) { error.setText("Passwords do not match."); return; }
            running[0] = true;
            Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK); Button cancel = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
            ok.setDisable(true); cancel.setDisable(true); error.setText("Creating user...");
            String userName = username.getText().trim(); String displayName = display.getText().trim(); Role selectedRole = role.getValue(); char[] passwordChars = password.getText().toCharArray();
            password.clear(); confirmation.clear();
            Thread worker = new Thread(() -> {
                try {
                    AuthUser saved = auth.createUser(actor, userName, displayName, passwordChars, selectedRole);
                    Platform.runLater(() -> { created[0] = saved; dialog.setResult(saved); dialog.close(); notifications.info("User created successfully."); });
                } catch (RuntimeException ex) {
                    Platform.runLater(() -> { running[0] = false; ok.setDisable(false); cancel.setDisable(false); error.setText(message(ex, "User could not be created.")); });
                } finally { java.util.Arrays.fill(passwordChars, '\0'); }
            }, "permitping-user-create"); worker.setDaemon(true); worker.start();
        }));
        dialog.setResultConverter(button -> button == ButtonType.OK ? created[0] : null);
        dialog.showAndWait().ifPresent(createdUser -> refresh());
    }

    private void toggleActive() {
        AuthUser selected = users.getSelectionModel().getSelectedItem(); if (selected == null) return;
        try { auth.setActive(actor, selected.id(), !selected.active()); refresh(); }
        catch (RuntimeException ex) { notifications.error(message(ex, "User status could not be changed.")); }
    }

    private void changeRole() {
        AuthUser selected = users.getSelectionModel().getSelectedItem(); if (selected == null) return;
        List<Role> available;
        try { available = auth.roles(actor); } catch (RuntimeException ex) { notifications.error(message(ex, "Could not load roles.")); return; }
        Dialog<Role> dialog = new Dialog<>(); dialog.setTitle("Change role"); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        ComboBox<Role> role = new ComboBox<>(FXCollections.observableArrayList(available)); role.setValue(available.stream().filter(value -> value.equals(selected.role())).findFirst().orElse(null)); VBox content = new VBox(10, new Label("Choose the permissions for " + selected.displayName() + "."), role); content.setPadding(new Insets(18)); dialog.getDialogPane().setContent(content);
        dialog.setOnShown(event -> dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, action -> { try { auth.setRole(actor, selected.id(), role.getValue()); } catch (RuntimeException ex) { action.consume(); notifications.error(message(ex, "User role could not be changed.")); } }));
        dialog.setResultConverter(button -> button == ButtonType.OK ? role.getValue() : null);
        dialog.showAndWait().ifPresent(changed -> refresh());
    }

    private void changePassword() { showPasswordDialog(null); }
    private void resetPassword(AuthUser selected) { if (selected != null) showPasswordDialog(selected); }

    private void showPasswordDialog(AuthUser resetTarget) {
        boolean reset = resetTarget != null;
        Dialog<Boolean> dialog = new Dialog<>(); dialog.setTitle(reset ? "Reset password" : "Change my password"); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        PasswordField current = reset ? null : new PasswordField(); PasswordField next = new PasswordField(); PasswordField confirmation = new PasswordField();
        if (current != null) current.setPromptText("Current password"); next.setPromptText("At least 12 characters"); confirmation.setPromptText("Repeat password");
        GridPane grid = new GridPane(); grid.setHgap(14); grid.setVgap(12); grid.setPadding(new Insets(18)); int row = 0;
        if (!reset) { grid.addRow(row++, new Label("Current password"), current); }
        grid.addRow(row++, new Label("New password"), next); grid.addRow(row++, new Label("Confirm password"), confirmation); Label error = new Label(); error.getStyleClass().add("form-error"); grid.add(error, 1, row); dialog.getDialogPane().setContent(grid);
        final boolean[] running = {false};
        Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK); Button cancel = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        dialog.setResultConverter(button -> button == ButtonType.OK ? Boolean.TRUE : null); dialog.setOnCloseRequest(event -> { if (running[0]) event.consume(); });
        ok.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume(); if (running[0]) return;
            if (!next.getText().equals(confirmation.getText())) { error.setText("Passwords do not match."); return; }
            running[0] = true; ok.setDisable(true); cancel.setDisable(true); error.setText(reset ? "Resetting password..." : "Changing password...");
            char[] currentChars = current == null ? null : current.getText().toCharArray(); char[] nextChars = next.getText().toCharArray();
            if (current != null) current.clear(); next.clear(); confirmation.clear();
            Thread worker = new Thread(() -> {
                try {
                    if (reset) auth.resetPassword(actor, resetTarget.id(), nextChars); else auth.changePassword(actor, currentChars, nextChars);
                    Platform.runLater(() -> { dialog.setResult(Boolean.TRUE); dialog.close(); notifications.info(reset ? "Password reset successfully." : "Your password was changed successfully."); });
                } catch (RuntimeException ex) { Platform.runLater(() -> { running[0] = false; ok.setDisable(false); cancel.setDisable(false); error.setText(message(ex, "Password could not be changed.")); }); }
                finally { if (currentChars != null) java.util.Arrays.fill(currentChars, '\0'); java.util.Arrays.fill(nextChars, '\0'); }
            }, "permitping-password-change"); worker.setDaemon(true); worker.start();
        });
        dialog.showAndWait();
    }

    private void editRole(Role existing) {
        Dialog<Role> dialog = new Dialog<>(); dialog.setTitle(existing == null ? "Add role" : "Edit role"); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        TextField name = new TextField(existing == null ? "" : existing.name()); if (existing != null) name.setDisable(true);
        VBox permissions = new VBox(8); Map<Permission, CheckBox> checks = new EnumMap<>(Permission.class); for (Permission permission : Permission.values()) { CheckBox check = new CheckBox(permission.name().replace('_', ' ')); check.setSelected(existing != null && existing.allows(permission)); checks.put(permission, check); permissions.getChildren().add(check); }
        GridPane grid = new GridPane(); grid.setHgap(14); grid.setVgap(12); grid.setPadding(new Insets(18)); grid.add(new Label("Role name"), 0, 0); grid.add(name, 1, 0); grid.add(new Label("Permissions"), 0, 1); grid.add(permissions, 1, 1); dialog.getDialogPane().setContent(grid);
        final Role[] saved = {null};
        dialog.setOnShown(event -> dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, action -> { try { EnumSet<Permission> selected = EnumSet.noneOf(Permission.class); checks.forEach((permission, check) -> { if (check.isSelected()) selected.add(permission); }); saved[0] = existing == null ? auth.createRole(actor, name.getText(), selected) : auth.updateRole(actor, existing.name(), selected); } catch (RuntimeException ex) { action.consume(); notifications.error(message(ex, "Role could not be saved.")); } }));
        dialog.setResultConverter(button -> button == ButtonType.OK ? saved[0] : null);
        dialog.showAndWait().ifPresent(value -> refresh());
    }

    private void deleteRole(Role role) {
        if (role == null || role.system()) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete the custom role '" + role.name() + "'? Users must be reassigned first.", ButtonType.CANCEL, ButtonType.OK); confirm.setHeaderText("Delete role");
        confirm.showAndWait().filter(button -> button == ButtonType.OK).ifPresent(button -> { try { auth.deleteRole(actor, role.name()); refresh(); } catch (RuntimeException ex) { notifications.error(message(ex, "Role could not be deleted.")); } });
    }

    private String message(RuntimeException ex, String fallback) { return ex.getMessage() == null ? fallback : ex.getMessage(); }
}
