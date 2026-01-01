package com.nyarutoru.nekoplugin.features.treefeller;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.LeavesDecayEvent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fast leaf decay listener that accelerates natural leaf decay.
 * Listens to vanilla LeavesDecayEvent and recursively triggers decay on
 * adjacent leaves.
 */
public class FastLeafDecayListener implements Listener {

    private static final List<BlockFace> FACES = Lists.newArrayList(
            Arrays.stream(BlockFace.values())
                    .filter(BlockFace::isCartesian)
                    .toList());

    private static final Set<Block> SCHEDULED = Sets.newHashSet();

    @EventHandler
    public void onDecay(LeavesDecayEvent event) {
        doDecay(event.getBlock());
    }

    public void doDecay(final Block block) {
        Collections.shuffle(FACES);

        for (final BlockFace face : FACES) {
            final Block b = block.getRelative(face);
            if (SCHEDULED.contains(b))
                continue;
            if (!(b.getBlockData() instanceof final Leaves leaves) || leaves.isPersistent()
                    || leaves.getDistance() < 7) {
                continue;
            }
            SCHEDULED.add(b);

            long delay = ThreadLocalRandom.current().nextLong(2, 10);
            SchedulerUtils.runAtLocationLater(b.getLocation(), () -> {
                final LeavesDecayEvent decayEvent = new LeavesDecayEvent(b);
                Bukkit.getPluginManager().callEvent(decayEvent);
                if (decayEvent.isCancelled())
                    return;
                b.breakNaturally();
                SCHEDULED.remove(b);
            }, delay);
        }
    }
}
