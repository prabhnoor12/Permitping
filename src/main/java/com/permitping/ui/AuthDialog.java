package com.permitping.ui;

import com.permitping.application.AuthService;
import com.permitping.domain.AuthUser;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

/** Blocks access to the workspace until a valid local account has authenticated. */
public final class AuthDialog {
    private AuthDialog() { }

    public static AuthUser show(Stage owner, AuthService auth) {
        boolean provisioned = auth.isProvisioned();
        Dialog<AuthUser> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(provisioned ? "Sign in to PermitPing" : "Set up PermitPing");
        dialog.setHeaderText(provisioned ? "Sign in to open your compliance workspace." : "Create the first administrator account.");

        ButtonType action = new ButtonType(provisioned ? "Sign in" : "Create administrator", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(action, ButtonType.CANCEL);
        GridPane form = new GridPane();
        form.setHgap(10); form.setVgap(10); form.setPadding(new Insets(12, 0, 6, 0));
        TextField username = new TextField(); username.setPromptText("Username");
        PasswordField password = new PasswordField(); password.setPromptText("At least 12 characters");
        form.add(new Label("Username"), 0, 0); form.add(username, 1, 0);
        int passwordRow = 1; TextField displayName = null; PasswordField confirm = null;
        if (!provisioned) {
            displayName = new TextField(); displayName.setPromptText("Your name");
            form.add(new Label("Display name"), 0, 1); form.add(displayName, 1, 1); passwordRow = 2;
        }
        form.add(new Label("Password"), 0, passwordRow); form.add(password, 1, passwordRow);
        if (!provisioned) {
            confirm = new PasswordField(); confirm.setPromptText("Repeat password");
            form.add(new Label("Confirm password"), 0, 3); form.add(confirm, 1, 3);
        }
        Label error = new Label(); error.getStyleClass().add("form-error"); form.add(error, 1, provisioned ? 2 : 4);
        dialog.getDialogPane().setContent(form);

        final AuthUser[] result = {null};
        final boolean[] authenticating = {false};
        PasswordField confirmation = confirm; TextField name = displayName;
        Button actionButton = (Button) dialog.getDialogPane().lookupButton(action);
        Button cancelButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        dialog.setResultConverter(button -> button == action ? result[0] : null);
        dialog.setOnCloseRequest(event -> { if (authenticating[0]) event.consume(); });
        actionButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (authenticating[0]) return;
            try {
                if (username.getText().isBlank() || password.getText().isBlank()) throw new IllegalArgumentException("Enter your username and password.");
                if (!provisioned) {
                    if (name.getText().isBlank()) throw new IllegalArgumentException("Display name is required.");
                    if (!password.getText().equals(confirmation.getText())) throw new IllegalArgumentException("Passwords do not match.");
                }
            } catch (RuntimeException ex) { error.setText(ex.getMessage() == null ? "Unable to sign in." : ex.getMessage()); return; }

            authenticating[0] = true; actionButton.setDisable(true); cancelButton.setDisable(true); error.setText(provisioned ? "Checking credentials..." : "Creating administrator...");
            String account = username.getText().trim(); String display = name == null ? "" : name.getText().trim();
            char[] passwordForRegistration = provisioned ? null : password.getText().toCharArray();
            char[] passwordForLogin = password.getText().toCharArray();
            password.clear(); if (confirmation != null) confirmation.clear();
            Thread worker = new Thread(() -> {
                try {
                    if (!provisioned) auth.registerFirstAdmin(account, display, passwordForRegistration);
                    AuthUser authenticated = auth.authenticate(account, passwordForLogin);
                    if (authenticated == null) throw new SecurityException("Sign-in failed. Check your username and password.");
                    Platform.runLater(() -> { result[0] = authenticated; dialog.setResult(authenticated); dialog.close(); });
                } catch (RuntimeException ex) {
                    Platform.runLater(() -> { authenticating[0] = false; actionButton.setDisable(false); cancelButton.setDisable(false); error.setText(ex.getMessage() == null ? "Unable to sign in." : ex.getMessage()); });
                } finally {
                    if (passwordForRegistration != null) java.util.Arrays.fill(passwordForRegistration, '\0');
                    java.util.Arrays.fill(passwordForLogin, '\0');
                }
            }, "permitping-authentication");
            worker.setDaemon(true); worker.start();
        });
        username.requestFocus();
        return dialog.showAndWait().orElse(null);
    }
}
