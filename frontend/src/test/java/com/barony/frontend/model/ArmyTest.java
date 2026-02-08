package com.barony.frontend.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArmyTest {

    @Test
    void armyInitializesWithDefaultValues() {
        Army army = new Army();
        
        // Should not throw any exceptions
        assertNotNull(army);
    }
    
    @Test
    void armyPropertiesCanBeSetAndRetrieved() {
        Army army = new Army();
        
        army.setId(1);
        army.setX(5);
        army.setY(7);
        army.setSoldiers(15);
        army.setPlayerId(2);
        
        assertEquals(1, army.getId());
        assertEquals(5, army.getX());
        assertEquals(7, army.getY());
        assertEquals(15, army.getSoldiers());
        assertEquals(2, army.getPlayerId());
    }
}
