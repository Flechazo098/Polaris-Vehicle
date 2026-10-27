package com.flechazo.polaris_vehicle.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.*;
import software.bernie.geckolib.util.RenderUtil;

/**
 * Extent of a baked model in its own render space, where one block is one unit.
 *
 * <p>GeckoLib exposes no bounds helper, so the walk here mirrors
 * {@code GeoRenderer.renderRecursively} / {@code renderCube}: replay each bone's
 * pivot/rotation/scale and each cube's offsets with the same {@link RenderUtil} calls the
 * renderer uses, then fold every quad vertex through the resulting matrix.  Authored models
 * place their origin wherever the artist put it, so item renderers need this to centre and
 * scale a model without a hand-tuned constant per asset.
 *
 * <p>A model tilted for the inventory slot is wider than its axis-aligned box, because the
 * diagonal swings into view; fitting by the box alone lets tall or deep models spill over
 * the slot edge.  The projected extents below are therefore measured against the same
 * rotation the renderer applies, which is the quantity that actually has to fit.
 */
public final class ModelBounds {
    private static final float UNSET = Float.MAX_VALUE;

    private float minX = UNSET;
    private float minY = UNSET;
    private float minZ = UNSET;
    private float maxX = -UNSET;
    private float maxY = -UNSET;
    private float maxZ = -UNSET;
    private float minProjectedX = UNSET;
    private float maxProjectedX = -UNSET;
    private float minProjectedY = UNSET;
    private float maxProjectedY = -UNSET;

    private ModelBounds() {
    }

    /**
     * @param pitchDegrees inventory tilt about X, matching the renderer's first rotation
     * @param yawDegrees   inventory turn about Y, applied before the tilt
     */
    public static ModelBounds of(BakedGeoModel model, float pitchDegrees, float yawDegrees) {
        ModelBounds bounds = new ModelBounds();
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        PoseStack poseStack = new PoseStack();
        for (GeoBone bone : model.topLevelBones()) {
            bounds.visit(poseStack, bone, Math.cos(yaw), Math.sin(yaw), Math.cos(pitch), Math.sin(pitch));
        }
        return bounds;
    }

    /**
     * Measures one named bone and its descendants without allowing unrelated siblings in the
     * same Bedrock geometry to influence an item's rotation centre. The tire item renderer uses
     * this because its source asset is a four-wheel vehicle-fit model, not a standalone item.
     */
    public static ModelBounds ofBone(
            BakedGeoModel model,
            String boneName,
            float pitchDegrees,
            float yawDegrees
    ) {
        ModelBounds bounds = new ModelBounds();
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        for (GeoBone topLevelBone : model.topLevelBones()) {
            GeoBone selected = findBone(topLevelBone, boneName);
            if (selected != null) {
                bounds.visit(
                        new PoseStack(),
                        selected,
                        Math.cos(yaw),
                        Math.sin(yaw),
                        Math.cos(pitch),
                        Math.sin(pitch)
                );
                break;
            }
        }
        return bounds;
    }

    private static GeoBone findBone(GeoBone bone, String name) {
        if (name.equals(bone.getName())) {
            return bone;
        }
        for (GeoBone child : bone.getChildBones()) {
            GeoBone found = findBone(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * True when the model produced no geometry, e.g. a Bedrock {@code poly_mesh} bone.
     */
    public boolean isEmpty() {
        return minX > maxX;
    }

    public float centerX() {
        return (minX + maxX) / 2.0F;
    }

    public float centerY() {
        return (minY + maxY) / 2.0F;
    }

    public float centerZ() {
        return (minZ + maxZ) / 2.0F;
    }

    /**
     * Longest edge of the axis-aligned box, used as the reference length for held items.
     */
    public float longestEdge() {
        return Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
    }

    /**
     * Half-width of the model after the inventory tilt, in blocks.
     */
    public float tiltedHalfWidth() {
        return (maxProjectedX - minProjectedX) / 2.0F;
    }

    /**
     * Half-height of the model after the inventory tilt, in blocks.
     */
    public float tiltedHalfHeight() {
        return (maxProjectedY - minProjectedY) / 2.0F;
    }

    private void visit(
            PoseStack poseStack,
            GeoBone bone,
            double yawCos,
            double yawSin,
            double pitchCos,
            double pitchSin
    ) {
        poseStack.pushPose();
        RenderUtil.prepMatrixForBone(poseStack, bone);

        if (!bone.isHidden()) {
            for (GeoCube cube : bone.getCubes()) {
                poseStack.pushPose();
                RenderUtil.translateToPivotPoint(poseStack, cube);
                RenderUtil.rotateMatrixAroundCube(poseStack, cube);
                RenderUtil.translateAwayFromPivotPoint(poseStack, cube);
                include(poseStack.last().pose(), cube, yawCos, yawSin, pitchCos, pitchSin);
                poseStack.popPose();
            }
        }
        if (!bone.isHidingChildren()) {
            for (GeoBone child : bone.getChildBones()) {
                visit(poseStack, child, yawCos, yawSin, pitchCos, pitchSin);
            }
        }
        poseStack.popPose();
    }

    private void include(
            Matrix4f matrix,
            GeoCube cube,
            double yawCos,
            double yawSin,
            double pitchCos,
            double pitchSin
    ) {
        for (GeoQuad quad : cube.quads()) {
            if (quad == null) {
                continue;
            }
            for (GeoVertex vertex : quad.vertices()) {
                var position = vertex.position();
                var transformed = matrix.transform(
                        new Vector4f(position.x(), position.y(), position.z(), 1.0F)
                );
                include(transformed.x(), transformed.y(), transformed.z());
                includeProjected(transformed, yawCos, yawSin, pitchCos, pitchSin);
            }
        }
    }

    /**
     * Projects a vertex the way the inventory pose does: a yaw about Y followed by a pitch
     * about X.  {@code Ry} maps {@code (x, z)} to {@code (x*cos + z*sin, -x*sin + z*cos)},
     * and the tilt then folds that depth into the vertical axis.
     */
    private void includeProjected(
            Vector4f vertex,
            double yawCos,
            double yawSin,
            double pitchCos,
            double pitchSin
    ) {
        double yawedX = vertex.x() * yawCos + vertex.z() * yawSin;
        double yawedZ = -vertex.x() * yawSin + vertex.z() * yawCos;
        double pitchedY = vertex.y() * pitchCos - yawedZ * pitchSin;
        minProjectedX = (float) Math.min(minProjectedX, yawedX);
        maxProjectedX = (float) Math.max(maxProjectedX, yawedX);
        minProjectedY = (float) Math.min(minProjectedY, pitchedY);
        maxProjectedY = (float) Math.max(maxProjectedY, pitchedY);
    }

    private void include(float x, float y, float z) {
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        minZ = Math.min(minZ, z);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
        maxZ = Math.max(maxZ, z);
    }
}
