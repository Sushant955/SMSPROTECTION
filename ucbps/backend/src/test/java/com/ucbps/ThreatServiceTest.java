package com.ucbps;

import com.ucbps.intel.ThreatService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThreatServiceTest {
    @Test
    void oneNoisyReporterIsNeverFlagged() {
        double s = ThreatService.score(1.0, 1);
        assertFalse(ThreatService.isFlagged(s, 1));
    }

    @Test
    void severalIndependentReportersWithHighRiskAreFlagged() {
        double s = ThreatService.score(0.9, 3);
        assertTrue(ThreatService.isFlagged(s, 3));
    }

    @Test
    void manyReportersWithLowRiskAreNotFlagged() {
        double s = ThreatService.score(0.1, 10);
        assertFalse(ThreatService.isFlagged(s, 10));
    }
}
