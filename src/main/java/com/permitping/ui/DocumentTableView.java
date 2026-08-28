package com.permitping.ui;

import com.permitping.application.DocumentService;
import com.permitping.application.ProfileService;
import com.permitping.domain.*;
import com.permitping.infrastructure.FileStorage;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.List;
import javafx.util.Callback;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public final class DocumentTableView extends TableView<Document> {
    private final DocumentService service; private final FileStorage files; private final ProfileService profiles; private final TextField search=new TextField(); private final Label placeholder=new Label(); private final DateTimeFormatter dates=DateTimeFormatter.ofPattern("dd MMM yyyy"); private String quickFilter="All"; private String projectFilter="All projects"; private Runnable resultsChanged=()->{};
    public DocumentTableView(DocumentService service, FileStorage files, Consumer<Document> onSelected){this(service,files,null,onSelected);}
    public DocumentTableView(DocumentService service, FileStorage files, ProfileService profiles, Consumer<Document> onSelected){this.service=service;this.files=files;this.profiles=profiles;getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);search.setPromptText("Search document, holder, or project...");search.setPrefWidth(360);search.textProperty().addListener((o,a,b)->refresh());buildColumns();placeholder.getStyleClass().add("helper-text");setPlaceholder(placeholder);setColumnResizePolicy(CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);getSelectionModel().selectedItemProperty().addListener((o,a,b)->onSelected.accept(b));setRowFactory(tv->{TableRow<Document> row=new TableRow<>();row.itemProperty().addListener((o,a,b)->styleRow(row,b));return row;});}
    public HBox toolbar(Runnable onRefresh){HBox bar=new HBox(10);bar.getStyleClass().add("toolbar");Label label=new Label("Document register");label.getStyleClass().add("section-title");Button clear=new Button("Clear search");clear.getStyleClass().add("secondary");clear.setOnAction(e->search.clear());Button refresh=new Button("↻ Refresh");refresh.getStyleClass().add("secondary");refresh.setOnAction(e->onRefresh.run());Region push=new Region();HBox.setHgrow(push,Priority.ALWAYS);search.setMaxWidth(320);bar.getChildren().addAll(label, push, search, clear, refresh);return bar;}
    public void setQuickFilter(String filter){quickFilter=filter==null?"All":filter;refresh();}
    public void setProjectFilter(String project){projectFilter=project==null?"All projects":project;refresh();}
    public void clearSearch(){search.clear();}
    public void setResultsChanged(Runnable listener){resultsChanged=listener==null?()->{}:listener;}
    public int totalCount(){return service.list().size();}
    public int filteredCount(){return getItems().size();}
    public Document selectedDocument(){return getSelectionModel().getSelectedItem();}
    public List<Document> selectedDocuments(){return List.copyOf(getSelectionModel().getSelectedItems());}
    public boolean selectById(long id){return getItems().stream().filter(document->document.id()==id).findFirst().map(document->{getSelectionModel().select(document);return true;}).orElse(false);}
    public void refresh(){var all=service.list();var filtered=all.stream().filter(this::matches).toList();placeholder.setText(all.isEmpty()?"No documents yet. Use '+ Add document' to create your first record.":filtered.isEmpty()?"No documents match the current search and filters.":"No documents match your filters.");setItems(FXCollections.observableArrayList(filtered));resultsChanged.run();}
    private boolean matches(Document d){String q=search.getText()==null?"":search.getText().trim().toLowerCase();String hay=(d.name()+" "+d.type()+" "+holder(d)+" "+d.holder()+" "+(d.project()==null?"":d.project())).toLowerCase();if(!q.isBlank()&&!hay.contains(q))return false;ComplianceStatus s=d.status(Clock.systemDefaultZone());long days=d.daysUntilExpiry(Clock.systemDefaultZone());return switch(quickFilter){case "Expired"->s==ComplianceStatus.EXPIRED;case "Due this week"->days>=0&&days<=7;case "Due this month"->days>=0&&days<=30;case "Missing file"->!files.exists(d.filePath());case "By project"->projectFilter.equalsIgnoreCase("All projects")||projectFilter.equalsIgnoreCase(d.project()==null?"":d.project().trim());default->true;};}
    private void buildColumns(){TableColumn<Document,String> expires=col("EXPIRES",d->dates.format(d.expiresOn()));expires.setComparator((left,right)->parseDate(left).compareTo(parseDate(right)));getColumns().addAll(col("DOCUMENT",Document::name),col("TYPE",Document::type),col("HOLDER / COMPANY",this::holder),col("PROJECT",d->blank(d.project())),expires,fileStatusColumn(),statusColumn());}
    private TableColumn<Document,String> col(String title,java.util.function.Function<Document,String> fn){TableColumn<Document,String> c=new TableColumn<>(title);c.setCellValueFactory(x->new SimpleStringProperty(fn.apply(x.getValue())));return c;}
    private TableColumn<Document,String> statusColumn(){TableColumn<Document,String> c=col("STATUS",d->d.status(Clock.systemDefaultZone()).label());c.setCellFactory(new Callback<>(){public TableCell<Document,String> call(TableColumn<Document,String> ignored){return new TableCell<>(){private final Label badge=new Label();private final HBox box=new HBox(6,new javafx.scene.shape.Circle(4),badge);{badge.getStyleClass().add("status-text");box.getStyleClass().add("status");}protected void updateItem(String text,boolean empty){super.updateItem(text,empty);if(empty){setGraphic(null);}else{Document d=getTableView().getItems().get(getIndex());badge.setText(text);box.getStyleClass().removeAll("status-current","status-expiring","status-expired");box.getStyleClass().add(switch(d.status(Clock.systemDefaultZone())){case CURRENT->"status-current";case EXPIRING_SOON->"status-expiring";case EXPIRED->"status-expired";});setGraphic(box);setText(null);}}};}});return c;}
    private TableColumn<Document,String> fileStatusColumn(){TableColumn<Document,String> c=col("FILE",d->files.exists(d.filePath())?"Available":d.filePath()==null||d.filePath().isBlank()?"Not attached":"Missing");c.setCellFactory(new Callback<>(){public TableCell<Document,String> call(TableColumn<Document,String> ignored){return new TableCell<>(){private final Label badge=new Label();{badge.getStyleClass().add("status");}protected void updateItem(String text,boolean empty){super.updateItem(text,empty);if(empty){setGraphic(null);}else{badge.setText(text);badge.getStyleClass().removeAll("file-available","file-missing","file-none");badge.getStyleClass().add(switch(text){case "Available"->"file-available";case "Missing"->"file-missing";default->"file-none";});setGraphic(badge);setText(null);}}};}});return c;}
    private void styleRow(TableRow<Document> row,Document d){row.getStyleClass().removeAll("expired-row","expiring-row");if(d!=null)switch(d.status(Clock.systemDefaultZone())){case EXPIRED->row.getStyleClass().add("expired-row");case EXPIRING_SOON->row.getStyleClass().add("expiring-row");default->{}}}
    private LocalDate parseDate(String value){return dates.parse(value,LocalDate::from);}
    private String holder(Document document){if(profiles!=null&&document.holderProfileId()>0)return profiles.list().stream().filter(profile->profile.id()==document.holderProfileId()).map(com.permitping.domain.Profile::name).findFirst().orElse(document.holder());return document.holder();}
    private String blank(String s){return s==null||s.isBlank()?"—":s;}
}
