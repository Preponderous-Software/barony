package com.barony.frontend.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommandTest {

    @Test
    void commandCreatesWithCorrectValues() {
        Command command = new Command("MOVE", 1, 5, 7);
        
        assertEquals("MOVE", command.getType());
        assertEquals(1, command.getArmyId());
        assertEquals(5, command.getTargetX());
        assertEquals(7, command.getTargetY());
    }
    
    @Test
    void commandTypeIsPreserved() {
        Command command = new Command("ATTACK", 2, 3, 4);
        
        assertEquals("ATTACK", command.getType());
    }
    
    @Test
    void splitCommandCreatesWithCorrectValues() {
        Command command = new Command("SPLIT", 1, 5);
        
        assertEquals("SPLIT", command.getType());
        assertEquals(1, command.getArmyId());
        assertEquals(5, command.getSplitAmount());
    }
}
