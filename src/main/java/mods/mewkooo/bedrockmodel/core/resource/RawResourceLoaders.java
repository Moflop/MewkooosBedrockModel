package mods.mewkooo.bedrockmodel.core.resource;

import mods.mewkooo.bedrockmodel.api.resource.RawResourceLoader;
import mods.mewkooo.bedrockmodel.core.json.GsonUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.LogicalSide;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class RawResourceLoaders {
    /** Keeps shared skeleton/animation data on both dedicated and integrated servers. */
    static final RawResourceLoader SERVER_MODEL_LOADER = new RawResourceLoader() {
        @Override
        public <T> T load(InputStream inputStream, Class<T> clazz) {
            try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                return GsonUtil.SERVER_GSON_FOR_ROOT_MOTION.fromJson(reader, clazz);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    };

    /** The repository side determines parsing, including in singleplayer. */
    static RawResourceLoader forLogicalSide(LogicalSide side) {
        return Objects.requireNonNull(side, "side") == LogicalSide.CLIENT ? FULL_LOADER : SERVER_MODEL_LOADER;
    }

    public static final RawResourceLoader CLIENT_ONLY_LOADER = new RawResourceLoader() {
        @Override
        public <T> T load(InputStream inputStream, Class<T> clazz) {
            if (FMLLoader.getDist() == Dist.CLIENT) {
                try (InputStreamReader reader = new InputStreamReader(inputStream)){
                    return GsonUtil.CLIENT_GSON.fromJson(reader, clazz);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                return null;
            }
        }
    };

    public static final RawResourceLoader COMMON_LOADER = new RawResourceLoader() {
        @Override
        public <T> T load(InputStream inputStream, Class<T> clazz) {
            try (InputStreamReader reader = new InputStreamReader(inputStream)) {
                if (FMLLoader.getDist() == Dist.CLIENT) {
                    return GsonUtil.CLIENT_GSON.fromJson(reader, clazz);
                } else {
                    return GsonUtil.SERVER_NORMAL_GSON.fromJson(reader, clazz);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    };

    public static final RawResourceLoader FULL_LOADER = new RawResourceLoader() {
        @Override
        public <T> T load(InputStream inputStream, Class<T> clazz) {
            try (InputStreamReader reader = new InputStreamReader(inputStream)) {
                return GsonUtil.CLIENT_GSON.fromJson(reader, clazz);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    };

    public static final RawResourceLoader ROOT_MOTION_READY_LOADER = new RawResourceLoader() {
        @Override
        public <T> T load(InputStream inputStream, Class<T> clazz) {
            try (InputStreamReader reader = new InputStreamReader(inputStream)) {
                if (FMLLoader.getDist() == Dist.CLIENT) {
                    return GsonUtil.CLIENT_GSON.fromJson(reader, clazz);
                } else {
                    return GsonUtil.SERVER_GSON_FOR_ROOT_MOTION.fromJson(reader, clazz);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    };
}


