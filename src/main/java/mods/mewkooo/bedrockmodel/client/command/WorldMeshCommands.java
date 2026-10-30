package mods.mewkooo.bedrockmodel.client.command;

import mods.mewkooo.bedrockmodel.BedrockModelMod;
import mods.mewkooo.bedrockmodel.client.mesh.world.WorldMeshGroupStats;
import mods.mewkooo.bedrockmodel.client.mesh.world.WorldMeshRenderer;
import mods.mewkooo.bedrockmodel.client.mesh.world.StrategyOverride;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import org.jetbrains.annotations.ApiStatus;

import java.util.Locale;

/** 库随包提供的客户端配置快捷入口，与 example 压测命令独立。 */
@ApiStatus.Internal
@EventBusSubscriber(modid = BedrockModelMod.MOD_ID, value = Dist.CLIENT)
public final class WorldMeshCommands {
    private WorldMeshCommands() {}

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        var strategy = Commands.literal("strategy");
        for (StrategyOverride value : StrategyOverride.values()) {
            strategy.then(Commands.literal(value.name().toLowerCase(Locale.ROOT)).executes(context -> {
                WorldMeshRenderer.setStrategy(value);
                context.getSource().sendSuccess(() -> Component.literal("Configured strategy=" + value), false);
                for (WorldMeshGroupStats group : WorldMeshRenderer.groupStats()) {
                    String message = group.id() + ": " + group.effectiveStrategy() + " (" + group.policySource() + ") " + group.warnings();
                    context.getSource().sendSuccess(() -> Component.literal(message), false);
                }
                return 1;
            }));
        }
        event.getDispatcher().register(Commands.literal("sbmrender").then(strategy)
                .then(Commands.literal("groups").executes(context -> {
                    for (WorldMeshGroupStats group : WorldMeshRenderer.groupStats()) {
                        context.getSource().sendSuccess(() -> Component.literal(group.toString()), false);
                    }
                    return 1;
                })));
    }
}
