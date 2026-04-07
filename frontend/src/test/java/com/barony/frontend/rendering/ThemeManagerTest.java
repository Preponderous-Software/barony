package com.barony.frontend.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThemeManagerTest {

    @Test
    void singletonInstanceIsNotNull() {
        ThemeManager instance = ThemeManager.getInstance();
        assertNotNull(instance);
    }

    @Test
    void defaultThemeIsDark() {
        ThemeManager tm = ThemeManager.getInstance();
        tm.setTheme("dark");
        tm.setColorblindMode("none");
        tm.setFontScale(1.0f);
        assertEquals("dark", tm.getThemeName());
    }

    @Test
    void defaultColorblindModeIsNone() {
        ThemeManager tm = ThemeManager.getInstance();
        tm.setColorblindMode("none");
        assertEquals("none", tm.getColorblindMode());
    }

    @Test
    void fontScaleDefaultsToOne() {
        ThemeManager tm = ThemeManager.getInstance();
        tm.setFontScale(1.0f);
        assertEquals(1.0f, tm.getFontScale(), 0.01f);
    }

    @Test
    void fontScaleClampedToValidRange() {
        ThemeManager tm = ThemeManager.getInstance();
        tm.setFontScale(0.1f);
        assertEquals(0.5f, tm.getFontScale(), 0.01f);
        tm.setFontScale(10.0f);
        assertEquals(2.0f, tm.getFontScale(), 0.01f);
        tm.setFontScale(1.0f); // Reset
    }

    @Test
    void colorblindModesChangePlayerColors() {
        ThemeManager tm = ThemeManager.getInstance();
        
        tm.setColorblindMode("none");
        float[] defaultP1 = tm.getPlayer1Color().clone();
        
        tm.setColorblindMode("deuteranopia");
        float[] deutP1 = tm.getPlayer1Color();
        assertFalse(defaultP1[0] == deutP1[0] && defaultP1[1] == deutP1[1] && defaultP1[2] == deutP1[2]);
        
        tm.setColorblindMode("none");
    }

    @Test
    void themeChangesColors() {
        ThemeManager tm = ThemeManager.getInstance();
        
        tm.setTheme("dark");
        float[] darkBg = tm.getBackgroundColor().clone();
        
        tm.setTheme("classic");
        float[] classicBg = tm.getBackgroundColor();
        assertFalse(darkBg[0] == classicBg[0] && darkBg[1] == classicBg[1] && darkBg[2] == classicBg[2]);
        
        tm.setTheme("high contrast");
        float[] hcBg = tm.getBackgroundColor();
        assertFalse(classicBg[0] == hcBg[0] && classicBg[1] == hcBg[1] && classicBg[2] == hcBg[2]);
        
        tm.setTheme("dark");
    }

    @Test
    void gettersReturnNonNullArrays() {
        ThemeManager tm = ThemeManager.getInstance();
        assertNotNull(tm.getPlayer1Color());
        assertNotNull(tm.getPlayer2Color());
        assertNotNull(tm.getPlayer1Tint());
        assertNotNull(tm.getPlayer2Tint());
        assertNotNull(tm.getBackgroundColor());
        assertNotNull(tm.getTextColor());
        assertNotNull(tm.getAccentColor());
        assertEquals(3, tm.getPlayer1Color().length);
        assertEquals(3, tm.getPlayer2Color().length);
        assertEquals(4, tm.getPlayer1Tint().length);
        assertEquals(4, tm.getPlayer2Tint().length);
    }

    @Test
    void protanopiaAndTritanopiaModesWork() {
        ThemeManager tm = ThemeManager.getInstance();
        
        tm.setColorblindMode("protanopia");
        assertNotNull(tm.getPlayer1Color());
        assertNotNull(tm.getPlayer2Color());
        
        tm.setColorblindMode("tritanopia");
        assertNotNull(tm.getPlayer1Color());
        assertNotNull(tm.getPlayer2Color());
        
        tm.setColorblindMode("none");
    }
}
