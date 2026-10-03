package com.doolecg.techtale.core.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.doolecg.techtale.core.BlockPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NetworkGraphTypedTest {
    private static final long CAP = 1000;

    private NetworkGraph<EnergyNetwork> graph;

    @BeforeEach
    void setUp() {
        graph = new NetworkGraph<>(EnergyNetwork::new);
    }

    private static long at(int x) {
        return BlockPos.pack(x, 64, 0);
    }

    @Test
    void splitKeepsTypeOnBothHalves() {
        for (int x = 0; x <= 4; x++) {
            graph.add(at(x), CAP, x == 0 ? 2000 : 0, x == 0 ? "water" : null);
        }
        assertEquals("water", graph.networkAt(at(0)).getType());
        graph.remove(at(2));
        EnergyNetwork left = graph.networkAt(at(0));
        EnergyNetwork right = graph.networkAt(at(4));
        assertEquals("water", left.getType());
        assertEquals("water", right.getType());
        assertEquals(true, left.getStored() > 0 && right.getStored() > 0);
    }

    @Test
    void sameTypeMergeSums() {
        graph.add(at(0), CAP, 300, "water");
        graph.add(at(2), CAP, 200, "water");
        graph.networks();
        graph.add(at(1), CAP, 0);
        EnergyNetwork n = graph.networkAt(at(0));
        assertEquals(500, n.getStored());
        assertEquals("water", n.getType());
    }

    @Test
    void mixedTypeMergeKeepsFirst() {
        graph.add(at(0), CAP, 300, "water");
        graph.networks();
        graph.add(at(1), CAP, 200, "lava");
        EnergyNetwork n = graph.networkAt(at(0));
        assertEquals(300, n.getStored());
        assertEquals("water", n.getType());
    }

    @Test
    void zeroStoredClearsType() {
        graph.add(at(0), CAP, 300, "water");
        EnergyNetwork n = graph.networkAt(at(0));
        n.setStored(0);
        assertNull(n.getType());
    }
}
