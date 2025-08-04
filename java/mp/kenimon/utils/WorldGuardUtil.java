package mp.kenimon.utils;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;

import mp.kenimon.Kenicompetitivo;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WorldGuardUtil implements Listener {
    private final Kenicompetitivo plugin;
    private boolean worldGuardEnabled;

    // Flags personalizadas para detección de regiones
    private StateFlag noKenistreakFlag;
    private StateFlag noKenitrophiesFlag;

    // Lista de IDs de regiones excluidas
    private final List<String> excludedRegionIds = new ArrayList<>();

    public WorldGuardUtil(Kenicompetitivo plugin) {
        this.plugin = plugin;

        // Verificar si WorldGuard está disponible
        this.worldGuardEnabled = plugin.getServer().getPluginManager().getPlugin("WorldGuard") != null;

        if (worldGuardEnabled) {
            try {
                // IMPORTANTE: Registrar el listener
                plugin.getServer().getPluginManager().registerEvents(this, plugin);

                // Cargar configuración
                loadConfiguration();

                // Registrar flags personalizadas - CORREGIDO para hacerlo en el evento de inicio
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    registerCustomFlags();
                });

            } catch (Exception e) {
                e.printStackTrace();
                worldGuardEnabled = false;
            }
        } else {
        }
    }

    /**
     * Registra flags personalizadas en WorldGuard
     */
    private void registerCustomFlags() {
        try {
            FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();

            // IMPORTANTE: Registrar flags correctamente y manejar excepciones
            try {
                StateFlag noKenistreakFlagTemp = new StateFlag("nokenistreak", false);
                registry.register(noKenistreakFlagTemp);
                noKenistreakFlag = noKenistreakFlagTemp;
                plugin.getLogger().info("Registrada flag 'nokenistreak' en WorldGuard");
            } catch (Exception e) {
                Flag<?> existing = registry.get("nokenistreak");
                if (existing instanceof StateFlag) {
                    noKenistreakFlag = (StateFlag) existing;
                } else {
                }
            }

            try {
                StateFlag noKenitrophiesFlagTemp = new StateFlag("nokenitrophies", false);
                registry.register(noKenitrophiesFlagTemp);
                noKenitrophiesFlag = noKenitrophiesFlagTemp;
            } catch (Exception e) {
                Flag<?> existing = registry.get("nokenitrophies");
                if (existing instanceof StateFlag) {
                    noKenitrophiesFlag = (StateFlag) existing;
                } else {
                }
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Error al registrar flags de WorldGuard: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Carga la configuración de regiones excluidas
     */
    public void loadConfiguration() {
        excludedRegionIds.clear();
        if (plugin.getConfigManager().getConfig().contains("excluded_regions.region_ids")) {
            List<String> configRegionIds = plugin.getConfigManager().getConfig()
                    .getStringList("excluded_regions.region_ids");

            // CORREGIDO: Convertir todos los IDs a minúsculas para comparación sin distinguir mayúsculas/minúsculas
            for (String regionId : configRegionIds) {
                excludedRegionIds.add(regionId.toLowerCase());
            }

            plugin.getLogger().info("Cargadas " + excludedRegionIds.size() + " regiones excluidas: " +
                    String.join(", ", excludedRegionIds));
        }
    }

    /**
     * Verifica si un jugador está en una región donde no se deben acumular rachas ni trofeos
     */
    public boolean isInExcludedRegion(Player player) {
        if (!worldGuardEnabled || !plugin.getConfigManager().getConfig().getBoolean("excluded_regions.enabled", true)) {
            return false; // Si WorldGuard no está activo o la funcionalidad está deshabilitada
        }

        try {
            Location loc = player.getLocation();
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regionManager = container.get(BukkitAdapter.adapt(loc.getWorld()));

            if (regionManager == null) {
                return false; // No hay regiones en este mundo
            }

            // Obtener todas las regiones aplicables en esta ubicación
            BlockVector3 vector = BlockVector3.at(loc.getX(), loc.getY(), loc.getZ());
            ApplicableRegionSet regions = regionManager.getApplicableRegions(vector);

            // Verificar si alguna región tiene las flags activadas
            if (noKenistreakFlag != null && regions.testState(null, noKenistreakFlag)) {
                return true;
            }

            if (noKenitrophiesFlag != null && regions.testState(null, noKenitrophiesFlag)) {
                return true;
            }

            // Verificar regiones por ID
            for (ProtectedRegion region : regions) {
                if (excludedRegionIds.contains(region.getId().toLowerCase())) {
                    return true;
                }
            }

            return false;

        } catch (Exception e) {
            plugin.getLogger().warning("Error al verificar regiones WorldGuard: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Obtiene los nombres de todas las regiones WorldGuard
     */
    public Set<String> getAllRegionIds() {
        Set<String> result = new HashSet<>();

        if (!worldGuardEnabled) {
            return result;
        }

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();

            // Iterar por todos los mundos
            for (World world : plugin.getServer().getWorlds()) {
                RegionManager regionManager = container.get(BukkitAdapter.adapt(world));
                if (regionManager != null) {
                    result.addAll(regionManager.getRegions().keySet());
                }
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Error al obtener regiones WorldGuard: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Obtiene todas las regiones en un mundo específico
     */
    public Set<String> getRegionsInWorld(World world) {
        Set<String> result = new HashSet<>();

        if (!worldGuardEnabled) {
            return result;
        }

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regionManager = container.get(BukkitAdapter.adapt(world));

            if (regionManager != null) {
                result.addAll(regionManager.getRegions().keySet());
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Error al obtener regiones de mundo: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Verifica si una región existe en WorldGuard
     */
    public boolean regionExists(String regionId) {
        if (!worldGuardEnabled) {
            return false;
        }

        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();

            // Buscar en todos los mundos
            for (World world : plugin.getServer().getWorlds()) {
                RegionManager regionManager = container.get(BukkitAdapter.adapt(world));
                if (regionManager != null && regionManager.hasRegion(regionId)) {
                    return true;
                }
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Error al verificar región WorldGuard: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Obtiene las regiones en las que está un jugador
     */
    public List<String> getPlayerRegions(Player player) {
        List<String> result = new ArrayList<>();

        if (!worldGuardEnabled) {
            return result;
        }

        try {
            Location loc = player.getLocation();
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regionManager = container.get(BukkitAdapter.adapt(loc.getWorld()));

            if (regionManager == null) {
                return result;
            }

            BlockVector3 vector = BlockVector3.at(loc.getX(), loc.getY(), loc.getZ());
            ApplicableRegionSet regions = regionManager.getApplicableRegions(vector);

            for (ProtectedRegion region : regions) {
                result.add(region.getId());
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Error al obtener regiones del jugador: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    /**
     * Verifica si WorldGuard está disponible
     */
    public boolean isWorldGuardEnabled() {
        return worldGuardEnabled;
    }

    /**
     * Obtiene la lista de IDs de regiones excluidas
     */
    public List<String> getExcludedRegionIds() {
        return new ArrayList<>(excludedRegionIds);
    }

    /**
     * Añade una región a la lista de regiones excluidas
     */
    public boolean addExcludedRegion(String regionId) {
        // Verificar que la región existe
        if (!regionExists(regionId)) {
            return false;
        }

        // Convertir a minúsculas para consistencia
        String normalizedId = regionId.toLowerCase();

        // Verificar si ya está en la lista
        if (excludedRegionIds.contains(normalizedId)) {
            return false; // Ya está excluida
        }

        // Añadir a la lista
        excludedRegionIds.add(normalizedId);

        // Actualizar en la configuración
        List<String> configList = plugin.getConfigManager().getConfig().getStringList("excluded_regions.region_ids");
        configList.add(regionId); // Mantener el ID original en la config
        plugin.getConfigManager().getConfig().set("excluded_regions.region_ids", configList);
        plugin.getConfigManager().saveConfig();

        return true;
    }

    /**
     * Elimina una región de la lista de regiones excluidas
     */
    public boolean removeExcludedRegion(String regionId) {
        // Convertir a minúsculas para consistencia
        String normalizedId = regionId.toLowerCase();

        // Verificar si está en la lista
        if (!excludedRegionIds.contains(normalizedId)) {
            return false; // No está excluida
        }

        // Eliminar de la lista
        excludedRegionIds.remove(normalizedId);

        // Actualizar en la configuración (mantener mayúsculas/minúsculas originales)
        List<String> configList = plugin.getConfigManager().getConfig().getStringList("excluded_regions.region_ids");
        configList.removeIf(id -> id.equalsIgnoreCase(regionId));
        plugin.getConfigManager().getConfig().set("excluded_regions.region_ids", configList);
        plugin.getConfigManager().saveConfig();

        return true;
    }

    /**
     * Lista todas las regiones excluidas
     */
    public List<String> listExcludedRegions() {
        return new ArrayList<>(excludedRegionIds);
    }

    /**
     * Lista todas las regiones disponibles en todos los mundos
     */
    public List<String> listAllRegions() {
        if (!worldGuardEnabled) {
            return new ArrayList<>();
        }

        Set<String> allRegions = getAllRegionIds();
        return new ArrayList<>(allRegions);
    }
}
