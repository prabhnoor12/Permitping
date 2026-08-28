package com.permitping.ui;

import com.permitping.application.ProfileService;
import com.permitping.application.DocumentService;
import com.permitping.application.AssignmentService;
import com.permitping.application.NotificationSubscriptionService;
import com.permitping.domain.ComplianceStatus;
import com.permitping.domain.NotificationChannel;
import com.permitping.domain.Profile;
import com.permitping.domain.ProfileType;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class ProfilesPage extends BorderPane {
    private final ProfileService profiles;
    private final DocumentService documents;
    private final AssignmentService assignments;
    private final NotificationService notifications;
    private final NotificationSubscriptionService subscriptions;
    private final ListView<Profile> list = new ListView<>();
    private final Label detail = new Label("Select a profile to see its details.");
    private final CheckBox showArchived = new CheckBox("Show archived");
    private final TextField search = new TextField();

    public ProfilesPage(ProfileService profiles, NotificationService notifications) {
        this(profiles, null, null, notifications, null, true);
    }

    public ProfilesPage(ProfileService profiles, DocumentService documents, AssignmentService assignments, NotificationService notifications) {
        this(profiles, documents, assignments, notifications, null, true);
    }

    public ProfilesPage(ProfileService profiles, DocumentService documents, AssignmentService assignments, NotificationService notifications, boolean canManage) {
        this(profiles, documents, assignments, notifications, null, canManage);
    }

    public ProfilesPage(ProfileService profiles, DocumentService documents, AssignmentService assignments, NotificationService notifications, NotificationSubscriptionService subscriptions, boolean canManage) {
        this.profiles = profiles;
        this.documents = documents;
        this.assignments = assignments;
        this.notifications = notifications;
        this.subscriptions = subscriptions;
        getStyleClass().add("page-shell");
        setPadding(new Insets(30, 34, 30, 34));
        setTop(new PageHeader("Profiles", "Keep people and companies in one reusable directory for documents and assignments."));
        search.setPromptText("Search profiles..."); search.textProperty().addListener((obs, old, value) -> refresh());
        list.setMinHeight(220);
        VBox.setVgrow(list, Priority.ALWAYS);
        list.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(Profile item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name() + "  ·  " + item.type().label()
                    + (item.email() == null || item.email().isBlank() ? "" : "  ·  " + item.email()));
            }
        });
        list.setCellFactory(view -> new ListCell<>() {
            private final Label avatar = new Label(); private final Label name = new Label(); private final Label meta = new Label(); private final Region spacer = new Region(); private final HBox row = new HBox(12, avatar, new VBox(3, name, meta), spacer);
            { HBox.setHgrow(spacer, Priority.ALWAYS); avatar.getStyleClass().add("profile-avatar"); name.getStyleClass().add("profile-name"); meta.getStyleClass().add("helper-text"); }
            @Override protected void updateItem(Profile item, boolean empty) { super.updateItem(item, empty); if (empty || item == null) { setGraphic(null); setText(null); return; } String[] words=item.name().trim().split("\\s+"); avatar.setText(words[0].substring(0,1).toUpperCase()); name.setText(item.name()); meta.setText(item.type().label() + (item.email()==null||item.email().isBlank()?"":" · "+item.email())); setGraphic(row); setText(null); }
        });
        Button add = new Button("+ Add profile");
        add.getStyleClass().add("primary");
        add.setOnAction(event -> addProfile());
        Button edit = new Button("Edit selected"); edit.getStyleClass().add("secondary"); edit.setDisable(true);
        Button archive = new Button("Archive selected"); archive.getStyleClass().add("danger"); archive.setDisable(true);
        Button restore = new Button("Restore selected"); restore.getStyleClass().add("secondary"); restore.setDisable(true);
        Button remove = new Button("Delete selected"); remove.getStyleClass().add("danger"); remove.setDisable(true);
        Button subscriptionsButton = new Button("Manage subscriptions"); subscriptionsButton.getStyleClass().add("secondary"); subscriptionsButton.setDisable(true);
        for (Button action : new Button[]{add, edit, archive, restore, remove, subscriptionsButton}) { action.setVisible(canManage); action.setManaged(canManage); }
        list.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> { boolean disabled = value == null; edit.setDisable(disabled); archive.setDisable(disabled || value.archived()); restore.setDisable(disabled || !value.archived()); remove.setDisable(disabled); subscriptionsButton.setDisable(disabled || subscriptions == null); showDetail(value); });
        edit.setOnAction(event -> editProfile(list.getSelectionModel().getSelectedItem()));
        archive.setOnAction(event -> archiveProfile(list.getSelectionModel().getSelectedItem()));
        restore.setOnAction(event -> restoreProfile(list.getSelectionModel().getSelectedItem()));
        remove.setOnAction(event -> deleteProfile(list.getSelectionModel().getSelectedItem()));
        subscriptionsButton.setOnAction(event -> manageSubscriptions(list.getSelectionModel().getSelectedItem()));
        showArchived.setOnAction(event -> refresh());
        Label hint = new Label("Archive profiles that should no longer be used. Delete is reserved for profiles with no linked records.");
        hint.getStyleClass().add("helper-text");
        detail.setWrapText(true); detail.getStyleClass().add("helper-text");
        FlowPane profileToolbar = new FlowPane(10, 8, search, showArchived); profileToolbar.getStyleClass().add("toolbar");
        FlowPane profileActions = new FlowPane(10, 8, add, edit, archive, restore, remove, subscriptionsButton); profileActions.getStyleClass().add("toolbar");
        VBox card = new VBox(14, profileToolbar, list, profileActions, hint, new Label("Profile details"), detail);
        card.getStyleClass().addAll("card", "profiles-card");
        setCenter(card);
        refresh();
    }

    public void refresh() { Profile selected = list.getSelectionModel().getSelectedItem(); String query = search.getText() == null ? "" : search.getText().trim().toLowerCase(); list.setItems(FXCollections.observableArrayList((showArchived.isSelected() ? profiles.archived() : profiles.list()).stream().filter(profile -> query.isBlank() || contains(profile.name(), query) || contains(profile.email(), query) || contains(profile.phone(), query)).toList())); if (selected != null) list.getSelectionModel().select(selected); showDetail(list.getSelectionModel().getSelectedItem()); }
    private boolean contains(String value, String query) { return value != null && value.toLowerCase().contains(query); }

    private void showDetail(Profile profile) { if (profile == null) { detail.setText("Select a profile to see its details."); return; } var linked = documents == null ? java.util.List.<com.permitping.domain.Document>of() : documents.list().stream().filter(document -> document.holderProfileId() == profile.id() || (document.holderProfileId() == 0 && contains(document.holder(), profile.name().toLowerCase()))).toList(); long expired = linked.stream().filter(document -> document.status(java.time.Clock.systemDefaultZone()) == ComplianceStatus.EXPIRED).count(); long assignmentCount = assignments == null ? 0 : assignments.list().stream().filter(assignment -> assignment.profileId() == profile.id()).count(); String health = documents == null ? "Not available" : linked.isEmpty() ? "No evidence linked" : expired > 0 ? "Needs attention" : "Evidence current"; String subscription = subscriptions == null ? "Not configured" : "Email " + subscriptionStatus(profile.id(), NotificationChannel.EMAIL) + ", SMS " + subscriptionStatus(profile.id(), NotificationChannel.SMS); detail.setText("Name: " + profile.name() + "\nType: " + profile.type().label() + "\nEmail: " + blank(profile.email()) + "\nPhone: " + blank(profile.phone()) + "\nStatus: " + (profile.archived() ? "Archived" : "Active") + "\nReminder subscriptions: " + subscription + "\nCompliance health: " + health + "\nLinked documents: " + linked.size() + " (" + expired + " expired)" + "\nAssignments: " + assignmentCount + "\nNotes: " + blank(profile.notes())); }
    private String subscriptionStatus(long profileId, NotificationChannel channel) { return subscriptions.isSubscribed(profileId, channel) ? "subscribed" : "not subscribed"; }
    private String blank(String value) { return value == null || value.isBlank() ? "Not provided" : value; }

    private void addProfile() {
        Dialog<Profile> dialog = profileDialog("Add profile", new Profile(0, "", ProfileType.COMPANY, "", "", ""));
        dialog.showAndWait().ifPresent(profile -> save(profile));
    }
    private void save(Profile profile) { try { profiles.save(profile); refresh(); } catch (RuntimeException ex) { notifications.error(message(ex)); } }
    private void editProfile(Profile existing) {
        if (existing == null) return;
        Dialog<Profile> dialog = profileDialog("Edit profile", existing);
        dialog.showAndWait().ifPresent(profile -> { try { profiles.save(new Profile(existing.id(), profile.name(), profile.type(), profile.email(), profile.phone(), profile.notes(), existing.archived(), profile.notificationsEnabled(), profile.notificationChannel())); refresh(); } catch (RuntimeException ex) { notifications.error(message(ex)); } });
    }
    private Dialog<Profile> profileDialog(String title, Profile existing) {
        Dialog<Profile> dialog = new Dialog<>(); dialog.setTitle(title); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        TextField name = new TextField(existing.name()); ComboBox<ProfileType> type = new ComboBox<>(FXCollections.observableArrayList(ProfileType.values())); type.setValue(existing.type()); TextField email = new TextField(existing.email()); TextField phone = new TextField(existing.phone()); phone.setPromptText("+15551234567 for SMS"); CheckBox enabled = new CheckBox("Allow reminder delivery"); enabled.setSelected(existing.notificationsEnabled()); ComboBox<com.permitping.domain.NotificationChannel> channel = new ComboBox<>(FXCollections.observableArrayList(com.permitping.domain.NotificationChannel.values())); channel.setValue(existing.notificationChannel());
        GridPane grid = new GridPane(); grid.setHgap(14); grid.setVgap(12); grid.setPadding(new Insets(18)); grid.addRow(0,new Label("Name"),name); grid.addRow(1,new Label("Type"),type); grid.addRow(2,new Label("Email"),email); grid.addRow(3,new Label("Phone"),phone); grid.addRow(4,new Label("Notifications"),enabled); grid.addRow(5,new Label("Preferred channel"),channel); channel.disableProperty().bind(enabled.selectedProperty().not()); dialog.getDialogPane().setContent(grid);
        dialog.setOnShown(event -> dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, action -> { try { profiles.validate(new Profile(existing.id(), name.getText(), type.getValue(), email.getText(), phone.getText(), existing.notes(), existing.archived(), enabled.isSelected(), channel.getValue())); } catch (RuntimeException ex) { action.consume(); notifications.error(message(ex)); } }));
        dialog.setResultConverter(button -> button == ButtonType.OK ? new Profile(existing.id(),name.getText(),type.getValue(),email.getText(),phone.getText(),existing.notes(),existing.archived(),enabled.isSelected(),channel.getValue()) : null); return dialog;
    }
    private void manageSubscriptions(Profile profile) {
        if (profile == null || subscriptions == null) return;
        CheckBox email = new CheckBox("Email reminders"); email.setSelected(subscriptions.isSubscribed(profile.id(), NotificationChannel.EMAIL));
        CheckBox sms = new CheckBox("SMS reminders"); sms.setSelected(subscriptions.isSubscribed(profile.id(), NotificationChannel.SMS));
        CheckBox attestation = new CheckBox("I have recorded the recipient's express consent for every channel being subscribed.");
        TextField source = new TextField(); source.setPromptText("Consent or unsubscribe source (for example, signed form or recipient request)");
        Label help = new Label("Uncheck a channel to suppress it. Re-subscribe only after new express consent. SMS recipients must be able to reply STOP."); help.setWrapText(true); help.getStyleClass().add("helper-text");
        VBox content = new VBox(12, new Label("Subscriptions are separate from the profile's delivery toggle."), email, sms, attestation, source, help); content.setPadding(new Insets(18)); content.setPrefWidth(520);
        Dialog<ButtonType> dialog = new Dialog<>(); dialog.setTitle("Manage reminder subscriptions"); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK); dialog.getDialogPane().setContent(content);
        dialog.setOnShown(event -> dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, action -> {
            boolean subscribing = (email.isSelected() && !subscriptions.isSubscribed(profile.id(), NotificationChannel.EMAIL)) || (sms.isSelected() && !subscriptions.isSubscribed(profile.id(), NotificationChannel.SMS));
            try {
                if (subscribing && !attestation.isSelected()) throw new IllegalArgumentException("Confirm that express recipient consent has been recorded before subscribing");
                String normalizedSource = source.getText() == null ? "" : source.getText().trim();
                boolean emailChanged = email.isSelected() != subscriptions.isSubscribed(profile.id(), NotificationChannel.EMAIL);
                boolean smsChanged = sms.isSelected() != subscriptions.isSubscribed(profile.id(), NotificationChannel.SMS);
                if ((emailChanged || smsChanged) && normalizedSource.isBlank()) throw new IllegalArgumentException("Record how the recipient requested this change");
                if (email.isSelected() && (profile.email() == null || !profile.email().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) throw new IllegalArgumentException("A valid email address is required before subscribing to email reminders");
                if (sms.isSelected() && (profile.phone() == null || !profile.phone().matches("\\+[1-9]\\d{7,14}"))) throw new IllegalArgumentException("Use a valid international phone number before subscribing to SMS reminders");
                boolean emailSubscribed = subscriptions.isSubscribed(profile.id(), NotificationChannel.EMAIL); boolean smsSubscribed = subscriptions.isSubscribed(profile.id(), NotificationChannel.SMS);
                if (email.isSelected() != emailSubscribed) { if (email.isSelected()) subscriptions.subscribe(profile.id(), NotificationChannel.EMAIL, normalizedSource); else subscriptions.unsubscribe(profile.id(), NotificationChannel.EMAIL, normalizedSource); }
                if (sms.isSelected() != smsSubscribed) { if (sms.isSelected()) subscriptions.subscribe(profile.id(), NotificationChannel.SMS, normalizedSource); else subscriptions.unsubscribe(profile.id(), NotificationChannel.SMS, normalizedSource); }
            } catch (RuntimeException ex) { action.consume(); notifications.error(message(ex)); }
        }));
        dialog.showAndWait().ifPresent(ignored -> { refresh(); notifications.info("Reminder subscriptions updated."); });
    }
    private void archiveProfile(Profile profile) { if(profile == null)return; Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,"Archived profiles stop appearing in holder and assignee pickers.",ButtonType.CANCEL,ButtonType.OK); confirm.setHeaderText("Archive profile"); confirm.showAndWait().filter(b->b==ButtonType.OK).ifPresent(b->{try{profiles.archive(profile.id());refresh();}catch(RuntimeException ex){notifications.error(message(ex));}}); }
    private void restoreProfile(Profile profile) { if (profile == null) return; try { profiles.restore(profile.id()); refresh(); } catch (RuntimeException ex) { notifications.error(message(ex)); } }
    private void deleteProfile(Profile profile) { if(profile == null)return; Alert confirm=new Alert(Alert.AlertType.CONFIRMATION,"This permanently removes the profile only when no documents or assignments are linked to it. Archive it instead to preserve those links.",ButtonType.CANCEL,ButtonType.OK); confirm.setHeaderText("Delete profile"); confirm.showAndWait().filter(b->b==ButtonType.OK).ifPresent(b->{try{profiles.delete(profile.id());refresh();}catch(RuntimeException ex){notifications.error(ex.getMessage()==null?"Profile could not be deleted. Archive it instead if it is still in use.":ex.getMessage());}}); }
    private String message(RuntimeException ex) { return ex.getMessage() == null ? "The profile could not be saved." : ex.getMessage(); }
}
