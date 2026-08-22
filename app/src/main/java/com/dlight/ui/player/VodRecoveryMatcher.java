package com.dlight.ui.player;

import com.dlight.data.model.VodData;
import java.util.List;

public final class VodRecoveryMatcher {
    private VodRecoveryMatcher() {
    }

    public static VodData findBest(VodData current, List<VodData> candidates) {
        if (current == null || candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (current.getVod_id() > 0) {
            for (VodData item : candidates) {
                if (item != null && item.getVod_id() == current.getVod_id()) {
                    return item;
                }
            }
        }
        String name = current.getVod_name();
        if (name != null) {
            for (VodData item : candidates) {
                if (item != null && name.equals(item.getVod_name())) {
                    return item;
                }
            }
        }
        for (VodData item : candidates) {
            if (item != null) {
                return item;
            }
        }
        return null;
    }
}
