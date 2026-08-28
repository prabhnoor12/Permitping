package com.permitping.ui;

import com.permitping.domain.*;
import com.permitping.infrastructure.FileStorage;
import com.permitping.application.DocumentVersionService;
import com.permitping.application.ProfileService;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import java.awt.Desktop;
import java.io.File;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public final class DocumentDetailView extends VBox {
    private final FileStorage files; private final DocumentVersionService versions; private final NotificationService notifications; private final ProfileService profiles; private final Label title=new Label("Select a document"); private final VBox body=new VBox(10); private Document selected; private final Button edit=new Button("Edit"),renew=new Button("Renew"),archive=new Button("Archive"),open=new Button("Open file"),copy=new Button("Copy path"),history=new Button("Version history");
    public DocumentDetailView(FileStorage files, DocumentVersionService versions, NotificationService notifications, Consumer<Document> onEdit,Consumer<Document> onRenew,Consumer<Document> onArchive){this(files,versions,notifications,null,onEdit,onRenew,onArchive);}
    public DocumentDetailView(FileStorage files, DocumentVersionService versions, NotificationService notifications, ProfileService profiles, Consumer<Document> onEdit,Consumer<Document> onRenew,Consumer<Document> onArchive){this(files,versions,notifications,profiles,onEdit,onRenew,onArchive,true);}
    public DocumentDetailView(FileStorage files, DocumentVersionService versions, NotificationService notifications, ProfileService profiles, Consumer<Document> onEdit,Consumer<Document> onRenew,Consumer<Document> onArchive, boolean canManage){this.files=files;this.versions=versions;this.notifications=notifications;this.profiles=profiles;setSpacing(16);setPadding(new javafx.geometry.Insets(20));getStyleClass().add("detail-panel");title.getStyleClass().add("detail-title");body.getStyleClass().add("muted");HBox actions=new HBox(8,edit,renew,archive);edit.getStyleClass().add("secondary");renew.getStyleClass().add("secondary");archive.getStyleClass().add("danger");edit.setVisible(canManage);edit.setManaged(canManage);renew.setVisible(canManage);renew.setManaged(canManage);archive.setVisible(canManage);archive.setManaged(canManage);open.getStyleClass().add("secondary");copy.getStyleClass().add("secondary");history.getStyleClass().add("secondary");edit.setOnAction(e->{if(selected!=null)onEdit.accept(selected);});renew.setOnAction(e->{if(selected!=null)onRenew.accept(selected);});archive.setOnAction(e->{if(selected!=null)onArchive.accept(selected);});open.setOnAction(e->openFile());copy.setOnAction(e->copyPath());history.setOnAction(e->showHistory());getChildren().addAll(title,actions,body,new Separator(),open,copy,history);clear();}
    public void show(Document document){selected=document;clear();if(document==null)return;title.setText(document.name());Label status=new Label(document.status(Clock.systemDefaultZone()).label());status.getStyleClass().addAll("status",switch(document.status(Clock.systemDefaultZone())){case CURRENT->"status-current";case EXPIRING_SOON->"status-expiring";case EXPIRED->"status-expired";});body.getChildren().addAll(status,field("Type",document.type()),field("Holder / company",holder(document)),field("Project",blank(document.project())),field("Expires",DateTimeFormatter.ofPattern("dd MMM yyyy").format(document.expiresOn())),field("File",blank(document.filePath())),field("Notes",blank(document.notes())));boolean hasFile=files.exists(document.filePath());open.setDisable(!hasFile);copy.setDisable(document.filePath()==null||document.filePath().isBlank());}
    public void showSelectionSummary(int count){selected=null;clear();title.setText(count+" documents selected");body.getChildren().addAll(field("Bulk action","Use Archive selected to remove these records from the active register."),field("Selection",""+count+" records"));}
    private void clear(){body.getChildren().clear();edit.setDisable(selected==null);renew.setDisable(selected==null);archive.setDisable(selected==null);open.setDisable(true);copy.setDisable(true);history.setDisable(selected==null||versions==null);}
    private String holder(Document document){if(profiles!=null&&document.holderProfileId()>0)return profiles.list().stream().filter(profile->profile.id()==document.holderProfileId()).map(Profile::name).findFirst().orElse(document.holder());return document.holder();}
    private VBox field(String label,String value){Label l=new Label(label);l.getStyleClass().add("detail-label");Label v=new Label(value);v.setWrapText(true);return new VBox(3,l,v);}
    private void openFile(){try{if(Desktop.isDesktopSupported())Desktop.getDesktop().open(new File(selected.filePath()));}catch(Exception e){notifications.error("Could not open the selected file.");}}
    private void copyPath(){ClipboardContent content=new ClipboardContent();content.putString(selected.filePath());Clipboard.getSystemClipboard().setContent(content);notifications.info("File path copied to the clipboard.");}
    private void showHistory(){var items=versions.list(selected.id());String text=items.isEmpty()?"No stored versions yet.":items.stream().map(v->"Version "+v.version()+" — "+v.createdAt()+"\n"+v.filePath()).collect(java.util.stream.Collectors.joining("\n\n"));new Alert(Alert.AlertType.INFORMATION,text,ButtonType.OK).showAndWait();}
    private String blank(String s){return s==null||s.isBlank()?"—":s;}
}
