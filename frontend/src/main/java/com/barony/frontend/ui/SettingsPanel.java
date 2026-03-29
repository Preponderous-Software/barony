package com.barony.frontend.ui;

import com.barony.frontend.rendering.ThemeManager;
import com.barony.frontend.ui.SimpleTextRenderer;

import static org.lwjgl.opengl.GL11.*;

/**
 * In-game settings panel opened via F9.
 * Provides colorblind mode, theme, and font size selection.
 * Settings are persisted to ~/.barony/settings.json via ThemeManager.
 */
public class SettingsPanel {

    private boolean visible = false;
    private int selectedRow = 0;

    private static final float PANEL_X = -0.4f;
    private static final float PANEL_Y = -0.35f;
    private static final float PANEL_WIDTH = 0.8f;
    private static final float PANEL_HEIGHT = 0.7f;
    private static final float ROW_HEIGHT = 0.08f;

    private static final String[] COLORBLIND_OPTIONS = {"None", "Deuteranopia", "Protanopia", "Tritanopia"};
    private static final String[] THEME_OPTIONS = {"Dark", "Classic", "High Contrast"};
    private static final String[] FONT_SIZE_OPTIONS = {"Small", "Medium", "Large"};

    private int colorblindIndex = 0;
    private int themeIndex = 0;
    private int fontSizeIndex = 1; // Medium default

    public boolean isVisible() { return visible; }

    public void toggle() {
        visible = !visible;
        if (visible) {
            loadFromThemeManager();
        }
    }

    public void hide() { visible = false; }

    private String normalizeThemeName(String name) {
        if (name == null) return "";
        return name.replace('-', ' ').trim();
    }

    private void loadFromThemeManager() {
        ThemeManager tm = ThemeManager.getInstance();
        String cb = tm.getColorblindMode();
        for (int i = 0; i < COLORBLIND_OPTIONS.length; i++) {
            if (COLORBLIND_OPTIONS[i].equalsIgnoreCase(cb)) { colorblindIndex = i; break; }
        }
        String theme = normalizeThemeName(tm.getThemeName());
        for (int i = 0; i < THEME_OPTIONS.length; i++) {
            if (normalizeThemeName(THEME_OPTIONS[i]).equalsIgnoreCase(theme)) { themeIndex = i; break; }
        }
        float scale = tm.getFontScale();
        if (scale <= 0.85f) fontSizeIndex = 0;
        else if (scale >= 1.15f) fontSizeIndex = 2;
        else fontSizeIndex = 1;
    }

    public void navigateUp() {
        if (selectedRow > 0) selectedRow--;
    }

    public void navigateDown() {
        if (selectedRow < 2) selectedRow++;
    }

    public void navigateLeft() {
        switch (selectedRow) {
            case 0: colorblindIndex = Math.max(0, colorblindIndex - 1); break;
            case 1: themeIndex = Math.max(0, themeIndex - 1); break;
            case 2: fontSizeIndex = Math.max(0, fontSizeIndex - 1); break;
        }
        applySettings();
    }

    public void navigateRight() {
        switch (selectedRow) {
            case 0: colorblindIndex = Math.min(COLORBLIND_OPTIONS.length - 1, colorblindIndex + 1); break;
            case 1: themeIndex = Math.min(THEME_OPTIONS.length - 1, themeIndex + 1); break;
            case 2: fontSizeIndex = Math.min(FONT_SIZE_OPTIONS.length - 1, fontSizeIndex + 1); break;
        }
        applySettings();
    }

    private void applySettings() {
        ThemeManager tm = ThemeManager.getInstance();
        tm.setColorblindMode(COLORBLIND_OPTIONS[colorblindIndex].toLowerCase());
        tm.setTheme(THEME_OPTIONS[themeIndex].toLowerCase());

        float scale;
        switch (fontSizeIndex) {
            case 0: scale = 0.8f; break;
            case 2: scale = 1.2f; break;
            default: scale = 1.0f; break;
        }
        tm.setFontScale(scale);
        tm.save();
    }

    public void render(int windowWidth, int windowHeight) {
        if (!visible) return;

        glPushAttrib(GL_ENABLE_BIT | GL_CURRENT_BIT);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Background
        glColor4f(0.12f, 0.10f, 0.08f, 0.95f);
        glBegin(GL_QUADS);
        glVertex2f(PANEL_X, PANEL_Y);
        glVertex2f(PANEL_X + PANEL_WIDTH, PANEL_Y);
        glVertex2f(PANEL_X + PANEL_WIDTH, PANEL_Y + PANEL_HEIGHT);
        glVertex2f(PANEL_X, PANEL_Y + PANEL_HEIGHT);
        glEnd();

        // Border
        glColor4f(0.79f, 0.66f, 0.43f, 1.0f);
        glLineWidth(2.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(PANEL_X, PANEL_Y);
        glVertex2f(PANEL_X + PANEL_WIDTH, PANEL_Y);
        glVertex2f(PANEL_X + PANEL_WIDTH, PANEL_Y + PANEL_HEIGHT);
        glVertex2f(PANEL_X, PANEL_Y + PANEL_HEIGHT);
        glEnd();
        glLineWidth(1.0f);

        float textScale = ThemeManager.getInstance().getFontScale() * 0.0012f;
        float startY = PANEL_Y + PANEL_HEIGHT - 0.08f;

        // Title
        SimpleTextRenderer.drawText("Settings (F9 to close)", PANEL_X + 0.02f, startY, textScale, 0.79f, 0.66f, 0.43f);

        startY -= ROW_HEIGHT * 1.5f;

        // Draw rows
        String[][] rows = {
            {"Colorblind Mode:", "< " + COLORBLIND_OPTIONS[colorblindIndex] + " >"},
            {"Theme:", "< " + THEME_OPTIONS[themeIndex] + " >"},
            {"Font Size:", "< " + FONT_SIZE_OPTIONS[fontSizeIndex] + " >"}
        };

        for (int i = 0; i < rows.length; i++) {
            float rowY = startY - i * ROW_HEIGHT;

            // Highlight selected row
            if (i == selectedRow) {
                glColor4f(0.79f, 0.66f, 0.43f, 0.15f);
                glBegin(GL_QUADS);
                glVertex2f(PANEL_X + 0.01f, rowY - 0.01f);
                glVertex2f(PANEL_X + PANEL_WIDTH - 0.01f, rowY - 0.01f);
                glVertex2f(PANEL_X + PANEL_WIDTH - 0.01f, rowY + ROW_HEIGHT - 0.02f);
                glVertex2f(PANEL_X + 0.01f, rowY + ROW_HEIGHT - 0.02f);
                glEnd();
            }

            // Label
            float[] textColor = ThemeManager.getInstance().getTextColor();
            SimpleTextRenderer.drawText(rows[i][0], PANEL_X + 0.03f, rowY + 0.01f, textScale, textColor[0], textColor[1], textColor[2]);

            // Value (highlighted when selected)
            if (i == selectedRow) {
                SimpleTextRenderer.drawText(rows[i][1], PANEL_X + 0.35f, rowY + 0.01f, textScale, 0.79f, 0.66f, 0.43f);
            } else {
                SimpleTextRenderer.drawText(rows[i][1], PANEL_X + 0.35f, rowY + 0.01f, textScale, textColor[0], textColor[1], textColor[2]);
            }
        }

        // Controls hint
        startY -= rows.length * ROW_HEIGHT + ROW_HEIGHT;
        SimpleTextRenderer.drawText("UP/DOWN: navigate  LEFT/RIGHT: change", PANEL_X + 0.03f, startY, textScale * 0.8f, 0.54f, 0.51f, 0.47f);

        glPopAttrib();
    }
}
