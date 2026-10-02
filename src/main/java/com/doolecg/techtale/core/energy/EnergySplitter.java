package com.doolecg.techtale.core.energy;

import com.doolecg.techtale.core.Direction;
import java.util.ArrayList;
import java.util.List;

/** Splits an amount of energy evenly between handlers, re-offering what satisfied handlers did not take. */
public final class EnergySplitter {
    public record Target(EnergyHandler handler, Direction side) {
    }

    private EnergySplitter() {
    }

    /** @return the amount actually sent */
    public static long distribute(long available, List<Target> targets) {
        if (available <= 0 || targets.isEmpty()) {
            return 0;
        }
        List<Target> open = new ArrayList<>(targets);
        long remaining = available;
        while (remaining > 0 && !open.isEmpty()) {
            long share = remaining / open.size();
            long extra = remaining % open.size();
            long sentThisRound = 0;
            List<Target> stillOpen = new ArrayList<>(open.size());
            for (int i = 0; i < open.size(); i++) {
                Target t = open.get(i);
                long offer = share + (i < extra ? 1 : 0);
                if (offer <= 0) {
                    stillOpen.add(t);
                    continue;
                }
                long accepted = t.handler().insertEnergy(t.side(), offer, false);
                sentThisRound += accepted;
                if (accepted >= offer) {
                    stillOpen.add(t);
                }
            }
            remaining -= sentThisRound;
            if (sentThisRound == 0) {
                break;
            }
            open = stillOpen;
        }
        return available - remaining;
    }
}
