package mods.mewkooo.bedrockmodel.client.mesh;

import mods.mewkooo.bedrockmodel.client.mesh.cache.StaticMeshCache;
import mods.mewkooo.bedrockmodel.client.mesh.world.WorldMeshRenderer;

import mods.mewkooo.bedrockmodel.BedrockModelMod;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.jetbrains.annotations.ApiStatus;

/** 资源重载的 apply 阶段运行于客户端主线程；世界卸载监听由渲染器的注解订阅器处理。 */
@ApiStatus.Internal
@EventBusSubscriber(modid = BedrockModelMod.MOD_ID, value = Dist.CLIENT)
public class MeshLifecycle {
    public MeshLifecycle() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            WorldMeshRenderer.clearCaches();
            StaticMeshCache.clear();
        });
    }
}
