package mp.kenimon.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import mp.kenimon.Kenicompetitivo;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.UUID;

public class KenicompetitivoPlaceholders extends PlaceholderExpansion {

    private Kenicompetitivo plugin;

    public KenicompetitivoPlaceholders(Kenicompetitivo plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "kenicompetitivo";
    }

    @Override
    public String getAuthor() {
        return plugin.getDescription().getAuthors().get(0);
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Mantener cargado mientras el servidor esté activo
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) {
            return "";
        }

        FileConfiguration config = plugin.getConfigManager().getConfig();
        FileConfiguration messages = plugin.getConfigManager().getMessages();
        String currency = config.getString("currency_name", "trofeos");

        // Racha actual de kills
        if (identifier.equals("rachadekills")) {
            int streak = plugin.getCacheManager().getCachedKillStreak(player.getUniqueId());
            String format = messages.getString("placeholders.rachadekills", "Racha actual: %value%");
            return format.replace("%value%", String.valueOf(streak));
        }

        // Racha máxima de kills
        if (identifier.equals("rachadekillsmax")) {
            int maxStreak = plugin.getCacheManager().getCachedMaxKillStreak(player.getUniqueId());
            String format = messages.getString("placeholders.rachadekillsmax", "Racha máxima alcanzada: %value%");
            return format.replace("%value%", String.valueOf(maxStreak));
        }

        // Jugador con mayor racha (global)
        if (identifier.equals("rachadekillsglobal")) {
            UUID topStreakUUID = plugin.getDatabaseManager().getTopStreakPlayer();
            String playerName = "Nadie";
            int topStreak = 0;

            if (topStreakUUID != null) {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(topStreakUUID);
                playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Desconocido";
                topStreak = plugin.getDatabaseManager().getMaxKillStreak(topStreakUUID);
            }

            String format = messages.getString("placeholders.rachadekillsglobal", "Mayor racha global: %value%");
            return format.replace("%value%", playerName + " (" + topStreak + ")");
        }

        // Trofeos del jugador
        if (identifier.equals("caminodetrofeos")) {
            int trophies = plugin.getCacheManager().getCachedTrophies(player.getUniqueId());
            String format = messages.getString("placeholders.caminodetrofeos", "Tienes %value% %currency%.");
            return format.replace("%value%", String.valueOf(trophies))
                    .replace("%currency%", currency);
        }

        if (identifier.equals("racha")) {
            return String.valueOf(plugin.getCacheManager().getCachedKillStreak(player.getUniqueId()));
        }

        // Racha con formato (similar a rachadekills pero personalizable)
        if (identifier.equals("racha_formateada")) {
            int streak = plugin.getCacheManager().getCachedKillStreak(player.getUniqueId());
            String format = messages.getString("placeholders.racha_formateada", "&7Racha: &c%value%");
            return ChatColor.translateAlternateColorCodes('&', format.replace("%value%", String.valueOf(streak)));
        }

        // Jugador con más trofeos (global)
        if (identifier.equals("caminodetrofeosglobal")) {
            UUID topTrophiesUUID = plugin.getDatabaseManager().getTopTrophiesPlayer();
            String playerName = "Nadie";
            int topTrophies = 0;

            if (topTrophiesUUID != null) {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(topTrophiesUUID);
                playerName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Desconocido";
                topTrophies = plugin.getDatabaseManager().getTrophies(topTrophiesUUID);
            }

            String format = messages.getString("placeholders.caminodetrofeosglobal", "El mejor tiene %value% %currency%.");
            return format.replace("%value%", playerName + " (" + topTrophies + ")")
                    .replace("%currency%", currency);
        }

        if (identifier.equals("gems")) {
            if (!plugin.getGemManager().isEnabled()) return "0";
            int gems = plugin.getGemManager().getGems(player.getUniqueId());
            return String.valueOf(gems);
        }

        // Placeholder para gemas con formato
        if (identifier.equals("gems_formatted")) {
            if (!plugin.getGemManager().isEnabled()) return "&7Gemas: &b0";
            int gems = plugin.getGemManager().getGems(player.getUniqueId());
            String format = messages.getString("placeholders.gems_formatted", "&7Gemas: &b%value%");
            return ChatColor.translateAlternateColorCodes('&', format.replace("%value%", String.valueOf(gems)));
        }

        return null;
    }
}
