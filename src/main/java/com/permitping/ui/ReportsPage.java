package com.permitping.ui;

import com.permitping.application.*;
import com.permitping.domain.*;
import com.permitping.infrastructure.FileStorage;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.io.File;
import java.time.Clock;

public final class ReportsPage extends VBox {
    private final DocumentService documents; private final AssignmentService assignments; private final ProfileService profiles; private final FileStorage files; private final NotificationService notifications; private final Label summary=new Label();
    public ReportsPage(DocumentService documents, AssignmentService assignments, ProfileService profiles, FileStorage files, NotificationService notifications) {
        this.documents=documents;this.assignments=assignments;this.profiles=profiles;this.files=files;this.notifications=notifications;getStyleClass().add("page-shell");setSpacing(18);setPadding(new Insets(30,34,30,34));getChildren().add(new PageHeader("Compliance reports","Export the current compliance picture for project reviews and record keeping."));
        VBox card=new VBox(16);card.getStyleClass().addAll("card","reports-card");Label title=new Label("Export center");title.getStyleClass().add("section-title");Label intro=new Label("Choose a report to download. Each export is a CSV file that can be shared with project teams or opened in a spreadsheet.");intro.setWrapText(true);intro.getStyleClass().add("helper-text");summary.getStyleClass().add("report-summary");card.getChildren().addAll(title,intro,new Separator(),new Label("Current summary"),summary);
        FlowPane options=new FlowPane(12,12);options.getStyleClass().add("report-options");options.getChildren().addAll(reportOption("Active documents","Every active document and its current compliance status.","Export active documents",false),reportOption("Expiring documents","Documents that are expiring or already expired.","Export expiring documents",false),reportOption("Assignment readiness","Assignment status and the compliance issues blocking readiness.","Export assignment readiness",true));card.getChildren().add(options);getChildren().add(card);refresh();
    }
    private VBox reportOption(String title,String description,String action,boolean assignmentReport){Label heading=new Label(title);heading.getStyleClass().add("section-title");Label copy=new Label(description);copy.setWrapText(true);copy.getStyleClass().add("helper-text");Button button=new Button(action);button.getStyleClass().add(assignmentReport?"primary":"secondary");button.setMaxWidth(Double.MAX_VALUE);button.setOnAction(e->{if(assignmentReport)exportAssignments();else if(action.toLowerCase().contains("active"))exportDocuments(documents.list(),"active-documents.csv");else exportDocuments(documents.list().stream().filter(d->d.status(Clock.systemDefaultZone())!=ComplianceStatus.CURRENT).toList(),"expiring-documents.csv");});VBox option=new VBox(10,heading,copy,button);option.getStyleClass().add("report-option");option.setPrefWidth(270);option.setMinWidth(220);return option;}
    private void refresh(){long expired=documents.countBy(ComplianceStatus.EXPIRED),expiring=documents.countBy(ComplianceStatus.EXPIRING_SOON);summary.setText(documents.count()+" documents  ·  "+expiring+" expiring  ·  "+expired+" expired  ·  "+(assignments==null?0:assignments.list().size())+" assignments");}
    private void exportDocuments(java.util.List<Document> data,String name){File file=choose(name);if(file==null)return;try{new ExportService().exportCsv(data,file.toPath());notifications.info("Report exported to "+file.getName());}catch(Exception ex){notifications.error("Report export failed: "+ex.getMessage());}}
    private void exportAssignments(){if(assignments==null){notifications.error("Assignment storage is not configured.");return;}File file=choose("assignment-readiness.csv");if(file==null)return;try{var clearances=new ClearanceService(assignments,profiles,documents,files).list();new ExportService().exportAssignmentsCsv(clearances,file.toPath());notifications.info("Assignment report exported to "+file.getName());}catch(Exception ex){notifications.error("Report export failed: "+ex.getMessage());}}
    private File choose(String name){FileChooser chooser=new FileChooser();chooser.setInitialFileName(name);chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files","*.csv"));return chooser.showSaveDialog(getScene()==null?null:getScene().getWindow());}
}
