package com.lauua.lauuacheats.scan;

/** Deliberately a confidence score, never proof of a base or newly generated terrain. */
public final class ScoreModel {
    private ScoreModel() { }
    public record Evidence(int containers, int spawners, int utilityBlocks, int artificialBlocks,
                           boolean naturalStructure, boolean nether, int y) { }
    public static double score(Evidence e) {
        // Naturally generated spawners, chests and underground obsidian are common.
        // Prefer distinct *combinations* of utility blocks and storage to a single find;
        // penalize expected biome/height/worldgen background. Scores remain heuristic.
        double score = Math.min(e.containers(), 12) * 1.5 + Math.min(e.utilityBlocks(), 12) * 3.0
            + Math.min(e.artificialBlocks(), 24) * 0.8;
        if (e.containers() > 0 && e.utilityBlocks() > 0) score += 10;
        if (e.artificialBlocks() >= 4 && e.utilityBlocks() >= 2) score += 8;
        if (e.naturalStructure()) score -= 8;
        if (e.nether() && e.y() < 32) score -= 3; // obsidian/portal remnants are common below lava.
        // Spawner location is a useful ESP target, not evidence a player built a base.
        return Math.max(0, score);
    }
    public static String classification(double score) {
        return score >= 28 ? "STRONG_LEAD" : score >= 12 ? "POSSIBLE_LEAD" : "UNKNOWN";
    }
}
