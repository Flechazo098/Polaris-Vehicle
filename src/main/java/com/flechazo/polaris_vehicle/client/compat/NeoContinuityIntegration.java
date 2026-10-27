// Connected-texture compatibility disabled at user request; original source retained below.
// package com.flechazo.polaris_vehicle.client.compat;
//
// import com.flechazo.polaris_vehicle.PolarisVehicle;
// import me.pepperbell.continuity.client.util.RenderUtil;
// import net.fabricmc.fabric.api.renderer.v1.Renderer;
// import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
// import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
// import net.fabricmc.fabric.api.renderer.v1.mesh.Mesh;
// import net.fabricmc.fabric.api.renderer.v1.mesh.MeshBuilder;
// import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
// import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
// import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
// import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
// import net.minecraft.client.renderer.RenderType;
// import net.minecraft.client.renderer.block.model.BakedQuad;
// import net.minecraft.client.renderer.texture.TextureAtlasSprite;
// import net.minecraft.client.resources.model.BakedModel;
// import net.minecraft.core.BlockPos;
// import net.minecraft.core.Direction;
// import net.minecraft.util.RandomSource;
// import net.minecraft.world.item.ItemDisplayContext;
// import net.minecraft.world.level.BlockAndTintGetter;
// import net.minecraft.world.level.block.state.BlockState;
// import net.neoforged.neoforge.client.model.data.ModelData;
// import org.jetbrains.annotations.Nullable;
// import org.joml.Vector2f;
// import org.joml.Vector3f;
//
// import java.util.ArrayList;
// import java.util.List;
// import java.util.function.Consumer;
//
// /** Direct integration for NeoContinuity 3.0.0+0.0.1 on Minecraft 1.21.1. */
// final class NeoContinuityIntegration {
//     private static boolean warned;
//
//     private NeoContinuityIntegration() {
//     }
//
//     @Nullable
//     static List<BakedQuad> capture(
//             BakedModel model,
//             BlockAndTintGetter level,
//             BlockState state,
//             BlockPos position,
//             ModelData modelData,
//             @Nullable RenderType renderType,
//             long seed
//     ) {
//         if (!(model instanceof FabricBakedModel fabricModel)) {
//             return null;
//         }
//         try {
//             Renderer renderer = RendererAccess.INSTANCE.getRenderer();
//             if (renderer == null) {
//                 return null;
//             }
//             MeshBuilder builder = renderer.meshBuilder();
//             RenderMaterial standardMaterial = renderer.materialFinder().clear().find();
//             CapturingContext context = new CapturingContext(builder.getEmitter(),
//                     standardMaterial, state, modelData, renderType, seed);
//             fabricModel.emitBlockQuads(level, state, position,
//                     () -> RandomSource.create(seed), context);
//
//             List<BakedQuad> result = new ArrayList<>();
//             builder.build().forEach(quad -> {
//                 TextureAtlasSprite sprite = RenderUtil
//                         .getSpriteFinder().find(quad);
//                 result.add(quad.toBakedQuad(sprite));
//             });
//             return List.copyOf(result);
//         } catch (RuntimeException | LinkageError exception) {
//             if (!warned) {
//                 warned = true;
//             }
//             return null;
//         }
//     }
//
//     @SuppressWarnings("removal")
//     private static final class CapturingContext implements RenderContext {
//         private final TransformingEmitter emitter;
//         private final RenderMaterial standardMaterial;
//         private final BlockState state;
//         private final ModelData modelData;
//         @Nullable
//         private final RenderType renderType;
//         private final long seed;
//         private final List<QuadTransform> transforms = new ArrayList<>();
//         private final BakedModelConsumer bakedModelConsumer = new BakedModelConsumer() {
//             @Override
//             public void accept(BakedModel model) {
//                 accept(model, state);
//             }
//
//             @Override
//             public void accept(BakedModel model, BlockState emittedState) {
//                 emitVanillaModel(model, emittedState);
//             }
//         };
//
//         private CapturingContext(QuadEmitter rawEmitter, RenderMaterial standardMaterial,
//                                  BlockState state, ModelData modelData,
//                                  @Nullable RenderType renderType, long seed) {
//             this.emitter = new TransformingEmitter(rawEmitter, transforms);
//             this.standardMaterial = standardMaterial;
//             this.state = state;
//             this.modelData = modelData;
//             this.renderType = renderType;
//             this.seed = seed;
//         }
//
//         @Override
//         public QuadEmitter getEmitter() {
//             return emitter;
//         }
//
//         @Override
//         public boolean hasTransform() {
//             return !transforms.isEmpty();
//         }
//
//         @Override
//         public void pushTransform(QuadTransform transform) {
//             transforms.add(transform);
//         }
//
//         @Override
//         public void popTransform() {
//             transforms.removeLast();
//         }
//
//         @Override
//         public boolean isFaceCulled(@Nullable Direction direction) {
//             return false;
//         }
//
//         @Override
//         public ItemDisplayContext itemTransformationMode() {
//             return ItemDisplayContext.NONE;
//         }
//
//         @Override
//         public Consumer<Mesh> meshConsumer() {
//             return mesh -> mesh.forEach(quad -> emitter.copyFrom(quad).emit());
//         }
//
//         @Override
//         public BakedModelConsumer bakedModelConsumer() {
//             return bakedModelConsumer;
//         }
//
//         @Override
//         public ModelData getModelData() {
//             return modelData;
//         }
//
//         @Override
//         public RenderType getRenderType() {
//             return renderType;
//         }
//
//         private void emitVanillaModel(BakedModel model, BlockState emittedState) {
//             for (Direction side : Direction.values()) {
//                 emitQuads(model.getQuads(emittedState, side, RandomSource.create(seed),
//                         modelData, renderType), side);
//             }
//             emitQuads(model.getQuads(emittedState, null, RandomSource.create(seed),
//                     modelData, renderType), null);
//         }
//
//         private void emitQuads(List<BakedQuad> quads, @Nullable Direction cullFace) {
//             for (BakedQuad quad : quads) {
//                 emitter.fromVanilla(quad, standardMaterial, cullFace).emit();
//             }
//         }
//     }
//
//     /**
//      * Delegates storage to Indigo while applying the current transform stack.
//      */
//         private record TransformingEmitter(QuadEmitter delegate,
//                                            List<RenderContext.QuadTransform> transforms) implements QuadEmitter {
//
//         @Override
//             public TransformingEmitter pos(int vertex, float x, float y, float z) {
//                 delegate.pos(vertex, x, y, z);
//                 return this;
//             }
//
//             @Override
//             public TransformingEmitter color(int vertex, int color) {
//                 delegate.color(vertex, color);
//                 return this;
//             }
//
//             @Override
//             public TransformingEmitter uv(int vertex, float u, float v) {
//                 delegate.uv(vertex, u, v);
//                 return this;
//             }
//
//             @Override
//             public TransformingEmitter spriteBake(TextureAtlasSprite sprite, int flags) {
//                 delegate.spriteBake(sprite, flags);
//                 return this;
//             }
//
//             @Override
//             public TransformingEmitter lightmap(int vertex, int lightmap) {
//                 delegate.lightmap(vertex, lightmap);
//                 return this;
//             }
//
//             @Override
//             public TransformingEmitter normal(int vertex, float x, float y, float z) {
//                 delegate.normal(vertex, x, y, z);
//                 return this;
//             }
//
//             @Override
//             public TransformingEmitter cullFace(@Nullable Direction face) {
//                 delegate.cullFace(face);
//                 return this;
//             }
//
//             @Override
//             public TransformingEmitter nominalFace(@Nullable Direction face) {
//                 delegate.nominalFace(face);
//                 return this;
//             }
//
//             @Override
//             public TransformingEmitter material(RenderMaterial material) {
//                 delegate.material(material);
//                 return this;
//             }
//
//             @Override
//             public TransformingEmitter colorIndex(int index) {
//                 delegate.colorIndex(index);
//                 return this;
//             }
//
//             @Override
//             public TransformingEmitter tag(int tag) {
//                 delegate.tag(tag);
//                 return this;
//             }
//
//             @Override
//             public TransformingEmitter copyFrom(QuadView quad) {
//                 delegate.copyFrom(quad);
//                 return this;
//             }
//
//             @Override
//             public TransformingEmitter fromVanilla(int[] data, int startIndex) {
//                 delegate.fromVanilla(data, startIndex);
//                 return this;
//             }
//
//             @Override
//             public TransformingEmitter fromVanilla(BakedQuad quad, RenderMaterial material,
//                                                    @Nullable Direction cullFace) {
//                 delegate.fromVanilla(quad, material, cullFace);
//                 return this;
//             }
//
//             @Override
//             public TransformingEmitter emit() {
//                 boolean keep = true;
//                 for (int index = transforms.size() - 1; index >= 0 && keep; index--) {
//                     keep = transforms.get(index).transform(delegate);
//                 }
//                 if (keep) {
//                     delegate.emit();
//                 }
//                 return this;
//             }
//
//             @Override
//             public float x(int vertex) {
//                 return delegate.x(vertex);
//             }
//
//         @Override
//         public float y(int vertex) {
//             return delegate.y(vertex);
//         }
//
//         @Override
//         public float z(int vertex) {
//             return delegate.z(vertex);
//         }
//
//         @Override
//         public float posByIndex(int vertex, int coordinate) {
//                 return delegate.posByIndex(vertex, coordinate);
//             }
//
//         @Override
//         public Vector3f copyPos(int vertex, Vector3f target) {
//                 return delegate.copyPos(vertex, target);
//             }
//
//         @Override
//         public int color(int vertex) {
//             return delegate.color(vertex);
//         }
//
//         @Override
//         public float u(int vertex) {
//             return delegate.u(vertex);
//         }
//
//         @Override
//         public float v(int vertex) {
//             return delegate.v(vertex);
//         }
//
//         @Override
//         public Vector2f copyUv(int vertex, Vector2f target) {
//                 return delegate.copyUv(vertex, target);
//             }
//
//         @Override
//         public int lightmap(int vertex) {
//             return delegate.lightmap(vertex);
//         }
//
//         @Override
//         public boolean hasNormal(int vertex) {
//             return delegate.hasNormal(vertex);
//         }
//
//         @Override
//         public float normalX(int vertex) {
//             return delegate.normalX(vertex);
//         }
//
//         @Override
//         public float normalY(int vertex) {
//             return delegate.normalY(vertex);
//         }
//
//         @Override
//         public float normalZ(int vertex) {
//             return delegate.normalZ(vertex);
//         }
//
//         @Override
//         public Vector3f copyNormal(int vertex, Vector3f target) {
//                 return delegate.copyNormal(vertex, target);
//             }
//
//         @Override
//         public Direction cullFace() {
//             return delegate.cullFace();
//         }
//
//         @Override
//         public Direction lightFace() {
//             return delegate.lightFace();
//         }
//
//         @Override
//         public Direction nominalFace() {
//             return delegate.nominalFace();
//         }
//
//         @Override
//         public Vector3f faceNormal() {
//             return delegate.faceNormal();
//         }
//
//         @Override
//         public RenderMaterial material() {
//             return delegate.material();
//         }
//
//         @Override
//         public int colorIndex() {
//             return delegate.colorIndex();
//         }
//
//         @Override
//         public int tag() {
//             return delegate.tag();
//         }
//
//         @Override
//         public void toVanilla(int[] target, int startIndex) {
//                 delegate.toVanilla(target, startIndex);
//             }
//         }
// }
