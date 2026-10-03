package com.doolecg.techtale.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.doolecg.techtale.core.BlockPos;
import com.doolecg.techtale.core.Direction;
import com.doolecg.techtale.core.resource.ResourceHandler;
import com.doolecg.techtale.core.resource.ResourceKind;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResourceNetworkTest {
    private static final long CAP = 1000;

    private static final class FakeHandler implements ResourceHandler {
        final long capacity;
        final Set<Direction> sides;
        final String accepts;
        final List<Direction> insertedThrough = new ArrayList<>();
        long stored;

        FakeHandler(long capacity, Set<Direction> sides, String accepts) {
            this.capacity = capacity;
            this.sides = sides;
            this.accepts = accepts;
        }

        @Override
        public boolean connects(ResourceKind kind, Direction side) {
            return kind == ResourceKind.FLUID && sides.contains(side);
        }

        @Override
        public long insert(ResourceKind kind, Direction side, String type, long amount, boolean simulate) {
            if (!accepts.equals(type)) {
                return 0;
            }
            long a = Math.min(amount, capacity - stored);
            if (!simulate) {
                stored += a;
                insertedThrough.add(side);
            }
            return a;
        }
    }

    private NetworkGraph<ResourceNetwork> graph;
    private final Map<Long, ResourceHandler> world = new HashMap<>();
    private ResourceNetwork.HandlerLookup lookup;

    private static long at(int x, int y, int z) {
        return BlockPos.pack(x, y, z);
    }

    private static FakeHandler water(long capacity) {
        return new FakeHandler(capacity, EnumSet.allOf(Direction.class), "water");
    }

    @BeforeEach
    void setUp() {
        graph = new NetworkGraph<>(() -> new ResourceNetwork(ResourceKind.FLUID));
        lookup = world::get;
        for (int x = 0; x < 3; x++) {
            graph.add(at(x, 64, 0), CAP, 0);
        }
    }

    private ResourceNetwork network() {
        return graph.networkAt(at(0, 64, 0));
    }

    @Test
    void pushesToAnAdjacentHandler() {
        FakeHandler h = water(10_000);
        world.put(at(3, 64, 0), h);
        network().insert("water", 500, false);

        assertEquals(500, network().tick(lookup));
        assertEquals(500, h.stored);
        assertEquals(0, network().getStored());
        assertNull(network().getType());
    }

    @Test
    void handlerSeesTheFaceTowardsThePipe() {
        FakeHandler east = water(10_000);
        FakeHandler above = water(10_000);
        world.put(at(3, 64, 0), east);
        world.put(at(1, 65, 0), above);
        network().insert("water", 1000, false);

        network().tick(lookup);

        assertEquals(List.of(Direction.WEST), east.insertedThrough);
        assertEquals(List.of(Direction.DOWN), above.insertedThrough);
    }

    @Test
    void respectsConnects() {
        FakeHandler closed = new FakeHandler(10_000, EnumSet.complementOf(EnumSet.of(Direction.WEST)), "water");
        FakeHandler open = water(10_000);
        world.put(at(3, 64, 0), closed);
        world.put(at(-1, 64, 0), open);
        network().insert("water", 1000, false);

        assertEquals(1000, network().tick(lookup));
        assertEquals(0, closed.stored);
        assertEquals(1000, open.stored);
    }

    @Test
    void onlyMatchingOrEmptyTypeIsAccepted() {
        assertEquals(400, network().insert("water", 400, false));
        assertEquals(0, network().insert("lava", 100, false));
        assertEquals("water", network().getType());
        assertEquals(400, network().getStored());
        assertEquals(0, network().insert(100, false));
    }

    @Test
    void handlerOfAnotherTypeGetsNothing() {
        FakeHandler lava = new FakeHandler(10_000, EnumSet.allOf(Direction.class), "lava");
        world.put(at(3, 64, 0), lava);
        network().insert("water", 300, false);

        assertEquals(0, network().tick(lookup));
        assertEquals(0, lava.stored);
        assertEquals(300, network().getStored());
        assertEquals("water", network().getType());
    }

    @Test
    void emptyNetworkTakesANewTypeAfterDraining() {
        world.put(at(3, 64, 0), water(10_000));
        network().insert("water", 300, false);
        network().tick(lookup);
        assertEquals(100, network().insert("lava", 100, false));
        assertEquals("lava", network().getType());
    }

    @Test
    void doesNotFlowBackIntoAHandlerThatRefusesThePipeFace() {
        // a source whose pipe-facing side is closed (it only outputs) never receives from the network
        FakeHandler source = new FakeHandler(10_000, EnumSet.complementOf(EnumSet.of(Direction.EAST)), "water");
        FakeHandler sink = water(10_000);
        world.put(at(-1, 64, 0), source);
        world.put(at(3, 64, 0), sink);
        network().insert("water", 1000, false);

        network().tick(lookup);

        assertEquals(0, source.stored);
        assertEquals(1000, sink.stored);
    }

    @Test
    void partialAcceptanceKeepsTheRest() {
        FakeHandler small = water(100);
        world.put(at(3, 64, 0), small);
        network().insert("water", 1000, false);

        assertEquals(100, network().tick(lookup));
        assertEquals(900, network().getStored());
        assertEquals("water", network().getType());
        assertEquals(100, network().getLastTransfer());
    }
}
