package com.barony.frontend.ui;

import java.util.LinkedList;
import java.util.Iterator;

/**
 * Singleton notification manager with a FIFO queue for toast notifications.
 * Manages lifecycle: creation, auto-dismiss, and flush of oldest when capacity exceeded.
 */
public class NotificationManager {

    public enum Severity {
        INFO, SUCCESS, WARNING, DANGER
    }

    public static class Notification {
        private final String message;
        private final Severity severity;
        private final long timestampMs;
        private final boolean autoDismiss;
        private boolean dismissed;

        public Notification(String message, Severity severity, boolean autoDismiss) {
            this.message = message;
            this.severity = severity;
            this.timestampMs = System.currentTimeMillis();
            this.autoDismiss = autoDismiss;
            this.dismissed = false;
        }

        public String getMessage() { return message; }
        public Severity getSeverity() { return severity; }
        public long getTimestampMs() { return timestampMs; }
        public boolean isAutoDismiss() { return autoDismiss; }
        public boolean isDismissed() { return dismissed; }
        public void dismiss() { this.dismissed = true; }
    }

    private static final NotificationManager INSTANCE = new NotificationManager();
    private static final int MAX_VISIBLE = 4;
    private static final long AUTO_DISMISS_MS = 4000;

    private final LinkedList<Notification> active = new LinkedList<>();
    private final LinkedList<Notification> history = new LinkedList<>();
    private static final int MAX_HISTORY = 50;

    private NotificationManager() {}

    public static NotificationManager getInstance() {
        return INSTANCE;
    }

    /**
     * Show a toast notification. Critical events (DANGER with certain keywords) persist until dismissed.
     */
    public void show(String message, Severity severity) {
        boolean isCritical = severity == Severity.DANGER &&
            (message.toLowerCase().contains("game over") ||
             message.toLowerCase().contains("castle") ||
             message.toLowerCase().contains("destroyed"));

        Notification n = new Notification(message, severity, !isCritical);

        synchronized (active) {
            // Flush oldest if at capacity
            while (active.size() >= MAX_VISIBLE) {
                Notification oldest = active.removeFirst();
                oldest.dismiss();
                addToHistory(oldest);
            }
            active.addLast(n);
        }
    }

    public void info(String message) { show(message, Severity.INFO); }
    public void success(String message) { show(message, Severity.SUCCESS); }
    public void warning(String message) { show(message, Severity.WARNING); }
    public void danger(String message) { show(message, Severity.DANGER); }

    /**
     * Called every frame to auto-dismiss expired notifications.
     */
    public void update() {
        long now = System.currentTimeMillis();
        synchronized (active) {
            Iterator<Notification> it = active.iterator();
            while (it.hasNext()) {
                Notification n = it.next();
                if (n.isDismissed()) {
                    it.remove();
                    addToHistory(n);
                } else if (n.isAutoDismiss() && (now - n.getTimestampMs()) > AUTO_DISMISS_MS) {
                    n.dismiss();
                    it.remove();
                    addToHistory(n);
                }
            }
        }
    }

    /**
     * Returns a snapshot of active notifications for rendering.
     */
    public Notification[] getActiveNotifications() {
        synchronized (active) {
            return active.toArray(new Notification[0]);
        }
    }

    /**
     * Returns notification history for the log panel.
     */
    public Notification[] getHistory() {
        synchronized (history) {
            return history.toArray(new Notification[0]);
        }
    }

    public void dismissAll() {
        synchronized (active) {
            for (Notification n : active) {
                n.dismiss();
                addToHistory(n);
            }
            active.clear();
        }
    }

    private void addToHistory(Notification n) {
        synchronized (history) {
            history.addLast(n);
            while (history.size() > MAX_HISTORY) {
                history.removeFirst();
            }
        }
    }
}
