package com.permitping.ui;

import com.permitping.application.ProjectReadinessService;
import com.permitping.application.RequirementTemplateService;
import com.permitping.domain.ProjectReadiness;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.util.function.Consumer;

public final class ProjectReadinessView extends VBox {
    private final ProjectReadinessService service;
    private final RequirementTemplateService templates;
    private final TableView<ProjectReadiness> table = new TableView<>();

    public ProjectReadinessView(ProjectReadinessService service, RequirementTemplateService templates, Consumer<String> onProjectSelected) {
        this.service = service; this.templates = templates; setSpacing(10);
        Label title = new Label("Project readiness"); title.getStyleClass().add("section-title");
        Label subtitle = new Label("Know which jobs are clear to start and what needs attention."); subtitle.getStyleClass().add("muted");
        buildColumns(); table.setPlaceholder(new Label("Add a project to a document to see readiness here."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS); table.setPrefHeight(190);
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, selected) -> {
            if (selected != null) onProjectSelected.accept(selected.project());
        });
        getChildren().addAll(title, subtitle, table); refresh();
    }

    public void refresh() { table.getItems().setAll(service.list()); }

    private void buildColumns() {
        table.getColumns().add(column("PROJECT", ProjectReadiness::project));
        table.getColumns().add(column("STATUS", readiness -> readiness.status().label()));
        table.getColumns().add(column("DOCUMENTS", readiness -> Integer.toString(readiness.documentCount())));
        if (templates != null) {
            TableColumn<ProjectReadiness, String> template = column("REQUIREMENT TEMPLATE", ProjectReadiness::templateName);
            template.setCellFactory(ignored -> new TableCell<>() {
                private final ComboBox<String> chooser = new ComboBox<>();
                { chooser.setMaxWidth(Double.MAX_VALUE); }
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) { setGraphic(null); return; }
                    ProjectReadiness readiness = getTableView().getItems().get(getIndex());
                    chooser.getItems().setAll("No template");
                    chooser.getItems().addAll(templates.list().stream().map(com.permitping.domain.RequirementTemplate::name).toList());
                    chooser.setValue(item);
                    chooser.setOnAction(event -> {
                        templates.list().stream().filter(t -> t.name().equals(chooser.getValue())).findFirst()
                            .ifPresent(t -> { templates.assign(readiness.project(), t.id()); refresh(); });
                    });
                    setGraphic(chooser); setText(null);
                }
            });
            table.getColumns().add(template);
        }
        table.getColumns().add(column("WHAT NEEDS ATTENTION", ProjectReadiness::issueSummary));
    }

    private TableColumn<ProjectReadiness, String> column(String title,
            java.util.function.Function<ProjectReadiness, String> value) {
        TableColumn<ProjectReadiness, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        if (title.equals("STATUS")) column.setCellFactory(ignored -> new TableCell<>() {
            private final Label badge = new Label();
            { badge.getStyleClass().add("status"); }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
                badge.setText(item); badge.getStyleClass().removeAll("status-current", "status-expiring", "status-expired");
                badge.getStyleClass().add(switch (item) { case "Ready" -> "status-current"; case "At Risk" -> "status-expiring"; default -> "status-expired"; });
                setGraphic(badge); setText(null);
            }
        });
        return column;
    }
}
