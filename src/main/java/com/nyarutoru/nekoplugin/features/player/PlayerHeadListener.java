package com.nyarutoru.nekoplugin.features.player;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.nyarutoru.nekoplugin.api.gui.AnvilTextInputGUI;
import com.nyarutoru.nekoplugin.utils.SchedulerUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Sneak while holding a carved pumpkin to trade levels for a player head with a custom skin. */
public final class PlayerHeadListener implements Listener {

    private static final String TEXTURES_HOST = "textures.minecraft.net";
    private static final String NAME_API = "https://api.mojang.com/users/profiles/minecraft/";
    private static final String PROFILE_API = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private int costLevels = 10;
    private int shiftCount = 10;
    private final Map<UUID, Integer> sneaks = new ConcurrentHashMap<>();

    public void setCostLevels(int costLevels) {
        this.costLevels = costLevels;
    }

    public void setShiftCount(int shiftCount) {
        this.shiftCount = shiftCount;
    }

    void resetSneaks() {
        sneaks.clear();
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        if (!isCarvedPumpkin(player.getInventory().getItemInMainHand())) {
            sneaks.remove(player.getUniqueId());
            return;
        }
        int count = sneaks.merge(player.getUniqueId(), 1, Integer::sum);
        if (count < shiftCount) return;
        sneaks.remove(player.getUniqueId());
        if (player.calculateTotalExperiencePoints() < xpCost()) {
            send(player, "You need at least " + xpCost() + " XP (" + costLevels + " levels) to carve a player head.", NamedTextColor.RED);
            return;
        }
        new AnvilTextInputGUI(Component.text("Player name or skin URL"), "",
                text -> onSubmit(player, text), () -> {
        }, false).open(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sneaks.remove(event.getPlayer().getUniqueId());
    }

    private void onSubmit(Player player, String input) {
        String value = input == null ? "" : input.trim();
        if (isTextureUrl(value)) {
            finish(player, UUID.nameUUIDFromBytes(("pumpkinhead:" + value).getBytes(StandardCharsets.UTF_8)),
                    "Custom Head", texturesValueFromUrl(value));
            return;
        }
        String namemc = namemcKey(value);
        if (namemc != null) {
            resolve(player, isUuid(namemc) ? "Custom Head" : namemc, namemc);
            return;
        }
        if (isValidName(value)) {
            resolve(player, value, value);
            return;
        }
        send(player, "Enter a player name, a NameMC link, or a textures.minecraft.net URL.", NamedTextColor.RED);
    }

    private void resolve(Player player, String headName, String key) {
        SchedulerUtils.runAsync(() -> {
            String texture = resolveTextureValue(key);
            SchedulerUtils.runAtPlayer(player, () -> {
                if (texture == null) {
                    send(player, "Could not fetch the skin for \"" + headName + "\".", NamedTextColor.RED);
                    return;
                }
                finish(player, isUuid(key) ? UUID.fromString(key) : offlineUuid(key), headName, texture);
            });
        });
    }

    private void finish(Player player, UUID uuid, String name, String textureValue) {
        if (!isCarvedPumpkin(player.getInventory().getItemInMainHand())) {
            send(player, "You are no longer holding a carved pumpkin.", NamedTextColor.RED);
            return;
        }
        int total = player.calculateTotalExperiencePoints();
        if (total < xpCost()) {
            send(player, "You need at least " + xpCost() + " XP (" + costLevels + " levels).", NamedTextColor.RED);
            return;
        }
        player.setLevel(0);
        player.setExp(0);
        player.giveExp(total - xpCost());
        ItemStack held = player.getInventory().getItemInMainHand();
        ItemStack head = createHead(uuid, name, textureValue);
        if (held.getAmount() > 1) {
            held.setAmount(held.getAmount() - 1);
            player.getInventory().setItemInMainHand(held);
            player.getInventory().addItem(head).values()
                    .forEach(extra -> player.getWorld().dropItem(player.getLocation(), extra));
        } else {
            player.getInventory().setItemInMainHand(head);
        }
        send(player, "Your carved pumpkin became a player head.", NamedTextColor.GREEN);
    }

    private static ItemStack createHead(UUID uuid, String name, String textureValue) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        PlayerProfile profile = Bukkit.createProfile(uuid, name);
        profile.setProperty(new ProfileProperty("textures", textureValue));
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);
        return head;
    }

    private int xpCost() {
        return xpToReachLevel(costLevels);
    }

    static int xpToReachLevel(int level) {
        if (level <= 16) return level * level + 6 * level;
        if (level <= 31) return (int) (2.5 * level * level - 40.5 * level + 360);
        return (int) (4.5 * level * level - 162.5 * level + 2220);
    }

    static boolean isCarvedPumpkin(ItemStack item) {
        Material type = item == null ? null : item.getType();
        return type == Material.PUMPKIN || type == Material.JACK_O_LANTERN;
    }

    static boolean isTextureUrl(String input) {
        try {
            URI uri = URI.create(input);
            String host = uri.getHost();
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && host != null && host.equalsIgnoreCase(TEXTURES_HOST);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    static String namemcKey(String input) {
        try {
            URI uri = URI.create(input);
            String host = uri.getHost();
            if (host == null || !(host.equalsIgnoreCase("namemc.com") || host.equalsIgnoreCase("www.namemc.com"))) {
                return null;
            }
            String path = uri.getPath();
            if (path == null) return null;
            for (String segment : path.split("/")) {
                int dot = segment.indexOf('.');
                if (dot > 0) {
                    String key = segment.substring(0, dot);
                    if (isValidName(key)) return key;
                } else if (isUuid(segment)) {
                    return segment;
                }
            }
        } catch (IllegalArgumentException ignored) {
            // not a URI
        }
        return null;
    }

    static boolean isUuid(String input) {
        return input.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }

    static boolean isValidName(String input) {
        if (input.length() < 3 || input.length() > 16) return false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_')) return false;
        }
        return true;
    }

    static String texturesValueFromUrl(String url) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}";
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    static String parseJsonString(String json, String key) {
        String marker = "\"" + key + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) return null;
        start += marker.length();
        int end = json.indexOf('"', start);
        return end < 0 ? null : json.substring(start, end);
    }

    static String resolveTextureValue(String nameOrUuid) {
        String id;
        if (isUuid(nameOrUuid)) {
            id = nameOrUuid.replace("-", "");
        } else {
            String body = fetch(NAME_API + nameOrUuid.toLowerCase(Locale.ROOT));
            id = body == null ? null : parseJsonString(body, "id");
        }
        if (id == null) return null;
        String body = fetch(PROFILE_API + id);
        if (body == null) return null;
        int marker = body.indexOf("\"name\":\"textures\"");
        if (marker < 0) return null;
        return parseJsonString(body.substring(marker), "value");
    }

    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
    }

    private static String fetch(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("User-Agent", "NekoPlugin")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 ? response.body() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void send(Player player, String message, NamedTextColor color) {
        player.sendMessage(Component.text(message, color));
    }
}
