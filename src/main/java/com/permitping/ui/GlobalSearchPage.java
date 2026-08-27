package com.permitping.ui;

import com.permitping.application.AssignmentService;
import com.permitping.application.DocumentService;
import com.permitping.application.ProfileService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import java.util.prefs.Preferences;

public final class GlobalSearchPage extends BorderPane {
    private final DocumentService documents;
    private final ProfileService profiles;
    private final AssignmentService assignments;
    private final TextField query = new TextField();
    private final ListView<String> results = new ListView<>();
    private final ComboBox<String> saved = new ComboBox<>();
    private final Label resultSummary = new Label();
    private final Preferences preferences = Preferences.userNodeForPackage(GlobalSearchPage.class);

    public GlobalSearchPage(DocumentService documents, ProfileService profiles, AssignmentService assignments) {
        this.documents = documents; this.profiles = profiles; this.assignments = assignments;
        getStyleClass().add("page-shell"); setPadding(new Insets(30, 34, 30, 34));
        setTop(new PageHeader("Global search", "Find documents, profiles, projects, and assignments from one place."));
        query.setPromptText("Search by name, contact, project, or assignment..."); query.setAccessibleText("Search workspace");
        query.textProperty().addListener((obs, oldValue, newValue) -> refresh());
        Button clear = new Button("Clear"); clear.getStyleClass().add("secondary"); clear.setOnAction(event -> query.clear());
        Button save = new Button("Save filter"); save.getStyleClass().add("secondary"); save.setOnAction(event -> saveFilter());
        saved.setPromptText("Saved filters"); saved.setOnAction(event -> { if (saved.getValue() != null) query.setText(saved.getValue()); }); loadSaved();
        FlowPane toolbar = new FlowPane(10, 10, query, saved, save, clear); toolbar.getStyleClass().add("toolbar"); toolbar.setPrefWrapLength(760); query.setPrefWidth(420); query.setMinWidth(220);
        Label title = new Label("Search workspace"); title.getStyleClass().add("section-title");
        Label hint = new Label("Search by document name, profile contact, project, or assignment."); hint.getStyleClass().add("helper-text");
        resultSummary.getStyleClass().add("helper-text"); results.setPlaceholder(new Label("Start typing to search your workspace.")); results.setAccessibleText("Search results");
        VBox card = new VBox(12, title, hint, toolbar, resultSummary, results); VBox.setVgrow(results, Priority.ALWAYS); card.getStyleClass().addAll("card", "search-card"); setCenter(card); refresh();
    }

    private void refresh() {
        String q = query.getText() == null ? "" : query.getText().trim().toLowerCase();
        if (q.isBlank()) { results.getItems().clear(); resultSummary.setText("Search across your workspace"); return; }
        var matches = FXCollections.<String>observableArrayList();
        documents.list().stream().filter(d -> contains(d.name(), q) || contains(d.type(), q) || contains(d.holder(), q) || contains(d.project(), q)).forEach(d -> matches.add("DOCUMENT  |  " + d.name() + " — " + d.holder() + " / " + blank(d.project())));
        if (profiles != null) profiles.list().stream().filter(p -> contains(p.name(), q) || contains(p.email(), q) || contains(p.phone(), q)).forEach(p -> matches.add("PROFILE  |  " + p.name() + " — " + p.type().label()));
        if (assignments != null) assignments.list().stream().filter(a -> contains(a.project(), q) || contains(Long.toString(a.profileId()), q)).forEach(a -> matches.add("ASSIGNMENT  |  " + a.project() + " — profile #" + a.profileId()));
        results.setItems(matches); resultSummary.setText(matches.size() + " result" + (matches.size() == 1 ? "" : "s") + " for ‘" + query.getText().trim() + "’");
    }
    private boolean contains(String value, String q) { return value != null && value.toLowerCase().contains(q); }
    private String blank(String value) { return value == null || value.isBlank() ? "No project" : value; }
    private void loadSaved() { String raw = preferences.get("filters", ""); if (!raw.isBlank()) saved.getItems().setAll(raw.split("\\|")); }
    private void saveFilter() { String value = query.getText() == null ? "" : query.getText().trim(); if (value.isBlank() || saved.getItems().contains(value)) return; saved.getItems().add(value); preferences.put("filters", String.join("|", saved.getItems())); saved.setValue(value); }
}
