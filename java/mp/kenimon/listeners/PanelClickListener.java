package mp.kenimon.listeners;

import mp.kenimon.Kenicompetitivo;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PanelClickListener implements Listener {

    private final Kenicompetitivo plugin;
    // Mapa para almacenar temporalmente la página actual de cada jugador
    private final Map<UUID, Integer> currentPages = new HashMap<>();

    public PanelClickListener(Kenicompetitivo plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPanelClick(InventoryClickEvent event) {
        // Verificar si el inventario es nuestro panel de trofeos
        if (event.getView().getTitle().contains(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfigManager().getConfig().getString("panel.title", "&6Camino de Trofeos")))) {
            // Cancelar el evento para evitar mover items
            event.setCancelled(true);

            // Si no se hizo clic en un inventario o no es un cofre, retornar
            if (event.getClickedInventory() == null || event.getClickedInventory().getType() != InventoryType.CHEST) {
                return;
            }

            // Si no se hizo clic en un item, retornar
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                return;
            }

            Player player = (Player) event.getWhoClicked();

            // Guardar la página actual antes de procesar el clic
            int currentPage = getCurrentPageFromTitle(event.getView().getTitle());
            // Loguear para depuración
            plugin.getLogger().info("Título: '" + event.getView().getTitle() + "', Página detectada: " + currentPage);
            currentPages.put(player.getUniqueId(), currentPage);

            // Verificar acciones específicas según el item
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta == null) return;

            PersistentDataContainer container = meta.getPersistentDataContainer();
            NamespacedKey actionKey = new NamespacedKey(plugin, "panel_action");
            if (container.has(actionKey, PersistentDataType.STRING)) {
                String action = container.get(actionKey, PersistentDataType.STRING);

                if ("close".equals(action)) {
                    // Cerrar el inventario
                    player.closeInventory();
                    return;
                } else if ("next_page".equals(action)) {
                    // Ir a la siguiente página
                    NamespacedKey pageKey = new NamespacedKey(plugin, "page_number");
                    if (container.has(pageKey, PersistentDataType.INTEGER)) {
                        int nextPage = container.get(pageKey, PersistentDataType.INTEGER);
                        plugin.getPanelManager().openPanel(player, nextPage);
                    }
                    return;
                } else if ("prev_page".equals(action)) {
                    // Ir a la página anterior
                    NamespacedKey pageKey = new NamespacedKey(plugin, "page_number");
                    if (container.has(pageKey, PersistentDataType.INTEGER)) {
                        int prevPage = container.get(pageKey, PersistentDataType.INTEGER);
                        plugin.getPanelManager().openPanel(player, prevPage);
                    } else {
                        // Por defecto, ir a la página anterior (actual - 1)
                        if (currentPage > 0) {
                            plugin.getPanelManager().openPanel(player, currentPage - 1);
                        }
                    }
                    return;
                }
            }

            // Verificar si es una recompensa
            NamespacedKey rewardKey = new NamespacedKey(plugin, "reward_threshold");
            if (container.has(rewardKey, PersistentDataType.STRING)) {
                // Obtener el umbral de trofeos para esta recompensa
                int threshold = Integer.parseInt(container.get(rewardKey, PersistentDataType.STRING));

                // Obtener trofeos actuales del jugador
                int playerTrophies = plugin.getCacheManager().getCachedTrophies(player.getUniqueId());

                // Verificar si ya reclamó esta recompensa
                boolean alreadyClaimed = plugin.getRewardManager().hasClaimedReward(player.getUniqueId(), String.valueOf(threshold));
                // Si tiene suficientes trofeos y no la ha reclamado aún
                if (playerTrophies >= threshold) {
                    if (!alreadyClaimed) {
                        // Obtener el comando a ejecutar
                        NamespacedKey commandKey = new NamespacedKey(plugin, "reward_command");
                        if (container.has(commandKey, PersistentDataType.STRING)) {
                            String command = container.get(commandKey, PersistentDataType.STRING);

                            // Ejecutar comando
                            if (command != null && !command.isEmpty()) {
                                // Verificar si es un comando múltiple separado por ||
                                if (command.contains("||")) {
                                    String[] commands = command.split("\\|\\|");
                                    for (String cmd : commands) {
                                        cmd = cmd.trim().replace("%player%", player.getName());
                                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                                    }
                                } else {
                                    command = command.replace("%player%", player.getName());
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                                }

                                // Marcar como reclamada en la base de datos
                                plugin.getRewardManager().markRewardAsClaimed(player.getUniqueId(), threshold);

                                // Sonido de éxito
                                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);

                                // Efectos visuales
                                player.spawnParticle(Particle.TOTEM, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);

                                // Mensaje de éxito
                                String message = plugin.getConfigManager().getFormattedMessage(
                                        "reward.claimed",
                                        "{prefix}&a¡Has reclamado tu recompensa!");
                                message = message.replace("{threshold}", String.valueOf(threshold));
                                player.sendMessage(message);

                                // CAMBIO CRUCIAL: En lugar de abrir o actualizar el panel,
                                // simplemente actualizamos el ítem actual para mostrar que está reclamado
                                ItemStack claimedItem = createClaimedRewardItem(threshold, player);
                                event.getInventory().setItem(event.getSlot(), claimedItem);

                                return;
                            }
                        }
                    } else {
                        // Sonido de recompensa ya reclamada
                        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 0.8f);

                        // Ya reclamada
                        String message = plugin.getConfigManager().getFormattedMessage(
                                "reward.already_claimed",
                                "{prefix}&cYa has reclamado esta recompensa.");
                        player.sendMessage(message);
                    }
                } else {
                    // Sonido de requisito no cumplido
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);

                    // No tiene suficientes trofeos
                    String message = plugin.getConfigManager().getFormattedMessage(
                            "reward.need_more",
                            "{prefix}&cNecesitas {remaining} trofeos más.");
                    message = message.replace("{remaining}", String.valueOf(threshold - playerTrophies));
                    player.sendMessage(message);
                }
            }
        }
    }

    /**
     * Crea un ítem que representa una recompensa ya reclamada
     */
    private ItemStack createClaimedRewardItem(int threshold, Player player) {
        FileConfiguration config = plugin.getConfigManager().getConfig();

        // Obtener material para recompensas reclamadas
        String itemStr = config.getString("panel.reward_item.claimed.item", "CHEST");
        Material material = Material.matchMaterial(itemStr);
        if (material == null) material = Material.CHEST;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // Extraer información de la recompensa original (si está disponible)
        String rewardName = "Recompensa";
        String rewardDescription = "";

        // Buscar la recompensa en la configuración
        if (config.isList("panel.rewards")) {
            List<?> rewards = config.getList("panel.rewards");
            if (rewards != null) {
                for (Object obj : rewards) {
                    if (obj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> rewardMap = (Map<String, Object>) obj;

                        // Verificar si esta es la recompensa para este umbral
                        if (rewardMap.containsKey("required")) {
                            int requiredTrophies = Integer.parseInt(rewardMap.get("required").toString());
                            if (requiredTrophies == threshold) {
                                // Encontramos la recompensa correcta
                                if (rewardMap.containsKey("name")) {
                                    rewardName = rewardMap.get("name").toString();
                                }
                                if (rewardMap.containsKey("description")) {
                                    rewardDescription = rewardMap.get("description").toString();
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }

        // Nombre para recompensa reclamada
        String nameFormat = config.getString("panel.reward_item.claimed.name", "&7Recompensa Reclamada");
        nameFormat = nameFormat.replace("{reward_name}", rewardName)
                .replace("{threshold}", String.valueOf(threshold));
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', nameFormat));

        // Lore para recompensa reclamada
        List<String> lore = new ArrayList<>();
        List<String> configuredLore = config.getStringList("panel.reward_item.claimed.lore");

        for (String line : configuredLore) {
            line = line.replace("{threshold}", String.valueOf(threshold))
                    .replace("{player}", player.getName())
                    .replace("{reward_name}", rewardName)
                    .replace("{reward_description}", rewardDescription);
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }

        meta.setLore(lore);

        // Mantener la información de threshold para identificación
        NamespacedKey rewardKey = new NamespacedKey(plugin, "reward_threshold");
        meta.getPersistentDataContainer().set(rewardKey, PersistentDataType.STRING, String.valueOf(threshold));

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Extrae el número de página actual del título del inventario
     * @param title Título del inventario
     * @return Número de página (comenzando en 0)
     */
    private int getCurrentPageFromTitle(String title) {
        // Intentamos extraer "Página X/Y" del título
        Pattern pattern = Pattern.compile("Página (\\d+)/(\\d+)");
        Matcher matcher = pattern.matcher(title);

        if (matcher.find()) {
            try {
                int pageNum = Integer.parseInt(matcher.group(1));
                return pageNum - 1; // Restamos 1 porque mostramos "Página 1" para pageIndex 0
            } catch (NumberFormatException e) {
                // Ignorar error de parsing
            }
        }

        // Si no podemos extraer la página, asumir página 0
        return 0;
    }

    /**
     * Reproduce efectos especiales al reclamar recompensas
     * @param player Jugador que recibe los efectos
     * @param effectType Tipo de efecto a reproducir
     */
    private void playRewardEffect(Player player, String effectType) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        String effectSection = "reward_effects." + effectType.toLowerCase();

        // Si no existe la configuración para este tipo de efecto, usar efectos básicos
        if (!config.contains(effectSection)) {
            // Efectos básicos predeterminados
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            player.spawnParticle(Particle.VILLAGER_HAPPY, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
            return;
        }

        // Nombre del efecto para mostrar
        String effectName = config.getString(effectSection + ".name", "&7" + effectType);
        effectName = ChatColor.translateAlternateColorCodes('&', effectName);

        // Sonidos
        if (config.getBoolean(effectSection + ".sound.enabled", true)) {
            try {
                String soundType = config.getString(effectSection + ".sound.type", "ENTITY_PLAYER_LEVELUP");
                float volume = (float) config.getDouble(effectSection + ".sound.volume", 1.0);
                float pitch = (float) config.getDouble(effectSection + ".sound.pitch", 1.0);

                Sound sound = Sound.valueOf(soundType);
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (Exception e) {
                // Sonido de respaldo
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
        }

        // Partículas
        if (config.contains(effectSection + ".particles")) {
            for (Map<?, ?> particleConfig : config.getMapList(effectSection + ".particles")) {
                try {
                    String particleType = particleConfig.get("type").toString();
                    int count = Integer.parseInt(particleConfig.get("count").toString());
                    double offsetX = Double.parseDouble(particleConfig.get("offsetX").toString());
                    double offsetY = Double.parseDouble(particleConfig.get("offsetY").toString());
                    double offsetZ = Double.parseDouble(particleConfig.get("offsetZ").toString());
                    double speed = Double.parseDouble(particleConfig.get("speed").toString());

                    Particle particle = Particle.valueOf(particleType);
                    player.spawnParticle(particle, player.getLocation().add(0, 1, 0),
                            count, offsetX, offsetY, offsetZ, speed);
                } catch (Exception e) {
                    plugin.getLogger().warning("Error al crear partículas para efecto " + effectType + ": " + e.getMessage());
                }
            }
        }

        // Rayo (si está habilitado)
        if (config.getBoolean(effectSection + ".lightning", false)) {
            player.getWorld().strikeLightningEffect(player.getLocation());
        }

        // Mensaje de broadcast
        if (config.getBoolean(effectSection + ".broadcast.enabled", false)) {
            String message = config.getString(effectSection + ".broadcast.message", "");
            if (!message.isEmpty()) {
                message = message.replace("{player}", player.getName());
                Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', message));
            }
        }

        // Fuegos artificiales
        if (config.getBoolean(effectSection + ".fireworks.enabled", false)) {
            int fireworkCount = config.getInt(effectSection + ".fireworks.count", 1);
            spawnFireworks(player.getLocation(), fireworkCount);
        }
    }

    /**
     * Genera fuegos artificiales en una ubicación
     * @param location Ubicación donde generar los fuegos artificiales
     * @param count Cantidad de fuegos artificiales a generar
     */
    private void spawnFireworks(Location location, int count) {
        for (int i = 0; i < count; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Firework fw = location.getWorld().spawn(location, Firework.class);
                FireworkMeta meta = fw.getFireworkMeta();

                // Efecto aleatorio
                FireworkEffect.Type[] types = FireworkEffect.Type.values();
                FireworkEffect.Type type = types[new Random().nextInt(types.length)];

                // Colores aleatorios
                Color[] colors = {Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW, Color.PURPLE, Color.AQUA, Color.ORANGE};
                Color color1 = colors[new Random().nextInt(colors.length)];
                Color color2 = colors[new Random().nextInt(colors.length)];

                // Crear el efecto correctamente
                FireworkEffect effect = FireworkEffect.builder()
                        .with(type)
                        .withColor(color1)
                        .withFade(color2)
                        .trail(true)
                        .flicker(new Random().nextBoolean())
                        .build();

                meta.addEffect(effect);
                meta.setPower(1); // Potencia baja para que explote rápido
                fw.setFireworkMeta(meta);
            }, i * 5L); // Pequeña demora entre cada fuego artificial
        }
    }
}