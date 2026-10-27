package com.flechazo.polaris_vehicle.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.*;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class VehicleTireMeshes {
    private static final Map<ResourceLocation, BakedGeoModel> MODELS = new HashMap<>();
    private static Object reloadToken;

    private VehicleTireMeshes() {
    }

    public static BakedGeoModel get(ResourceLocation resource) {
        // Gecko replaces this map on resource reload. Never retain vertices
        // from the previous resource pack or share mutable wheel bones.
        var token = GeckoLibCache.getBakedModels();
        if (reloadToken != token) {
            MODELS.clear();
            reloadToken = token;
        }
        return MODELS.computeIfAbsent(resource, location -> {
            try (var reader = Minecraft.getInstance().getResourceManager().getResourceOrThrow(location).openAsReader()) {
                return separateCoplanarFaces(bake(JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (IOException error) {
                throw new IllegalStateException("Unable to read vehicle tire mesh " + location, error);
            }
        });
    }

    /**
     * Gecko4's PolyMesh parser expects flat vectors and its factory ignores faces.
     * Read standard nested Bedrock vectors here and pass actual GeoQuads to the
     * existing Gecko renderer, including AR's normal vertex submission path.
     */
    public static BakedGeoModel bake(JsonObject document) {
        var skeleton = document.deepCopy();
        var bones = skeleton.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
        for (var element : bones) element.getAsJsonObject().remove("poly_mesh");
        var parsed = KeyFramesAdapter.GEO_GSON.fromJson(skeleton, Model.class);
        var model = BakedModelFactory.DEFAULT_FACTORY.constructGeoModel(GeometryTree.fromModel(parsed));
        var authored = document.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
        for (var element : authored) {
            var bone = element.getAsJsonObject();
            if (!bone.has("poly_mesh")) continue;
            var target = model.getBone(bone.get("name").getAsString()).orElseThrow();
            var mesh = bone.getAsJsonObject("poly_mesh");
            var positions = mesh.getAsJsonArray("positions");
            var normals = mesh.getAsJsonArray("normals");
            var uvs = mesh.getAsJsonArray("uvs");
            var polygons = mesh.getAsJsonArray("polys");
            var directions = bone.getAsJsonArray("polaris_vehicle_face_directions");
            GeoQuad[] quads = new GeoQuad[polygons.size()];
            for (int face = 0; face < polygons.size(); face++) {
                var polygon = polygons.get(face).getAsJsonArray();
                if (polygon.size() != 4) throw new IllegalArgumentException("Tire mesh requires four-corner faces");
                GeoVertex[] vertices = new GeoVertex[4];
                Vector3f normal = null;
                for (int corner = 0; corner < 4; corner++) {
                    // Reversing the X-reflected Bedrock winding restores the
                    // source Gecko face order and its exact vertex/UV pairing.
                    var indices = polygon.get(3 - corner).getAsJsonArray();
                    var position = vector(positions.get(indices.get(0).getAsInt()).getAsJsonArray());
                    var uv = uvs.get(indices.get(2).getAsInt()).getAsJsonArray();
                    vertices[corner] = new GeoVertex(position.mul(-1F / 16, 1F / 16, 1F / 16),
                            uv.get(0).getAsFloat(), 1 - uv.get(1).getAsFloat());
                    normal = vector(normals.get(indices.get(1).getAsInt()).getAsJsonArray()).mul(-1, 1, 1).normalize();
                }
                Direction direction = Direction.byName(directions.get(face).getAsString());
                if (direction == null) throw new IllegalArgumentException("Unknown tire face direction");
                quads[face] = new GeoQuad(vertices, normal, direction);
            }
            // This is a volume mesh. A zero-size placeholder makes Gecko's
            // fixInvertedFlatCube flip negative normals as if every face were flat.
            Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
            Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
            for (var quad : quads)
                for (var vertex : quad.vertices()) {
                    min.min(vertex.position());
                    max.max(vertex.position());
                }
            var extent = max.sub(min).mul(16);
            target.getCubes().add(new GeoCube(quads, Vec3.ZERO, Vec3.ZERO,
                    new Vec3(extent.x, extent.y, extent.z), 0, false));
        }
        return model;
    }

    public static BakedGeoModel separateCoplanarFaces(BakedGeoModel model) {
        model.topLevelBones().forEach(VehicleTireMeshes::separateCoplanarFaces);
        return model;
    }

    private static void separateCoplanarFaces(GeoBone bone) {
        var cubes = bone.getCubes();
        for (int i = 0; i < cubes.size(); i++) {
            GeoCube cube = cubes.get(i);
            GeoQuad[] separated = CoplanarFaceSeparation.separateClosedCuboids(cube.quads());
            if (separated != cube.quads()) cubes.set(i, new GeoCube(separated, cube.pivot(), cube.rotation(),
                    cube.size(), cube.inflate(), cube.mirror()));
        }
        bone.getChildBones().forEach(VehicleTireMeshes::separateCoplanarFaces);
    }

    private static Vector3f vector(JsonArray value) {
        return new Vector3f(value.get(0).getAsFloat(), value.get(1).getAsFloat(), value.get(2).getAsFloat());
    }
}
