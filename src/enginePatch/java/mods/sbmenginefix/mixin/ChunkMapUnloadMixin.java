package mods.sbmenginefix.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stop obsolete save callbacks from repeatedly scheduling an already-reclaimed holder. */
@Mixin(value = ChunkMap.class, remap = false)
public abstract class ChunkMapUnloadMixin {
    @Shadow @Final
    private Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads;

    @Inject(method = "scheduleUnload(JLnet/minecraft/server/level/ChunkHolder;)V", at = @At("HEAD"),
            cancellable = true)
    private void sbm$skipObsoleteUnload(long position, ChunkHolder holder, CallbackInfo callback) {
        // Reacquiring this coordinate removes the old holder from pendingUnloads.
        // A queued callback can still carry it while generation refs are nonzero.
        // Only the current pending holder may continue the vanilla unload path.
        if (pendingUnloads.get(position) != holder) callback.cancel();
    }
}
