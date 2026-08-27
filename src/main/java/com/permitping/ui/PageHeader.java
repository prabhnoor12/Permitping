package com.permitping.ui;

import javafx.scene.control.Label;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import javafx.scene.layout.FlowPane;

/** Shared heading treatment for full workspace pages. */
public final class PageHeader extends VBox {
    public PageHeader(String title, String description) {
        this(title, description, null);
    }
    public PageHeader(String title, String description, Node actions) {
        setSpacing(5);
        getStyleClass().add("page-header");
        Label heading = new Label(title);
        heading.getStyleClass().add("title");
        Label copy = new Label(description);
        copy.getStyleClass().add("muted");
        if (actions == null) getChildren().addAll(heading, copy);
        else { FlowPane row = new FlowPane(18, 10, new VBox(5, heading, copy), actions); row.setPrefWrapLength(760); getChildren().add(row); }
    }
}
