package com.permitping.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import com.permitping.domain.ReminderNotice;
import java.util.List;

public final class NotificationService {
    public void error(String message) { new Alert(Alert.AlertType.ERROR, message, ButtonType.OK).showAndWait(); }
    public void info(String message) { new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK).showAndWait(); }
    public void reminders(List<ReminderNotice> notices) { if (notices == null || notices.isEmpty()) return; String message = notices.stream().map(ReminderNotice::message).collect(java.util.stream.Collectors.joining("\n")); new Alert(Alert.AlertType.WARNING, message, ButtonType.OK).showAndWait(); }
}
