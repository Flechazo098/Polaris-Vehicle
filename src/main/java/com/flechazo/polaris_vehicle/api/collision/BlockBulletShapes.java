package com.flechazo.polaris_vehicle.api.collision;

import com.flechazo.hkt.business.core.Pathway;
import com.flechazo.polaris_vehicle.vehicle.collision.OrientedBoundingBox;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.math.Matrix3f;
import com.jme3.math.Vector3f;

/** Called only by the physics world's shape cache, after native Bullet initialization. */
public final class BlockBulletShapes {
    private BlockBulletShapes() {}
    public static CompoundCollisionShape create(BlockCollisionGeometry geometry) {
        var compound = new CompoundCollisionShape(geometry.boxes().size());
        Pathway.listPath(geometry.boxes().toList()).peek(box -> add(compound, box));
        compound.setMargin(0.001F);
        return compound;
    }
    private static void add(CompoundCollisionShape compound, OrientedBoundingBox box) {
        var half = box.halfExtents(); var center = box.center();
        var x = box.axisX(); var y = box.axisY(); var z = box.axisZ();
        var child = new BoxCollisionShape((float) half.x, (float) half.y, (float) half.z);
        child.setMargin((float) Math.min(0.001, Math.min(half.x, Math.min(half.y, half.z)) * 0.1));
        compound.addChildShape(child, new Vector3f((float) center.x - 0.5F, (float) center.y - 0.5F, (float) center.z - 0.5F),
                new Matrix3f((float) x.x, (float) y.x, (float) z.x, (float) x.y, (float) y.y, (float) z.y, (float) x.z, (float) y.z, (float) z.z));
    }
}
