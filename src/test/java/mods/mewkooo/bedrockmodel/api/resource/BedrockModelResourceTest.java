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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        assertNull(resource.getAnimation(animationId, "animation.idle"),
                "the legacy constructor cannot infer names from unnamed clips");
    }

    @Test
    void namedLookupUsesFileLocalNamesAndTheSameClipsAsTheList() {
        ResourceLocation firstFile = id("first");
        ResourceLocation secondFile = id("second");
        AnimationClip idle = unnamedClip();
        AnimationClip walk = unnamedClip();
        AnimationClip otherIdle = unnamedClip();
        Map<String, AnimationClip> firstClips = new LinkedHashMap<>();
        firstClips.put("animation.idle", idle);
        firstClips.put("animation.walk", walk);
        BedrockModelResource resource = BedrockModelResource.fromNamedAnimations(emptyTreeModel(),
                Map.of(firstFile, firstClips, secondFile, Map.of("animation.idle", otherIdle)));

        assertEquals(List.of(idle, walk), resource.getAnimations(firstFile));
        assertSame(idle, resource.getAnimation(firstFile, "animation.idle"));
        assertSame(walk, resource.getAnimation(firstFile, "animation.walk"));
        assertSame(otherIdle, resource.getAnimation(secondFile, "animation.idle"));
        assertSame(resource.getAnimations(firstFile).getFirst(),
                resource.getAnimation(firstFile, "animation.idle"));
        assertNull(resource.getAnimation(firstFile, "idle"), "lookup requires the exact source name");
        assertNull(resource.getAnimation(firstFile, "animation.missing"));
        assertNull(resource.getAnimation(id("missing"), "animation.idle"));
    }

    @Test
    void namedFactoryDefensivelyCopiesBothMapLevelsAndKeepsReadOnlyViews() {
        ResourceLocation animationId = id("animation");
        AnimationClip clip = unnamedClip();
        Map<String, AnimationClip> sourceClips = new LinkedHashMap<>(Map.of("animation.idle", clip));
        Map<ResourceLocation, Map<String, AnimationClip>> source = new LinkedHashMap<>();
        source.put(animationId, sourceClips);
        BedrockModelResource resource = BedrockModelResource.fromNamedAnimations(emptyTreeModel(), source);

        sourceClips.clear();
        source.clear();

        assertSame(clip, resource.getAnimation(animationId, "animation.idle"));
        assertEquals(List.of(clip), resource.getAnimations(animationId));
        assertThrows(UnsupportedOperationException.class, () -> resource.animationsByName().clear());
        assertThrows(UnsupportedOperationException.class, resource.animationsByName().get(animationId)::clear);
        assertThrows(UnsupportedOperationException.class,
                () -> resource.animationsByName().get(animationId).put("animation.walk", unnamedClip()));
        assertThrows(UnsupportedOperationException.class, resource.getAnimations(animationId)::clear);
        assertThrows(UnsupportedOperationException.class, () -> resource.animations().clear());
    }

    @Test
    void explicitNamedConstructorCopiesItsIndexAndRejectsUnrelatedClips() {
        ResourceLocation animationId = id("animation");
        AnimationClip clip = unnamedClip();
        Map<String, AnimationClip> names = new LinkedHashMap<>(Map.of("animation.idle", clip));
        Map<ResourceLocation, Map<String, AnimationClip>> index = new LinkedHashMap<>();
        index.put(animationId, names);
        BedrockModelResource resource = new BedrockModelResource(emptyTreeModel(),
                Map.of(animationId, List.of(clip)), index);

        names.clear();
        index.clear();

        assertSame(clip, resource.getAnimation(animationId, "animation.idle"));
        assertThrows(IllegalArgumentException.class, () -> new BedrockModelResource(emptyTreeModel(),
                Map.of(animationId, List.of(clip)), Map.of(id("other"), Map.of("animation.idle", clip))));
        assertThrows(IllegalArgumentException.class, () -> new BedrockModelResource(emptyTreeModel(),
                Map.of(animationId, List.of(clip)),
                Map.of(animationId, Map.of("animation.idle", unnamedClip()))));
    }

    @Test
    void explicitNamedConstructorRequiresClipIdentityRatherThanEquality() {
        ResourceLocation animationId = id("animation");
        AnimationClip listedClip = new EqualClip();
        AnimationClip differentClip = new EqualClip();
        assertEquals(listedClip, differentClip);
        assertNotSame(listedClip, differentClip);

        assertThrows(IllegalArgumentException.class, () -> new BedrockModelResource(emptyTreeModel(),
                Map.of(animationId, List.of(listedClip)),
                Map.of(animationId, Map.of("animation.idle", differentClip))));
    }

    @Test
    void accessResolvesNamedClipsFromTheCurrentModelResource() {
        ResourceLocation modelId = id("model");
        ResourceLocation animationId = id("animation");
        Map<ResourceLocation, BedrockModelResource> resources = new LinkedHashMap<>();
        BedrockModelAccess access = new BedrockModelAccess() {
            @Override public BedrockModelResource getResource(ResourceLocation id) { return resources.get(id); }
            @Override public Map<ResourceLocation, BedrockModelResource> getAllResources() { return Map.copyOf(resources); }
        };
        assertNull(access.getAnimation(modelId, animationId, "animation.idle"));

        AnimationClip firstClip = unnamedClip();
        BedrockModelResource firstResource = BedrockModelResource.fromNamedAnimations(emptyTreeModel(),
                Map.of(animationId, Map.of("animation.idle", firstClip)));
        resources.put(modelId, firstResource);
        assertSame(firstClip, access.getAnimation(modelId, animationId, "animation.idle"));
        assertSame(firstResource.getAnimations(animationId), access.getAnimations(modelId, animationId));
        assertNull(access.getAnimation(id("missing_model"), animationId, "animation.idle"));
        assertNull(access.getAnimation(modelId, id("missing_file"), "animation.idle"));
        assertNull(access.getAnimation(modelId, animationId, "animation.missing"));

        AnimationClip replacement = unnamedClip();
        resources.put(modelId, BedrockModelResource.fromNamedAnimations(emptyTreeModel(),
                Map.of(animationId, Map.of("animation.idle", replacement))));

        assertSame(replacement, access.getAnimation(modelId, animationId, "animation.idle"));
        assertSame(firstClip, firstResource.getAnimation(animationId, "animation.idle"),
                "a previous resource keeps its own generation rather than a shared mutable index");
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    private static AnimationClip unnamedClip() {
        return new AnimationClip() {
            @Override public Pose evaluate(float timeSeconds) { return null; }
            @Override public float getSpecifiedEndTimeS() { return 0; }
        };
    }

    private static final class EqualClip implements AnimationClip {
        @Override public Pose evaluate(float timeSeconds) { return null; }
        @Override public float getSpecifiedEndTimeS() { return 0; }
        @Override public boolean equals(Object other) { return other instanceof EqualClip; }
        @Override public int hashCode() { return 0; }
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
