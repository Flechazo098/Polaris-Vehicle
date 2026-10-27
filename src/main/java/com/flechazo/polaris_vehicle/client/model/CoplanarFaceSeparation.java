package com.flechazo.polaris_vehicle.client.model;

import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * Gives overlapping coplanar faces a fixed depth order.
 *
 * <p>Wheels built in Blockbench from several boxes rotated about the axle have
 * side faces that lie in one plane and overlap, each showing a different part
 * of the texture. Vanilla draws them with an equal-depth test in submission
 * order, so the last one wins consistently. Shader packs jitter the projection
 * every frame (TAA) and compare shadow depths, so which face is in front flips
 * from frame to frame and the hub flickers. Each later overlapping face is
 * moved along its normal until it lies at least {@link #LAYER_OFFSET} in front
 * of every earlier face it covers. That keeps the vanilla result (later on
 * top) while making it independent of depth precision.</p>
 */
public final class CoplanarFaceSeparation {

    public static final float LAYER_OFFSET = 0.001F;
    private static final float SAME_DIRECTION = 0.9995F;
    private static final float SAME_PLANE = 1.0E-4F;
    private static final float MINIMUM_OVERLAP_RATIO = 1.0E-3F;

    private CoplanarFaceSeparation() {
    }

    /**
     * Separate the wheel importer's consecutive six-face cuboids, extending
     * each adjacent face to the displaced plane. Moving just the overlapping
     * face opens the cuboid at its perimeter and exposes a slit at grazing
     * angles, even without shaders. Intersecting the three incident planes
     * keeps every shared corner identical without adding faces or inflating
     * the whole tire. UVs remain attached to their original face corners.
     */
    public static GeoQuad[] separateClosedCuboids(GeoQuad[] quads) {
        if (quads.length % 6 != 0) throw new IllegalArgumentException("Expected complete six-face tire cuboids");
        var separated = separate(quads);
        if (separated == quads) return quads;
        var result = separated.clone();
        for (int start = 0; start < quads.length; start += 6) {
            boolean moved = false;
            for (int face = start; face < start + 6; face++) moved |= separated[face] != quads[face];
            if (!moved) continue;
            // Reuse each solved corner across all its incident faces. This
            // also avoids independent float rounding at their shared edge.
            var corners = new HashMap<Vector3f, Vector3f>();
            for (int face = start; face < start + 6; face++) {
                var source = quads[face].vertices();
                var vertices = new GeoVertex[source.length];
                for (int corner = 0; corner < source.length; corner++) {
                    Vector3f point = source[corner].position();
                    Vector3f closed = corners.get(point);
                    if (closed == null) {
                        closed = intersectCorner(point, quads, separated, start);
                        if (closed != null) corners.put(point, closed);
                    }
                    // Zero-volume decoration has no three-plane corner to
                    // close; retain its already separated flat face.
                    vertices[corner] = new GeoVertex(closed == null
                            ? new Vector3f(separated[face].vertices()[corner].position()) : new Vector3f(closed),
                            source[corner].texU(), source[corner].texV());
                }
                result[face] = new GeoQuad(vertices, quads[face].normal(), quads[face].direction());
            }
        }
        return result;
    }

    private static Vector3f intersectCorner(Vector3f point, GeoQuad[] original, GeoQuad[] separated, int start) {
        var normals = new ArrayList<Vector3f>(3);
        var shifts = new ArrayList<Float>(3);
        for (int face = start; face < start + 6; face++) {
            boolean incident = false;
            for (var vertex : original[face].vertices()) incident |= vertex.position().equals(point);
            if (!incident) continue;
            var normal = planeNormal(original[face]);
            if (normal == null) continue;
            normals.add(normal);
            shifts.add(normal.dot(new Vector3f(separated[face].vertices()[0].position())
                    .sub(original[face].vertices()[0].position())));
        }
        for (int a = 0; a < normals.size(); a++)
            for (int b = a + 1; b < normals.size(); b++)
                for (int c = b + 1; c < normals.size(); c++) {
                    var bc = new Vector3f(normals.get(b)).cross(normals.get(c));
                    float determinant = normals.get(a).dot(bc);
                    if (Math.abs(determinant) < 1E-6F) continue;
                    var displacement = bc.mul(shifts.get(a))
                            .add(new Vector3f(normals.get(c)).cross(normals.get(a)).mul(shifts.get(b)))
                            .add(new Vector3f(normals.get(a)).cross(normals.get(b)).mul(shifts.get(c)))
                            .div(determinant);
                    return new Vector3f(point).add(displacement);
                }
        return null;
    }

    /**
     * @return the same faces, with later overlapping coplanar ones raised in front of earlier ones
     */
    public static GeoQuad[] separate(GeoQuad[] quads) {
        int count = quads.length;
        Vector3f[] normals = new Vector3f[count];
        float[] planes = new float[count];
        for (int i = 0; i < count; i++) {
            normals[i] = planeNormal(quads[i]);
            if (normals[i] != null) planes[i] = normals[i].dot(quads[i].vertices()[0].position());
        }
        // Final plane of each face: at least one layer in front of every earlier
        // face it overlaps. Measured from those faces' final planes, so faces
        // that were only nearly coplanar still end up a full layer apart.
        float[] finalPlanes = planes.clone();
        boolean changed = false;
        for (int i = 0; i < count; i++) {
            if (normals[i] == null) continue;
            for (int j = 0; j < i; j++) {
                if (normals[j] == null || finalPlanes[j] + LAYER_OFFSET <= finalPlanes[i]
                        || normals[i].dot(normals[j]) < SAME_DIRECTION
                        || Math.abs(planes[i] - planes[j]) > SAME_PLANE
                        || !overlaps(quads[i], quads[j], normals[i])) continue;
                finalPlanes[i] = finalPlanes[j] + LAYER_OFFSET;
                changed = true;
            }
        }
        if (!changed) return quads;
        GeoQuad[] result = new GeoQuad[count];
        for (int i = 0; i < count; i++) {
            if (finalPlanes[i] == planes[i]) {
                result[i] = quads[i];
                continue;
            }
            Vector3f offset = new Vector3f(normals[i]).mul(finalPlanes[i] - planes[i]);
            GeoVertex[] source = quads[i].vertices();
            GeoVertex[] moved = new GeoVertex[source.length];
            for (int corner = 0; corner < source.length; corner++) {
                moved[corner] = new GeoVertex(new Vector3f(source[corner].position()).add(offset),
                        source[corner].texU(), source[corner].texV());
            }
            result[i] = new GeoQuad(moved, quads[i].normal(), quads[i].direction());
        }
        return result;
    }

    static Vector3f planeNormal(GeoQuad quad) {
        GeoVertex[] vertices = quad.vertices();
        Vector3f normal = new Vector3f();
        for (int i = 0; i < vertices.length; i++) {
            Vector3f a = vertices[i].position();
            Vector3f b = vertices[(i + 1) % vertices.length].position();
            normal.add(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
        }
        if (normal.lengthSquared() < 1.0E-14F) return null;
        normal.normalize();
        return quad.normal() != null && normal.dot(quad.normal()) < 0 ? normal.negate() : normal;
    }

    static boolean overlaps(GeoQuad a, GeoQuad b, Vector3f normal) {
        Vector3f u = new Vector3f(Math.abs(normal.x) < 0.9F ? 1 : 0, Math.abs(normal.x) < 0.9F ? 0 : 1, 0)
                .cross(normal).normalize();
        Vector3f v = new Vector3f(normal).cross(u);
        List<float[]> first = project(a, u, v);
        List<float[]> second = project(b, u, v);
        float smaller = Math.min(Math.abs(area(first)), Math.abs(area(second)));
        if (smaller <= 0) return false;
        return Math.abs(area(clip(first, second))) > smaller * MINIMUM_OVERLAP_RATIO;
    }

    private static List<float[]> project(GeoQuad quad, Vector3f u, Vector3f v) {
        List<float[]> points = new ArrayList<>();
        for (GeoVertex vertex : quad.vertices()) {
            points.add(new float[]{vertex.position().dot(u), vertex.position().dot(v)});
        }
        if (area(points) < 0) Collections.reverse(points);
        return points;
    }

    private static float area(List<float[]> polygon) {
        float sum = 0;
        for (int i = 0; i < polygon.size(); i++) {
            float[] a = polygon.get(i);
            float[] b = polygon.get((i + 1) % polygon.size());
            sum += a[0] * b[1] - b[0] * a[1];
        }
        return sum * 0.5F;
    }

    /**
     * Sutherland-Hodgman clip of a convex polygon by a counter-clockwise convex clipper.
     */
    private static List<float[]> clip(List<float[]> subject, List<float[]> clipper) {
        List<float[]> output = subject;
        for (int i = 0; i < clipper.size() && !output.isEmpty(); i++) {
            float[] a = clipper.get(i);
            float[] b = clipper.get((i + 1) % clipper.size());
            List<float[]> input = output;
            output = new ArrayList<>();
            float[] previous = input.getLast();
            for (float[] current : input) {
                boolean currentInside = side(a, b, current) >= -1.0E-7F;
                boolean previousInside = side(a, b, previous) >= -1.0E-7F;
                if (currentInside) {
                    if (!previousInside) output.add(intersection(previous, current, a, b));
                    output.add(current);
                } else if (previousInside) {
                    output.add(intersection(previous, current, a, b));
                }
                previous = current;
            }
        }
        return output.size() < 3 ? List.of() : output;
    }

    private static float side(float[] a, float[] b, float[] p) {
        return (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]);
    }

    private static float[] intersection(float[] p, float[] q, float[] a, float[] b) {
        float denominator = (p[0] - q[0]) * (a[1] - b[1]) - (p[1] - q[1]) * (a[0] - b[0]);
        if (Math.abs(denominator) < 1.0E-12F) return q;
        float t = ((p[0] - a[0]) * (a[1] - b[1]) - (p[1] - a[1]) * (a[0] - b[0])) / denominator;
        return new float[]{p[0] + t * (q[0] - p[0]), p[1] + t * (q[1] - p[1])};
    }
}
