package com.permitping;

import javafx.application.Platform;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

final class FxTestSupport {
    private static final CountDownLatch STARTED = new CountDownLatch(1);
    static {
        try { Platform.startup(STARTED::countDown); STARTED.await(); }
        catch (IllegalStateException alreadyStarted) { STARTED.countDown(); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new RuntimeException(interrupted); }
    }
    static void runAndWait(Runnable action) throws Exception {
        STARTED.await(); if (Platform.isFxApplicationThread()) { action.run(); return; }
        CountDownLatch done = new CountDownLatch(1); AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> { try { action.run(); } catch (Throwable error) { failure.set(error); } finally { done.countDown(); } });
        done.await(); if (failure.get() != null) throw new AssertionError(failure.get());
    }
}
