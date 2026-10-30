package mods.mewkooo.bedrockmodel.api.resource;

import java.io.InputStream;

/** Decodes one Bedrock JSON resource on the current logical side. */
@FunctionalInterface
public interface RawResourceLoader {
    <T> T load(InputStream inputStream, Class<T> clazz);
}

