package mods.mewkooo.bedrockmodel.core.particle.runtime;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Side-specific effects requested by a particle event.
 *
 * <p>The particle interpreter is shared by client and server code.  Rendering
 * and sound engines implement this small sink at the edge of the appropriate
 * runtime, keeping the shared interpreter free of client-only Minecraft
 * classes.</p>
 */
public interface ParticleEventSink {
    void spawnParticle(ResourceLocation effectId, Vec3 position);

    void playSound(ResourceLocation soundId, Vec3 position);
}

