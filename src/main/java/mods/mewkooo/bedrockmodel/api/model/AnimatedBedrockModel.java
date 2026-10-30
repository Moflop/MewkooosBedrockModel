package mods.mewkooo.bedrockmodel.api.model;

/** A shared model representation that can create independent animated instances. */
public interface AnimatedBedrockModel extends BedrockModel {
    BedrockModelInstance createInstance();
}
