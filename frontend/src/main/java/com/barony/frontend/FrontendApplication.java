package com.barony.frontend;

import com.barony.frontend.client.GameClient;
import com.barony.frontend.model.*;
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
                    GameState previousState = gameState;
                    gameState = client.tick();
                    if (gameState != null && previousState != null) {
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
                    System.out.println("Move command sent for army ID " + firstArmyId + " to Player 1 castle (0,0)");
                }
            }
            if (key == GLFW_KEY_2 && action == GLFW_RELEASE) {
                // Move to Player 2 castle (9,9)
                if (client != null && gameState != null && gameState.getArmies() != null && !gameState.getArmies().isEmpty()) {
                    int firstArmyId = gameState.getArmies().get(0).getId();
                    Command cmd = new Command("MOVE", firstArmyId, 9, 9);
                    gameState = client.sendCommand(cmd);
                    System.out.println("Move command sent for army ID " + firstArmyId + " to Player 2 castle (9,9)");
                }
            }
            if (key == GLFW_KEY_3 && action == GLFW_RELEASE) {
                // Move to village at (3,3)
                if (client != null && gameState != null && gameState.getArmies() != null && !gameState.getArmies().isEmpty()) {
                    int firstArmyId = gameState.getArmies().get(0).getId();
                    Command cmd = new Command("MOVE", firstArmyId, 3, 3);
                    gameState = client.sendCommand(cmd);
                    System.out.println("Move command sent for army ID " + firstArmyId + " to village (3,3)");
                }
            }
            if (key == GLFW_KEY_4 && action == GLFW_RELEASE) {
                // Move to village at (6,6)
                if (client != null && gameState != null && gameState.getArmies() != null && !gameState.getArmies().isEmpty()) {
                    int firstArmyId = gameState.getArmies().get(0).getId();
                    Command cmd = new Command("MOVE", firstArmyId, 6, 6);
                    gameState = client.sendCommand(cmd);
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
        // Main game area: from -1 to 0.7 (horizontal), -0.7 to 0.85 (vertical)
        float gameLeft = -1.0f;
        float gameRight = 0.7f;
        float gameBottom = -0.7f;
        float gameTop = 0.85f;
        float gameWidth = gameRight - gameLeft;
        float gameHeight = gameTop - gameBottom;
        
        cellWidth = gameWidth / gridWidth;
        cellHeight = gameHeight / gridHeight;
        
        // Draw tiles
        for (int x = 0; x < gridWidth; x++) {
            for (int y = 0; y < gridHeight; y++) {
                float x1 = gameLeft + x * cellWidth;
                float y1 = gameBottom + y * cellHeight;
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
                        
                        float destCenterX = gameLeft + destX * cellWidth + cellWidth / 2;
                        float destCenterY = gameBottom + destY * cellHeight + cellHeight / 2;
                        
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
                    
                    float centerX = gameLeft + armyX * cellWidth + cellWidth / 2 + offsetX;
                    float centerY = gameBottom + armyY * cellHeight + cellHeight / 2 + offsetY;
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
        
        // Get window size
        int[] width = new int[1];
        int[] height = new int[1];
        glfwGetWindowSize(window, width, height);
        
        // Convert mouse position to normalized coordinates
        float normX = (float) (mouseX / width[0] * 2 - 1);
        float normY = (float) (1 - mouseY / height[0] * 2);
        
        // Game area boundaries
        float gameLeft = -1.0f;
        float gameRight = 0.7f;
        float gameBottom = -0.7f;
        float gameTop = 0.85f;
        
        // Check if mouse is in game area
        if (normX < gameLeft || normX > gameRight || normY < gameBottom || normY > gameTop) {
            hoveredGridX = -1;
            hoveredGridY = -1;
            return;
        }
        
        // Convert to grid coordinates
        int gridWidth = gameState.getGrid().length;
        int gridHeight = gameState.getGrid()[0].length;
        
        float gameWidth = gameRight - gameLeft;
        float gameHeight = gameTop - gameBottom;
        
        int newGridX = (int) ((normX - gameLeft) / gameWidth * gridWidth);
        int newGridY = (int) ((normY - gameBottom) / gameHeight * gridHeight);
        
        // Check if hovered tile changed
        if (newGridX != hoveredGridX || newGridY != hoveredGridY) {
            hoveredGridX = newGridX;
            hoveredGridY = newGridY;
            hoverStartTime = System.currentTimeMillis();
        }
    }
    
    private void handleMouseClick(int button) {
        if (gameState == null || gameState.getGrid() == null) {
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
                gameState = client.sendCommand(cmd);
                addLogMessage("Army #" + selectedArmyId + " moving to (" + hoveredGridX + "," + hoveredGridY + ")");
                System.out.println("Move command sent for army ID " + selectedArmyId + " to (" + hoveredGridX + "," + hoveredGridY + ")");
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
        
        // Return first army at this position (prioritize Player 1 armies)
        Army player1Army = null;
        for (Army army : gameState.getArmies()) {
            if (army.getX() == x && army.getY() == y) {
                if (army.getPlayerId() == 1) {
                    player1Army = army;
                }
            }
        }
        
        // If no Player 1 army, return any army at this position
        if (player1Army != null) {
            return player1Army;
        }
        
        for (Army army : gameState.getArmies()) {
            if (army.getX() == x && army.getY() == y) {
                return army;
            }
        }
        
        return null;
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
        
        int[] width = new int[1];
        int[] height = new int[1];
        glfwGetWindowSize(window, width, height);
        
        // Render top bar (dark background)
        glColor3f(0.1f, 0.1f, 0.1f);
        glBegin(GL_QUADS);
        glVertex2f(-1.0f, 0.85f);
        glVertex2f(1.0f, 0.85f);
        glVertex2f(1.0f, 1.0f);
        glVertex2f(-1.0f, 1.0f);
        glEnd();
        
        // Top bar border
        glColor3f(0.5f, 0.5f, 0.5f);
        glBegin(GL_LINES);
        glVertex2f(-1.0f, 0.85f);
        glVertex2f(1.0f, 0.85f);
        glEnd();
        
        // Count territories for display
        int player1Armies = 0;
        int player2Armies = 0;
        if (gameState.getArmies() != null) {
            for (Army army : gameState.getArmies()) {
                if (army.getPlayerId() == 1) player1Armies++;
                else if (army.getPlayerId() == 2) player2Armies++;
            }
        }
        
        int player1Castles = 0;
        int player2Castles = 0;
        int player1Villages = 0;
        int player2Villages = 0;
        
        if (gameState.getGrid() != null) {
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
        }
        
        // Render side panel (right side, dark background)
        glColor3f(0.1f, 0.1f, 0.1f);
        glBegin(GL_QUADS);
        glVertex2f(0.7f, -1.0f);
        glVertex2f(1.0f, -1.0f);
        glVertex2f(1.0f, 0.85f);
        glVertex2f(0.7f, 0.85f);
        glEnd();
        
        // Side panel border
        glColor3f(0.5f, 0.5f, 0.5f);
        glBegin(GL_LINES);
        glVertex2f(0.7f, -1.0f);
        glVertex2f(0.7f, 0.85f);
        glEnd();
        
        // Render selected army info in side panel
        Army selectedArmy = getSelectedArmy();
        if (selectedArmy != null) {
            // Title bar for selected army
            glColor3f(0.2f, 0.2f, 0.3f);
            glBegin(GL_QUADS);
            glVertex2f(0.7f, 0.75f);
            glVertex2f(1.0f, 0.75f);
            glVertex2f(1.0f, 0.85f);
            glVertex2f(0.7f, 0.85f);
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
            float barHeight = 0.03f;
            int displaySoldiers = Math.min(selectedArmy.getSoldiers(), 20);
            for (int i = 0; i < displaySoldiers; i++) {
                if (selectedArmy.getPlayerId() == 1) {
                    glColor3f(0.3f, 0.3f, 0.8f);
                } else {
                    glColor3f(0.8f, 0.3f, 0.3f);
                }
                
                glBegin(GL_QUADS);
                glVertex2f(0.72f, barY - i * 0.032f);
                glVertex2f(0.98f, barY - i * 0.032f);
                glVertex2f(0.98f, barY - i * 0.032f - barHeight);
                glVertex2f(0.72f, barY - i * 0.032f - barHeight);
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
        }
        
        // Render bottom bar (game log)
        glColor3f(0.1f, 0.1f, 0.1f);
        glBegin(GL_QUADS);
        glVertex2f(-1.0f, -1.0f);
        glVertex2f(0.7f, -1.0f);
        glVertex2f(0.7f, -0.7f);
        glVertex2f(-1.0f, -0.7f);
        glEnd();
        
        // Bottom bar border
        glColor3f(0.5f, 0.5f, 0.5f);
        glBegin(GL_LINES);
        glVertex2f(-1.0f, -0.7f);
        glVertex2f(0.7f, -0.7f);
        glEnd();
        
        // Draw game log entries as colored bars (can't render text in basic OpenGL)
        float logY = -0.72f;
        float logHeight = 0.025f;
        for (int i = 0; i < Math.min(gameLog.size(), 10); i++) {
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
        
        // Get mouse position in normalized coords
        int[] width = new int[1];
        int[] height = new int[1];
        glfwGetWindowSize(window, width, height);
        
        float normX = (float) (mouseX / width[0] * 2 - 1);
        float normY = (float) (1 - mouseY / height[0] * 2);
        
        // Don't show tooltip if hovering over HUD areas
        if (normY > 0.85f || normX > 0.7f || normY < -0.7f) {
            return;
        }
        
        // Draw tooltip box
        float tooltipWidth = 0.3f;
        float tooltipHeight = 0.15f;
        float tooltipX = normX + 0.05f;
        float tooltipY = normY - 0.05f;
        
        // Keep tooltip on screen
        if (tooltipX + tooltipWidth > 0.7f) tooltipX = normX - tooltipWidth - 0.05f;
        if (tooltipY - tooltipHeight < -0.7f) tooltipY = normY + tooltipHeight + 0.05f;
        
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
        
        // Draw info indicators (colored bars representing different properties)
        float barY = tooltipY - 0.02f;
        float barHeight = 0.02f;
        float barSpacing = 0.025f;
        
        // Tile type indicator
        switch (tile.getType()) {
            case CASTLE:
                glColor3f(0.5f, 0.5f, 0.5f); // Gray
                break;
            case VILLAGE:
                glColor3f(0.6f, 0.3f, 0.0f); // Brown
                break;
            case EMPTY:
                glColor3f(0.0f, 0.5f, 0.0f); // Green
                break;
        }
        glBegin(GL_QUADS);
        glVertex2f(tooltipX + 0.01f, barY);
        glVertex2f(tooltipX + 0.1f, barY);
        glVertex2f(tooltipX + 0.1f, barY - barHeight);
        glVertex2f(tooltipX + 0.01f, barY - barHeight);
        glEnd();
        barY -= barSpacing;
        
        // Ownership indicator
        if (tile.getOwnerId() == 1) {
            glColor3f(0.0f, 0.0f, 1.0f); // Blue - Player 1
        } else if (tile.getOwnerId() == 2) {
            glColor3f(1.0f, 0.0f, 0.0f); // Red - Player 2
        } else {
            glColor3f(0.5f, 0.5f, 0.5f); // Gray - Neutral
        }
        glBegin(GL_QUADS);
        glVertex2f(tooltipX + 0.01f, barY);
        glVertex2f(tooltipX + 0.1f, barY);
        glVertex2f(tooltipX + 0.1f, barY - barHeight);
        glVertex2f(tooltipX + 0.01f, barY - barHeight);
        glEnd();
        barY -= barSpacing;
        
        // Army info if present
        if (army != null) {
            // Army player indicator
            if (army.getPlayerId() == 1) {
                glColor3f(0.0f, 0.0f, 1.0f);
            } else {
                glColor3f(1.0f, 0.0f, 0.0f);
            }
            glBegin(GL_QUADS);
            glVertex2f(tooltipX + 0.01f, barY);
            glVertex2f(tooltipX + 0.1f, barY);
            glVertex2f(tooltipX + 0.1f, barY - barHeight);
            glVertex2f(tooltipX + 0.01f, barY - barHeight);
            glEnd();
            barY -= barSpacing;
            
            // Soldier count indicator (bars)
            int displaySoldiers = Math.min(army.getSoldiers(), 10);
            float soldierBarWidth = 0.015f;
            for (int i = 0; i < displaySoldiers; i++) {
                if (army.getPlayerId() == 1) {
                    glColor3f(0.3f, 0.3f, 0.8f);
                } else {
                    glColor3f(0.8f, 0.3f, 0.3f);
                }
                glBegin(GL_QUADS);
                glVertex2f(tooltipX + 0.01f + i * soldierBarWidth, barY);
                glVertex2f(tooltipX + 0.01f + (i + 1) * soldierBarWidth - 0.002f, barY);
                glVertex2f(tooltipX + 0.01f + (i + 1) * soldierBarWidth - 0.002f, barY - barHeight);
                glVertex2f(tooltipX + 0.01f + i * soldierBarWidth, barY - barHeight);
                glEnd();
            }
            barY -= barSpacing;
            
            // Movement status indicator
            if (army.isMoving()) {
                glColor3f(0.0f, 0.8f, 0.0f); // Green - moving
                glBegin(GL_QUADS);
                glVertex2f(tooltipX + 0.01f, barY);
                glVertex2f(tooltipX + 0.1f, barY);
                glVertex2f(tooltipX + 0.1f, barY - barHeight);
                glVertex2f(tooltipX + 0.01f, barY - barHeight);
                glEnd();
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
        
        // Game area boundaries
        float gameLeft = -1.0f;
        float gameRight = 0.7f;
        float gameBottom = -0.7f;
        float gameTop = 0.85f;
        float gameWidth = gameRight - gameLeft;
        float gameHeight = gameTop - gameBottom;
        
        float cellWidth = gameWidth / gridWidth;
        float cellHeight = gameHeight / gridHeight;
        
        int armyX = selectedArmy.getX();
        int armyY = selectedArmy.getY();
        
        float x1 = gameLeft + armyX * cellWidth;
        float y1 = gameBottom + armyY * cellHeight;
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
        int[] width = new int[1];
        int[] height = new int[1];
        glfwGetWindowSize(window, width, height);
        float normX = (float) (mouseX / width[0] * 2 - 1);
        float normY = (float) (1 - mouseY / height[0] * 2);
        if (normY > 0.85f || normX > 0.7f || normY < -0.7f) {
            return;
        }
        
        int gridWidth = gameState.getGrid().length;
        int gridHeight = gameState.getGrid()[0].length;
        
        if (hoveredGridX >= gridWidth || hoveredGridY >= gridHeight) {
            return;
        }
        
        // Game area boundaries
        float gameLeft = -1.0f;
        float gameRight = 0.7f;
        float gameBottom = -0.7f;
        float gameTop = 0.85f;
        float gameWidth = gameRight - gameLeft;
        float gameHeight = gameTop - gameBottom;
        
        float cellWidth = gameWidth / gridWidth;
        float cellHeight = gameHeight / gridHeight;
        
        float centerX = gameLeft + hoveredGridX * cellWidth + cellWidth / 2;
        float centerY = gameBottom + hoveredGridY * cellHeight + cellHeight / 2;
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
