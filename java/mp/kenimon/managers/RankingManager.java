package mp.kenimon.managers;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.DecentHolograms;
import eu.decentsoftware.holograms.api.DecentHologramsAPI;
import mp.kenimon.Kenicompetitivo;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RankingManager {

    private Kenicompetitivo plugin;
    private Map<String, List<RankingEntry>> cachedRankings;
    private boolean hologramsEnabled = false;

    public RankingManager(Kenicompetitivo plugin) {
        this.plugin = plugin;
        this.cachedRankings = new HashMap<>();

        // Verificar si DecentHolograms está disponible
        if (Bukkit.getPluginManager().getPlugin("DecentHolograms") != null) {
            hologramsEnabled = true;
        } else {
        }

        // Cargar configuraciones iniciales
        loadConfig();

        // Programar actualizaciones periódicas
        startUpdateTask();
    }

    private void loadConfig() {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        ConfigurationSection rankingsSection = config.getConfigurationSection("rankings");

        if (rankingsSection == null) {
            // Crear configuración por defecto si no existe
            config.createSection("rankings");
            config.set("rankings.update_interval", 300); // 5 minutos
            config.set("rankings.types.trophies.enabled", true);
            config.set("rankings.types.trophies.display_name", "&6Top Trofeos");
            config.set("rankings.types.trophies.limit", 10);
            config.set("rankings.types.killstreak.enabled", true);
            config.set("rankings.types.killstreak.display_name", "&cTop Racha");
            config.set("rankings.types.killstreak.limit", 10);

            // Crear sección para ubicaciones de hologramas
            config.createSection("rankings.holograms");
            config.createSection("rankings.signs");

            plugin.getConfigManager().saveConfig();
        }

        // Actualizar rankings al iniciar
        updateAllRankings();
    }

    private void startUpdateTask() {
        int interval = plugin.getConfigManager().getConfig().getInt("rankings.update_interval", 300);

        new BukkitRunnable() {
            @Override
            public void run() {
                updateAllRankings();
                updateAllDisplays();
            }
        }.runTaskTimerAsynchronously(plugin, interval * 20L, interval * 20L);
    }

    /**
     * Actualiza todos los rankings disponibles
     */
    public void updateAllRankings() {
        updateRanking("trophies", "Trofeos", "trophies", "DESC");

        // IMPORTANTE: Actualizamos la lista de killstreaks directamente desde la base de datos
        // para asegurar que sea en tiempo real y sin jugadores con racha 0
        updateRanking("killstreak", "Racha", "kill_streak", "DESC");

        // IMPORTANTE: Forzar actualización de displays inmediatamente
        updateAllDisplays();
    }

    public void updateAllDisplays() {
        // Usar runTask para asegurarnos de que esto corre en el hilo principal
        Bukkit.getScheduler().runTask(plugin, () -> {
            updateHolograms();
            updateSigns();
        });
    }

    /**
     * Actualiza un tipo específico de ranking
     */
    public void updateRanking(String type, String displayName, String dbColumn, String order) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        if (!config.getBoolean("rankings.types." + type + ".enabled", true)) {
            return;
        }

        int limit = config.getInt("rankings.types." + type + ".limit", 10);
        List<RankingEntry> rankings = new ArrayList<>();

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            
            // MODIFICADO: Para el tipo killstreak, usar kill_streak
            String columnToUse = type.equals("killstreak") ? "kill_streak" : dbColumn;
            
            String sql = buildRankingQuery(type, dbColumn, columnToUse, order, limit);
            
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                int position = 1;
                while (rs.next()) {
                    try {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        int value = rs.getInt(columnToUse);

                        // IMPORTANTE: Solo incluir jugadores con valores positivos
                        if (value > 0) {
                            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                            String name = player.getName();
                            if (name == null) name = "Desconocido";

                            RankingEntry entry = new RankingEntry(position, uuid, name, value);
                            rankings.add(entry);
                            position++;
                        }
                    } catch (IllegalArgumentException e) {
                        // UUID inválido, continuar con el siguiente
                        plugin.getLogger().warning("UUID inválido en ranking: " + rs.getString("uuid"));
                    }
                }

                // Actualizar caché
                cachedRankings.put(type, rankings);
            }

        } catch (SQLException e) {
            plugin.getLogger().warning("Error actualizando ranking " + type + ": " + e.getMessage());
        }
        
        // CRÍTICO: Mover updateHolograms fuera del try-with-resources 
        // y hacerlo asíncrono para no bloquear conexiones
        if (type.equals("killstreak")) {
            Bukkit.getScheduler().runTask(plugin, this::updateHolograms);
        }
    }

    /**
     * Versión asíncrona de updateRanking para evitar bloqueos del hilo principal
     */
    public void updateRankingAsync(String type, String displayName, String dbColumn, String order) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            updateRanking(type, displayName, dbColumn, order);
        });
    }
    
    /**
     * Construye la consulta SQL para el ranking optimizada
     */
    private String buildRankingQuery(String type, String dbColumn, String columnToUse, String order, int limit) {
        if (type.equals("killstreak")) {
            // CRÍTICO: Incluir también rachas de 1 kill, usar índice optimizado
            return "SELECT uuid, " + columnToUse + " FROM players WHERE " + columnToUse + " > 0 ORDER BY " + columnToUse + " " + order + " LIMIT " + limit;
        } else {
            return "SELECT uuid, " + dbColumn + " FROM players ORDER BY " + dbColumn + " " + order + " LIMIT " + limit;
        }
    }
    
    /**
     * Obtiene un ranking específico desde la caché
     */
    public List<RankingEntry> getRanking(String type) {
        if (!cachedRankings.containsKey(type)) {
            return new ArrayList<>();
        }
        return cachedRankings.get(type);
    }

    /**
     * Actualiza los hologramas de ranking con optimización
     */
    public void updateHolograms() {
        if (!hologramsEnabled) return;

        FileConfiguration config = plugin.getConfigManager().getConfig();
        ConfigurationSection hologramsSection = config.getConfigurationSection("rankings.holograms");

        if (hologramsSection == null) {
            return;
        }

        // Verificar si hay jugadores cerca antes de hacer actualizaciones
        boolean shouldUpdate = false;
        Map<String, Location> hologramLocations = new HashMap<>();

        // Primero recolectar todas las ubicaciones
        for (String key : hologramsSection.getKeys(false)) {
            String world = config.getString("rankings.holograms." + key + ".world");
            if (world == null) continue;

            org.bukkit.World bukkitWorld = Bukkit.getWorld(world);
            if (bukkitWorld == null) continue;

            int x = config.getInt("rankings.holograms." + key + ".x");
            int y = config.getInt("rankings.holograms." + key + ".y");
            int z = config.getInt("rankings.holograms." + key + ".z");

            Location loc = new Location(bukkitWorld, x + 0.5, y + 0.5, z + 0.5);
            hologramLocations.put(key, loc);
        }

        // Verificar si hay jugadores cerca de algún holograma
        int updateDistance = config.getInt("rankings.holograms.update_distance", 50);
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location playerLoc = player.getLocation();
            for (Location loc : hologramLocations.values()) {
                if (playerLoc.getWorld().equals(loc.getWorld()) &&
                        playerLoc.distance(loc) <= updateDistance) {
                    shouldUpdate = true;
                    break;
                }
            }
            if (shouldUpdate) break;
        }

        // Si no hay jugadores cerca, no hacer nada
        if (!shouldUpdate && !hologramLocations.isEmpty()) {
            return;
        }

        // Estructura para almacenar la información de actualización
        class HologramUpdate {
            String id;
            Location location;
            List<String> lines;

            HologramUpdate(String id, Location location, List<String> lines) {
                this.id = id;
                this.location = location;
                this.lines = lines;
            }
        }

        // Lista para almacenar todas las actualizaciones
        List<HologramUpdate> updates = new ArrayList<>();

        // Preparar datos para actualización
        for (Map.Entry<String, Location> entry : hologramLocations.entrySet()) {
            String key = entry.getKey();
            Location location = entry.getValue();

            try {
                String type = config.getString("rankings.holograms." + key + ".type", "trophies");
                String holoId = "keni_" + key;

                // Preparar las líneas del holograma
                List<String> lines = new ArrayList<>();

                // Título personalizado desde la configuración
                String defaultTitle = "&6Top " + type;
                String title;
                if (config.contains("holograms_format." + type + ".header")) {
                    title = config.getString("holograms_format." + type + ".header", defaultTitle);
                } else {
                    title = config.getString("rankings.types." + type + ".display_name", defaultTitle);
                }
                lines.add(ChatColor.translateAlternateColorCodes('&', title));

                // Contenido
                List<RankingEntry> ranking = getRanking(type);

                if (ranking.isEmpty()) {
                    lines.add(ChatColor.RED + "No hay datos disponibles");
                } else {
                    String defaultFormat = "&e#{position} &f{player} &7- &6{value}";
                    String playerFormat = config.getString("holograms_format." + type + ".player_format", defaultFormat);

                    int count = 0;
                    int displayLimit = config.getInt("rankings.types." + type + ".display_limit", 10);

                    for (RankingEntry entry2 : ranking) {
                        if (count >= displayLimit) break;

                        String format = playerFormat;
                        // Aplicar colores especiales para las primeras posiciones
                        if (entry2.getPosition() == 1 && config.contains("holograms_format." + type + ".colors.first")) {
                            format = config.getString("holograms_format." + type + ".colors.first", "&e&l") +
                                    format.replace("&e#{position}", "#{position}");
                        } else if (entry2.getPosition() == 2 && config.contains("holograms_format." + type + ".colors.second")) {
                            format = config.getString("holograms_format." + type + ".colors.second", "&7&l") +
                                    format.replace("&e#{position}", "#{position}");
                        } else if (entry2.getPosition() == 3 && config.contains("holograms_format." + type + ".colors.third")) {
                            format = config.getString("holograms_format." + type + ".colors.third", "&6&l") +
                                    format.replace("&e#{position}", "#{position}");
                        }

                        // Reemplazar placeholders
                        format = format.replace("{position}", String.valueOf(entry2.getPosition()))
                                .replace("{player}", entry2.getPlayerName())
                                .replace("{value}", String.valueOf(entry2.getValue()));

                        lines.add(ChatColor.translateAlternateColorCodes('&', format));
                        count++;
                    }
                }

                // Añadir esta actualización a la lista
                updates.add(new HologramUpdate(holoId, location, lines));

            } catch (Exception e) {
                // Excepción silenciada
            }
        }

        // Procesar todas las actualizaciones en un solo batch en el hilo principal
        if (!updates.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (HologramUpdate update : updates) {
                    try {
                        // Eliminar el holograma anterior si existe
                        if (DHAPI.getHologram(update.id) != null) {
                            DHAPI.removeHologram(update.id);
                        }

                        // Crear el holograma con las líneas preparadas
                        DHAPI.createHologram(update.id, update.location, update.lines);
                    } catch (Exception e) {
                        // Excepción silenciada
                    }
                }
            });
        }
    }

    /**
     * Actualiza los letreros de rankings
     */
    public void updateSigns() {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        ConfigurationSection signsSection = config.getConfigurationSection("rankings.signs");

        if (signsSection == null) {
            return;
        }

        for (String key : signsSection.getKeys(false)) {
            final String type = config.getString("rankings.signs." + key + ".type", "trophies");
            final String world = config.getString("rankings.signs." + key + ".world");
            final int x = config.getInt("rankings.signs." + key + ".x");
            final int y = config.getInt("rankings.signs." + key + ".y");
            final int z = config.getInt("rankings.signs." + key + ".z");
            final int position = config.getInt("rankings.signs." + key + ".position", 1);

            if (world == null) continue;

            // Actualizar letrero - debe hacerse en el hilo principal
            new BukkitRunnable() {
                @Override
                public void run() {
                    updateSign(type, world, x, y, z, position);
                }
            }.runTask(plugin);
        }
    }

    /**
     * Actualiza un letrero de ranking
     */
    private void updateSign(String type, String world, int x, int y, int z, int position) {
        org.bukkit.World bukkitWorld = Bukkit.getWorld(world);
        if (bukkitWorld == null) return;

        Block block = bukkitWorld.getBlockAt(x, y, z);
        if (!(block.getState() instanceof Sign)) {
            return;
        }

        Sign sign = (Sign) block.getState();
        List<RankingEntry> ranking = getRanking(type);

        // Configurar letrero
        String displayType = plugin.getConfigManager().getConfig().getString("rankings.types." + type + ".display_name", type);
        displayType = ChatColor.translateAlternateColorCodes('&', displayType);

        sign.setLine(0, ChatColor.DARK_BLUE + "[Ranking]");
        sign.setLine(1, displayType);

        if (ranking.size() >= position) {
            RankingEntry entry = ranking.get(position - 1);
            sign.setLine(2, ChatColor.YELLOW + "#" + position + " " + entry.getPlayerName());
            sign.setLine(3, ChatColor.GOLD.toString() + entry.getValue());
        } else {
            sign.setLine(2, ChatColor.RED + "No hay datos");
            sign.setLine(3, "");
        }

        sign.update();
    }

    /**
     * Añade un nuevo holograma en la ubicación del jugador
     */
    public void addHologram(Player player, String type) {
        if (!hologramsEnabled) {
            player.sendMessage(ChatColor.RED + "DecentHolograms no está disponible.");
            return;
        }

        FileConfiguration config = plugin.getConfigManager().getConfig();
        ConfigurationSection section = config.getConfigurationSection("rankings.holograms");

        if (section == null) {
            section = config.createSection("rankings.holograms");
        }

        // Generar ID único
        String id = "holo_" + System.currentTimeMillis();

        // Guardar ubicación
        Location loc = player.getLocation();
        config.set("rankings.holograms." + id + ".type", type);
        config.set("rankings.holograms." + id + ".world", loc.getWorld().getName());
        config.set("rankings.holograms." + id + ".x", loc.getBlockX());
        config.set("rankings.holograms." + id + ".y", loc.getBlockY() + 2); // +2 para que esté sobre la cabeza
        config.set("rankings.holograms." + id + ".z", loc.getBlockZ());

        plugin.getConfigManager().saveConfig();

        // Actualizar hologramas
        updateAllRankings();
        updateHolograms();

        player.sendMessage(ChatColor.GREEN + "Holograma de ranking creado correctamente.");
    }

    /**
     * Añade un nuevo letrero de ranking en la ubicación marcada
     */
    public void registerSign(Block block, Player player, String type, int position) {
        if (!(block.getState() instanceof Sign)) {
            player.sendMessage(ChatColor.RED + "El bloque seleccionado no es un letrero.");
            return;
        }

        FileConfiguration config = plugin.getConfigManager().getConfig();
        ConfigurationSection section = config.getConfigurationSection("rankings.signs");

        if (section == null) {
            section = config.createSection("rankings.signs");
        }

        // Generar ID único
        String id = "sign_" + System.currentTimeMillis();

        // Guardar ubicación
        Location loc = block.getLocation();
        config.set("rankings.signs." + id + ".type", type);
        config.set("rankings.signs." + id + ".world", loc.getWorld().getName());
        config.set("rankings.signs." + id + ".x", loc.getBlockX());
        config.set("rankings.signs." + id + ".y", loc.getBlockY());
        config.set("rankings.signs." + id + ".z", loc.getBlockZ());
        config.set("rankings.signs." + id + ".position", position);

        plugin.getConfigManager().saveConfig();

        // Actualizar letrero
        updateAllRankings();
        updateSign(type, loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), position);

        player.sendMessage(ChatColor.GREEN + "Letrero de ranking registrado correctamente.");
    }

    /**
     * Clase para representar una entrada en el ranking
     */
    public static class RankingEntry {
        private int position;
        private UUID uuid;
        private String playerName;
        private int value;

        public RankingEntry(int position, UUID uuid, String playerName, int value) {
            this.position = position;
            this.uuid = uuid;
            this.playerName = playerName;
            this.value = value;
        }

        public int getPosition() {
            return position;
        }

        public UUID getUUID() {
            return uuid;
        }

        public String getPlayerName() {
            return playerName;
        }

        public int getValue() {
            return value;
        }
    }
    /**
     * Lista todos los hologramas de ranking para un jugador
     */
    public void listHolograms(Player player) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        ConfigurationSection hologramsSection = config.getConfigurationSection("rankings.holograms");

        if (hologramsSection == null || hologramsSection.getKeys(false).isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "No hay hologramas configurados.");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "=== Hologramas de Ranking ===");
        for (String key : hologramsSection.getKeys(false)) {
            String type = config.getString("rankings.holograms." + key + ".type", "desconocido");
            String world = config.getString("rankings.holograms." + key + ".world", "desconocido");
            int x = config.getInt("rankings.holograms." + key + ".x");
            int y = config.getInt("rankings.holograms." + key + ".y");
            int z = config.getInt("rankings.holograms." + key + ".z");

            player.sendMessage(ChatColor.AQUA + "ID: " + key +
                    ChatColor.WHITE + " - Tipo: " + type +
                    ChatColor.GRAY + " (" + world + ": " + x + ", " + y + ", " + z + ")");
        }
    }

    /**
     * Elimina el holograma de ranking más cercano al jugador
     */
    public boolean removeNearestHologram(Player player) {
        if (!hologramsEnabled) return false;

        FileConfiguration config = plugin.getConfigManager().getConfig();
        ConfigurationSection hologramsSection = config.getConfigurationSection("rankings.holograms");

        if (hologramsSection == null) {
            return false;
        }

        String closestId = null;
        double closestDistance = Double.MAX_VALUE;

        // Buscar el holograma más cercano
        for (String key : hologramsSection.getKeys(false)) {
            String worldName = config.getString("rankings.holograms." + key + ".world");
            if (!player.getWorld().getName().equals(worldName)) continue;

            int x = config.getInt("rankings.holograms." + key + ".x");
            int y = config.getInt("rankings.holograms." + key + ".y");
            int z = config.getInt("rankings.holograms." + key + ".z");

            Location holoLoc = new Location(player.getWorld(), x + 0.5, y + 0.5, z + 0.5);
            double distance = player.getLocation().distance(holoLoc);

            if (distance < closestDistance && distance <= 10) { // Máximo 10 bloques de distancia
                closestDistance = distance;
                closestId = key;
            }
        }

        if (closestId != null) {
            // Eliminar holograma de DecentHolograms
            String holoId = "keni_" + closestId;
            if (Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
                DHAPI.removeHologram(holoId);
            }

            // Eliminar de la configuración
            config.set("rankings.holograms." + closestId, null);
            plugin.getConfigManager().saveConfig();
            return true;
        }

        return false;
    }

    /**
     * Elimina un letrero de ranking
     */
    public boolean removeSign(Block block) {
        if (!(block.getState() instanceof Sign)) {
            return false;
        }

        Location loc = block.getLocation();
        FileConfiguration config = plugin.getConfigManager().getConfig();
        ConfigurationSection signsSection = config.getConfigurationSection("rankings.signs");

        if (signsSection == null) {
            return false;
        }

        String toRemove = null;

        for (String key : signsSection.getKeys(false)) {
            String world = config.getString("rankings.signs." + key + ".world");
            int x = config.getInt("rankings.signs." + key + ".x");
            int y = config.getInt("rankings.signs." + key + ".y");
            int z = config.getInt("rankings.signs." + key + ".z");

            if (loc.getWorld().getName().equals(world) &&
                    loc.getBlockX() == x &&
                    loc.getBlockY() == y &&
                    loc.getBlockZ() == z) {
                toRemove = key;
                break;
            }
        }

        if (toRemove != null) {
            config.set("rankings.signs." + toRemove, null);
            plugin.getConfigManager().saveConfig();
            return true;
        }

        return false;
    }

    public void moveHologram(Player player, String type) {
        if (!hologramsEnabled) {
            player.sendMessage(ChatColor.RED + "DecentHolograms no está disponible.");
            return;
        }

        // Buscar hologramas cercanos del tipo especificado
        FileConfiguration config = plugin.getConfigManager().getConfig();
        ConfigurationSection hologramsSection = config.getConfigurationSection("rankings.holograms");

        if (hologramsSection == null) {
            player.sendMessage(ChatColor.RED + "No hay hologramas configurados.");
            return;
        }

        String closestId = null;
        double closestDistance = Double.MAX_VALUE;

        for (String key : hologramsSection.getKeys(false)) {
            if (config.getString("rankings.holograms." + key + ".type").equals(type)) {
                String worldName = config.getString("rankings.holograms." + key + ".world");
                if (!player.getWorld().getName().equals(worldName)) continue;

                int x = config.getInt("rankings.holograms." + key + ".x");
                int y = config.getInt("rankings.holograms." + key + ".y");
                int z = config.getInt("rankings.holograms." + key + ".z");

                Location holoLoc = new Location(player.getWorld(), x + 0.5, y + 0.5, z + 0.5);
                double distance = player.getLocation().distance(holoLoc);

                if (distance < closestDistance && distance <= 10) {
                    closestDistance = distance;
                    closestId = key;
                }
            }
        }

        if (closestId == null) {
            player.sendMessage(ChatColor.RED + "No se encontraron hologramas cercanos del tipo: " + type);
            return;
        }

        // Mover el holograma a la ubicación actual del jugador
        Location loc = player.getLocation();
        config.set("rankings.holograms." + closestId + ".world", loc.getWorld().getName());
        config.set("rankings.holograms." + closestId + ".x", loc.getBlockX());
        config.set("rankings.holograms." + closestId + ".y", loc.getBlockY() + 2);
        config.set("rankings.holograms." + closestId + ".z", loc.getBlockZ());
        plugin.getConfigManager().saveConfig();

        // Actualizar hologramas
        updateAllRankings();
        updateHolograms();

        player.sendMessage(ChatColor.GREEN + "Holograma movido correctamente.");
    }
}
