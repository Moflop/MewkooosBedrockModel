package mods.mewkooo.bedrockmodel.resource;

import java.io.InputStream;

public interface RawResourceLoader {
    <T> T load(InputStream inputStream, Class<T> clazz);
}
