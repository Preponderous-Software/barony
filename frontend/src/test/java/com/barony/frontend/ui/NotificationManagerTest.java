package com.barony.frontend.ui;

import com.barony.frontend.ui.NotificationManager.Notification;
import com.barony.frontend.ui.NotificationManager.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotificationManagerTest {

    private NotificationManager manager;

    @BeforeEach
    void setUp() {
        manager = NotificationManager.getInstance();
        manager.dismissAll();
    }

    @Test
    void showAddsNotification() {
        manager.show("Test message", Severity.INFO);
        Notification[] active = manager.getActiveNotifications();
        assertEquals(1, active.length);
        assertEquals("Test message", active[0].getMessage());
        assertEquals(Severity.INFO, active[0].getSeverity());
    }

    @Test
    void convenienceMethodsWork() {
        manager.info("Info msg");
        manager.success("Success msg");
        manager.warning("Warning msg");
        manager.danger("Danger msg");
        Notification[] active = manager.getActiveNotifications();
        assertEquals(4, active.length);
        assertEquals(Severity.INFO, active[0].getSeverity());
        assertEquals(Severity.SUCCESS, active[1].getSeverity());
        assertEquals(Severity.WARNING, active[2].getSeverity());
        assertEquals(Severity.DANGER, active[3].getSeverity());
    }

    @Test
    void flushesOldestWhenMaxExceeded() {
        manager.info("msg1");
        manager.info("msg2");
        manager.info("msg3");
        manager.info("msg4");
        // Adding 5th should flush the oldest
        manager.info("msg5");
        Notification[] active = manager.getActiveNotifications();
        assertEquals(4, active.length);
        assertEquals("msg2", active[0].getMessage());
        assertEquals("msg5", active[3].getMessage());
    }

    @Test
    void dismissAllClearsActive() {
        manager.info("msg1");
        manager.info("msg2");
        manager.dismissAll();
        Notification[] active = manager.getActiveNotifications();
        assertEquals(0, active.length);
    }

    @Test
    void dismissedNotificationsMovedToHistory() {
        manager.info("history msg");
        manager.dismissAll();
        Notification[] history = manager.getHistory();
        assertTrue(history.length > 0);
        assertEquals("history msg", history[history.length - 1].getMessage());
    }

    @Test
    void criticalDangerNotificationsDoNotAutoDismiss() {
        manager.danger("Your castle is under attack");
        Notification[] active = manager.getActiveNotifications();
        assertEquals(1, active.length);
        assertFalse(active[0].isAutoDismiss());
    }

    @Test
    void nonCriticalDangerNotificationsAutoDismiss() {
        manager.danger("Some error occurred");
        Notification[] active = manager.getActiveNotifications();
        assertEquals(1, active.length);
        assertTrue(active[0].isAutoDismiss());
    }

    @Test
    void notificationSeverityLevelsExist() {
        assertEquals(4, Severity.values().length);
        assertNotNull(Severity.INFO);
        assertNotNull(Severity.SUCCESS);
        assertNotNull(Severity.WARNING);
        assertNotNull(Severity.DANGER);
    }
}
