package com.flechazo.polaris_vehicle.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.*;
import software.bernie.geckolib.util.RenderUtil;

import java.util.ArrayList;
import java.util.List;

public final class StaticVehicleMesh {
    public static BakedGeoModel bake(BakedGeoModel source, int lod) {
        GeoBone root = new GeoBone(null, "static_chassis", false, 0.0, false, false);
        FaceRuns runs = new FaceRuns(root);
        PoseStack stack = new PoseStack();
        for (GeoBone bone : source.topLevelBones()) collect(bone, stack, runs, lod);
        runs.flush();
        root.saveInitialSnapshot();
        return new BakedGeoModel(List.of(root), source.properties());
    }

    private static void collect(GeoBone bone, PoseStack stack, FaceRuns destination, int lod) {
        // These are maintenance-only sensors. Their invisible geometry must
        // never be included in a static visual mesh.
        if (bone.getName().startsWith("interaction_wheel_") || Boolean.TRUE.equals(bone.shouldNeverRender())) return;
        stack.pushPose();
        RenderUtil.prepMatrixForBone(stack, bone);
        if (!bone.isHidden()) {
            for (GeoCube cube : bone.getCubes()) {
                if (!VehicleLod.retain(cube, lod)) continue;
                stack.pushPose();
                RenderUtil.translateToPivotPoint(stack, cube);
                RenderUtil.rotateMatrixAroundCube(stack, cube);
                RenderUtil.translateAwayFromPivotPoint(stack, cube);
                for (GeoQuad quad : cube.quads()) {
                    if (quad == null) continue;
                    GeoVertex[] vertices = new GeoVertex[quad.vertices().length];
                    for (int v = 0; v < vertices.length; v++) {
                        var vertex = quad.vertices()[v];
                        vertices[v] = new GeoVertex(stack.last().pose().transformPosition(
                                new Vector3f(vertex.position())), vertex.texU(), vertex.texV());
                    }
                    var normal = stack.last().normal().transform(new Vector3f(quad.normal()));
                    destination.add(cube, new GeoQuad(vertices, normal, quad.direction()));
                }
                stack.popPose();
            }
        }
        if (!bone.isHidingChildren()) {
            for (GeoBone child : bone.getChildBones()) collect(child, stack, destination, lod);
        }
        stack.popPose();
    }

    private static final class FaceRuns {
        private final GeoBone destination;
        private final List<GeoQuad> faces = new ArrayList<>();
        private int flatAxes = -1;

        FaceRuns(GeoBone destination) {
            this.destination = destination;
        }

        private void add(GeoCube original, GeoQuad face) {
            int axes = (original.size().x == 0 ? 1 : 0)
                    | (original.size().y == 0 ? 2 : 0) | (original.size().z == 0 ? 4 : 0);
            if (flatAxes != axes) {
                flush();
                flatAxes = axes;
            }
            faces.add(face);
        }

        private void flush() {
            if (faces.isEmpty()) return;
            Vec3 size = new Vec3((flatAxes & 1) == 0 ? 1 : 0,
                    (flatAxes & 2) == 0 ? 1 : 0, (flatAxes & 4) == 0 ? 1 : 0);
            destination.getCubes().add(new GeoCube(faces.toArray(GeoQuad[]::new),
                    Vec3.ZERO, Vec3.ZERO, size, 0, false));
            faces.clear();
        }
    }
}
