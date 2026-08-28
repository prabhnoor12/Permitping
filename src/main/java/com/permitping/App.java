package com.permitping;

import com.permitping.application.DocumentService;
import com.permitping.infrastructure.Database;
import com.permitping.infrastructure.SqliteDocumentRepository;
import com.permitping.infrastructure.SqliteRequirementTemplateRepository;
import com.permitping.infrastructure.SqliteProfileRepository;
import com.permitping.infrastructure.SqliteAssignmentRepository;
import com.permitping.infrastructure.SqliteReminderRepository;
import com.permitping.infrastructure.SqliteDocumentVersionRepository;
import com.permitping.infrastructure.BackupService;
import com.permitping.application.RequirementTemplateService;
import com.permitping.application.ProfileService;
import com.permitping.application.AssignmentService;
import com.permitping.application.ReminderService;
import com.permitping.application.DocumentVersionService;
import com.permitping.application.AuthService;
import com.permitping.application.AuditService;
import com.permitping.infrastructure.SqliteAuthRepository;
import com.permitping.ui.MainView;
import com.permitping.ui.AuthDialog;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class App extends Application {
    @Override public void start(Stage stage) {
        java.nio.file.Path dataDirectory = java.nio.file.Path.of(System.getProperty("user.dir"), "data");
        java.nio.file.Path databasePath = dataDirectory.resolve("permitping.db");
        Database database = new Database(databasePath);
        try { new BackupService(databasePath).createAutomaticBackup(dataDirectory.resolve("backups"), 7); } catch (Exception backupFailure) { System.err.println("PermitPing backup skipped: " + backupFailure.getMessage()); }
        AuthService auth = new AuthService(new SqliteAuthRepository(database), new AuditService(new com.permitping.infrastructure.SqliteAuditRepository(databasePath)));
        com.permitping.domain.AuthUser authenticated = AuthDialog.show(stage, auth);
        if (authenticated == null) { javafx.application.Platform.exit(); return; }
        showWorkspace(stage, dataDirectory, database, auth, authenticated);
    }
    private void showWorkspace(Stage stage, java.nio.file.Path dataDirectory, Database database, AuthService auth, com.permitping.domain.AuthUser authenticated) {
        DocumentService documents = new DocumentService(new SqliteDocumentRepository(database));
        MainView view = new MainView(documents, new RequirementTemplateService(new SqliteRequirementTemplateRepository(database)), new ProfileService(new SqliteProfileRepository(database)), new AssignmentService(new SqliteAssignmentRepository(database)), new ReminderService(documents, new SqliteReminderRepository(database)), new DocumentVersionService(new SqliteDocumentVersionRepository(database)), authenticated.role().permissions(), auth, authenticated, () -> {
            com.permitping.domain.AuthUser next = AuthDialog.show(stage, auth);
            if (next == null) { javafx.application.Platform.exit(); return; }
            showWorkspace(stage, dataDirectory, database, auth, next);
        });
        Scene scene = new Scene(view, 1180, 760);
        scene.getStylesheets().add(getClass().getResource("/app.css").toExternalForm());
        stage.setTitle("PermitPing â€” compliance, without the spreadsheet chase");
        stage.setMinWidth(980); stage.setMinHeight(650); stage.setScene(scene); stage.show();
    }
    public static void main(String[] args) { launch(args); }
}
