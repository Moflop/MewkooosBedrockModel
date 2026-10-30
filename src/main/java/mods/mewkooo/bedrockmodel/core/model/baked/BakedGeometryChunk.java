package mods.mewkooo.bedrockmodel.core.model.baked;


public final class BakedGeometryChunk {
    private final int attachBoneIndex;
    private final BakedQuadData quads;
    private final BakedVertexData vertices;
    private final String[] sourceBones;

    public BakedGeometryChunk(int attachBoneIndex, BakedQuadData quads, BakedVertexData vertices, String[] sourceBones) {
        this.attachBoneIndex = attachBoneIndex;
        this.quads = quads == null ? BakedQuadData.EMPTY : quads;
        this.vertices = vertices == null ? BakedVertexData.EMPTY : vertices;
        this.sourceBones = sourceBones.clone();
    }

    public int attachBoneIndex() {
        return attachBoneIndex;
    }

    public BakedQuadData quads() {
        return quads;
    }

    public BakedVertexData vertices() {
        return vertices;
    }

    public String[] sourceBones() {
        return sourceBones.clone();
    }

    public int quadCount() {
        return quads.quadCount();
    }

    public int vertexCount() {
        return quads.quadCount() * 4 + vertices.vertexCount();
    }

    public boolean hasQuads() {
        return quads.quadCount() > 0;
    }

    public boolean hasVertices() {
        return vertices.vertexCount() > 0;
    }

    public boolean isRootAttached() {
        return attachBoneIndex < 0;
    }
}

