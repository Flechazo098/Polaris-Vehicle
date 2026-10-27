// Connected-texture compatibility disabled at user request; original source retained below.
// package com.flechazo.polaris_vehicle.client.compat;
//
// import net.minecraft.client.renderer.RenderType;
// import net.minecraft.client.renderer.block.model.BakedQuad;
// import net.minecraft.client.resources.model.BakedModel;
// import net.minecraft.core.BlockPos;
// import net.minecraft.world.level.BlockAndTintGetter;
// import net.minecraft.world.level.block.state.BlockState;
// import net.neoforged.fml.ModList;
// import net.neoforged.neoforge.client.model.data.ModelData;
// import org.jetbrains.annotations.Nullable;
//
// import java.util.List;
//
// /** Optional facade which never loads NeoContinuity classes when the mod is absent. */
// public final class NeoContinuityCompat {
//     private NeoContinuityCompat() {
//     }
//
//     /** Returns null when NeoContinuity is not installed; an empty list is valid. */
//     @Nullable
//     public static List<BakedQuad> capture(
//             BakedModel model,
//             BlockAndTintGetter level,
//             BlockState state,
//             BlockPos position,
//             ModelData modelData,
//             @Nullable RenderType renderType,
//             long seed
//     ) {
//         if (!ModList.get().isLoaded("continuity")) {
//             return null;
//         }
//         return NeoContinuityIntegration.capture(model, level, state, position,
//                 modelData, renderType, seed);
//     }
// }
