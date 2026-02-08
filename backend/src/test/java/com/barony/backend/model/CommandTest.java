package com.barony.backend.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommandTest {

    @Test
    void commandCanBeCreatedWithSetters() {
        Command command = new Command();
        command.setType("MOVE");
        command.setArmyId(5);
        command.setTargetX(3);
        command.setTargetY(7);
        
        assertEquals("MOVE", command.getType());
        assertEquals(5, command.getArmyId());
        assertEquals(3, command.getTargetX());
        assertEquals(7, command.getTargetY());
    }
    
    @Test
    void commandTypeCanBeChanged() {
        Command command = new Command();
        command.setType("MOVE");
        assertEquals("MOVE", command.getType());
        
        command.setType("ATTACK");
        assertEquals("ATTACK", command.getType());
    }
    
    @Test
    void commandArmyIdCanBeUpdated() {
        Command command = new Command();
        command.setArmyId(1);
        assertEquals(1, command.getArmyId());
        
        command.setArmyId(10);
        assertEquals(10, command.getArmyId());
    }
    
    @Test
    void commandTargetCoordinatesCanBeUpdated() {
        Command command = new Command();
        command.setTargetX(5);
        command.setTargetY(8);
        
        assertEquals(5, command.getTargetX());
        assertEquals(8, command.getTargetY());
    }
}
