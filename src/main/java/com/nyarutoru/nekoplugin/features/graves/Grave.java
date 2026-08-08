package com.nyarutoru.nekoplugin.features.graves;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Grave {
    public enum State { ACTIVE, REMOVING, DISPOSED }
    public enum Disposition { NONE, DROP, LOOTED }

    public record ItemClaim(int index, ItemStack item) {
        public ItemClaim {
            item = item.clone();
        }
        @Override public ItemStack item() { return item.clone(); }
    }

    private final UUID id;
    private final UUID ownerId;
    private final String ownerName;
    private final GravePosition deathPosition;
    private final GravePosition gravePosition;
    private final long createdAt;
    private final long expiresAt;
    private final List<ItemStack> items;
    private int experience;
    private State state;
    private Disposition disposition;
    private ItemClaim pendingClaim;

    public Grave(UUID id, UUID ownerId, String ownerName, GravePosition deathPosition,
                 GravePosition gravePosition, List<ItemStack> items, int experience,
                 long createdAt, long expiresAt) {
        this(id, ownerId, ownerName, deathPosition, gravePosition, items, experience,
            createdAt, expiresAt, State.ACTIVE, Disposition.NONE);
    }

    public Grave(UUID id, UUID ownerId, String ownerName, GravePosition deathPosition,
                 GravePosition gravePosition, List<ItemStack> items, int experience,
                 long createdAt, long expiresAt, State state, Disposition disposition) {
        this.id = Objects.requireNonNull(id, "id");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.ownerName = Objects.requireNonNull(ownerName, "ownerName");
        this.deathPosition = Objects.requireNonNull(deathPosition, "deathPosition");
        this.gravePosition = Objects.requireNonNull(gravePosition, "gravePosition");
        this.items = cloneItems(items);
        this.experience = Math.max(0, experience);
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.state = Objects.requireNonNull(state, "state");
        this.disposition = Objects.requireNonNull(disposition, "disposition");
    }

    public static Grave create(UUID ownerId, String ownerName, GravePosition deathPosition,
                               GravePosition gravePosition, List<ItemStack> items, int experience, long now) {
        return new Grave(UUID.randomUUID(), ownerId, ownerName, deathPosition, gravePosition,
            items, experience, now, now + GraveConfig.GRAVE_LIFETIME_MS);
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getOwnerName() { return ownerName; }
    public GravePosition getDeathPosition() { return deathPosition; }
    public GravePosition getGravePosition() { return gravePosition; }
    public long getCreatedAt() { return createdAt; }
    public long getExpiresAt() { return expiresAt; }

    public synchronized List<ItemStack> getItems() { return cloneItems(items); }
    public synchronized int getExperience() { return experience; }
    public synchronized State getState() { return state; }
    public synchronized Disposition getDisposition() { return disposition; }
    public synchronized boolean hasPendingClaim() { return pendingClaim != null; }

    public synchronized ItemClaim claimItem(int index) {
        if (state != State.ACTIVE || pendingClaim != null || index < 0 || index >= items.size()) return null;
        pendingClaim = new ItemClaim(index, items.remove(index));
        return pendingClaim;
    }

    public synchronized boolean commitClaim(ItemClaim claim) {
        if (!sameClaim(claim)) return false;
        pendingClaim = null;
        return true;
    }

    public synchronized boolean rollbackClaim(ItemClaim claim) {
        if (!sameClaim(claim)) return false;
        items.add(Math.min(claim.index(), items.size()), claim.item());
        pendingClaim = null;
        return true;
    }

    public synchronized void restoreItem(int index, ItemStack item) {
        items.add(Math.min(Math.max(index, 0), items.size()), item.clone());
    }

    public synchronized boolean beginRemoval(Disposition removalDisposition) {
        if (state != State.ACTIVE || removalDisposition == Disposition.NONE) return false;
        if (pendingClaim != null && !(removalDisposition == Disposition.LOOTED && items.isEmpty())) return false;
        state = State.REMOVING;
        disposition = removalDisposition;
        return true;
    }

    public synchronized void cancelRemoval() {
        state = State.ACTIVE;
        disposition = Disposition.NONE;
    }

    public synchronized void markDisposed() { state = State.DISPOSED; }

    public synchronized int consumeExperience() {
        int claimed = experience;
        experience = 0;
        return claimed;
    }

    public synchronized void restoreExperience(int amount) { experience += Math.max(0, amount); }
    public synchronized boolean isEmpty() { return items.isEmpty(); }
    public synchronized int getStackCount() { return items.size(); }
    public boolean isExpired(long now) { return now >= expiresAt; }
    public long getRemainingMillis(long now) { return Math.max(0L, expiresAt - now); }

    public synchronized GraveSnapshot snapshot() {
        return new GraveSnapshot(id, ownerId, ownerName, deathPosition, gravePosition,
            GraveItemCodec.encode(items), experience, createdAt, expiresAt, state, disposition);
    }

    private boolean sameClaim(ItemClaim claim) {
        return pendingClaim != null && pendingClaim.index() == claim.index() && pendingClaim.item().equals(claim.item());
    }

    private static List<ItemStack> cloneItems(List<ItemStack> source) {
        List<ItemStack> copy = new ArrayList<>();
        if (source == null) return copy;
        for (ItemStack item : source) if (item != null && !item.isEmpty()) copy.add(item.clone());
        return copy;
    }

    @Override public boolean equals(Object object) { return object instanceof Grave grave && id.equals(grave.id); }
    @Override public int hashCode() { return id.hashCode(); }
}
