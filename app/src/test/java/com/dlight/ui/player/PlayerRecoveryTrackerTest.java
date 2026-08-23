package com.dlight.ui.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PlayerRecoveryTrackerTest {
    @Test
    public void firstMissingSource_startsAutomaticRecovery() {
        PlayerRecoveryTracker tracker = new PlayerRecoveryTracker(false);

        long generation = tracker.beginAutomaticRecovery();

        assertNotEquals(PlayerRecoveryTracker.NO_GENERATION, generation);
        assertTrue(tracker.isCurrent(generation));
    }

    @Test
    public void secondAutomaticRecovery_isRejected() {
        PlayerRecoveryTracker tracker = new PlayerRecoveryTracker(false);
        tracker.beginAutomaticRecovery();

        long secondGeneration = tracker.beginAutomaticRecovery();

        assertEquals(PlayerRecoveryTracker.NO_GENERATION, secondGeneration);
    }

    @Test
    public void recoveredActivity_doesNotAutomaticallyRecoverAgain() {
        PlayerRecoveryTracker tracker = new PlayerRecoveryTracker(true);

        assertEquals(
            PlayerRecoveryTracker.NO_GENERATION,
            tracker.beginAutomaticRecovery()
        );
    }

    @Test
    public void explicitRetry_startsANewGeneration() {
        PlayerRecoveryTracker tracker = new PlayerRecoveryTracker(false);
        long automaticGeneration = tracker.beginAutomaticRecovery();

        long retryGeneration = tracker.beginUserRecovery();

        assertNotEquals(PlayerRecoveryTracker.NO_GENERATION, retryGeneration);
        assertNotEquals(automaticGeneration, retryGeneration);
        assertFalse(tracker.isCurrent(automaticGeneration));
        assertTrue(tracker.isCurrent(retryGeneration));
    }

    @Test
    public void staleGenerationCallbacks_areRejected() {
        PlayerRecoveryTracker tracker = new PlayerRecoveryTracker(false);
        long staleGeneration = tracker.beginAutomaticRecovery();
        long currentGeneration = tracker.beginUserRecovery();

        assertFalse(tracker.complete(staleGeneration));
        assertTrue(tracker.isCurrent(currentGeneration));
    }

    @Test
    public void destroy_invalidatesCallbacksAndRejectsNewRequests() {
        PlayerRecoveryTracker tracker = new PlayerRecoveryTracker(false);
        long generation = tracker.beginAutomaticRecovery();

        tracker.destroy();

        assertFalse(tracker.isCurrent(generation));
        assertFalse(tracker.complete(generation));
        assertEquals(PlayerRecoveryTracker.NO_GENERATION, tracker.beginUserRecovery());
    }

    @Test
    public void success_completesOnlyTheCurrentRequest() {
        PlayerRecoveryTracker tracker = new PlayerRecoveryTracker(false);
        long staleGeneration = tracker.beginAutomaticRecovery();
        long currentGeneration = tracker.beginUserRecovery();

        assertFalse(tracker.complete(staleGeneration));
        assertTrue(tracker.complete(currentGeneration));
        assertFalse(tracker.isCurrent(currentGeneration));
    }

    @Test
    public void failure_completesOnlyTheCurrentRequest() {
        PlayerRecoveryTracker tracker = new PlayerRecoveryTracker(false);
        long currentGeneration = tracker.beginAutomaticRecovery();

        assertFalse(tracker.complete(currentGeneration + 1));
        assertTrue(tracker.isCurrent(currentGeneration));
        assertTrue(tracker.complete(currentGeneration));
        assertFalse(tracker.complete(currentGeneration));
    }
}
