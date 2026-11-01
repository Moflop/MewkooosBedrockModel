package mods.mewkooo.bedrockmodel.api.model;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Options controlling static geometry baking. */
public record BakerOptions(
        Set<String> animatedBones,
        Set<String> preservedBones,
        Set<Pattern> preservedBonePatterns,
        boolean bakeStaticGeometry,
        boolean debugFoldedTree,
        boolean retainCubeGeometry
) {
    public BakerOptions {
        animatedBones = Set.copyOf(animatedBones);
        preservedBones = Set.copyOf(preservedBones);
        preservedBonePatterns = Set.copyOf(preservedBonePatterns);
    }

    public BakerOptions(Set<String> animatedBones, Set<String> preservedBones,
                        Set<Pattern> preservedBonePatterns, boolean bakeStaticGeometry,
                        boolean debugFoldedTree) {
        this(animatedBones, preservedBones, preservedBonePatterns, bakeStaticGeometry, debugFoldedTree, false);
    }

    public BakerOptions(Set<String> animatedBones, Set<String> preservedBones,
                        boolean bakeStaticGeometry, boolean debugFoldedTree) {
        this(animatedBones, preservedBones, Set.of(), bakeStaticGeometry, debugFoldedTree, false);
    }

    public static BakerOptions defaults() {
        return new BakerOptions(Set.of(), Set.of(), Set.of(), true, false, false);
    }

    public static BakerOptions ofAnimatedBones(Set<String> animatedBones) {
        return new BakerOptions(animatedBones, Set.of(), Set.of(), true, false, false);
    }

    public BakerOptions withPreservedBonePatterns(Set<Pattern> patterns) {
        return new BakerOptions(animatedBones, preservedBones, patterns, bakeStaticGeometry, debugFoldedTree, retainCubeGeometry);
    }

    public BakerOptions withPreservedBoneRegexes(Set<String> regexes) {
        return withPreservedBonePatterns(regexes.stream().map(Pattern::compile).collect(Collectors.toSet()));
    }

    public BakerOptions withDebugFoldedTree(boolean value) {
        return new BakerOptions(animatedBones, preservedBones, preservedBonePatterns, bakeStaticGeometry, value, retainCubeGeometry);
    }

    public BakerOptions withRetainedCubeGeometry(boolean value) {
        return new BakerOptions(animatedBones, preservedBones, preservedBonePatterns, bakeStaticGeometry, debugFoldedTree, value);
    }
}

