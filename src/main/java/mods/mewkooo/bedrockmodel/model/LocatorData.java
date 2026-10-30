package mods.mewkooo.bedrockmodel.model;

public record LocatorData(float[] offset, float[] rotation) {
    public static final LocatorData EMPTY = new LocatorData(new float[3], new float[3]);
}
