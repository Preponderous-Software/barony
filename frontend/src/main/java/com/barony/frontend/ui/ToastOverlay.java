package com.barony.frontend.ui;

import com.barony.frontend.rendering.ThemeManager;
import com.barony.frontend.ui.NotificationManager.Notification;
import com.barony.frontend.ui.NotificationManager.Severity;
import com.barony.frontend.ui.SimpleTextRenderer;

import static org.lwjgl.opengl.GL11.*;

/**
 * Renders toast notifications in the bottom-right corner of the screen.
 * Draws above all other UI elements (highest Z-order).
 */
public class ToastOverlay {

    private static final float TOAST_WIDTH = 0.45f;
    private static final float TOAST_HEIGHT = 0.06f;
    private static final float TOAST_MARGIN = 0.01f;
    private static final float TOAST_RIGHT_MARGIN = 0.02f;
    private static final float TOAST_BOTTOM_MARGIN = 0.02f;
    private static final float BORDER_WIDTH = 0.005f;

    /**
     * Render active toast notifications. Call this last in the render loop.
     * Assumes orthographic projection is set to [-1, 1] on both axes.
     */
    public void render(int windowWidth, int windowHeight) {
        NotificationManager mgr = NotificationManager.getInstance();
        mgr.update();

        Notification[] notifications = mgr.getActiveNotifications();
        if (notifications.length == 0) return;

        glPushAttrib(GL_ENABLE_BIT | GL_CURRENT_BIT);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        float rightEdge = 1.0f - TOAST_RIGHT_MARGIN;
        float bottomStart = -1.0f + TOAST_BOTTOM_MARGIN;

        for (int i = 0; i < notifications.length; i++) {
            // Render newest notifications closest to the bottom edge
            Notification n = notifications[notifications.length - 1 - i];
            float y = bottomStart + i * (TOAST_HEIGHT + TOAST_MARGIN);
            float x = rightEdge - TOAST_WIDTH;

            // Draw background
            float[] bgColor = getBackgroundColor(n.getSeverity());
            glColor4f(bgColor[0], bgColor[1], bgColor[2], 0.9f);
            glBegin(GL_QUADS);
            glVertex2f(x, y);
            glVertex2f(rightEdge, y);
            glVertex2f(rightEdge, y + TOAST_HEIGHT);
            glVertex2f(x, y + TOAST_HEIGHT);
            glEnd();

            // Draw left border accent
            float[] accentColor = getAccentColor(n.getSeverity());
            glColor4f(accentColor[0], accentColor[1], accentColor[2], 1.0f);
            glBegin(GL_QUADS);
            glVertex2f(x, y);
            glVertex2f(x + BORDER_WIDTH, y);
            glVertex2f(x + BORDER_WIDTH, y + TOAST_HEIGHT);
            glVertex2f(x, y + TOAST_HEIGHT);
            glEnd();

            // Draw text
            float[] textColor = ThemeManager.getInstance().getTextColor();
            float textScale = ThemeManager.getInstance().getFontScale() * 0.0012f;
            float textX = x + BORDER_WIDTH + 0.01f;
            float textY = y + TOAST_HEIGHT / 2 - 0.015f;

            // Truncate message if too long
            String msg = n.getMessage();
            if (msg.length() > 50) {
                msg = msg.substring(0, 47) + "...";
            }
            SimpleTextRenderer.drawText(msg, textX, textY, textScale, textColor[0], textColor[1], textColor[2]);
        }

        glPopAttrib();
    }

    private float[] getBackgroundColor(Severity severity) {
        switch (severity) {
            case SUCCESS: return new float[]{0.16f, 0.23f, 0.16f};
            case WARNING: return new float[]{0.23f, 0.21f, 0.12f};
            case DANGER:  return new float[]{0.24f, 0.10f, 0.10f};
            default:      return new float[]{0.23f, 0.22f, 0.20f}; // INFO
        }
    }

    private float[] getAccentColor(Severity severity) {
        switch (severity) {
            case SUCCESS: return new float[]{0.35f, 0.60f, 0.35f};
            case WARNING: return new float[]{0.79f, 0.66f, 0.43f};
            case DANGER:  return new float[]{0.88f, 0.31f, 0.31f};
            default:      return new float[]{0.54f, 0.51f, 0.47f}; // INFO
        }
    }
}
