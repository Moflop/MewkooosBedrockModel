package mods.mewkooo.bedrockmodel.client.legacy.event;

import mods.mewkooo.bedrockmodel.BedrockModelMod;
import mods.mewkooo.bedrockmodel.api.animation.time.AnimationClock;
import mods.mewkooo.bedrockmodel.client.legacy.animation.PausedClientAnimationClock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;


@EventBusSubscriber(modid = BedrockModelMod.MOD_ID, value = Dist.CLIENT)
public final class ClientAnimationClockTicker {
    private ClientAnimationClockTicker() {}

    public static AnimationClock getAnimationClock() {
        return PausedClientAnimationClock.getInstance();
    }

    @SubscribeEvent
    public static void onRenderTick(RenderFrameEvent.Pre event) {
        PausedClientAnimationClock.getInstance().update();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PausedClientAnimationClock.getInstance().reset();
    }
}
