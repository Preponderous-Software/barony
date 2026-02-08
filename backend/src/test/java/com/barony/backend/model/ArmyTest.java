package com.barony.backend.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArmyTest {

    @Test
    void armyCreationAssignsUniqueIds() {
        Army army1 = new Army(0, 0, 10, 1);
        Army army2 = new Army(1, 1, 20, 2);
        
        assertNotEquals(army1.getId(), army2.getId(), "Army IDs should be unique");
        assertTrue(army1.getId() > 0, "Army ID should be positive");
        assertTrue(army2.getId() > 0, "Army ID should be positive");
    }
    
    @Test
    void armyInitializesWithCorrectValues() {
        Army army = new Army(5, 7, 15, 1);
        
        assertEquals(5, army.getX());
        assertEquals(7, army.getY());
        assertEquals(15, army.getSoldiers());
        assertEquals(1, army.getPlayerId());
    }
    
    @Test
    void armyPositionCanBeUpdated() {
        Army army = new Army(0, 0, 10, 1);
        
        army.setX(3);
        army.setY(4);
        
        assertEquals(3, army.getX());
        assertEquals(4, army.getY());
    }
}
