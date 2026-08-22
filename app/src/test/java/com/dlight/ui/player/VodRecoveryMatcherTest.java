package com.dlight.ui.player;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.dlight.data.model.VodData;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class VodRecoveryMatcherTest {
    @Test
    public void findBest_prefersMatchingPositiveIdOverMatchingName() {
        VodData current = vod(42, "Current");
        VodData byName = vod(7, "Current");
        VodData byId = vod(42, "Different");

        assertSame(byId, VodRecoveryMatcher.findBest(current, Arrays.asList(null, byName, byId)));
    }

    @Test
    public void findBest_usesExactNonNullNameWhenIdsDiffer() {
        VodData current = vod(42, "Current");
        VodData differentCase = vod(1, "current");
        VodData byName = vod(2, "Current");

        assertSame(byName, VodRecoveryMatcher.findBest(current, Arrays.asList(differentCase, byName)));
    }

    @Test
    public void findBest_returnsFirstNonNullCandidateWhenNothingMatches() {
        VodData current = vod(42, "Current");
        VodData first = vod(1, "First");
        VodData second = vod(2, "Second");

        assertSame(first, VodRecoveryMatcher.findBest(current, Arrays.asList(first, second)));
    }

    @Test
    public void findBest_skipsLeadingNullCandidate() {
        VodData current = vod(0, null);
        VodData firstNonNull = vod(1, "First");

        assertSame(firstNonNull, VodRecoveryMatcher.findBest(current, Arrays.asList(null, firstNonNull)));
    }

    @Test
    public void findBest_returnsNullForMissingInputsOrOnlyNullCandidates() {
        VodData current = vod(42, "Current");

        assertNull(VodRecoveryMatcher.findBest(null, Collections.singletonList(current)));
        assertNull(VodRecoveryMatcher.findBest(current, null));
        assertNull(VodRecoveryMatcher.findBest(current, Collections.emptyList()));
        assertNull(VodRecoveryMatcher.findBest(current, Arrays.asList(null, null)));
    }

    private static VodData vod(int id, String name) {
        return new VodData(id, name, null, null, null, null, null, null, null);
    }
}
