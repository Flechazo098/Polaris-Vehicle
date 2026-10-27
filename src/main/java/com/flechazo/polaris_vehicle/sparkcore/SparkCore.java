// SPDX-License-Identifier: MIT
// Vehicle-only bootstrap for script-extracted SparkCore 1.0.1031 MIT sources.
package com.flechazo.polaris_vehicle.sparkcore;

import com.flechazo.polaris_vehicle.sparkcore.pack.NativeLoader;
import com.flechazo.polaris_vehicle.sparkcore.physics.level.PhysicsLevelApplier;
import com.flechazo.polaris_vehicle.sparkcore.physics.terrain.BlockPhysicsDataMaps;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registration glue only; all solver/thread/terrain code is extracted upstream.
 */
public final class SparkCore {
    public static final String MOD_ID = "polaris_vehicle";
    public static final Logger LOGGER = LoggerFactory.getLogger("Polaris/SparkPhysics");

    public static Logger logger(String suffix) {
        return LoggerFactory.getLogger("Polaris/SparkPhysics/" + suffix);
    }

    public static void register(IEventBus modBus) {
        NativeLoader.load(MOD_ID, "bullet", "bullet_sp");
        modBus.addListener(BlockPhysicsDataMaps.INSTANCE::register);
        NeoForge.EVENT_BUS.register(PhysicsLevelApplier.INSTANCE);
        NeoForge.EVENT_BUS.addListener(BlockPhysicsDataMaps.INSTANCE::onAddReloadListener);
    }
}
