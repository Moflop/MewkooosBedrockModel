package mods.mewkooo.bedrockmodel.client.compat.sodium;

import mods.mewkooo.bedrockmodel.client.legacy.compat.embeddium.EmbeddiumCompat;
import mods.mewkooo.bedrockmodel.client.compat.embeddium.EmbeddiumBakedChunkWriter;
import mods.mewkooo.bedrockmodel.client.compat.embeddium.EmbeddiumTreeGeometryWriter;
import mods.mewkooo.bedrockmodel.model.baked.BakedGeometryChunk;
import mods.mewkooo.bedrockmodel.model.tree.ICube;
import mods.mewkooo.bedrockmodel.model.tree.PolyMesh;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public final class SodiumCompat {
    private static final ChunkVertexWriter WRITER = selectWriter();

    private SodiumCompat() {
    }

    public static boolean writeQuads(BakedGeometryChunk chunk, VertexConsumer consumer, int lightmap, int overlay,
                                     float red, float green, float blue, float alpha, Matrix4f finalPose, Matrix3f finalNormal) {
        return writeQuads(chunk, consumer, lightmap, overlay, red, green, blue, alpha, finalPose, finalNormal, false);
    }

    public static boolean writeQuads(BakedGeometryChunk chunk, VertexConsumer consumer, int lightmap, int overlay,
                                     float red, float green, float blue, float alpha, Matrix4f finalPose, Matrix3f finalNormal,
                                     boolean skipNormalVisibilityCull) {
        return WRITER.writeQuads(chunk, consumer, lightmap, overlay, red, green, blue, alpha, finalPose, finalNormal, skipNormalVisibilityCull);
    }

    public static boolean writeVertices(BakedGeometryChunk chunk, VertexConsumer consumer, int lightmap, int overlay,
                                        float red, float green, float blue, float alpha, Matrix4f finalPose, Matrix3f finalNormal) {
        // Sodium and Embeddium triangle buffers must use the standard VertexConsumer path.
        return false;
    }

    public static boolean writeCubes(ICube[] cubes, VertexConsumer consumer, int lightmap, int overlay,
                                     float red, float green, float blue, float alpha, Matrix4f finalPose, Matrix3f finalNormal) {
        return writeCubes(cubes, consumer, lightmap, overlay, red, green, blue, alpha, finalPose, finalNormal, false);
    }

    public static boolean writeCubes(ICube[] cubes, VertexConsumer consumer, int lightmap, int overlay,
                                     float red, float green, float blue, float alpha, Matrix4f finalPose, Matrix3f finalNormal,
                                     boolean skipNormalVisibilityCull) {
        if (mods.mewkooo.bedrockmodel.client.legacy.compat.sodium.SodiumCompat.isSodiumInstalled()) {
            return SodiumTreeWriterHolder.WRITER.writeCubes(cubes, consumer, lightmap, overlay, red, green, blue, alpha, finalPose, finalNormal, skipNormalVisibilityCull);
        }
        if (EmbeddiumCompat.isEmbeddiumInstalled()) {
            return EmbeddiumTreeWriterHolder.WRITER.writeCubes(cubes, consumer, lightmap, overlay, red, green, blue, alpha, finalPose, finalNormal, skipNormalVisibilityCull);
        }
        return false;
    }

    public static boolean writePolyMeshes(PolyMesh[] polyMeshes, VertexConsumer consumer, int lightmap, int overlay,
                                          float red, float green, float blue, float alpha, Matrix4f finalPose, Matrix3f finalNormal) {
        // Keep both tree polymesh backends on the ordinary vertex writer.
        return false;
    }

    private static ChunkVertexWriter selectWriter() {
        if (mods.mewkooo.bedrockmodel.client.legacy.compat.sodium.SodiumCompat.isSodiumInstalled()) {
            return new SodiumBakedChunkWriter();
        }
        if (EmbeddiumCompat.isEmbeddiumInstalled()) {
            return new EmbeddiumBakedChunkWriter();
        }
        return ChunkVertexWriter.NOOP;
    }

    private static final class SodiumTreeWriterHolder {
        private static final SodiumTreeGeometryWriter WRITER = new SodiumTreeGeometryWriter();
    }

    private static final class EmbeddiumTreeWriterHolder {
        private static final EmbeddiumTreeGeometryWriter WRITER = new EmbeddiumTreeGeometryWriter();
    }
}
