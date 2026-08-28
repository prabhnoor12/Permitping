package com.permitping.ui;

import com.permitping.application.DocumentService;
import com.permitping.domain.Document;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;

public final class ArchivedManager {
    private final DocumentService documents; private final NotificationService notifications;
    public ArchivedManager(DocumentService documents, NotificationService notifications) { this.documents=documents;this.notifications=notifications; }
    public void show() {
        Dialog<Void> dialog=new Dialog<>();dialog.setTitle("Archived documents");dialog.setHeaderText("Restore documents removed from the active register");dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        ListView<Document> list=new ListView<>(FXCollections.observableArrayList(documents.archived()));list.setPrefSize(620,300);list.setCellFactory(ignored->new ListCell<>(){protected void updateItem(Document item,boolean empty){super.updateItem(item,empty);setText(empty||item==null?null:item.name()+" · "+item.holder()+" · "+(item.project()==null?"No project":item.project()));}});
        Button restore=new Button("Restore selected");restore.getStyleClass().add("secondary");restore.setDisable(true);list.getSelectionModel().selectedItemProperty().addListener((obs,old,value)->restore.setDisable(value==null));restore.setOnAction(event->{Document selected=list.getSelectionModel().getSelectedItem();if(selected==null)return;try{documents.restore(selected.id());list.getItems().setAll(documents.archived());notifications.info("Document restored to the active register.");}catch(RuntimeException ex){notifications.error(ex.getMessage()==null?"Document could not be restored.":ex.getMessage());}});
        Label hint=new Label("Archived files remain available here and can be restored at any time.");hint.getStyleClass().add("helper-text");javafx.scene.layout.VBox content=new javafx.scene.layout.VBox(12,hint,list,restore);content.setPadding(new Insets(10));dialog.getDialogPane().setContent(content);dialog.showAndWait();
    }
}
