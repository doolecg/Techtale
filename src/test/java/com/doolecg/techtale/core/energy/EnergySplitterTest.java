package com.doolecg.techtale.core.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.doolecg.techtale.core.Direction;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EnergySplitterTest {
    private static EnergyHandler handler(EnergyBuffer buffer) {
        return new EnergyHandler() {
            @Override
            public boolean canConnectEnergy(Direction side) {
                return true;
            }

            @Override
            public long insertEnergy(Direction side, long amount, boolean simulate) {
                return buffer.insert(amount, simulate);
            }
        };
    }

    private static List<EnergySplitter.Target> targets(EnergyBuffer... buffers) {
        List<EnergySplitter.Target> out = new ArrayList<>();
        for (EnergyBuffer b : buffers) {
            out.add(new EnergySplitter.Target(handler(b), Direction.UP));
        }
        return out;
    }

    @Test
    void splitsEvenly() {
        EnergyBuffer a = new EnergyBuffer(1000);
        EnergyBuffer b = new EnergyBuffer(1000);
        assertEquals(600, EnergySplitter.distribute(600, targets(a, b)));
        assertEquals(300, a.getStored());
        assertEquals(300, b.getStored());
    }

    @Test
    void fullTargetsLeftoverGoesToOthers() {
        EnergyBuffer small = new EnergyBuffer(100);
        EnergyBuffer big = new EnergyBuffer(10_000);
        EnergyBuffer full = new EnergyBuffer(10);
        full.setStored(10);
        assertEquals(1000, EnergySplitter.distribute(1000, targets(small, big, full)));
        assertEquals(100, small.getStored());
        assertEquals(900, big.getStored());
        assertEquals(10, full.getStored());
    }

    @Test
    void sendsOnlyWhatFits() {
        EnergyBuffer a = new EnergyBuffer(100);
        EnergyBuffer b = new EnergyBuffer(50);
        assertEquals(150, EnergySplitter.distribute(10_000, targets(a, b)));
        assertEquals(100, a.getStored());
        assertEquals(50, b.getStored());
    }

    @Test
    void remainderGoesToTheFirstTargets() {
        EnergyBuffer a = new EnergyBuffer(1000);
        EnergyBuffer b = new EnergyBuffer(1000);
        EnergyBuffer c = new EnergyBuffer(1000);
        assertEquals(10, EnergySplitter.distribute(10, targets(a, b, c)));
        assertEquals(4, a.getStored());
        assertEquals(3, b.getStored());
        assertEquals(3, c.getStored());
    }

    @Test
    void fewerJoulesThanTargetsStillAllSent() {
        EnergyBuffer a = new EnergyBuffer(1000);
        EnergyBuffer b = new EnergyBuffer(1000);
        EnergyBuffer c = new EnergyBuffer(1000);
        assertEquals(2, EnergySplitter.distribute(2, targets(a, b, c)));
        assertEquals(2, a.getStored() + b.getStored() + c.getStored());
    }

    @Test
    void zeroTargetsOrZeroEnergySendsNothing() {
        assertEquals(0, EnergySplitter.distribute(1000, List.of()));
        EnergyBuffer a = new EnergyBuffer(1000);
        assertEquals(0, EnergySplitter.distribute(0, targets(a)));
        assertEquals(0, EnergySplitter.distribute(-5, targets(a)));
        assertEquals(0, a.getStored());
    }

    @Test
    void allTargetsFullSendsNothing() {
        EnergyBuffer a = new EnergyBuffer(10);
        a.setStored(10);
        assertEquals(0, EnergySplitter.distribute(100, targets(a)));
    }
}
