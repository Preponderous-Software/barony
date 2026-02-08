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
        
        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE) {
                glfwSetWindowShouldClose(window, true);
            }
            if (key == GLFW_KEY_SPACE && action == GLFW_RELEASE) {
                // Send a tick command
                if (client != null) {
                    gameState = client.tick();
                    System.out.println("Tick sent. Current tick: " + (gameState != null ? gameState.getTickCount() : "null"));
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
            
            render();
            
            glfwSwapBuffers(window);
            glfwPollEvents();
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
        
        // Draw tiles
        for (int x = 0; x < gridWidth; x++) {
            for (int y = 0; y < gridHeight; y++) {
                float x1 = -1.0f + x * cellWidth;
                float y1 = -1.0f + y * cellHeight;
                float x2 = x1 + cellWidth;
                float y2 = y1 + cellHeight;
                
                TileType type = grid[x][y].getType();
                switch (type) {
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
            }
        }
        
        // Draw armies
        if (gameState.getArmies() != null) {
            for (Army army : gameState.getArmies()) {
                int armyX = army.getX();
                int armyY = army.getY();
                
                // Draw destination indicator if army is moving
                if (army.isMoving()) {
                    int destX = army.getDestinationX();
                    int destY = army.getDestinationY();
                    
                    float destCenterX = -1.0f + destX * cellWidth + cellWidth / 2;
                    float destCenterY = -1.0f + destY * cellHeight + cellHeight / 2;
                    
                    // Draw destination square (lighter color based on player)
                    if (army.getPlayerId() == 1) {
                        glColor3f(0.5f, 0.5f, 1.0f); // Light blue
                    } else {
                        glColor3f(1.0f, 0.5f, 0.5f); // Light red
                    }
                    
                    float squareSize = cellWidth / 3;
                    glBegin(GL_LINE_LOOP);
                    glVertex2f(destCenterX - squareSize, destCenterY - squareSize);
                    glVertex2f(destCenterX + squareSize, destCenterY - squareSize);
                    glVertex2f(destCenterX + squareSize, destCenterY + squareSize);
                    glVertex2f(destCenterX - squareSize, destCenterY + squareSize);
                    glEnd();
                }
                
                float centerX = -1.0f + armyX * cellWidth + cellWidth / 2;
                float centerY = -1.0f + armyY * cellHeight + cellHeight / 2;
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
                for (int i = 0; i <= 20; i++) {
                    float angle = (float) (2 * Math.PI * i / 20);
                    float x = centerX + radius * (float) Math.cos(angle);
                    float y = centerY + radius * (float) Math.sin(angle);
                    glVertex2f(x, y);
                }
                glEnd();
            }
        }
    }
    
    public static void main(String[] args) {
        new FrontendApplication().run();
    }
}
