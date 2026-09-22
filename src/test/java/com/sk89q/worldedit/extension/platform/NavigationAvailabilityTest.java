package com.sk89q.worldedit.extension.platform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntUnaryOperator;

import org.junit.Test;

import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.WorldVector;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.blocks.BlockID;
import com.sk89q.worldedit.internal.LocalWorldAdapter;
import com.sk89q.worldedit.world.World;

public class NavigationAvailabilityTest {

    @Test
    public void descendStopsAtUnloadedCubeInVoid() {
        for (int bottom : new int[] { -32, 320 }) {
            Scene scene = new Scene(bottom, bottom + 15, bottom + 14, y -> BlockID.AIR);
            assertFalse(scene.player.descendLevel());
            assertNull(scene.destination.get());
            assertTrue(scene.reads <= 16);
        }
    }

    @Test
    public void descendStopsAtUnloadedCubeInSolidColumn() {
        Scene scene = new Scene(-32, -17, -18, y -> BlockID.STONE);
        assertFalse(scene.player.descendLevel());
        assertNull(scene.destination.get());
    }

    @Test
    public void descendFindsLoadedFloorsAcrossCubeBoundaries() {
        for (int floor : new int[] { -36, 312 }) {
            Scene scene = new Scene(floor - 12, floor + 35, floor + 22, y -> y == floor ? BlockID.STONE : BlockID.AIR);
            assertTrue(scene.player.descendLevel());
            assertEquals(
                floor + 1,
                scene.destination.get()
                    .getY(),
                0);
        }
    }

    @Test
    public void descendDoesNotJumpAcrossUnloadedGap() {
        Scene scene = new Scene(-16, -1, -2, y -> y == -33 ? BlockID.STONE : BlockID.AIR);
        assertFalse(scene.player.descendLevel());
        assertNull(scene.destination.get());
    }

    @Test
    public void freePositionStopsAtUnloadedCubeInSolidColumn() {
        Scene scene = new Scene(320, 335, 320, y -> BlockID.STONE);
        scene.player.findFreePosition();
        assertNull(scene.destination.get());
        assertEquals(16, scene.reads);
    }

    @Test
    public void freePositionFindsNegativeYDestination() {
        Scene scene = new Scene(-32, -1, -30, y -> y < -26 ? BlockID.STONE : BlockID.AIR);
        scene.player.findFreePosition();
        assertEquals(
            -26,
            scene.destination.get()
                .getY(),
            0);
    }

    @Test
    public void groundSearchStopsAtUnloadedCube() {
        Scene scene = new Scene(-32, -17, -17, y -> BlockID.AIR);
        scene.player.setOnGround(scene.origin);
        assertNull(scene.destination.get());
        assertEquals(32, scene.reads);
    }

    @Test
    public void groundSearchFindsLoadedNegativeFloor() {
        Scene scene = new Scene(-48, -1, -17, y -> y == -36 ? BlockID.STONE : BlockID.AIR);
        scene.player.setOnGround(scene.origin);
        assertEquals(
            -35,
            scene.destination.get()
                .getY(),
            0);
    }

    @Test
    public void groundSearchRejectsDestinationAboveLoadedCube() {
        Scene scene = new Scene(0, 15, 15, y -> BlockID.STONE);
        scene.player.setOnGround(scene.origin);
        assertNull(scene.destination.get());
    }

    @Test
    public void tallSupportRequiresLoadedHeadSpace() {
        Scene descend = new Scene(0, 15, 16, y -> y == 13 ? BlockID.FENCE : BlockID.AIR);
        assertFalse(descend.player.descendLevel());
        assertNull(descend.destination.get());
        Scene free = new Scene(0, 15, 13, y -> y == 13 ? BlockID.FENCE : BlockID.AIR);
        free.player.findFreePosition();
        assertNull(free.destination.get());
        Scene ascend = new Scene(0, 15, 10, y -> y == 13 ? BlockID.FENCE : BlockID.AIR);
        assertFalse(ascend.player.ascendLevel());
        assertNull(ascend.destination.get());
    }

    @Test
    public void tallSupportWorksWhenHeadCubeIsLoaded() {
        Scene scene = new Scene(0, 31, 16, y -> y == 13 ? BlockID.FENCE : BlockID.AIR);
        assertTrue(scene.player.descendLevel());
        assertEquals(
            14.5,
            scene.destination.get()
                .getY(),
            0);
    }

    @Test
    public void fullBlockEdgeUsesContainingBlocksForFeetAndHead() {
        for (int bottom : new int[] { -32, 0, 320 }) {
            Scene scene = new Scene(
                bottom,
                bottom + 15,
                bottom + 16,
                y -> y == bottom + 13 ? BlockID.STONE : BlockID.AIR);
            doReturn(new WorldVector(scene.origin.getWorld(), 15, bottom + 16, -1)).when(scene.player)
                .getPosition();
            when(scene.world.isNavigationPositionAvailable(any(Vector.class))).thenAnswer(call -> {
                Vector pos = call.getArgument(0);
                return pos.getBlockX() == 15 && pos.getBlockZ() == -1
                    && pos.getBlockY() >= bottom
                    && pos.getBlockY() <= bottom + 15;
            });
            assertTrue(scene.player.descendLevel());
            assertEquals(
                bottom + 14,
                scene.destination.get()
                    .getY(),
                0);
        }
    }

    @Test
    public void upwardPlatformRequiresLoadedSupportAndHead() {
        Scene missingSupport = new Scene(320, 335, 320, y -> BlockID.AIR);
        assertFalse(missingSupport.player.ascendUpwards(0));
        assertNull(missingSupport.destination.get());
        Scene missingHead = new Scene(320, 335, 334, y -> BlockID.AIR);
        assertFalse(missingHead.player.ascendUpwards(1));
        assertNull(missingHead.destination.get());
        Scene available = new Scene(320, 351, 334, y -> BlockID.AIR);
        assertTrue(available.player.ascendUpwards(1));
        assertEquals(
            335,
            available.destination.get()
                .getY(),
            0);
    }

    @Test
    public void upwardHelpersStopAtUnloadedCube() {
        Scene solid = new Scene(320, 335, 320, y -> BlockID.STONE);
        assertFalse(solid.player.ascendLevel());
        Scene air = new Scene(320, 335, 320, y -> BlockID.AIR);
        assertFalse(air.player.ascendToCeiling(0));
        assertFalse(air.player.ascendUpwards(100000000));
        assertNull(air.destination.get());
    }

    @Test
    public void ceilingDoesNotReadUnavailableStartingPosition() {
        Scene scene = new Scene(320, 335, 334, y -> BlockID.AIR);
        assertFalse(scene.player.ascendToCeiling(0));
        assertEquals(0, scene.reads);
    }

    @Test
    public void adapterForwardsAvailabilityIncludingNegativeY() {
        Scene scene = new Scene(-32, -17, -20, y -> BlockID.AIR);
        assertTrue(
            scene.origin.getWorld()
                .isNavigationPositionAvailable(new Vector(0, -32, 0)));
        assertFalse(
            scene.origin.getWorld()
                .isNavigationPositionAvailable(new Vector(0, -33, 0)));
        assertFalse(
            scene.origin.getWorld()
                .isNavigationPositionAvailable(new Vector(0, 320, 0)));
    }

    @Test
    public void defaultWorldPolicyRetainsLegacySearch() {
        Scene scene = new Scene(0, 257, 64, y -> y < 66 ? BlockID.STONE : BlockID.AIR);
        when(scene.world.getMinY()).thenReturn(0);
        when(scene.world.getMaxY()).thenReturn(255);
        when(scene.world.isNavigationPositionAvailable(any(Vector.class))).thenCallRealMethod();
        assertTrue(scene.world.isNavigationPositionAvailable(new Vector(0, -32, 0)));
        scene.player.findFreePosition();
        assertEquals(
            66,
            scene.destination.get()
                .getY(),
            0);
    }

    private static final class Scene {

        final World world = mock(World.class);
        final AbstractPlayerActor player = mock(AbstractPlayerActor.class, CALLS_REAL_METHODS);
        final AtomicReference<Vector> destination = new AtomicReference<>();
        final WorldVector origin;
        int reads;

        Scene(int loadedMin, int loadedMax, int startY, IntUnaryOperator blocks) {
            when(world.getMinY()).thenReturn(-1073741824);
            when(world.getMaxY()).thenReturn(1073741823);
            when(world.isNavigationPositionAvailable(any(Vector.class))).thenAnswer(call -> {
                int y = ((Vector) call.getArgument(0)).getBlockY();
                return y >= loadedMin && y <= loadedMax;
            });
            when(world.getBlock(any(Vector.class)))
                .thenAnswer(call -> new BaseBlock(read(call.getArgument(0), loadedMin, loadedMax, blocks)));
            when(world.getBlockType(any(Vector.class)))
                .thenAnswer(call -> read(call.getArgument(0), loadedMin, loadedMax, blocks));
            when(world.getBlockData(any(Vector.class))).thenAnswer(call -> {
                read(call.getArgument(0), loadedMin, loadedMax, blocks);
                return 0;
            });
            try {
                when(world.setBlock(any(Vector.class), any(BaseBlock.class), anyBoolean())).thenAnswer(call -> {
                    read(call.getArgument(0), loadedMin, loadedMax, blocks);
                    return true;
                });
            } catch (WorldEditException exception) {
                throw new AssertionError(exception);
            }
            origin = new WorldVector(LocalWorldAdapter.adapt(world), 0, startY, 0);
            doReturn(origin).when(player)
                .getPosition();
            doReturn(world).when(player)
                .getWorld();
            doAnswer(call -> {
                destination.set(call.getArgument(0));
                return null;
            }).when(player)
                .setPosition(any(Vector.class), anyFloat(), anyFloat());
        }

        private int read(Vector position, int loadedMin, int loadedMax, IntUnaryOperator blocks) {
            int y = position.getBlockY();
            assertTrue("Read unavailable Y=" + y, y >= loadedMin && y <= loadedMax);
            assertTrue("Navigation exceeded loaded terrain budget", ++reads <= 256);
            return blocks.applyAsInt(y);
        }
    }
}
