package com.barony.frontend;

import com.barony.frontend.client.GameClient;
import com.barony.frontend.model.*;
import com.barony.frontend.ui.SimpleTextRenderer;
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
    private java.util.Scanner inputScanner; // Shared scanner for console input, never closed
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
    private static final float GAME_RIGHT = 0.7f;
    private static final float GAME_BOTTOM = -0.7f;
    private static final float GAME_TOP = 0.85f;
    
    // Cached window size to avoid allocations every frame
    private int windowWidth = 800;
    private int windowHeight = 800;
    
    // Cached HUD counts to avoid recomputing every frame
    private int cachedPlayer1Armies = 0;
    private int cachedPlayer2Armies = 0;
    private int cachedPlayer1Castles = 0;
    private int cachedPlayer2Castles = 0;
    private int cachedPlayer1Villages = 0;
    private int cachedPlayer2Villages = 0;
    
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
        
        window = glfwCreateWindow(800, 800, "Barony Client", NULL, NULL);
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
        
        // Window size callback to update cached dimensions
        glfwSetFramebufferSizeCallback(window, (window, width, height) -> {
            windowWidth = width;
            windowHeight = height;
            glViewport(0, 0, width, height);
        });
        
        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(window, true);
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
                            System.out.println("Game reset!");
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
                        System.out.println("Tick sent. Current tick: " + gameState.getTickCount());
                    }
                }
            }
            if (key == GLFW_KEY_M && action == GLFW_RELEASE) {
                // Send a move command for first army using its ID
                if (client != null && gameState != null && gameState.getArmies() != null && !gameState.getArmies().isEmpty()) {
                    int firstArmyId = gameState.getArmies().get(0).getId();
                    Command cmd = new Command("MOVE", firstArmyId, 5, 5);
                    gameState = client.sendCommand(cmd);
                    updateCachedCounts();
                    System.out.println("Move command sent for army ID " + firstArmyId + " to (5,5)");
                }
            }
            // Number keys for moving first army to strategic locations
            if (key == GLFW_KEY_1 && action == GLFW_RELEASE) {
                // Move to Player 1 castle (0,0)
                if (client != null && gameState != null && gameState.getArmies() != null && !gameState.getArmies().isEmpty()) {
                    int firstArmyId = gameState.getArmies().get(0).getId();
                    Command cmd = new Command("MOVE", firstArmyId, 0, 0);
                    gameState = client.sendCommand(cmd);
                    updateCachedCounts();
                    System.out.println("Move command sent for army ID " + firstArmyId + " to Player 1 castle (0,0)");
                }
            }
            if (key == GLFW_KEY_2 && action == GLFW_RELEASE) {
                // Move to Player 2 castle (9,9)
                if (client != null && gameState != null && gameState.getArmies() != null && !gameState.getArmies().isEmpty()) {
                    int firstArmyId = gameState.getArmies().get(0).getId();
                    Command cmd = new Command("MOVE", firstArmyId, 9, 9);
                    gameState = client.sendCommand(cmd);
                    updateCachedCounts();
                    System.out.println("Move command sent for army ID " + firstArmyId + " to Player 2 castle (9,9)");
                }
            }
            if (key == GLFW_KEY_3 && action == GLFW_RELEASE) {
                // Move to village at (3,3)
                if (client != null && gameState != null && gameState.getArmies() != null && !gameState.getArmies().isEmpty()) {
                    int firstArmyId = gameState.getArmies().get(0).getId();
                    Command cmd = new Command("MOVE", firstArmyId, 3, 3);
                    gameState = client.sendCommand(cmd);
                    updateCachedCounts();
                    System.out.println("Move command sent for army ID " + firstArmyId + " to village (3,3)");
                }
            }
            if (key == GLFW_KEY_4 && action == GLFW_RELEASE) {
                // Move to village at (6,6)
                if (client != null && gameState != null && gameState.getArmies() != null && !gameState.getArmies().isEmpty()) {
                    int firstArmyId = gameState.getArmies().get(0).getId();
                    Command cmd = new Command("MOVE", firstArmyId, 6, 6);
                    gameState = client.sendCommand(cmd);
                    updateCachedCounts();
                    System.out.println("Move command sent for army ID " + firstArmyId + " to village (6,6)");
                }
            }
            if (key == GLFW_KEY_S && action == GLFW_RELEASE) {
                // Send a split command for first army
                if (client != null && gameState != null && gameState.getArmies() != null && !gameState.getArmies().isEmpty()) {
                    Army firstArmy = gameState.getArmies().get(0);
                    int firstArmyId = firstArmy.getId();
                    int totalSoldiers = firstArmy.getSoldiers();
                    
                    // Check if army has enough soldiers to split
                    if (totalSoldiers <= 1) {
                        System.out.println("Split command not possible for army ID " + firstArmyId + " because it has " + totalSoldiers + " soldier" + (totalSoldiers == 1 ? "" : "s") + ".");
                    } else {
                        // Note: Reading from console will block the render loop. Consider this a limitation for the prototype.
                        // In a production game, use an in-game UI or handle input on a separate thread.
                        System.out.println("Split command initiated for army ID " + firstArmyId + " with " + totalSoldiers + " soldiers");
                        System.out.print("Enter number of soldiers to split off (1-" + (totalSoldiers - 1) + "): ");
                        
                        try {
                            // Use shared scanner that never closes System.in
                            if (inputScanner == null) {
                                inputScanner = new java.util.Scanner(System.in);
                            }
                            
                            if (inputScanner.hasNextInt()) {
                                int splitAmount = inputScanner.nextInt();
                                
                                if (splitAmount >= 1 && splitAmount < totalSoldiers) {
                                    Command cmd = new Command("SPLIT", firstArmyId, splitAmount);
                                    gameState = client.sendCommand(cmd);
                                    updateCachedCounts();
                                    System.out.println("Split command sent for army ID " + firstArmyId + ", splitting off " + splitAmount + " soldiers");
                                } else {
                                    System.out.println("Invalid split amount. Must be between 1 and " + (totalSoldiers - 1));
                                }
                            } else {
                                System.out.println("Invalid input. Split command cancelled.");
                                inputScanner.next(); // Consume invalid token
                            }
                        } catch (Exception e) {
                            System.out.println("Error processing split command: " + e.getMessage());
                        }
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
        
        // Initialize game client
        client = new GameClient("http://localhost:8080");
        gameState = client.getState();
        System.out.println("Connected to server. Initial state retrieved.");
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
                    
                    // Color based on player
                    if (army.getPlayerId() == 1) {
                        glColor3f(0.0f, 0.0f, 1.0f); // Blue
                    } else {
                        glColor3f(1.0f, 0.0f, 0.0f); // Red
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
                System.out.println("Selected army ID " + selectedArmyId + " at (" + hoveredGridX + "," + hoveredGridY + ")");
            } else if (selectedArmyId != null) {
                // Move selected army to clicked tile
                Command cmd = new Command("MOVE", selectedArmyId, hoveredGridX, hoveredGridY);
                GameState newState = client.sendCommand(cmd);
                if (newState != null) {
                    gameState = newState;
                    updateCachedCounts();
                    addLogMessage("Army #" + selectedArmyId + " moving to (" + hoveredGridX + "," + hoveredGridY + ")");
                    System.out.println("Move command sent for army ID " + selectedArmyId + " to (" + hoveredGridX + "," + hoveredGridY + ")");
                } else {
                    addLogMessage("Failed to move army #" + selectedArmyId + " to (" + hoveredGridX + "," + hoveredGridY + ")");
                    System.err.println("Failed to send move command for army ID " + selectedArmyId + " to (" + hoveredGridX + "," + hoveredGridY + ")");
                }
            }
        } else if (button == GLFW_MOUSE_BUTTON_RIGHT) {
            // Right-click to deselect
            if (selectedArmyId != null) {
                addLogMessage("Deselected army #" + selectedArmyId);
                System.out.println("Deselected army");
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
    
    private void addLogMessage(String message) {
        gameLog.addFirst(message);
        if (gameLog.size() > MAX_LOG_ENTRIES) {
            gameLog.removeLast();
        }
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
        float hudRight = 0.95f;
        float hudTop = 0.98f;
        float barHeight = 0.03f;
        float barSpacing = 0.01f;
        
        // Armies bar (top row)
        int totalArmies = player1Armies + player2Armies;
        if (totalArmies > 0) {
            float armiesBarTop = hudTop;
            float armiesBarBottom = hudTop - barHeight;
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
            float castlesBarTop = hudTop - (barHeight + barSpacing);
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
            float villagesBarTop = hudTop - 2 * (barHeight + barSpacing);
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
        float labelScale = 0.012f;
        float labelX = hudLeft;
        
        // Tick count at the far left
        SimpleTextRenderer.drawText("TICK: " + gameState.getTickCount(), labelX, hudTop + 0.005f, labelScale, 1.0f, 1.0f, 1.0f);
        
        // Armies label
        SimpleTextRenderer.drawText("ARMIES", labelX, hudTop - 0.005f, labelScale * 0.7f, 0.8f, 0.8f, 0.8f);
        SimpleTextRenderer.drawText("P1:" + player1Armies, labelX, hudTop - 0.025f, labelScale * 0.7f, 0.3f, 0.7f, 1.0f);
        SimpleTextRenderer.drawText("P2:" + player2Armies, labelX + 0.15f, hudTop - 0.025f, labelScale * 0.7f, 1.0f, 0.4f, 0.4f);
        
        // Castles label
        float castlesY = hudTop - (barHeight + barSpacing) - 0.005f;
        SimpleTextRenderer.drawText("CASTLES", labelX, castlesY, labelScale * 0.7f, 0.8f, 0.8f, 0.8f);
        SimpleTextRenderer.drawText("P1:" + player1Castles, labelX, castlesY - 0.02f, labelScale * 0.7f, 0.2f, 0.9f, 0.5f);
        SimpleTextRenderer.drawText("P2:" + player2Castles, labelX + 0.15f, castlesY - 0.02f, labelScale * 0.7f, 1.0f, 0.9f, 0.3f);
        
        // Villages label
        float villagesY = hudTop - 2 * (barHeight + barSpacing) - 0.005f;
        SimpleTextRenderer.drawText("VILLAGES", labelX, villagesY, labelScale * 0.7f, 0.8f, 0.8f, 0.8f);
        SimpleTextRenderer.drawText("P1:" + player1Villages, labelX, villagesY - 0.02f, labelScale * 0.7f, 0.6f, 0.6f, 1.0f);
        SimpleTextRenderer.drawText("P2:" + player2Villages, labelX + 0.15f, villagesY - 0.02f, labelScale * 0.7f, 1.0f, 0.6f, 0.6f);
        
        // Income display (villages = income per tick)
        float incomeX = hudRight - 0.3f;
        SimpleTextRenderer.drawText("INCOME/TICK", incomeX, hudTop + 0.005f, labelScale * 0.7f, 0.8f, 0.8f, 0.8f);
        SimpleTextRenderer.drawText("P1: +" + player1Villages, incomeX, hudTop - 0.02f, labelScale * 0.8f, 0.3f, 1.0f, 0.3f);
        SimpleTextRenderer.drawText("P2: +" + player2Villages, incomeX, hudTop - 0.045f, labelScale * 0.8f, 1.0f, 0.3f, 0.3f);
        
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
            // Title bar for selected army
            glColor3f(0.2f, 0.2f, 0.3f);
            glBegin(GL_QUADS);
            glVertex2f(GAME_RIGHT, 0.75f);
            glVertex2f(1.0f, 0.75f);
            glVertex2f(1.0f, 0.85f);
            glVertex2f(GAME_RIGHT, 0.85f);
            glEnd();
            
            // Visual indicator of selected army
            if (selectedArmy.getPlayerId() == 1) {
                glColor3f(0.0f, 0.0f, 1.0f); // Blue
            } else {
                glColor3f(1.0f, 0.0f, 0.0f); // Red
            }
            
            // Draw army indicator circle
            float circleX = 0.75f;
            float circleY = 0.8f;
            float circleRadius = 0.03f;
            glBegin(GL_TRIANGLE_FAN);
            glVertex2f(circleX, circleY);
            for (int i = 0; i <= 20; i++) {
                float angle = (float) (2 * Math.PI * i / 20);
                float x = circleX + circleRadius * (float) Math.cos(angle);
                float y = circleY + circleRadius * (float) Math.sin(angle);
                glVertex2f(x, y);
            }
            glEnd();
            
            // Draw soldier count bars
            float barY = 0.68f;
            float soldierBarHeight = 0.03f;
            int displaySoldiers = Math.min(selectedArmy.getSoldiers(), MAX_PANEL_SOLDIERS);
            for (int i = 0; i < displaySoldiers; i++) {
                if (selectedArmy.getPlayerId() == 1) {
                    glColor3f(0.3f, 0.3f, 0.8f);
                } else {
                    glColor3f(0.8f, 0.3f, 0.3f);
                }
                
                glBegin(GL_QUADS);
                glVertex2f(0.72f, barY - i * 0.032f);
                glVertex2f(0.98f, barY - i * 0.032f);
                glVertex2f(0.98f, barY - i * 0.032f - soldierBarHeight);
                glVertex2f(0.72f, barY - i * 0.032f - soldierBarHeight);
                glEnd();
            }
            
            // Show destination if moving
            if (selectedArmy.isMoving()) {
                glColor3f(0.3f, 0.8f, 0.3f);
                float destBoxY = -0.2f;
                glBegin(GL_QUADS);
                glVertex2f(0.72f, destBoxY);
                glVertex2f(0.98f, destBoxY);
                glVertex2f(0.98f, destBoxY + 0.08f);
                glVertex2f(0.72f, destBoxY + 0.08f);
                glEnd();
            }
            
            // Add text labels for selected army panel
            float panelTextScale = 0.01f;
            
            // Title
            SimpleTextRenderer.drawText("SELECTED ARMY", 0.72f, 0.84f, panelTextScale, 1.0f, 1.0f, 1.0f);
            
            // Army ID and Player
            String playerText = selectedArmy.getPlayerId() == 1 ? "P1" : "P2";
            SimpleTextRenderer.drawText("ID: " + selectedArmyId, 0.82f, 0.805f, panelTextScale * 0.8f, 0.9f, 0.9f, 0.9f);
            SimpleTextRenderer.drawText(playerText, 0.72f, 0.805f, panelTextScale * 0.8f, 
                selectedArmy.getPlayerId() == 1 ? 0.3f : 1.0f, 
                selectedArmy.getPlayerId() == 1 ? 0.3f : 0.3f,
                selectedArmy.getPlayerId() == 1 ? 1.0f : 0.3f);
            
            // Soldier count label
            SimpleTextRenderer.drawText("SOLDIERS: " + selectedArmy.getSoldiers(), 0.72f, 0.70f, panelTextScale * 0.9f, 1.0f, 1.0f, 0.0f);
            
            // Position
            SimpleTextRenderer.drawText("POS: (" + selectedArmy.getX() + "," + selectedArmy.getY() + ")", 
                0.72f, 0.65f, panelTextScale * 0.8f, 0.8f, 0.8f, 0.8f);
            
            // Destination if moving
            if (selectedArmy.isMoving()) {
                SimpleTextRenderer.drawText("MOVING TO:", 0.72f, -0.15f, panelTextScale * 0.8f, 0.3f, 1.0f, 0.3f);
                SimpleTextRenderer.drawText("(" + selectedArmy.getDestinationX() + "," + selectedArmy.getDestinationY() + ")", 
                    0.72f, -0.18f, panelTextScale * 0.9f, 0.5f, 1.0f, 0.5f);
            }
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
        float logY = -0.72f;
        float logHeight = 0.025f;
        float logTextScale = 0.0065f;
        
        // Add title for game log
        SimpleTextRenderer.drawText("GAME LOG:", -0.98f, GAME_BOTTOM + 0.005f, 0.01f, 1.0f, 1.0f, 1.0f);
        
        for (int i = 0; i < Math.min(gameLog.size(), MAX_LOG_ENTRIES); i++) {
            // Alternate colors for visibility
            if (i % 2 == 0) {
                glColor3f(0.3f, 0.3f, 0.4f);
            } else {
                glColor3f(0.25f, 0.25f, 0.35f);
            }
            
            glBegin(GL_QUADS);
            glVertex2f(-0.98f, logY - i * 0.028f);
            glVertex2f(0.68f, logY - i * 0.028f);
            glVertex2f(0.68f, logY - i * 0.028f - logHeight);
            glVertex2f(-0.98f, logY - i * 0.028f - logHeight);
            glEnd();
            
            // Draw the log message text
            String logMessage = gameLog.get(i);
            SimpleTextRenderer.drawText(logMessage, -0.97f, logY - i * 0.028f - 0.003f, logTextScale, 1.0f, 1.0f, 1.0f);
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
        float tooltipWidth = 0.3f;
        float tooltipHeight = 0.15f;
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
        float tooltipTextScale = 0.008f;
        float textY = tooltipY - 0.015f;
        
        // Position
        SimpleTextRenderer.drawText("TILE: (" + hoveredGridX + "," + hoveredGridY + ")", tooltipX + 0.01f, textY, tooltipTextScale, 1.0f, 1.0f, 1.0f);
        textY -= 0.025f;
        
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
        textY -= 0.022f;
        
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
        textY -= 0.022f;
        
        // Income for villages
        if (tile.getType() == TileType.VILLAGE && tile.getOwnerId() > 0) {
            SimpleTextRenderer.drawText("INCOME: +1/TICK", tooltipX + 0.01f, textY, tooltipTextScale, 0.3f, 1.0f, 0.3f);
            textY -= 0.022f;
        }
        
        // Army info if present
        if (army != null) {
            String armyText = "ARMY ID: " + army.getId() + " (P" + army.getPlayerId() + ")";
            float armyR = army.getPlayerId() == 1 ? 0.3f : 1.0f;
            float armyG = army.getPlayerId() == 1 ? 0.3f : 0.3f;
            float armyB = army.getPlayerId() == 1 ? 1.0f : 0.3f;
            SimpleTextRenderer.drawText(armyText, tooltipX + 0.01f, textY, tooltipTextScale, armyR, armyG, armyB);
            textY -= 0.022f;
            
            SimpleTextRenderer.drawText("SOLDIERS: " + army.getSoldiers(), tooltipX + 0.01f, textY, tooltipTextScale, 1.0f, 1.0f, 0.0f);
            textY -= 0.022f;
            
            if (army.isMoving()) {
                SimpleTextRenderer.drawText("MOVING TO: (" + army.getDestinationX() + "," + army.getDestinationY() + ")", 
                    tooltipX + 0.01f, textY, tooltipTextScale * 0.9f, 0.3f, 1.0f, 0.3f);
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
