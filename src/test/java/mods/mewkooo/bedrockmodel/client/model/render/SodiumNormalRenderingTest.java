package mods.mewkooo.bedrockmodel.client.model.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import mods.mewkooo.bedrockmodel.api.model.BakerOptions;
import mods.mewkooo.bedrockmodel.client.compat.sodium.SodiumBakedChunkWriter;
import mods.mewkooo.bedrockmodel.client.compat.sodium.SodiumTreeGeometryWriter;
import mods.mewkooo.bedrockmodel.core.json.GsonUtil;
import mods.mewkooo.bedrockmodel.core.json.pojo.BedrockModelPOJO;
import mods.mewkooo.bedrockmodel.core.model.baked.BakedBedrockModel;
import mods.mewkooo.bedrockmodel.core.model.baked.BakedGeometryChunk;
import mods.mewkooo.bedrockmodel.core.model.baked.BakedQuadData;
import mods.mewkooo.bedrockmodel.core.model.runtime.BoneState;
import mods.mewkooo.bedrockmodel.core.model.tree.TreeBedrockModel;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.common.EntityVertex;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SodiumNormalRenderingTest {
    private static final float NORMAL_QUANTIZATION_ERROR = 1.0f / 127.0f + 1.0e-6f;
    private static final String ROTATED_CUBE = """
            {
              "format_version": "1.12.0",
              "minecraft:geometry": [{
                "description": {"identifier":"geometry.sodium_normals", "texture_width":16, "texture_height":16},
                "bones": [{
                  "name":"root", "pivot":[0,0,0],
                  "cubes":[{"origin":[0,0,0], "size":[4,6,8], "uv":[0,0],
                            "pivot":[0,0,0], "rotation":[0,0,45]}]
                }]
              }]
            }
            """;

    @Test
    void treePackedVerticesMatchFallbackAfterNonUniformBoneScale() {
        TreeBedrockModel model = TreeBedrockModel.bake(modelPojo());
        int rootIndex = model.getIndex("root");
        BoneState bone = model.createInstance().getBone(rootIndex);
        assertNotNull(bone);
        bone.xScale = 2.0f;
        bone.rotation.rotateXYZ(0.2f, -0.3f, 0.4f);
        Matrix4f pose = new Matrix4f();
        Matrix3f normal = new Matrix3f();
        bone.applyLocalTransform(pose, normal);

        RecordingConsumer fallback = new RecordingConsumer();
        TreeGeometryWriter.writeCubesFallback(model.bone(rootIndex).cubes(), fallback, pose, normal,
                0, 0, 1, 1, 1, 1, true);
        RecordingConsumer packed = new RecordingConsumer();
        assertTrue(new SodiumTreeGeometryWriter().writeCubes(model.bone(rootIndex).cubes(), packed,
                0, 0, 1, 1, 1, 1, pose, normal, true));

        assertEquals(24, fallback.vertices.size());
        assertEquals(fallback.vertices.size(), packed.vertices.size());
        assertTrue(packed.pushCount > 0, "The Sodium native buffer path must be exercised");
        for (int i = 0; i < fallback.vertices.size(); i++) {
            assertVectorEquals(fallback.vertices.get(i).position, packed.vertices.get(i).position, 1.0e-6f);
            assertVectorEquals(fallback.vertices.get(i).normal, packed.vertices.get(i).normal, NORMAL_QUANTIZATION_ERROR);
        }
        assertPerpendicularFaces(fallback.vertices, 1.0e-5f);
        assertPerpendicularFaces(packed.vertices, 0.025f);
    }

    @Test
    void bakedPackedNormalsAreNormalizedBeforeByteEncodingForSmallScales() {
        BakedBedrockModel model = BakedBedrockModel.bake(modelPojo(), BakerOptions.ofAnimatedBones(Set.of("root")));
        int rootIndex = model.getIndex("root");
        BoneState bone = model.createInstance().getBone(rootIndex);
        assertNotNull(bone);
        bone.xScale = 0.25f;
        bone.yScale = 0.5f;
        bone.zScale = 0.75f;
        bone.rotation.rotateXYZ(0.2f, -0.3f, 0.4f);
        Matrix4f pose = new Matrix4f();
        Matrix3f normal = new Matrix3f();
        bone.applyLocalTransform(pose, normal);
        BakedGeometryChunk chunk = model.chunkForBone(rootIndex);
        assertNotNull(chunk);

        RecordingConsumer packed = new RecordingConsumer();
        assertTrue(new SodiumBakedChunkWriter().writeQuads(chunk, packed,
                0, 0, 1, 1, 1, 1, pose, normal, true));
        assertEquals(24, packed.vertices.size());
        assertTrue(packed.pushCount > 0, "The Sodium native buffer path must be exercised");
        Matrix3f expectedNormalTransform = new Matrix3f(pose).invert().transpose();
        BakedQuadData quads = chunk.quads();
        for (int face = 0; face < quads.quadCount(); face++) {
            int nb = face * BakedQuadData.NORMAL_STRIDE;
            Vector3f expectedNormal = new Vector3f(quads.normals()[nb], quads.normals()[nb + 1], quads.normals()[nb + 2])
                    .mul(expectedNormalTransform).normalize();
            for (int corner = 0; corner < 4; corner++) {
                int pb = face * BakedQuadData.POSITION_STRIDE + corner * 3;
                Vector3f expectedPosition = new Vector3f(quads.positions()[pb], quads.positions()[pb + 1], quads.positions()[pb + 2])
                        .mulPosition(pose);
                Vertex actual = packed.vertices.get(face * 4 + corner);
                assertVectorEquals(expectedPosition, actual.position, 1.0e-6f);
                assertVectorEquals(expectedNormal, actual.normal, NORMAL_QUANTIZATION_ERROR);
                assertEquals(1.0f, actual.normal.length(), 0.015f, "Encoded normal must retain unit length within byte precision");
            }
        }
        assertPerpendicularFaces(packed.vertices, 0.025f);
    }

    private static BedrockModelPOJO modelPojo() {
        return GsonUtil.CLIENT_GSON.fromJson(ROTATED_CUBE, BedrockModelPOJO.class);
    }

    private static void assertPerpendicularFaces(List<Vertex> vertices, float tolerance) {
        for (int face = 0; face < vertices.size(); face += 4) {
            Vertex first = vertices.get(face);
            Vector3f edgeA = new Vector3f(vertices.get(face + 1).position).sub(first.position).normalize();
            Vector3f edgeB = new Vector3f(vertices.get(face + 3).position).sub(first.position).normalize();
            Vector3f normal = new Vector3f(first.normal).normalize();
            assertEquals(0.0f, edgeA.dot(normal), tolerance, "First edge must be perpendicular to emitted normal");
            assertEquals(0.0f, edgeB.dot(normal), tolerance, "Second edge must be perpendicular to emitted normal");
        }
    }

    private static void assertVectorEquals(Vector3f expected, Vector3f actual, float tolerance) {
        assertEquals(expected.x, actual.x, tolerance);
        assertEquals(expected.y, actual.y, tolerance);
        assertEquals(expected.z, actual.z, tolerance);
    }

    private record Vertex(Vector3f position, Vector3f normal) {}

    private static final class RecordingConsumer implements VertexConsumer, VertexBufferWriter {
        private final List<Vertex> vertices = new ArrayList<>();
        private int pushCount;

        @Override
        public void push(MemoryStack stack, long buffer, int count, VertexFormat format) {
            assertSame(EntityVertex.FORMAT, format);
            pushCount++;
            int positionOffset = format.getOffset(VertexFormatElement.POSITION);
            int normalOffset = format.getOffset(VertexFormatElement.NORMAL);
            for (int i = 0; i < count; i++) {
                long vertex = buffer + (long) i * format.getVertexSize();
                long position = vertex + positionOffset;
                long normal = vertex + normalOffset;
                vertices.add(new Vertex(
                        new Vector3f(MemoryUtil.memGetFloat(position), MemoryUtil.memGetFloat(position + 4), MemoryUtil.memGetFloat(position + 8)),
                        new Vector3f(MemoryUtil.memGetByte(normal) / 127.0f, MemoryUtil.memGetByte(normal + 1) / 127.0f,
                                MemoryUtil.memGetByte(normal + 2) / 127.0f)));
            }
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light,
                              float nx, float ny, float nz) {
            vertices.add(new Vertex(new Vector3f(x, y, z), new Vector3f(nx, ny, nz)));
        }

        @Override public VertexConsumer addVertex(float x, float y, float z) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setUv(float u, float v) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setUv1(int u, int v) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setUv2(int u, int v) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer setNormal(float x, float y, float z) { throw new UnsupportedOperationException(); }
    }
}
