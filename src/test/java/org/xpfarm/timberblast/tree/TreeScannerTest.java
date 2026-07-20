/*
 * TimberBlast - a gunpowder-fuelled axe that fells a whole tree in one swing.
 * Copyright (C) 2026 Carmelo Santana
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * See the LICENSE file at the project root for the full license text.
 */
package org.xpfarm.timberblast.tree;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TreeScanner} against a hand-built {@link MapBlockQuery}.
 * Bounds mirror the shipped defaults (256 blocks, radius 8, height 32) except where a
 * test is specifically about a bound.
 */
class TreeScannerTest {

    private static final int MAX_BLOCKS = 256;
    private static final int MAX_RADIUS = 8;
    private static final int MAX_HEIGHT = 32;

    private final TreeScanner scanner = new TreeScanner();

    private static BlockPos at(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    private ScanResult scan(MapBlockQuery world) {
        return scanner.scan(at(0, 0, 0), world, MAX_BLOCKS, MAX_RADIUS, MAX_HEIGHT);
    }

    @Test
    void singleLog_isReturnedAlone() {
        ScanResult result = scan(new MapBlockQuery().put(0, 0, 0, BlockKind.LOG));

        assertEquals(List.of(at(0, 0, 0)), result.logs());
        assertEquals(List.of(), result.leaves());
        assertFalse(result.truncated());
    }

    @Test
    void straightTrunk_isCollectedBottomUpInOrder() {
        ScanResult result = scan(new MapBlockQuery().trunk(0, 0, 0, 5));

        assertEquals(
                List.of(at(0, 0, 0), at(0, 1, 0), at(0, 2, 0), at(0, 3, 0), at(0, 4, 0)),
                result.logs(),
                "breadth-first from the struck block gives a stable bottom-up order");
        assertFalse(result.truncated());
    }

    @Test
    void diagonalBranch_isFollowed() {
        MapBlockQuery world = new MapBlockQuery()
                .trunk(0, 0, 0, 4)
                .put(1, 4, 0, BlockKind.LOG)
                .put(2, 5, 0, BlockKind.LOG);

        ScanResult result = scan(world);

        assertEquals(
                List.of(at(0, 0, 0), at(0, 1, 0), at(0, 2, 0), at(0, 3, 0), at(1, 4, 0), at(2, 5, 0)),
                result.logs(),
                "a branch that touches the trunk only corner-to-corner must still come down");
    }

    @Test
    void twoByTwoTrunk_isCollectedIncludingTheDiagonalCorner() {
        MapBlockQuery world = new MapBlockQuery()
                .trunk(0, 0, 0, 2)
                .trunk(1, 0, 0, 2)
                .trunk(0, 0, 1, 2)
                .trunk(1, 0, 1, 2);

        ScanResult result = scan(world);

        // All seven other logs sit inside the origin's 26-neighbourhood, so a diagonal-aware
        // fill reaches every one of them on the first BFS level; a 6-neighbour fill would
        // still collect them all, but only over several levels and so in a different order.
        assertEquals(
                List.of(at(0, 0, 0), at(0, 0, 1), at(0, 1, 0), at(0, 1, 1),
                        at(1, 0, 0), at(1, 0, 1), at(1, 1, 0), at(1, 1, 1)),
                result.logs(),
                "the diagonally opposite corner of a 2x2 trunk is an immediate neighbour");
    }

    @Test
    void touchingCanopies_doNotChainTheNeighbouringTree() {
        // Two trunks four blocks apart, bridged by a continuous run of leaves.
        MapBlockQuery world = new MapBlockQuery()
                .trunk(0, 0, 0, 4)
                .trunk(4, 0, 0, 4)
                .put(1, 3, 0, BlockKind.LEAF)
                .put(2, 3, 0, BlockKind.LEAF)
                .put(3, 3, 0, BlockKind.LEAF);

        ScanResult result = scan(world);

        assertEquals(
                List.of(at(0, 0, 0), at(0, 1, 0), at(0, 2, 0), at(0, 3, 0)),
                result.logs(),
                "leaves must not be traversed through, or the neighbouring trunk gets felled too");
        assertFalse(result.logs().contains(at(4, 0, 0)));
        assertEquals(List.of(at(1, 3, 0)), result.leaves(),
                "only the leaf adjacent to a collected log is taken; the bridge beyond it is not");
    }

    @Test
    void leavesAdjacentToLogs_areCollectedButNeverExpandedFrom() {
        MapBlockQuery world = new MapBlockQuery()
                .trunk(0, 0, 0, 3)
                .put(1, 2, 0, BlockKind.LEAF)
                .put(2, 2, 0, BlockKind.LEAF)
                .put(3, 2, 0, BlockKind.LEAF);

        ScanResult result = scan(world);

        assertEquals(Set.of(at(1, 2, 0)), Set.copyOf(result.leaves()),
                "the second and third leaves are only reachable by expanding through a leaf");
        assertEquals(3, result.logs().size());
    }

    @Test
    void maxBlocks_capsTheLogCountAndFlagsTruncation() {
        ScanResult result = scanner.scan(at(0, 0, 0), new MapBlockQuery().trunk(0, 0, 0, 10),
                4, MAX_RADIUS, MAX_HEIGHT);

        assertEquals(4, result.logs().size(), "the cap counts logs");
        assertEquals(List.of(at(0, 0, 0), at(0, 1, 0), at(0, 2, 0), at(0, 3, 0)), result.logs());
        assertTrue(result.truncated());
    }

    @Test
    void treeThatExactlyFillsTheCap_isNotReportedAsTruncated() {
        ScanResult result = scanner.scan(at(0, 0, 0), new MapBlockQuery().trunk(0, 0, 0, 4),
                4, MAX_RADIUS, MAX_HEIGHT);

        assertEquals(4, result.logs().size());
        assertFalse(result.truncated(), "the flag means blocks were left standing, not that the cap was reached");
    }

    @Test
    void maxBlocksBelowOne_isTreatedAsOneAndStillFlagsTruncation() {
        ScanResult result = scanner.scan(at(0, 0, 0), new MapBlockQuery().trunk(0, 0, 0, 5),
                0, MAX_RADIUS, MAX_HEIGHT);

        assertEquals(List.of(at(0, 0, 0)), result.logs(), "the origin log is always collected");
        assertTrue(result.truncated(),
                "a cap the result could not honour must never claim the tree came down whole");
    }

    @Test
    void maxBlocksBelowOne_onALoneLogIsNotTruncated() {
        ScanResult result = scanner.scan(at(0, 0, 0), new MapBlockQuery().put(0, 0, 0, BlockKind.LOG),
                -5, MAX_RADIUS, MAX_HEIGHT);

        assertEquals(List.of(at(0, 0, 0)), result.logs());
        assertFalse(result.truncated(), "nothing was left standing, so nothing was truncated");
    }

    @Test
    void truncatedScan_stillReturnsTheLeavesFoundSoFar() {
        MapBlockQuery world = new MapBlockQuery()
                .trunk(0, 0, 0, 10)
                .put(1, 1, 0, BlockKind.LEAF);

        ScanResult result = scanner.scan(at(0, 0, 0), world, 3, MAX_RADIUS, MAX_HEIGHT);

        assertTrue(result.truncated());
        assertEquals(List.of(at(1, 1, 0)), result.leaves());
    }

    @Test
    void blocksBeyondMaxRadius_areExcluded() {
        MapBlockQuery world = new MapBlockQuery()
                .put(0, 0, 0, BlockKind.LOG)
                .put(1, 0, 0, BlockKind.LOG)
                .put(2, 0, 0, BlockKind.LOG)
                .put(3, 0, 0, BlockKind.LOG);

        ScanResult result = scanner.scan(at(0, 0, 0), world, MAX_BLOCKS, 2, MAX_HEIGHT);

        assertEquals(List.of(at(0, 0, 0), at(1, 0, 0), at(2, 0, 0)), result.logs());
        assertFalse(result.truncated(), "a bound is not a cap; hitting it does not truncate");
    }

    @Test
    void blocksBeyondMaxRadius_areExcludedAlongNegativeX() {
        MapBlockQuery world = new MapBlockQuery()
                .put(0, 0, 0, BlockKind.LOG)
                .put(-1, 0, 0, BlockKind.LOG)
                .put(-2, 0, 0, BlockKind.LOG)
                .put(-3, 0, 0, BlockKind.LOG);

        ScanResult result = scanner.scan(at(0, 0, 0), world, MAX_BLOCKS, 2, MAX_HEIGHT);

        assertEquals(List.of(at(0, 0, 0), at(-1, 0, 0), at(-2, 0, 0)), result.logs(),
                "the radius bound is an absolute distance, so it must hold west of the origin too");
    }

    @Test
    void blocksBeyondMaxRadius_areExcludedAlongPositiveZ() {
        MapBlockQuery world = new MapBlockQuery()
                .put(0, 0, 0, BlockKind.LOG)
                .put(0, 0, 1, BlockKind.LOG)
                .put(0, 0, 2, BlockKind.LOG)
                .put(0, 0, 3, BlockKind.LOG);

        ScanResult result = scanner.scan(at(0, 0, 0), world, MAX_BLOCKS, 2, MAX_HEIGHT);

        assertEquals(List.of(at(0, 0, 0), at(0, 0, 1), at(0, 0, 2)), result.logs(),
                "maxRadius bounds z as well as x, or a branch eats sideways past the limit");
    }

    @Test
    void blocksBeyondMaxRadius_areExcludedAlongNegativeZ() {
        MapBlockQuery world = new MapBlockQuery()
                .put(0, 0, 0, BlockKind.LOG)
                .put(0, 0, -1, BlockKind.LOG)
                .put(0, 0, -2, BlockKind.LOG)
                .put(0, 0, -3, BlockKind.LOG);

        ScanResult result = scanner.scan(at(0, 0, 0), world, MAX_BLOCKS, 2, MAX_HEIGHT);

        assertEquals(List.of(at(0, 0, 0), at(0, 0, -1), at(0, 0, -2)), result.logs(),
                "the z bound is an absolute distance, so it must hold north of the origin too");
    }

    @Test
    void leavesBeyondMaxRadius_areExcluded() {
        MapBlockQuery world = new MapBlockQuery()
                .put(0, 0, 0, BlockKind.LOG)
                .put(1, 0, 0, BlockKind.LOG)
                .put(2, 0, 0, BlockKind.LEAF);

        ScanResult result = scanner.scan(at(0, 0, 0), world, MAX_BLOCKS, 1, MAX_HEIGHT);

        assertEquals(List.of(at(0, 0, 0), at(1, 0, 0)), result.logs());
        assertEquals(List.of(), result.leaves(), "out-of-bounds blocks are not collected either");
    }

    @Test
    void blocksBeyondMaxHeight_areExcluded() {
        ScanResult result = scanner.scan(at(0, 0, 0), new MapBlockQuery().trunk(0, 0, 0, 5),
                MAX_BLOCKS, MAX_RADIUS, 2);

        assertEquals(List.of(at(0, 0, 0), at(0, 1, 0), at(0, 2, 0)), result.logs());
        assertFalse(result.truncated());
    }

    @Test
    void maxHeight_boundsDistanceBelowTheOriginToo() {
        MapBlockQuery world = new MapBlockQuery().trunk(0, -3, 0, 4);

        ScanResult result = scanner.scan(at(0, 0, 0), world, MAX_BLOCKS, MAX_RADIUS, 2);

        assertEquals(List.of(at(0, 0, 0), at(0, -1, 0), at(0, -2, 0)), result.logs(),
                "the height bound is an absolute distance, not a ceiling");
    }

    @Test
    void originThatIsNotALog_returnsEmptyResult() {
        MapBlockQuery world = new MapBlockQuery()
                .put(0, 0, 0, BlockKind.LEAF)
                .trunk(0, 1, 0, 3);

        ScanResult result = scan(world);

        assertEquals(List.of(), result.logs());
        assertEquals(List.of(), result.leaves());
        assertFalse(result.truncated());
    }

    @Test
    void originInEmptySpace_returnsEmptyResult() {
        ScanResult result = scan(new MapBlockQuery());

        assertEquals(List.of(), result.logs());
        assertEquals(List.of(), result.leaves());
    }
}
