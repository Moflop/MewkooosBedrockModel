package mods.mewkooo.bedrockmodel.api.resource;

import mods.mewkooo.bedrockmodel.api.animation.AnimationClip;
import mods.mewkooo.bedrockmodel.api.model.display.ItemDisplayTransforms;
import mods.mewkooo.bedrockmodel.api.model.BedrockModel;
import mods.mewkooo.bedrockmodel.api.model.ModelType;
import net.minecraft.world.phys.AABB;
import net.minecraft.resources.ResourceLocation;
import com.maydaymemory.mae.basic.Pose;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BedrockModelResourceTest {
    @Test
    void modelKindComesFromTypedModelContract() {
        BedrockModel model = new BedrockModel() {
            @Override public ModelType kind() { return ModelType.TREE; }
            @Override public int getIndex(String boneName) { return -1; }
            @Override public AABB getRenderBoundingBox() { return null; }
            @Override public ItemDisplayTransforms getItemDisplayTransforms() { return ItemDisplayTransforms.NONE; }
        };

        BedrockModelResource resource = new BedrockModelResource(model, Map.of());

        assertSame(model, resource.model());
        assertEquals(ModelType.TREE, resource.kind());
    }

    @Test
    void animationListsAreDefensivelyCopiedAndReadOnly() {
        BedrockModel model = emptyTreeModel();
        ResourceLocation animationId = ResourceLocation.fromNamespaceAndPath("test", "animation");
        AnimationClip clip = new AnimationClip() {
            @Override public Pose evaluate(float timeSeconds) { return null; }
            @Override public float getSpecifiedEndTimeS() { return 0; }
        };
        List<AnimationClip> sourceClips = new ArrayList<>(List.of(clip));
        BedrockModelResource resource = new BedrockModelResource(model, Map.of(animationId, sourceClips));

        sourceClips.clear();

        List<AnimationClip> loadedClips = resource.getAnimations(animationId);
        assertEquals(List.of(clip), loadedClips);
        assertNotSame(sourceClips, loadedClips);
        assertThrows(UnsupportedOperationException.class, loadedClips::clear);
        assertThrows(UnsupportedOperationException.class, resource.animations().get(animationId)::clear);
        assertThrows(UnsupportedOperationException.class, resource.getAllAnimations().get(animationId)::clear);
        assertThrows(UnsupportedOperationException.class, () -> resource.getAllAnimations().clear());
    }

    private static BedrockModel emptyTreeModel() {
        return new BedrockModel() {
            @Override public ModelType kind() { return ModelType.TREE; }
            @Override public int getIndex(String boneName) { return -1; }
            @Override public AABB getRenderBoundingBox() { return null; }
            @Override public ItemDisplayTransforms getItemDisplayTransforms() { return ItemDisplayTransforms.NONE; }
        };
    }
}
