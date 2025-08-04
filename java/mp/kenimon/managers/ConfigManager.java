package mp.kenimon.managers;

import mp.kenimon.Kenicompetitivo;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ConfigManager {

    private final Kenicompetitivo plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private File configFile;
    private File messagesFile;
    private String prefix;

    public ConfigManager(Kenicompetitivo plugin) {
        this.plugin = plugin;
        loadConfigs();
    }

    /**
     * Carga o crea los archivos de configuración
     */
    public void loadConfigs() {  // Cambiado de private a public
        // Cargar config.yml
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
        }
        config = plugin.getConfig();

        // Cargar messages.yml
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);

        // Cargar prefijo
        prefix = ChatColor.translateAlternateColorCodes('&', messages.getString("prefix", "&7[&6Kenicompetitivo&7] "));
    }

    /**
     * Recarga todas las configuraciones
     */
    public void reloadConfigs() {
        try {
            // Recargar configuración principal
            if (configFile.exists()) {
                config = YamlConfiguration.loadConfiguration(configFile);
                plugin.getLogger().info("Configuración principal recargada correctamente");
            }

            // Recargar mensajes
            if (messagesFile.exists()) {
                messages = YamlConfiguration.loadConfiguration(messagesFile);
                plugin.getLogger().info("Mensajes recargados correctamente");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error al recargar configuraciones: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Guarda la configuración
     */
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar config.yml: " + e.getMessage());
        }
    }

    /**
     * Guarda el archivo de mensajes
     */
    public void saveMessages() {
        try {
            messages.save(messagesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar messages.yml: " + e.getMessage());
        }
    }

    /**
     * Obtiene la configuración
     */
    public FileConfiguration getConfig() {
        return config;
    }

    /**
     * Obtiene la configuración de mensajes
     */
    public FileConfiguration getMessages() {
        return messages;
    }

    /**
     * Obtiene un mensaje formateado del archivo de mensajes
     */
    public String getFormattedMessage(String path, String defaultMessage) {
        String message = messages.getString(path, defaultMessage);

        // Asegurarse de que el prefijo esté cargado correctamente
        if (prefix == null || prefix.isEmpty()) {
            prefix = messages.getString("prefix", "&7[&6Kenicompetitivo&7] ");
            prefix = ChatColor.translateAlternateColorCodes('&', prefix);
        }

        // Reemplazar el prefijo y colores
        if (message.contains("{prefix}")) {
            message = message.replace("{prefix}", prefix);
        }

        // Reemplazar códigos de colores
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Obtiene el prefijo del plugin formateado
     */
    public String getPrefix() {
        if (prefix == null || prefix.isEmpty()) {
            prefix = messages.getString("prefix", "&7[&6Kenicompetitivo&7] ");
            prefix = ChatColor.translateAlternateColorCodes('&', prefix);
        }
        return prefix;
    }

    /**
     * Método específico para cargar el archivo tienda.yml
     */
    public FileConfiguration loadShopConfig() {
        File shopFile = new File(plugin.getDataFolder(), "tienda.yml");

        // Crear el archivo si no existe
        if (!shopFile.exists()) {
            try {
                plugin.saveResource("tienda.yml", false);
                plugin.getLogger().info("Archivo tienda.yml creado correctamente.");
            } catch (Exception e) {
                plugin.getLogger().severe("No se pudo crear tienda.yml desde el jar: " + e.getMessage());
                // Crear un archivo vacío si no se puede extraer del jar
                try {
                    shopFile.createNewFile();
                    plugin.getLogger().info("Archivo tienda.yml vacío creado.");
                } catch (Exception ex) {
                    plugin.getLogger().severe("No se pudo crear tienda.yml: " + ex.getMessage());
                }
            }
        }

        // Cargar el archivo
        YamlConfiguration shopConfig = YamlConfiguration.loadConfiguration(shopFile);
        plugin.getLogger().info("tienda.yml cargado con " + shopConfig.getKeys(false).size() + " secciones principales.");

        return shopConfig;
    }

    public String getFormattedMessage(String key) {
        return getFormattedMessage(key, "");  // Usar cadena vacía como valor predeterminado
    }
}
