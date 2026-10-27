package com.flechazo.polaris_vehicle.model;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.*;
import software.bernie.geckolib.loading.json.raw.Cube;
import software.bernie.geckolib.loading.json.raw.ModelProperties;
import software.bernie.geckolib.loading.json.raw.PolyMesh;
import software.bernie.geckolib.loading.json.raw.PolysUnion;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.BoneStructure;
import software.bernie.geckolib.loading.object.GeometryTree;

import java.util.ArrayList;
import java.util.List;

public final class PolyMeshBakedModelFactory implements BakedModelFactory {
    private static final BakedModelFactory DELEGATE = BakedModelFactory.DEFAULT_FACTORY;
    private static final float UNIT = 16.0F;
    private static final Vec3 MESH_FACE_SIZE = new Vec3(1.0, 1.0, 1.0);

    @Override
    public BakedGeoModel constructGeoModel(GeometryTree geometryTree) {
        List<GeoBone> bones = new ArrayList<>();
        for (BoneStructure structure : geometryTree.topLevelBones().values()) {
            bones.add(constructBone(structure, geometryTree.properties(), null));
        }
        return new BakedGeoModel(bones, geometryTree.properties());
    }

    @Override
    public GeoBone constructBone(BoneStructure boneStructure, ModelProperties properties, GeoBone parent) {
        GeoBone bone = DELEGATE.constructBone(boneStructure, properties, parent);
        appendMeshes(boneStructure, bone, properties);
        return bone;
    }

    @Override
    public GeoCube constructCube(Cube cube, ModelProperties properties, GeoBone bone) {
        return DELEGATE.constructCube(cube, properties, bone);
    }

    private static void appendMeshes(BoneStructure structure, GeoBone bone, ModelProperties properties) {
        PolyMesh mesh = structure.self().polyMesh();
        if (mesh != null) {
            List<GeoCube> faces = meshFaces(mesh, properties);

            bone.getCubes().addAll(faces);
        }

        List<BoneStructure> rawChildren = List.copyOf(structure.children().values());
        List<GeoBone> bakedChildren = bone.getChildBones();
        int shared = Math.min(rawChildren.size(), bakedChildren.size());
        for (int i = 0; i < shared; i++) {
            appendMeshes(rawChildren.get(i), bakedChildren.get(i), properties);
        }
    }

    private static List<GeoCube> meshFaces(PolyMesh mesh, ModelProperties properties) {
        PolysUnion polys = mesh.polysUnion();
        if (polys == null || polys.union() == null) {
            return List.of();
        }
        boolean normalizedUvs = Boolean.TRUE.equals(mesh.normalizedUVs());
        MeshUvs uvs = new MeshUvs(
                mesh.uvs(),
                normalizedUvs ? 1.0F : (float) (1.0 / properties.textureWidth()),
                normalizedUvs ? 1.0F : (float) (1.0 / properties.textureHeight())
        );
        double[] positions = mesh.positions();
        double[] normals = mesh.normals();

        List<GeoCube> faces = new ArrayList<>(polys.union().length);
        for (double[][] polygon : polys.union()) {
            GeoQuad quad = buildQuad(polygon, positions, normals, uvs);
            if (quad != null) {
                faces.add(new GeoCube(
                        new GeoQuad[]{quad, null, null, null, null, null},
                        Vec3.ZERO,
                        Vec3.ZERO,
                        MESH_FACE_SIZE,
                        0.0,
                        false
                ));
            }
        }
        return faces;
    }

    private static GeoQuad buildQuad(
            double[][] polygon,
            double[] positions,
            double[] normals,
            MeshUvs uvs
    ) {
        if (polygon == null || polygon.length < 3) {
            return null;
        }
        GeoVertex[] vertices = new GeoVertex[polygon.length];
        Vector3f normal = new Vector3f();

        for (int i = 0; i < polygon.length; i++) {
            double[] reference = polygon[i];
            if (reference == null || reference.length < 3) {
                return null;
            }
            int positionIndex = (int) reference[0];
            int normalIndex = (int) reference[1];
            int uvIndex = (int) reference[2];
            if (!hasTriple(positions, positionIndex) || !uvs.has(uvIndex)) {
                return null;
            }
            vertices[i] = new GeoVertex(
                    new Vector3f(
                            -(float) positions[positionIndex * 3] / UNIT,
                            (float) positions[positionIndex * 3 + 1] / UNIT,
                            (float) positions[positionIndex * 3 + 2] / UNIT
                    ),
                    uvs.u(uvIndex),
                    uvs.v(uvIndex)
            );
            if (hasTriple(normals, normalIndex)) {
                normal.add(
                        -(float) normals[normalIndex * 3],
                        (float) normals[normalIndex * 3 + 1],
                        (float) normals[normalIndex * 3 + 2]
                );
            }
        }

        if (normal.lengthSquared() == 0.0F) {
            return null;
        }
        normal.normalize();
        return new GeoQuad(
                vertices,
                normal,
                Direction.getNearest(normal.x, normal.y, normal.z)
        );
    }

    private static boolean hasTriple(double[] values, int index) {
        return values != null && index >= 0 && (index + 1) * 3 <= values.length;
    }

    private record MeshUvs(double[] values, float uScale, float vScale) {
        boolean has(int index) {
            return values != null && index >= 0 && (index + 1) * 2 <= values.length;
        }

        float u(int index) {
            return (float) values[index * 2] * uScale;
        }

        float v(int index) {
            return 1.0F - (float) values[index * 2 + 1] * vScale;
        }
    }
}
