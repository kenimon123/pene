package mp.kenimon.managers;

import mp.kenimon.Kenicompetitivo;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PanelManager {

    private Kenicompetitivo plugin;
    private final int REWARDS_PER_PAGE = 21; // Número máximo de recompensas por página

    public PanelManager(Kenicompetitivo plugin) {
        this.plugin = plugin;
    }

    /**
     * Abre una página específica del panel de trofeos para un jugador
     * @param player El jugador
     * @param page Número de página (empezando por 0)
     */
    public void openPanel(Player player, int page) {
        player.openInventory(buildPlayerPanel(player, page));
    }

    /**
     * Construye el panel de trofeos según la configuración
     */
    public Inventory buildPlayerPanel(Player player, int page) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        FileConfiguration messages = plugin.getConfigManager().getMessages();

        // Obtener detalles de la configuración
        String title = ChatColor.translateAlternateColorCodes('&',
                config.getString("panel.title", "&6Camino de Trofeos"));

        // Agregar número de página si hay más de una
        int totalPages = calculateTotalPages();
        if (totalPages > 1) {
            String pageFormat = messages.getString("trophies_path.page", "&ePágina {current}/{total}");
            pageFormat = pageFormat.replace("{current}", String.valueOf(page + 1))
                    .replace("{total}", String.valueOf(totalPages));
            title += " - " + ChatColor.translateAlternateColorCodes('&', pageFormat);
        }

        // Crear inventario según la configuración
        int rows = config.getInt("panel.rows", 6);
        Inventory inventory = Bukkit.createInventory(null, rows * 9, title);

        // Añadir decoración si está habilitada
        if (config.getBoolean("panel.decoration.enabled", true)) {
            List<Integer> positions = config.getIntegerList("panel.decoration.positions");
            String itemName = config.getString("panel.decoration.name", " ");
            Material decorMaterial = Material.matchMaterial(
                    config.getString("panel.decoration.item", "GRAY_STAINED_GLASS_PANE"));

            if (decorMaterial == null) decorMaterial = Material.GRAY_STAINED_GLASS_PANE;

            ItemStack decorItem = new ItemStack(decorMaterial);
            ItemMeta meta = decorItem.getItemMeta();
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', itemName));
            decorItem.setItemMeta(meta);

            for (int slot : positions) {
                if (slot < inventory.getSize()) {
                    inventory.setItem(slot, decorItem);
                }
            }
        }

        // Añadir información del jugador si está habilitada
        if (config.getBoolean("panel.player_info.enabled", true)) {
            int position = config.getInt("panel.player_info.position", 4);
            String materialStr = config.getString("panel.player_info.item", "PLAYER_HEAD");
            Material material = Material.matchMaterial(materialStr);
            if (material == null) material = Material.PLAYER_HEAD;

            ItemStack playerInfo = new ItemStack(material);

            // Si es una cabeza de jugador, establecer al jugador como dueño
            if (material == Material.PLAYER_HEAD) {
                SkullMeta skullMeta = (SkullMeta) playerInfo.getItemMeta();
                skullMeta.setOwningPlayer(player);
                playerInfo.setItemMeta(skullMeta);
            }

            // Establecer meta
            ItemMeta meta = playerInfo.getItemMeta();

            // Nombre personalizado con placeholders
            String nameFormat = config.getString("panel.player_info.name", "&e{player}");
            nameFormat = nameFormat.replace("{player}", player.getName());
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', nameFormat));

            // Lore con información del jugador y placeholders
            List<String> loreConfig = config.getStringList("panel.player_info.lore");
            List<String> lore = new ArrayList<>();

            int trophies = plugin.getCacheManager().getCachedTrophies(player.getUniqueId());
            int streak = plugin.getCacheManager().getCachedKillStreak(player.getUniqueId());
            int maxStreak = plugin.getDatabaseManager().getMaxKillStreak(player.getUniqueId());

            for (String line : loreConfig) {
                line = line.replace("{trophies}", String.valueOf(trophies))
                        .replace("{streak}", String.valueOf(streak))
                        .replace("{max_streak}", String.valueOf(maxStreak));
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }

            meta.setLore(lore);
            playerInfo.setItemMeta(meta);

            inventory.setItem(position, playerInfo);
        }

        // NUEVO: Integración del camino y las recompensas
        addIntegratedPathAndRewards(inventory, player, page);

        // Añadir botones de navegación si hay múltiples páginas
        if (totalPages > 1) {
            addNavigationButtons(inventory, page, totalPages);
        }

        // Añadir botón para cerrar en la esquina inferior izquierda
        addCloseButton(inventory);

        return inventory;
    }

    /**
     * Añade el camino de progresión integrado con las recompensas
     */
    private void addIntegratedPathAndRewards(Inventory inventory, Player player, int page) {
        FileConfiguration config = plugin.getConfigManager().getConfig();

        // Obtener trofeos del jugador
        int playerTrophies = plugin.getCacheManager().getCachedTrophies(player.getUniqueId());

        // Configuración del camino
        int trophiesPerBlock = config.getInt("panel.path.trophies_per_block", 10);
        String notReachedMaterialStr = config.getString("panel.path.not_reached_material", "BLACK_STAINED_GLASS_PANE");
        String reachedMaterialStr = config.getString("panel.path.reached_material", "LIME_STAINED_GLASS_PANE");

        Material notReachedMaterial = Material.matchMaterial(notReachedMaterialStr);
        Material reachedMaterial = Material.matchMaterial(reachedMaterialStr);

        if (notReachedMaterial == null) notReachedMaterial = Material.BLACK_STAINED_GLASS_PANE;
        if (reachedMaterial == null) reachedMaterial = Material.LIME_STAINED_GLASS_PANE;

        // Patrón de gusanito personalizado
        List<Integer> pathPattern = new ArrayList<>();

        // Columna 1 (bajando desde slot 10 hasta 37)
        pathPattern.add(10); // Primera columna, primera fila
        pathPattern.add(19); // Primera columna, segunda fila
        pathPattern.add(28); // Primera columna, tercera fila
        pathPattern.add(37); // Primera columna, cuarta fila

        // Slots horizontales 38 y 39
        pathPattern.add(38); // Segunda columna, cuarta fila
        pathPattern.add(39); // Tercera columna, cuarta fila

        // Subiendo al slot 12
        pathPattern.add(30); // Tercera columna, tercera fila
        pathPattern.add(21); // Tercera columna, segunda fila
        pathPattern.add(12); // Tercera columna, primera fila

        // Slots horizontales 13 y 14
        pathPattern.add(13); // Cuarta columna, primera fila
        pathPattern.add(14); // Quinta columna, primera fila

        // Bajando al slot 41
        pathPattern.add(23); // Quinta columna, segunda fila
        pathPattern.add(32); // Quinta columna, tercera fila
        pathPattern.add(41); // Quinta columna, cuarta fila

        // Slots horizontales 42 y 43
        pathPattern.add(42); // Sexta columna, cuarta fila
        pathPattern.add(43); // Séptima columna, cuarta fila

        // Subiendo al slot 16
        pathPattern.add(34); // Séptima columna, tercera fila
        pathPattern.add(25); // Séptima columna, segunda fila
        pathPattern.add(16); // Séptima columna, primera fila

        // Se puede continuar si necesitas más slots...

        // Máximo de trofeos
        int maxTotalTrophies = config.getInt("max_total_trophies", 10000);

        // Número de bloques por página
        int blocksPerPage = pathPattern.size();

        // Índice del primer bloque para esta página
        int startBlockIndex = page * blocksPerPage;

        // Cargar todas las recompensas
        List<Map<String, Object>> rewards = new ArrayList<>();

        if (config.isList("panel.rewards")) {
            List<?> rewardsList = config.getList("panel.rewards");
            if (rewardsList != null) {
                for (Object obj : rewardsList) {
                    if (obj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>) obj;
                        rewards.add(map);
                    }
                }
            }
        }

        // Procesar cada posición del camino para esta página
        for (int i = 0; i < blocksPerPage; i++) {
            int blockIndex = startBlockIndex + i;

            // Asegurar que no exceda el número máximo de bloques
            int totalBlocks = (int) Math.ceil((double) maxTotalTrophies / trophiesPerBlock);
            if (blockIndex >= totalBlocks) break;

            // Obtener el slot para este bloque
            if (i >= pathPattern.size()) break;
            int slot = pathPattern.get(i);

            // Verificar que el slot es válido
            if (slot < 0 || slot >= inventory.getSize()) continue;

            // Calcular los trofeos para este bloque
            int blockTrophies = (blockIndex + 1) * trophiesPerBlock;

            // Buscar si existe una recompensa para este umbral exacto
            Map<String, Object> matchingReward = null;
            for (Map<String, Object> reward : rewards) {
                Object requiredObj = reward.get("required");
                if (requiredObj != null) {
                    int required = Integer.parseInt(requiredObj.toString());
                    if (required == blockTrophies) {
                        matchingReward = reward;
                        break;
                    }
                }
            }

            // Si hay una recompensa para este umbral, mostrarla
            if (matchingReward != null) {
                ItemStack rewardItem = createRewardItem(matchingReward, player);
                inventory.setItem(slot, rewardItem);
            } else {
                // Si no hay recompensa, mostrar un bloque del camino
                boolean reached = playerTrophies >= blockTrophies;
                Material material = reached ? reachedMaterial : notReachedMaterial;

                ItemStack pathBlock = new ItemStack(material);
                ItemMeta meta = pathBlock.getItemMeta();

                // Nombre del bloque
                String blockName = config.getString("panel.path.block_name", "&e{trophies} Trofeos")
                        .replace("{trophies}", String.valueOf(blockTrophies));
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', blockName));

                // Lore del bloque
                List<String> lore = new ArrayList<>();
                List<String> baseLore = reached ?
                        config.getStringList("panel.path.reached_lore") :
                        config.getStringList("panel.path.not_reached_lore");

                for (String line : baseLore) {
                    line = line.replace("{trophies}", String.valueOf(blockTrophies))
                            .replace("{remaining}", String.valueOf(Math.max(0, blockTrophies - playerTrophies)));
                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }

                meta.setLore(lore);
                pathBlock.setItemMeta(meta);

                inventory.setItem(slot, pathBlock);
            }
        }
    }

    /**
     * Crea un ItemStack para una recompensa basado en un mapa de configuración
     */
    private ItemStack createRewardItem(Map<String, Object> rewardMap, Player player) {
        // AÑADIDO: Comprobación de nulo para evitar NullPointerException
        if (rewardMap == null) {
            // Crear un elemento predeterminado si el mapa es nulo
            ItemStack defaultItem = new ItemStack(Material.BARRIER);
            ItemMeta meta = defaultItem.getItemMeta();
            meta.setDisplayName(ChatColor.RED + "Error en la recompensa");
            defaultItem.setItemMeta(meta);
            return defaultItem;
        }

        // MODIFICADO: Comprobar cada clave antes de acceder a ella
        int threshold = rewardMap.containsKey("required") ? Integer.parseInt(rewardMap.get("required").toString()) : 0;
        String itemStr = rewardMap.containsKey("item") ? rewardMap.get("item").toString() : "BARRIER";
        String nameStr = rewardMap.containsKey("name") ? rewardMap.get("name").toString() : "&cRecompensa";
        String description = rewardMap.containsKey("description") ? rewardMap.get("description").toString() : "Sin descripción";
        String command = rewardMap.containsKey("command") ? rewardMap.get("command").toString() : "";

        // MODIFICADO: Comprobar si existe la clave "effect"
        String effectType = rewardMap.containsKey("effect") ? rewardMap.get("effect").toString() : "";

        // Nuevos campos opcionales
        List<String> customLore = new ArrayList<>();
        if (rewardMap.containsKey("lore")) {
            if (rewardMap.get("lore") instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> configLore = (List<String>) rewardMap.get("lore");
                customLore.addAll(configLore);
            }
        }

        // Comandos múltiples (opcional)
        List<String> commandsList = new ArrayList<>();
        if (rewardMap.containsKey("commands") && rewardMap.get("commands") instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> configCommands = (List<String>) rewardMap.get("commands");
            commandsList.addAll(configCommands);
        } else if (command != null && !command.isEmpty()) {
            commandsList.add(command);
        }

        // Obtener trofeos del jugador
        int playerTrophies = plugin.getCacheManager().getCachedTrophies(player.getUniqueId());

        // Verificar si el jugador ya reclamó esta recompensa
        boolean alreadyClaimed = plugin.getRewardManager().hasClaimedReward(player.getUniqueId(), String.valueOf(threshold));

        // Determinar estado: disponible, ya reclamado o bloqueado
        FileConfiguration config = plugin.getConfigManager().getConfig();
        String statusKey;
        Material material = Material.matchMaterial(itemStr);

        if (material == null) material = Material.BARRIER;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (alreadyClaimed) {
            statusKey = "panel.reward_item.claimed";
        } else if (playerTrophies >= threshold) {
            statusKey = "panel.reward_item.available";
        } else {
            statusKey = "panel.reward_item.unavailable";
        }

        // Obtener formato de nombre según estado
        String nameFormat = config.getString(statusKey + ".name", "&6Recompensa: {threshold} Trofeos");
        nameFormat = nameFormat.replace("{threshold}", String.valueOf(threshold))
                .replace("{reward_name}", nameStr)
                .replace("{reward_description}", description);

        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', nameFormat));

        // Preparar lore
        List<String> lore = new ArrayList<>();

        // Añadir lore personalizado si existe
        if (!customLore.isEmpty()) {
            for (String line : customLore) {
                line = line.replace("{threshold}", String.valueOf(threshold))
                        .replace("{player}", player.getName())
                        .replace("{trophies}", String.valueOf(playerTrophies))
                        .replace("{remaining}", String.valueOf(Math.max(0, threshold - playerTrophies)))
                        .replace("{reward_description}", description);

                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }

            lore.add(""); // Línea separadora
        }

        // Obtener el lore configurado
        List<String> configuredLore = config.getStringList(statusKey + ".lore");

        // Cálculo para barra de progreso
        int percent = (int)(((double)playerTrophies / threshold) * 100);
        int barSize = 20;
        int filledBars = (int)(((double)playerTrophies / threshold) * barSize);

        StringBuilder progressBar = new StringBuilder("&8[");
        for (int i = 0; i < barSize; i++) {
            if (i < filledBars) {
                progressBar.append("&a|");
            } else {
                progressBar.append("&7|");
            }
        }
        progressBar.append("&8]");

        for (String line : configuredLore) {
            line = line.replace("{threshold}", String.valueOf(threshold))
                    .replace("{reward_description}", description)
                    .replace("{remaining}", String.valueOf(Math.max(0, threshold - playerTrophies)))
                    .replace("{progress_percent}", String.valueOf(percent))
                    .replace("{progress_bar}", progressBar.toString());

            // NUEVO: Añadir el display del tipo de efecto
            if (line.contains("{effect_display}") && effectType != null && !effectType.isEmpty()) {
                // Intentar obtener el nombre traducido desde reward_effects
                String effectDisplay = "&7" + effectType; // Valor por defecto
                if (config.contains("reward_effects." + effectType.toLowerCase() + ".name")) {
                    effectDisplay = config.getString("reward_effects." + effectType.toLowerCase() + ".name", effectDisplay);
                }
                line = line.replace("{effect_display}", effectDisplay);
            }

            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }

        meta.setLore(lore);

        // Añadir datos persistentes para identificar la recompensa
        NamespacedKey rewardKey = new NamespacedKey(plugin, "reward_threshold");
        meta.getPersistentDataContainer().set(rewardKey, PersistentDataType.STRING, String.valueOf(threshold));

        // Guardar comandos a ejecutar cuando se reclame
        if (!commandsList.isEmpty()) {
            NamespacedKey commandKey = new NamespacedKey(plugin, "reward_command");
            meta.getPersistentDataContainer().set(commandKey, PersistentDataType.STRING, String.join("||", commandsList));
        }

        // Opcional: Añadir efecto al reclamar
        if (effectType != null && !effectType.isEmpty()) {
            NamespacedKey effectKey = new NamespacedKey(plugin, "reward_effect");
            meta.getPersistentDataContainer().set(effectKey, PersistentDataType.STRING, effectType);
        }

        // Añadir brillo si está disponible
        if (!alreadyClaimed && playerTrophies >= threshold) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Añade un botón para la siguiente página en la esquina inferior derecha
     */
    private void addNextPageButton(Inventory inventory, int currentPage) {
        FileConfiguration config = plugin.getConfigManager().getConfig();

        // Posición en la esquina inferior derecha
        int position = inventory.getSize() - 1;

        Material material = Material.ARROW;
        ItemStack nextButton = new ItemStack(material);
        ItemMeta meta = nextButton.getItemMeta();

        String name = "&aSiguiente Página";
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.translateAlternateColorCodes('&', "&7Haz clic para ir a la"));
        lore.add(ChatColor.translateAlternateColorCodes('&', "&7siguiente página"));
        meta.setLore(lore);

        // Añadir datos para identificar la acción
        NamespacedKey actionKey = new NamespacedKey(plugin, "panel_action");
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "next_page");

        // Añadir número de página
        NamespacedKey pageKey = new NamespacedKey(plugin, "page_number");
        meta.getPersistentDataContainer().set(pageKey, PersistentDataType.INTEGER, currentPage + 1);

        nextButton.setItemMeta(meta);
        inventory.setItem(position, nextButton);
    }

    /**
     * Añade las recompensas al inventario según la página
     */
    private void addRewardsToInventory(Inventory inventory, Player player, int page) {
        FileConfiguration config = plugin.getConfigManager().getConfig();

        // Obtener la lista de recompensas directamente como lista
        List<Map<?, ?>> rewardsList = new ArrayList<>();

        if (config.isList("panel.rewards")) {
            // Es una lista, procesarla directamente
            List<?> rawList = config.getList("panel.rewards");
            if (rawList != null) {
                for (Object obj : rawList) {
                    if (obj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<?, ?> map = (Map<?, ?>) obj;
                        rewardsList.add(map);
                    }
                }
            }
        } else {
            // Es una sección, convertirla a lista de mapas
            ConfigurationSection rewardsSection = config.getConfigurationSection("panel.rewards");
            if (rewardsSection != null) {
                for (String key : rewardsSection.getKeys(false)) {
                    ConfigurationSection rewardSection = rewardsSection.getConfigurationSection(key);
                    if (rewardSection != null) {
                        Map<String, Object> map = new HashMap<>();
                        for (String subKey : rewardSection.getKeys(true)) {
                            map.put(subKey, rewardSection.get(subKey));
                        }
                        rewardsList.add(map);
                    }
                }
            }
        }

        if (rewardsList.isEmpty()) {
            plugin.getLogger().warning("No se encontraron recompensas configuradas en panel.rewards");
            return;
        }

        // Ordenar las recompensas por umbral requerido
        rewardsList.sort((r1, r2) -> {
            int threshold1 = r1.containsKey("required") ? Integer.parseInt(r1.get("required").toString()) : 0;
            int threshold2 = r2.containsKey("required") ? Integer.parseInt(r2.get("required").toString()) : 0;
            return Integer.compare(threshold1, threshold2);
        });

        // Calcular índices para la página actual
        int startIndex = page * REWARDS_PER_PAGE;
        int endIndex = Math.min(startIndex + REWARDS_PER_PAGE, rewardsList.size());

        // Obtener posiciones desde la configuración
        List<Integer> rewardPositions = config.getIntegerList("panel.progression.pattern");
        if (rewardPositions.isEmpty()) {
            // Posición por defecto si no está configurado
            int startSlot = config.getInt("panel.progression.start_from", 18);
            for (int i = 0; i < REWARDS_PER_PAGE; i++) {
                rewardPositions.add(startSlot + i);
            }
        }

        // Agregar recompensas en los slots correspondientes
        int positionIndex = 0;
        for (int i = startIndex; i < endIndex; i++) {
            if (positionIndex >= rewardPositions.size()) break;

            Map<?, ?> rewardMap = rewardsList.get(i);
            int slot = rewardPositions.get(positionIndex);

            // Crear ítem de recompensa
            ItemStack rewardItem = createRewardItemFromMap(rewardMap, player);

            // Añadir al inventario
            if (rewardItem != null) {
                inventory.setItem(slot, rewardItem);
                positionIndex++;
            }
        }
    }

    /**
     * Crea un ítem de recompensa a partir de un mapa de datos
     */
    private ItemStack createRewardItemFromMap(Map<?, ?> rewardMap, Player player) {
        if (rewardMap == null) return null;

        FileConfiguration config = plugin.getConfigManager().getConfig();

        // Verificar que tenga los campos necesarios
        if (!rewardMap.containsKey("required") || !rewardMap.containsKey("item")) {
            return null;
        }

        // Extraer datos básicos
        int requiredTrophies = Integer.parseInt(rewardMap.get("required").toString());
        String itemId = rewardMap.get("item").toString();
        Material material = Material.matchMaterial(itemId);
        if (material == null) material = Material.DIAMOND;

        // Crear ítem base
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // Personalizar nombre si está especificado
        if (rewardMap.containsKey("name")) {
            String name = rewardMap.get("name").toString();
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        }

        // Obtener trofeos del jugador
        int playerTrophies = plugin.getCacheManager().getCachedTrophies(player.getUniqueId());

        // Verificar si ya ha reclamado la recompensa
        boolean alreadyClaimed = plugin.getRewardManager().hasClaimedReward(
                player.getUniqueId(), String.valueOf(requiredTrophies));

        // Generar lore según el estado
        List<String> lore = new ArrayList<>();

        // Determinar la configuración del lore a usar
        String configPath;
        if (alreadyClaimed) {
            configPath = "panel.reward_item.claimed";
        } else if (playerTrophies >= requiredTrophies) {
            configPath = "panel.reward_item.available";
        } else {
            configPath = "panel.reward_item.unavailable";
        }

        // Obtener descripción
        String description = rewardMap.containsKey("description") ?
                rewardMap.get("description").toString() : "";

        // Obtener y procesar lore desde la configuración
        List<String> loreConfig = config.getStringList(configPath + ".lore");
        for (String line : loreConfig) {
            line = line.replace("{threshold}", String.valueOf(requiredTrophies))
                    .replace("{remaining}", String.valueOf(Math.max(0, requiredTrophies - playerTrophies)))
                    .replace("{reward_description}", description);
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }

        meta.setLore(lore);

        // Añadir datos persistentes para identificar la recompensa
        NamespacedKey thresholdKey = new NamespacedKey(plugin, "reward_threshold");
        meta.getPersistentDataContainer().set(thresholdKey, PersistentDataType.STRING,
                String.valueOf(requiredTrophies));

        // Guardar comando si existe
        if (rewardMap.containsKey("command")) {
            String command = rewardMap.get("command").toString();
            NamespacedKey commandKey = new NamespacedKey(plugin, "reward_command");
            meta.getPersistentDataContainer().set(commandKey, PersistentDataType.STRING, command);
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Añade los botones de navegación al panel
     */
    private void addNavigationButtons(Inventory inventory, int page, int maxPages) {
        FileConfiguration config = plugin.getConfigManager().getConfig();

        int rows = inventory.getSize() / 9;
        int lastRow = (rows - 1) * 9; // Índice de la primera posición de la última fila

        // Posiciones de los botones (en la última fila)
        int prevButtonSlot = lastRow;      // Esquina inferior izquierda
        int pageIndicatorSlot = lastRow + 4; // Centro inferior
        int nextButtonSlot = lastRow + 8;  // Esquina inferior derecha

        // Configurar botón de cierre/página anterior en la esquina inferior izquierda
        ItemStack closeOrBackButton;

        if (page == 0) {
            // En la primera página, mostrar botón de cerrar
            String closeButtonMaterial = config.getString("panel.close_button.material", "BARRIER");
            Material closeMaterial = Material.matchMaterial(closeButtonMaterial);
            if (closeMaterial == null || closeMaterial == Material.AIR) {
                closeMaterial = Material.BARRIER;
            }

            closeOrBackButton = new ItemStack(closeMaterial);
            ItemMeta closeMeta = closeOrBackButton.getItemMeta();

            String closeName = config.getString("panel.close_button.name", "&cCerrar");
            closeMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', closeName));

            // Añadir lore si está configurado
            List<String> closeLore = config.getStringList("panel.close_button.lore");
            if (!closeLore.isEmpty()) {
                List<String> formattedLore = new ArrayList<>();
                for (String line : closeLore) {
                    formattedLore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                closeMeta.setLore(formattedLore);
            }

            // Marcar como botón de cerrar
            NamespacedKey actionKey = new NamespacedKey(plugin, "panel_action");
            closeMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "close");

            closeOrBackButton.setItemMeta(closeMeta);
        } else {
            // En otras páginas, mostrar botón de página anterior
            String prevButtonMaterial = config.getString("panel.prev_button.material", "ARROW");
            Material prevMaterial = Material.matchMaterial(prevButtonMaterial);
            if (prevMaterial == null || prevMaterial == Material.AIR) {
                prevMaterial = Material.ARROW;
            }

            closeOrBackButton = new ItemStack(prevMaterial);
            ItemMeta prevMeta = closeOrBackButton.getItemMeta();

            // Usar un texto simple
            String prevText = config.getString("panel.prev_button.name", "&a&lPágina Anterior");
            prevMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', prevText));

            List<String> prevLore = config.getStringList("panel.prev_button.lore");
            if (!prevLore.isEmpty()) {
                List<String> formattedLore = new ArrayList<>();
                for (String line : prevLore) {
                    line = line.replace("{current}", String.valueOf(page))
                            .replace("{total}", String.valueOf(maxPages));
                    formattedLore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                prevMeta.setLore(formattedLore);
            }

            // Marcar como botón de página anterior
            NamespacedKey actionKey = new NamespacedKey(plugin, "panel_action");
            NamespacedKey pageKey = new NamespacedKey(plugin, "page_number");

            prevMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "prev_page");

            // IMPORTANTE: Ahora guardamos la página anterior real en lugar de siempre ir a la página 1
            prevMeta.getPersistentDataContainer().set(pageKey, PersistentDataType.INTEGER, page - 1);

            closeOrBackButton.setItemMeta(prevMeta);
        }

        // Colocar el botón en la esquina inferior izquierda
        inventory.setItem(prevButtonSlot, closeOrBackButton);

        // Añadir botón de siguiente página si hay más páginas
        if (page < maxPages - 1) {
            String nextButtonMaterial = config.getString("panel.next_button.material", "ARROW");
            Material nextMaterial = Material.matchMaterial(nextButtonMaterial);
            if (nextMaterial == null || nextMaterial == Material.AIR) {
                nextMaterial = Material.ARROW;
            }

            ItemStack nextButton = new ItemStack(nextMaterial);
            ItemMeta nextMeta = nextButton.getItemMeta();

            // Usar un texto simple
            String nextText = config.getString("panel.next_button.name", "&a&lSiguiente Página");
            nextMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', nextText));

            List<String> nextLore = config.getStringList("panel.next_button.lore");
            if (!nextLore.isEmpty()) {
                List<String> formattedLore = new ArrayList<>();
                for (String line : nextLore) {
                    line = line.replace("{current}", String.valueOf(page))
                            .replace("{total}", String.valueOf(maxPages));
                    formattedLore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                nextMeta.setLore(formattedLore);
            }

            // Marcar como botón de siguiente página
            NamespacedKey actionKey = new NamespacedKey(plugin, "panel_action");
            NamespacedKey pageKey = new NamespacedKey(plugin, "page_number");

            nextMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "next_page");
            nextMeta.getPersistentDataContainer().set(pageKey, PersistentDataType.INTEGER, page + 1);

            nextButton.setItemMeta(nextMeta);

            // Colocar en la esquina inferior derecha
            inventory.setItem(nextButtonSlot, nextButton);
        }

        // Indicador de página actual
        String pageFormat = config.getString("panel.page_indicator.format", "&ePágina {current}/{total}");
        pageFormat = pageFormat.replace("{current}", String.valueOf(page + 1))
                .replace("{total}", String.valueOf(maxPages));

        ItemStack pageIndicator = new ItemStack(Material.PAPER);
        ItemMeta pageMeta = pageIndicator.getItemMeta();
        pageMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', pageFormat));
        pageIndicator.setItemMeta(pageMeta);

        // Colocar en el centro inferior
        inventory.setItem(pageIndicatorSlot, pageIndicator);
    }

    /**
     * Añade un botón para cerrar en la esquina inferior izquierda
     */
    private void addCloseButton(Inventory inventory) {
        int position = inventory.getSize() - 9; // Esquina inferior izquierda
    }
    /**
     * Calcula el número total de páginas necesarias
     */
    private int calculateTotalPages() {
        FileConfiguration config = plugin.getConfigManager().getConfig();

        // Determinar el máximo de trofeos y trofeos por bloque
        int maxTotalTrophies = config.getInt("max_total_trophies", 10000);
        int trophiesPerBlock = config.getInt("panel.path.trophies_per_block", 10);

        // Calcular total de bloques necesarios para llegar al máximo
        int totalBlocks = (int) Math.ceil((double) maxTotalTrophies / trophiesPerBlock);

        // Determinar cuántos bloques caben por página
        int blocksPerPage = config.getIntegerList("panel.path.pattern").size();
        if (blocksPerPage == 0) blocksPerPage = 17; // Valor predeterminado

        // Calcular páginas necesarias
        return (int) Math.ceil((double) totalBlocks / blocksPerPage);
    }

    /**
     * Añade el camino visual de progresión al inventario y las recompensas en los slots correspondientes
     */
    private void addProgressPathToInventory(Inventory inventory, Player player, int page) {
        FileConfiguration config = plugin.getConfigManager().getConfig();

        // Obtener trofeos del jugador
        int playerTrophies = plugin.getCacheManager().getCachedTrophies(player.getUniqueId());

        // Configuración del camino
        int trophiesPerBlock = config.getInt("panel.path.trophies_per_block", 10);
        String notReachedMaterialStr = config.getString("panel.path.not_reached_material", "BLACK_STAINED_GLASS_PANE");
        String reachedMaterialStr = config.getString("panel.path.reached_material", "LIME_STAINED_GLASS_PANE");

        Material notReachedMaterial = Material.matchMaterial(notReachedMaterialStr);
        Material reachedMaterial = Material.matchMaterial(reachedMaterialStr);

        if (notReachedMaterial == null) notReachedMaterial = Material.BLACK_STAINED_GLASS_PANE;
        if (reachedMaterial == null) reachedMaterial = Material.LIME_STAINED_GLASS_PANE;

        String blockNameFormat = config.getString("panel.path.block_name", "&e{trophies} Trofeos");
        List<String> notReachedLore = config.getStringList("panel.path.not_reached_lore");
        List<String> reachedLore = config.getStringList("panel.path.reached_lore");

        // Usar el patrón de la configuración
        List<Integer> pathPattern = config.getIntegerList("panel.path.pattern");
        if (pathPattern.isEmpty()) {
            // Si no hay un patrón específico, usar un patrón predeterminado
            pathPattern = new ArrayList<>();
            pathPattern.add(10);
            pathPattern.add(19);
            pathPattern.add(28);
            pathPattern.add(37);
            pathPattern.add(38);
            pathPattern.add(39);
            // y más slots...
        }

        // Obtener el máximo de trofeos configurado
        int maxTotalTrophies = config.getInt("max_total_trophies", 10000);

        // Calcular el número total de bloques necesarios
        int totalBlocks = (int) Math.ceil((double) maxTotalTrophies / trophiesPerBlock);

        // Calcular cuántos bloques caben por página
        int blocksPerPage = pathPattern.size();

        // Calcular los bloques para esta página específica
        int startBlockIndex = page * blocksPerPage;

        // Obtener todas las recompensas configuradas
        List<Map<?, ?>> rewardsList = new ArrayList<>();
        if (config.isList("panel.rewards")) {
            List<?> rawList = config.getList("panel.rewards");
            if (rawList != null) {
                for (Object obj : rawList) {
                    if (obj instanceof Map) {
                        rewardsList.add((Map<?, ?>) obj);
                    }
                }
            }
        }

        // Crear los bloques del camino para esta página
        for (int i = 0; i < blocksPerPage; i++) {
            int blockIndex = startBlockIndex + i;
            if (blockIndex >= totalBlocks) break; // No exceder el máximo total

            // Usar la posición del patrón
            if (i >= pathPattern.size()) break;
            int slot = pathPattern.get(i);

            if (slot < 0 || slot >= inventory.getSize()) continue;

            // Calcular trofeos para este bloque
            int blockTrophies = (blockIndex + 1) * trophiesPerBlock;

            // No exceder el máximo configurado
            if (blockTrophies > maxTotalTrophies) {
                blockTrophies = maxTotalTrophies;
            }

            // Determinar si el jugador ha alcanzado este bloque
            boolean reached = playerTrophies >= blockTrophies;

            // NUEVO: Comprobar si hay una recompensa para este umbral exacto
            boolean hasReward = false;
            Map<?, ?> rewardForThisThreshold = null;

            for (Map<?, ?> reward : rewardsList) {
                Object requiredObj = reward.get("required");
                if (requiredObj != null) {
                    int required = Integer.parseInt(requiredObj.toString());
                    if (required == blockTrophies) {
                        hasReward = true;
                        rewardForThisThreshold = reward;
                        break;
                    }
                }
            }

            // Si hay una recompensa específica para este umbral, crear un ítem de recompensa
            if (hasReward && rewardForThisThreshold != null) {
                // Crear ítem de recompensa
                ItemStack rewardItem = createRewardItemFromMap(rewardForThisThreshold, player);
                if (rewardItem != null) {
                    inventory.setItem(slot, rewardItem);
                }
            } else {
                // Si no hay recompensa específica, crear el bloque del camino normal
                Material material = reached ? reachedMaterial : notReachedMaterial;
                ItemStack pathBlock = new ItemStack(material);
                ItemMeta meta = pathBlock.getItemMeta();

                // Establecer nombre
                String blockName = blockNameFormat.replace("{trophies}", String.valueOf(blockTrophies));
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', blockName));

                // Establecer lore
                List<String> lore = new ArrayList<>();
                List<String> baseLore = reached ? reachedLore : notReachedLore;

                for (String line : baseLore) {
                    line = line.replace("{trophies}", String.valueOf(blockTrophies))
                            .replace("{remaining}", String.valueOf(Math.max(0, blockTrophies - playerTrophies)));
                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }

                meta.setLore(lore);
                pathBlock.setItemMeta(meta);

                // Añadir al inventario
                inventory.setItem(slot, pathBlock);
            }
        }
    }
    /**
     * Actualiza el panel sin cerrar el inventario actual, manteniendo al jugador en la misma página
     *
     * @param player El jugador
     * @param page La página actual que está viendo
     */
    public void updatePanel(Player player, int page) {
        // Comprobar si el inventario ya está abierto
        if (player.getOpenInventory() == null) return;

        Inventory currentInventory = player.getOpenInventory().getTopInventory();
        String title = player.getOpenInventory().getTitle();

        // Verificar que es nuestro panel
        if (!title.contains(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfigManager().getConfig().getString("panel.title", "&6Camino de Trofeos")))) {
            return;
        }

        // Actualizar solo el contenido del camino y las recompensas
        addIntegratedPathAndRewards(currentInventory, player, page);
    }
}
