package com.permitping.ui;

import com.permitping.application.*;
import com.permitping.domain.Document;
import com.permitping.domain.Permission;
import com.permitping.domain.AuthUser;
import com.permitping.infrastructure.FileStorage;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.stage.WindowEvent;

public final class MainView extends BorderPane {
    private final DocumentService documents; private final FileStorage files; private final DashboardView dashboard; private final ProjectReadinessView readiness; private final DocumentTableView table; private final DocumentForm form; private final DocumentDetailView details; private final ProfileService profiles; private final AssignmentService assignments; private final ReminderService reminders; private final DocumentVersionService versions; private final NotificationService notifications = new NotificationService();
    private final StackPane workspace = new StackPane(); private final StackPane tableHost = new StackPane(); private final VBox emptyState = new VBox(10); private final Label filterLabel = new Label(); private Button activeNav; private final AuditService audit = new AuditService(new com.permitping.infrastructure.SqliteAuditRepository(java.nio.file.Path.of(System.getProperty("user.dir"),"data","permitping.db"))); private final ReminderDeliveryService delivery; private ComboBox<String> projects; private ScheduledExecutorService reminderScheduler; private final AtomicBoolean reminderDeliveryRunning = new AtomicBoolean(); private final java.util.Set<Permission> permissions; private final AuthService auth; private final AuthUser authenticatedUser; private final Runnable onLogout;
    public MainView(DocumentService documents) { this(documents, null, null); }
    public MainView(DocumentService documents, RequirementTemplateService templates) { this(documents, templates, null, null); }
    public MainView(DocumentService documents, RequirementTemplateService templates, ProfileService profiles) { this(documents, templates, profiles, null, null); }
    public MainView(DocumentService documents, RequirementTemplateService templates, ProfileService profiles, AssignmentService assignments) { this(documents, templates, profiles, assignments, null, null); }
    public MainView(DocumentService documents, RequirementTemplateService templates, ProfileService profiles, AssignmentService assignments, ReminderService reminders) { this(documents, templates, profiles, assignments, reminders, null); }
    public MainView(DocumentService documents, RequirementTemplateService templates, ProfileService profiles, AssignmentService assignments, ReminderService reminders, DocumentVersionService versions) { this(documents, templates, profiles, assignments, reminders, versions, java.util.EnumSet.allOf(Permission.class), null, null); }
    public MainView(DocumentService documents, RequirementTemplateService templates, ProfileService profiles, AssignmentService assignments, ReminderService reminders, DocumentVersionService versions, java.util.Set<Permission> permissions) { this(documents, templates, profiles, assignments, reminders, versions, permissions, null, null, null); }
    public MainView(DocumentService documents, RequirementTemplateService templates, ProfileService profiles, AssignmentService assignments, ReminderService reminders, DocumentVersionService versions, java.util.Set<Permission> permissions, AuthService auth, AuthUser authenticatedUser) { this(documents, templates, profiles, assignments, reminders, versions, permissions, auth, authenticatedUser, null); }
    public MainView(DocumentService documents, RequirementTemplateService templates, ProfileService profiles, AssignmentService assignments, ReminderService reminders, DocumentVersionService versions, java.util.Set<Permission> permissions, AuthService auth, AuthUser authenticatedUser, Runnable onLogout) {
        this.documents=documents; this.profiles=profiles; this.assignments=assignments; this.reminders=reminders; this.versions=versions; this.permissions=java.util.Set.copyOf(permissions==null?java.util.EnumSet.allOf(Permission.class):permissions); this.auth=auth; this.authenticatedUser=authenticatedUser; this.onLogout=onLogout; files=new FileStorage(java.nio.file.Path.of(System.getProperty("user.dir"),"data","documents")); workspace.getStyleClass().add("workspace");
        delivery = reminders != null && profiles != null && can(Permission.MANAGE_REMINDERS) ? new ReminderDeliveryService(reminders, profiles, new com.permitping.infrastructure.SqliteReminderDeliveryRepository(new com.permitping.infrastructure.Database(java.nio.file.Path.of(System.getProperty("user.dir"),"data","permitping.db"))), new com.permitping.infrastructure.SendGridEmailSender(), new com.permitping.infrastructure.TwilioSmsSender()) : null; form=new DocumentForm(documents,profiles,files,versions,notifications); dashboard=new DashboardView(documents); readiness=new ProjectReadinessView(new ProjectReadinessService(documents,files,java.time.Clock.systemDefaultZone(),templates),templates,this::showProjectDocuments); details=new DocumentDetailView(files,versions,notifications,profiles,this::edit,this::renew,this::archive,can(Permission.MANAGE_DOCUMENTS)); table=new DocumentTableView(documents,files,profiles,details::show);
        setLeft(sidebar()); ScrollPane scroll = new ScrollPane(workspace); scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED); scroll.setPannable(true); scroll.getStyleClass().add("workspace-scroll"); setCenter(scroll); showDashboard(); refresh(); if(reminders!=null) { javafx.application.Platform.runLater(this::showPendingReminders); if (delivery != null) startReminderScheduler(); }
    }
    private void showDashboard() { workspace.getChildren().setAll(can(Permission.VIEW_DOCUMENTS)?buildDashboard():accessDenied("the document overview")); }
    private Node buildDashboard() {
        VBox content=new VBox(18); content.setPadding(new Insets(30,34,30,34)); content.getStyleClass().add("content-shell");
        HBox heading=new HBox(14); VBox titles=new VBox(4); Label title=new Label("Compliance overview"); title.getStyleClass().add("title"); Label sub=new Label("Keep every crew, credential, and permit ready for the next job."); sub.getStyleClass().add("muted"); titles.getChildren().addAll(title,sub); Region push=new Region(); HBox.setHgrow(push,Priority.ALWAYS); Button add=new Button("+ Add document"); add.getStyleClass().add("primary"); add.setVisible(can(Permission.MANAGE_DOCUMENTS)); add.setManaged(add.isVisible()); add.setOnAction(e->form.show(null,d->refresh())); heading.getChildren().addAll(titles,push,add);
        FlowPane chips=quickFilters(); emptyState.getStyleClass().add("empty-state"); Label emptyTitle=new Label("You're ready to start"); emptyTitle.getStyleClass().add("empty-title"); Label emptyCopy=new Label("Add your first license, insurance certificate, or permit."); emptyCopy.getStyleClass().add("muted"); Button emptyAdd=new Button("Add your first document"); emptyAdd.getStyleClass().add("primary"); emptyAdd.setVisible(can(Permission.MANAGE_DOCUMENTS)); emptyAdd.setManaged(emptyAdd.isVisible()); emptyAdd.setOnAction(e->form.show(null,d->refresh())); emptyState.getChildren().addAll(emptyTitle,emptyCopy,emptyAdd); tableHost.getChildren().setAll(table,emptyState); tableHost.setMinHeight(420);
        SplitPane split=new SplitPane(tableHost,details); split.setDividerPositions(.68); split.getStyleClass().add("document-split"); details.setPrefWidth(315); split.widthProperty().addListener((observable, oldWidth, newWidth) -> split.setOrientation(newWidth.doubleValue() < 760 ? Orientation.VERTICAL : Orientation.HORIZONTAL)); split.setOrientation(Orientation.HORIZONTAL); VBox tableCard=new VBox(12,table.toolbar(this::refresh),chips,split); tableCard.getStyleClass().add("card"); VBox.setVgrow(tableCard,Priority.ALWAYS); VBox readinessCard=new VBox(readiness); readinessCard.getStyleClass().add("card"); content.getChildren().addAll(heading,dashboard,readinessCard,tableCard); return content;
    }
    private FlowPane quickFilters(){ FlowPane box=new FlowPane(8,8); box.getStyleClass().add("filter-chips"); for(String name:new String[]{"All","Expired","Due this week","Due this month","Missing file","By project"}){ ToggleButton chip=new ToggleButton(name); chip.getStyleClass().add("filter-chip"); chip.setOnAction(e->{for(Node node:box.getChildren())if(node instanceof ToggleButton other&&other!=chip)other.setSelected(false);table.setQuickFilter(name);if(projects!=null)projects.setVisible(name.equals("By project"));filterLabel.setText(name.equals("All")?"Showing all documents":"Showing: "+name);}); if(name.equals("All"))chip.setSelected(true); box.getChildren().add(chip); } projects=new ComboBox<>(); projects.getItems().add("All projects"); projects.setVisible(false); projects.valueProperty().addListener((o,a,b)->table.setProjectFilter(b)); filterLabel.getStyleClass().add("muted"); filterLabel.setText("Showing all documents"); box.getChildren().addAll(projects,filterLabel); return box; }
    private VBox sidebar(){ VBox box=new VBox(18); box.getStyleClass().add("sidebar"); box.setPrefWidth(220); Label brand=new Label("PermitPing"); brand.getStyleClass().add("brand"); Label sub=new Label("CONTRACTOR COMPLIANCE"); sub.getStyleClass().add("brand-sub"); VBox nav=new VBox(5); if(can(Permission.VIEW_DOCUMENTS)){Button dashboardButton=navButton("Dashboard"); dashboardButton.setOnAction(e->showDashboard()); Button all=navButton("All documents"); all.setOnAction(e->showDocuments()); nav.getChildren().addAll(dashboardButton,all);} if(can(Permission.VIEW_PROFILES)){Button profileButton=navButton("Profiles"); profileButton.setOnAction(e->showProfiles()); nav.getChildren().add(profileButton);} if(can(Permission.MANAGE_ASSIGNMENTS)){Button assignmentButton=navButton("Assignments"); assignmentButton.setOnAction(e->showAssignments()); nav.getChildren().add(assignmentButton);} if(can(Permission.MANAGE_REMINDERS)){Button reminderButton=navButton("Reminders"); reminderButton.setOnAction(e->showReminders()); nav.getChildren().add(reminderButton);} if(can(Permission.VIEW_DOCUMENTS)){Button archivedButton=navButton("Archived"); archivedButton.setOnAction(e->showArchived()); nav.getChildren().add(archivedButton);} if(can(Permission.MANAGE_SETTINGS)){Button settingsButton=navButton("System settings"); settingsButton.setOnAction(e->showSettings()); nav.getChildren().add(settingsButton);} if(can(Permission.VIEW_AUDIT)){Button auditButton=navButton("Activity history"); auditButton.setOnAction(e->showAudit()); nav.getChildren().add(auditButton);} Region grow=new Region(); VBox.setVgrow(grow,Priority.ALWAYS); Label hint=new Label("LOCAL WORKSPACE\nData is stored on this computer."); hint.getStyleClass().add("brand-sub"); box.getChildren().addAll(brand,sub,nav,grow); if(authenticatedUser!=null){Label account=new Label("SIGNED IN AS\n"+authenticatedUser.displayName()); account.getStyleClass().add("brand-sub"); Button signOut=new Button("Sign out"); signOut.getStyleClass().add("nav"); signOut.setMaxWidth(Double.MAX_VALUE); signOut.setOnAction(event->signOut()); box.getChildren().addAll(account,signOut);} box.getChildren().add(hint); return box; }
    private Button navButton(String text){Button b=new Button(text);b.getStyleClass().add("nav");b.setMaxWidth(Double.MAX_VALUE);b.setMnemonicParsing(false);b.setTooltip(new Tooltip("Open " + text));if(activeNav==null){b.getStyleClass().add("active");activeNav=b;}b.addEventHandler(javafx.event.ActionEvent.ACTION,event->{if(!sessionValid()){event.consume();return;}if(activeNav!=null)activeNav.getStyleClass().remove("active");activeNav=b;if(!b.getStyleClass().contains("active"))b.getStyleClass().add("active");});return b;}
    private void showProfiles(){ if(!can(Permission.VIEW_PROFILES)){showDenied("profiles");return;} if(profiles==null){notifications.error("Profile storage is not configured.");return;} workspace.getChildren().setAll(new ProfilesPage(profiles,documents,assignments,notifications,can(Permission.MANAGE_PROFILES))); }
    private void showDocuments(){ if(!can(Permission.VIEW_DOCUMENTS)){showDenied("documents");return;} workspace.getChildren().setAll(new DocumentsPage(documents,profiles,files,versions,notifications,this::showSearch,can(Permission.VIEW_REPORTS)?this::showReports:null,can(Permission.MANAGE_DOCUMENTS),can(Permission.VIEW_REPORTS))); }
    private void showSearch(){ if(!can(Permission.VIEW_DOCUMENTS)){showDenied("search");return;} workspace.getChildren().setAll(new GlobalSearchPage(documents,profiles,assignments)); }
    private void showReports(){ if(!can(Permission.VIEW_REPORTS)){showDenied("reports");return;} workspace.getChildren().setAll(new ReportsPage(documents,assignments,profiles,files,notifications)); }
    private void showAssignments(){ if(!can(Permission.MANAGE_ASSIGNMENTS)){showDenied("assignments");return;} if(assignments==null||profiles==null){notifications.error("Assignment storage is not configured.");return;} workspace.getChildren().setAll(new AssignmentsPage(assignments,profiles,new ClearanceService(assignments,profiles,documents,files),projectNames(),notifications)); }
    private void showReminders(){ if(!can(Permission.MANAGE_REMINDERS)){showDenied("reminders");return;} if(reminders!=null)workspace.getChildren().setAll(new RemindersPage(reminders, delivery, notifications, this::showDeliveryHistory)); }
    private void showDeliveryHistory(){ if(delivery!=null)workspace.getChildren().setAll(new DeliveryHistoryPage(new com.permitping.infrastructure.SqliteReminderDeliveryRepository(new com.permitping.infrastructure.Database(java.nio.file.Path.of(System.getProperty("user.dir"),"data","permitping.db"))))); }
    private void showArchived(){ if(!can(Permission.VIEW_DOCUMENTS)){showDenied("archived documents");return;} workspace.getChildren().setAll(new ArchivedPage(documents,notifications,can(Permission.MANAGE_DOCUMENTS))); }
    private void showSettings(){ if(!can(Permission.MANAGE_SETTINGS)){showDenied("system settings");return;} workspace.getChildren().setAll(new SettingsPage(java.nio.file.Path.of(System.getProperty("user.dir"),"data"),notifications,this::showAudit,audit,can(Permission.MANAGE_USERS)&&auth!=null&&authenticatedUser!=null?this::showUsers:null,this::prepareForRestore,this::restoreCompleted,this::restoreFailed)); }
    private void showUsers(){ if(!can(Permission.MANAGE_USERS)||auth==null||authenticatedUser==null){showDenied("user management");return;} workspace.getChildren().setAll(new UserManagementPage(auth,authenticatedUser,notifications)); }
    private void showAudit(){ if(!can(Permission.VIEW_AUDIT)){showDenied("activity history");return;} workspace.getChildren().setAll(new AuditPage(audit)); }
    private void edit(Document document){form.show(document,d->refresh());} private void renew(Document document){form.showRenewal(document,d->refresh());}
    private void archive(Document document){Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,"Archive '"+document.name()+"'? It will leave the active register but can be restored later.",ButtonType.CANCEL,ButtonType.OK);confirm.setHeaderText("Archive document");confirm.showAndWait().filter(b->b==ButtonType.OK).ifPresent(b->{try{documents.archive(document.id());details.show(null);refresh();notifications.info("Document archived. You can restore it from Archived.");}catch(RuntimeException ex){notifications.error("Document could not be archived: "+ex.getMessage());}});}
    private void refresh(){long selectedId=table.selectedDocument()==null?0:table.selectedDocument().id();dashboard.refresh();readiness.refresh();table.refresh();if(selectedId>0&&!table.selectById(selectedId))details.show(null);if(projects!=null){projects.getItems().setAll("All projects");projects.getItems().addAll(projectNames());}boolean empty=documents.list().isEmpty();emptyState.setVisible(empty);emptyState.setManaged(empty);table.setVisible(!empty);table.setManaged(!empty);}
    private List<String> projectNames(){return documents.list().stream().map(Document::project).filter(p->p!=null&&!p.isBlank()).map(String::trim).collect(java.util.stream.Collectors.toMap(p->p.toLowerCase(Locale.ROOT),p->p,(first,ignored)->first,LinkedHashMap::new)).values().stream().sorted().toList();}
    private boolean can(Permission permission){return permissions.contains(permission);}
    private boolean sessionValid(){if(auth==null||authenticatedUser==null)return true;try{AuthUser current=auth.validateSession(authenticatedUser);if(permissionsChanged(current, authenticatedUser)){notifications.info("Your permissions changed. Please sign in again.");signOut();return false;}return true;}catch(SecurityException ex){notifications.error("Your session has expired. Please sign in again.");signOut();return false;}}
    public static boolean permissionsChanged(AuthUser current, AuthUser authenticated){return !current.role().equals(authenticated.role())||!current.role().permissions().equals(authenticated.role().permissions());}
    private void signOut(){stopReminderScheduler();if(onLogout!=null)onLogout.run();}
    private void restoreCompleted(){stopReminderScheduler();if(onLogout!=null)onLogout.run();else notifications.info("Backup restored. Restart PermitPing to reload the workspace.");}
    private void prepareForRestore(){stopReminderScheduler();if(reminderDeliveryRunning.get())throw new IllegalStateException("A reminder delivery is still running. Wait for it to finish, then try restoring again.");}
    private void restoreFailed(){if(delivery!=null&&reminderScheduler==null)startReminderScheduler();}
    private void showDenied(String area){workspace.getChildren().setAll(accessDenied(area));}
    private VBox accessDenied(String area){Label title=new Label("Access restricted");title.getStyleClass().add("title");Label copy=new Label("Your role does not have permission to view "+area+".");copy.getStyleClass().add("muted");VBox box=new VBox(8,title,copy);box.getStyleClass().addAll("content-shell","empty-state");box.setPadding(new Insets(34));return box;}
    private void showProjectDocuments(String project){showDashboard();table.setQuickFilter("By project");if(projects!=null){projects.setValue(project);projects.setVisible(true);}filterLabel.setText("Showing project: "+project);}
    private void showPendingReminders(){
        if (delivery != null) {
            runReminderDelivery("permitping-startup-reminder-delivery");
            return;
        }
        notifications.reminders(reminders.pending());
    }
    private void startReminderScheduler(){
        reminderScheduler = Executors.newSingleThreadScheduledExecutor(task -> { Thread worker = new Thread(task, "permitping-reminder-scheduler"); worker.setDaemon(true); return worker; });
        reminderScheduler.scheduleWithFixedDelay(() -> runReminderDelivery("permitping-scheduled-reminder-delivery"), 15, 15, TimeUnit.MINUTES);
        sceneProperty().addListener((observable, oldScene, newScene) -> watchReminderSchedulerWindow(newScene));
        watchReminderSchedulerWindow(getScene());
    }
    private void watchReminderSchedulerWindow(javafx.scene.Scene scene){
        if (scene == null) return;
        scene.windowProperty().addListener((observable, oldWindow, newWindow) -> { if (newWindow != null) newWindow.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> stopReminderScheduler()); });
        if (scene.getWindow() != null) scene.getWindow().addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> stopReminderScheduler());
    }
    private void stopReminderScheduler(){ if (reminderScheduler != null) { reminderScheduler.shutdownNow(); reminderScheduler = null; } }
    private void runReminderDelivery(String threadName){
        if (delivery == null || !reminderDeliveryRunning.compareAndSet(false, true)) return;
        Thread worker = new Thread(() -> {
            try {
                java.util.List<com.permitping.domain.ReminderDelivery> results = delivery.sendPending();
                javafx.application.Platform.runLater(() -> notifyDeliveryResults(results));
            } catch (RuntimeException ex) {
                javafx.application.Platform.runLater(() -> notifications.error("Automatic reminder delivery failed: " + (ex.getMessage() == null ? "Unknown error" : ex.getMessage())));
            } finally { reminderDeliveryRunning.set(false); }
        }, threadName);
        worker.setDaemon(true);
        worker.start();
    }
    private void notifyDeliveryResults(java.util.List<com.permitping.domain.ReminderDelivery> results) {
        long sent = results.stream().filter(d -> d.status() == com.permitping.domain.DeliveryStatus.SENT).count();
        long failed = results.stream().filter(d -> d.status() == com.permitping.domain.DeliveryStatus.FAILED).count();
        if (sent > 0) notifications.info(sent + " contractor reminder(s) sent.");
        if (failed > 0) notifications.error(failed + " contractor reminder(s) failed. Review delivery history.");
    }
}
