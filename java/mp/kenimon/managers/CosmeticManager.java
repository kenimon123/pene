package mp.kenimon.managers;

import mp.kenimon.Kenicompetitivo;
import mp.kenimon.cosmetics.CosmeticEffect;
import mp.kenimon.cosmetics.KillEffect;
import mp.kenimon.cosmetics.ParticleEffect;
import mp.kenimon.cosmetics.SoundEffect;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class CosmeticManager {

    private final Kenicompetitivo plugin;
    private final Map<String, CosmeticEffect> availableEffects;
    private final Map<String, ParticleEffect> particleEffects = new HashMap<>();
    private final Map<String, SoundEffect> soundEffects = new HashMap<>();
    private final Map<String, KillEffect> killEffects = new HashMap<>();
    private final Map<UUID, BukkitTask> particleTasks;
    private final Map<UUID, Map<String, String>> playerEffectsCache;

    public CosmeticManager(Kenicompetitivo plugin) {
        this.plugin = plugin;
        this.availableEffects = new HashMap<>();
        this.particleTasks = new HashMap<>();
        this.playerEffectsCache = new HashMap<>();

        // Registrar efectos predeterminados
        registerDefaultEffects();

        // Iniciar tarea de partículas
        startParticleTask();
    }

    /**
     * Registra los efectos predeterminados disponibles
     */
    private void registerDefaultEffects() {
        // Efectos de partículas con diferentes patrones
        registerEffect(new ParticleEffect("particle_flame", "Llamas", Material.BLAZE_POWDER, Particle.FLAME));
        registerEffect(new ParticleEffect("particle_heart", "Corazones", Material.APPLE, Particle.HEART));
        registerEffect(new ParticleEffect("particle_portal", "Portal", Material.ENDER_PEARL, Particle.PORTAL));
        registerEffect(new ParticleEffect("particle_totem", "Totem", Material.TOTEM_OF_UNDYING, Particle.TOTEM));
        registerEffect(new ParticleEffect("particle_note", "Notas Musicales", Material.NOTE_BLOCK, Particle.NOTE));
        registerEffect(new ParticleEffect("particle_lava", "Lava", Material.LAVA_BUCKET, Particle.LAVA));

        // Partículas con patrones avanzados
        registerEffect(new ParticleEffect("particle_circle", "Círculo", Material.ENDER_EYE,
                Particle.END_ROD, 0, 0, 0, 0, 1, ParticleEffect.ParticlePattern.CIRCLE));

        registerEffect(new ParticleEffect("particle_helix", "Hélice", Material.SPECTRAL_ARROW,
                Particle.SPELL_WITCH, 0, 0, 0, 0, 1, ParticleEffect.ParticlePattern.HELIX));

        registerEffect(new ParticleEffect("particle_wings", "Alas", Material.ELYTRA,
                Particle.DRAGON_BREATH, 0, 0, 0, 0, 1, ParticleEffect.ParticlePattern.WINGS));

        registerEffect(new ParticleEffect("particle_crown", "Corona", Material.GOLDEN_HELMET,
                Particle.CRIT, 0, 0, 0, 0, 1, ParticleEffect.ParticlePattern.CROWN));

        registerEffect(new ParticleEffect("particle_orbit", "Órbita", Material.CLOCK,
                Particle.PORTAL, 0, 0, 0, 0, 1, ParticleEffect.ParticlePattern.ORBIT));

        registerEffect(new ParticleEffect("particle_aura", "Aura", Material.BEACON,
                Particle.END_ROD, 0, 0, 0, 0, 1, ParticleEffect.ParticlePattern.AURA));

        registerEffect(new ParticleEffect("particle_trail", "Rastro", Material.SOUL_LANTERN,
                Particle.SOUL_FIRE_FLAME, 0, 0, 0, 0, 1, ParticleEffect.ParticlePattern.TRAIL));

        registerEffect(new ParticleEffect("particle_soul", "Almas", Material.SOUL_SAND, Particle.SOUL));
        registerEffect(new ParticleEffect("particle_ash", "Cenizas", Material.COAL, Particle.ASH));
        registerEffect(new ParticleEffect("particle_enchant", "Encantamiento", Material.ENCHANTING_TABLE, Particle.ENCHANTMENT_TABLE));
        registerEffect(new ParticleEffect("particle_slime", "Slime", Material.SLIME_BLOCK, Particle.SLIME));
        registerEffect(new ParticleEffect("particle_water", "Agua", Material.WATER_BUCKET, Particle.WATER_SPLASH));
        registerEffect(new ParticleEffect("particle_snow", "Nieve", Material.SNOW_BLOCK, Particle.SNOWFLAKE));
        registerEffect(new ParticleEffect("particle_dust", "Polvo Carmesí", Material.REDSTONE, Particle.DUST_COLOR_TRANSITION));
        registerEffect(new ParticleEffect("particle_wax", "Cera", Material.HONEYCOMB, Particle.WAX_ON));

        // Efectos de sonido
        registerEffect(new SoundEffect("sound_levelup", "Level Up", Material.EXPERIENCE_BOTTLE, Sound.ENTITY_PLAYER_LEVELUP));
        registerEffect(new SoundEffect("sound_dragon", "Rugido de Dragón", Material.DRAGON_HEAD, Sound.ENTITY_ENDER_DRAGON_GROWL));
        registerEffect(new SoundEffect("sound_wither", "Wither", Material.WITHER_SKELETON_SKULL, Sound.ENTITY_WITHER_SPAWN));
        registerEffect(new SoundEffect("sound_anvil", "Yunque", Material.ANVIL, Sound.BLOCK_ANVIL_LAND));

        // Efectos de sonido existentes
        registerEffect(new SoundEffect("sound_wolf", "Aullido de Lobo", Material.BONE, Sound.ENTITY_WOLF_HOWL));
        registerEffect(new SoundEffect("sound_blaze", "Blaze", Material.BLAZE_ROD, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.8f));
        registerEffect(new SoundEffect("sound_ghast", "Ghast", Material.GHAST_TEAR, Sound.ENTITY_GHAST_SCREAM, 0.8f, 1.2f));
        registerEffect(new SoundEffect("sound_goat", "Grito de Cabra", Material.GOAT_HORN, Sound.ENTITY_GOAT_SCREAMING_AMBIENT, 1.0f, 1.0f));
        registerEffect(new SoundEffect("sound_lightning", "Rayo", Material.LIGHTNING_ROD, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 0.8f));
        registerEffect(new SoundEffect("sound_bell", "Campana", Material.BELL, Sound.BLOCK_BELL_USE, 1.0f, 1.0f));
        registerEffect(new SoundEffect("sound_ender", "Enderman", Material.ENDER_PEARL, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f));
        registerEffect(new SoundEffect("sound_ravager", "Ravager", Material.RAVAGER_SPAWN_EGG, Sound.ENTITY_RAVAGER_ROAR, 0.8f, 1.0f));
        registerEffect(new SoundEffect("sound_fox", "Zorro", Material.SWEET_BERRIES, Sound.ENTITY_FOX_SCREECH, 1.0f, 1.2f));
        registerEffect(new SoundEffect("sound_totem", "Totem", Material.TOTEM_OF_UNDYING, Sound.ITEM_TOTEM_USE, 0.8f, 1.5f));
        registerEffect(new SoundEffect("sound_raid", "Victoria de Raid", Material.EMERALD, Sound.EVENT_RAID_HORN, 0.7f, 1.0f));
        registerEffect(new SoundEffect("sound_warden", "Warden", Material.SCULK_SENSOR, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 1.0f));
        registerEffect(new SoundEffect("sound_elder", "Elder Guardian", Material.PRISMARINE_CRYSTALS, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.7f, 1.2f));

        // Nuevos efectos de sonido (adicionales)
        registerEffect(new SoundEffect("sound_ping_victory", "Victoria de Ping", Material.ENDER_EYE,
                Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f));
        registerEffect(new SoundEffect("sound_nether_portal", "Portal del Nether", Material.OBSIDIAN,
                Sound.BLOCK_PORTAL_AMBIENT, 1.0f, 0.7f));
        registerEffect(new SoundEffect("sound_slime_squish", "Slime Aplastado", Material.SLIME_BALL,
                Sound.ENTITY_SLIME_SQUISH, 1.0f, 0.8f));
        registerEffect(new SoundEffect("sound_dolphin_happy", "Delfín Feliz", Material.TROPICAL_FISH,
                Sound.ENTITY_DOLPHIN_PLAY, 1.0f, 1.0f));
        registerEffect(new SoundEffect("sound_zombie_angry", "Zombie Enfadado", Material.ZOMBIE_HEAD,
                Sound.ENTITY_ZOMBIE_AMBIENT, 1.0f, 0.7f));
        registerEffect(new SoundEffect("sound_cat_meow", "Maullido", Material.STRING,
                Sound.ENTITY_CAT_PURREOW, 1.0f, 1.5f));
        registerEffect(new SoundEffect("sound_villager_no", "Villager Dice No", Material.EMERALD,
                Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f));
        registerEffect(new SoundEffect("sound_anvil_land", "Yunque Caído", Material.ANVIL,
                Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f));
        registerEffect(new SoundEffect("sound_puffer_inflate", "Pez Globo", Material.PUFFERFISH,
                Sound.ENTITY_PUFFER_FISH_BLOW_UP, 1.0f, 1.0f));
        registerEffect(new SoundEffect("sound_witch_laugh", "Risa de Bruja", Material.BREWING_STAND,
                Sound.ENTITY_WITCH_CELEBRATE, 1.0f, 1.0f));

        // Efectos de kill básicos
        registerEffect(new KillEffect("kill_lightning", "Rayo", Material.LIGHTNING_ROD, KillEffect.KillEffectType.LIGHTNING, plugin));
        registerEffect(new KillEffect("kill_explosion", "Explosión", Material.TNT, KillEffect.KillEffectType.EXPLOSION, plugin));
        registerEffect(new KillEffect("kill_firework", "Fuegos Artificiales", Material.FIREWORK_ROCKET, KillEffect.KillEffectType.FIREWORK, plugin));
        registerEffect(new KillEffect("kill_smoke", "Humo", Material.CAMPFIRE, KillEffect.KillEffectType.SMOKE, plugin));
        registerEffect(new KillEffect("kill_totem", "Totem", Material.TOTEM_OF_UNDYING, KillEffect.KillEffectType.TOTEM, plugin));
        registerEffect(new KillEffect("kill_vortex", "Vórtice", Material.ENDER_PEARL, KillEffect.KillEffectType.VORTEX, plugin));
        registerEffect(new KillEffect("kill_blackhole", "Agujero Negro", Material.BLACK_CONCRETE, KillEffect.KillEffectType.BLACKHOLE, plugin));
        registerEffect(new KillEffect("kill_freeze", "Congelación", Material.BLUE_ICE, KillEffect.KillEffectType.FREEZE, plugin));

        // Efectos de kill existentes - NOMBRES CORREGIDOS PARA COINCIDIR CON CONFIG
        registerEffect(new KillEffect("bro_respeta", "Bro Respeta", Material.ARMOR_STAND, KillEffect.KillEffectType.BRO_RESPETA, plugin));
        registerEffect(new KillEffect("phantom_strike", "Ataque Fantasma", Material.PHANTOM_MEMBRANE, KillEffect.KillEffectType.PHANTOM_STRIKE, plugin));
        registerEffect(new KillEffect("lava_eruption", "Erupción de Lava", Material.LAVA_BUCKET, KillEffect.KillEffectType.LAVA_ERUPTION, plugin));
        registerEffect(new KillEffect("musical_death", "Muerte Musical", Material.JUKEBOX, KillEffect.KillEffectType.MUSICAL_DEATH, plugin));
        registerEffect(new KillEffect("kill_thunder", "Tormenta", Material.TRIDENT, KillEffect.KillEffectType.THUNDER_STORM, plugin));
        registerEffect(new KillEffect("kill_meteor", "Meteoro", Material.MAGMA_BLOCK, KillEffect.KillEffectType.METEOR, plugin));
        registerEffect(new KillEffect("kill_ghost", "Fantasma", Material.SKELETON_SKULL, KillEffect.KillEffectType.GHOST, plugin));
        registerEffect(new KillEffect("kill_soul", "Robo de Alma", Material.SOUL_LANTERN, KillEffect.KillEffectType.SOUL_STEAL, plugin));

        // Nuevos efectos de muerte - NOMBRES CORREGIDOS PARA COINCIDIR CON CONFIG
        registerEffect(new KillEffect("void_prison", "Prisión del Vacío", Material.END_GATEWAY, KillEffect.KillEffectType.VOID_PRISON, plugin));
        registerEffect(new KillEffect("rainbow_explosion", "Explosión Arcoíris", Material.FIREWORK_STAR, KillEffect.KillEffectType.RAINBOW_EXPLOSION, plugin));
        registerEffect(new KillEffect("thanos_snap", "Chasquido de Thanos", Material.DRAGON_BREATH, KillEffect.KillEffectType.THANOS_SNAP, plugin));
        registerEffect(new KillEffect("ice_prison", "Prisión de Hielo", Material.BLUE_ICE, KillEffect.KillEffectType.ICE_PRISON, plugin));
        registerEffect(new KillEffect("treasure_explosion", "Explosión de Tesoro", Material.GOLD_INGOT, KillEffect.KillEffectType.TREASURE_EXPLOSION, plugin));
        registerEffect(new KillEffect("rocket_launch", "Lanzamiento Espacial", Material.FIREWORK_ROCKET, KillEffect.KillEffectType.ROCKET_LAUNCH, plugin));
        registerEffect(new KillEffect("kill_snap", "Desaparición", Material.GLOWSTONE_DUST, KillEffect.KillEffectType.SNAP_VANISH, plugin));

        registerAdditionalSoundEffects();

        // Cargar efectos personalizados desde la configuración
        loadCustomEffectsFromConfig();
    }

    /**
     * Carga efectos personalizados definidos en la config.yml
     */
    private void loadCustomEffectsFromConfig() {
        FileConfiguration config = plugin.getConfigManager().getConfig();

        if (!config.contains("cosmetics.custom_effects")) {
            return;
        }

        ConfigurationSection customSection = config.getConfigurationSection("cosmetics.custom_effects");
        if (customSection == null) {
            return;
        }

        for (String key : customSection.getKeys(false)) {
            String type = config.getString("cosmetics.custom_effects." + key + ".type");
            String name = config.getString("cosmetics.custom_effects." + key + ".name", key);
            String iconStr = config.getString("cosmetics.custom_effects." + key + ".icon", "DIAMOND");
            Material icon = Material.matchMaterial(iconStr);
            if (icon == null) icon = Material.DIAMOND;

            if (type == null) continue;

            if (type.equals("particle")) {
                String particleStr = config.getString("cosmetics.custom_effects." + key + ".particle", "FLAME");
                Particle particle;
                try {
                    particle = Particle.valueOf(particleStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    continue;
                }

                String patternStr = config.getString("cosmetics.custom_effects." + key + ".pattern", "SIMPLE");
                ParticleEffect.ParticlePattern pattern;
                try {
                    pattern = ParticleEffect.ParticlePattern.valueOf(patternStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    pattern = ParticleEffect.ParticlePattern.SIMPLE;
                }

                double offsetX = config.getDouble("cosmetics.custom_effects." + key + ".offset_x", 0.5);
                double offsetY = config.getDouble("cosmetics.custom_effects." + key + ".offset_y", 0.5);
                double offsetZ = config.getDouble("cosmetics.custom_effects." + key + ".offset_z", 0.5);
                double speed = config.getDouble("cosmetics.custom_effects." + key + ".speed", 0.01);
                int count = config.getInt("cosmetics.custom_effects." + key + ".count", 10);

                registerEffect(new ParticleEffect(
                        "custom_" + key,
                        name,
                        icon,
                        particle,
                        offsetX,
                        offsetY,
                        offsetZ,
                        speed,
                        count,
                        pattern
                ));

            } else if (type.equals("sound")) {
                String soundStr = config.getString("cosmetics.custom_effects." + key + ".sound", "ENTITY_PLAYER_LEVELUP");
                Sound sound;
                try {
                    sound = Sound.valueOf(soundStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Sonido inválido en la configuración: " + soundStr);
                    continue;
                }

                float volume = (float) config.getDouble("cosmetics.custom_effects." + key + ".volume", 1.0);
                float pitch = (float) config.getDouble("cosmetics.custom_effects." + key + ".pitch", 1.0);

                registerEffect(new SoundEffect(
                        "custom_" + key,
                        name,
                        icon,
                        sound,
                        volume,
                        pitch
                ));
            }
        }
    }

    /**
     * Registra un nuevo efecto cosmético
     */
    public void registerEffect(CosmeticEffect effect) {
        availableEffects.put(effect.getId(), effect);
    }

    /**
     * Obtiene un efecto cosmético por su ID
     * @param id El ID del efecto
     * @return El efecto o null si no existe
     */
    public CosmeticEffect getEffectById(String id) {
        // Buscar directamente en los mapas (más eficiente)
        if (particleEffects.containsKey(id)) {
            return particleEffects.get(id);
        }

        if (soundEffects.containsKey(id)) {
            return soundEffects.get(id);
        }

        if (killEffects.containsKey(id)) {
            return killEffects.get(id);
        }

        // Como alternativa, buscar en availableEffects que contiene todos los efectos
        return availableEffects.get(id);
    }
    /**
     * Abre el menú principal de cosméticos para un jugador
     */
    public void openCosmeticsMenu(Player player) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        String title = ChatColor.translateAlternateColorCodes('&',
                config.getString("cosmetics.menu.title", "&b&lCosméticos"));
        int rows = config.getInt("cosmetics.menu.rows", 4);

        Inventory menu = Bukkit.createInventory(null, rows * 9, title);

        // Cargar categorías desde la configuración
        ConfigurationSection categories = config.getConfigurationSection("cosmetics.menu.categories");
        if (categories != null) {
            for (String category : categories.getKeys(false)) {
                // Verificar si la categoría está habilitada - IMPORTANTE: comprobar la ruta correcta
                // Antes estaba comprobando "cosmetics.categories.particle.enabled" que no existe en la config
                // Ahora comprobamos "cosmetics.menu.categories.particle.enabled" que es la correcta
                boolean enabled = config.getBoolean("cosmetics.menu.categories." + category + ".enabled", true);
                if (!enabled) {
                    // Si la categoría está desactivada, saltamos a la siguiente iteración
                    continue;
                }

                // Solo si la categoría está habilitada, añadimos su botón al menú
                String itemStr = config.getString("cosmetics.menu.categories." + category + ".item", "DIAMOND");
                String name = config.getString("cosmetics.menu.categories." + category + ".name", category);
                List<String> loreConfig = config.getStringList("cosmetics.menu.categories." + category + ".lore");
                int position = config.getInt("cosmetics.menu.categories." + category + ".position", 0);

                Material material = Material.matchMaterial(itemStr);
                if (material == null) material = Material.DIAMOND;

                ItemStack item = new ItemStack(material);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

                // Procesar lore
                List<String> lore = new ArrayList<>();
                for (String line : loreConfig) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(lore);

                // Añadir datos para identificar categoría
                NamespacedKey key = new NamespacedKey(plugin, "cosmetic_category");
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, category);

                item.setItemMeta(meta);
                menu.setItem(position, item);
            }
        }

        // Agregar decoración si está habilitada
        if (config.getBoolean("cosmetics.menu.decoration.enabled", true)) {
            String itemStr = config.getString("cosmetics.menu.decoration.item", "GRAY_STAINED_GLASS_PANE");
            String name = config.getString("cosmetics.menu.decoration.name", " ");

            Material material = Material.matchMaterial(itemStr);
            if (material == null) material = Material.GRAY_STAINED_GLASS_PANE;

            ItemStack filler = new ItemStack(material);
            ItemMeta meta = filler.getItemMeta();
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            filler.setItemMeta(meta);

            // Rellenar espacios vacíos
            for (int i = 0; i < menu.getSize(); i++) {
                if (menu.getItem(i) == null) {
                    menu.setItem(i, filler);
                }
            }
        }

        player.openInventory(menu);
    }

    /**
     * Abre el menú de una categoría específica de cosméticos
     */
    public void openCategoryMenu(Player player, String category) {
        FileConfiguration config = plugin.getConfigManager().getConfig();
        FileConfiguration messages = plugin.getConfigManager().getMessages();

        // Obtener nombre de categoría de la configuración de mensajes
        String categoryName = messages.getString("cosmetics.menu.category_" + category,
                config.getString("cosmetics.menu.categories." + category + ".name", category));

        String titleFormat = config.getString("cosmetics.menu.category_menu.title_format", "&b{category_name}");
        String title = ChatColor.translateAlternateColorCodes('&',
                titleFormat.replace("{category_name}", categoryName));

        int rows = config.getInt("cosmetics.menu.category_menu.rows", 6);
        Inventory menu = Bukkit.createInventory(null, rows * 9, title);

        // Agregar botón para volver al menú principal
        int backPosition = config.getInt("cosmetics.menu.category_menu.back_button.position", 0);
        String backItemStr = config.getString("cosmetics.menu.category_menu.back_button.item", "BARRIER");

        Material backMaterial = Material.matchMaterial(backItemStr);
        if (backMaterial == null) backMaterial = Material.BARRIER;

        ItemStack backButton = new ItemStack(backMaterial);
        ItemMeta backMeta = backButton.getItemMeta();

        String backName = config.getString("cosmetics.menu.category_menu.back_button.name", "&cVolver");
        backMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', backName));

        List<String> backLoreConfig = config.getStringList("cosmetics.menu.category_menu.back_button.lore");
        List<String> backLore = new ArrayList<>();
        for (String line : backLoreConfig) {
            backLore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        backMeta.setLore(backLore);

        // Agregar datos para identificar el botón de volver
        NamespacedKey backKey = new NamespacedKey(plugin, "cosmetic_action");
        backMeta.getPersistentDataContainer().set(backKey, PersistentDataType.STRING, "back");

        backButton.setItemMeta(backMeta);
        menu.setItem(backPosition, backButton);

        // Agregar botón para deseleccionar
        int nonePosition = config.getInt("cosmetics.menu.category_menu.none_button.position", 9);
        String noneItemStr = config.getString("cosmetics.menu.category_menu.none_button.item", "BARRIER");

        Material noneMaterial = Material.matchMaterial(noneItemStr);
        if (noneMaterial == null) noneMaterial = Material.BARRIER;

        ItemStack noneButton = new ItemStack(noneMaterial);
        ItemMeta noneMeta = noneButton.getItemMeta();

        String noneName = config.getString("cosmetics.menu.category_menu.none_button.name", "&cNinguno");
        noneMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', noneName));

        List<String> noneLoreConfig = config.getStringList("cosmetics.menu.category_menu.none_button.lore");
        List<String> noneLore = new ArrayList<>();
        for (String line : noneLoreConfig) {
            noneLore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        noneMeta.setLore(noneLore);

        // Agregar datos para identificar el botón de ninguno
        NamespacedKey noneKey = new NamespacedKey(plugin, "cosmetic_action");
        noneMeta.getPersistentDataContainer().set(noneKey, PersistentDataType.STRING, "none");

        NamespacedKey categoryKey = new NamespacedKey(plugin, "cosmetic_category");
        noneMeta.getPersistentDataContainer().set(categoryKey, PersistentDataType.STRING, category);

        noneButton.setItemMeta(noneMeta);
        menu.setItem(nonePosition, noneButton);

        // Añadir efectos disponibles
        List<Integer> patternSlots = config.getIntegerList("cosmetics.menu.category_menu.pattern");
        if (patternSlots.isEmpty()) {
            int startPos = config.getInt("cosmetics.menu.category_menu.start_position", 18);
            for (int i = 0; i < 27; i++) {
                patternSlots.add(startPos + i);
            }
        }

        // Obtener efectos desbloqueados para este jugador
        List<String> unlockedEffects = getUnlockedEffects(player.getUniqueId());

        // Obtener efecto seleccionado actualmente
        String selectedEffect = getSelectedEffect(player.getUniqueId(), category);

        // Filtrar efectos por categoría y añadirlos al menú
        List<CosmeticEffect> categoryEffects = new ArrayList<>();
        for (CosmeticEffect effect : availableEffects.values()) {
            if (effect.getId().startsWith(category + "_")) {
                categoryEffects.add(effect);
            }
        }

        // Ordenar por nombre
        categoryEffects.sort(Comparator.comparing(CosmeticEffect::getName));

        // Añadir al menú
        int slot = 0;
        for (CosmeticEffect effect : categoryEffects) {
            if (slot >= patternSlots.size()) break;

            boolean isUnlocked = unlockedEffects.contains(effect.getId());
            boolean isSelected = effect.getId().equals(selectedEffect);

            ItemStack effectItem = new ItemStack(effect.getIcon());
            ItemMeta meta = effectItem.getItemMeta();

            // Formatear nombre según estado
            String nameFormat;
            String configPath;

            if (isSelected) {
                configPath = "cosmetics.menu.category_menu.item_format.selected";
            } else if (isUnlocked) {
                configPath = "cosmetics.menu.category_menu.item_format.unlocked";
            } else {
                configPath = "cosmetics.menu.category_menu.item_format.locked";
            }

            nameFormat = config.getString(configPath + ".name", "&a{effect_name}");
            nameFormat = nameFormat.replace("{effect_name}", effect.getName());
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', nameFormat));

            // Formatear lore según estado
            List<String> loreConfig = config.getStringList(configPath + ".lore");
            List<String> lore = new ArrayList<>();

            for (String line : loreConfig) {
                // Obtener requisito de desbloqueo
                String requirement = getUnlockRequirement(effect.getId());
                line = line.replace("{requirement}", requirement);
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }

            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

            // Agregar datos para identificar el efecto
            NamespacedKey effectKey = new NamespacedKey(plugin, "cosmetic_effect");
            meta.getPersistentDataContainer().set(effectKey, PersistentDataType.STRING, effect.getId());

            meta.getPersistentDataContainer().set(categoryKey, PersistentDataType.STRING, category);

            effectItem.setItemMeta(meta);
            menu.setItem(patternSlots.get(slot), effectItem);

            slot++;
        }

        // Rellenar espacios vacíos con decoración
        if (config.getBoolean("cosmetics.menu.decoration.enabled", true)) {
            String itemStr = config.getString("cosmetics.menu.decoration.item", "GRAY_STAINED_GLASS_PANE");
            String name = config.getString("cosmetics.menu.decoration.name", " ");

            Material material = Material.matchMaterial(itemStr);
            if (material == null) material = Material.GRAY_STAINED_GLASS_PANE;

            ItemStack filler = new ItemStack(material);
            ItemMeta meta = filler.getItemMeta();
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            filler.setItemMeta(meta);

            // Rellenar espacios vacíos
            for (int i = 0; i < menu.getSize(); i++) {
                if (menu.getItem(i) == null) {
                    menu.setItem(i, filler);
                }
            }
        }

        player.openInventory(menu);
    }

    /**
     * Obtiene el requisito para desbloquear un efecto
     */
    private String getUnlockRequirement(String effectId) {
        FileConfiguration config = plugin.getConfigManager().getConfig();

        ConfigurationSection unlocks = config.getConfigurationSection("cosmetics.unlocks.streaks");
        if (unlocks == null) return "Desconocido";

        for (String streakStr : unlocks.getKeys(false)) {
            List<String> effects = config.getStringList("cosmetics.unlocks.streaks." + streakStr + ".effects");
            if (effects.contains(effectId)) {
                return "Racha de " + streakStr;
            }
        }

        return "Desconocido";
    }

    /**
     * Maneja un clic en el menú de cosméticos
     */
    public void handleMenuClick(Player player, ItemStack clickedItem) {
        if (clickedItem == null) return;

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey categoryKey = new NamespacedKey(plugin, "cosmetic_category");
        NamespacedKey effectKey = new NamespacedKey(plugin, "cosmetic_effect");
        NamespacedKey actionKey = new NamespacedKey(plugin, "cosmetic_action");

        // Verificar si es un ítem de categoría
        if (container.has(categoryKey, PersistentDataType.STRING) &&
                !container.has(effectKey, PersistentDataType.STRING) &&
                !container.has(actionKey, PersistentDataType.STRING)) {

            String category = container.get(categoryKey, PersistentDataType.STRING);
            openCategoryMenu(player, category);
            return;
        }

        // Verificar si es un botón de acción
        if (container.has(actionKey, PersistentDataType.STRING)) {
            String action = container.get(actionKey, PersistentDataType.STRING);

            if (action.equals("back")) {
                openCosmeticsMenu(player);
                return;
            }

            if (action.equals("none") && container.has(categoryKey, PersistentDataType.STRING)) {
                String category = container.get(categoryKey, PersistentDataType.STRING);
                deselectEffect(player, category);
                return;
            }
        }

        // Verificar si es un efecto
        if (container.has(effectKey, PersistentDataType.STRING) &&
                container.has(categoryKey, PersistentDataType.STRING)) {

            String effectId = container.get(effectKey, PersistentDataType.STRING);
            String category = container.get(categoryKey, PersistentDataType.STRING);

            // Verificar si el efecto está desbloqueado
            if (hasUnlockedEffect(player.getUniqueId(), effectId)) {
                toggleEffect(player, category, effectId);
            } else {
                sendUnlockRequiredMessage(player, effectId);
            }
        }
    }

    /**
     * Desactiva un efecto para un jugador
     */
    private void deselectEffect(Player player, String category) {
        FileConfiguration messages = plugin.getConfigManager().getMessages();
        plugin.getDatabaseManager().selectCosmetic(player.getUniqueId(), category, null);

        // Actualizar caché
        if (playerEffectsCache.containsKey(player.getUniqueId())) {
            playerEffectsCache.get(player.getUniqueId()).remove(category);
        }

        // Mensaje de desactivación
        String message = messages.getString("cosmetics.deselected", "&aHas desactivado los efectos de {category}");
        message = message.replace("{category}", category);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));

        // Refrescar menú
        openCategoryMenu(player, category);
    }

    /**
     * Activa/desactiva un efecto para un jugador
     */
    private void toggleEffect(Player player, String category, String effectId) {
        FileConfiguration messages = plugin.getConfigManager().getMessages();
        String currentEffect = getSelectedEffect(player.getUniqueId(), category);

        // Si ya está seleccionado, deseleccionar
        if (effectId.equals(currentEffect)) {
            plugin.getDatabaseManager().selectCosmetic(player.getUniqueId(), category, null);

            // Actualizar caché
            if (playerEffectsCache.containsKey(player.getUniqueId())) {
                playerEffectsCache.get(player.getUniqueId()).remove(category);
            }

            // Mensaje de desactivación
            String message = messages.getString("cosmetics.deselected", "&aHas desactivado los efectos de {category}");
            message = message.replace("{category}", category);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        } else {
            // Seleccionar nuevo efecto
            plugin.getDatabaseManager().selectCosmetic(player.getUniqueId(), category, effectId);

            // Actualizar caché
            if (!playerEffectsCache.containsKey(player.getUniqueId())) {
                playerEffectsCache.put(player.getUniqueId(), new HashMap<>());
            }
            playerEffectsCache.get(player.getUniqueId()).put(category, effectId);

            // Mensaje de selección
            CosmeticEffect effect = getEffectById(effectId);
            String name = (effect != null) ? effect.getName() : effectId;

            String message = messages.getString("cosmetics.selected", "&aHas seleccionado el cosmético: &e{name}&a!");
            message = message.replace("{name}", name);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));

            // NUEVO: Reproducir un sonido al equipar
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

            // NUEVO: Si es un efecto de sonido, reproducirlo como muestra
            if (effect instanceof SoundEffect) {
                SoundEffect soundEffect = (SoundEffect) effect;
                Bukkit.getScheduler().runTaskLater(plugin, () -> soundEffect.play(player), 5L);
            }
        }

        // Refrescar menú
        openCategoryMenu(player, category);
    }

    /**
     * Envía un mensaje al jugador sobre los requisitos para desbloquear un efecto
     */
    private void sendUnlockRequiredMessage(Player player, String effectId) {
        FileConfiguration messages = plugin.getConfigManager().getMessages();
        String requirement = getUnlockRequirement(effectId);

        String message = messages.getString("cosmetics.not_unlocked", "&cNo has desbloqueado este cosmético.");
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7Requiere: &e" + requirement));
    }

    /**
     * Inicia la tarea de partículas para todos los jugadores
     */
    private void startParticleTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.getConfigManager().getConfig().getBoolean("cosmetics.options.particles.enabled", true)) {
                    return;
                }

                // Solo verificar jugadores que tengan efecto seleccionado
                for (Player player : Bukkit.getOnlinePlayers()) {
                    // Verificar rápidamente en caché si tiene efectos antes de procesar
                    if (hasParticleEffectSelected(player.getUniqueId())) {
                        applyParticleEffect(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Cada 1 segundo
    }

    private boolean hasParticleEffectSelected(UUID uuid) {
        // Verificar en caché primero para evitar consultas a BD
        if (playerEffectsCache.containsKey(uuid) &&
                playerEffectsCache.get(uuid).containsKey("particle")) {
            return true;
        }
        return false;
    }

    /**
     * Aplica el efecto de partículas seleccionado a un jugador
     */
    private void applyParticleEffect(Player player) {
        String particleEffectId = getSelectedEffect(player.getUniqueId(), "particle");
        if (particleEffectId == null) return;

        CosmeticEffect effect = getEffectById(particleEffectId);
        if (!(effect instanceof ParticleEffect)) return;

        ParticleEffect particleEffect = (ParticleEffect) effect;

        // Optimización: Solo mostrar a jugadores en un radio controlado por config
        int visibilityRange = plugin.getConfigManager().getConfig().getInt("cosmetics.options.particles.visibility_range", 32);

        // Crear colección de jugadores cercanos una sola vez
        List<Player> nearbyPlayers = null;

        if (visibilityRange > 0) {
            // Corrección para usar el método getPlayersInRange de manera compatible
            nearbyPlayers = new ArrayList<>();
            Location playerLoc = player.getLocation();

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getWorld().equals(playerLoc.getWorld()) &&
                        p.getLocation().distance(playerLoc) <= visibilityRange) {
                    nearbyPlayers.add(p);
                }
            }
        }

        // Si hay jugadores cercanos, mostrar partículas
        if (nearbyPlayers == null || !nearbyPlayers.isEmpty()) {
            // Usar directamente el efecto en lugar de tareas adicionales
            if (visibilityRange <= 0) {
                particleEffect.play(player);
            } else {
                for (Player nearby : nearbyPlayers) {
                    if (nearby != player) { // Evitar procesar al propio jugador dos veces
                        particleEffect.playForPlayer(nearby, player);
                    }
                }
            }
        }
    }

    /**
     * Reproduce el efecto de sonido seleccionado por un jugador
     */
    public void playSoundEffectForPlayer(Player player) {
        String soundEffectId = getSelectedEffect(player.getUniqueId(), "sound");
        if (soundEffectId == null) return;

        CosmeticEffect effect = getEffectById(soundEffectId);
        if (effect instanceof SoundEffect) {
            SoundEffect soundEffect = (SoundEffect) effect;
            soundEffect.play(player);
        }
    }

    /**
     * Procesa un evento de asesinato para aplicar efectos de kill y sonido
     * VERSIÓN CORREGIDA CON LOGGING
     */
    public void handleKillEvent(Player killer, Player victim) {
        if (killer == null || victim == null) {
            plugin.getLogger().warning("handleKillEvent llamado con killer o victim null");
            return;
        }

        // NUEVO: Log para debugging
        if (plugin.getConfigManager().getConfig().getBoolean("cosmetics.debug", false)) {
            plugin.getLogger().info("Procesando kill event: " + killer.getName() + " mató a " + victim.getName());
        }

        // Verificar y aplicar efecto de kill seleccionado
        String killEffectId = getSelectedEffect(killer.getUniqueId(), "kill");
        if (killEffectId != null) {
            CosmeticEffect effect = getEffectById(killEffectId);
            if (effect instanceof KillEffect) {
                try {
                    KillEffect killEffect = (KillEffect) effect;
                    killEffect.play(victim, killer);
                    
                    if (plugin.getConfigManager().getConfig().getBoolean("cosmetics.debug", false)) {
                        plugin.getLogger().info("Ejecutado efecto de kill: " + killEffectId + " para " + killer.getName());
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Error ejecutando efecto de kill " + killEffectId + ": " + e.getMessage());
                }
            } else {
                plugin.getLogger().warning("Efecto de kill " + killEffectId + " no es una instancia de KillEffect");
            }
        } else {
            if (plugin.getConfigManager().getConfig().getBoolean("cosmetics.debug", false)) {
                plugin.getLogger().info("No hay efecto de kill seleccionado para " + killer.getName());
            }
        }

        // Reproducir sonido seleccionado para el killer cuando mata a alguien
        String soundEffectId = getSelectedEffect(killer.getUniqueId(), "sound");
        if (soundEffectId != null) {
            CosmeticEffect effect = getEffectById(soundEffectId);
            if (effect instanceof SoundEffect) {
                try {
                    SoundEffect soundEffect = (SoundEffect) effect;
                    soundEffect.playOnKill(killer);
                    
                    if (plugin.getConfigManager().getConfig().getBoolean("cosmetics.debug", false)) {
                        plugin.getLogger().info("Ejecutado efecto de sonido: " + soundEffectId + " para " + killer.getName());
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Error ejecutando efecto de sonido " + soundEffectId + ": " + e.getMessage());
                }
            } else {
                plugin.getLogger().warning("Efecto de sonido " + soundEffectId + " no es una instancia de SoundEffect");
            }
        } else {
            if (plugin.getConfigManager().getConfig().getBoolean("cosmetics.debug", false)) {
                plugin.getLogger().info("No hay efecto de sonido seleccionado para " + killer.getName());
            }
        }
    }

    /**
     * Verifica si un jugador tiene desbloqueado un efecto
     */
    public boolean hasUnlockedEffect(UUID uuid, String effectId) {
        return plugin.getDatabaseManager().hasUnlockedCosmetic(uuid, effectId);
    }

    /**
     * Obtiene la lista de efectos desbloqueados por un jugador
     */
    public List<String> getUnlockedEffects(UUID uuid) {
        return plugin.getDatabaseManager().getUnlockedCosmetics(uuid);
    }

    /**
     * Obtiene el efecto seleccionado por un jugador en una categoría
     */
    public String getSelectedEffect(UUID uuid, String category) {
        // Consultar caché primero
        if (playerEffectsCache.containsKey(uuid)) {
            String cachedEffect = playerEffectsCache.get(uuid).get(category);
            if (cachedEffect != null) {
                return cachedEffect;
            }
        }

        // Si no está en caché, consultar base de datos
        String effect = plugin.getDatabaseManager().getSelectedCosmetic(uuid, category);

        // Actualizar caché
        if (effect != null) {
            if (!playerEffectsCache.containsKey(uuid)) {
                playerEffectsCache.put(uuid, new HashMap<>());
            }
            playerEffectsCache.get(uuid).put(category, effect);
        }

        return effect;
    }

    /**
     * Desbloquea un efecto para un jugador
     */
    public void unlockEffect(UUID uuid, String effectId) {
        if (!plugin.getDatabaseManager().hasUnlockedCosmetic(uuid, effectId)) {
            plugin.getDatabaseManager().unlockCosmetic(uuid, effectId);

            // Notificar al jugador si está online
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                CosmeticEffect effect = getEffectById(effectId);
                String name = (effect != null) ? effect.getName() : effectId;

                FileConfiguration messages = plugin.getConfigManager().getMessages();
                String message = messages.getString("cosmetics.unlocked", "&a¡Has desbloqueado el cosmético: &e{name}&a!");
                message = message.replace("{name}", name);
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            }
        }
    }

    /**
     * Verifica si un efecto existe por su ID
     */
    public boolean effectExists(String effectId) {
        return availableEffects.containsKey(effectId);
    }

    /**
     * Obtiene una lista de todos los IDs de efectos disponibles
     */
    public List<String> getAvailableEffects() {
        return new ArrayList<>(availableEffects.keySet());
    }

    /**
     * Verifica y desbloquea cosméticos basados en la racha de kills
     * VERSIÓN CORREGIDA - ASÍNCRONA
     */
    public void checkStreakUnlocks(Player player, int streak) {
        // Ejecutar asíncronamente para no bloquear el hilo principal
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            FileConfiguration config = plugin.getConfigManager().getConfig();
            ConfigurationSection streaksSection = config.getConfigurationSection("cosmetics.unlocks.streaks");

            if (streaksSection == null) {
                plugin.getLogger().warning("No se encontró la sección cosmetics.unlocks.streaks en la configuración");
                return;
            }

            for (String streakKey : streaksSection.getKeys(false)) {
                try {
                    int streakThreshold = Integer.parseInt(streakKey);

                    // IMPORTANTE: Las rachas deben desbloquearse cuando se alcanzan o superan
                    if (streak >= streakThreshold) {
                        List<String> effects = config.getStringList("cosmetics.unlocks.streaks." + streakKey + ".effects");

                        for (String effectId : effects) {
                            if (!hasUnlockedEffect(player.getUniqueId(), effectId) && effectExists(effectId)) {
                                unlockEffect(player.getUniqueId(), effectId);
                            }
                        }
                    }
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Valor de racha inválido en la configuración: " + streakKey);
                }
            }
        });
    }
    }

    /**
     * Registra efectos de sonido adicionales
     * Este método debe ser parte de la clase CosmeticManager, no de SoundEffect
     */
    private void registerAdditionalSoundEffects() {
        // Nuevos efectos de sonido (adicionales)
        registerEffect(new SoundEffect("sound_ping_victory", "Victoria de Ping", Material.ENDER_EYE,
                Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f));

        registerEffect(new SoundEffect("sound_nether_portal", "Portal del Nether", Material.OBSIDIAN,
                Sound.BLOCK_PORTAL_AMBIENT, 1.0f, 0.7f));

        registerEffect(new SoundEffect("sound_slime_squish", "Slime Aplastado", Material.SLIME_BALL,
                Sound.ENTITY_SLIME_SQUISH, 1.0f, 0.8f));

        registerEffect(new SoundEffect("sound_dolphin_happy", "Delfín Feliz", Material.TROPICAL_FISH,
                Sound.ENTITY_DOLPHIN_PLAY, 1.0f, 1.0f));

        registerEffect(new SoundEffect("sound_zombie_angry", "Zombie Enfadado", Material.ZOMBIE_HEAD,
                Sound.ENTITY_ZOMBIE_AMBIENT, 1.0f, 0.7f));

        registerEffect(new SoundEffect("sound_cat_meow", "Maullido", Material.STRING,
                Sound.ENTITY_CAT_PURREOW, 1.0f, 1.5f));

        registerEffect(new SoundEffect("sound_villager_no", "Villager Dice No", Material.EMERALD,
                Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f));

        registerEffect(new SoundEffect("sound_anvil_land", "Yunque Caído", Material.ANVIL,
                Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f));

        registerEffect(new SoundEffect("sound_puffer_inflate", "Pez Globo", Material.PUFFERFISH,
                Sound.ENTITY_PUFFER_FISH_BLOW_UP, 1.0f, 1.0f));

        registerEffect(new SoundEffect("sound_witch_laugh", "Risa de Bruja", Material.BREWING_STAND,
                Sound.ENTITY_WITCH_CELEBRATE, 1.0f, 1.0f));
    }
}
