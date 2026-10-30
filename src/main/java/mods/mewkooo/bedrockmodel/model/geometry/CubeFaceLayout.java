package mods.mewkooo.bedrockmodel.model.geometry;

/** Cube vertex indices and face winding shared by model bakers and client render backends. */
public final class CubeFaceLayout {
    public static final int NUM_CUBE_FACES = 6;
    public static final int VERTEX_X1_Y1_Z1 = 0;
    public static final int VERTEX_X2_Y1_Z1 = 1;
    public static final int VERTEX_X2_Y2_Z1 = 2;
    public static final int VERTEX_X1_Y2_Z1 = 3;
    public static final int VERTEX_X1_Y1_Z2 = 4;
    public static final int VERTEX_X2_Y1_Z2 = 5;
    public static final int VERTEX_X2_Y2_Z2 = 6;
    public static final int VERTEX_X1_Y2_Z2 = 7;

    public static final int[][] VERTEX_ORDER = {
            {VERTEX_X1_Y1_Z1, VERTEX_X2_Y1_Z1, VERTEX_X2_Y1_Z2, VERTEX_X1_Y1_Z2},
            {VERTEX_X1_Y2_Z2, VERTEX_X2_Y2_Z2, VERTEX_X2_Y2_Z1, VERTEX_X1_Y2_Z1},
            {VERTEX_X1_Y2_Z1, VERTEX_X2_Y2_Z1, VERTEX_X2_Y1_Z1, VERTEX_X1_Y1_Z1},
            {VERTEX_X2_Y2_Z2, VERTEX_X1_Y2_Z2, VERTEX_X1_Y1_Z2, VERTEX_X2_Y1_Z2},
            {VERTEX_X1_Y2_Z2, VERTEX_X1_Y2_Z1, VERTEX_X1_Y1_Z1, VERTEX_X1_Y1_Z2},
            {VERTEX_X2_Y2_Z1, VERTEX_X2_Y2_Z2, VERTEX_X2_Y1_Z2, VERTEX_X2_Y1_Z1},
    };

    private CubeFaceLayout() {}
}
