package com.flechazo.polaris_vehicle.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3fc;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.util.RenderUtil;

import java.util.ArrayList;
import java.util.List;

import static org.joml.Math.fma;

/**
 * Immutable cube-local transforms and vertex attributes; live bone poses stay outside this mesh.
 */
public final class PreparedVehicleMesh {
    private record Face(float nx, float ny, float nz, int flatAxes, float[] vertices) {
    }

    private final List<Face> faces;

    PreparedVehicleMesh(List<Face> faces) {
        this.faces = List.copyOf(faces);
    }

    public static PreparedVehicleMesh bake(GeoBone bone) {
        var faces = new ArrayList<Face>();
        var stack = new PoseStack();
        for (var cube : bone.getCubes()) {
            stack.pushPose();
            RenderUtil.translateToPivotPoint(stack, cube);
            RenderUtil.rotateMatrixAroundCube(stack, cube);
            RenderUtil.translateAwayFromPivotPoint(stack, cube);
            int axes = flatAxes(cube);
            for (var quad : cube.quads()) {
                if (quad == null) continue;
                var normal = stack.last().normal().transform(new Vector3f(quad.normal()));
                var packed = new float[quad.vertices().length * 5];
                for (int i = 0; i < quad.vertices().length; i++) {
                    var vertex = quad.vertices()[i];
                    var point = stack.last().pose().transform(new Vector4f(vertex.position(), 1));
                    int offset = i * 5;
                    packed[offset] = point.x;
                    packed[offset + 1] = point.y;
                    packed[offset + 2] = point.z;
                    packed[offset + 3] = vertex.texU();
                    packed[offset + 4] = vertex.texV();
                }
                faces.add(new Face(normal.x, normal.y, normal.z, axes, packed));
            }
            stack.popPose();
        }
        return new PreparedVehicleMesh(faces);
    }

    public void render(Matrix4fc pose, Matrix3fc normal, VertexConsumer buffer, int light, int overlay, int color) {
        for (var face : faces) {
            float nx = normal.m00() * face.nx + normal.m10() * face.ny + normal.m20() * face.nz;
            float ny = normal.m01() * face.nx + normal.m11() * face.ny + normal.m21() * face.nz;
            float nz = normal.m02() * face.nx + normal.m12() * face.ny + normal.m22() * face.nz;
            // Gecko's flat-cube correction is applied AFTER the current world
            // normal transform. Pre-baking abs(normal) changes lighting on turns.
            if ((face.flatAxes & 6) != 0 && nx < 0) nx = -nx;
            if ((face.flatAxes & 5) != 0 && ny < 0) ny = -ny;
            if ((face.flatAxes & 3) != 0 && nz < 0) nz = -nz;
            var v = face.vertices;
            for (int i = 0; i < v.length; i += 5) {
                float x = v[i], y = v[i + 1], z = v[i + 2];
                float px = fma(pose.m00(), x, fma(pose.m10(), y,
                        fma(pose.m20(), z, pose.m30())));
                float py = fma(pose.m01(), x, fma(pose.m11(), y,
                        fma(pose.m21(), z, pose.m31())));
                float pz = fma(pose.m02(), x, fma(pose.m12(), y,
                        fma(pose.m22(), z, pose.m32())));
                buffer.addVertex(px, py, pz, color, v[i + 3], v[i + 4], overlay, light, nx, ny, nz);
            }
        }
    }

    private static int flatAxes(GeoCube cube) {
        return (cube.size().x == 0 ? 1 : 0) | (cube.size().y == 0 ? 2 : 0) | (cube.size().z == 0 ? 4 : 0);
    }
}
