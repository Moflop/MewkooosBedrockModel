package mods.mewkooo.bedrockmodel.api.resource;

import org.jetbrains.annotations.Nullable;
import net.neoforged.fml.LogicalSide;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Entry point for querying the library's current resource generation.
 *
 * <p>The library publishes the implementation after its first resource generation
 * is applied successfully. Consumers only need this class and the contracts in
 * {@code api.resource}; no {@code core.resource} class is required.</p>
 */
public final class BedrockResources {
    private static volatile BedrockModelAccess clientModels;
    private static volatile BedrockModelAccess serverModels;

    private BedrockResources() {
    }

    /**
     * Returns the current model view for a logical side, or {@code null}
     * before that side's first resource generation has been published. A physical client
     * may have both a client-resource view and an integrated-server view.
     * A replacement reload listener keeps the previous view published until its
     * complete generation and resource callbacks have been applied successfully.
     */
    @Nullable
    public static BedrockModelAccess models(LogicalSide side) {
        Objects.requireNonNull(side, "side");
        return side == LogicalSide.CLIENT ? clientModels : serverModels;
    }

    /**
     * Publishes the completed view used by the reload bootstrap.
     *
     * <p>This method is public so the core bootstrap can remain an implementation
     * package.  Downstream mods should only read {@link #models(LogicalSide)}.</p>
     */
    @ApiStatus.Internal
    public static void install(LogicalSide side, BedrockModelAccess access) {
        Objects.requireNonNull(side, "side");
        BedrockModelAccess value = Objects.requireNonNull(access, "access");
        if (side == LogicalSide.CLIENT) {
            clientModels = value;
        } else {
            serverModels = value;
        }
    }

    @ApiStatus.Internal
    public static void clear(LogicalSide side) {
        Objects.requireNonNull(side, "side");
        if (side == LogicalSide.CLIENT) {
            clientModels = null;
        } else {
            serverModels = null;
        }
    }
}
