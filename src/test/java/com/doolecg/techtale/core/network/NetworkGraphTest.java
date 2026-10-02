package com.doolecg.techtale.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.doolecg.techtale.core.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NetworkGraphTest {
    private static final long CAP = 1000;

    private NetworkGraph<EnergyNetwork> graph;

    @BeforeEach
    void setUp() {
        graph = new NetworkGraph<>(EnergyNetwork::new);
    }

    private static long at(int x) {
        return BlockPos.pack(x, 64, 0);
    }

    private void addLine(int from, int toInclusive) {
        for (int x = from; x <= toInclusive; x++) {
            graph.add(at(x), CAP, 0);
        }
    }

    private long totalStored() {
        return graph.networks().stream().mapToLong(Network::getStored).sum();
    }

    @Test
    void aLineOfCablesIsOneNetwork() {
        addLine(0, 4);
        assertEquals(1, graph.networks().size());
        EnergyNetwork n = graph.networkAt(at(0));
        assertNotNull(n);
        assertEquals(5, n.size());
        for (int x = 0; x <= 4; x++) {
            assertSame(n, graph.networkAt(at(x)));
        }
    }

    @Test
    void capacityIsTheSumOfTheMembers() {
        graph.add(at(0), 1000, 0);
        graph.add(at(1), 2500, 0);
        graph.add(at(2), 500, 0);
        assertEquals(4000, graph.networkAt(at(0)).getCapacity());
    }

    @Test
    void separatedCablesAreSeparateNetworksAndDiagonalDoesNotConnect() {
        graph.add(at(0), CAP, 0);
        graph.add(at(2), CAP, 0);
        graph.add(BlockPos.pack(1, 65, 0), CAP, 0);
        assertEquals(3, graph.networks().size());
    }

    @Test
    void positionWithoutACableHasNoNetwork() {
        addLine(0, 1);
        assertNull(graph.networkAt(at(5)));
    }

    @Test
    void removingTheMiddleCableSplitsIntoTwoAndConservesEnergy() {
        addLine(0, 4);
        graph.networkAt(at(0)).insert(3000, false);

        long share = graph.remove(at(2));

        assertEquals(2, graph.networks().size());
        EnergyNetwork left = graph.networkAt(at(0));
        EnergyNetwork right = graph.networkAt(at(4));
        assertNotSame(left, right);
        assertSame(left, graph.networkAt(at(1)));
        assertSame(right, graph.networkAt(at(3)));
        assertNull(graph.networkAt(at(2)));
        assertEquals(2, left.size());
        assertEquals(2, right.size());
        assertEquals(2000, left.getCapacity());
        assertEquals(600, share);
        assertEquals(1200, left.getStored());
        assertEquals(1200, right.getStored());
        assertEquals(3000, left.getStored() + right.getStored() + share);
    }

    @Test
    void splitConservesEnergyWhenSharesDoNotDivideEvenly() {
        addLine(0, 6);
        graph.networkAt(at(0)).insert(4321, false);

        long share = graph.remove(at(3));

        assertEquals(4321, totalStored() + share);
    }

    @Test
    void removeReturnsTheMembersShare() {
        addLine(0, 3);
        graph.networkAt(at(0)).insert(2000, false);
        assertEquals(500, graph.remove(at(1)));
    }

    @Test
    void removingAnEndCableKeepsOneNetwork() {
        addLine(0, 3);
        graph.networkAt(at(0)).insert(2000, false);
        long share = graph.remove(at(3));
        assertEquals(1, graph.networks().size());
        assertEquals(500, share);
        assertEquals(1500, graph.networkAt(at(0)).getStored());
    }

    @Test
    void removingAnUnknownCableReturnsZero() {
        addLine(0, 1);
        assertEquals(0, graph.remove(at(9)));
        assertEquals(1, graph.networks().size());
    }

    @Test
    void removingTheOnlyCableLeavesNothing() {
        graph.add(at(0), CAP, 0);
        graph.networkAt(at(0)).insert(400, false);
        assertEquals(400, graph.remove(at(0)));
        assertTrue(graph.networks().isEmpty());
        assertFalse(graph.contains(at(0)));
    }

    @Test
    void addingABridgingCableMergesNetworksAndConservesEnergy() {
        addLine(0, 1);
        addLine(3, 4);
        graph.networkAt(at(0)).insert(700, false);
        graph.networkAt(at(3)).insert(301, false);
        assertEquals(2, graph.networks().size());

        graph.add(at(2), CAP, 0);

        assertEquals(1, graph.networks().size());
        EnergyNetwork merged = graph.networkAt(at(2));
        assertEquals(5, merged.size());
        assertEquals(5 * CAP, merged.getCapacity());
        assertEquals(1001, merged.getStored());
        for (int x = 0; x <= 4; x++) {
            assertSame(merged, graph.networkAt(at(x)));
        }
    }

    @Test
    void addingACableToTheEndOfANetworkKeepsItsEnergy() {
        addLine(0, 2);
        graph.networkAt(at(0)).insert(1500, false);
        graph.add(at(3), CAP, 0);
        EnergyNetwork n = graph.networkAt(at(0));
        assertEquals(4, n.size());
        assertEquals(1500, n.getStored());
    }

    @Test
    void pendingSharesEndUpInTheNetwork() {
        graph.add(at(0), CAP, 400);
        graph.add(at(1), CAP, 100);
        graph.add(at(10), CAP, 50);
        assertEquals(2, graph.networks().size());
        assertEquals(500, graph.networkAt(at(0)).getStored());
        assertEquals(50, graph.networkAt(at(10)).getStored());
    }

    @Test
    void sharesAreClampedToCapacity() {
        graph.add(at(0), 100, 5000);
        assertEquals(100, graph.networkAt(at(0)).getStored());
    }

    @Test
    void aSharedMemberJoiningAnExistingNetworkAddsItsShare() {
        addLine(0, 1);
        graph.networkAt(at(0)).insert(800, false);
        graph.add(at(2), CAP, 150);
        assertEquals(950, graph.networkAt(at(0)).getStored());
    }

    @Test
    void removeBeforeRebuildReturnsThePendingShare() {
        graph.add(at(0), CAP, 300);
        assertEquals(300, graph.remove(at(0)));
        assertTrue(graph.networks().isEmpty());
    }

    @Test
    void rebuildingTwiceIsStable() {
        addLine(0, 4);
        graph.networkAt(at(0)).insert(3333, false);
        graph.remove(at(2));

        graph.rebuildIfDirty();
        EnergyNetwork left = graph.networkAt(at(0));
        EnergyNetwork right = graph.networkAt(at(4));
        long leftStored = left.getStored();
        long rightStored = right.getStored();

        graph.rebuildIfDirty();

        assertFalse(graph.isDirty());
        assertEquals(2, graph.networks().size());
        assertSame(left, graph.networkAt(at(0)));
        assertSame(right, graph.networkAt(at(4)));
        assertEquals(leftStored, graph.networkAt(at(0)).getStored());
        assertEquals(rightStored, graph.networkAt(at(4)).getStored());
    }

    @Test
    void graphIsCleanAfterAMergeRebuild() {
        addLine(0, 1);
        addLine(3, 4);
        graph.rebuildIfDirty();
        graph.add(at(2), CAP, 0);
        graph.rebuildIfDirty();
        assertFalse(graph.isDirty());
        EnergyNetwork merged = graph.networkAt(at(0));
        assertSame(merged, graph.networkAt(at(4)));
    }
}
