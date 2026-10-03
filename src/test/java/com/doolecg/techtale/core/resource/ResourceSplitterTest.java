package com.doolecg.techtale.core.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.doolecg.techtale.core.Direction;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResourceSplitterTest {
    private static final class Fake implements ResourceHandler {
        final long room;
        final String accepts;
        long got;

        Fake(long room, String accepts) {
            this.room = room;
            this.accepts = accepts;
        }

        @Override
        public boolean connects(ResourceKind kind, Direction side) {
            return true;
        }

        @Override
        public long insert(ResourceKind kind, Direction side, String type, long amount, boolean simulate) {
            if (!accepts.equals(type)) {
                return 0;
            }
            long a = Math.min(amount, room - got);
            if (!simulate) {
                got += a;
            }
            return a;
        }
    }

    private static ResourceSplitter.Target t(Fake f) {
        return new ResourceSplitter.Target(f, Direction.WEST);
    }

    @Test
    void splitsEvenly() {
        Fake a = new Fake(1000, "water");
        Fake b = new Fake(1000, "water");
        long sent = ResourceSplitter.distribute(ResourceKind.FLUID, "water", 101, List.of(t(a), t(b)));
        assertEquals(101, sent);
        assertEquals(101, a.got + b.got);
        assertEquals(1, Math.abs(a.got - b.got));
    }

    @Test
    void reoffersWhatAFullHandlerRefused() {
        Fake small = new Fake(10, "water");
        Fake big = new Fake(1000, "water");
        long sent = ResourceSplitter.distribute(ResourceKind.FLUID, "water", 200, List.of(t(small), t(big)));
        assertEquals(200, sent);
        assertEquals(10, small.got);
        assertEquals(190, big.got);
    }

    @Test
    void wrongTypeGetsNothing() {
        Fake lava = new Fake(1000, "lava");
        assertEquals(0, ResourceSplitter.distribute(ResourceKind.FLUID, "water", 100, List.of(t(lava))));
        assertEquals(0, lava.got);
    }

    @Test
    void nothingToSendOrNoTargets() {
        assertEquals(0, ResourceSplitter.distribute(ResourceKind.FLUID, "water", 0, List.of(t(new Fake(10, "water")))));
        assertEquals(0, ResourceSplitter.distribute(ResourceKind.FLUID, "water", 10, List.of()));
    }
}
