package mods.mewkooo.bedrockmodel.api.animation.time;

/**
 * Factory for clocks that are safe to use from shared code.
 *
 * <p>Client pause handling lives in {@code client.animation}; keeping this
 * class free of client imports is important because model and animation
 * contracts are also loaded by a logical or dedicated server.</p>
 */
public final class AnimationClocks {
    /** A monotonic clock suitable for shared and server-side simulation. */
    private static final AnimationClock SYSTEM = System::nanoTime;

    private AnimationClocks() {
    }

    public static AnimationClock system() {
        return SYSTEM;
    }
}

