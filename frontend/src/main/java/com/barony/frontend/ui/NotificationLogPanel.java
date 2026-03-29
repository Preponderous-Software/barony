package com.barony.frontend.ui;

import com.barony.frontend.rendering.ThemeManager;
import com.barony.frontend.ui.NotificationManager.Notification;
import com.barony.frontend.ui.NotificationManager.Severity;
import com.barony.frontend.ui.SimpleTextRenderer;

import static org.lwjgl.opengl.GL11.*;

/**
 * Renders a scrollable notification history panel.
 * Shows past notifications with severity-based coloring.
 */
public class NotificationLogPanel {

    private boolean visible = false;
    private int scrollOffset = 0;

    private static final float PANEL_X = -0.9f;
    private static final float PANEL_Y = -0.5f;
    private static final float PANEL_WIDTH = 0.8f;
    private static final float PANEL_HEIGHT = 0.9f;
    private static final float LINE_HEIGHT = 0.04f;
    private static final int MAX_VISIBLE_LINES = 20;

    public boolean isVisible() { return visible; }
    public void toggle() { visible = !visible; scrollOffset = 0; }
    public void hide() { visible = false; }

    public void scrollUp() {
        if (scrollOffset > 0) scrollOffset--;
    }

    public void scrollDown() {
        Notification[] history = NotificationManager.getInstance().getHistory();
        if (scrollOffset < Math.max(0, history.length - MAX_VISIBLE_LINES)) {
            scrollOffset++;
        }
    }

    public void render(int windowWidth, int windowHeight) {
        if (!visible) return;

        Notification[] history = NotificationManager.getInstance().getHistory();

        glPushAttrib(GL_ENABLE_BIT | GL_CURRENT_BIT);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Background
        glColor4f(0.15f, 0.13f, 0.10f, 0.95f);
        glBegin(GL_QUADS);
        glVertex2f(PANEL_X, PANEL_Y);
        glVertex2f(PANEL_X + PANEL_WIDTH, PANEL_Y);
        glVertex2f(PANEL_X + PANEL_WIDTH, PANEL_Y + PANEL_HEIGHT);
        glVertex2f(PANEL_X, PANEL_Y + PANEL_HEIGHT);
        glEnd();

        // Border
        glColor4f(0.54f, 0.45f, 0.33f, 1.0f);
        glLineWidth(2.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(PANEL_X, PANEL_Y);
        glVertex2f(PANEL_X + PANEL_WIDTH, PANEL_Y);
        glVertex2f(PANEL_X + PANEL_WIDTH, PANEL_Y + PANEL_HEIGHT);
        glVertex2f(PANEL_X, PANEL_Y + PANEL_HEIGHT);
        glEnd();
        glLineWidth(1.0f);

        // Title
        float[] titleColor = new float[]{0.79f, 0.66f, 0.43f};
        float textScale = ThemeManager.getInstance().getFontScale() * 0.0012f;
        SimpleTextRenderer.drawText("Notification Log", PANEL_X + 0.02f, PANEL_Y + PANEL_HEIGHT - 0.05f, textScale, titleColor[0], titleColor[1], titleColor[2]);

        // Draw entries (newest first)
        float startY = PANEL_Y + PANEL_HEIGHT - 0.1f;
        int totalEntries = history.length;
        int startIdx = Math.max(0, totalEntries - 1 - scrollOffset);
        int count = Math.min(MAX_VISIBLE_LINES, startIdx + 1);

        for (int i = 0; i < count; i++) {
            Notification n = history[startIdx - i];
            float y = startY - i * LINE_HEIGHT;

            float[] color = getSeverityColor(n.getSeverity());

            String prefix = "[" + n.getSeverity().name() + "] ";
            String msg = prefix + n.getMessage();
            if (msg.length() > 70) msg = msg.substring(0, 67) + "...";

            SimpleTextRenderer.drawText(msg, PANEL_X + 0.02f, y, textScale * 0.8f, color[0], color[1], color[2]);
        }

        glPopAttrib();
    }

    private float[] getSeverityColor(Severity severity) {
        switch (severity) {
            case SUCCESS: return new float[]{0.35f, 0.60f, 0.35f};
            case WARNING: return new float[]{0.79f, 0.66f, 0.43f};
            case DANGER:  return new float[]{0.88f, 0.31f, 0.31f};
            default:      return new float[]{0.83f, 0.81f, 0.78f}; // INFO
        }
    }
}
