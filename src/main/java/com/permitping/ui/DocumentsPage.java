package com.permitping.ui;

import com.permitping.application.DocumentService;
import com.permitping.application.DocumentVersionService;
import com.permitping.application.ProfileService;
import com.permitping.domain.Document;
import com.permitping.infrastructure.FileStorage;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class DocumentsPage extends BorderPane {
    private final DocumentService documents;
    private final DocumentTableView table;
    private final DocumentDetailView details;
    private final DocumentForm form;
    private final NotificationService notifications;

    public DocumentsPage(DocumentService documents, ProfileService profiles, FileStorage files,
                         DocumentVersionService versions, NotificationService notifications) {
        this(documents, profiles, files, versions, notifications, null, null);
    }
    public DocumentsPage(DocumentService documents, ProfileService profiles, FileStorage files,
                         DocumentVersionService versions, NotificationService notifications, Runnable openSearch) {
        this(documents, profiles, files, versions, notifications, openSearch, null);
    }
    public DocumentsPage(DocumentService documents, ProfileService profiles, FileStorage files,
                         DocumentVersionService versions, NotificationService notifications, Runnable openSearch, Runnable openReports) {
        this.documents = documents;
        this.notifications = notifications;
        this.form = new DocumentForm(documents, profiles, files, versions, notifications);
        this.details = new DocumentDetailView(files, versions, notifications, profiles, this::edit, this::renew, this::archive);
        this.table = new DocumentTableView(documents, files, profiles, details::show);
        table.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<Document>) change -> { var selected = table.selectedDocuments(); if (selected.size() > 1) details.showSelectionSummary(selected.size()); else if (selected.size() == 1) details.show(selected.get(0)); });
        getStyleClass().add("page-shell");
        setPadding(new Insets(30, 34, 30, 34));
        PageHeader header = new PageHeader("All documents", "Search, review, and maintain every active compliance record.");
        ComboBox<String> filter = new ComboBox<>(javafx.collections.FXCollections.observableArrayList("All", "Expired", "Due this week", "Due this month", "Missing file"));
        Label summary = new Label(); summary.getStyleClass().add("filter-summary");
        filter.setValue("All"); filter.setOnAction(event -> { table.setQuickFilter(filter.getValue()); updateSummary(filter, summary); });
        table.setResultsChanged(() -> updateSummary(filter, summary));
        Button clearFilters = new Button("Clear all filters"); clearFilters.getStyleClass().add("secondary"); clearFilters.setOnAction(event -> { filter.setValue("All"); table.clearSearch(); table.setQuickFilter("All"); updateSummary(filter, summary); });
        FlowPane filterBar = new FlowPane(10, 8, new Label("Quick filter"), filter, clearFilters, summary); filterBar.getStyleClass().add("filter-chips");
        setTop(new VBox(10, header, filterBar));
        Button add = new Button("+ Add document"); add.getStyleClass().add("primary"); add.setOnAction(event -> form.show(null, saved -> refresh()));
        Button archive = new Button("Archive selected"); archive.getStyleClass().add("danger"); archive.setDisable(true); table.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<Document>) change -> archive.setDisable(table.selectedDocuments().isEmpty())); archive.setOnAction(event -> archiveSelected());
        Button search = new Button("Global search"); search.getStyleClass().add("secondary"); if (openSearch != null) search.setOnAction(event -> openSearch.run());
        Button reports = new Button("Reports"); reports.getStyleClass().add("secondary"); if (openReports != null) reports.setOnAction(event -> openReports.run());
        FlowPane actionButtons = new FlowPane(10, 8, add, archive, search, reports); actionButtons.getStyleClass().add("toolbar");
        SplitPane split = new SplitPane(table, details); split.setDividerPositions(.68); split.setPadding(new Insets(0)); split.getStyleClass().add("document-split"); details.setPrefWidth(315); split.widthProperty().addListener((observable, oldWidth, newWidth) -> split.setOrientation(newWidth.doubleValue() < 760 ? Orientation.VERTICAL : Orientation.HORIZONTAL)); split.setOrientation(Orientation.HORIZONTAL);
        VBox card = new VBox(12, table.toolbar(this::refresh), actionButtons, split); card.getStyleClass().add("card"); VBox.setVgrow(split, Priority.ALWAYS);
        setCenter(card); refresh(); updateSummary(filter, summary);
    }

    private void refresh() {
        long selectedId = table.selectedDocument() == null ? 0 : table.selectedDocument().id();
        table.refresh();
        if (selectedId > 0 && !table.selectById(selectedId)) details.show(null);
    }
    private void edit(Document document) { form.show(document, saved -> refresh()); }
    private void renew(Document document) { form.showRenewal(document, saved -> refresh()); }
    private void archive(Document document) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Archive '" + document.name() + "'? It can be restored later.", ButtonType.CANCEL, ButtonType.OK);
        confirm.setHeaderText("Archive document");
        confirm.showAndWait().filter(button -> button == ButtonType.OK).ifPresent(button -> { documents.archive(document.id()); details.show(null); refresh(); });
    }
    private void archiveSelected() {
        var selected = table.selectedDocuments(); if (selected.isEmpty()) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Archive " + selected.size() + " selected documents? They can be restored later.", ButtonType.CANCEL, ButtonType.OK);
        confirm.setHeaderText("Archive selected documents");
        confirm.showAndWait().filter(button -> button == ButtonType.OK).ifPresent(button -> { try { selected.forEach(document -> documents.archive(document.id())); details.show(null); refresh(); } catch (RuntimeException ex) { notifications.error("Some documents could not be archived: " + ex.getMessage()); } });
    }
    private void updateSummary(ComboBox<String> filter, Label summary) { summary.setText("Showing " + table.filteredCount() + " of " + table.totalCount() + " documents" + ("All".equals(filter.getValue()) ? "" : " · Filter: " + filter.getValue())); }
}
