package mods.mewkooo.bedrockmodel.core.resource;

import mods.mewkooo.bedrockmodel.core.json.pojo.BedrockAnimationFile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.profiling.InactiveProfiler;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BedrockAnimationSourceRepositoryTest {
    @Test
    void publishedFileMapIsAnImmutableGenerationSnapshot() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("test", "animation");
        BedrockAnimationSourceRepository repository = new BedrockAnimationSourceRepository(Map.of());
        repository.apply(Map.of(id, Optional.<BedrockAnimationFile>empty()), null, InactiveProfiler.INSTANCE);

        Map<ResourceLocation, Optional<BedrockAnimationFile>> first = repository.getAllAnimationFiles();
        repository.apply(Map.of(), null, InactiveProfiler.INSTANCE);

        assertEquals(Map.of(id, Optional.empty()), first);
        assertEquals(Map.of(), repository.getAllAnimationFiles());
        assertThrows(UnsupportedOperationException.class, first::clear);
    }

    @Test
    void concurrentReaderKeepsThePreviousGenerationUntilTheNextOneIsComplete() throws Exception {
        ResourceLocation previousId = ResourceLocation.fromNamespaceAndPath("test", "previous");
        ResourceLocation firstId = ResourceLocation.fromNamespaceAndPath("test", "first");
        ResourceLocation secondId = ResourceLocation.fromNamespaceAndPath("test", "second");
        BedrockAnimationSourceRepository repository = new BedrockAnimationSourceRepository(Map.of());
        repository.apply(Map.of(previousId, Optional.<BedrockAnimationFile>empty()), null, InactiveProfiler.INSTANCE);

        CountDownLatch firstEntryCopied = new CountDownLatch(1);
        CountDownLatch continueCopy = new CountDownLatch(1);
        Map<ResourceLocation, Optional<BedrockAnimationFile>> prepared = new AbstractMap<>() {
            private final java.util.List<Entry<ResourceLocation, Optional<BedrockAnimationFile>>> entries = java.util.List.of(
                    Map.entry(firstId, Optional.empty()), Map.entry(secondId, Optional.empty()));

            @Override
            public Set<Entry<ResourceLocation, Optional<BedrockAnimationFile>>> entrySet() {
                return new AbstractSet<>() {
                    @Override public int size() { return entries.size(); }

                    @Override
                    public Iterator<Entry<ResourceLocation, Optional<BedrockAnimationFile>>> iterator() {
                        return new Iterator<>() {
                            private int index;
                            private boolean paused;

                            @Override
                            public boolean hasNext() {
                                if (index == 1 && !paused) {
                                    paused = true;
                                    firstEntryCopied.countDown();
                                    try {
                                        if (!continueCopy.await(5, TimeUnit.SECONDS)) {
                                            throw new IllegalStateException("timed out waiting to finish generation copy");
                                        }
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        throw new IllegalStateException(e);
                                    }
                                }
                                return index < entries.size();
                            }

                            @Override public Entry<ResourceLocation, Optional<BedrockAnimationFile>> next() {
                                return entries.get(index++);
                            }
                        };
                    }
                };
            }
        };

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var reload = executor.submit(() -> repository.apply(prepared, null, InactiveProfiler.INSTANCE));
            org.junit.jupiter.api.Assertions.assertTrue(firstEntryCopied.await(5, TimeUnit.SECONDS));
            assertEquals(Map.of(previousId, Optional.empty()), repository.getAllAnimationFiles());
            continueCopy.countDown();
            reload.get(5, TimeUnit.SECONDS);
            assertEquals(Map.of(firstId, Optional.empty(), secondId, Optional.empty()), repository.getAllAnimationFiles());
        } finally {
            continueCopy.countDown();
            executor.shutdownNow();
        }
    }
}
