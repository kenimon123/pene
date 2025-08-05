package mp.kenimon.managers;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import mp.kenimon.Kenicompetitivo;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.lang.reflect.Field;
import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import java.util.UUID;

public class ShopManager {

    private final Kenicompetitivo plugin;
    private Map<String, Long> recentPurchases = new HashMap<>();
    private Inventory shopInventory;
    private Map<UUID, Long> lastOpenedTime = new HashMap<>();
    private List<String> availableItems = new ArrayList<>();
    private List<String> permanentItems = new ArrayList<>();
    private String freeItemId = null;
    private Set<UUID> claimedFreeItem = new HashSet<>();
    private Map<UUID, Map<String, Long>> purchaseHistory = new HashMap<>();
    private LocalDateTime nextUpdate;
    private String shopTitle;
    private long lastUpdateTime;

    // Cacheo de items personalizados guardados
    private Map<String, ItemStack> customItems = new HashMap<>();

    public ShopManager(Kenicompetitivo plugin) {
        this.plugin = plugin;
        this.lastOpenedTime = new HashMap<>();
        this.availableItems = new ArrayList<>();
        this.claimedFreeItem = new HashSet<>();
        this.purchaseHistory = new HashMap<>();
        this.customItems = new HashMap<>();
        this.lastUpdateTime = System.currentTimeMillis();

        try {
            // Crear tabla para historial de compras si está habilitado
            if (plugin.getConfigManager().getConfig().getBoolean("settings.save_purchases", true)) {
                setupDatabase();
                loadPurchaseHistory();
            }

            loadConfiguration();
            scheduleShopUpdates();
            plugin.getLogger().info("ShopManager inicializado correctamente");
        } catch (Exception e) {
            plugin.getLogger().severe("Error al inicializar ShopManager: " + e.getMessage());
            e.printStackTrace();

            // Inicializar valores por defecto para evitar NullPointerException
            this.nextUpdate = LocalDateTime.now().plusDays(1);
            this.shopTitle = "§cTienda (Error)";

            // Crear inventario vacío por defecto
            this.shopInventory = Bukkit.createInventory(null, 9, this.shopTitle);
        }
    }

    /**
     * Configura la base de datos para el historial de compras
     */
    private void setupDatabase() {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String createTableSQL = "CREATE TABLE IF NOT EXISTS shop_purchases ("
                    + "player_uuid VARCHAR(36) NOT NULL, "
                    + "item_id VARCHAR(64) NOT NULL, "
                    + "purchase_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (player_uuid, item_id));";

            conn.createStatement().execute(createTableSQL);
            plugin.getLogger().info("Tabla de compras configurada correctamente en la base de datos.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error al configurar la tabla de compras en la base de datos:");
            e.printStackTrace();
        }
    }

    /**
     * Carga el historial de compras desde la base de datos
     */
    private void loadPurchaseHistory() {
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String query = "SELECT player_uuid, item_id, purchase_time FROM shop_purchases";
            ResultSet rs = conn.createStatement().executeQuery(query);

            while (rs.next()) {
                UUID playerId = UUID.fromString(rs.getString("player_uuid"));
                String itemId = rs.getString("item_id");
                long purchaseTime = rs.getTimestamp("purchase_time").getTime();

                // Guardar en el mapa en memoria
                purchaseHistory.computeIfAbsent(playerId, k -> new HashMap<>())
                        .put(itemId, purchaseTime);
            }

            plugin.getLogger().info("Historial de compras cargado desde la base de datos.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error al cargar el historial de compras desde la base de datos:");
            e.printStackTrace();
        }
    }

    private void loadClaimedFreeItems() {
        // Limpiar la colección actual
        claimedFreeItem.clear();

        // Si no hay ítem gratuito, no hay nada que cargar para el gratuito
        if (freeItemId != null && !freeItemId.isEmpty()) {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                // Consultar todos los jugadores que reclamaron el ítem gratuito hoy
                String sql = "SELECT player_uuid FROM shop_purchases WHERE item_id = ? AND purchase_time > ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, freeItemId);

                    // Calcular el inicio del día actual
                    LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
                    stmt.setTimestamp(2, Timestamp.valueOf(today));

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String uuidStr = rs.getString("player_uuid");
                            try {
                                UUID playerUUID = UUID.fromString(uuidStr);
                                claimedFreeItem.add(playerUUID);
                            } catch (IllegalArgumentException e) {
                                plugin.getLogger().warning("UUID inválido en la base de datos: " + uuidStr);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error al cargar ítems gratuitos reclamados: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // También cargar todas las compras recientes para asegurar que se muestren correctamente
        recentPurchases.clear();

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            // Consultar todas las compras recientes (últimas 24 horas)
            String sql = "SELECT player_uuid, item_id, purchase_time FROM shop_purchases WHERE purchase_time > ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                // Calcular tiempo hace 24 horas
                Timestamp yesterday = new Timestamp(System.currentTimeMillis() - (24 * 60 * 60 * 1000));
                stmt.setTimestamp(1, yesterday);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String uuidStr = rs.getString("player_uuid");
                        String itemId = rs.getString("item_id");
                        Timestamp purchaseTime = rs.getTimestamp("purchase_time");

                        try {
                            UUID playerUUID = UUID.fromString(uuidStr);
                            String key = playerUUID.toString() + ":" + itemId;
                            recentPurchases.put(key, purchaseTime.getTime());
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("UUID inválido en la base de datos: " + uuidStr);
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error al cargar compras recientes: " + e.getMessage());
            e.printStackTrace();
        }

        plugin.getLogger().info("Cargados " + claimedFreeItem.size() + " jugadores que ya reclamaron el ítem gratuito");
        plugin.getLogger().info("Cargadas " + recentPurchases.size() + " compras recientes");
    }

    /**
     * Carga la configuración de la tienda
     */
    public void loadConfiguration() {
        // Cargar configuración de la tienda usando ConfigManager
        FileConfiguration config = plugin.getConfigManager().loadShopConfig();

        // Cargar items permanentes
        permanentItems = config.getStringList("permanent_items");

        // Configurar próxima actualización
        String updateTimeStr = config.getString("settings.update_time", "00:00");
        LocalTime updateTime = LocalTime.parse(updateTimeStr);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextUpdateTime = now.toLocalDate().atTime(updateTime);

        // Si ya pasó la hora de actualización hoy, programar para mañana
        if (now.isAfter(nextUpdateTime)) {
            nextUpdateTime = nextUpdateTime.plusDays(1);
        }

        this.nextUpdate = nextUpdateTime;

        // Cargar items personalizados guardados
        loadCustomItems();

        // Generar artículos disponibles para hoy
        generateDailyItems();

        // Actualizar tiempo
        lastUpdateTime = System.currentTimeMillis();

        // Crear el inventario de la tienda
        createShopInventory();

        // Cargar ítems reclamados
        loadClaimedFreeItems();

        plugin.getLogger().info("Configuración de tienda cargada correctamente");
    }

    /**
     * Carga los items personalizados guardados
     */
    private void loadCustomItems() {
        // Primero limpiar el mapa existente
        customItems.clear();

        // Código para cargar items personalizados desde archivo o base de datos
        File customItemsFile = new File(plugin.getDataFolder(), "custom_items.yml");
        if (customItemsFile.exists()) {
            YamlConfiguration customConfig = YamlConfiguration.loadConfiguration(customItemsFile);

            ConfigurationSection itemsSection = customConfig.getConfigurationSection("items");
            if (itemsSection != null) {
                for (String key : itemsSection.getKeys(false)) {
                    try {
                        ItemStack item = itemsSection.getItemStack(key);
                        if (item != null) {
                            customItems.put(key, item);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error al cargar item personalizado '" + key + "': " + e.getMessage());
                    }
                }
            }

            plugin.getLogger().info("Se cargaron " + customItems.size() + " items personalizados para la tienda.");
        }
    }

    /**
     * Guarda un item personalizado
     * @param id Identificador único
     * @param item ItemStack a guardar
     * @return true si se guardó correctamente
     */
    public boolean saveCustomItem(String id, ItemStack item) {
        // Primero guardar en memoria
        customItems.put(id, item.clone());

        // Luego guardar en archivo
        File customItemsFile = new File(plugin.getDataFolder(), "custom_items.yml");
        YamlConfiguration customConfig;

        if (customItemsFile.exists()) {
            customConfig = YamlConfiguration.loadConfiguration(customItemsFile);
        } else {
            customConfig = new YamlConfiguration();
        }

        customConfig.set("items." + id, item);

        try {
            customConfig.save(customItemsFile);
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Error al guardar item personalizado: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Programa la actualización periódica de la tienda
     */
    private void scheduleShopUpdates() {
        // Programar actualización de tienda diaria
        new BukkitRunnable() {
            @Override
            public void run() {
                LocalDateTime now = LocalDateTime.now();

                // Si es hora de actualizar la tienda
                if (now.isAfter(nextUpdate) || now.isEqual(nextUpdate)) {
                    // Actualizar la tienda
                    updateShop();

                    // Programar próxima actualización
                    nextUpdate = nextUpdate.plusDays(1);

                    // Anunciar actualización si está configurado
                    if (plugin.getConfigManager().getConfig().getBoolean("settings.broadcast_update", true)) {
                        String message = plugin.getConfigManager().getFormattedMessage("shop.broadcast", "");
                        Bukkit.broadcastMessage(message);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L * 60); // Comprobar cada minuto
    }

    /**
     * Actualiza la tienda
     */
    public void updateShop() {
        claimedFreeItem.clear();
        generateDailyItems();
        createShopInventory();
    }

    /**
     * Genera los ítems disponibles para el día actual
     */
    private void generateDailyItems() {
        FileConfiguration config = plugin.getConfigManager().loadShopConfig();
        ConfigurationSection itemsSection = config.getConfigurationSection("items");

        if (itemsSection == null) {
            plugin.getLogger().warning("No se encontró la sección 'items' en tienda.yml");
            availableItems = new ArrayList<>();
            return;
        }

        // Lista de todos los items posibles (excluyendo permanentes para evitar duplicados)
        List<String> allItems = itemsSection.getKeys(false).stream()
                .filter(id -> !permanentItems.contains(id))
                .collect(Collectors.toList());

        // Verificar si siempre debe haber un item gratis
        boolean alwaysFreeItem = config.getBoolean("settings.always_free_item", true);

        // Número máximo de items por día (excluyendo permanentes)
        int maxItemsPerDay = config.getInt("settings.max_items_per_day", 8);
        maxItemsPerDay = Math.min(maxItemsPerDay, allItems.size());

        // Probabilidad de aparición de cada item
        int baseAppearanceChance = config.getInt("settings.item_appearance_chance", 70);

        // Seleccionar items para hoy basados en rareza
        availableItems = new ArrayList<>(permanentItems); // Primero añadir los permanentes
        Random random = new Random();

        // Lista de items que pueden ser gratuitos
        List<String> freeItemCandidates = new ArrayList<>();

        // Para cada ítem en la configuración, decidir si aparece hoy basado en su rareza
        for (String itemId : allItems) {
            // Si ya tenemos el máximo de ítems no permanentes, salir
            if (availableItems.size() - permanentItems.size() >= maxItemsPerDay) {
                break;
            }

            // Obtener la rareza del item (o usar "common" por defecto)
            String rarityKey = config.getString("items." + itemId + ".rarity", "common");

            // Obtener el multiplicador de probabilidad para esta rareza
            double rarityMultiplier = config.getDouble("rarity." + rarityKey + ".chance_multiplier", 1.0);

            // Calcular probabilidad final
            int finalChance = (int)(baseAppearanceChance * rarityMultiplier);

            // Si la probabilidad es favorable, añadir a la lista temporal
            if (random.nextInt(100) < finalChance) {
                availableItems.add(itemId);

                // Si puede ser gratis, añadirlo como candidato
                if (config.getBoolean("items." + itemId + ".can_be_free", false)) {
                    freeItemCandidates.add(itemId);
                }
            }
        }

        // Si no hay suficientes items, añadir algunos al azar hasta alcanzar el mínimo (al menos 1)
        int nonPermanentItemsCount = availableItems.size() - permanentItems.size();
        int minItems = Math.min(1, allItems.size());
        while (nonPermanentItemsCount < minItems && !allItems.isEmpty()) {
            String randomItem = allItems.get(random.nextInt(allItems.size()));
            if (!availableItems.contains(randomItem)) {
                availableItems.add(randomItem);
                nonPermanentItemsCount++;

                // Si puede ser gratis, añadirlo como candidato
                if (config.getBoolean("items." + randomItem + ".can_be_free", false)) {
                    freeItemCandidates.add(randomItem);
                }
            }
        }

        // Manejar el ítem gratuito
        if (alwaysFreeItem) {
            // Si necesitamos un ítem gratuito pero no tenemos candidatos o la lista está vacía
            if (freeItemCandidates.isEmpty() || availableItems.isEmpty()) {
                // Buscar cualquier ítem que pueda ser gratis
                for (String itemId : allItems) {
                    if (config.getBoolean("items." + itemId + ".can_be_free", false)) {
                        freeItemCandidates.add(itemId);
                    }
                }
            }

            // Si hay candidatos para ser gratuitos
            if (!freeItemCandidates.isEmpty()) {
                // Seleccionar uno al azar
                freeItemId = freeItemCandidates.get(random.nextInt(freeItemCandidates.size()));

                // Si no está ya en la lista de disponibles, añadirlo
                if (!availableItems.contains(freeItemId)) {
                    availableItems.add(freeItemId);
                }
            } else {
                // Si no hay candidatos, usar el primero disponible
                if (!availableItems.isEmpty()) {
                    freeItemId = availableItems.get(0);
                } else if (!allItems.isEmpty()) {
                    // Si no hay items disponibles pero sí hay en la configuración, usar uno
                    freeItemId = allItems.get(0);
                    availableItems.add(freeItemId);
                }
            }
        } else {
            freeItemId = null;
        }

        plugin.getLogger().info("Tienda: Generados " + availableItems.size() + " items para hoy (" +
                (availableItems.size() - permanentItems.size()) + " temporales y " +
                permanentItems.size() + " permanentes)");

        this.lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Crea el inventario de la tienda
     */
    private void createShopInventory() {
        FileConfiguration config = plugin.getConfigManager().loadShopConfig();

        // Obtener título y filas del inventario
        shopTitle = ChatColor.translateAlternateColorCodes('&', config.getString("settings.title", "&b&lTienda Diaria"));

        // Calcular número mínimo de filas necesario
        int configRows = config.getInt("settings.rows", 6);
        int minRowsNeeded = 2; // Mínimo 2 filas (18 slots)

        // Calcular filas necesarias basado en número de ítems
        int itemCount = availableItems.size();
        int decorationSlotsCount = config.getIntegerList("decoration.positions").size();
        int totalItemsNeeded = itemCount + decorationSlotsCount + 1; // +1 para el botón de cerrar

        int calculatedRows = (int) Math.ceil(totalItemsNeeded / 9.0);
        if (calculatedRows > minRowsNeeded) {
            minRowsNeeded = calculatedRows;
        }

        // Usar el máximo entre las filas configuradas y las necesarias
        int rows = Math.max(configRows, minRowsNeeded);

        // Limitar a máximo 6 filas
        rows = Math.min(rows, 6);

        // Crear el inventario
        shopInventory = Bukkit.createInventory(null, rows * 9, shopTitle);

        // Decoración del inventario
        if (config.getBoolean("decoration.enabled", true)) {
            String decorationMaterial = config.getString("decoration.item", "LIGHT_BLUE_STAINED_GLASS_PANE");
            String decorationName = config.getString("decoration.name", " ");
            List<Integer> decorationPositions = config.getIntegerList("decoration.positions");

            Material decMaterial;
            try {
                decMaterial = Material.valueOf(decorationMaterial);
            } catch (IllegalArgumentException e) {
                decMaterial = Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            }

            ItemStack decorationItem = createItem(
                    decMaterial,
                    ChatColor.translateAlternateColorCodes('&', decorationName),
                    new ArrayList<>()
            );

            // Solo colocar decoración dentro de los límites
            for (int position : decorationPositions) {
                if (position >= 0 && position < shopInventory.getSize()) {
                    shopInventory.setItem(position, decorationItem);
                }
            }
        }

        // Botón de cerrar
        if (config.getBoolean("close_button.enabled", true)) {
            int closePosition = config.getInt("close_button.position", 49);

            // Asegurar que la posición del botón está dentro del inventario
            if (closePosition >= shopInventory.getSize()) {
                closePosition = shopInventory.getSize() - 5;
            }

            String closeMaterial = config.getString("close_button.item", "BARRIER");
            String closeName = config.getString("close_button.name", "&cCerrar");
            List<String> closeLore = config.getStringList("close_button.lore");

            Material closeMatObj;
            try {
                closeMatObj = Material.valueOf(closeMaterial);
            } catch (IllegalArgumentException e) {
                closeMatObj = Material.BARRIER;
            }

            ItemStack closeItem = createItem(
                    closeMatObj,
                    ChatColor.translateAlternateColorCodes('&', closeName),
                    colorizeStringList(closeLore)
            );

            shopInventory.setItem(closePosition, closeItem);
        }

        // Crear slots para ítems de manera dinámica
        List<Integer> itemSlots = new ArrayList<>();

        // Crear un patrón de slots disponibles para ítems
        for (int i = 0; i < shopInventory.getSize(); i++) {
            // Evitar slots de decoración y botón cerrar
            List<Integer> decorationPositions = config.getIntegerList("decoration.positions");
            int closePosition = config.getInt("close_button.position", 49);

            if (!decorationPositions.contains(i) && i != closePosition) {
                itemSlots.add(i);
            }
        }

        // Asegurar que hay suficientes slots para todos los ítems
        if (itemSlots.size() < availableItems.size()) {
            plugin.getLogger().warning("No hay suficientes slots para mostrar todos los ítems (" +
                    availableItems.size() + " ítems, " + itemSlots.size() + " slots)");
        }

        // Colocar ítems disponibles
        int itemIndex = 0;
        for (String itemId : availableItems) {
            if (itemIndex >= itemSlots.size()) break;

            // Llamada con null como UUID para inventario genérico
            ItemStack item = createShopItem(itemId, null);
            if (item != null) {
                shopInventory.setItem(itemSlots.get(itemIndex), item);
                itemIndex++;
            }
        }
    }

    /**
     * Crea un ítem para la tienda basado en la configuración
     */
    private ItemStack createShopItem(String itemId, UUID playerUUID) {
        FileConfiguration config = plugin.getConfigManager().loadShopConfig();
        ConfigurationSection itemSection = config.getConfigurationSection("items." + itemId);

        if (itemSection == null) {
            plugin.getLogger().warning("No se encontró la configuración para el ítem: " + itemId);
            return null;
        }

        // Determinar si este es el item gratuito
        boolean isFreeItem = itemId.equals(freeItemId);

        // Verificar el tipo de ítem
        String itemType = itemSection.getString("type", "item");

        // Material por defecto
        Material material = Material.STONE;
        ItemStack baseItem = null;

        // Lógica específica según el tipo de ítem
        switch (itemType) {
            case "item":
                // Para ítems normales, verificar si es una cabeza personalizada
                if (itemSection.getString("item", "").equals("PLAYER_HEAD")) {
                    // Es una cabeza con textura
                    String texture = itemSection.getString("texture");
                    if (texture != null && !texture.isEmpty()) {
                        baseItem = createCustomSkull(texture);
                    } else {
                        // Cabeza sin textura
                        baseItem = new ItemStack(Material.PLAYER_HEAD);
                    }
                } else if (customItems.containsKey(itemId)) {
                    // Es un ítem personalizado guardado
                    baseItem = customItems.get(itemId).clone();
                } else {
                    // Es un ítem normal
                    String materialName = itemSection.getString("item", "STONE");
                    try {
                        material = Material.valueOf(materialName.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Material inválido para ítem " + itemId + ": " + materialName);
                        material = Material.STONE;
                    }
                    baseItem = new ItemStack(material);
                }
                break;

            case "cosmetic":
                // Para cosméticos, verificar el tipo de cosmético
                String cosmeticId = itemSection.getString("cosmetic_id");
                if (cosmeticId == null) {
                    plugin.getLogger().warning("Cosmético sin ID: " + itemId);
                    return null;
                }

                // Obtener el cosmético real desde el manager
                mp.kenimon.cosmetics.CosmeticEffect effect = plugin.getCosmeticManager().getEffectById(cosmeticId);
                if (effect == null) {
                    plugin.getLogger().warning("Cosmético no encontrado: " + cosmeticId);
                    return null;
                }

                // Usar el icono del cosmético
                material = effect.getIcon();
                baseItem = new ItemStack(material);
                break;

            default:
                plugin.getLogger().warning("Tipo de ítem desconocido: " + itemType);
                baseItem = new ItemStack(Material.STONE);
                break;
        }

        // Atributos básicos del ítem
        String itemName = itemSection.getString("name", "Item");
        // Manejar la descripción ya sea como string o como lista
        String description = "";
        if (itemSection.isList("description")) {
            // Si la descripción está guardada como una lista (lore completo)
            List<String> descLines = itemSection.getStringList("description");
            if (!descLines.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < descLines.size(); i++) {
                    if (i > 0) {
                        sb.append("\n");
                    }
                    sb.append(descLines.get(i));
                }
                description = sb.toString();
            }
        } else {
            // Si es un string simple
            description = itemSection.getString("description", "");
        }

        int price = itemSection.getInt("price", 100);
        String currency = itemSection.getString("currency", "trophies");

        // Obtener el formato configurado para la moneda
        String currencyFormat = "";
        FileConfiguration shopConfig = plugin.getConfigManager().loadShopConfig();
        if (currency.equalsIgnoreCase("trophies")) {
            currencyFormat = shopConfig.getString("economies.trophies.format", "trofeos");
        } else if (currency.equalsIgnoreCase("gems")) {
            currencyFormat = shopConfig.getString("economies.gems.format", "{value} gemas");
        } else if (currency.equalsIgnoreCase("money")) {
            currencyFormat = shopConfig.getString("economies.money.format", "${value}");
        }

        // Aplicar el formato reemplazando {value} con el precio real
        String formattedPrice = currencyFormat.replace("{value}", String.valueOf(price));

        // Atributos de rareza
        String rarity = itemSection.getString("rarity", "common");
        String rarityDisplayName = config.getString("rarity." + rarity + ".name", "&7Común");
        String rarityColor = config.getString("rarity." + rarity + ".color", "&7");

        // Determinar estado y formato
        ConfigurationSection formatSection;
        boolean alreadyPurchased = false;
        boolean alreadyClaimed = false;
        String timeRemaining = "";

        // Solo verificar si hay un jugador real
        if (playerUUID != null) {
            if (isFreeItem) {
                // Verificar si el jugador ya reclamó el ítem gratuito
                alreadyClaimed = claimedFreeItem.contains(playerUUID);

                if (alreadyClaimed) {
                    formatSection = config.getConfigurationSection("claimed_free_item_format");
                    if (formatSection == null) {
                        plugin.getLogger().warning("No se encontró la sección claimed_free_item_format en tienda.yml");
                        formatSection = config.getConfigurationSection("free_item_format");
                    }
                } else {
                    formatSection = config.getConfigurationSection("free_item_format");
                }
            } else {
                // Verificar compras para ítems pagados
                alreadyPurchased = hasPurchasedRecently(playerUUID, itemId);

                if (alreadyPurchased) {
                    formatSection = config.getConfigurationSection("purchased_item_format");
                    timeRemaining = getPurchaseCooldownRemaining(playerUUID, itemId);

                    // Cambiar el item si está configurado
                    if (formatSection.contains("item")) {
                        try {
                            String purchasedMaterialStr = formatSection.getString("item");
                            Material purchasedMaterial = Material.valueOf(purchasedMaterialStr);
                            baseItem = new ItemStack(purchasedMaterial);
                        } catch (Exception e) {
                            // Mantener el ítem original si hay error
                        }
                    }
                } else {
                    formatSection = config.getConfigurationSection("paid_item_format");
                }
            }
        } else {
            // Durante la inicialización, usar el formato predeterminado
            formatSection = isFreeItem ?
                    config.getConfigurationSection("free_item_format") :
                    config.getConfigurationSection("paid_item_format");
        }

        if (formatSection == null) {
            // Valores por defecto si no hay configuración
            List<String> defaultLore = new ArrayList<>();
            defaultLore.add("&7" + description);
            defaultLore.add("");
            if (isFreeItem) {
                defaultLore.add("&a¡Haz clic para reclamar gratis!");
                return createItem(material, "&a" + itemName + " &7- &a&lGRATIS", colorizeStringList(defaultLore), true);
            } else {
                defaultLore.add("&ePrecio: &6" + formattedPrice);
                defaultLore.add("&7Haz clic para comprar");
                return createItem(material, "&f" + itemName + " &7- &6" + formattedPrice,
                        colorizeStringList(defaultLore), false);
            }
        }

        // Formatear nombre
        String nameFormat = formatSection.getString("name", "{item_name}");
        nameFormat = nameFormat
                .replace("{item_name}", itemName)
                .replace("{price}", String.valueOf(price))
                .replace("{currency}", currency)
                .replace("{formatted_price}", formattedPrice)
                .replace("{rarity}", rarity)
                .replace("{rarity_color}", rarityColor);

        List<String> loreFormat = formatSection.getStringList("lore");
        List<String> formattedLore = new ArrayList<>();

        for (String line : loreFormat) {
            // Si la línea contiene el placeholder de descripción y la descripción tiene múltiples líneas
            if (line.contains("{item_description}") && description.contains("\n")) {
                String[] descLines = description.split("\n");
                boolean firstLine = true;

                for (String descLine : descLines) {
                    if (firstLine) {
                        // La primera línea usa el formato completo
                        String formattedLine = line
                                .replace("{item_description}", descLine)
                                .replace("{price}", String.valueOf(price))
                                .replace("{currency}", currency)
                                .replace("{formatted_price}", formattedPrice)
                                .replace("{rarity}", rarity)
                                .replace("{rarity_color}", rarityColor)
                                .replace("{rarity_display}", rarityDisplayName)
                                .replace("{time_remaining}", timeRemaining);

                        formattedLore.add(formattedLine);
                        firstLine = false;
                    } else {
                        // Las líneas adicionales solo contienen el texto, sin formato adicional
                        formattedLore.add(descLine);
                    }
                }
            } else {
                // Reemplazar placeholders normalmente para otras líneas
                line = line
                        .replace("{item_description}", description)
                        .replace("{price}", String.valueOf(price))
                        .replace("{currency}", currency)
                        .replace("{formatted_price}", formattedPrice)
                        .replace("{rarity}", rarity)
                        .replace("{rarity_color}", rarityColor)
                        .replace("{rarity_display}", rarityDisplayName)
                        .replace("{time_remaining}", timeRemaining);

                formattedLore.add(line);
            }
        }

        // Configurar metadata del ítem
        if (baseItem == null) {
            baseItem = new ItemStack(material);
        }

        ItemMeta meta = baseItem.hasItemMeta() ? baseItem.getItemMeta() : Bukkit.getItemFactory().getItemMeta(baseItem.getType());

        if (meta != null) {
            // Establecer nombre y lore
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', nameFormat));
            meta.setLore(colorizeStringList(formattedLore));

            // Añadir flags para ocultar atributos
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

            // Aplicar brillo si está configurado
            if (formatSection.getBoolean("glow", false)) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
            }

            // Guardar metadata en el ítem para identificarlo más tarde
            NamespacedKey idKey = new NamespacedKey(plugin, "shop_item_id");
            meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, itemId);

            NamespacedKey freeKey = new NamespacedKey(plugin, "is_free");
            meta.getPersistentDataContainer().set(freeKey, PersistentDataType.INTEGER, isFreeItem ? 1 : 0);

            NamespacedKey typeKey = new NamespacedKey(plugin, "item_type");
            meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, itemType);

            NamespacedKey priceKey = new NamespacedKey(plugin, "price");
            meta.getPersistentDataContainer().set(priceKey, PersistentDataType.INTEGER, price);

            NamespacedKey currencyKey = new NamespacedKey(plugin, "currency");
            meta.getPersistentDataContainer().set(currencyKey, PersistentDataType.STRING, currency);

            baseItem.setItemMeta(meta);
        }

        return baseItem;
    }

    /**
     * Verifica si un jugador ha comprado recientemente un artículo
     * @param itemId ID del artículo
     * @return true si el jugador compró el artículo en las últimas 24 horas
     */
    public boolean hasPurchasedRecently(UUID playerUUID, String itemId) {
        if (playerUUID == null || itemId == null) {
            return false;
        }

        // Primero verificar en el caché de memoria
        String key = playerUUID.toString() + ":" + itemId;
        if (recentPurchases.containsKey(key)) {
            long purchaseTime = recentPurchases.get(key);
            long cooldownMillis = plugin.getConfigManager().loadShopConfig().getInt("settings.purchase_cooldown", 24) * 3600000L;

            // Verificar si ha pasado el tiempo de cooldown
            if (System.currentTimeMillis() - purchaseTime < cooldownMillis) {
                return true;
            } else {
                // Ya pasó el cooldown, eliminar del caché
                recentPurchases.remove(key);
                return false;
            }
        }

        // Si no está en el caché, consultar la base de datos
        boolean hasPurchased = false;
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String sql = "SELECT COUNT(*) FROM shop_purchases WHERE player_uuid = ? AND item_id = ? AND purchase_time > ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, itemId);

                // Calcular tiempo de cooldown
                int cooldownHours = plugin.getConfigManager().loadShopConfig().getInt("settings.purchase_cooldown", 24);
                Timestamp cooldownTime = new Timestamp(System.currentTimeMillis() - (cooldownHours * 3600000L));
                stmt.setTimestamp(3, cooldownTime);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        hasPurchased = rs.getInt(1) > 0;

                        // Si ha comprado, añadirlo al caché
                        if (hasPurchased) {
                            // Obtener el tiempo de compra
                            PreparedStatement timeStmt = conn.prepareStatement(
                                    "SELECT purchase_time FROM shop_purchases WHERE player_uuid = ? AND item_id = ? ORDER BY purchase_time DESC LIMIT 1");
                            timeStmt.setString(1, playerUUID.toString());
                            timeStmt.setString(2, itemId);
                            ResultSet timeRs = timeStmt.executeQuery();

                            if (timeRs.next()) {
                                Timestamp purchaseTime = timeRs.getTimestamp("purchase_time");
                                recentPurchases.put(key, purchaseTime.getTime());
                            }

                            timeRs.close();
                            timeStmt.close();
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error al verificar compra reciente: " + e.getMessage());
            e.printStackTrace();
        }

        return hasPurchased;
    }

    /**
     * Obtiene el tiempo restante del cooldown de compra de un ítem
     * @param playerId UUID del jugador
     * @param itemId ID del ítem
     * @return String formateado con el tiempo restante
     */
    public String getPurchaseCooldownRemaining(UUID playerId, String itemId) {
        // Verificar si el ítem fue comprado recientemente consultando la base de datos
        String key = playerId.toString() + ":" + itemId;
        long purchaseTime = 0;

        // Obtener tiempo de la última compra de la caché
        if (recentPurchases.containsKey(key)) {
            purchaseTime = recentPurchases.get(key);
        } else {
            // Si no está en caché, consultar la base de datos
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                String sql = "SELECT MAX(purchase_time) AS latest FROM shop_purchases WHERE player_uuid = ? AND item_id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, playerId.toString());
                    stmt.setString(2, itemId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next() && rs.getTimestamp("latest") != null) {
                            purchaseTime = rs.getTimestamp("latest").getTime();
                            // Actualizar la caché
                            recentPurchases.put(key, purchaseTime);
                        } else {
                            return "0h 0m"; // No hay compra registrada
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error al obtener tiempo de compra: " + e.getMessage());
                return "Error";
            }
        }

        // Si no hay tiempo de compra registrado
        if (purchaseTime == 0) {
            return "0h 0m";
        }

        // Calcular tiempo restante
        long currentTime = System.currentTimeMillis();
        int cooldownHours = plugin.getConfigManager().loadShopConfig().getInt("settings.purchase_cooldown", 24);
        long cooldownMillis = cooldownHours * 3600000L; // Convertir horas a milisegundos
        long expiryTime = purchaseTime + cooldownMillis;
        long timeRemainingMillis = expiryTime - currentTime;

        // Si ya expiró el cooldown
        if (timeRemainingMillis <= 0) {
            return "0h 0m";
        }

        // Convertir a horas y minutos
        long hours = timeRemainingMillis / 3600000;
        long minutes = (timeRemainingMillis % 3600000) / 60000;

        return hours + "h " + minutes + "m";
    }

    /**
     * Identifica el ID de un ítem de la tienda a partir del ItemStack
     */
    private String getItemIdFromItemStack(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;

        ItemMeta meta = item.getItemMeta();
        NamespacedKey idKey = new NamespacedKey(plugin, "shop_item_id");

        if (meta.getPersistentDataContainer().has(idKey, PersistentDataType.STRING)) {
            return meta.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        }

        return null;
    }

    /**
     * Crea una cabeza personalizada con textura Base64
     * @param texture String Base64 con la textura
     * @return ItemStack con la cabeza personalizada
     */
    private ItemStack createCustomSkull(String texture) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (texture == null || texture.isEmpty()) return head;

        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;

        GameProfile profile = new GameProfile(UUID.randomUUID(), "");
        profile.getProperties().put("textures", new Property("textures", texture));

        try {
            Field profileField = meta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(meta, profile);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            plugin.getLogger().warning("Error al aplicar textura a cabeza: " + e.getMessage());
            e.printStackTrace();
        }

        head.setItemMeta(meta);
        return head;
    }

    /**
     * Procesa la compra de un ítem de la tienda
     *
     * @param player Jugador que realiza la compra
     * @param item Ítem que se está comprando
     */
    public void processItemPurchase(Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;

        String itemId = getItemIdFromItemStack(item);
        if (itemId == null) return;

        // Obtener datos del ítem desde el metadata
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        // Verificar si el ítem es gratuito
        boolean isFree = false;
        NamespacedKey freeKey = new NamespacedKey(plugin, "is_free");
        if (meta.getPersistentDataContainer().has(freeKey, PersistentDataType.INTEGER)) {
            isFree = meta.getPersistentDataContainer().get(freeKey, PersistentDataType.INTEGER) == 1;
        }

        // CORRECCIÓN: Verificar primero si ya reclamó el ítem gratuito
        if (isFree && itemId.equals(freeItemId)) {
            // Verificar si ya reclamó el ítem gratuito
            if (claimedFreeItem.contains(player.getUniqueId())) {
                player.sendMessage(plugin.getConfigManager().getFormattedMessage("shop.already_purchased", ""));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.5f);
                return; // Salir del método para evitar procesar la compra
            }
        }

        // Verificar tipo de ítem
        String itemType = "item";
        NamespacedKey typeKey = new NamespacedKey(plugin, "item_type");
        if (meta.getPersistentDataContainer().has(typeKey, PersistentDataType.STRING)) {
            itemType = meta.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        }

        // Verificar precio y moneda
        int price = 100;
        NamespacedKey priceKey = new NamespacedKey(plugin, "price");
        if (meta.getPersistentDataContainer().has(priceKey, PersistentDataType.INTEGER)) {
            price = meta.getPersistentDataContainer().get(priceKey, PersistentDataType.INTEGER);
        }

        String currency = "trophies";
        NamespacedKey currencyKey = new NamespacedKey(plugin, "currency");
        if (meta.getPersistentDataContainer().has(currencyKey, PersistentDataType.STRING)) {
            currency = meta.getPersistentDataContainer().get(currencyKey, PersistentDataType.STRING);
        }

        // Verificar si el ítem requiere cooldown
        boolean hasCooldown = itemHasCooldown(itemId);

        // Verificar si ya compró este ítem (solo para ítems pagados)
        if (!isFree && hasCooldown && hasPurchasedRecently(player.getUniqueId(), itemId)) {
            player.sendMessage(plugin.getConfigManager().getFormattedMessage("shop.already_purchased", ""));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.5f);
            return;
        }

        // Procesar según sea gratis o de pago
        if (isFree) {
            if (itemType.equals("cosmetic")) {
                grantCosmetic(player, itemId);
            } else {
                giveItemToPlayer(player, itemId);
            }

            // CORRECCIÓN: Siempre registrar el ítem gratuito, independientemente de hasCooldown
            registerPurchase(player.getUniqueId(), itemId);

            // CORRECCIÓN: Marcar explícitamente como reclamado y actualizar la caché
            claimedFreeItem.add(player.getUniqueId());

            player.sendMessage(plugin.getConfigManager().getFormattedMessage("shop.free_claimed", ""));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
        } else {
            // Verificar si tiene suficientes recursos según la moneda
            boolean canAfford = false;

            switch (currency.toLowerCase()) {
                case "trophies":
                    int trophies = plugin.getCacheManager().getCachedTrophies(player.getUniqueId());
                    canAfford = trophies >= price;
                    if (canAfford) {
                        plugin.getCacheManager().setCachedTrophies(player.getUniqueId(), trophies - price);
                        plugin.getDatabaseManager().setTrophies(player.getUniqueId(), trophies - price);
                    } else {
                        player.sendMessage(plugin.getConfigManager().getFormattedMessage("shop.not_enough_trophies", ""));
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.5f);
                        return;
                    }
                    break;

                case "gems":
                    // Verificar si la economía de gemas está habilitada
                    if (plugin.getGemManager() != null && plugin.getGemManager().isEnabled()) {
                        int gems = plugin.getGemManager().getGems(player.getUniqueId());
                        canAfford = gems >= price;
                        if (canAfford) {
                            plugin.getGemManager().removeGems(player.getUniqueId(), price);
                        } else {
                            player.sendMessage(plugin.getConfigManager().getFormattedMessage("shop.not_enough_gems", ""));
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.5f);
                            return;
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "El sistema de gemas no está disponible.");
                        return;
                    }
                    break;

                case "money":
                    // Usar Vault para dinero
                    if (plugin.getVaultManager() != null && plugin.getVaultManager().isEnabled()) {
                        canAfford = plugin.getVaultManager().has(player, price);
                        if (canAfford) {
                            plugin.getVaultManager().withdraw(player, price);
                        } else {
                            player.sendMessage(plugin.getConfigManager().getFormattedMessage("shop.not_enough_money", ""));
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.5f);
                            return;
                        }
                    } else {
                        player.sendMessage(ChatColor.RED + "Vault no está instalado. No se puede comprar con dinero.");
                        return;
                    }
                    break;

                default:
                    player.sendMessage(ChatColor.RED + "Moneda no soportada: " + currency);
                    return;
            }

            // Si llegamos aquí, la compra fue exitosa
            if (itemType.equals("cosmetic")) {
                grantCosmetic(player, itemId);
            } else {
                giveItemToPlayer(player, itemId);
            }

            // Registrar la compra solo si se completó exitosamente y tiene cooldown
            if (hasCooldown) {
                registerPurchase(player.getUniqueId(), itemId);
            }

            player.sendMessage(plugin.getConfigManager().getFormattedMessage("shop.purchase_success", "")
                    .replace("{price}", String.valueOf(price))
                    .replace("{currency}", getCurrencyName(currency)));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
        }

        // Actualizar la tienda para reflejar la compra
        // IMPORTANTE: NO cerrar el inventario, solo actualizar su contenido
        updateShopInventoryForPlayer(player);
    }

    /**
     * Actualiza el inventario de la tienda para un jugador específico sin cerrar la GUI
     */
    private void updateShopInventoryForPlayer(Player player) {
        // Verificar si el jugador tiene la tienda abierta
        if (player.getOpenInventory() != null &&
                player.getOpenInventory().getTitle().equals(shopTitle)) {

            // Obtener el inventario actual
            Inventory currentInventory = player.getOpenInventory().getTopInventory();

            // Actualizar todos los ítems en el inventario
            for (int i = 0; i < currentInventory.getSize(); i++) {
                ItemStack currentItem = currentInventory.getItem(i);
                if (currentItem != null && currentItem.getType() != Material.AIR) {
                    // Obtener el ID del ítem
                    String itemId = getItemIdFromItemStack(currentItem);
                    if (itemId != null) {
                        // Crear el ítem actualizado
                        ItemStack updatedItem = createShopItem(itemId, player.getUniqueId());
                        if (updatedItem != null) {
                            currentInventory.setItem(i, updatedItem);
                        }
                    }
                }
            }

            // Actualizar vista del inventario para el jugador
            player.updateInventory();
        }
    }

    /**
     * Obtiene el nombre formateado de la moneda
     */
    private String getCurrencyName(String currency) {
        switch (currency.toLowerCase()) {
            case "trophies":
                return "trofeos";
            case "gems":
                return "gemas";
            case "money":
                return "monedas";
            default:
                return currency;
        }
    }

    private void grantCosmetic(Player player, String itemId) {
        FileConfiguration config = plugin.getConfigManager().loadShopConfig();
        ConfigurationSection itemSection = config.getConfigurationSection("items." + itemId);

        if (itemSection == null) {
            plugin.getLogger().warning("No se encontró la configuración para el ítem: " + itemId);
            return;
        }

        String cosmeticId = itemSection.getString("cosmetic_id");
        if (cosmeticId == null || cosmeticId.isEmpty()) {
            plugin.getLogger().warning("El ítem " + itemId + " no tiene cosmetic_id definido.");
            return;
        }

        // Desbloquear el cosmético para el jugador
        plugin.getCosmeticManager().unlockEffect(player.getUniqueId(), cosmeticId);

        // Equipar automáticamente si está configurado
        if (itemSection.getBoolean("auto_equip", true)) {
            // Determinar la categoría del cosmético
            String category = "particle";
            if (cosmeticId.startsWith("sound_")) category = "sound";
            if (cosmeticId.startsWith("kill_")) category = "kill";

            // CORRECCIÓN: Usar el método público correcto en lugar de toggleEffect
            plugin.getDatabaseManager().selectCosmetic(player.getUniqueId(), category, cosmeticId);
        }
    }

    /**
     * Actualiza el inventario de la tienda para un jugador específico
     * (Para reflejar compras recientes)
     */
    private void updateShopInventory(Player player) {
        // Si el jugador tiene la tienda abierta, actualizarla
        if (player.getOpenInventory() != null &&
                player.getOpenInventory().getTitle().equals(shopTitle)) {
            // Reabrir la tienda para el jugador
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> openShop(player), 1L);
        }
    }

    /**
     * Crea un ItemStack básico
     */
    private ItemStack createItem(Material material, String name, List<String> lore) {
        return createItem(material, name, lore, false);
    }

    /**
     * Crea un ItemStack con opción de brillo
     */
    private ItemStack createItem(Material material, String name, List<String> lore, boolean glow) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);

            if (glow) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Abre la tienda para un jugador
     */
    public void openShop(Player player) {
        // Actualizar el tiempo de última apertura
        lastOpenedTime.put(player.getUniqueId(), System.currentTimeMillis());

        // Construir inventario personalizado para este jugador
        Inventory playerShopInventory = buildPlayerInventory(player);

        // Abrir el inventario
        player.openInventory(playerShopInventory);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.6f, 1.0f);
    }

    private Inventory buildPlayerInventory(Player player) {
        FileConfiguration config = plugin.getConfigManager().loadShopConfig();

        // Obtener título y filas del inventario
        String title = ChatColor.translateAlternateColorCodes('&', config.getString("settings.title", "&b&lTienda Diaria"));

        // Respetar el número de filas configurado
        int rows = config.getInt("settings.rows", 6);
        rows = Math.min(Math.max(rows, 1), 6); // Asegurar entre 1 y 6 filas

        // Crear el inventario
        Inventory playerInventory = Bukkit.createInventory(null, rows * 9, title);

        // Decoración del inventario
        if (config.getBoolean("decoration.enabled", true)) {
            String decorationMaterial = config.getString("decoration.item", "LIGHT_BLUE_STAINED_GLASS_PANE");
            String decorationName = config.getString("decoration.name", " ");
            List<Integer> decorationPositions = config.getIntegerList("decoration.positions");

            Material decMaterial;
            try {
                decMaterial = Material.valueOf(decorationMaterial);
            } catch (IllegalArgumentException e) {
                decMaterial = Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            }

            ItemStack decorationItem = createItem(
                    decMaterial,
                    ChatColor.translateAlternateColorCodes('&', decorationName),
                    new ArrayList<>()
            );

            // Solo colocar decoración dentro de los límites
            for (int position : decorationPositions) {
                if (position >= 0 && position < playerInventory.getSize()) {
                    playerInventory.setItem(position, decorationItem);
                }
            }
        }

        // Botón de cerrar
        if (config.getBoolean("close_button.enabled", true)) {
            int closePosition = config.getInt("close_button.position", 49);

            // Asegurar que la posición del botón está dentro del inventario
            if (closePosition >= playerInventory.getSize()) {
                closePosition = playerInventory.getSize() - 5;
            }

            String closeMaterial = config.getString("close_button.item", "BARRIER");
            String closeName = config.getString("close_button.name", "&cCerrar");
            List<String> closeLore = config.getStringList("close_button.lore");

            Material closeMatObj;
            try {
                closeMatObj = Material.valueOf(closeMaterial);
            } catch (IllegalArgumentException e) {
                closeMatObj = Material.BARRIER;
            }

            ItemStack closeItem = createItem(
                    closeMatObj,
                    ChatColor.translateAlternateColorCodes('&', closeName),
                    colorizeStringList(closeLore)
            );

            playerInventory.setItem(closePosition, closeItem);
        }

        // Crear slots para ítems de manera dinámica
        List<Integer> itemSlots = new ArrayList<>();

        // Crear un patrón de slots disponibles para ítems
        for (int i = 0; i < playerInventory.getSize(); i++) {
            // Evitar slots de decoración y botón cerrar
            List<Integer> decorationPositions = config.getIntegerList("decoration.positions");
            int closePosition = config.getInt("close_button.position", 49);

            if (!decorationPositions.contains(i) && i != closePosition) {
                itemSlots.add(i);
            }
        }

        // Colocar ítems disponibles
        int itemIndex = 0;
        for (String itemId : availableItems) {
            if (itemIndex >= itemSlots.size()) break;

            // Usar UUID del jugador para personalizar el ítem
            ItemStack item = createShopItem(itemId, player.getUniqueId());
            if (item != null) {
                playerInventory.setItem(itemSlots.get(itemIndex), item);
                itemIndex++;
            }
        }

        return playerInventory;
    }

    /**
     * Da un ítem al jugador mediante comandos
     */
    private void giveItemToPlayer(Player player, String itemId) {
        FileConfiguration config = plugin.getConfigManager().loadShopConfig();
        ConfigurationSection itemSection = config.getConfigurationSection("items." + itemId);

        if (itemSection == null) return;

        List<String> commands = itemSection.getStringList("commands");

        for (String cmd : commands) {
            String processedCmd = cmd.replace("{player}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCmd);
        }
    }

    /**
     * Registra una compra en el sistema
     */
    public void registerPurchase(UUID playerUUID, String itemId) {
        long currentTime = System.currentTimeMillis();

        // Registrar en la base de datos
        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String sql = "INSERT INTO shop_purchases (player_uuid, item_id, purchase_time) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, itemId);
                stmt.setTimestamp(3, new Timestamp(currentTime));
                stmt.executeUpdate();
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error al registrar compra: " + e.getMessage());
            e.printStackTrace();
        }

        // Actualizar el caché de compras recientes
        String key = playerUUID.toString() + ":" + itemId;
        recentPurchases.put(key, currentTime);

        // Si es el ítem gratuito, marcarlo como reclamado
        if (itemId.equals(freeItemId)) {
            claimedFreeItem.add(playerUUID);
        }
    }

    /**
     * Procesa un comando para guardar un ítem personalizado
     * @param player Jugador que ejecuta el comando
     * @param itemId ID con el que se guardará
     * @return true si se guardó correctamente
     */
    public boolean saveItemInHand(Player player, String itemId) {
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand == null || itemInHand.getType() == Material.AIR) {
            player.sendMessage(plugin.getConfigManager().getFormattedMessage("shop.no_item_in_hand", ""));
            return false;
        }

        boolean success = saveCustomItem(itemId, itemInHand.clone());

        if (success) {
            player.sendMessage(plugin.getConfigManager().getFormattedMessage("shop.item_saved", "")
                    .replace("{id}", itemId));
        } else {
            player.sendMessage(plugin.getConfigManager().getFormattedMessage("shop.item_save_error", ""));
        }

        return success;
    }

    /**
     * Obtiene el tiempo formateado hasta la próxima actualización
     */
    public String getNextUpdateTimeFormatted() {
        long seconds = getSecondsUntilNextUpdate();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    /**
     * Obtiene los segundos hasta la próxima actualización
     */
    public long getSecondsUntilNextUpdate() {
        FileConfiguration config = plugin.getConfigManager().loadShopConfig();
        String updateTimeStr = config.getString("settings.update_time", "00:00");

        // Parseamos la hora de actualización
        int updateHour = 0;
        int updateMinute = 0;

        try {
            String[] parts = updateTimeStr.split(":");
            updateHour = Integer.parseInt(parts[0]);
            updateMinute = Integer.parseInt(parts[1]);
        } catch (Exception e) {
            plugin.getLogger().warning("Formato de hora inválido en tienda.yml: " + updateTimeStr);
        }

        // Calculamos la próxima actualización
        Calendar now = Calendar.getInstance();
        Calendar nextUpdate = Calendar.getInstance();
        nextUpdate.set(Calendar.HOUR_OF_DAY, updateHour);
        nextUpdate.set(Calendar.MINUTE, updateMinute);
        nextUpdate.set(Calendar.SECOND, 0);

        // Si ya pasó la hora de hoy, programar para mañana
        if (nextUpdate.before(now)) {
            nextUpdate.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Calcular segundos restantes
        return (nextUpdate.getTimeInMillis() - now.getTimeInMillis()) / 1000;
    }

    /**
     * Fuerza una actualización de la tienda
     */
    public void forceUpdate() {
        // Generar nuevos ítems
        generateDailyItems();

        // Recrear el inventario
        createShopInventory();

        // MUY IMPORTANTE: Cargar ítems reclamados
        loadClaimedFreeItems();

        // Actualizar el tiempo de última actualización
        lastUpdateTime = System.currentTimeMillis();

        // Opcional: notificar a todos los jugadores
        if (plugin.getConfigManager().loadShopConfig().getBoolean("settings.broadcast_update", true)) {
            String message = plugin.getConfigManager().getFormattedMessage("shop.update_broadcast", "");
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(message);
            }
        }

        plugin.getLogger().info("Tienda actualizada manualmente");
    }

    /**
     * Helper method para colorear una lista de strings
     */
    private List<String> colorizeStringList(List<String> list) {
        List<String> colorized = new ArrayList<>();
        for (String line : list) {
            colorized.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        return colorized;
    }

    // Getter methods for ShopListener.java
    public String getShopTitle() {
        return shopTitle;
    }

    public int getCloseButtonSlot() {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        return config.getInt("close_button.position", 49);
    }

    /**
     * Añade un nuevo método para obtener la información sobre gemas
     */
    public int getGems(UUID playerId) {
        // Este método debería conectar con tu sistema de gemas
        if (plugin.getGemManager() != null) {
            return plugin.getGemManager().getGems(playerId);
        }
        return 0;
    }

    /**
     * Reinicia el cooldown de compra para un item específico
     */
    public boolean resetPurchaseCooldown(UUID playerUUID, String itemId) {
        if (playerUUID == null || itemId == null) {
            return false;
        }

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String sql = "DELETE FROM shop_purchases WHERE player_uuid = ? AND item_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, itemId);
                int rows = stmt.executeUpdate();
                return rows > 0;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error al reiniciar cooldown: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Reinicia todos los cooldowns de compra para un jugador
     */
    public void resetAllPurchaseCooldowns(UUID playerUUID) {
        if (playerUUID == null) {
            return;
        }

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            String sql = "DELETE FROM shop_purchases WHERE player_uuid = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                stmt.executeUpdate();
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error al reiniciar todos los cooldowns: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Verifica si un item tiene configurado cooldown
     */
    public boolean itemHasCooldown(String itemId) {
        FileConfiguration config = plugin.getConfigManager().loadShopConfig();
        String path = "items." + itemId + ".has_cooldown";

        // Si no existe la configuración específica, usar la global
        if (!config.contains(path)) {
            return true; // Por defecto, todos los items tienen cooldown
        }

        return config.getBoolean(path);
    }
}
