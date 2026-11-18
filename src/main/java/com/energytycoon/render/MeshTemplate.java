package com.energytycoon.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.FloatArray;
import com.badlogic.gdx.utils.IntArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bir OBJ modelinin ucgenlerini (konum, normal, malzeme adi) bellekte tutar; arazi parcalarina
 * "damga" olarak pisirilmek icin. Boylece binlerce agac/cali/kaya tek bir mesh'te cizilir.
 */
public final class MeshTemplate {
    /** Ucgen basina 3 kose x (x,y,z,nx,ny,nz). */
    public final float[] data;
    /** Ucgen basina malzeme indeksi (materials listesine). */
    public final int[] triMaterial;
    public final List<String> materials = new ArrayList<>();
    public final float minY, maxY, radius;

    private static final Map<String, MeshTemplate> CACHE = new HashMap<>();

    public static synchronized MeshTemplate get(String path) {
        return CACHE.computeIfAbsent(path, MeshTemplate::new);
    }

    private MeshTemplate(String path) {
        Model m = new ObjLoader().loadModel(Gdx.files.classpath(path));
        FloatArray out = new FloatArray();
        IntArray mats = new IntArray();
        for (Node n : m.nodes) collect(n, out, mats);
        data = out.toArray();
        triMaterial = mats.toArray();
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE, r = 0;
        for (int i = 0; i < data.length; i += 6) {
            lo = Math.min(lo, data[i + 1]); hi = Math.max(hi, data[i + 1]);
            r = Math.max(r, (float) Math.hypot(data[i], data[i + 2]));
        }
        minY = lo; maxY = hi; radius = r;
        m.dispose();
    }

    private void collect(Node n, FloatArray out, IntArray mats) {
        Matrix4 tf = n.globalTransform;
        Vector3 p = new Vector3(), nm = new Vector3();
        for (NodePart np : n.parts) {
            String mat = np.material.id;
            int mi = materials.indexOf(mat);
            if (mi < 0) { materials.add(mat); mi = materials.size() - 1; }
            Mesh mesh = np.meshPart.mesh;
            int stride = mesh.getVertexSize() / 4;
            VertexAttribute pa = mesh.getVertexAttribute(Usage.Position), na = mesh.getVertexAttribute(Usage.Normal);
            float[] verts = new float[mesh.getNumVertices() * stride];
            mesh.getVertices(verts);
            int off = np.meshPart.offset, size = np.meshPart.size;
            short[] ind = null;
            if (mesh.getNumIndices() > 0) { ind = new short[mesh.getNumIndices()]; mesh.getIndices(ind); }
            for (int k = 0; k + 2 < size; k += 3) {
                for (int c = 0; c < 3; c++) {
                    int vi = ind != null ? (ind[off + k + c] & 0xFFFF) : off + k + c;
                    int b = vi * stride;
                    p.set(verts[b + pa.offset / 4], verts[b + pa.offset / 4 + 1], verts[b + pa.offset / 4 + 2]).mul(tf);
                    if (na != null) nm.set(verts[b + na.offset / 4], verts[b + na.offset / 4 + 1], verts[b + na.offset / 4 + 2]).rot(tf).nor();
                    else nm.set(0, 1, 0);
                    out.addAll(p.x, p.y, p.z, nm.x, nm.y, nm.z);
                }
                mats.add(mi);
            }
        }
        for (Node c : n.getChildren()) collect(c, out, mats);
    }

    public int triangleCount() { return triMaterial.length; }
}
