package com.permitping.ui;

import com.permitping.application.DocumentService;
import com.permitping.domain.Document;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public final class ArchivedPage extends BorderPane {
    private final DocumentService documents; private final NotificationService notifications; private final ListView<Document> list = new ListView<>();
    public ArchivedPage(DocumentService documents, NotificationService notifications) {
        this.documents=documents; this.notifications=notifications; getStyleClass().add("page-shell");
        Label empty=new Label("No archived documents. Archived records will appear here when you remove them from the active register."); empty.getStyleClass().add("helper-text"); empty.setWrapText(true); list.setPlaceholder(empty); list.setMinHeight(220); VBox.setVgrow(list,Priority.ALWAYS);
        list.setCellFactory(view -> new ListCell<>() { private final Label name=new Label(); private final Label meta=new Label(); private final Region spacer=new Region(); private final Button restore=new Button("Restore"); private final HBox row=new HBox(14,new VBox(3,name,meta),spacer,restore); { HBox.setHgrow(spacer,Priority.ALWAYS); name.getStyleClass().add("profile-name"); meta.getStyleClass().add("helper-text"); restore.getStyleClass().add("secondary"); restore.setOnAction(e->{Document item=getItem();if(item!=null)restore(item);}); } protected void updateItem(Document item,boolean empty){super.updateItem(item,empty);if(empty||item==null){setText(null);setGraphic(null);return;}name.setText(item.name());meta.setText(item.holder()+"  ·  "+(item.project()==null||item.project().isBlank()?"No project":item.project())+"  ·  Expires "+item.expiresOn());setGraphic(row);setText(null);} });
        Button restoreSelected=new Button("Restore selected"); restoreSelected.getStyleClass().add("secondary"); restoreSelected.setDisable(true); list.getSelectionModel().selectedItemProperty().addListener((o,a,b)->restoreSelected.setDisable(b==null)); restoreSelected.setOnAction(e->{Document item=list.getSelectionModel().getSelectedItem();if(item!=null)restore(item);});
        setPadding(new Insets(30,34,30,34)); setTop(new PageHeader("Archived documents","Review records removed from the active register and restore them when they are needed again.")); Label title=new Label("Archive");title.getStyleClass().add("section-title");Label subtitle=new Label("Restore a record to return it to the active documents register.");subtitle.getStyleClass().add("helper-text");FlowPane actions=new FlowPane(10,8,restoreSelected);actions.getStyleClass().add("toolbar");VBox card=new VBox(14,title,subtitle,list,actions);card.getStyleClass().addAll("card","archive-card");setCenter(card);refresh();
    }
    private void restore(Document item){try{documents.restore(item.id());refresh();notifications.info("Document restored to the active register.");}catch(RuntimeException ex){notifications.error("Document could not be restored.");}}
    public void refresh(){list.setItems(FXCollections.observableArrayList(documents.archived()));}
}
