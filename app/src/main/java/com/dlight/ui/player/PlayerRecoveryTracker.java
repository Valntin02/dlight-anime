package com.dlight.ui.player;

final class PlayerRecoveryTracker {
    static final long NO_GENERATION = 0L;

    private boolean automaticRecoveryAttempted;
    private boolean destroyed;
    private long generation;
    private long activeGeneration = NO_GENERATION;

    PlayerRecoveryTracker(boolean automaticRecoveryAttempted) {
        this.automaticRecoveryAttempted = automaticRecoveryAttempted;
    }

    long beginAutomaticRecovery() {
        if (destroyed || automaticRecoveryAttempted) {
            return NO_GENERATION;
        }
        automaticRecoveryAttempted = true;
        return beginRecovery();
    }

    long beginUserRecovery() {
        if (destroyed) {
            return NO_GENERATION;
        }
        return beginRecovery();
    }

    boolean isCurrent(long candidateGeneration) {
        return !destroyed
            && candidateGeneration != NO_GENERATION
            && candidateGeneration == activeGeneration;
    }

    boolean complete(long candidateGeneration) {
        if (!isCurrent(candidateGeneration)) {
            return false;
        }
        activeGeneration = NO_GENERATION;
        return true;
    }

    void destroy() {
        destroyed = true;
        activeGeneration = NO_GENERATION;
    }

    private long beginRecovery() {
        generation++;
        activeGeneration = generation;
        return activeGeneration;
    }
}
