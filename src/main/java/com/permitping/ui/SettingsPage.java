package com.permitping.ui;

import com.permitping.infrastructure.BackupService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import java.awt.Desktop;
import java.io.File;
import java.nio.file.*;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SettingsPage extends VBox {
    private final Path dataDirectory; private final Path documentDirectory; private final Path backupDirectory;
    private final Label backupStatus = new Label();
    private final com.permitping.application.AuditService audit; private final Runnable beforeRestore; private final Runnable onRestoreComplete; private final Runnable onRestoreFailure; private final AtomicBoolean storageOperationRunning = new AtomicBoolean();
    public SettingsPage(Path dataDirectory, NotificationService notifications) {
        this(dataDirectory, notifications, null);
    }
    public SettingsPage(Path dataDirectory, NotificationService notifications, Runnable openAudit) {
        this(dataDirectory, notifications, openAudit, null);
    }
    public SettingsPage(Path dataDirectory, NotificationService notifications, Runnable openAudit, com.permitping.application.AuditService audit) {
        this(dataDirectory, notifications, openAudit, audit, null);
    }
    public SettingsPage(Path dataDirectory, NotificationService notifications, Runnable openAudit, com.permitping.application.AuditService audit, Runnable openUsers) {
        this(dataDirectory, notifications, openAudit, audit, openUsers, null, null, null);
    }
    public SettingsPage(Path dataDirectory, NotificationService notifications, Runnable openAudit, com.permitping.application.AuditService audit, Runnable openUsers, Runnable onRestoreComplete) {
        this(dataDirectory, notifications, openAudit, audit, openUsers, null, onRestoreComplete, null);
    }
    public SettingsPage(Path dataDirectory, NotificationService notifications, Runnable openAudit, com.permitping.application.AuditService audit, Runnable openUsers, Runnable beforeRestore, Runnable onRestoreComplete) {
        this(dataDirectory, notifications, openAudit, audit, openUsers, beforeRestore, onRestoreComplete, null);
    }
    public SettingsPage(Path dataDirectory, NotificationService notifications, Runnable openAudit, com.permitping.application.AuditService audit, Runnable openUsers, Runnable beforeRestore, Runnable onRestoreComplete, Runnable onRestoreFailure) {
        this.dataDirectory = dataDirectory; documentDirectory = dataDirectory.resolve("documents"); backupDirectory = dataDirectory.resolve("backups");
        this.audit = audit; this.beforeRestore = beforeRestore; this.onRestoreComplete = onRestoreComplete; this.onRestoreFailure = onRestoreFailure;
        getStyleClass().add("page-shell");
        setSpacing(18); setPadding(new Insets(30, 34, 30, 34));
        getChildren().add(new PageHeader("System settings", "Manage where PermitPing keeps its database, documents, and recovery backups."));
        VBox storage = new VBox(14); storage.getStyleClass().addAll("card", "settings-card");
        Label storageTitle = new Label("Storage and recovery"); storageTitle.getStyleClass().add("section-title");
        Label storageSubtitle = new Label("Your data stays on this computer. Review the locations below before creating or restoring a backup."); storageSubtitle.getStyleClass().add("helper-text");
        storage.getChildren().addAll(storageTitle, storageSubtitle);
        storage.getChildren().add(pathRow("Database and backups", dataDirectory)); storage.getChildren().add(pathRow("Managed documents", documentDirectory)); storage.getChildren().add(pathRow("Backup files", backupDirectory));
        Label privacy = new Label("This is a local workspace. Anyone with access to this Windows account may be able to access these files."); privacy.getStyleClass().add("helper-text"); storage.getChildren().add(privacy);
        FlowPane actions = new FlowPane(10, 8); Button open = new Button("Open data folder"); open.getStyleClass().add("secondary"); open.setTooltip(new Tooltip("Open the local PermitPing data folder")); open.setOnAction(event -> openFolder(dataDirectory, notifications));
        Button backup = new Button("Create backup now"); backup.getStyleClass().add("primary"); backup.setTooltip(new Tooltip("Create a verified backup of the database and managed documents"));
        Button restore = new Button("Restore backup"); restore.getStyleClass().add("danger"); restore.setTooltip(new Tooltip("Replace the current database and managed documents with a backup")); backup.setOnAction(event -> createBackup(backup, restore, notifications)); restore.setOnAction(event -> restoreBackup(event.getSource() instanceof Control control ? control.getScene().getWindow() : null, notifications, backup, restore)); actions.getChildren().addAll(open, backup, restore); if (openAudit != null) { Button activity = new Button("View activity history"); activity.getStyleClass().add("secondary"); activity.setOnAction(event -> openAudit.run()); actions.getChildren().add(activity); } if (openUsers != null) { Button users = new Button("Manage users and roles"); users.getStyleClass().add("secondary"); users.setOnAction(event -> openUsers.run()); actions.getChildren().add(users); } storage.getChildren().add(actions);
        backupStatus.getStyleClass().addAll("helper-text", "backup-status"); storage.getChildren().add(backupStatus); refresh(); getChildren().add(storage);
    }
    public void refresh() { try { if (!Files.exists(backupDirectory)) { backupStatus.setText("No backups found yet."); return; } try (var paths = Files.list(backupDirectory)) { var newest = paths.filter(Files::isRegularFile).max(Comparator.comparing(this::modified)).orElse(null); backupStatus.setText(newest == null ? "No backups found yet." : "Latest backup: " + newest.getFileName()); } } catch (Exception ex) { backupStatus.setText("Backup status unavailable."); } }
    private long modified(Path path) { try { return Files.getLastModifiedTime(path).toMillis(); } catch (Exception ex) { return Long.MIN_VALUE; } }
    private HBox pathRow(String label, Path path) { Label name = new Label(label); name.getStyleClass().add("settings-path-label"); name.setMinWidth(130); Label value = new Label(path.toAbsolutePath().toString()); value.getStyleClass().add("muted"); value.setWrapText(true); Button copy = new Button("Copy"); copy.getStyleClass().add("secondary"); copy.setTooltip(new Tooltip("Copy this path")); copy.setOnAction(event -> { ClipboardContent content = new ClipboardContent(); content.putString(path.toAbsolutePath().toString()); Clipboard.getSystemClipboard().setContent(content); }); HBox row = new HBox(12, name, value, copy); row.getStyleClass().add("settings-path-row"); row.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(value, Priority.ALWAYS); return row; }
    private void createBackup(Button backupButton, Button restoreButton, NotificationService notifications) { if (!storageOperationRunning.compareAndSet(false, true)) return; backupButton.setDisable(true); restoreButton.setDisable(true); backupButton.setText("Creating backup..."); Thread worker = new Thread(() -> { try { Path backup = new BackupService(dataDirectory.resolve("permitping.db")).createAutomaticBackup(backupDirectory, 7); if (audit != null) audit.record("Created backup", backup.getFileName().toString()); Platform.runLater(() -> { resetStorageButtons(backupButton, restoreButton); refresh(); notifications.info("Backup created and verified."); }); } catch (Exception ex) { Platform.runLater(() -> { resetStorageButtons(backupButton, restoreButton); notifications.error("Backup failed: " + message(ex)); }); } }, "permitping-backup"); worker.setDaemon(true); worker.start(); }
    private void restoreBackup(Window owner, NotificationService notifications, Button backupButton, Button restoreButton) { if (storageOperationRunning.get()) { notifications.info("Another storage operation is still running. Wait for it to finish, then try again."); return; } FileChooser chooser = new FileChooser(); chooser.setTitle("Select a PermitPing backup"); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PermitPing backup (*.zip, *.db)", "*.zip", "*.db")); File selected = chooser.showOpenDialog(owner); if (selected == null) return; Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Restoring will replace the current database and managed documents. Copies of the current data will be kept.", ButtonType.CANCEL, ButtonType.OK); confirm.setHeaderText("Restore PermitPing backup"); confirm.showAndWait().filter(button -> button == ButtonType.OK).ifPresent(button -> { try { if (beforeRestore != null) beforeRestore.run(); if (!storageOperationRunning.compareAndSet(false, true)) throw new IllegalStateException("Another storage operation is still running. Try again."); backupButton.setDisable(true); restoreButton.setDisable(true); restoreButton.setText("Restoring backup..."); Thread worker = new Thread(() -> { try { Path previous = new BackupService(dataDirectory.resolve("permitping.db")).restoreBackup(selected.toPath()); if (audit != null) audit.record("Restored backup", selected.getName()); Platform.runLater(() -> { storageOperationRunning.set(false); if (onRestoreComplete != null) onRestoreComplete.run(); else { resetStorageButtons(backupButton, restoreButton); notifications.info("Backup restored. Previous database saved as " + previous.getFileName() + ". Restart PermitPing to reload the restored data."); } }); } catch (Exception ex) { Platform.runLater(() -> { storageOperationRunning.set(false); resetStorageButtons(backupButton, restoreButton); if (onRestoreFailure != null) onRestoreFailure.run(); notifications.error("Backup restore failed: " + message(ex)); }); } }, "permitping-restore"); worker.setDaemon(true); worker.start(); } catch (Exception ex) { storageOperationRunning.set(false); resetStorageButtons(backupButton, restoreButton); if (onRestoreFailure != null) onRestoreFailure.run(); notifications.error("Backup restore failed: " + message(ex)); } }); }
    private void resetStorageButtons(Button backupButton, Button restoreButton) { storageOperationRunning.set(false); backupButton.setDisable(false); restoreButton.setDisable(false); backupButton.setText("Create backup now"); restoreButton.setText("Restore backup"); }
    private String message(Exception ex) { return ex.getMessage() == null ? "Unknown error" : ex.getMessage(); }
    private void openFolder(Path path, NotificationService notifications) { try { Files.createDirectories(path); if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(new File(path.toString())); } catch (Exception ex) { notifications.error("Could not open the data folder."); } }
}
