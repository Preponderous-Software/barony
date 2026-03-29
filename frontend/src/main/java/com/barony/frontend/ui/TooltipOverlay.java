package com.barony.frontend.ui;

import com.barony.frontend.rendering.ThemeManager;
import com.barony.frontend.ui.SimpleTextRenderer;

import static org.lwjgl.opengl.GL11.*;

/**
 * Renders a single tooltip near the cursor position.
 * Renderers register tooltip text; the overlay handles positioning and drawing.
 */
public class TooltipOverlay {

    private String text = null;
    private float cursorX = 0;
    private float cursorY = 0;
    private boolean visible = false;

    private static final float PADDING = 0.01f;
    private static final float MAX_WIDTH = 0.5f;
    private static final float LINE_HEIGHT = 0.035f;
    private static final float CURSOR_OFFSET_X = 0.02f;
    private static final float CURSOR_OFFSET_Y = -0.04f;

    /**
     * Set the tooltip text and cursor position. Pass null text to hide.
     */
    public void set(String text, float cursorX, float cursorY) {
        this.text = text;
        this.cursorX = cursorX;
        this.cursorY = cursorY;
        this.visible = text != null && !text.isEmpty();
    }

    public void hide() {
        this.visible = false;
        this.text = null;
    }

    /**
     * Render the tooltip if visible. Call after all other UI rendering.
     */
    public void render(int windowWidth, int windowHeight) {
        if (!visible || text == null) return;

        float textScale = ThemeManager.getInstance().getFontScale() * 0.001f;
        float charWidth = 6 * textScale;

        // Word wrap
        String[] words = text.split(" ");
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        int maxCharsPerLine = (int)(MAX_WIDTH / charWidth);
        if (maxCharsPerLine < 10) maxCharsPerLine = 10;

        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > maxCharsPerLine) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                if (currentLine.length() > 0) currentLine.append(" ");
                currentLine.append(word);
            }
        }
        if (currentLine.length() > 0) lines.add(currentLine.toString());

        // Calculate tooltip dimensions
        int maxLineLen = 0;
        for (String line : lines) {
            if (line.length() > maxLineLen) maxLineLen = line.length();
        }
        float tooltipWidth = maxLineLen * charWidth + PADDING * 2;
        float tooltipHeight = lines.size() * LINE_HEIGHT + PADDING * 2;

        float x = cursorX + CURSOR_OFFSET_X;
        float y = cursorY + CURSOR_OFFSET_Y;

        // Clamp to screen bounds
        if (x + tooltipWidth > 1.0f) x = 1.0f - tooltipWidth;
        if (y - tooltipHeight < -1.0f) y = -1.0f + tooltipHeight;

        glPushAttrib(GL_ENABLE_BIT | GL_CURRENT_BIT);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Background
        glColor4f(0.12f, 0.10f, 0.08f, 0.92f);
        glBegin(GL_QUADS);
        glVertex2f(x, y - tooltipHeight);
        glVertex2f(x + tooltipWidth, y - tooltipHeight);
        glVertex2f(x + tooltipWidth, y);
        glVertex2f(x, y);
        glEnd();

        // Border
        glColor4f(0.35f, 0.31f, 0.24f, 1.0f);
        glLineWidth(1.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y - tooltipHeight);
        glVertex2f(x + tooltipWidth, y - tooltipHeight);
        glVertex2f(x + tooltipWidth, y);
        glVertex2f(x, y);
        glEnd();

        // Text
        float[] textColor = ThemeManager.getInstance().getTextColor();
        for (int i = 0; i < lines.size(); i++) {
            float textX = x + PADDING;
            float textY = y - PADDING - (i + 1) * LINE_HEIGHT + LINE_HEIGHT * 0.3f;
            SimpleTextRenderer.drawText(lines.get(i), textX, textY, textScale, textColor[0], textColor[1], textColor[2]);
        }

        glPopAttrib();
    }
}
