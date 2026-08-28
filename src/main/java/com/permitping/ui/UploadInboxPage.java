package com.permitping.ui;

import com.permitping.application.UploadRequestService;
import com.permitping.domain.*;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.LocalDate;

public final class UploadInboxPage extends BorderPane {
    private final UploadRequestService uploads;
    private final NotificationService notifications;
    private final Runnable back;
    private final ListView<UploadSubmission> list = new ListView<>();
    private final Label details = new Label("Select an upload to review it.");

    public UploadInboxPage(UploadRequestService uploads, NotificationService notifications) { this(uploads, notifications, null); }
    public UploadInboxPage(UploadRequestService uploads, NotificationService notifications, Runnable back) {
        this.uploads = uploads; this.notifications = notifications; this.back = back;
        getStyleClass().add("page-shell"); setPadding(new Insets(30, 34, 30, 34));
        Button backButton = new Button("Back to assignments"); backButton.getStyleClass().add("secondary"); backButton.setVisible(back != null); backButton.setManaged(back != null); if (back != null) backButton.setOnAction(e -> back.run());
        setTop(new VBox(10, new PageHeader("Upload review inbox", "Review subcontractor uploads before they become clearance evidence."), backButton));
        list.setPlaceholder(new Label("No pending uploads.")); list.setMinHeight(260);
        list.setCellFactory(view -> new ListCell<>() { @Override protected void updateItem(UploadSubmission item, boolean empty) { super.updateItem(item, empty); if (empty || item == null) { setText(null); setGraphic(null); return; } UploadRequest request = uploads.requestFor(item); setText(request.project() + "  ·  " + request.documentType() + "  ·  " + item.originalFilename()); } });
        Button accept = new Button("Accept selected"); accept.getStyleClass().add("primary"); accept.setDisable(true);
        Button reject = new Button("Reject selected"); reject.getStyleClass().add("danger"); reject.setDisable(true);
        list.getSelectionModel().selectedItemProperty().addListener((o, old, value) -> { boolean disabled = value == null; accept.setDisable(disabled); reject.setDisable(disabled); showDetails(value); });
        accept.setOnAction(e -> accept(list.getSelectionModel().getSelectedItem())); reject.setOnAction(e -> reject(list.getSelectionModel().getSelectedItem()));
        details.setWrapText(true); details.getStyleClass().add("helper-text");
        FlowPane actions = new FlowPane(10, 8, accept, reject); actions.getStyleClass().add("toolbar");
        VBox left = new VBox(12, list, actions); VBox detailCard = new VBox(10, new Label("Upload details"), details); detailCard.getStyleClass().add("card");
        SplitPane split = new SplitPane(left, detailCard); split.setDividerPositions(.65); VBox card = new VBox(14, split); card.getStyleClass().addAll("card", "assignments-card"); setCenter(card); refresh();
    }
    public void refresh() { list.setItems(FXCollections.observableArrayList(uploads.pendingSubmissions(200))); showDetails(list.getSelectionModel().getSelectedItem()); }
    private void showDetails(UploadSubmission submission) { if (submission == null) { details.setText("Select an upload to review it."); return; } UploadRequest request = uploads.requestFor(submission); details.setText("Project: " + request.project() + "\nDocument type: " + request.documentType() + "\nFilename: " + submission.originalFilename() + "\nSize: " + submission.sizeBytes() + " bytes\nSubmitted: " + submission.submittedAt() + "\nStatus: Pending review"); }
    private void accept(UploadSubmission submission) {
        if (submission == null) return;
        UploadRequest request = uploads.requestFor(submission);
        Dialog<ButtonType> dialog = new Dialog<>(); dialog.setTitle("Accept uploaded document"); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        TextField name = new TextField(request.documentType() + " - " + submission.originalFilename()); DatePicker expires = new DatePicker(LocalDate.now().plusYears(1)); TextField reviewer = new TextField("PermitPing reviewer"); TextField notes = new TextField(); notes.setPromptText("Optional review notes");
        GridPane grid = new GridPane(); grid.setHgap(14); grid.setVgap(12); grid.setPadding(new Insets(18)); grid.addRow(0, new Label("Document name"), name); grid.addRow(1, new Label("Expires on"), expires); grid.addRow(2, new Label("Reviewed by"), reviewer); grid.addRow(3, new Label("Notes"), notes); dialog.getDialogPane().setContent(grid);
        dialog.showAndWait().filter(value -> value == ButtonType.OK).ifPresent(value -> { try { uploads.accept(submission.id(), reviewer.getText(), name.getText(), expires.getValue(), notes.getText()); refresh(); notifications.info("Upload accepted and added to compliance evidence."); } catch (RuntimeException ex) { notifications.error(ex.getMessage() == null ? "Upload could not be accepted." : ex.getMessage()); } });
    }
    private void reject(UploadSubmission submission) {
        if (submission == null) return;
        TextInputDialog dialog = new TextInputDialog(); dialog.setTitle("Reject upload"); dialog.setHeaderText("Explain what the subcontractor must correct"); dialog.setContentText("Reason:");
        dialog.showAndWait().ifPresent(reason -> { try { uploads.reject(submission.id(), "PermitPing reviewer", reason); refresh(); notifications.info("Upload rejected. Send the reason to the subcontractor."); } catch (RuntimeException ex) { notifications.error(ex.getMessage() == null ? "Upload could not be rejected." : ex.getMessage()); } });
    }
}
