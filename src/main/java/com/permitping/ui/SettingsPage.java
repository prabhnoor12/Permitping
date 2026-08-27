package com.permitping.ui;

import com.permitping.infrastructure.BackupService;
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

public final class SettingsPage extends VBox {
    private final Path dataDirectory; private final Path documentDirectory; private final Path backupDirectory;
    private final Label backupStatus = new Label();
    private final com.permitping.application.AuditService audit;
    public SettingsPage(Path dataDirectory, NotificationService notifications) {
        this(dataDirectory, notifications, null);
    }
    public SettingsPage(Path dataDirectory, NotificationService notifications, Runnable openAudit) {
        this(dataDirectory, notifications, openAudit, null);
    }
    public SettingsPage(Path dataDirectory, NotificationService notifications, Runnable openAudit, com.permitping.application.AuditService audit) {
        this.dataDirectory = dataDirectory; documentDirectory = dataDirectory.resolve("documents"); backupDirectory = dataDirectory.resolve("backups");
        this.audit = audit;
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
        Button backup = new Button("Create backup now"); backup.getStyleClass().add("primary"); backup.setTooltip(new Tooltip("Create a verified backup of the PermitPing database")); backup.setOnAction(event -> createBackup(notifications));
        Button restore = new Button("Restore backup"); restore.getStyleClass().add("danger"); restore.setTooltip(new Tooltip("Replace the current database with a backup")); restore.setOnAction(event -> restoreBackup(event.getSource() instanceof Control control ? control.getScene().getWindow() : null, notifications)); actions.getChildren().addAll(open, backup, restore); if (openAudit != null) { Button activity = new Button("View activity history"); activity.getStyleClass().add("secondary"); activity.setOnAction(event -> openAudit.run()); actions.getChildren().add(activity); } storage.getChildren().add(actions);
        backupStatus.getStyleClass().addAll("helper-text", "backup-status"); storage.getChildren().add(backupStatus); refresh(); getChildren().add(storage);
    }
    public void refresh() { try { if (!Files.exists(backupDirectory)) { backupStatus.setText("No backups found yet."); return; } try (var paths = Files.list(backupDirectory)) { var newest = paths.filter(Files::isRegularFile).max(Comparator.comparing(this::modified)).orElse(null); backupStatus.setText(newest == null ? "No backups found yet." : "Latest backup: " + newest.getFileName()); } } catch (Exception ex) { backupStatus.setText("Backup status unavailable."); } }
    private long modified(Path path) { try { return Files.getLastModifiedTime(path).toMillis(); } catch (Exception ex) { return Long.MIN_VALUE; } }
    private HBox pathRow(String label, Path path) { Label name = new Label(label); name.getStyleClass().add("settings-path-label"); name.setMinWidth(130); Label value = new Label(path.toAbsolutePath().toString()); value.getStyleClass().add("muted"); value.setWrapText(true); Button copy = new Button("Copy"); copy.getStyleClass().add("secondary"); copy.setTooltip(new Tooltip("Copy this path")); copy.setOnAction(event -> { ClipboardContent content = new ClipboardContent(); content.putString(path.toAbsolutePath().toString()); Clipboard.getSystemClipboard().setContent(content); }); HBox row = new HBox(12, name, value, copy); row.getStyleClass().add("settings-path-row"); row.setMaxWidth(Double.MAX_VALUE); HBox.setHgrow(value, Priority.ALWAYS); return row; }
    private void createBackup(NotificationService notifications) { try { Path backup = new BackupService(dataDirectory.resolve("permitping.db")).createAutomaticBackup(backupDirectory, 7); if (audit != null) audit.record("Created backup", backup.getFileName().toString()); refresh(); notifications.info("Backup created and verified."); } catch (Exception ex) { notifications.error("Backup failed: " + ex.getMessage()); } }
    private void restoreBackup(Window owner, NotificationService notifications) { FileChooser chooser = new FileChooser(); chooser.setTitle("Select a PermitPing backup"); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PermitPing database backup", "*.db")); File selected = chooser.showOpenDialog(owner); if (selected == null) return; Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Restoring will replace the current database. A copy of the current database will be kept.", ButtonType.CANCEL, ButtonType.OK); confirm.setHeaderText("Restore database backup"); confirm.showAndWait().filter(button -> button == ButtonType.OK).ifPresent(button -> { try { Path previous = new BackupService(dataDirectory.resolve("permitping.db")).restoreBackup(selected.toPath()); if (audit != null) audit.record("Restored backup", selected.getName()); notifications.info("Backup restored. Previous database saved as " + previous.getFileName() + ". Restart PermitPing to reload the restored data."); } catch (Exception ex) { notifications.error("Backup restore failed: " + ex.getMessage()); } }); }
    private void openFolder(Path path, NotificationService notifications) { try { Files.createDirectories(path); if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(new File(path.toString())); } catch (Exception ex) { notifications.error("Could not open the data folder."); } }
}
