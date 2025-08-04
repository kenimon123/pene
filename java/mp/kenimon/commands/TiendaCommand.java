package mp.kenimon.commands;

import mp.kenimon.Kenicompetitivo;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class TiendaCommand implements CommandExecutor, TabCompleter {

    private final Kenicompetitivo plugin;

    public TiendaCommand(Kenicompetitivo plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player) && args.length == 0) {
            sender.sendMessage(plugin.getConfigManager().getFormattedMessage("commands.player_only", ""));
            return true;
        }

        if (args.length == 0) {
            // Abrir la tienda
            if (sender instanceof Player) {
                plugin.getShopManager().openShop((Player) sender);
            }
            return true;
        }

        // Subcomandos
        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "actualizar":
            case "update":
                if (!sender.hasPermission("kenicompetitivo.admin.tienda")) {
                    sender.sendMessage(plugin.getConfigManager().getFormattedMessage("commands.no_permission", ""));
                    return true;
                }
                plugin.getShopManager().forceUpdate();
                sender.sendMessage(plugin.getConfigManager().getFormattedMessage("shop.admin.update", ""));
                return true;

            case "recargar":
            case "reload":
                if (!sender.hasPermission("kenicompetitivo.admin.tienda")) {
                    sender.sendMessage(plugin.getConfigManager().getFormattedMessage("commands.no_permission", ""));
                    return true;
                }
                plugin.getConfigManager().loadShopConfig();
                plugin.getShopManager().loadConfiguration();
                sender.sendMessage(plugin.getConfigManager().getFormattedMessage("shop.admin.reload", ""));
                return true;

            case "tiempo":
            case "time":
                long seconds = plugin.getShopManager().getSecondsUntilNextUpdate();
                String timeFormatted = String.format("%02d:%02d:%02d",
                        seconds / 3600,
                        (seconds % 3600) / 60,
                        seconds % 60);
                sender.sendMessage(plugin.getConfigManager().getFormattedMessage("shop.next_update", "")
                        .replace("{time}", timeFormatted));
                return true;

            case "saveitem":
            case "guardaritem":
                if (!(sender instanceof Player)) {
                    sender.sendMessage(plugin.getConfigManager().getFormattedMessage("commands.player_only", ""));
                    return true;
                }

                if (!sender.hasPermission("kenicompetitivo.admin.tienda")) {
                    sender.sendMessage(plugin.getConfigManager().getFormattedMessage("commands.no_permission", ""));
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage("§cUso: /tienda saveitem <id>");
                    return true;
                }

                String itemId = args[1];
                saveItemToShopConfig((Player) sender, itemId);
                return true;

            case "give":
                if (!sender.hasPermission("kenicompetitivo.admin.tienda")) {
                    sender.sendMessage(plugin.getConfigManager().getFormattedMessage("commands.no_permission", ""));
                    return true;
                }

                if (args.length < 3) {
                    sender.sendMessage("§cUso: /tienda give <jugador> <itemId>");
                    return true;
                }

                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§cJugador no encontrado: " + args[1]);
                    return true;
                }

                String giveItemId = args[2];
                if (giveItemToPlayer(target, giveItemId)) {
                    sender.sendMessage("§aItem entregado a " + target.getName());
                } else {
                    sender.sendMessage("§cNo se pudo entregar el item. Revisa que el ID sea correcto.");
                }
                return true;

            case "resetcooldown":
                if (!sender.hasPermission("kenicompetitivo.admin.tienda")) {
                    sender.sendMessage(plugin.getConfigManager().getFormattedMessage("commands.no_permission", ""));
                    return true;
                }

                if (args.length < 2) {
                    sender.sendMessage("§cUso: /tienda resetcooldown <jugador> [itemId]");
                    return true;
                }

                Player targetPlayer = Bukkit.getPlayer(args[1]);
                if (targetPlayer == null) {
                    sender.sendMessage("§cJugador no encontrado: " + args[1]);
                    return true;
                }

                if (args.length >= 3) {
                    // Reiniciar cooldown de un ítem específico
                    String resetItemId = args[2];
                    if (plugin.getShopManager().resetPurchaseCooldown(targetPlayer.getUniqueId(), resetItemId)) {
                        sender.sendMessage("§aReiniciado el cooldown del ítem " + resetItemId + " para " + targetPlayer.getName());
                    } else {
                        sender.sendMessage("§cNo se pudo reiniciar el cooldown. Verifica que el ID sea correcto.");
                    }
                } else {
                    // Reiniciar todos los cooldowns
                    plugin.getShopManager().resetAllPurchaseCooldowns(targetPlayer.getUniqueId());
                    sender.sendMessage("§aReiniciados todos los cooldowns de compra para " + targetPlayer.getName());
                }
                return true;

            case "help":
            case "ayuda":
                sendHelpMessage(sender);
                return true;

            default:
                sender.sendMessage(plugin.getConfigManager().getFormattedMessage("errors.invalid_command", ""));
                return true;
        }
    }

    /**
     * Guarda un ítem en la configuración de la tienda
     */
    private boolean saveItemToShopConfig(Player player, String itemId) {
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage(plugin.getConfigManager().getFormattedMessage("shop.admin.no_item_in_hand", ""));
            return false;
        }

        // Comenzar la conversación interactiva
        player.sendMessage("§a§lGuardando ítem como: §e" + itemId);
        player.sendMessage("§7Responde a las siguientes preguntas en el chat:");

        // Preguntar precio
        player.sendMessage("§b1. §fIndica el precio del ítem:");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            new ConversationHandler(player, response -> {
                int price = 500;
                try {
                    price = Integer.parseInt(response);
                    if (price < 0) price = 0;
                } catch (NumberFormatException e) {
                    player.sendMessage("§cValor inválido, usando 500 como predeterminado.");
                }

                // Guardar precio y continuar con moneda
                saveItemStep2(player, itemId, item, price);
            });
        }, 5L);

        return true;
    }

    private void saveItemStep2(Player player, String itemId, ItemStack item, int price) {
        // Preguntar moneda
        player.sendMessage("§b2. §fSelecciona la moneda (trophies/gems/money):");
        new ConversationHandler(player, response -> {
            String currency = response.toLowerCase();
            if (!currency.equals("trophies") && !currency.equals("gems") && !currency.equals("money")) {
                player.sendMessage("§cMoneda inválida, usando trophies como predeterminado.");
                currency = "trophies";
            }

            // Guardar moneda y continuar con rareza
            saveItemStep3(player, itemId, item, price, currency);
        });
    }

    private void saveItemStep3(Player player, String itemId, ItemStack item, int price, String currency) {
        // Preguntar rareza
        player.sendMessage("§b3. §fSelecciona la rareza (common/uncommon/rare/epic/legendary/mythic):");
        new ConversationHandler(player, response -> {
            String rarity = response.toLowerCase();
            String[] validRarities = {"common", "uncommon", "rare", "epic", "legendary", "mythic"};
            boolean validRarity = false;

            for (String valid : validRarities) {
                if (valid.equals(rarity)) {
                    validRarity = true;
                    break;
                }
            }

            if (!validRarity) {
                player.sendMessage("§cRareza inválida, usando uncommon como predeterminado.");
                rarity = "uncommon";
            }

            // Guardar rareza y continuar con item gratuito
            saveItemStep4(player, itemId, item, price, currency, rarity);
        });
    }

    private void saveItemStep4(Player player, String itemId, ItemStack item, int price, String currency, String rarity) {
        // Preguntar si puede ser gratis
        player.sendMessage("§b4. §f¿Puede ser gratis? (si/no):");
        new ConversationHandler(player, response -> {
            boolean canBeFree = response.toLowerCase().startsWith("s") || response.toLowerCase().startsWith("y");

            // Guardar y continuar con la siguiente pregunta
            saveItemStep5(player, itemId, item, price, currency, rarity, canBeFree);
        });
    }

    private void saveItemStep5(Player player, String itemId, ItemStack item, int price, String currency, String rarity, boolean canBeFree) {
        // Preguntar si debe tener cooldown
        player.sendMessage("§b5. §f¿Debe tener tiempo de espera para volver a comprar? (si/no):");
        new ConversationHandler(player, response -> {
            boolean hasCooldown = response.toLowerCase().startsWith("s") || response.toLowerCase().startsWith("y");

            // Continuar con comando
            saveItemStep6(player, itemId, item, price, currency, rarity, canBeFree, hasCooldown);
        });
    }

    private void saveItemStep6(Player player, String itemId, ItemStack item, int price, String currency, String rarity, boolean canBeFree, boolean hasCooldown) {
        // Preguntar si debe ser permanente en la tienda
        player.sendMessage("§b6. §f¿Debe aparecer siempre en la tienda? (si/no):");
        new ConversationHandler(player, response -> {
            boolean isPermanent = response.toLowerCase().startsWith("s") || response.toLowerCase().startsWith("y");

            // Continuar con comando
            saveItemStep7(player, itemId, item, price, currency, rarity, canBeFree, hasCooldown, isPermanent);
        });
    }

    private void saveItemStep7(Player player, String itemId, ItemStack item, int price, String currency, String rarity, boolean canBeFree, boolean hasCooldown, boolean isPermanent) {
        // Preguntar comando personalizado
        player.sendMessage("§b7. §fEscribe el comando para dar este ítem (usa {player} para referirte al jugador):");
        player.sendMessage("§7Ejemplo: give {player} " + item.getType() + " 1");
        new ConversationHandler(player, response -> {
            String command = response;
            if (command.trim().isEmpty()) {
                command = "give {player} " + item.getType() + " 1";
                player.sendMessage("§cComando vacío, usando predeterminado: " + command);
            }

            // Finalizar guardado
            finalSaveItem(player, itemId, item, price, currency, rarity, canBeFree, hasCooldown, isPermanent, command);
        });
    }

    private void finalSaveItem(Player player, String itemId, ItemStack item, int price,
                               String currency, String rarity, boolean canBeFree,
                               boolean hasCooldown, boolean isPermanent, String command) {
        try {
            FileConfiguration config = plugin.getConfigManager().loadShopConfig();
            String path = "items." + itemId;

            // Configuración básica
            config.set(path + ".type", "item");
            config.set(path + ".item", item.getType().toString());
            config.set(path + ".price", price);
            config.set(path + ".currency", currency);
            config.set(path + ".rarity", rarity);
            config.set(path + ".can_be_free", canBeFree);
            config.set(path + ".has_cooldown", hasCooldown);
            config.set(path + ".permanent", isPermanent);

            // Nombre y descripción con colores
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                // Preservar el nombre con formato
                String displayName = item.getItemMeta().getDisplayName();
                config.set(path + ".name", displayName);
            } else {
                String readableName = formatMaterialName(item.getType().toString());
                config.set(path + ".name", readableName);
            }

            // IMPORTANTE: Guardar todo el lore como descripción
            if (item.hasItemMeta() && item.getItemMeta().hasLore() && !item.getItemMeta().getLore().isEmpty()) {
                List<String> lore = item.getItemMeta().getLore();

                // Guardar el lore completo como descripción
                config.set(path + ".description", lore);

                // También guardar el lore completo en full_lore (para compatibilidad)
                config.set(path + ".full_lore", lore);
            } else {
                List<String> defaultDescription = new ArrayList<>();
                defaultDescription.add("Ítem especial de la tienda");
                config.set(path + ".description", defaultDescription);
                config.set(path + ".full_lore", defaultDescription);
            }

            // Comandos para dar el ítem
            List<String> commands = new ArrayList<>();
            commands.add(command);
            config.set(path + ".commands", commands);

            // Si es permanente, añadirlo a la lista de permanentes
            if (isPermanent) {
                List<String> permanentItems = config.getStringList("permanent_items");
                if (!permanentItems.contains(itemId)) {
                    permanentItems.add(itemId);
                    config.set("permanent_items", permanentItems);
                }
            }

            // Guardar la configuración
            File shopFile = new File(plugin.getDataFolder(), "tienda.yml");
            config.save(shopFile);

            // Recargar la configuración
            plugin.getConfigManager().loadShopConfig();
            plugin.getShopManager().loadConfiguration();

            player.sendMessage("§a§l¡Ítem guardado correctamente!");
            player.sendMessage("§fID: §e" + itemId);
            player.sendMessage("§fPrecio: §e" + price + " " + currency);
            player.sendMessage("§fRareza: §e" + rarity);
            player.sendMessage("§fPuede ser gratis: §e" + (canBeFree ? "Sí" : "No"));
            player.sendMessage("§fTiene tiempo de espera: §e" + (hasCooldown ? "Sí" : "No"));
            player.sendMessage("§fEs permanente: §e" + (isPermanent ? "Sí" : "No"));
            player.sendMessage("§fComando: §e" + command);
        } catch (Exception e) {
            player.sendMessage("§c§lError al guardar el ítem.");
            plugin.getLogger().severe("Error al guardar ítem en tienda.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Formatea un nombre de material para hacerlo más legible
     */
    private String formatMaterialName(String materialName) {
        String[] words = materialName.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.length() > 0) {
                result.append(word.substring(0, 1).toUpperCase())
                        .append(word.substring(1))
                        .append(" ");
            }
        }

        return result.toString().trim();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Primer argumento - subcomandos
            List<String> subCommands = new ArrayList<>(Arrays.asList("tiempo", "time", "help", "ayuda"));

            if (sender.hasPermission("kenicompetitivo.admin.tienda")) {
                subCommands.addAll(Arrays.asList("reload", "recargar", "update", "actualizar",
                        "saveitem", "guardaritem", "give", "resetcooldown"));
            }

            String partialCmd = args[0].toLowerCase();
            completions.addAll(subCommands.stream()
                    .filter(subCmd -> subCmd.startsWith(partialCmd))
                    .collect(Collectors.toList()));
        } else if (args.length == 2) {
            // Segundo argumento - depende del subcomando
            switch (args[0].toLowerCase()) {
                case "give":
                case "resetcooldown":
                    // Lista de jugadores online
                    String partialName = args[1].toLowerCase();
                    completions.addAll(Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(partialName))
                            .collect(Collectors.toList()));
                    break;

                case "saveitem":
                case "guardaritem":
                    // Sugerencias para IDs de items
                    completions.add("item_" + System.currentTimeMillis());
                    break;
            }
        } else if (args.length == 3) {
            // Tercer argumento - depende del subcomando
            switch (args[0].toLowerCase()) {
                case "give":
                case "resetcooldown":
                    // Lista de IDs de items disponibles
                    String partialId = args[2].toLowerCase();
                    FileConfiguration config = plugin.getConfigManager().loadShopConfig();
                    if (config.contains("items")) {
                        ConfigurationSection itemsSection = config.getConfigurationSection("items");
                        if (itemsSection != null) {
                            completions.addAll(itemsSection.getKeys(false).stream()
                                    .filter(id -> id.toLowerCase().startsWith(partialId))
                                    .collect(Collectors.toList()));
                        }
                    }
                    break;
            }
        }

        return completions;
    }

    /**
     * Envía el mensaje de ayuda del comando tienda
     */
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Ayuda de Tienda Diaria ===");
        sender.sendMessage(ChatColor.YELLOW + "/tienda " + ChatColor.WHITE + "- Abrir la tienda diaria");
        sender.sendMessage(ChatColor.YELLOW + "/tienda tiempo " + ChatColor.WHITE + "- Ver tiempo hasta próxima actualización");

        if (sender.hasPermission("kenicompetitivo.admin.tienda")) {
            sender.sendMessage(ChatColor.GOLD + "--- Comandos de Administración ---");
            sender.sendMessage(ChatColor.YELLOW + "/tienda actualizar " + ChatColor.WHITE + "- Forzar actualización de la tienda");
            sender.sendMessage(ChatColor.YELLOW + "/tienda recargar " + ChatColor.WHITE + "- Recargar configuración de la tienda");
            sender.sendMessage(ChatColor.YELLOW + "/tienda saveitem <id> " + ChatColor.WHITE + "- Guardar ítem en mano en la tienda");
            sender.sendMessage(ChatColor.YELLOW + "/tienda give <jugador> <itemId> " + ChatColor.WHITE + "- Dar un ítem a un jugador");
            sender.sendMessage(ChatColor.YELLOW + "/tienda resetcooldown <jugador> [itemId] " + ChatColor.WHITE + "- Reiniciar tiempo de espera");
        }
    }

    /**
     * Entrega un ítem de la tienda a un jugador
     */
    private boolean giveItemToPlayer(Player player, String itemId) {
        FileConfiguration config = plugin.getConfigManager().loadShopConfig();
        ConfigurationSection itemSection = config.getConfigurationSection("items." + itemId);

        if (itemSection == null) {
            return false;
        }

        List<String> commands = itemSection.getStringList("commands");
        if (commands.isEmpty()) {
            return false;
        }

        for (String cmd : commands) {
            String processedCmd = cmd.replace("{player}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCmd);
        }

        return true;
    }

    /**
     * Clase para manejar conversaciones simples
     */
    private class ConversationHandler implements org.bukkit.event.Listener {
        private final Player player;
        private final java.util.function.Consumer<String> callback;

        public ConversationHandler(Player player, java.util.function.Consumer<String> callback) {
            this.player = player;
            this.callback = callback;
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }

        @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
        public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
            if (event.getPlayer().equals(player)) {
                event.setCancelled(true);
                String response = event.getMessage();

                // Desregistrar este listener
                org.bukkit.event.HandlerList.unregisterAll(this);

                // Ejecutar callback en el hilo principal
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(response));
            }
        }
    }
}
