package mods.sbmenginefix;

import net.neoforged.fml.common.Mod;
import org.slf4j.LoggerFactory;

/** Independent opt-in engine compatibility mod; no dependency on the model library. */
@Mod(ChunkUnloadFixMod.MOD_ID)
public final class ChunkUnloadFixMod {
    public static final String MOD_ID = "sbm_chunk_unload_fix";

    public ChunkUnloadFixMod() {
        LoggerFactory.getLogger(MOD_ID).info("Optional Minecraft 1.21.1 chunk-unload compatibility fix loaded");
    }
}
