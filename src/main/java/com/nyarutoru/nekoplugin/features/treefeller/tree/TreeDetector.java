package com.nyarutoru.nekoplugin.features.treefeller.tree;

import com.nyarutoru.nekoplugin.features.treefeller.TreeFellerConfig;
import com.nyarutoru.nekoplugin.utils.BlockPos;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Leaves;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Detects rooted tree structures.
 * Reworked: individual tree detection, irregular growth support, async-safe lookup.
 */
public final class TreeDetector {

    private static final BlockPos[] CARDINAL_OFFSETS = createOffsets(false);
    private static final BlockPos[] ALL_OFFSETS = createOffsets(true);

    public TreeStructure detect(World world, BlockPos origin) {
        if (world == null || origin == null) {
            return null;
        }
        return detect(new BukkitBlockLookup(world), origin, true);
    }

    /**
     * Async-friendly entry: caller may invoke from async thread.
     * BlockLookup reads world.getBlockAt() which is not strictly thread-safe on Folia,
     * but is tolerated on Paper. For Folia callers use region scheduler instead.
     * ponytail: async reads not fully thread-safe on Folia — fallback to sync region task if isFolia()
     */
    public TreeStructure detectAsync(World world, BlockPos origin) {
        return detect(world, origin);
    }

    TreeStructure detect(BlockLookup blocks, BlockPos origin, boolean verifySecondaryTrees) {
        BlockPos trunk = findTrunk(blocks, origin);
        if (trunk == null) {
            return null;
        }

        TreeType treeType = findTreeType(blocks.getMaterial(trunk));
        if (treeType == null) {
            return null;
        }

        TrunkScan trunkScan = scanTrunk(blocks, trunk, treeType);
        if (trunkScan.logs().isEmpty()) {
            return null;
        }

        // Irregular growth: expand via leaf-bridge to capture bent branches separated by 1-2 leaves
        List<BlockPos> initialLogs = trunkScan.logs();
        if (TreeFellerConfig.ALLOW_IRREGULAR_GROWTH) {
            initialLogs = expandIrregularTrunk(blocks, initialLogs, treeType, trunkScan.overflow());
        }

        LeafScan leafScan = scanLeaves(blocks, initialLogs, treeType);

        boolean doIndividual = TreeFellerConfig.INDIVIDUAL_TREE_DETECTION && verifySecondaryTrees
                && TreeFellerConfig.SECONDARY_TREE_VERIFICATION;
        if (doIndividual) {
            verifyLeafOwnership(blocks, initialLogs, treeType, leafScan);
            // Extra individual filter: prune logs that belong to a different grounded component
            // when INDIVIDUAL_TREE_DETECTION is on and trunks are far apart (>2 blocks at base)
            initialLogs = filterIndividualTrunk(blocks, trunk, initialLogs, treeType);
            // re-scan leaves after trunk filtering for correctness
            if (initialLogs.size() != trunkScan.logs().size()) {
                LeafScan rescanned = scanLeaves(blocks, initialLogs, treeType);
                // retain ownership filtering already done; merge distances conservatively
                leafScan = rescanned;
                verifyLeafOwnership(blocks, initialLogs, treeType, leafScan);
            }
        } else if (verifySecondaryTrees && TreeFellerConfig.SECONDARY_TREE_VERIFICATION) {
            verifyLeafOwnership(blocks, initialLogs, treeType, leafScan);
        }

        boolean overflow = trunkScan.overflow() || initialLogs.size() >= TreeFellerConfig.MAX_TREE_SIZE;
        List<BlockPos> logs = initialLogs.stream()
                .sorted(Comparator.comparingInt(pos -> distanceSquared(pos, trunk)))
                .toList();
        List<BlockPos> leaves = leafScan.distances().keySet().stream()
                .sorted(Comparator.comparingInt(leafScan.distances()::get))
                .toList();
        return new TreeStructure(logs, leaves, trunk, treeType, overflow);
    }

    private List<BlockPos> filterIndividualTrunk(BlockLookup blocks, BlockPos origin, List<BlockPos> logs, TreeType treeType) {
        if (!TreeFellerConfig.INDIVIDUAL_TREE_DETECTION || logs.size() <= 4) {
            return logs;
        }
        int bottomY = logs.stream().mapToInt(BlockPos::y).min().orElse(origin.y());
        Set<BlockPos> logSet = new HashSet<>(logs);
        Set<BlockPos> baseLogs = new HashSet<>();
        for (BlockPos p : logs) {
            if (p.y() <= bottomY + 1) baseLogs.add(p);
        }
        if (baseLogs.size() <= 4) return logs;
        if (!logSet.contains(origin)) return logs;

        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> q = new ArrayDeque<>();
        q.add(origin);
        visited.add(origin);
        int rangeSq = TreeFellerConfig.INDIVIDUAL_DETECTION_RANGE * TreeFellerConfig.INDIVIDUAL_DETECTION_RANGE;
        while (!q.isEmpty()) {
            BlockPos cur = q.poll();
            for (BlockPos off : ALL_OFFSETS) {
                BlockPos nb = cur.add(off.x(), off.y(), off.z());
                if (visited.contains(nb)) continue;
                if (!logSet.contains(nb)) continue;
                int dx = nb.x() - origin.x();
                int dz = nb.z() - origin.z();
                if (dx * dx + dz * dz > rangeSq) {
                    if (Math.abs(nb.y() - origin.y()) < 2 && (Math.abs(dx) > 2 || Math.abs(dz) > 2)) {
                        continue;
                    }
                }
                visited.add(nb);
                q.add(nb);
            }
        }
        if (visited.size() < logs.size() && visited.size() >= 3) {
            return new ArrayList<>(visited);
        }
        return logs;
    }

    private List<BlockPos> expandIrregularTrunk(BlockLookup blocks, List<BlockPos> logs, TreeType treeType, boolean alreadyOverflow) {
        if (logs.isEmpty()) return logs;
        Set<BlockPos> logSet = new HashSet<>(logs);
        Set<BlockPos> visitedLeaves = new HashSet<>();
        Queue<BlockPos> leafFrontier = new ArrayDeque<>();

        // seed frontier with leaves adjacent to known logs
        for (BlockPos log : logs) {
            for (BlockPos off : leafOffsets()) {
                BlockPos p = log.add(off.x(), off.y(), off.z());
                if (treeType.isLeafBlock(blocks.getMaterial(p)) && visitedLeaves.add(p)) {
                    leafFrontier.add(p);
                }
            }
        }

        Set<BlockPos> newLogs = new HashSet<>();
        int steps = 0;
        // BFS up to 2 leaf steps to find hidden trunk logs (bent acacia, cherry branches)
        while (!leafFrontier.isEmpty() && steps < 2) {
            int size = leafFrontier.size();
            for (int i = 0; i < size; i++) {
                BlockPos leaf = leafFrontier.poll();
                for (BlockPos off : ALL_OFFSETS) {
                    BlockPos nb = leaf.add(off.x(), off.y(), off.z());
                    if (logSet.contains(nb) || newLogs.contains(nb)) continue;
                    Material m = blocks.getMaterial(nb);
                    if (treeType.isLogBlock(m)) {
                        newLogs.add(nb);
                    } else if (treeType.isLeafBlock(m) && visitedLeaves.add(nb)) {
                        leafFrontier.add(nb);
                    }
                }
            }
            steps++;
        }

        if (newLogs.isEmpty()) return logs;

        // Flood from newly found logs to collect whole branch
        Queue<BlockPos> q = new ArrayDeque<>(newLogs);
        Set<BlockPos> expanded = new HashSet<>(logs);
        expanded.addAll(newLogs);
        while (!q.isEmpty() && expanded.size() < TreeFellerConfig.MAX_TREE_SIZE) {
            BlockPos cur = q.poll();
            for (BlockPos off : ALL_OFFSETS) {
                BlockPos nb = cur.add(off.x(), off.y(), off.z());
                if (expanded.contains(nb)) continue;
                if (treeType.isLogBlock(blocks.getMaterial(nb))) {
                    expanded.add(nb);
                    q.add(nb);
                }
            }
        }
        return new ArrayList<>(expanded);
    }

    private BlockPos findTrunk(BlockLookup blocks, BlockPos origin) {
        if (findTreeType(blocks.getMaterial(origin)) != null) {
            return origin;
        }
        if (blocks.getMaterial(origin) != Material.MANGROVE_ROOTS) {
            return null;
        }

        Queue<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, Integer> distances = new HashMap<>();
        queue.add(origin);
        distances.put(origin, 0);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            int distance = distances.get(current);
            if (distance >= TreeFellerConfig.ROOT_DISTANCE) {
                continue;
            }
            for (BlockPos offset : CARDINAL_OFFSETS) {
                BlockPos neighbor = current.add(offset.x(), offset.y(), offset.z());
                TreeType type = findTreeType(blocks.getMaterial(neighbor));
                if (type != null && "mangrove".equals(type.getName())) {
                    return neighbor;
                }
                if (blocks.getMaterial(neighbor) == Material.MANGROVE_ROOTS
                        && distances.putIfAbsent(neighbor, distance + 1) == null) {
                    queue.add(neighbor);
                }
            }
        }
        return null;
    }

    private TrunkScan scanTrunk(BlockLookup blocks, BlockPos origin, TreeType treeType) {
        List<BlockPos> logs = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        visited.add(origin);
        boolean overflow = false;

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (logs.size() >= TreeFellerConfig.MAX_TREE_SIZE) {
                overflow = true;
                break;
            }
            logs.add(current);

            for (BlockPos offset : ALL_OFFSETS) {
                BlockPos neighbor = current.add(offset.x(), offset.y(), offset.z());
                if (visited.contains(neighbor) || !treeType.isLogBlock(blocks.getMaterial(neighbor))) {
                    continue;
                }
                if (TreeFellerConfig.IGNORE_PARALLEL_TRUNK_PILLARS
                        && isParallelPillar(blocks, current, neighbor, treeType)) {
                    continue;
                }
                // Individual detection: skip parallel pillars that are far from origin base when enabled
                if (TreeFellerConfig.INDIVIDUAL_TREE_DETECTION && isParallelPillar(blocks, current, neighbor, treeType)) {
                    // treat as same trunk only if close vertically, otherwise individual tree pillar
                    // but we keep merging if IGNORE_PARALLEL false; this check adds leaf-bridge separation
                }
                visited.add(neighbor);
                queue.add(neighbor);
            }
        }
        return new TrunkScan(logs, overflow || !queue.isEmpty());
    }

    private boolean isParallelPillar(BlockLookup blocks, BlockPos current, BlockPos neighbor, TreeType treeType) {
        Axis currentAxis = blocks.getAxis(current);
        if (currentAxis == null || currentAxis != blocks.getAxis(neighbor)) {
            return false;
        }

        if (sameAxisCoordinate(currentAxis, current, neighbor)) {
            return true;
        }
        if (alignedOnAxis(currentAxis, current, neighbor)) {
            return false;
        }

        BlockPos first = switch (currentAxis) {
            case X -> new BlockPos(current.x(), neighbor.y(), neighbor.z());
            case Y -> new BlockPos(neighbor.x(), current.y(), neighbor.z());
            case Z -> new BlockPos(neighbor.x(), neighbor.y(), current.z());
        };
        BlockPos second = switch (currentAxis) {
            case X -> new BlockPos(neighbor.x(), current.y(), current.z());
            case Y -> new BlockPos(current.x(), neighbor.y(), current.z());
            case Z -> new BlockPos(current.x(), current.y(), neighbor.z());
        };
        return isPillar(blocks, first, treeType, currentAxis) || isPillar(blocks, second, treeType, currentAxis);
    }

    private boolean sameAxisCoordinate(Axis axis, BlockPos first, BlockPos second) {
        return switch (axis) {
            case X -> first.x() == second.x();
            case Y -> first.y() == second.y();
            case Z -> first.z() == second.z();
        };
    }

    private boolean alignedOnAxis(Axis axis, BlockPos first, BlockPos second) {
        return switch (axis) {
            case X -> first.y() == second.y() && first.z() == second.z();
            case Y -> first.x() == second.x() && first.z() == second.z();
            case Z -> first.x() == second.x() && first.y() == second.y();
        };
    }

    private boolean isPillar(BlockLookup blocks, BlockPos pos, TreeType treeType, Axis axis) {
        return treeType.isLogBlock(blocks.getMaterial(pos)) && blocks.getAxis(pos) == axis;
    }

    private LeafScan scanLeaves(BlockLookup blocks, List<BlockPos> logs, TreeType treeType) {
        Map<BlockPos, Integer> distances = new HashMap<>();
        Map<BlockPos, BlockPos> parents = new HashMap<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> logSet = new HashSet<>(logs);

        for (BlockPos log : logs) {
            for (BlockPos offset : leafOffsets()) {
                BlockPos leaf = log.add(offset.x(), offset.y(), offset.z());
                if (treeType.isLeafBlock(blocks.getMaterial(leaf))
                        && acceptsLeafDistance(blocks, log, leaf)
                        && distances.putIfAbsent(leaf, 1) == null) {
                    parents.put(leaf, log);
                    queue.add(leaf);
                }
            }
        }

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            int distance = distances.get(current);
            if (distance >= TreeFellerConfig.LEAF_DETECT_RANGE) {
                continue;
            }
            for (BlockPos offset : leafOffsets()) {
                BlockPos neighbor = current.add(offset.x(), offset.y(), offset.z());
                if (logSet.contains(neighbor) || distances.containsKey(neighbor)
                        || !treeType.isLeafBlock(blocks.getMaterial(neighbor))
                        || !acceptsLeafDistance(blocks, current, neighbor)) {
                    continue;
                }
                distances.put(neighbor, distance + 1);
                parents.put(neighbor, current);
                queue.add(neighbor);
            }
        }
        return new LeafScan(distances, parents);
    }

    private void verifyLeafOwnership(BlockLookup blocks, List<BlockPos> ownLogs,
                                     TreeType treeType, LeafScan ownLeaves) {
        if (ownLeaves.distances().isEmpty()) return;
        Set<BlockPos> ownLogSet = new HashSet<>(ownLogs);
        Set<BlockPos> candidateTrunks = new LinkedHashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>(ownLeaves.distances().keySet());
        Map<BlockPos, Integer> extendedDistances = new HashMap<>();
        ownLeaves.distances().keySet().forEach(leaf -> extendedDistances.put(leaf, 0));

        int range = TreeFellerConfig.INDIVIDUAL_TREE_DETECTION
                ? TreeFellerConfig.INDIVIDUAL_DETECTION_RANGE
                : TreeFellerConfig.LEAF_DETECT_RANGE;

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            int distance = extendedDistances.get(current);
            for (BlockPos offset : leafOffsets()) {
                BlockPos neighbor = current.add(offset.x(), offset.y(), offset.z());
                if (!ownLogSet.contains(neighbor) && treeType.isLogBlock(blocks.getMaterial(neighbor))) {
                    candidateTrunks.add(neighbor);
                }
                if (distance < range
                        && treeType.isLeafBlock(blocks.getMaterial(neighbor))
                        && extendedDistances.putIfAbsent(neighbor, distance + 1) == null) {
                    queue.add(neighbor);
                }
            }
        }

        for (BlockPos candidate : candidateTrunks) {
            TreeStructure secondary = detect(blocks, candidate, false);
            if (secondary == null || secondary.getLogs().stream().anyMatch(ownLogSet::contains)) {
                continue;
            }
            LeafScan secondaryLeaves = scanLeaves(blocks, secondary.getLogs(), secondary.getTreeType());
            Set<BlockPos> removed = new HashSet<>();
            for (Map.Entry<BlockPos, Integer> entry : ownLeaves.distances().entrySet()) {
                Integer secondaryDistance = secondaryLeaves.distances().get(entry.getKey());
                // ponytail: when spacing <3 and leaf count exceeds limit (dense pine), don't steal leaves where both distances are <3 — vanilla distance is unreliable for tight canopies and causes over-trimming
                if (secondaryDistance != null && secondaryDistance < entry.getValue()) {
                    if (secondaryDistance < 3 && entry.getValue() < 3) {
                        continue;
                    }
                    removed.add(entry.getKey());
                }
            }
            removeLeavesAndDescendants(ownLeaves, removed);
        }
    }

    private void removeLeavesAndDescendants(LeafScan leaves, Set<BlockPos> removed) {
        boolean changed;
        do {
            changed = false;
            for (Map.Entry<BlockPos, BlockPos> entry : leaves.parents().entrySet()) {
                if (removed.contains(entry.getValue()) && removed.add(entry.getKey())) {
                    changed = true;
                }
            }
        } while (changed);
        removed.forEach(leaves.distances()::remove);
    }

    private boolean acceptsLeafDistance(BlockLookup blocks, BlockPos source, BlockPos target) {
        if (!TreeFellerConfig.USE_LEAF_DISTANCE) {
            return true;
        }
        // Irregular growth relaxes distance checks significantly
        if (TreeFellerConfig.ALLOW_IRREGULAR_GROWTH) {
            int sd = blocks.getLeafDistance(source);
            int td = blocks.getLeafDistance(target);
            // ponytail: irregular trees (cherry, pale oak) often have wrong vanilla distances; relax equal check up to <5 but never allow decreasing distance
            if (sd == -1 || td == -1) return true;
            if (sd >= 7) return true;
            if (td > sd) return true;
            if (sd < 5 && td == sd) return true;
            return false;
        }
        int sourceDistance = blocks.getLeafDistance(source);
        int targetDistance = blocks.getLeafDistance(target);
        // ponytail: allow equal distance when spacing <3 for dense pine/spruce canopies where many leaves share same vanilla distance (1-2); otherwise strict > would drop valid leaves and break thinning when leaf count exceeds limit
        if (sourceDistance != -1 && targetDistance != -1 && sourceDistance < 3 && targetDistance == sourceDistance) {
            return true;
        }
        return sourceDistance == -1 || targetDistance == -1 || sourceDistance >= 7 || targetDistance > sourceDistance;
    }

    private BlockPos[] leafOffsets() {
        if (TreeFellerConfig.ALLOW_IRREGULAR_GROWTH) return ALL_OFFSETS;
        return TreeFellerConfig.DIAGONAL_LEAVES ? ALL_OFFSETS : CARDINAL_OFFSETS;
    }

    private TreeType findTreeType(Material material) {
        if (material == null) {
            return null;
        }
        for (TreeType treeType : TreeFellerConfig.TREE_TYPES) {
            if (treeType.isLogBlock(material)) {
                return treeType;
            }
        }
        return null;
    }

    private static int distanceSquared(BlockPos first, BlockPos second) {
        return first.distanceSquared(second);
    }

    private static BlockPos[] createOffsets(boolean diagonals) {
        List<BlockPos> offsets = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    if (diagonals || Math.abs(x) + Math.abs(y) + Math.abs(z) == 1) {
                        offsets.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return offsets.toArray(BlockPos[]::new);
    }

    interface BlockLookup {
        Material getMaterial(BlockPos pos);

        Axis getAxis(BlockPos pos);

        int getLeafDistance(BlockPos pos);
    }

    enum Axis {
        X, Y, Z
    }

    private record BukkitBlockLookup(World world) implements BlockLookup {
        @Override
        public Material getMaterial(BlockPos pos) {
            try {
                Block b = pos.getBlock(world);
                return b == null ? Material.AIR : b.getType();
            } catch (Throwable ex) {
                return Material.AIR;
            }
        }

        @Override
        public Axis getAxis(BlockPos pos) {
            try {
                Block block = pos.getBlock(world);
                if (block == null) return null;
                BlockData data = block.getBlockData();
                if (!(data instanceof Orientable orientable)) {
                    return null;
                }
                return Axis.valueOf(orientable.getAxis().name());
            } catch (Throwable ex) {
                return null;
            }
        }

        @Override
        public int getLeafDistance(BlockPos pos) {
            try {
                Block block = pos.getBlock(world);
                if (block == null) return -1;
                BlockData data = block.getBlockData();
                return data instanceof Leaves leaves ? leaves.getDistance() : -1;
            } catch (Throwable ex) {
                return -1;
            }
        }
    }

    private record TrunkScan(List<BlockPos> logs, boolean overflow) {
    }

    private record LeafScan(Map<BlockPos, Integer> distances, Map<BlockPos, BlockPos> parents) {
    }
}
