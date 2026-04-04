package com.barony.frontend;

import com.barony.frontend.client.GameClient;
import com.barony.frontend.model.*;
import com.barony.frontend.rendering.ThemeManager;
import com.barony.frontend.ui.SimpleTextRenderer;
import com.barony.frontend.ui.NotificationManager;
import com.barony.frontend.ui.ToastOverlay;
import com.barony.frontend.ui.NotificationLogPanel;
import com.barony.frontend.ui.SettingsPanel;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.*;

import java.nio.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class FrontendApplication {
    
    private long window;
    private GameClient client;
    private GameState gameState;
    private String lastWindowTitle = "";
    private boolean gameOverMessagePrinted = false; // Track if we've printed the game over message
    
    // Mouse state
    private double mouseX = 0;
    private double mouseY = 0;
    private int hoveredGridX = -1;
    private int hoveredGridY = -1;
    private long hoverStartTime = 0;
    private static final long TOOLTIP_DELAY_MS = 500;
    
    // Selection state
    private Integer selectedArmyId = null;
    
    // Game log (circular buffer)
    private java.util.LinkedList<String> gameLog = new java.util.LinkedList<>();
    private static final int MAX_LOG_ENTRIES = 10;
    private static final int MAX_PANEL_SOLDIERS = 20;
    
    // HUD layout constants (centralized to avoid duplication)
    private static final float GAME_LEFT = -1.0f;
    private static final float GAME_RIGHT = 0.6f;
    private static final float GAME_BOTTOM = -0.6f;
    private static final float GAME_TOP = 0.76f;
    
    // Cached window size to avoid allocations every frame
    private int windowWidth = 1280;
    private int windowHeight = 900;
    
    // Cached HUD counts to avoid recomputing every frame
    private int cachedPlayer1Armies = 0;
    private int cachedPlayer2Armies = 0;
    private int cachedPlayer1Castles = 0;
    private int cachedPlayer2Castles = 0;
    private int cachedPlayer1Villages = 0;
    private int cachedPlayer2Villages = 0;
    
    // Cached ruler stats
    private RulerStats cachedRulerStats = null;
    private long lastRulerStatsUpdate = 0;
    private int lastRulerStatsTickCount = -1; // Track tick count to detect changes
    private static final long RULER_STATS_UPDATE_INTERVAL_MS = 1000; // Update every second
    
    // Policy selection UI state
    private boolean policyMenuOpen = false;
    private String selectedPolicyCategory = null; // "ECONOMIC", "MILITARY", or "POPULATION"
    
    // Split mode UI state
    private boolean splitModeActive = false;
    private int splitModeArmyId = -1;
    private int splitModeTotalSoldiers = 0;
    
    // Overlay UI components
    private ToastOverlay toastOverlay = new ToastOverlay();
    private NotificationLogPanel notificationLogPanel = new NotificationLogPanel();
    private SettingsPanel settingsPanel = new SettingsPanel();
    
    public void run() {
        init();
        loop();
        
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }
    
    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }
        
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        
        window = glfwCreateWindow(1280, 900, "Barony Client", NULL, NULL);
        if (window == NULL) {
            throw new RuntimeException("Failed to create the GLFW window");
        }
        
        // Mouse position callback
        glfwSetCursorPosCallback(window, (window, xpos, ypos) -> {
            mouseX = xpos;
            mouseY = ypos;
            updateHoveredTile();
        });
        
        // Mouse button callback
        glfwSetMouseButtonCallback(window, (window, button, action, mods) -> {
            if (action == GLFW_PRESS) {
                handleMouseClick(button);
            }
        });
        
        // Framebuffer size callback: update viewport with framebuffer size,
        // and cache logical window size (in window coordinates) for input mapping.
        glfwSetFramebufferSizeCallback(window, (win, fbWidth, fbHeight) -> {
            // Query the window size in screen coordinates; this may differ from
            // framebuffer size on HiDPI/retina displays.
            try (MemoryStack stack = stackPush()) {
                IntBuffer pw = stack.mallocInt(1);
                IntBuffer ph = stack.mallocInt(1);
                glfwGetWindowSize(win, pw, ph);
                windowWidth = pw.get(0);
                windowHeight = ph.get(0);
            }

            // Use framebuffer size for the OpenGL viewport.
            glViewport(0, 0, fbWidth, fbHeight);
        });
        
        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                if (settingsPanel.isVisible()) {
                    settingsPanel.hide();
                } else if (notificationLogPanel.isVisible()) {
                    notificationLogPanel.hide();
                } else if (splitModeActive) {
                    splitModeActive = false;
                    splitModeArmyId = -1;
                    splitModeTotalSoldiers = 0;
                    NotificationManager.getInstance().info("Split mode cancelled");
                } else {
                    glfwSetWindowShouldClose(window, true);
                }
            }
            
            // F9 toggles settings panel
            if (key == GLFW_KEY_F9 && action == GLFW_RELEASE) {
                settingsPanel.toggle();
                return;
            }
            
            // F10 toggles notification log panel
            if (key == GLFW_KEY_F10 && action == GLFW_RELEASE) {
                notificationLogPanel.toggle();
                return;
            }
            
            // Notification log panel scroll handling
            if (notificationLogPanel.isVisible() && action == GLFW_RELEASE) {
                if (key == GLFW_KEY_UP) notificationLogPanel.scrollUp();
                else if (key == GLFW_KEY_DOWN) notificationLogPanel.scrollDown();
                return;
            }
            
            // Settings panel input handling
            if (settingsPanel.isVisible() && action == GLFW_RELEASE) {
                if (key == GLFW_KEY_UP) settingsPanel.navigateUp();
                else if (key == GLFW_KEY_DOWN) settingsPanel.navigateDown();
                else if (key == GLFW_KEY_LEFT) settingsPanel.navigateLeft();
                else if (key == GLFW_KEY_RIGHT) settingsPanel.navigateRight();
                return;
            }
            
            // Disable input when game is over (except R for reset)
            if (gameState != null && gameState.isGameOver()) {
                if (key == GLFW_KEY_R && action == GLFW_RELEASE) {
                    // Reset game
                    if (client != null) {
                        GameState newState = client.reset();
                        if (newState != null) {
                            gameState = newState;
                            updateCachedCounts();
                            gameOverMessagePrinted = false; // Reset flag
                        } else {
                            System.err.println("Failed to reset game: server returned no state. Previous state preserved.");
                        }
                    }
                }
                return; // Ignore all other input when game is over
            }
            
            if (key == GLFW_KEY_SPACE && action == GLFW_RELEASE) {
                // Send a tick command
                if (client != null) {
                    gameState = client.tick();
                    if (gameState != null) {
                        updateCachedCounts();
                        // Add log message for tick
                        addLogMessage("Tick " + gameState.getTickCount());
                    }
                }
            }
            if (key == GLFW_KEY_M && action == GLFW_RELEASE) {
                // Send a move command for first army using its ID
                if (client != null && gameState != null && gameState.getArmies() != null && !gameState.getArmies().isEmpty()) {
                    int firstArmyId = gameState.getArmies().get(0).getId();
                    Command cmd = new Command("MOVE", firstArmyId, 5, 5);
                    GameState newState = client.sendCommand(cmd);
                    if (newState != null) {
                        gameState = newState;
                        updateCachedCounts();
                    }
                }
            }
            // Split mode: number keys 1-9 select split amount
            if (splitModeActive && action == GLFW_RELEASE) {
                int splitAmount = -1;
                if (key == GLFW_KEY_1) splitAmount = 1;
                else if (key == GLFW_KEY_2) splitAmount = 2;
                else if (key == GLFW_KEY_3) splitAmount = 3;
                else if (key == GLFW_KEY_4) splitAmount = 4;
                else if (key == GLFW_KEY_5) splitAmount = 5;
                else if (key == GLFW_KEY_6) splitAmount = 6;
                else if (key == GLFW_KEY_7) splitAmount = 7;
                else if (key == GLFW_KEY_8) splitAmount = 8;
                else if (key == GLFW_KEY_9) splitAmount = 9;
                
                if (splitAmount >= 1) {
                    if (splitAmount < splitModeTotalSoldiers) {
                        Command cmd = new Command("SPLIT", splitModeArmyId, splitAmount);
                        GameState newState = client.sendCommand(cmd);
                        if (newState != null) {
                            gameState = newState;
                            updateCachedCounts();
                            addLogMessage("Split army " + splitModeArmyId + ": " + splitAmount + " soldiers");
                            System.out.println("Split command sent for army ID " + splitModeArmyId + ", splitting off " + splitAmount + " soldiers");
                        } else {
                            System.out.println("Split command failed for army ID " + splitModeArmyId + "; no updated game state received.");
                        }
                        splitModeActive = false;
                        splitModeArmyId = -1;
                        splitModeTotalSoldiers = 0;
                    } else {
                        System.out.println("Invalid split amount: " + splitAmount + ". Must be between 1 and " + (splitModeTotalSoldiers - 1));
                    }
                }
            }
            if (key == GLFW_KEY_S && action == GLFW_RELEASE) {
                if (splitModeActive) {
                    // Cancel split mode
                    splitModeActive = false;
                    splitModeArmyId = -1;
                    splitModeTotalSoldiers = 0;
                    System.out.println("Split mode cancelled");
                } else if (client != null && gameState != null && gameState.getArmies() != null && !gameState.getArmies().isEmpty()) {
                    Army firstArmy = gameState.getArmies().get(0);
                    int firstArmyId = firstArmy.getId();
                    int totalSoldiers = firstArmy.getSoldiers();
                    
                    if (totalSoldiers <= 1) {
                        // Army too small to split
                    } else {
                        splitModeActive = true;
                        splitModeArmyId = firstArmyId;
                        splitModeTotalSoldiers = totalSoldiers;
                        // Close policy menu if open
                        policyMenuOpen = false;
                        selectedPolicyCategory = null;
                        int maxSplit = Math.min(totalSoldiers - 1, 9);
                        System.out.println("Split mode: press 1-" + maxSplit + " to split off soldiers (S or ESC to cancel)");
                    }
                }
            }
            
            // P key - Toggle policy menu
            if (key == GLFW_KEY_P && action == GLFW_RELEASE) {
                if (policyMenuOpen) {
                    // Close menu
                    policyMenuOpen = false;
                    selectedPolicyCategory = null;
                } else {
                    // Open menu
                    policyMenuOpen = true;
                }
            }
            
            // Policy category selection when menu is open
            if (policyMenuOpen && action == GLFW_RELEASE) {
                if (key == GLFW_KEY_E) {
                    selectedPolicyCategory = "ECONOMIC";
                } else if (key == GLFW_KEY_M) {
                    selectedPolicyCategory = "MILITARY";
                } else if (key == GLFW_KEY_O) {
                    selectedPolicyCategory = "POPULATION";
                }
                
                // Policy choice selection (1, 2, 3) when category is selected
                if (selectedPolicyCategory != null && client != null) {
                    String choice = null;
                    if (key == GLFW_KEY_1) {
                        if (selectedPolicyCategory.equals("ECONOMIC")) choice = "HEAVY_TAXATION";
                        else if (selectedPolicyCategory.equals("MILITARY")) choice = "AGGRESSIVE_TRAINING";
                        else if (selectedPolicyCategory.equals("POPULATION")) choice = "GROWTH_FOCUS";
                    } else if (key == GLFW_KEY_2) {
                        if (selectedPolicyCategory.equals("ECONOMIC")) choice = "BALANCED_BUDGET";
                        else if (selectedPolicyCategory.equals("MILITARY")) choice = "STANDARD_SERVICE";
                        else if (selectedPolicyCategory.equals("POPULATION")) choice = "STABLE_POPULATION";
                    } else if (key == GLFW_KEY_3) {
                        if (selectedPolicyCategory.equals("ECONOMIC")) choice = "INFRASTRUCTURE_INVESTMENT";
                        else if (selectedPolicyCategory.equals("MILITARY")) choice = "VETERAN_BENEFITS";
                        else if (selectedPolicyCategory.equals("POPULATION")) choice = "QUALITY_OVER_QUANTITY";
                    }
                    
                    if (choice != null) {
                        GameState newState = client.changePolicy(selectedPolicyCategory, choice);
                        if (newState != null) {
                            gameState = newState;
                            updateCachedCounts();
                        }
                        // Close menu after selection
                        policyMenuOpen = false;
                        selectedPolicyCategory = null;
                    }
                }
            }
        });
        
        try (MemoryStack stack = stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, pWidth, pHeight);
            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(
                window,
                (vidmode.width() - pWidth.get(0)) / 2,
                (vidmode.height() - pHeight.get(0)) / 2
            );
        }
        
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        
        // Display loading message
        glfwSetWindowTitle(window, "Barony - Connecting to server...");
        
        // Initialize game client
        client = new GameClient("http://localhost:8080");
        gameState = client.getState();
        
        // Update title with game state once connected
        if (gameState != null) {
            glfwSetWindowTitle(window, "Barony - Connected");
        }
    }
    
    private void loop() {
        GL.createCapabilities();
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        
        // Initial count update
        updateCachedCounts();
        
        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            
            updateWindowTitle();
            render();
            
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }
    
    private void updateWindowTitle() {
        if (gameState == null || gameState.getGrid() == null) {
            return;
        }
        
        // Count territory ownership
        int player1Castles = 0;
        int player2Castles = 0;
        int player1Villages = 0;
        int player2Villages = 0;
        
        Tile[][] grid = gameState.getGrid();
        for (int x = 0; x < grid.length; x++) {
            for (int y = 0; y < grid[0].length; y++) {
                Tile tile = grid[x][y];
                if (tile.getType() == TileType.CASTLE) {
                    if (tile.getOwnerId() == 1) player1Castles++;
                    else if (tile.getOwnerId() == 2) player2Castles++;
                } else if (tile.getType() == TileType.VILLAGE) {
                    if (tile.getOwnerId() == 1) player1Villages++;
                    else if (tile.getOwnerId() == 2) player2Villages++;
                }
            }
        }
        
        int player1Income = player1Villages;
        int player2Income = player2Villages;
        
        String title = String.format("Barony Client | P1: %dC %dV +%d/tick | P2: %dC %dV +%d/tick | Tick: %d",
            player1Castles, player1Villages, player1Income,
            player2Castles, player2Villages, player2Income,
            gameState.getTickCount());
        
        // Only update window title if it changed
        if (!title.equals(lastWindowTitle)) {
            glfwSetWindowTitle(window, title);
            lastWindowTitle = title;
        }
    }
    
    private void render() {
        if (gameState == null || gameState.getGrid() == null) {
            return;
        }
        
        Tile[][] grid = gameState.getGrid();
        int gridWidth = grid.length;
        int gridHeight = grid.length > 0 ? grid[0].length : 0;
        
        float cellWidth = 2.0f / gridWidth;
        float cellHeight = 2.0f / gridHeight;
        
        // Adjust rendering to account for HUD space
        float gameWidth = GAME_RIGHT - GAME_LEFT;
        float gameHeight = GAME_TOP - GAME_BOTTOM;
        
        cellWidth = gameWidth / gridWidth;
        cellHeight = gameHeight / gridHeight;
        
        // Draw tiles
        for (int x = 0; x < gridWidth; x++) {
            for (int y = 0; y < gridHeight; y++) {
                float x1 = GAME_LEFT + x * cellWidth;
                float y1 = GAME_BOTTOM + y * cellHeight;
                float x2 = x1 + cellWidth;
                float y2 = y1 + cellHeight;
                
                Tile tile = grid[x][y];
                TileType type = tile.getType();
                int ownerId = tile.getOwnerId();
                
                switch (type) {
                    case CASTLE:
                        // Gray base with colored outline for owned castles
                        glColor3f(0.5f, 0.5f, 0.5f); // Gray
                        break;
                    case VILLAGE:
                        // Brown base with ownership tint
                        if (ownerId == 1) {
                            glColor3f(0.4f, 0.3f, 0.6f); // Brown with blue tint
                        } else if (ownerId == 2) {
                            glColor3f(0.7f, 0.2f, 0.0f); // Brown with red tint
                        } else {
                            glColor3f(0.6f, 0.3f, 0.0f); // Brown (neutral)
                        }
                        break;
                    case EMPTY:
                        glColor3f(0.0f, 0.5f, 0.0f); // Green
                        break;
                }
                
                glBegin(GL_QUADS);
                glVertex2f(x1, y1);
                glVertex2f(x2, y1);
                glVertex2f(x2, y2);
                glVertex2f(x1, y2);
                glEnd();
                
                // Draw grid lines
                glColor3f(0.0f, 0.0f, 0.0f);
                glBegin(GL_LINE_LOOP);
                glVertex2f(x1, y1);
                glVertex2f(x2, y1);
                glVertex2f(x2, y2);
                glVertex2f(x1, y2);
                glEnd();
                
                // Draw colored outline for owned castles
                if (type == TileType.CASTLE && ownerId > 0) {
                    if (ownerId == 1) {
                        glColor3f(0.0f, 0.0f, 1.0f); // Blue outline for Player 1
                    } else if (ownerId == 2) {
                        glColor3f(1.0f, 0.0f, 0.0f); // Red outline for Player 2
                    }
                    
                    glLineWidth(3.0f);
                    glBegin(GL_LINE_LOOP);
                    glVertex2f(x1, y1);
                    glVertex2f(x2, y1);
                    glVertex2f(x2, y2);
                    glVertex2f(x1, y2);
                    glEnd();
                    glLineWidth(1.0f);
                }
                
                // Draw capture progress bar for contested castles
                if (type == TileType.CASTLE && tile.getOccupationTicks() > 0) {
                    float barHeight = cellHeight / 10;
                    float barY = y2 - barHeight; // Just below top of tile
                    float barWidth = cellWidth * 0.8f;
                    float barX1 = x1 + (cellWidth - barWidth) / 2;
                    float barX2 = barX1 + barWidth;
                    
                    // Background (gray)
                    glColor3f(0.3f, 0.3f, 0.3f);
                    glBegin(GL_QUADS);
                    glVertex2f(barX1, barY);
                    glVertex2f(barX2, barY);
                    glVertex2f(barX2, barY - barHeight);
                    glVertex2f(barX1, barY - barHeight);
                    glEnd();
                    
                    // Progress fill (red for enemy occupation)
                    float progress = Math.min(tile.getOccupationTicks(), 3) / 3.0f;
                    float fillWidth = barWidth * progress;
                    glColor3f(1.0f, 0.0f, 0.0f); // Red
                    glBegin(GL_QUADS);
                    glVertex2f(barX1, barY);
                    glVertex2f(barX1 + fillWidth, barY);
                    glVertex2f(barX1 + fillWidth, barY - barHeight);
                    glVertex2f(barX1, barY - barHeight);
                    glEnd();
                    
                    // Border
                    glColor3f(0.0f, 0.0f, 0.0f);
                    glBegin(GL_LINE_LOOP);
                    glVertex2f(barX1, barY);
                    glVertex2f(barX2, barY);
                    glVertex2f(barX2, barY - barHeight);
                    glVertex2f(barX1, barY - barHeight);
                    glEnd();
                }
            }
        }
        
        // Draw armies
        if (gameState.getArmies() != null) {
            // Group armies by location for offset rendering
            java.util.Map<String, java.util.List<Army>> armiesByLocation = new java.util.HashMap<>();
            for (Army army : gameState.getArmies()) {
                String locationKey = army.getX() + "," + army.getY();
                armiesByLocation.computeIfAbsent(locationKey, k -> new java.util.ArrayList<>()).add(army);
            }
            
            for (java.util.Map.Entry<String, java.util.List<Army>> entry : armiesByLocation.entrySet()) {
                java.util.List<Army> armies = entry.getValue();
                
                for (int i = 0; i < armies.size(); i++) {
                    Army army = armies.get(i);
                    int armyX = army.getX();
                    int armyY = army.getY();
                    
                    // Calculate offset for multiple armies at same location
                    float offsetX = 0;
                    float offsetY = 0;
                    if (armies.size() > 1) {
                        // Offset in a circular pattern
                        double angle = 2 * Math.PI * i / armies.size();
                        offsetX = (float) (Math.cos(angle) * cellWidth / 8);
                        offsetY = (float) (Math.sin(angle) * cellHeight / 8);
                    }
                    
                    // Draw destination indicator if army is moving
                    if (army.isMoving()) {
                        int destX = army.getDestinationX();
                        int destY = army.getDestinationY();
                        
                        float destCenterX = GAME_LEFT + destX * cellWidth + cellWidth / 2;
                        float destCenterY = GAME_BOTTOM + destY * cellHeight + cellHeight / 2;
                        
                        // Draw destination square (lighter color based on player)
                        if (army.getPlayerId() == 1) {
                            glColor3f(0.5f, 0.5f, 1.0f); // Light blue
                        } else {
                            glColor3f(1.0f, 0.5f, 0.5f); // Light red
                        }
                        
                        float squareSize = Math.min(cellWidth, cellHeight) / 3;
                        glBegin(GL_LINE_LOOP);
                        glVertex2f(destCenterX - squareSize, destCenterY - squareSize);
                        glVertex2f(destCenterX + squareSize, destCenterY - squareSize);
                        glVertex2f(destCenterX + squareSize, destCenterY + squareSize);
                        glVertex2f(destCenterX - squareSize, destCenterY + squareSize);
                        glEnd();
                    }
                    
                    float centerX = GAME_LEFT + armyX * cellWidth + cellWidth / 2 + offsetX;
                    float centerY = GAME_BOTTOM + armyY * cellHeight + cellHeight / 2 + offsetY;
                    float radius = cellWidth / 4;
                    
                    // Color based on player (uses ThemeManager for colorblind support)
                    float[] p1Color = ThemeManager.getInstance().getPlayer1Color();
                    float[] p2Color = ThemeManager.getInstance().getPlayer2Color();
                    if (army.getPlayerId() == 1) {
                        glColor3f(p1Color[0], p1Color[1], p1Color[2]);
                    } else {
                        glColor3f(p2Color[0], p2Color[1], p2Color[2]);
                    }
                    
                    // Draw circle for army
                    glBegin(GL_TRIANGLE_FAN);
                    glVertex2f(centerX, centerY);
                    for (int j = 0; j <= 20; j++) {
                        float angle = (float) (2 * Math.PI * j / 20);
                        float x = centerX + radius * (float) Math.cos(angle);
                        float y = centerY + radius * (float) Math.sin(angle);
                        glVertex2f(x, y);
                    }
                    glEnd();
                    
                    // Draw a white circle in the center as a visual background for soldier count indicators
                    // Since LWJGL doesn't have built-in text rendering, we use simple shapes
                    // The soldier count is represented by the colored dots drawn below, not by this circle's size
                    glColor3f(1.0f, 1.0f, 1.0f); // White
                    float textRadius = radius * 0.5f;
                    glBegin(GL_TRIANGLE_FAN);
                    glVertex2f(centerX, centerY);
                    for (int j = 0; j <= 20; j++) {
                        float angle = (float) (2 * Math.PI * j / 20);
                        float x = centerX + textRadius * (float) Math.cos(angle);
                        float y = centerY + textRadius * (float) Math.sin(angle);
                        glVertex2f(x, y);
                    }
                    glEnd();
                    
                    // Draw soldier count indicator using small dots (limited to showing up to 10)
                    int displaySoldiers = Math.min(army.getSoldiers(), 10);
                    if (army.getPlayerId() == 1) {
                        glColor3f(0.0f, 0.0f, 0.5f); // Dark blue
                    } else {
                        glColor3f(0.5f, 0.0f, 0.0f); // Dark red
                    }
                    
                    // Draw dots in a grid pattern
                    float dotRadius = textRadius / 5;
                    int dotsPerRow = 3;
                    for (int d = 0; d < displaySoldiers; d++) {
                        int row = d / dotsPerRow;
                        int col = d % dotsPerRow;
                        float dotX = centerX - dotRadius * 2 + col * dotRadius * 2;
                        float dotY = centerY - dotRadius * 2 + row * dotRadius * 2;
                        
                        glBegin(GL_TRIANGLE_FAN);
                        glVertex2f(dotX, dotY);
                        for (int j = 0; j <= 8; j++) {
                            float angle = (float) (2 * Math.PI * j / 8);
                            float x = dotX + dotRadius * (float) Math.cos(angle);
                            float y = dotY + dotRadius * (float) Math.sin(angle);
                            glVertex2f(x, y);
                        }
                        glEnd();
                    }
                }
            }
        }
        
        // Render selection highlight
        renderSelectionHighlight();
        
        // Render movement preview
        renderMovementPreview();
        
        // Render HUD
        renderHUD();
        
        // Render tooltip
        renderTooltip();
        
        // Draw win/loss overlay if game is over
        if (gameState.isGameOver()) {
            // Draw semi-transparent overlay
            glColor4f(0.0f, 0.0f, 0.0f, 0.7f);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glBegin(GL_QUADS);
            glVertex2f(-1.0f, -1.0f);
            glVertex2f(1.0f, -1.0f);
            glVertex2f(1.0f, 1.0f);
            glVertex2f(-1.0f, 1.0f);
            glEnd();
            glDisable(GL_BLEND);
            
            // Draw win/loss text box
            float boxWidth = 0.8f;
            float boxHeight = 0.4f;
            
            // Determine winner and color
            Integer winnerId = gameState.getWinnerId();
            boolean playerWon = winnerId != null && winnerId.intValue() == 1;
            
            // Box background
            if (playerWon) {
                glColor3f(0.0f, 0.3f, 0.0f); // Dark green for win
            } else {
                glColor3f(0.3f, 0.0f, 0.0f); // Dark red for loss
            }
            glBegin(GL_QUADS);
            glVertex2f(-boxWidth, -boxHeight);
            glVertex2f(boxWidth, -boxHeight);
            glVertex2f(boxWidth, boxHeight);
            glVertex2f(-boxWidth, boxHeight);
            glEnd();
            
            // Box border
            if (playerWon) {
                glColor3f(0.0f, 1.0f, 0.0f); // Bright green
            } else {
                glColor3f(1.0f, 0.0f, 0.0f); // Bright red
            }
            glLineWidth(4.0f);
            glBegin(GL_LINE_LOOP);
            glVertex2f(-boxWidth, -boxHeight);
            glVertex2f(boxWidth, -boxHeight);
            glVertex2f(boxWidth, boxHeight);
            glVertex2f(-boxWidth, boxHeight);
            glEnd();
            glLineWidth(1.0f);
            
            // Draw large title text representation (using shapes since no text rendering)
            // We'll draw a large colored bar to represent the text
            if (playerWon) {
                glColor3f(0.0f, 1.0f, 0.0f); // Green
            } else {
                glColor3f(1.0f, 0.0f, 0.0f); // Red
            }
            
            // Draw text indicator (large rectangle at top of box)
            float textHeight = 0.15f;
            float textWidth = 0.6f;
            glBegin(GL_QUADS);
            glVertex2f(-textWidth, boxHeight - 0.1f - textHeight);
            glVertex2f(textWidth, boxHeight - 0.1f - textHeight);
            glVertex2f(textWidth, boxHeight - 0.1f);
            glVertex2f(-textWidth, boxHeight - 0.1f);
            glEnd();
            
            // Draw "Press R to Play Again" indicator (smaller rectangle at bottom)
            glColor3f(1.0f, 1.0f, 1.0f); // White
            float buttonHeight = 0.08f;
            float buttonWidth = 0.5f;
            glBegin(GL_QUADS);
            glVertex2f(-buttonWidth, -boxHeight + 0.1f);
            glVertex2f(buttonWidth, -boxHeight + 0.1f);
            glVertex2f(buttonWidth, -boxHeight + 0.1f + buttonHeight);
            glVertex2f(-buttonWidth, -boxHeight + 0.1f + buttonHeight);
            glEnd();
            
            // Print message to console only once
            if (!gameOverMessagePrinted) {
                if (playerWon) {
                    System.out.println("=== VICTORY! Player 1 Wins! ===");
                    System.out.println("Press R to play again");
                } else {
                    System.out.println("=== DEFEAT! Player 2 Wins! ===");
                    System.out.println("Press R to play again");
                }
                gameOverMessagePrinted = true;
            }
        }
        
        // Render overlay UI components — panels first, toasts last (highest Z-order)
        settingsPanel.render(windowWidth, windowHeight);
        notificationLogPanel.render(windowWidth, windowHeight);
        toastOverlay.render(windowWidth, windowHeight);
    }
    
    private void updateHoveredTile() {
        if (gameState == null || gameState.getGrid() == null) {
            return;
        }
        
        // Guard against minimized window (width/height could be 0)
        if (windowWidth <= 0 || windowHeight <= 0) {
            hoveredGridX = -1;
            hoveredGridY = -1;
            return;
        }
        
        // Convert mouse position to normalized coordinates
        float normX = (float) (mouseX / windowWidth * 2 - 1);
        float normY = (float) (1 - mouseY / windowHeight * 2);
        
        // Check if mouse is in game area
        if (normX < GAME_LEFT || normX > GAME_RIGHT || normY < GAME_BOTTOM || normY > GAME_TOP) {
            hoveredGridX = -1;
            hoveredGridY = -1;
            return;
        }
        
        // Convert to grid coordinates
        int gridWidth = gameState.getGrid().length;
        int gridHeight = gameState.getGrid()[0].length;
        
        float gameWidth = GAME_RIGHT - GAME_LEFT;
        float gameHeight = GAME_TOP - GAME_BOTTOM;
        
        int newGridX = (int) ((normX - GAME_LEFT) / gameWidth * gridWidth);
        int newGridY = (int) ((normY - GAME_BOTTOM) / gameHeight * gridHeight);
        
        // Clamp to valid grid index ranges to avoid off-by-one at right/top edges
        if (newGridX < 0) {
            newGridX = 0;
        } else if (newGridX >= gridWidth) {
            newGridX = gridWidth - 1;
        }
        
        if (newGridY < 0) {
            newGridY = 0;
        } else if (newGridY >= gridHeight) {
            newGridY = gridHeight - 1;
        }
        
        // Check if hovered tile changed
        if (newGridX != hoveredGridX || newGridY != hoveredGridY) {
            hoveredGridX = newGridX;
            hoveredGridY = newGridY;
            hoverStartTime = System.currentTimeMillis();
        }
    }
    
    private void handleMouseClick(int button) {
        if (gameState == null || gameState.getGrid() == null || client == null) {
            return;
        }
        
        // Ignore clicks when game is over
        if (gameState.isGameOver()) {
            return;
        }
        
        int gridWidth = gameState.getGrid().length;
        int gridHeight = gameState.getGrid()[0].length;
        
        if (hoveredGridX < 0 || hoveredGridX >= gridWidth || hoveredGridY < 0 || hoveredGridY >= gridHeight) {
            return;
        }
        
        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            // Check if clicking on an army to select it
            Army clickedArmy = getArmyAt(hoveredGridX, hoveredGridY);
            
            if (clickedArmy != null && clickedArmy.getPlayerId() == 1) {
                // Select Player 1 army
                selectedArmyId = clickedArmy.getId();
                addLogMessage("Selected army #" + selectedArmyId + " (" + clickedArmy.getSoldiers() + " soldiers)");
            } else if (selectedArmyId != null) {
                // Move selected army to clicked tile
                Command cmd = new Command("MOVE", selectedArmyId, hoveredGridX, hoveredGridY);
                GameState newState = client.sendCommand(cmd);
                if (newState != null) {
                    gameState = newState;
                    updateCachedCounts();
                    addLogMessage("Army #" + selectedArmyId + " moving to (" + hoveredGridX + "," + hoveredGridY + ")");
                } else {
                    addLogMessage("Failed to move army #" + selectedArmyId + " to (" + hoveredGridX + "," + hoveredGridY + ")");
                    System.err.println("Failed to send move command for army ID " + selectedArmyId + " to (" + hoveredGridX + "," + hoveredGridY + ")");
                }
            }
        } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
            // Right-click to deselect
            if (selectedArmyId != null) {
                addLogMessage("Deselected army #" + selectedArmyId);
                selectedArmyId = null;
            }
        }
    }
    
    private Army getArmyAt(int x, int y) {
        if (gameState == null || gameState.getArmies() == null) {
            return null;
        }
        
        // Find armies at this position (prioritize Player 1 armies)
        Army firstArmy = null;
        for (Army army : gameState.getArmies()) {
            if (army.getX() == x && army.getY() == y) {
                if (army.getPlayerId() == 1) {
                    return army; // Return Player 1 army immediately
                }
                if (firstArmy == null) {
                    firstArmy = army; // Store first army found
                }
            }
        }
        
        return firstArmy;
    }
    
    private void updateCachedCounts() {
        if (gameState == null) {
            cachedPlayer1Armies = 0;
            cachedPlayer2Armies = 0;
            cachedPlayer1Castles = 0;
            cachedPlayer2Castles = 0;
            cachedPlayer1Villages = 0;
            cachedPlayer2Villages = 0;
            return;
        }
        
        // Count armies
        cachedPlayer1Armies = 0;
        cachedPlayer2Armies = 0;
        if (gameState.getArmies() != null) {
            for (Army army : gameState.getArmies()) {
                if (army.getPlayerId() == 1) cachedPlayer1Armies++;
                else if (army.getPlayerId() == 2) cachedPlayer2Armies++;
            }
        }
        
        // Count territories
        cachedPlayer1Castles = 0;
        cachedPlayer2Castles = 0;
        cachedPlayer1Villages = 0;
        cachedPlayer2Villages = 0;
        if (gameState.getGrid() != null) {
            Tile[][] grid = gameState.getGrid();
            for (int x = 0; x < grid.length; x++) {
                for (int y = 0; y < grid[0].length; y++) {
                    Tile tile = grid[x][y];
                    if (tile.getType() == TileType.CASTLE) {
                        if (tile.getOwnerId() == 1) cachedPlayer1Castles++;
                        else if (tile.getOwnerId() == 2) cachedPlayer2Castles++;
                    } else if (tile.getType() == TileType.VILLAGE) {
                        if (tile.getOwnerId() == 1) cachedPlayer1Villages++;
                        else if (tile.getOwnerId() == 2) cachedPlayer2Villages++;
                    }
                }
            }
        }
        
        // Update ruler stats whenever cached counts are refreshed to avoid stale HUD data
        if (client != null) {
            long currentTime = System.currentTimeMillis();
            int currentTick = gameState.getTickCount();
            
            // Refresh if tick changed OR if enough time has passed
            boolean tickChanged = currentTick != lastRulerStatsTickCount;
            boolean timeElapsed = (currentTime - lastRulerStatsUpdate) >= RULER_STATS_UPDATE_INTERVAL_MS;
            
            if (tickChanged || timeElapsed) {
                cachedRulerStats = client.getRulerStats();
                lastRulerStatsUpdate = currentTime;
                lastRulerStatsTickCount = currentTick;
            }
        }
    }
    
    private Army getSelectedArmy() {
        if (selectedArmyId == null || gameState == null || gameState.getArmies() == null) {
            return null;
        }
        
        for (Army army : gameState.getArmies()) {
            if (army.getId() == selectedArmyId) {
                return army;
            }
        }
        
        return null;
    }
    
    private String formatPolicyName(String policy) {
        if (policy == null) return "None";
        // Convert HEAVY_TAXATION -> Heavy Taxation
        String[] parts = policy.split("_");
        if (parts.length == 0) return policy;
        
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) continue;
            String lower = part.toLowerCase();
            char firstChar = Character.toUpperCase(lower.charAt(0));
            String word = firstChar + lower.substring(1);
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(word);
        }
        
        // Fallback: if all parts were empty, return the original policy string
        return result.length() > 0 ? result.toString() : policy;
    }
    
    private void addLogMessage(String message) {
        gameLog.addFirst(message);
        if (gameLog.size() > MAX_LOG_ENTRIES) {
            gameLog.removeLast();
        }
        // Also show as toast notification
        NotificationManager.getInstance().info(message);
    }
    
    private boolean shouldShowTooltip() {
        return System.currentTimeMillis() - hoverStartTime >= TOOLTIP_DELAY_MS;
    }
    
    private void renderHUD() {
        if (gameState == null) {
            return;
        }
        
        // Render top bar (dark background)
        glColor3f(0.1f, 0.1f, 0.1f);
        glBegin(GL_QUADS);
        glVertex2f(-1.0f, GAME_TOP);
        glVertex2f(1.0f, GAME_TOP);
        glVertex2f(1.0f, 1.0f);
        glVertex2f(-1.0f, 1.0f);
        glEnd();
        
        // Top bar border
        glColor3f(0.5f, 0.5f, 0.5f);
        glBegin(GL_LINES);
        glVertex2f(-1.0f, GAME_TOP);
        glVertex2f(1.0f, GAME_TOP);
        glEnd();
        
        // Use cached counts instead of recomputing every frame
        int player1Armies = cachedPlayer1Armies;
        int player2Armies = cachedPlayer2Armies;
        int player1Castles = cachedPlayer1Castles;
        int player2Castles = cachedPlayer2Castles;
        int player1Villages = cachedPlayer1Villages;
        int player2Villages = cachedPlayer2Villages;
        
        // Visualize stats in the top HUD as simple proportional bars
        float hudLeft = -0.95f;
        float hudRight = 0.15f;
        float barsTop = 0.94f;
        float barHeight = 0.03f;
        float barSpacing = 0.01f;
        
        // Armies bar (top row)
        int totalArmies = player1Armies + player2Armies;
        if (totalArmies > 0) {
            float armiesBarTop = barsTop;
            float armiesBarBottom = barsTop - barHeight;
            float armiesMid = hudLeft + (hudRight - hudLeft) * (player1Armies / (float) totalArmies);
            
            // Player 1 armies segment
            glColor3f(0.2f, 0.6f, 1.0f);
            glBegin(GL_QUADS);
            glVertex2f(hudLeft, armiesBarBottom);
            glVertex2f(armiesMid, armiesBarBottom);
            glVertex2f(armiesMid, armiesBarTop);
            glVertex2f(hudLeft, armiesBarTop);
            glEnd();
            
            // Player 2 armies segment
            glColor3f(1.0f, 0.3f, 0.3f);
            glBegin(GL_QUADS);
            glVertex2f(armiesMid, armiesBarBottom);
            glVertex2f(hudRight, armiesBarBottom);
            glVertex2f(hudRight, armiesBarTop);
            glVertex2f(armiesMid, armiesBarTop);
            glEnd();
        }
        
        // Castles bar (middle row)
        int totalCastles = player1Castles + player2Castles;
        if (totalCastles > 0) {
            float castlesBarTop = barsTop - (barHeight + barSpacing);
            float castlesBarBottom = castlesBarTop - barHeight;
            float castlesMid = hudLeft + (hudRight - hudLeft) * (player1Castles / (float) totalCastles);
            
            // Player 1 castles segment
            glColor3f(0.1f, 0.8f, 0.4f);
            glBegin(GL_QUADS);
            glVertex2f(hudLeft, castlesBarBottom);
            glVertex2f(castlesMid, castlesBarBottom);
            glVertex2f(castlesMid, castlesBarTop);
            glVertex2f(hudLeft, castlesBarTop);
            glEnd();
            
            // Player 2 castles segment
            glColor3f(0.9f, 0.8f, 0.2f);
            glBegin(GL_QUADS);
            glVertex2f(castlesMid, castlesBarBottom);
            glVertex2f(hudRight, castlesBarBottom);
            glVertex2f(hudRight, castlesBarTop);
            glVertex2f(castlesMid, castlesBarTop);
            glEnd();
        }
        
        // Villages bar (bottom row within the top HUD)
        int totalVillages = player1Villages + player2Villages;
        if (totalVillages > 0) {
            float villagesBarTop = barsTop - 2 * (barHeight + barSpacing);
            float villagesBarBottom = villagesBarTop - barHeight;
            float villagesMid = hudLeft + (hudRight - hudLeft) * (player1Villages / (float) totalVillages);
            
            // Player 1 villages segment
            glColor3f(0.5f, 0.5f, 1.0f);
            glBegin(GL_QUADS);
            glVertex2f(hudLeft, villagesBarBottom);
            glVertex2f(villagesMid, villagesBarBottom);
            glVertex2f(villagesMid, villagesBarTop);
            glVertex2f(hudLeft, villagesBarTop);
            glEnd();
            
            // Player 2 villages segment
            glColor3f(1.0f, 0.5f, 0.5f);
            glBegin(GL_QUADS);
            glVertex2f(villagesMid, villagesBarBottom);
            glVertex2f(hudRight, villagesBarBottom);
            glVertex2f(hudRight, villagesBarTop);
            glVertex2f(villagesMid, villagesBarTop);
            glEnd();
        }
        
        // Add text labels to the top HUD
        float labelScale = 0.005f;
        float smallLabelScale = 0.004f;
        
        // Tick count at top of HUD (above the bars)
        SimpleTextRenderer.drawText("TICK: " + gameState.getTickCount(), hudLeft, 0.98f, labelScale, 1.0f, 1.0f, 1.0f);
        
        // Bar labels on the right side of each bar
        float barLabelX = hudRight + 0.02f;
        
        // Armies bar label (centered on bar)
        SimpleTextRenderer.drawText("ARMIES   P1: " + player1Armies + "  P2: " + player2Armies, 
            barLabelX, barsTop - barHeight / 2, smallLabelScale, 0.9f, 0.9f, 0.9f);
        
        // Castles bar label
        SimpleTextRenderer.drawText("CASTLES  P1: " + player1Castles + "  P2: " + player2Castles, 
            barLabelX, barsTop - (barHeight + barSpacing) - barHeight / 2, smallLabelScale, 0.9f, 0.9f, 0.9f);
        
        // Villages bar label
        SimpleTextRenderer.drawText("VILLAGES P1: " + player1Villages + "  P2: " + player2Villages, 
            barLabelX, barsTop - 2 * (barHeight + barSpacing) - barHeight / 2, smallLabelScale, 0.9f, 0.9f, 0.9f);
        
        // Income display below the bars
        float incomeY = barsTop - 3 * (barHeight + barSpacing) - 0.025f;
        SimpleTextRenderer.drawText("INCOME   P1: +" + player1Villages + "  P2: +" + player2Villages, 
            barLabelX, incomeY, smallLabelScale, 0.8f, 0.8f, 0.8f);
        
        // Render side panel (right side, dark background)
        glColor3f(0.1f, 0.1f, 0.1f);
        glBegin(GL_QUADS);
        glVertex2f(GAME_RIGHT, -1.0f);
        glVertex2f(1.0f, -1.0f);
        glVertex2f(1.0f, GAME_TOP);
        glVertex2f(GAME_RIGHT, GAME_TOP);
        glEnd();
        
        // Side panel border
        glColor3f(0.5f, 0.5f, 0.5f);
        glBegin(GL_LINES);
        glVertex2f(GAME_RIGHT, -1.0f);
        glVertex2f(GAME_RIGHT, GAME_TOP);
        glEnd();
        
        // Render selected army info in side panel
        Army selectedArmy = getSelectedArmy();
        if (selectedArmy != null) {
            float panelLeft = GAME_RIGHT + 0.02f;
            float panelRight = 0.98f;
            
            // Title bar for selected army
            glColor3f(0.2f, 0.2f, 0.3f);
            glBegin(GL_QUADS);
            glVertex2f(GAME_RIGHT, 0.65f);
            glVertex2f(1.0f, 0.65f);
            glVertex2f(1.0f, GAME_TOP);
            glVertex2f(GAME_RIGHT, GAME_TOP);
            glEnd();
            
            // Visual indicator of selected army
            if (selectedArmy.getPlayerId() == 1) {
                glColor3f(0.0f, 0.0f, 1.0f); // Blue
            } else {
                glColor3f(1.0f, 0.0f, 0.0f); // Red
            }
            
            // Draw army indicator circle
            float circleX = panelLeft + 0.03f;
            float circleY = GAME_TOP - 0.04f;
            float circleRadius = 0.025f;
            glBegin(GL_TRIANGLE_FAN);
            glVertex2f(circleX, circleY);
            for (int i = 0; i <= 20; i++) {
                float angle = (float) (2 * Math.PI * i / 20);
                float x = circleX + circleRadius * (float) Math.cos(angle);
                float y = circleY + circleRadius * (float) Math.sin(angle);
                glVertex2f(x, y);
            }
            glEnd();
            
            // Add text labels for selected army panel
            float panelTextScale = 0.005f;
            
            // Title
            SimpleTextRenderer.drawText("SELECTED ARMY", circleX + 0.05f, GAME_TOP - 0.02f, panelTextScale, 1.0f, 1.0f, 1.0f);
            
            // Army ID and Player
            String playerText = selectedArmy.getPlayerId() == 1 ? "P1" : "P2";
            SimpleTextRenderer.drawText(playerText, circleX + 0.05f, GAME_TOP - 0.07f, panelTextScale, 
                selectedArmy.getPlayerId() == 1 ? 0.3f : 1.0f, 
                selectedArmy.getPlayerId() == 1 ? 0.3f : 0.3f,
                selectedArmy.getPlayerId() == 1 ? 1.0f : 0.3f);
            SimpleTextRenderer.drawText("ID: " + selectedArmyId, circleX + 0.12f, GAME_TOP - 0.07f, panelTextScale, 0.9f, 0.9f, 0.9f);
            
            // Soldier count label
            SimpleTextRenderer.drawText("SOLDIERS: " + selectedArmy.getSoldiers(), panelLeft, GAME_TOP - 0.12f, panelTextScale, 1.0f, 1.0f, 0.0f);
            
            // Position
            SimpleTextRenderer.drawText("POS: (" + selectedArmy.getX() + "," + selectedArmy.getY() + ")", 
                panelLeft, GAME_TOP - 0.17f, panelTextScale, 0.8f, 0.8f, 0.8f);
            
            // Draw soldier count bars
            float soldierBarTop = GAME_TOP - 0.22f;
            float soldierBarHeight = 0.02f;
            int displaySoldiers = Math.min(selectedArmy.getSoldiers(), MAX_PANEL_SOLDIERS);
            for (int i = 0; i < displaySoldiers; i++) {
                if (selectedArmy.getPlayerId() == 1) {
                    glColor3f(0.3f, 0.3f, 0.8f);
                } else {
                    glColor3f(0.8f, 0.3f, 0.3f);
                }
                
                glBegin(GL_QUADS);
                glVertex2f(panelLeft, soldierBarTop - i * 0.025f);
                glVertex2f(panelRight, soldierBarTop - i * 0.025f);
                glVertex2f(panelRight, soldierBarTop - i * 0.025f - soldierBarHeight);
                glVertex2f(panelLeft, soldierBarTop - i * 0.025f - soldierBarHeight);
                glEnd();
            }
            
            // Destination if moving
            if (selectedArmy.isMoving()) {
                float destInfoY = soldierBarTop - displaySoldiers * 0.025f - 0.04f;
                
                glColor3f(0.3f, 0.8f, 0.3f);
                glBegin(GL_QUADS);
                glVertex2f(panelLeft, destInfoY);
                glVertex2f(panelRight, destInfoY);
                glVertex2f(panelRight, destInfoY + 0.06f);
                glVertex2f(panelLeft, destInfoY + 0.06f);
                glEnd();
                
                SimpleTextRenderer.drawText("MOVING TO:", panelLeft + 0.01f, destInfoY + 0.048f, panelTextScale, 0.1f, 0.3f, 0.1f);
                SimpleTextRenderer.drawText("(" + selectedArmy.getDestinationX() + "," + selectedArmy.getDestinationY() + ")", 
                    panelLeft + 0.01f, destInfoY + 0.008f, panelTextScale, 0.1f, 0.3f, 0.1f);
            }
        }
        
        // Render ruler stats panel (below selected army or at top if no selection)
        float statsY = selectedArmy != null ? 0.35f : GAME_TOP - 0.02f;
        float panelLeft = GAME_RIGHT + 0.02f;
        float panelTextScale = 0.004f;
        
        // Title for ruler stats
        SimpleTextRenderer.drawText("RULER STATS (P1)", panelLeft, statsY, panelTextScale, 1.0f, 0.8f, 0.2f);
        statsY -= 0.05f;
        
        // Use cached ruler stats from backend if available
        if (cachedRulerStats != null) {
            int avgMorale = (int) cachedRulerStats.getAverageMorale();
            int avgLoyalty = (int) cachedRulerStats.getAverageLoyalty();
            int avgStability = (int) cachedRulerStats.getAverageStability();
            
            // Morale (color based on value)
            float moraleColor = avgMorale >= 100 ? 0.2f : 1.0f;
            SimpleTextRenderer.drawText("Avg Morale: " + avgMorale + "%", panelLeft, statsY, panelTextScale, 
                moraleColor, 0.8f, moraleColor);
            statsY -= 0.04f;
            
            // Loyalty (color based on value)
            float loyaltyColor = avgLoyalty >= 80 ? 0.2f : 1.0f;
            SimpleTextRenderer.drawText("Avg Loyalty: " + avgLoyalty + "%", panelLeft, statsY, panelTextScale, 
                loyaltyColor, 0.8f, loyaltyColor);
            statsY -= 0.04f;
            
            // Stability (color based on value)
            float stabilityColor = avgStability >= 70 ? 0.2f : 1.0f;
            SimpleTextRenderer.drawText("Avg Stability: " + avgStability + "%", panelLeft, statsY, panelTextScale, 
                stabilityColor, 0.8f, stabilityColor);
            statsY -= 0.04f;
        }
        
        // Display current policies from cached ruler stats (with fallback to gameState)
        statsY -= 0.02f;
        SimpleTextRenderer.drawText("POLICIES:", panelLeft, statsY, panelTextScale, 0.8f, 0.8f, 0.8f);
        statsY -= 0.04f;
        
        String economicPolicy = cachedRulerStats != null && cachedRulerStats.getEconomicPolicy() != null ? 
            formatPolicyName(cachedRulerStats.getEconomicPolicy()) : 
            (gameState.getEconomicPolicy() != null ? formatPolicyName(gameState.getEconomicPolicy()) : "Balanced");
        SimpleTextRenderer.drawText("Econ: " + economicPolicy, panelLeft, statsY, panelTextScale, 0.7f, 0.7f, 0.7f);
        statsY -= 0.035f;
        
        String militaryPolicy = cachedRulerStats != null && cachedRulerStats.getMilitaryPolicy() != null ? 
            formatPolicyName(cachedRulerStats.getMilitaryPolicy()) : 
            (gameState.getMilitaryPolicy() != null ? formatPolicyName(gameState.getMilitaryPolicy()) : "Standard");
        SimpleTextRenderer.drawText("Mil: " + militaryPolicy, panelLeft, statsY, panelTextScale, 0.7f, 0.7f, 0.7f);
        statsY -= 0.035f;
        
        String populationPolicy = cachedRulerStats != null && cachedRulerStats.getPopulationPolicy() != null ? 
            formatPolicyName(cachedRulerStats.getPopulationPolicy()) : 
            (gameState.getPopulationPolicy() != null ? formatPolicyName(gameState.getPopulationPolicy()) : "Stable");
        SimpleTextRenderer.drawText("Pop: " + populationPolicy, panelLeft, statsY, panelTextScale, 0.7f, 0.7f, 0.7f);
        statsY -= 0.04f;
        
        // Policy cooldown from cached ruler stats (with fallback to gameState calculation)
        int ticksUntilNext;
        if (cachedRulerStats != null) {
            ticksUntilNext = cachedRulerStats.getTicksUntilNextDecision();
        } else {
            int ticksSinceLastChange = gameState.getTickCount() - gameState.getLastPolicyChangeTick();
            ticksUntilNext = Math.max(0, 15 - ticksSinceLastChange);
        }
        
        if (ticksUntilNext > 0) {
            SimpleTextRenderer.drawText("Cooldown: " + ticksUntilNext + " ticks", panelLeft, statsY, panelTextScale, 1.0f, 0.5f, 0.5f);
        } else {
            SimpleTextRenderer.drawText("Policy ready!", panelLeft, statsY, panelTextScale, 0.2f, 1.0f, 0.2f);
        }
        statsY -= 0.04f;
        
        // Policy menu hint
        SimpleTextRenderer.drawText("Press 'P' for policy menu", panelLeft, statsY, panelTextScale * 0.8f, 0.6f, 0.6f, 0.6f);
        
        // Render policy selection menu if open
        if (policyMenuOpen) {
            float menuCenterX = 0.0f;
            float menuCenterY = 0.0f;
            float menuWidth = 0.8f;
            float menuHeight = 0.6f;
            
            // Semi-transparent background overlay
            glColor4f(0.0f, 0.0f, 0.0f, 0.7f);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glBegin(GL_QUADS);
            glVertex2f(-1.0f, -1.0f);
            glVertex2f(1.0f, -1.0f);
            glVertex2f(1.0f, 1.0f);
            glVertex2f(-1.0f, 1.0f);
            glEnd();
            
            // Menu panel
            glColor3f(0.15f, 0.15f, 0.2f);
            glBegin(GL_QUADS);
            glVertex2f(menuCenterX - menuWidth/2, menuCenterY - menuHeight/2);
            glVertex2f(menuCenterX + menuWidth/2, menuCenterY - menuHeight/2);
            glVertex2f(menuCenterX + menuWidth/2, menuCenterY + menuHeight/2);
            glVertex2f(menuCenterX - menuWidth/2, menuCenterY + menuHeight/2);
            glEnd();
            
            // Menu border
            glColor3f(0.5f, 0.7f, 1.0f);
            glLineWidth(2.0f);
            glBegin(GL_LINE_LOOP);
            glVertex2f(menuCenterX - menuWidth/2, menuCenterY - menuHeight/2);
            glVertex2f(menuCenterX + menuWidth/2, menuCenterY - menuHeight/2);
            glVertex2f(menuCenterX + menuWidth/2, menuCenterY + menuHeight/2);
            glVertex2f(menuCenterX - menuWidth/2, menuCenterY + menuHeight/2);
            glEnd();
            glLineWidth(1.0f);
            glDisable(GL_BLEND);
            
            // Menu content
            float menuTextScale = 0.007f;
            float menuTextY = menuCenterY + menuHeight/2 - 0.05f;
            float menuTextX = menuCenterX - menuWidth/2 + 0.05f;
            
            SimpleTextRenderer.drawText("POLICY MENU", menuTextX, menuTextY, menuTextScale * 1.2f, 1.0f, 1.0f, 0.2f);
            menuTextY -= 0.08f;
            
            if (selectedPolicyCategory == null) {
                // Category selection
                SimpleTextRenderer.drawText("Select a policy category:", menuTextX, menuTextY, menuTextScale, 0.9f, 0.9f, 0.9f);
                menuTextY -= 0.08f;
                
                SimpleTextRenderer.drawText("[E] Economic Policies", menuTextX, menuTextY, menuTextScale, 0.7f, 1.0f, 0.7f);
                menuTextY -= 0.05f;
                SimpleTextRenderer.drawText("    Heavy Taxation / Balanced Budget / Infrastructure Investment", menuTextX, menuTextY, menuTextScale * 0.8f, 0.6f, 0.6f, 0.6f);
                menuTextY -= 0.07f;
                
                SimpleTextRenderer.drawText("[M] Military Policies", menuTextX, menuTextY, menuTextScale, 0.7f, 1.0f, 0.7f);
                menuTextY -= 0.05f;
                SimpleTextRenderer.drawText("    Aggressive Training / Standard Service / Veteran Benefits", menuTextX, menuTextY, menuTextScale * 0.8f, 0.6f, 0.6f, 0.6f);
                menuTextY -= 0.07f;
                
                SimpleTextRenderer.drawText("[O] Population Policies", menuTextX, menuTextY, menuTextScale, 0.7f, 1.0f, 0.7f);
                menuTextY -= 0.05f;
                SimpleTextRenderer.drawText("    Growth Focus / Stable Population / Quality Over Quantity", menuTextX, menuTextY, menuTextScale * 0.8f, 0.6f, 0.6f, 0.6f);
            } else {
                // Policy choice selection
                SimpleTextRenderer.drawText("Select a policy:", menuTextX, menuTextY, menuTextScale, 0.9f, 0.9f, 0.9f);
                menuTextY -= 0.08f;
                
                if (selectedPolicyCategory.equals("ECONOMIC")) {
                    SimpleTextRenderer.drawText("[1] Heavy Taxation", menuTextX, menuTextY, menuTextScale, 0.7f, 1.0f, 0.7f);
                    menuTextY -= 0.05f;
                    SimpleTextRenderer.drawText("    +20% income, -10% stability", menuTextX, menuTextY, menuTextScale * 0.8f, 0.6f, 0.6f, 0.6f);
                    menuTextY -= 0.07f;
                    
                    SimpleTextRenderer.drawText("[2] Balanced Budget", menuTextX, menuTextY, menuTextScale, 0.7f, 1.0f, 0.7f);
                    menuTextY -= 0.05f;
                    SimpleTextRenderer.drawText("    No modifiers (baseline)", menuTextX, menuTextY, menuTextScale * 0.8f, 0.6f, 0.6f, 0.6f);
                    menuTextY -= 0.07f;
                    
                    SimpleTextRenderer.drawText("[3] Infrastructure Investment", menuTextX, menuTextY, menuTextScale, 0.7f, 1.0f, 0.7f);
                    menuTextY -= 0.05f;
                    SimpleTextRenderer.drawText("    -10% income, +10% stability", menuTextX, menuTextY, menuTextScale * 0.8f, 0.6f, 0.6f, 0.6f);
                } else if (selectedPolicyCategory.equals("MILITARY")) {
                    SimpleTextRenderer.drawText("[1] Aggressive Training", menuTextX, menuTextY, menuTextScale, 0.7f, 1.0f, 0.7f);
                    menuTextY -= 0.05f;
                    SimpleTextRenderer.drawText("    +10% morale, -5% loyalty", menuTextX, menuTextY, menuTextScale * 0.8f, 0.6f, 0.6f, 0.6f);
                    menuTextY -= 0.07f;
                    
                    SimpleTextRenderer.drawText("[2] Standard Service", menuTextX, menuTextY, menuTextScale, 0.7f, 1.0f, 0.7f);
                    menuTextY -= 0.05f;
                    SimpleTextRenderer.drawText("    No modifiers (baseline)", menuTextX, menuTextY, menuTextScale * 0.8f, 0.6f, 0.6f, 0.6f);
                    menuTextY -= 0.07f;
                    
                    SimpleTextRenderer.drawText("[3] Veteran Benefits", menuTextX, menuTextY, menuTextScale, 0.7f, 1.0f, 0.7f);
                    menuTextY -= 0.05f;
                    SimpleTextRenderer.drawText("    -10% morale, +10% loyalty", menuTextX, menuTextY, menuTextScale * 0.8f, 0.6f, 0.6f, 0.6f);
                } else if (selectedPolicyCategory.equals("POPULATION")) {
                    SimpleTextRenderer.drawText("[1] Growth Focus", menuTextX, menuTextY, menuTextScale, 0.7f, 1.0f, 0.7f);
                    menuTextY -= 0.05f;
                    SimpleTextRenderer.drawText("    +15% population growth, -5% stability", menuTextX, menuTextY, menuTextScale * 0.8f, 0.6f, 0.6f, 0.6f);
                    menuTextY -= 0.07f;
                    
                    SimpleTextRenderer.drawText("[2] Stable Population", menuTextX, menuTextY, menuTextScale, 0.7f, 1.0f, 0.7f);
                    menuTextY -= 0.05f;
                    SimpleTextRenderer.drawText("    No modifiers (baseline)", menuTextX, menuTextY, menuTextScale * 0.8f, 0.6f, 0.6f, 0.6f);
                    menuTextY -= 0.07f;
                    
                    SimpleTextRenderer.drawText("[3] Quality Over Quantity", menuTextX, menuTextY, menuTextScale, 0.7f, 1.0f, 0.7f);
                    menuTextY -= 0.05f;
                    SimpleTextRenderer.drawText("    -10% population growth, +10% stability", menuTextX, menuTextY, menuTextScale * 0.8f, 0.6f, 0.6f, 0.6f);
                }
            }
            
            menuTextY -= 0.1f;
            SimpleTextRenderer.drawText("[P] Close Menu", menuTextX, menuTextY, menuTextScale, 1.0f, 0.5f, 0.5f);
        }
        
        // Render split mode overlay
        if (splitModeActive) {
            float menuCenterX = 0.0f;
            float menuCenterY = 0.0f;
            float menuWidth = 0.6f;
            float menuHeight = 0.3f;
            
            // Semi-transparent background overlay
            glColor4f(0.0f, 0.0f, 0.0f, 0.7f);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glBegin(GL_QUADS);
            glVertex2f(-1.0f, -1.0f);
            glVertex2f(1.0f, -1.0f);
            glVertex2f(1.0f, 1.0f);
            glVertex2f(-1.0f, 1.0f);
            glEnd();
            
            // Menu panel
            glColor3f(0.15f, 0.15f, 0.2f);
            glBegin(GL_QUADS);
            glVertex2f(menuCenterX - menuWidth/2, menuCenterY - menuHeight/2);
            glVertex2f(menuCenterX + menuWidth/2, menuCenterY - menuHeight/2);
            glVertex2f(menuCenterX + menuWidth/2, menuCenterY + menuHeight/2);
            glVertex2f(menuCenterX - menuWidth/2, menuCenterY + menuHeight/2);
            glEnd();
            
            // Menu border
            glColor3f(1.0f, 0.7f, 0.3f);
            glLineWidth(2.0f);
            glBegin(GL_LINE_LOOP);
            glVertex2f(menuCenterX - menuWidth/2, menuCenterY - menuHeight/2);
            glVertex2f(menuCenterX + menuWidth/2, menuCenterY - menuHeight/2);
            glVertex2f(menuCenterX + menuWidth/2, menuCenterY + menuHeight/2);
            glVertex2f(menuCenterX - menuWidth/2, menuCenterY + menuHeight/2);
            glEnd();
            glLineWidth(1.0f);
            glDisable(GL_BLEND);
            
            // Menu content
            float menuTextScale = 0.007f;
            float menuTextY = menuCenterY + menuHeight/2 - 0.05f;
            float menuTextX = menuCenterX - menuWidth/2 + 0.05f;
            
            SimpleTextRenderer.drawText("SPLIT ARMY", menuTextX, menuTextY, menuTextScale * 1.2f, 1.0f, 0.8f, 0.2f);
            menuTextY -= 0.08f;
            
            SimpleTextRenderer.drawText("Army ID: " + splitModeArmyId + "  Soldiers: " + splitModeTotalSoldiers, menuTextX, menuTextY, menuTextScale, 0.9f, 0.9f, 0.9f);
            menuTextY -= 0.07f;
            
            int maxSplit = Math.min(splitModeTotalSoldiers - 1, 9);
            SimpleTextRenderer.drawText("Press [1-" + maxSplit + "] to split off soldiers", menuTextX, menuTextY, menuTextScale, 0.7f, 1.0f, 0.7f);
            menuTextY -= 0.08f;
            
            SimpleTextRenderer.drawText("[S] or [ESC] Cancel", menuTextX, menuTextY, menuTextScale, 1.0f, 0.5f, 0.5f);
        }
        
        // Render bottom bar (game log)
        glColor3f(0.1f, 0.1f, 0.1f);
        glBegin(GL_QUADS);
        glVertex2f(-1.0f, -1.0f);
        glVertex2f(GAME_RIGHT, -1.0f);
        glVertex2f(GAME_RIGHT, GAME_BOTTOM);
        glVertex2f(-1.0f, GAME_BOTTOM);
        glEnd();
        
        // Bottom bar border
        glColor3f(0.5f, 0.5f, 0.5f);
        glBegin(GL_LINES);
        glVertex2f(-1.0f, GAME_BOTTOM);
        glVertex2f(GAME_RIGHT, GAME_BOTTOM);
        glEnd();
        
        // Draw game log entries as colored bars representing events
        float logStartY = GAME_BOTTOM - 0.02f;
        float logHeight = 0.02f;
        float logSpacing = 0.038f;
        float logTextScale = 0.004f;
        
        // Add title for game log
        SimpleTextRenderer.drawText("GAME LOG:", -0.98f, GAME_BOTTOM - 0.005f, 0.005f, 1.0f, 1.0f, 1.0f);
        
        float logEntryStartY = logStartY - 0.04f;
        for (int i = 0; i < Math.min(gameLog.size(), MAX_LOG_ENTRIES); i++) {
            // Alternate colors for visibility
            if (i % 2 == 0) {
                glColor3f(0.3f, 0.3f, 0.4f);
            } else {
                glColor3f(0.25f, 0.25f, 0.35f);
            }
            
            float entryY = logEntryStartY - i * logSpacing;
            glBegin(GL_QUADS);
            glVertex2f(-0.98f, entryY);
            glVertex2f(GAME_RIGHT - 0.02f, entryY);
            glVertex2f(GAME_RIGHT - 0.02f, entryY - logHeight);
            glVertex2f(-0.98f, entryY - logHeight);
            glEnd();
            
            // Draw the log message text
            String logMessage = gameLog.get(i);
            SimpleTextRenderer.drawText(logMessage, -0.97f, entryY - 0.002f, logTextScale, 1.0f, 1.0f, 1.0f);
        }
    }
    
    private void renderTooltip() {
        if (!shouldShowTooltip() || gameState == null || gameState.getGrid() == null) {
            return;
        }
        
        int gridWidth = gameState.getGrid().length;
        int gridHeight = gameState.getGrid()[0].length;
        
        if (hoveredGridX < 0 || hoveredGridX >= gridWidth || hoveredGridY < 0 || hoveredGridY >= gridHeight) {
            return;
        }
        
        // Guard against minimized window
        if (windowWidth <= 0 || windowHeight <= 0) {
            return;
        }
        
        // Get mouse position in normalized coords (using cached window size)
        float normX = (float) (mouseX / windowWidth * 2 - 1);
        float normY = (float) (1 - mouseY / windowHeight * 2);
        
        // Don't show tooltip if hovering over HUD areas
        if (normY > GAME_TOP || normX > GAME_RIGHT || normY < GAME_BOTTOM) {
            return;
        }
        
        // Draw tooltip box
        float tooltipWidth = 0.25f;
        float tooltipHeight = 0.25f;
        float tooltipX = normX + 0.05f;
        float tooltipY = normY - 0.05f;
        
        // Keep tooltip on screen
        if (tooltipX + tooltipWidth > GAME_RIGHT) tooltipX = normX - tooltipWidth - 0.05f;
        if (tooltipY - tooltipHeight < GAME_BOTTOM) tooltipY = normY + tooltipHeight + 0.05f;
        
        // Tooltip background
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(0.0f, 0.0f, 0.0f, 0.8f);
        glBegin(GL_QUADS);
        glVertex2f(tooltipX, tooltipY);
        glVertex2f(tooltipX + tooltipWidth, tooltipY);
        glVertex2f(tooltipX + tooltipWidth, tooltipY - tooltipHeight);
        glVertex2f(tooltipX, tooltipY - tooltipHeight);
        glEnd();
        
        // Tooltip border
        glColor4f(1.0f, 1.0f, 1.0f, 0.9f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(tooltipX, tooltipY);
        glVertex2f(tooltipX + tooltipWidth, tooltipY);
        glVertex2f(tooltipX + tooltipWidth, tooltipY - tooltipHeight);
        glVertex2f(tooltipX, tooltipY - tooltipHeight);
        glEnd();
        glDisable(GL_BLEND);
        
        // Get tile info
        Tile tile = gameState.getGrid()[hoveredGridX][hoveredGridY];
        Army army = getArmyAt(hoveredGridX, hoveredGridY);
        
        // Add text labels to tooltip
        float tooltipTextScale = 0.004f;
        float tooltipLineSpacing = 0.033f;
        float textY = tooltipY - 0.015f;
        
        // Position
        SimpleTextRenderer.drawText("TILE: (" + hoveredGridX + "," + hoveredGridY + ")", tooltipX + 0.01f, textY, tooltipTextScale, 1.0f, 1.0f, 1.0f);
        textY -= tooltipLineSpacing;
        
        // Tile type
        String tileTypeText = "TYPE: ";
        switch (tile.getType()) {
            case CASTLE:
                tileTypeText += "CASTLE";
                break;
            case VILLAGE:
                tileTypeText += "VILLAGE";
                break;
            case EMPTY:
                tileTypeText += "EMPTY";
                break;
        }
        SimpleTextRenderer.drawText(tileTypeText, tooltipX + 0.01f, textY, tooltipTextScale, 0.9f, 0.9f, 0.9f);
        textY -= tooltipLineSpacing;
        
        // Ownership
        String ownerText = "OWNER: ";
        if (tile.getOwnerId() == 1) {
            ownerText += "PLAYER 1";
            SimpleTextRenderer.drawText(ownerText, tooltipX + 0.01f, textY, tooltipTextScale, 0.3f, 0.3f, 1.0f);
        } else if (tile.getOwnerId() == 2) {
            ownerText += "PLAYER 2";
            SimpleTextRenderer.drawText(ownerText, tooltipX + 0.01f, textY, tooltipTextScale, 1.0f, 0.3f, 0.3f);
        } else {
            ownerText += "NEUTRAL";
            SimpleTextRenderer.drawText(ownerText, tooltipX + 0.01f, textY, tooltipTextScale, 0.7f, 0.7f, 0.7f);
        }
        textY -= tooltipLineSpacing;
        
        // Income for villages
        if (tile.getType() == TileType.VILLAGE && tile.getOwnerId() > 0) {
            SimpleTextRenderer.drawText("INCOME: +1/TICK", tooltipX + 0.01f, textY, tooltipTextScale, 0.3f, 1.0f, 0.3f);
            textY -= tooltipLineSpacing;
        }
        
        // Army info if present
        if (army != null) {
            String armyText = "ARMY ID: " + army.getId() + " (P" + army.getPlayerId() + ")";
            float armyR = army.getPlayerId() == 1 ? 0.3f : 1.0f;
            float armyG = army.getPlayerId() == 1 ? 0.3f : 0.3f;
            float armyB = army.getPlayerId() == 1 ? 1.0f : 0.3f;
            SimpleTextRenderer.drawText(armyText, tooltipX + 0.01f, textY, tooltipTextScale, armyR, armyG, armyB);
            textY -= tooltipLineSpacing;
            
            SimpleTextRenderer.drawText("SOLDIERS: " + army.getSoldiers(), tooltipX + 0.01f, textY, tooltipTextScale, 1.0f, 1.0f, 0.0f);
            textY -= tooltipLineSpacing;
            
            if (army.isMoving()) {
                SimpleTextRenderer.drawText("MOVING TO: (" + army.getDestinationX() + "," + army.getDestinationY() + ")", 
                    tooltipX + 0.01f, textY, tooltipTextScale, 0.3f, 1.0f, 0.3f);
            }
        }
    }
    
    private void renderSelectionHighlight() {
        if (selectedArmyId == null || gameState == null || gameState.getGrid() == null) {
            return;
        }
        
        Army selectedArmy = getSelectedArmy();
        if (selectedArmy == null) {
            return;
        }
        
        int gridWidth = gameState.getGrid().length;
        int gridHeight = gameState.getGrid()[0].length;
        
        float gameWidth = GAME_RIGHT - GAME_LEFT;
        float gameHeight = GAME_TOP - GAME_BOTTOM;
        
        float cellWidth = gameWidth / gridWidth;
        float cellHeight = gameHeight / gridHeight;
        
        int armyX = selectedArmy.getX();
        int armyY = selectedArmy.getY();
        
        float x1 = GAME_LEFT + armyX * cellWidth;
        float y1 = GAME_BOTTOM + armyY * cellHeight;
        float x2 = x1 + cellWidth;
        float y2 = y1 + cellHeight;
        
        // Draw glowing border effect
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Pulsing effect
        float pulse = (float) (0.5f + 0.3f * Math.sin(System.currentTimeMillis() / 200.0));
        
        if (selectedArmy.getPlayerId() == 1) {
            glColor4f(0.0f, 0.5f, 1.0f, pulse); // Light blue
        } else {
            glColor4f(1.0f, 0.5f, 0.0f, pulse); // Orange
        }
        
        glLineWidth(4.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x1, y1);
        glVertex2f(x2, y1);
        glVertex2f(x2, y2);
        glVertex2f(x1, y2);
        glEnd();
        glLineWidth(1.0f);
        
        glDisable(GL_BLEND);
    }
    
    private void renderMovementPreview() {
        if (selectedArmyId == null || hoveredGridX < 0 || hoveredGridY < 0) {
            return;
        }
        
        if (gameState == null || gameState.getGrid() == null) {
            return;
        }
        
        Army selectedArmy = getSelectedArmy();
        if (selectedArmy == null) {
            return;
        }
        
        // Don't show preview if hovering over HUD
        // Guard against minimized window
        if (windowWidth <= 0 || windowHeight <= 0) {
            return;
        }
        
        // Get mouse position in normalized coords (using cached window size)
        float normX = (float) (mouseX / windowWidth * 2 - 1);
        float normY = (float) (1 - mouseY / windowHeight * 2);
        if (normY > GAME_TOP || normX > GAME_RIGHT || normY < GAME_BOTTOM) {
            return;
        }
        
        int gridWidth = gameState.getGrid().length;
        int gridHeight = gameState.getGrid()[0].length;
        
        if (hoveredGridX >= gridWidth || hoveredGridY >= gridHeight) {
            return;
        }
        
        float gameWidth = GAME_RIGHT - GAME_LEFT;
        float gameHeight = GAME_TOP - GAME_BOTTOM;
        
        float cellWidth = gameWidth / gridWidth;
        float cellHeight = gameHeight / gridHeight;
        
        float centerX = GAME_LEFT + hoveredGridX * cellWidth + cellWidth / 2;
        float centerY = GAME_BOTTOM + hoveredGridY * cellHeight + cellHeight / 2;
        float radius = cellWidth / 4;
        
        // Draw faint circle preview
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        if (selectedArmy.getPlayerId() == 1) {
            glColor4f(0.0f, 0.0f, 1.0f, 0.3f); // Faint blue
        } else {
            glColor4f(1.0f, 0.0f, 0.0f, 0.3f); // Faint red
        }
        
        glBegin(GL_TRIANGLE_FAN);
        glVertex2f(centerX, centerY);
        for (int i = 0; i <= 20; i++) {
            float angle = (float) (2 * Math.PI * i / 20);
            float x = centerX + radius * (float) Math.cos(angle);
            float y = centerY + radius * (float) Math.sin(angle);
            glVertex2f(x, y);
        }
        glEnd();
        
        glDisable(GL_BLEND);
    }
    
    public static void main(String[] args) {
        new FrontendApplication().run();
    }
}
