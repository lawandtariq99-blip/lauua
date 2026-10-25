package com.lauua.lauuacheats.scan;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScoreModelTest {
    @Test void emptyOrNaturalSpawnerIsNotAConfirmedBase() {
        assertEquals("UNKNOWN", ScoreModel.classification(ScoreModel.score(new ScoreModel.Evidence(0,1,0,0,false,false,64))));
        assertEquals(0,ScoreModel.score(new ScoreModel.Evidence(1,2,0,0,true,false,64)));
    }
    @Test void combinedPlayerUtilitySignalsOutrankIsolatedContainers() {
        double chest = ScoreModel.score(new ScoreModel.Evidence(1,0,0,0,false,false,70));
        double base = ScoreModel.score(new ScoreModel.Evidence(3,0,4,8,false,false,70));
        assertTrue(base > chest + 25);
        assertEquals("STRONG_LEAD",ScoreModel.classification(base));
    }
    @Test void worldgenPenaltyNeverMakesNegativeScore() {
        assertEquals(0, ScoreModel.score(new ScoreModel.Evidence(0,2,0,0,true,true,15)));
    }
}
