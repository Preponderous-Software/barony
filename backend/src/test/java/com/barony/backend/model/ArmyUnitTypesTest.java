package com.barony.backend.model;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** An army's mix of infantry, archers and cavalry, and the one rule every loss goes through. */
class ArmyUnitTypesTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static Army mixed(int inf, int arch, int cav) {
        Army army = new Army(0, 0, inf + arch + cav, 1);
        army.setArchers(arch);
        army.setCavalry(cav);
        return army;
    }

    @Test
    void infantryIsWhatIsNotArchersOrCavalry() {
        Army army = mixed(16, 5, 3);
        assertEquals(24, army.getSoldiers());
        assertEquals(16, army.getInfantry());
        assertEquals(16, new Army(0, 0, 16, 1).getInfantry(), "a new army is all infantry");
    }

    @Test
    void lossesAreSharedInProportion() {
        assertArrayEquals(new int[] {6, 3, 1}, Army.shareLosses(12, 6, 2, 10), "exact shares");
        // 7 of 10/5/5: exact 3.5 / 1.75 / 1.75 -> 3/1/1, then the two left go to the largest
        // remainders: archers and cavalry (.75 each) before infantry (.5).
        assertArrayEquals(new int[] {3, 2, 2}, Army.shareLosses(10, 5, 5, 7));
        // 1 of 10/5/5: .5 / .25 / .25 -> infantry.
        assertArrayEquals(new int[] {1, 0, 0}, Army.shareLosses(10, 5, 5, 1));
    }

    @Test
    void tiesGoToInfantryThenArchersThenCavalry() {
        assertArrayEquals(new int[] {1, 0, 0}, Army.shareLosses(1, 1, 1, 1));
        assertArrayEquals(new int[] {1, 1, 0}, Army.shareLosses(1, 1, 1, 2));
        assertArrayEquals(new int[] {0, 1, 0}, Army.shareLosses(0, 1, 1, 1));
        assertArrayEquals(new int[] {0, 0, 1}, Army.shareLosses(0, 0, 3, 1));
    }

    @Test
    void anAllInfantryArmyJustLosesSoldiers() {
        for (int n = 0; n <= 30; n++) {
            assertArrayEquals(new int[] {n, 0, 0}, Army.shareLosses(30, 0, 0, n));
        }
        Army army = new Army(0, 0, 10, 1);
        army.loseSoldiers(4);
        assertEquals(6, army.getSoldiers());
        army.loseSoldiers(99);
        assertEquals(0, army.getSoldiers(), "clamped at zero, like the old Math.max(0, ...)");
        army.loseSoldiers(-3);
        assertEquals(0, army.getSoldiers());
    }

    @Test
    void sharingIsExactDeterministicAndKeepsTheInvariant() {
        for (int inf = 0; inf <= 9; inf++) {
            for (int arch = 0; arch <= 9; arch++) {
                for (int cav = 0; cav <= 9; cav++) {
                    int total = inf + arch + cav;
                    for (int lose = 0; lose <= total; lose++) {
                        int[] taken = Army.shareLosses(inf, arch, cav, lose);
                        assertArrayEquals(taken, Army.shareLosses(inf, arch, cav, lose));
                        assertEquals(lose, taken[0] + taken[1] + taken[2]);
                        assertTrue(taken[0] >= 0 && taken[0] <= inf);
                        assertTrue(taken[1] >= 0 && taken[1] <= arch);
                        assertTrue(taken[2] >= 0 && taken[2] <= cav);
                        // never more than one off the exact proportional share
                        if (total > 0) {
                            assertTrue(Math.abs(taken[1] - (double) lose * arch / total) < 1.0);
                            assertTrue(Math.abs(taken[2] - (double) lose * cav / total) < 1.0);
                        }
                        Army army = mixed(inf, arch, cav);
                        army.loseSoldiers(lose);
                        assertEquals(total - lose, army.getSoldiers());
                        assertTrue(army.getArchers() >= 0 && army.getCavalry() >= 0 && army.getInfantry() >= 0);
                    }
                }
            }
        }
    }

    @Test
    void impossibleSharesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> Army.shareLosses(1, 1, 1, 4));
        assertThrows(IllegalArgumentException.class, () -> Army.shareLosses(-1, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> Army.shareLosses(1, 1, 1, -1));
    }

    @Test
    void detachingTakesEachTypeInProportion() {
        Army army = mixed(12, 6, 2);
        army.setMorale(80);
        Army part = army.detach(10);
        assertEquals(10, part.getSoldiers());
        assertEquals(3, part.getArchers());
        assertEquals(1, part.getCavalry());
        assertEquals(10, army.getSoldiers());
        assertEquals(3, army.getArchers());
        assertEquals(1, army.getCavalry());
        assertEquals(army.getX(), part.getX());
        assertEquals(army.getPlayerId(), part.getPlayerId());
        assertNotEquals(army.getId(), part.getId());
    }

    @Test
    void detachingExactCounts() {
        Army army = mixed(12, 6, 2);
        Army part = army.detach(0, 6, 2);
        assertEquals(8, part.getSoldiers());
        assertEquals(12, army.getSoldiers());
        assertEquals(12, army.getInfantry());
        assertThrows(IllegalArgumentException.class, () -> army.detach(0, 1, 0));
    }

    @Test
    void absorbingAddsEveryTypeAndKeepsATrainingOrder() {
        Army a = mixed(10, 2, 0);
        Army b = mixed(5, 1, 3);
        b.setTrainingType(UnitType.CAVALRY);
        b.setTrainingRemaining(2);
        a.absorb(b);
        assertEquals(21, a.getSoldiers());
        assertEquals(3, a.getArchers());
        assertEquals(3, a.getCavalry());
        assertEquals(UnitType.CAVALRY, a.getTrainingType(), "takes the other's order when it has none");
    }

    @Test
    void anArmySavedBeforeUnitTypesLoadsAsAllInfantry() throws Exception {
        String old = "{\"id\":7,\"x\":1,\"y\":2,\"soldiers\":12,\"playerId\":1,\"destinationX\":null,"
                + "\"destinationY\":null,\"morale\":100,\"loyalty\":100,\"desertionCarryBasisPoints\":0,\"moving\":false}";
        Army army = mapper.readValue(old, Army.class);
        assertEquals(12, army.getSoldiers());
        assertEquals(0, army.getArchers());
        assertEquals(0, army.getCavalry());
        assertEquals(12, army.getInfantry());
        assertNull(army.getTrainingType());
        assertFalse(army.isTraining());
    }

    @Test
    void theMixRoundTripsAndInfantryIsNeverReadBack() throws Exception {
        Army army = mixed(16, 5, 3);
        army.setTrainingType(UnitType.ARCHERS);
        army.setTrainingRemaining(4);
        String json = mapper.writeValueAsString(army);
        assertTrue(json.contains("\"infantry\":16"), json);
        // A stale or hostile "infantry" in the JSON cannot change the mix: it is derived.
        Army back = mapper.readValue(json.replace("\"infantry\":16", "\"infantry\":999"), Army.class);
        assertEquals(24, back.getSoldiers());
        assertEquals(5, back.getArchers());
        assertEquals(3, back.getCavalry());
        assertEquals(16, back.getInfantry());
        assertEquals(UnitType.ARCHERS, back.getTrainingType());
        assertEquals(4, back.getTrainingRemaining());
        Army copy = new Army(back);
        assertEquals(5, copy.getArchers());
        assertEquals(3, copy.getCavalry());
        assertEquals(4, copy.getTrainingRemaining());
    }

    @Test
    void fieldOrderOnLoadDoesNotMatter() throws Exception {
        // Jackson may set archers before soldiers; nothing clamps along the way.
        Army army = mapper.readValue("{\"archers\":5,\"cavalry\":3,\"soldiers\":24,\"playerId\":1}", Army.class);
        assertEquals(16, army.getInfantry());
    }
}
