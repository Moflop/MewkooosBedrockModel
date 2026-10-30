package mods.mewkooo.bedrockmodel.client.legacy.compat.sodium;

import mods.mewkooo.bedrockmodel.client.legacy.model.BedrockBone;
import mods.mewkooo.bedrockmodel.client.legacy.model.BedrockPolyMesh;
import mods.mewkooo.bedrockmodel.core.json.pojo.PolyMeshItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public class SodiumBedrockPolyMesh extends BedrockPolyMesh implements ISodiumVertexWriter {
    public SodiumBedrockPolyMesh(PolyMeshItem polyMesh, BedrockBone part, float texWidth, float texHeight) {
        super(polyMesh, part, texWidth, texHeight);
    }

    @Override
    public void compileTriangles(PoseStack.Pose pose, VertexConsumer consumer, int lightmap, int overlay, float red, float green, float blue, float alpha) {
        // polymesh 三角形使用普通顶点接口；直接实例化此兼容类也不启用快速写入。
        super.compileTriangles(pose, consumer, lightmap, overlay, red, green, blue, alpha);
    }
}
