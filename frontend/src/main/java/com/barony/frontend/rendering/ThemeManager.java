package com.barony.frontend.rendering;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralizes all color lookups so theme switching replaces the palette map
 * rather than individual render call sites. Supports colorblind modes,
 * themes, and font scaling.
 *
 * Settings are persisted to ~/.barony/settings.json.
 */
public class ThemeManager {

    private static final ThemeManager INSTANCE = new ThemeManager();

    // Font scaling
    private static final float BASE_FONT_SIZE = 1.0f;
    private float fontScale = BASE_FONT_SIZE;

    // Theme
    private String themeName = "dark";
    private String colorblindMode = "none";

    // Palette maps
    private float[] player1Color = {0.0f, 0.4f, 1.0f};   // #0066FF
    private float[] player2Color = {1.0f, 0.0f, 0.0f};     // #FF0000
    private float[] player1Tint = {0.39f, 0.59f, 1.0f, 0.3f};
    private float[] player2Tint = {1.0f, 0.39f, 0.39f, 0.3f};
    private float[] backgroundColor = {0.10f, 0.08f, 0.06f};
    private float[] textColor = {0.83f, 0.81f, 0.78f};
    private float[] accentColor = {0.79f, 0.66f, 0.43f};

    // Colorblind palette presets
    private static final Map<String, float[][]> COLORBLIND_PALETTES = new HashMap<>();
    static {
        // {player1Color, player2Color, player1Tint(rgb), player2Tint(rgb)}
        COLORBLIND_PALETTES.put("none", new float[][]{
            {0.0f, 0.4f, 1.0f},         // player1
            {1.0f, 0.0f, 0.0f},          // player2
            {0.39f, 0.59f, 1.0f},        // player1 tint rgb
            {1.0f, 0.39f, 0.39f}         // player2 tint rgb
        });
        COLORBLIND_PALETTES.put("deuteranopia", new float[][]{
            {0.0f, 0.45f, 0.70f},
            {0.90f, 0.62f, 0.0f},
            {0.0f, 0.45f, 0.70f},
            {0.90f, 0.62f, 0.0f}
        });
        COLORBLIND_PALETTES.put("protanopia", new float[][]{
            {0.94f, 0.89f, 0.26f},
            {0.84f, 0.37f, 0.0f},
            {0.94f, 0.89f, 0.26f},
            {0.84f, 0.37f, 0.0f}
        });
        COLORBLIND_PALETTES.put("tritanopia", new float[][]{
            {0.80f, 0.47f, 0.65f},
            {0.0f, 0.62f, 0.45f},
            {0.80f, 0.47f, 0.65f},
            {0.0f, 0.62f, 0.45f}
        });
    }

    private ThemeManager() {
        load();
    }

    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    // Getters
    public float getFontScale() { return fontScale; }
    public String getThemeName() { return themeName; }
    public String getColorblindMode() { return colorblindMode; }
    public float[] getPlayer1Color() { return player1Color; }
    public float[] getPlayer2Color() { return player2Color; }
    public float[] getPlayer1Tint() { return player1Tint; }
    public float[] getPlayer2Tint() { return player2Tint; }
    public float[] getBackgroundColor() { return backgroundColor; }
    public float[] getTextColor() { return textColor; }
    public float[] getAccentColor() { return accentColor; }

    // Setters
    public void setFontScale(float scale) {
        this.fontScale = Math.max(0.5f, Math.min(2.0f, scale));
    }

    public void setTheme(String theme) {
        if (theme == null || theme.trim().isEmpty()) {
            this.themeName = "dark";
        } else {
            String normalized = theme.trim().toLowerCase();
            switch (normalized) {
                case "classic":
                    this.themeName = "classic";
                    break;
                case "high contrast":
                case "high-contrast":
                    this.themeName = "high contrast";
                    break;
                default:
                    this.themeName = "dark";
                    break;
            }
        }
        applyTheme();
    }

    public void setColorblindMode(String mode) {
        if (mode == null || mode.trim().isEmpty()) {
            this.colorblindMode = "none";
        } else {
            this.colorblindMode = mode.trim().toLowerCase();
        }
        applyColorblindPalette();
    }

    private void applyTheme() {
        switch (themeName.toLowerCase()) {
            case "classic":
                backgroundColor = new float[]{0.96f, 0.94f, 0.91f};
                textColor = new float[]{0.16f, 0.13f, 0.09f};
                accentColor = new float[]{0.55f, 0.45f, 0.33f};
                break;
            case "high contrast":
            case "high-contrast":
                backgroundColor = new float[]{0.0f, 0.0f, 0.0f};
                textColor = new float[]{1.0f, 1.0f, 1.0f};
                accentColor = new float[]{1.0f, 1.0f, 0.0f};
                break;
            default: // dark
                backgroundColor = new float[]{0.10f, 0.08f, 0.06f};
                textColor = new float[]{0.83f, 0.81f, 0.78f};
                accentColor = new float[]{0.79f, 0.66f, 0.43f};
                break;
        }
    }

    private void applyColorblindPalette() {
        float[][] palette = COLORBLIND_PALETTES.getOrDefault(colorblindMode.toLowerCase(),
                COLORBLIND_PALETTES.get("none"));
        player1Color = palette[0].clone();
        player2Color = palette[1].clone();
        player1Tint = new float[]{palette[2][0], palette[2][1], palette[2][2], 0.3f};
        player2Tint = new float[]{palette[3][0], palette[3][1], palette[3][2], 0.3f};
    }

    // Persistence
    private Path getSettingsPath() {
        return Paths.get(System.getProperty("user.home"), ".barony", "settings.json");
    }

    public void save() {
        try {
            Path path = getSettingsPath();
            Files.createDirectories(path.getParent());
            Map<String, Object> settings = new HashMap<>();
            settings.put("colorblindMode", colorblindMode);
            settings.put("theme", themeName);
            settings.put("fontScale", fontScale);
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(settings);
            Files.writeString(path, json);
        } catch (IOException e) {
            System.err.println("Failed to save settings: " + e.getMessage());
        }
    }

    public void load() {
        try {
            Path path = getSettingsPath();
            if (Files.exists(path)) {
                String json = Files.readString(path);
                Gson gson = new Gson();
                @SuppressWarnings("unchecked")
                Map<String, Object> settings = gson.fromJson(json, Map.class);
                Object cbValue = settings.get("colorblindMode");
                if (cbValue instanceof String && !((String) cbValue).isEmpty()) {
                    colorblindMode = (String) cbValue;
                }
                Object themeValue = settings.get("theme");
                if (themeValue instanceof String && !((String) themeValue).isEmpty()) {
                    themeName = (String) themeValue;
                }
                Object scaleValue = settings.get("fontScale");
                if (scaleValue instanceof Number) {
                    setFontScale(((Number) scaleValue).floatValue());
                }
                applyTheme();
                applyColorblindPalette();
            }
        } catch (Exception e) {
            System.err.println("Failed to load settings: " + e.getMessage());
        }
    }
}
