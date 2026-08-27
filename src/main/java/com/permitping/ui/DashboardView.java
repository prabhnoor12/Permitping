package com.permitping.ui;

import com.permitping.application.DocumentService;
import com.permitping.domain.ComplianceStatus;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

public final class DashboardView extends HBox {
    private final DocumentService service;
    private final Label total=value(), expiring=value(), expired=value();
    public DashboardView(DocumentService service) { this.service=service;setSpacing(14);getStyleClass().add("dashboard-metrics");getChildren().addAll(card("TOTAL DOCUMENTS",total,"#2f6fed"),card("EXPIRING IN 30 DAYS",expiring,"#d98a00"),card("EXPIRED",expired,"#d94b4b"));refresh(); }
    public void refresh(){total.setText(Long.toString(service.count()));expiring.setText(Long.toString(service.countBy(ComplianceStatus.EXPIRING_SOON)));expired.setText(Long.toString(service.countBy(ComplianceStatus.EXPIRED)));}
    private VBox card(String label,Label number,String color){Label l=new Label(label);l.getStyleClass().add("metric-label");number.setStyle("-fx-text-fill: "+color+";");VBox box=new VBox(8,l,number);box.getStyleClass().addAll("card","metric-card");box.setStyle("-fx-border-color: "+color+"; ");box.setPrefWidth(220);HBox.setHgrow(box,Priority.ALWAYS);return box;}
    private Label value(){Label l=new Label("0");l.getStyleClass().add("metric");return l;}
}
