package mods.mewkooo.bedrockmodel.client.model.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mods.mewkooo.bedrockmodel.api.model.BakerOptions;
import mods.mewkooo.bedrockmodel.core.json.GsonUtil;
import mods.mewkooo.bedrockmodel.core.json.pojo.BedrockModelPOJO;
import mods.mewkooo.bedrockmodel.core.model.baked.BakedBedrockModel;
import mods.mewkooo.bedrockmodel.core.model.runtime.BakedModelInstance;
import mods.mewkooo.bedrockmodel.core.model.runtime.BoneState;
import mods.mewkooo.bedrockmodel.core.model.runtime.BoneTreeInstance;
import mods.mewkooo.bedrockmodel.core.model.runtime.TreeModelInstance;
import mods.mewkooo.bedrockmodel.core.model.tree.CubeBox;
import mods.mewkooo.bedrockmodel.core.model.tree.ICube;
import mods.mewkooo.bedrockmodel.core.model.tree.TreeBedrockModel;
import net.neoforged.fml.ModList;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoneNormalRenderingTest {
    private static final float EPSILON = 2.0E-5f;
    private static final String MODEL = """
            {
              "format_version": "1.12.0",
              "minecraft:geometry": [{
                "description": {"identifier":"geometry.normal_test", "texture_width":32, "texture_height":32},
                "bones": [
                  {"name":"root", "pivot":[0,0,0]},
                  {"name":"child", "parent":"root", "pivot":[0,0,0],
                   "cubes":[{"origin":[-4,-3,-2], "size":[8,6,4], "uv":[0,0],
                             "pivot":[0,0,0], "rotation":[0,0,45]}]}
                ]
              }]
            }
            """;

    @BeforeAll
    static void initializeEmptyModListForOptionalRendererDetection() throws ReflectiveOperationException {
        if (ModList.get() == null) {
            // Plain JUnit does not discover or load mods. ModList.of leaves its container index unset.
            ModList modList = ModList.of(List.of(), List.of());
            Method setLoadedMods = ModList.class.getDeclaredMethod("setLoadedMods", List.class);
            setLoadedMods.setAccessible(true);
            setLoadedMods.invoke(modList, List.of());
        }
    }

    @Test
    void rotatedCubeEmitsPerpendicularUnitNormalsUnderNonUniformBoneScale() {
        TreeBedrockModel model = treeModel();
        TreeModelInstance instance = model.createInstance();
        BoneState bone = instance.getBone("child");
        assertNotNull(bone);
        bone.xScale = 2;
        ICube[] cubes = model.bone(bone.index()).cubes();
        assertInstanceOf(CubeBox.class, cubes[0]);
        Matrix4f pose = new Matrix4f();
        Matrix3f normal = new Matrix3f();
        bone.applyLocalTransform(pose, normal);
        RecordingConsumer out = new RecordingConsumer();

        TreeGeometryWriter.writeCubesFallback(cubes, out, pose, normal, 0, 0, 1, 1, 1, 1, true);

        assertPerpendicularUnitNormals(out.vertices);
        assertMatchesGlobalTransform(localVertices(model), out.vertices, instance, bone.index());
    }

    @Test
    void treeHierarchyAndSingleBoneEmitTheSameCorrectNormalsIncludingNegativeScale() {
        TreeBedrockModel model = treeModel();
        TreeModelInstance instance = model.createInstance();
        configureHierarchy(instance);
        int childIndex = model.getIndex("child");
        RecordingConsumer wholeTree = new RecordingConsumer();
        RecordingConsumer singleBone = new RecordingConsumer();

        TreeModelRenderer.render(instance, new PoseStack(), wholeTree, 0, 0, 1, 1, 1, 1, true, true);
        TreeModelRenderer.renderSingleBone(instance, childIndex, new PoseStack(), singleBone,
                0, 0, 1, 1, 1, 1, true, true);

        assertPerpendicularUnitNormals(wholeTree.vertices);
        assertPerpendicularUnitNormals(singleBone.vertices);
        assertVerticesEqual(wholeTree.vertices, singleBone.vertices);
        assertMatchesGlobalTransform(localVertices(model), wholeTree.vertices, instance, childIndex);
    }

    @Test
    void bakedRuntimeHierarchyAndSingleBonePreservePerpendicularUnitNormals() {
        BakedBedrockModel model = BakedBedrockModel.bake(pojo(), BakerOptions.ofAnimatedBones(Set.of("root", "child")));
        BakedModelInstance instance = model.createInstance();
        configureHierarchy(instance);
        int childIndex = model.getIndex("child");
        assertNotNull(instance.getBone("root"));
        assertNotNull(instance.getBone("child"));
        BakedGeometryChunkRenderer.installBackend(null);
        RecordingConsumer wholeModel = new RecordingConsumer();
        RecordingConsumer singleBone = new RecordingConsumer();

        BakedModelRenderer.render(instance, new PoseStack(), wholeModel, 0, 0, 1, 1, 1, 1, true, true);
        BakedModelRenderer.renderSingleBone(instance, childIndex, new PoseStack(), singleBone,
                0, 0, 1, 1, 1, 1, true, true);

        assertPerpendicularUnitNormals(wholeModel.vertices);
        assertPerpendicularUnitNormals(singleBone.vertices);
        assertVerticesEqual(wholeModel.vertices, singleBone.vertices);
        assertMatchesGlobalTransform(localVertices(treeModel()), wholeModel.vertices, instance, childIndex);
    }

    private static BedrockModelPOJO pojo() {
        return GsonUtil.CLIENT_GSON.fromJson(MODEL, BedrockModelPOJO.class);
    }

    private static TreeBedrockModel treeModel() {
        return TreeBedrockModel.bake(pojo());
    }

    private static List<Vertex> localVertices(TreeBedrockModel model) {
        RecordingConsumer out = new RecordingConsumer();
        TreeGeometryWriter.writeCubesFallback(model.bone(model.getIndex("child")).cubes(), out,
                new Matrix4f(), new Matrix3f(), 0, 0, 1, 1, 1, 1, true);
        return out.vertices;
    }

    private static void configureHierarchy(BoneTreeInstance instance) {
        BoneState root = instance.getBone("root");
        assertNotNull(root);
        root.rotation.identity().rotateZYX(0.25f, -0.4f, 0.15f);
        root.x = 3;
        root.y = -2;
        root.z = 1;
        root.xScale = 2;
        root.yScale = 0.75f;
        root.zScale = 1.5f;
        BoneState child = instance.getBone("child");
        assertNotNull(child);
        child.rotation.identity().rotateZYX(-0.45f, 0.3f, 0.6f);
        child.x = -1;
        child.y = 4;
        child.z = 2;
        child.xScale = -0.65f;
        child.yScale = 1.4f;
        child.zScale = 0.9f;
    }

    private static void assertPerpendicularUnitNormals(List<Vertex> vertices) {
        assertEquals(24, vertices.size(), "The fixture must emit all six cube faces");
        for (int face = 0; face < 6; face++) {
            int start = face * 4;
            Vector3f origin = vertices.get(start).position();
            Vector3f firstEdge = vertices.get(start + 1).position().sub(origin);
            Vector3f secondEdge = vertices.get(start + 3).position().sub(origin);
            assertTrue(firstEdge.lengthSquared() > 1.0E-8f, "First face edge must be nondegenerate");
            assertTrue(secondEdge.lengthSquared() > 1.0E-8f, "Second face edge must be nondegenerate");
            firstEdge.normalize();
            secondEdge.normalize();
            for (int corner = 0; corner < 4; corner++) {
                Vector3f normal = vertices.get(start + corner).normal();
                String label = "face " + face + ", corner " + corner;
                assertEquals(1, normal.length(), EPSILON, "Unit normal for " + label);
                assertEquals(0, firstEdge.dot(normal), EPSILON, "First edge perpendicular to normal for " + label);
                assertEquals(0, secondEdge.dot(normal), EPSILON, "Second edge perpendicular to normal for " + label);
            }
        }
    }

    private static void assertMatchesGlobalTransform(List<Vertex> local, List<Vertex> emitted,
                                                     BoneTreeInstance instance, int boneIndex) {
        assertEquals(local.size(), emitted.size());
        Matrix4f global = instance.getGlobalTransform(boneIndex);
        Matrix3f globalNormal = instance.getGlobalNormal(boneIndex);
        for (int i = 0; i < local.size(); i++) {
            assertVectorEquals(local.get(i).position().mulPosition(global), emitted.get(i).position(), "Global vertex " + i);
            assertVectorEquals(local.get(i).normal().mul(globalNormal).normalize(), emitted.get(i).normal(), "Global normal " + i);
        }
    }

    private static void assertVerticesEqual(List<Vertex> expected, List<Vertex> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertVectorEquals(expected.get(i).position(), actual.get(i).position(), "Vertex " + i);
            assertVectorEquals(expected.get(i).normal(), actual.get(i).normal(), "Normal " + i);
        }
    }

    private static void assertVectorEquals(Vector3f expected, Vector3f actual, String label) {
        assertEquals(expected.x, actual.x, EPSILON, label + ".x");
        assertEquals(expected.y, actual.y, EPSILON, label + ".y");
        assertEquals(expected.z, actual.z, EPSILON, label + ".z");
    }

    private record Vertex(float x, float y, float z, float nx, float ny, float nz) {
        Vector3f position() { return new Vector3f(x, y, z); }
        Vector3f normal() { return new Vector3f(nx, ny, nz); }
    }

    private static final class RecordingConsumer implements VertexConsumer {
        private final List<Vertex> vertices = new ArrayList<>();

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light,
                              float nx, float ny, float nz) {
            vertices.add(new Vertex(x, y, z, nx, ny, nz));
        }

        @Override public VertexConsumer addVertex(float x, float y, float z) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setUv(float u, float v) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setUv1(int u, int v) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setUv2(int u, int v) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setNormal(float x, float y, float z) { throw new UnsupportedOperationException(); }
    }
}
