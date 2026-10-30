package mods.mewkooo.bedrockmodel.core.model.baked;

import mods.mewkooo.bedrockmodel.api.json.ItemDisplayTransforms;
import mods.mewkooo.bedrockmodel.api.model.AnimatedBedrockModel;
import mods.mewkooo.bedrockmodel.api.model.BakerOptions;
import mods.mewkooo.bedrockmodel.api.model.ModelType;
import mods.mewkooo.bedrockmodel.core.json.pojo.BedrockModelPOJO;
import mods.mewkooo.bedrockmodel.core.model.runtime.BakedModelInstance;
import com.maydaymemory.mae.basic.Pose;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

/** Shared baked Bedrock geometry and bone definition. Client rendering is implemented in {@code client.model}. */
public class BakedBedrockModel implements AnimatedBedrockModel {
    private final BakedBoneDefinition[] bones;
    private final Map<String, Integer> boneIndexByName;
    private final BakedGeometryChunk[] chunks;
    private final BakedGeometryChunk[] chunkByBone;
    @Nullable private final BakedGeometryChunk rootChunk;
    private final boolean retainsCubeGeometry;
    private final BakedCubeGeometry[] cubeGeometry;
    private final BoneLocator[] locators;
    private final Map<String, BoneLocator> locatorByName;
    private final Map<String, QueryTransform> queryTransformByName;
    private final Pose bindPose;
    private final AABB renderBoundingBox;
    private final ItemDisplayTransforms itemDisplayTransforms;

    public BakedBedrockModel(BakedBoneDefinition[] bones, Map<String, Integer> boneIndexByName, BakedGeometryChunk[] chunks,
                             BoneLocator[] locators, Map<String, BoneLocator> locatorByName,
                             QueryTransform[] queryTransforms, Map<String, QueryTransform> queryTransformByName,
                             Pose bindPose, AABB renderBoundingBox) {
        this(bones, boneIndexByName, chunks, new BakedCubeGeometry[0], false, locators, locatorByName,
                queryTransforms, queryTransformByName, bindPose, renderBoundingBox);
    }

    public BakedBedrockModel(BakedBoneDefinition[] bones, Map<String, Integer> boneIndexByName, BakedGeometryChunk[] chunks,
                             BakedCubeGeometry[] cubeGeometry, boolean retainsCubeGeometry,
                             BoneLocator[] locators, Map<String, BoneLocator> locatorByName,
                             QueryTransform[] queryTransforms, Map<String, QueryTransform> queryTransformByName,
                             Pose bindPose, AABB renderBoundingBox) {
        this(bones, boneIndexByName, chunks, cubeGeometry, retainsCubeGeometry, locators, locatorByName,
                queryTransforms, queryTransformByName, bindPose, renderBoundingBox, ItemDisplayTransforms.NONE);
    }

    public BakedBedrockModel(BakedBoneDefinition[] bones, Map<String, Integer> boneIndexByName, BakedGeometryChunk[] chunks,
                             BakedCubeGeometry[] cubeGeometry, boolean retainsCubeGeometry,
                             BoneLocator[] locators, Map<String, BoneLocator> locatorByName,
                             QueryTransform[] queryTransforms, Map<String, QueryTransform> queryTransformByName,
                             Pose bindPose, AABB renderBoundingBox, ItemDisplayTransforms itemDisplayTransforms) {
        this.bones = bones.clone();
        this.boneIndexByName = Map.copyOf(boneIndexByName);
        this.chunks = chunks.clone();
        ChunkIndex chunkIndex = indexChunks(this.chunks, bones.length);
        this.chunkByBone = chunkIndex.chunkByBone();
        this.rootChunk = chunkIndex.rootChunk();
        this.retainsCubeGeometry = retainsCubeGeometry;
        this.cubeGeometry = cubeGeometry.clone();
        this.locators = locators.clone();
        this.locatorByName = Map.copyOf(locatorByName);
        Objects.requireNonNull(queryTransforms, "queryTransforms");
        this.queryTransformByName = Map.copyOf(queryTransformByName);
        this.bindPose = bindPose;
        this.renderBoundingBox = renderBoundingBox;
        this.itemDisplayTransforms = Objects.requireNonNull(itemDisplayTransforms, "itemDisplayTransforms");
    }

    public static BakedBedrockModel bake(BedrockModelPOJO pojo) { return bake(pojo, BakerOptions.defaults()); }
    public static BakedBedrockModel bake(BedrockModelPOJO pojo, BakerOptions options) { return BedrockModelBaker.bake(pojo, options); }
    @Override public ModelType kind() { return ModelType.BAKED; }
    @Override public BakedModelInstance createInstance() { return new BakedModelInstance(this); }
    public BakedBoneDefinition[] bones() { return bones.clone(); }
    public BakedGeometryChunk[] chunks() { return chunks.clone(); }
    public boolean retainsCubeGeometry() { return retainsCubeGeometry; }
    public BakedCubeGeometry[] cubeGeometry() { return cubeGeometry.clone(); }
    public BakedGeometryChunk[] cubeChunks() { return chunksWithQuads(); }
    public BakedGeometryChunk[] meshChunks() { return chunksWithVertices(); }
    public BoneLocator[] locators() { return locators.clone(); }
    @Nullable public BoneLocator locator(String name) { return locatorByName.get(name); }
    @Nullable public QueryTransform queryTransform(String name) { return queryTransformByName.get(name); }
    @Override public int getIndex(String boneName) { return boneIndexByName.getOrDefault(boneName, -1); }
    public AABB getRenderBoundingBox() { return renderBoundingBox; }
    public ItemDisplayTransforms getItemDisplayTransforms() { return itemDisplayTransforms; }
    public Pose getBindPose() { return bindPose; }

    /** Internal chunk lookup for the client renderer. */
    @Nullable public BakedGeometryChunk rootChunk() { return rootChunk; }
    public BakedGeometryChunk chunkForBone(int index) { return index >= 0 && index < chunkByBone.length ? chunkByBone[index] : null; }

    private BakedGeometryChunk[] chunksWithQuads() {
        ArrayList<BakedGeometryChunk> result = new ArrayList<>();
        for (BakedGeometryChunk chunk : chunks) if (chunk.hasQuads()) result.add(chunk);
        return result.toArray(BakedGeometryChunk[]::new);
    }

    private BakedGeometryChunk[] chunksWithVertices() {
        ArrayList<BakedGeometryChunk> result = new ArrayList<>();
        for (BakedGeometryChunk chunk : chunks) if (chunk.hasVertices()) result.add(chunk);
        return result.toArray(BakedGeometryChunk[]::new);
    }

    private static ChunkIndex indexChunks(BakedGeometryChunk[] chunks, int boneCount) {
        BakedGeometryChunk[] chunkByBone = new BakedGeometryChunk[boneCount];
        BakedGeometryChunk rootChunk = null;
        for (BakedGeometryChunk chunk : chunks) {
            if (chunk.isRootAttached()) rootChunk = chunk;
            else if (chunk.attachBoneIndex() >= 0 && chunk.attachBoneIndex() < boneCount) chunkByBone[chunk.attachBoneIndex()] = chunk;
        }
        return new ChunkIndex(chunkByBone, rootChunk);
    }

    private record ChunkIndex(BakedGeometryChunk[] chunkByBone, @Nullable BakedGeometryChunk rootChunk) { }
}
