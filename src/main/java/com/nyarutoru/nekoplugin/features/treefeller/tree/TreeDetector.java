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
 * Detects rooted tree structures using the layered scanner from TreeFeller v2.
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

        LeafScan leafScan = scanLeaves(blocks, trunkScan.logs(), treeType);
        if (verifySecondaryTrees && TreeFellerConfig.SECONDARY_TREE_VERIFICATION) {
            verifyLeafOwnership(blocks, trunkScan.logs(), treeType, leafScan);
        }

        List<BlockPos> logs = trunkScan.logs().stream()
                .sorted(Comparator.comparingInt(pos -> distanceSquared(pos, trunk)))
                .toList();
        List<BlockPos> leaves = leafScan.distances().keySet().stream()
                .sorted(Comparator.comparingInt(leafScan.distances()::get))
                .toList();
        return new TreeStructure(logs, leaves, trunk, treeType, trunkScan.overflow());
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
        Set<BlockPos> ownLogSet = new HashSet<>(ownLogs);
        Set<BlockPos> candidateTrunks = new LinkedHashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>(ownLeaves.distances().keySet());
        Map<BlockPos, Integer> extendedDistances = new HashMap<>(ownLeaves.distances());

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            int distance = extendedDistances.get(current);
            for (BlockPos offset : leafOffsets()) {
                BlockPos neighbor = current.add(offset.x(), offset.y(), offset.z());
                if (!ownLogSet.contains(neighbor) && treeType.isLogBlock(blocks.getMaterial(neighbor))) {
                    candidateTrunks.add(neighbor);
                }
                if (distance < TreeFellerConfig.LEAF_DETECT_RANGE
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
                if (secondaryDistance != null && secondaryDistance < entry.getValue()) {
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
        int sourceDistance = blocks.getLeafDistance(source);
        int targetDistance = blocks.getLeafDistance(target);
        return sourceDistance == -1 || targetDistance == -1 || sourceDistance >= 7 || targetDistance > sourceDistance;
    }

    private BlockPos[] leafOffsets() {
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
            return pos.getBlock(world).getType();
        }

        @Override
        public Axis getAxis(BlockPos pos) {
            BlockData data = pos.getBlock(world).getBlockData();
            if (!(data instanceof Orientable orientable)) {
                return null;
            }
            return Axis.valueOf(orientable.getAxis().name());
        }

        @Override
        public int getLeafDistance(BlockPos pos) {
            BlockData data = pos.getBlock(world).getBlockData();
            return data instanceof Leaves leaves ? leaves.getDistance() : -1;
        }
    }

    private record TrunkScan(List<BlockPos> logs, boolean overflow) {
    }

    private record LeafScan(Map<BlockPos, Integer> distances, Map<BlockPos, BlockPos> parents) {
    }
}
