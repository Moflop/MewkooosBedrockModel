package mods.mewkooo.bedrockmodel.api.resource;

import mods.mewkooo.bedrockmodel.api.model.display.ItemDisplayTransforms;
import mods.mewkooo.bedrockmodel.api.model.BedrockModel;
import mods.mewkooo.bedrockmodel.api.model.ModelType;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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
}
