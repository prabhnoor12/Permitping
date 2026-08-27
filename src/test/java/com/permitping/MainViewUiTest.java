package com.permitping;

import com.permitping.application.DocumentRepository;
import com.permitping.application.DocumentService;
import com.permitping.domain.Document;
import com.permitping.ui.MainView;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MainViewUiTest {
    @Test void laysOutAtMinimumSupportedWindowSize() throws Exception {
        FxTestSupport.runAndWait(() -> {
            DocumentService service = new DocumentService(new DocumentRepository() {
                public List<Document> findAll() { return List.of(); }
                public Document save(Document value) { return value; }
                public void delete(long id) { }
            });
            MainView view = new MainView(service);
            Scene scene = new Scene(view, 980, 650);
            view.applyCss();
            view.layout();

            assertNotNull(view.getLeft(), "The navigation must remain visible at the minimum width");
            assertTrue(view.getCenter() instanceof ScrollPane, "The content shell must remain scrollable at the minimum width");
            assertTrue(view.getWidth() >= 980 || scene.getWidth() >= 980);
            assertTrue(((ScrollPane) view.getCenter()).getViewportBounds().getWidth() > 0, "The content shell must receive layout width");
        });
    }
}
