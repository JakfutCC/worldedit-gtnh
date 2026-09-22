package com.sk89q.worldedit.forge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.Test;
import org.mockito.MockedStatic;

import com.sk89q.worldedit.Vector;

import cpw.mods.fml.common.Loader;

public class ForgeWorldBoundsTest {

    @Test(expected = ClassNotFoundException.class)
    public void optionalCubicChunksClassesAreAbsent() throws ClassNotFoundException {
        Class.forName("com.cardinalstar.cubicchunks.world.ICubicWorld");
    }

    @Test
    public void noCubicChunksRetainsVanillaBoundsWithoutLinkingOptionalClasses() {
        try (MockedStatic<Loader> loader = mockStatic(Loader.class)) {
            loader.when(() -> Loader.isModLoaded("cubicchunks"))
                .thenReturn(false);
            net.minecraft.world.World minecraftWorld = mock(net.minecraft.world.World.class);
            ForgeWorld world = new ForgeWorld(minecraftWorld);
            assertEquals(0, world.getMinY());
            assertEquals(255, world.getMaxY());
            assertEquals(0, world.getMinGenerationY());
            assertEquals(255, world.getMaxGenerationY());
            assertTrue(world.isNavigationPositionAvailable(new Vector(0, -32, 0)));
            assertTrue(world.isNavigationPositionAvailable(new Vector(0, 320, 0)));
            verifyNoInteractions(minecraftWorld);
        }
    }
}
