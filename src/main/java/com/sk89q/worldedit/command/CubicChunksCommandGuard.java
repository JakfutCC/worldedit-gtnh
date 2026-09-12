package com.sk89q.worldedit.command;

import com.sk89q.worldedit.entity.Player;

import cpw.mods.fml.common.Loader;

final class CubicChunksCommandGuard {

    private CubicChunksCommandGuard() {}

    static boolean reject(Player player, String command) {
        if (!Loader.isModLoaded("cubicchunks")) {
            return false;
        }
        player.printError(command + " is disabled with CubicChunks because it is not cube-compatible.");
        return true;
    }
}
