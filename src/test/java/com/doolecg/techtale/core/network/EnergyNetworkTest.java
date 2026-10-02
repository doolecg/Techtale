package com.doolecg.techtale.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.doolecg.techtale.core.BlockPos;
import com.doolecg.techtale.core.Direction;
import com.doolecg.techtale.core.energy.EnergyBuffer;
import com.doolecg.techtale.core.energy.EnergyHandler;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EnergyNetworkTest {
    private static final long CAP = 1000;

    /** A buffer-backed handler that connects only on the given sides and remembers which side was used. */
    private static final class FakeHandler implements EnergyHandler {
        final EnergyBuffer buffer;
        final Set<Direction> sides;
        final List<Direction> insertedThrough = new ArrayList<>();

        FakeHandler(long capacity, Set<Direction> sides) {
            this.buffer = new EnergyBuffer(capacity);
            this.sides = sides;
        }

        @Override
        public boolean canConnectEnergy(Direction side) {
            return sides.contains(side);
        }

        @Override
        public long insertEnergy(Direction side, long amount, boolean simulate) {
            if (!simulate) {
                insertedThrough.add(side);
            }
            return buffer.insert(amount, simulate);
        }
    }

    private NetworkGraph<EnergyNetwork> graph;
    private final Map<Long, EnergyHandler> world = new HashMap<>();
    private final List<Long> lookedUp = new ArrayList<>();
    private EnergyNetwork.HandlerLookup lookup;

    private static long at(int x, int y, int z) {
        return BlockPos.pack(x, y, z);
    }

    @BeforeEach
    void setUp() {
        graph = new NetworkGraph<>(EnergyNetwork::new);
        lookup = pos -> {
            lookedUp.add(pos);
            return world.get(pos);
        };
        // three cables along +X at y=64
        for (int x = 0; x < 3; x++) {
            graph.add(at(x, 64, 0), CAP, 0);
        }
    }

    private EnergyNetwork network() {
        return graph.networkAt(at(0, 64, 0));
    }

    @Test
    void distributesStoredEnergyToAnAdjacentHandler() {
        FakeHandler h = new FakeHandler(10_000, EnumSet.allOf(Direction.class));
        world.put(at(3, 64, 0), h);
        network().insert(2500, false);

        long sent = network().tick(lookup);

        assertEquals(2500, sent);
        assertEquals(2500, h.buffer.getStored());
        assertEquals(0, network().getStored());
        assertEquals(2500, network().getLastTransfer());
    }

    @Test
    void handlerSeesTheFaceTowardsTheCable() {
        FakeHandler east = new FakeHandler(10_000, EnumSet.allOf(Direction.class));
        FakeHandler above = new FakeHandler(10_000, EnumSet.allOf(Direction.class));
        world.put(at(3, 64, 0), east);
        world.put(at(1, 65, 0), above);
        network().insert(1000, false);

        network().tick(lookup);

        assertEquals(List.of(Direction.WEST), east.insertedThrough);
        assertEquals(List.of(Direction.DOWN), above.insertedThrough);
    }

    @Test
    void splitsBetweenSeveralHandlers() {
        FakeHandler a = new FakeHandler(10_000, EnumSet.allOf(Direction.class));
        FakeHandler b = new FakeHandler(10_000, EnumSet.allOf(Direction.class));
        world.put(at(3, 64, 0), a);
        world.put(at(-1, 64, 0), b);
        network().insert(2000, false);

        network().tick(lookup);

        assertEquals(1000, a.buffer.getStored());
        assertEquals(1000, b.buffer.getStored());
    }

    @Test
    void onlyAdjacentPositionsAreAsked() {
        FakeHandler far = new FakeHandler(10_000, EnumSet.allOf(Direction.class));
        FakeHandler diagonal = new FakeHandler(10_000, EnumSet.allOf(Direction.class));
        world.put(at(5, 64, 0), far);
        world.put(at(3, 65, 0), diagonal);
        network().insert(1000, false);

        network().tick(lookup);

        assertEquals(0, far.buffer.getStored());
        assertEquals(0, diagonal.buffer.getStored());
        assertEquals(1000, network().getStored());
        for (long pos : lookedUp) {
            assertFalse(network().contains(pos), "cable positions are not boundary faces");
            boolean adjacent = false;
            for (Direction d : Direction.ALL) {
                adjacent |= network().contains(BlockPos.offset(pos, d));
            }
            assertTrue(adjacent, "looked up a position not touching the network: " + BlockPos.toString(pos));
        }
    }

    @Test
    void respectsCanConnectEnergy() {
        // The face of the handler towards the cable (WEST) is closed.
        FakeHandler closed = new FakeHandler(10_000, EnumSet.complementOf(EnumSet.of(Direction.WEST)));
        FakeHandler open = new FakeHandler(10_000, EnumSet.allOf(Direction.class));
        world.put(at(3, 64, 0), closed);
        world.put(at(-1, 64, 0), open);
        network().insert(1000, false);

        long sent = network().tick(lookup);

        assertEquals(0, closed.buffer.getStored());
        assertEquals(1000, open.buffer.getStored());
        assertEquals(1000, sent);
    }

    @Test
    void neverSendsMoreThanStored() {
        FakeHandler greedy = new FakeHandler(Long.MAX_VALUE / 4, EnumSet.allOf(Direction.class));
        world.put(at(3, 64, 0), greedy);
        network().insert(700, false);

        long sent = network().tick(lookup);

        assertEquals(700, sent);
        assertEquals(700, greedy.buffer.getStored());
        assertEquals(0, network().getStored());
        assertEquals(0, network().tick(lookup));
    }

    @Test
    void keepsTheEnergyAHandlerCannotTake() {
        FakeHandler small = new FakeHandler(100, EnumSet.allOf(Direction.class));
        world.put(at(3, 64, 0), small);
        network().insert(1000, false);

        long sent = network().tick(lookup);

        assertEquals(100, sent);
        assertEquals(900, network().getStored());
    }

    @Test
    void emptyNetworkSendsNothing() {
        FakeHandler h = new FakeHandler(10_000, EnumSet.allOf(Direction.class));
        world.put(at(3, 64, 0), h);
        assertEquals(0, network().tick(lookup));
        assertEquals(0, h.buffer.getStored());
    }

    @Test
    void noHandlersMeansTheEnergyStays() {
        network().insert(500, false);
        assertEquals(0, network().tick(lookup));
        assertEquals(500, network().getStored());
    }
}
