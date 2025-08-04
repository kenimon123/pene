package mp.kenimon.cosmetics;

import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import mp.kenimon.Kenicompetitivo;

import java.util.*;

public class KillEffect implements CosmeticEffect {

    public enum KillEffectType {
        LIGHTNING,
        EXPLOSION,
        FIREWORK,
        SMOKE,
        TOTEM,
        VORTEX,
        BLACKHOLE,
        SNAP_VANISH,
        FREEZE,
        THUNDER_STORM,
        METEOR,
        GHOST,
        SOUL_STEAL,
        // Nuevos efectos
        BRO_RESPETA,     // Efecto meme "Bro Respeta"
        PHANTOM_STRIKE,  // Invoca fantasmas
        LAVA_ERUPTION,   // Erupción de lava
        MUSICAL_DEATH,   // Notas musicales
        VOID_PRISON,     // Prisión del vacío
        RAINBOW_EXPLOSION, // Explosión colorida
        THANOS_SNAP,     // Desintegración estilo Thanos
        ICE_PRISON,      // Prisión de hielo
        TREASURE_EXPLOSION, // Explosión de tesoro
        ROCKET_LAUNCH    // Lanzamiento como cohete
    }

    private String id;
    private String name;
    private Material icon;
    private KillEffectType type;
    private static final Random random = new Random();
    private Kenicompetitivo plugin;

    public KillEffect(String id, String name, Material icon, KillEffectType type, Kenicompetitivo plugin) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.type = type;
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Material getIcon() {
        return icon;
    }

    public KillEffectType getType() {
        return type;
    }

    /**
     * Ejecuta el efecto en la ubicación de la víctima
     */
    public void play(Player victim, Player killer) {
        Location loc = victim.getLocation();
        World world = loc.getWorld();

        switch (type) {
            case LIGHTNING:
                world.strikeLightningEffect(loc);
                break;

            case EXPLOSION:
                world.createExplosion(loc, 0.0f, false, false);
                break;

            case FIREWORK:
                spawnFirework(loc);
                break;

            case SMOKE:
                world.spawnParticle(
                        Particle.SMOKE_LARGE,
                        loc.clone().add(0, 1, 0),
                        30, 0.5, 0.5, 0.5, 0.1
                );
                break;

            case TOTEM:
                spawnTotemEffect(victim, killer);
                break;

            case VORTEX:
                spawnVortexEffect(loc);
                break;

            case BLACKHOLE:
                spawnBlackHoleEffect(loc);
                break;

            case FREEZE:
                spawnFreezeEffect(loc, victim);
                break;

            case THUNDER_STORM:
                spawnThunderStormEffect(loc);
                break;

            case METEOR:
                spawnMeteorEffect(loc);
                break;

            case GHOST:
                spawnGhostEffect(loc);
                break;

            case SOUL_STEAL:
                spawnSoulStealEffect(victim, killer);
                break;

            // Nuevos efectos implementados
            case BRO_RESPETA:
                spawnBroRespetaEffect(victim, killer);
                break;

            case SNAP_VANISH:
                spawnSnapVanishEffect(victim);
                break;

            case PHANTOM_STRIKE:
                spawnPhantomStrikeEffect(victim, killer);
                break;

            case LAVA_ERUPTION:
                spawnLavaEruptionEffect(loc);
                break;

            case MUSICAL_DEATH:
                spawnMusicalDeathEffect(loc);
                break;

            case VOID_PRISON:
                spawnVoidPrisonEffect(victim, loc);
                break;

            case RAINBOW_EXPLOSION:
                spawnRainbowExplosionEffect(loc);
                break;

            case THANOS_SNAP:
                spawnThanosSnapEffect(victim);
                break;

            case ICE_PRISON:
                spawnIcePrisonEffect(victim);
                break;

            case TREASURE_EXPLOSION:
                spawnTreasureExplosionEffect(loc);
                break;

            case ROCKET_LAUNCH:
                spawnRocketLaunchEffect(victim);
                break;
        }
    }

    /**
     * Genera un fuego artificial en la ubicación especificada
     */
    private void spawnFirework(Location loc) {
        Firework fw = (Firework) loc.getWorld().spawnEntity(loc, EntityType.FIREWORK);
        FireworkMeta meta = fw.getFireworkMeta();

        // Generar un efecto aleatorio
        FireworkEffect.Type[] types = FireworkEffect.Type.values();
        FireworkEffect.Type type = types[random.nextInt(types.length)];

        Color[] colors = {
                Color.RED,
                Color.BLUE,
                Color.GREEN,
                Color.YELLOW,
                Color.PURPLE,
                Color.ORANGE,
                Color.AQUA
        };

        Color color1 = colors[random.nextInt(colors.length)];
        Color color2 = colors[random.nextInt(colors.length)];

        FireworkEffect effect = FireworkEffect.builder()
                .with(type)
                .withColor(color1)
                .withFade(color2)
                .flicker(random.nextBoolean())
                .trail(random.nextBoolean())
                .build();

        meta.addEffect(effect);
        meta.setPower(0);

        fw.setFireworkMeta(meta);

        // Detonación inmediata
        fw.detonate();
    }

    /**
     * Nuevo efecto: muestra el meme "Bro Respeta" con hologramas y efectos
     * Este efecto es exclusivo de la racha 100
     */
    private void spawnBroRespetaEffect(Player victim, Player killer) {
        Location loc = victim.getLocation().clone().add(0, 1.8, 0);

        // Crear efecto de partículas primero
        victim.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, loc, 1, 0, 0, 0, 0);

        // Sonido épico
        victim.getWorld().playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);

        // ELIMINADO: Ya no enviamos mensajes al chat
        // Solo reproducimos sonidos para los jugadores cercanos
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getLocation().distance(loc) <= 50) {
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 0.8f);
            }
        }

        // Crear un holograma temporal con DecentHolograms si está disponible
        if (Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
            String holoId = "respeta_" + System.currentTimeMillis();
            List<String> lines = new ArrayList<>();

            // Frases del meme "Bro Respeta"
            lines.add(ChatColor.translateAlternateColorCodes('&', "&c&l⚔ &e&lBRO RESPETA &c&l⚔"));
            lines.add(ChatColor.translateAlternateColorCodes('&', "&7" + victim.getName() + " &cfue eliminado"));
            lines.add(ChatColor.translateAlternateColorCodes('&', "&cpor &7" + killer.getName()));

            try {
                // Crear holograma temporal
                eu.decentsoftware.holograms.api.DHAPI.createHologram(holoId, loc, lines);

                // Efecto de partículas adicional (estrellas y explosiones pequeñas)
                new BukkitRunnable() {
                    private int ticks = 0;

                    @Override
                    public void run() {
                        if (ticks >= 100) { // 5 segundos (100 ticks)
                            // Eliminar holograma al finalizar
                            eu.decentsoftware.holograms.api.DHAPI.removeHologram(holoId);
                            this.cancel();
                            return;
                        }

                        // Partículas circulares
                        double radius = 1.2;
                        double y = Math.sin(ticks * 0.1) * 0.2;

                        for (int i = 0; i < 3; i++) {
                            double angle = (i * Math.PI * 2 / 3) + (ticks * 0.1);
                            double x = Math.cos(angle) * radius;
                            double z = Math.sin(angle) * radius;

                            Location particleLoc = loc.clone().add(x, y, z);
                            loc.getWorld().spawnParticle(
                                    Particle.FLAME,
                                    particleLoc,
                                    1, 0, 0, 0, 0
                            );

                            if (ticks % 10 == 0) {
                                loc.getWorld().spawnParticle(
                                        Particle.VILLAGER_ANGRY,
                                        particleLoc,
                                        1, 0, 0, 0, 0
                                );
                            }
                        }

                        ticks++;
                    }
                }.runTaskTimer(plugin, 0L, 1L);

            } catch (Exception e) {
                plugin.getLogger().warning("Error al crear holograma Bro Respeta: " + e.getMessage());
            }
        }
    }

    /**
     * Nuevo efecto: invoca fantasmas que atacan a la víctima
     */
    private void spawnPhantomStrikeEffect(Player victim, Player killer) {
        final Location loc = victim.getLocation();

        // Sonido inicial
        loc.getWorld().playSound(loc, Sound.ENTITY_PHANTOM_AMBIENT, 1.0f, 0.5f);

        // Número de fantasmas a crear
        final int phantomCount = 4;
        final List<ArmorStand> phantoms = new ArrayList<>();

        // Crear fantasmas (usando armor stands con cabezas de esqueleto)
        for (int i = 0; i < phantomCount; i++) {
            // Posición inicial alrededor del jugador
            double angle = (Math.PI * 2 * i) / phantomCount;
            double x = Math.sin(angle) * 3;
            double z = Math.cos(angle) * 3;

            Location phantomLoc = loc.clone().add(x, 1.5, z);

            ArmorStand phantom = (ArmorStand) loc.getWorld().spawnEntity(phantomLoc, EntityType.ARMOR_STAND);
            phantom.setVisible(false);
            phantom.setGravity(false);
            phantom.setMarker(true);

            // Poner cabeza de esqueleto
            ItemStack skull = new ItemStack(Material.SKELETON_SKULL);
            phantom.getEquipment().setHelmet(skull);

            phantoms.add(phantom);
        }

        // Animar los fantasmas atacando a la víctima
        new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 40) {
                    // Eliminar fantasmas después de 2 segundos
                    for (ArmorStand phantom : phantoms) {
                        phantom.remove();
                    }
                    this.cancel();
                    return;
                }

                // Mover fantasmas hacia la víctima
                for (int i = 0; i < phantoms.size(); i++) {
                    ArmorStand phantom = phantoms.get(i);

                    // Calcular vector hacia la víctima
                    Vector direction = loc.clone().subtract(phantom.getLocation()).toVector().normalize().multiply(0.2);

                    // Mover fantasma
                    phantom.teleport(phantom.getLocation().add(direction));

                    // Partículas de rastro
                    loc.getWorld().spawnParticle(
                            Particle.SOUL,
                            phantom.getLocation(),
                            3, 0.1, 0.1, 0.1, 0
                    );

                    // Cuando está cerca, hacer efecto de impacto
                    if (phantom.getLocation().distance(loc) < 0.8) {
                        // Efecto de impacto
                        loc.getWorld().spawnParticle(
                                Particle.SOUL_FIRE_FLAME,
                                phantom.getLocation(),
                                10, 0.2, 0.2, 0.2, 0.05
                        );

                        // Sonido de impacto
                        loc.getWorld().playSound(phantom.getLocation(), Sound.ENTITY_PHANTOM_BITE, 0.5f, 1.2f);

                        // Reiniciar posición
                        double angle = (Math.PI * 2 * i) / phantomCount;
                        double distance = 3 + random.nextDouble() * 2;
                        double x = Math.sin(angle) * distance;
                        double z = Math.cos(angle) * distance;
                        double y = 1.0 + random.nextDouble();

                        phantom.teleport(loc.clone().add(x, y, z));
                    }
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Nuevo efecto: crea una erupción de lava visual
     */
    private void spawnLavaEruptionEffect(Location loc) {
        // Sonido inicial
        loc.getWorld().playSound(loc, Sound.BLOCK_LAVA_EXTINGUISH, 1.0f, 0.6f);

        // Partículas iniciales
        loc.getWorld().spawnParticle(
                Particle.LAVA,
                loc.clone().add(0, 0.5, 0),
                20, 0.5, 0.5, 0.5, 0
        );

        // Erupción principal
        new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 60) {
                    this.cancel();
                    return;
                }

                // Columna central de lava
                double height = Math.min(3.0, ticks * 0.1);
                for (double y = 0; y < height; y += 0.5) {
                    Location particleLoc = loc.clone().add(0, y, 0);

                    // Partículas de lava en columna
                    loc.getWorld().spawnParticle(
                            Particle.DRIP_LAVA,
                            particleLoc,
                            2, 0.2, 0.1, 0.2, 0
                    );

                    // Bloques de lava visual (humo y fuego)
                    if (ticks > 20 && ticks % 5 == 0) {
                        loc.getWorld().spawnParticle(
                                Particle.FLAME,
                                particleLoc,
                                3, 0.3, 0.1, 0.3, 0.05
                        );
                    }
                }

                // Explosión en la punta
                if (ticks > 15) {
                    double explosionHeight = Math.min(3.0, ticks * 0.1);
                    Location explosionLoc = loc.clone().add(0, explosionHeight, 0);

                    // Partículas de explosión
                    loc.getWorld().spawnParticle(
                            Particle.LAVA,
                            explosionLoc,
                            1, 0, 0, 0, 0
                    );

                    // Salpicaduras laterales
                    if (ticks % 5 == 0) {
                        for (int i = 0; i < 4; i++) {
                            double angle = i * Math.PI / 2 + (ticks * 0.1);
                            double distance = 0.5 + (ticks * 0.02);
                            double splashX = Math.cos(angle) * distance;
                            double splashZ = Math.sin(angle) * distance;

                            Location splashLoc = explosionLoc.clone().add(splashX, 0, splashZ);
                            loc.getWorld().spawnParticle(
                                    Particle.FLAME,
                                    splashLoc,
                                    1, 0.05, 0.05, 0.05, 0.02
                            );
                        }
                    }

                    // Sonidos de lava burbujeante
                    if (ticks % 10 == 0) {
                        loc.getWorld().playSound(explosionLoc, Sound.BLOCK_LAVA_POP, 0.5f, 0.8f);
                    }
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Nuevo efecto: notas musicales explotan alrededor
     */
    private void spawnMusicalDeathEffect(Location loc) {
        // Sonido inicial
        loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);

        // Notas musicales animadas
        new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 40) {
                    this.cancel();
                    return;
                }

                // Crear varias notas en diferentes direcciones
                for (int i = 0; i < 3; i++) {
                    // Posición aleatoria alrededor del centro
                    double angle = random.nextDouble() * Math.PI * 2;
                    double distance = 0.5 + random.nextDouble() * 1.5;
                    double x = Math.cos(angle) * distance;
                    double z = Math.sin(angle) * distance;
                    double y = 1 + random.nextDouble() * 2;

                    Location noteLoc = loc.clone().add(x, y, z);

                    // Tipo de nota aleatoria
                    Particle noteType = random.nextBoolean() ? Particle.NOTE : Particle.VILLAGER_HAPPY;

                    // Color aleatorio para notas
                    float noteColor = random.nextFloat();

                    // Mostrar partícula
                    loc.getWorld().spawnParticle(
                            noteType,
                            noteLoc,
                            1, 0, 0, 0, noteColor
                    );

                    // Sonido de nota aleatoria
                    if (ticks % 2 == 0) {
                        float pitch = 0.5f + random.nextFloat();
                        loc.getWorld().playSound(noteLoc, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, pitch);
                    }
                }

                // Gran final con acorde
                if (ticks == 35) {
                    loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
                    loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 0.8f);
                    loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, 1.0f, 1.2f);

                    // Explosión de notas
                    loc.getWorld().spawnParticle(
                            Particle.NOTE,
                            loc.clone().add(0, 1.5, 0),
                            15, 0.8, 0.8, 0.8, 1
                    );
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Nuevo efecto: cárcel del vacío
     */
    private void spawnVoidPrisonEffect(Player victim, Location loc) {
        // Sonido inicial
        loc.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);

        // Efecto de partículas iniciales
        loc.getWorld().spawnParticle(
                Particle.REVERSE_PORTAL,
                loc.clone().add(0, 1, 0),
                50, 0.5, 1, 0.5, 0.1
        );

        // Crear la prisión de vacío
        new BukkitRunnable() {
            private int ticks = 0;
            private final List<Location> prisonPoints = new ArrayList<>();

            @Override
            public void run() {
                if (ticks == 0) {
                    // Inicializar puntos de la prisión
                    double radius = 1.0;
                    int pointsPerCircle = 8;
                    int layers = 3;

                    for (int layer = 0; layer < layers; layer++) {
                        double y = layer * 1.0;

                        for (int i = 0; i < pointsPerCircle; i++) {
                            double angle = (Math.PI * 2 * i) / pointsPerCircle;
                            double x = Math.cos(angle) * radius;
                            double z = Math.sin(angle) * radius;

                            prisonPoints.add(loc.clone().add(x, y, z));
                        }
                    }

                    // Añadir puntos para el techo
                    for (double x = -radius; x <= radius; x += 0.5) {
                        for (double z = -radius; z <= radius; z += 0.5) {
                            if (x*x + z*z <= radius*radius) {
                                prisonPoints.add(loc.clone().add(x, layers, z));
                            }
                        }
                    }
                }

                if (ticks >= 60) {
                    // Explosión final
                    loc.getWorld().spawnParticle(
                            Particle.DRAGON_BREATH,
                            loc.clone().add(0, 1, 0),
                            50, 0.5, 1, 0.5, 0.1
                    );

                    loc.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f);
                    this.cancel();
                    return;
                }

                // Animar la prisión
                for (Location point : prisonPoints) {
                    // Efecto fluctuante
                    double offsetY = Math.sin(ticks * 0.2) * 0.05;
                    Location animatedPoint = point.clone().add(0, offsetY, 0);

                    // Partículas para la prisión
                    if (ticks < 40) {
                        loc.getWorld().spawnParticle(
                                Particle.PORTAL,
                                animatedPoint,
                                1, 0.05, 0.05, 0.05, 0
                        );
                    } else {
                        // Partículas de desvanecimiento
                        loc.getWorld().spawnParticle(
                                Particle.REVERSE_PORTAL,
                                animatedPoint,
                                1, 0.05, 0.05, 0.05, 0.05
                        );
                    }
                }

                // Sonidos ambientales
                if (ticks % 10 == 0) {
                    loc.getWorld().playSound(loc, Sound.BLOCK_PORTAL_AMBIENT, 0.5f, 0.7f);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Nuevo efecto: explosión de arcoiris
     */
    private void spawnRainbowExplosionEffect(Location loc) {
        // Sonido inicial
        loc.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.0f);

        // Colores del arcoiris
        final Color[] rainbowColors = {
                Color.RED,
                Color.ORANGE,
                Color.YELLOW,
                Color.LIME,
                Color.AQUA,
                Color.BLUE,
                Color.PURPLE
        };

        // Explosión inicial
        loc.getWorld().spawnParticle(
                Particle.EXPLOSION_LARGE,
                loc.clone().add(0, 1, 0),
                1, 0, 0, 0, 0
        );

        // Animar explosión de arcoiris
        new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 40) {
                    this.cancel();
                    return;
                }

                // Expandir el radio con el tiempo
                double radius = Math.min(3.0, ticks * 0.15);

                // Crear explosión de arcoiris en forma de esfera
                for (int i = 0; i < 10; i++) {
                    // Posición aleatoria en una esfera
                    double phi = random.nextDouble() * Math.PI * 2;
                    double costheta = random.nextDouble() * 2 - 1;
                    double theta = Math.acos(costheta);

                    double x = radius * Math.sin(theta) * Math.cos(phi);
                    double y = radius * Math.sin(theta) * Math.sin(phi);
                    double z = radius * Math.cos(theta);

                    // Seleccionar color del arcoiris
                    int colorIndex = (i + ticks) % rainbowColors.length;

                    // Intentar usar partículas de redstone coloreadas para el arcoiris
                    // Esto es una aproximación - idealmente usaríamos Particle.REDSTONE con Dust
                    Location particleLoc = loc.clone().add(x, 1 + y, z);

                    // Usar partículas que den sensación de color
                    switch (colorIndex) {
                        case 0: // Rojo
                            loc.getWorld().spawnParticle(Particle.FLAME, particleLoc, 1, 0, 0, 0, 0);
                            break;
                        case 1: // Naranja
                            loc.getWorld().spawnParticle(Particle.FALLING_LAVA, particleLoc, 1, 0, 0, 0, 0);
                            break;
                        case 2: // Amarillo
                            loc.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, particleLoc, 1, 0, 0, 0, 0);
                            break;
                        case 3: // Verde
                            loc.getWorld().spawnParticle(Particle.SLIME, particleLoc, 1, 0, 0, 0, 0);
                            break;
                        case 4: // Azul claro
                            loc.getWorld().spawnParticle(Particle.DRIP_WATER, particleLoc, 1, 0, 0, 0, 0);
                            break;
                        case 5: // Azul
                            loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, particleLoc, 1, 0, 0, 0, 0);
                            break;
                        case 6: // Morado
                            loc.getWorld().spawnParticle(Particle.PORTAL, particleLoc, 1, 0, 0, 0, 0);
                            break;
                    }
                }

                // Sonidos festivos
                if (ticks % 5 == 0) {
                    float pitch = 0.8f + (ticks / 40.0f);
                    loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, pitch);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Nuevo efecto: desintegración estilo "chasquido de Thanos"
     */
    private void spawnThanosSnapEffect(Player victim) {
        final Location loc = victim.getLocation();

        // Sonido inicial
        loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_DEATH, 0.3f, 2.0f);

        // Crear un clon visual (armor stand) que se desintegrará
        ArmorStand dummy = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        dummy.setVisible(false);
        dummy.setGravity(false);
        dummy.setInvulnerable(true);
        dummy.setCustomName(victim.getName());
        dummy.setCustomNameVisible(true);

        // Imitar la apariencia del jugador
        dummy.getEquipment().setHelmet(new ItemStack(Material.PLAYER_HEAD));

        // Animar la desintegración
        new BukkitRunnable() {
            private int ticks = 0;
            private final int totalParticles = 150;
            private final List<Location> particlePoints = new ArrayList<>();

            @Override
            public void run() {
                if (ticks == 0) {
                    // Inicializar puntos de partículas basados en la forma del jugador
                    // Crear una silueta básica
                    double height = 1.8;
                    double width = 0.6;
                    double depth = 0.3;

                    for (int i = 0; i < totalParticles; i++) {
                        double x = (random.nextDouble() * 2 - 1) * width;
                        double y = random.nextDouble() * height;
                        double z = (random.nextDouble() * 2 - 1) * depth;

                        particlePoints.add(loc.clone().add(x, y, z));
                    }
                }

                if (ticks >= 60) {
                    // Eliminar el dummy al finalizar
                    dummy.remove();
                    this.cancel();
                    return;
                }

                // Calcular cuántas partículas mostrar en este tick
                int particlesToShow = (int)(totalParticles * (ticks / 60.0));

                // Mostrar partículas de desintegración
                for (int i = 0; i < particlesToShow && i < particlePoints.size(); i++) {
                    Location point = particlePoints.get(i);

                    // Mover partícula hacia arriba y ligeramente al lado
                    double upwardSpeed = 0.05 + random.nextDouble() * 0.1;
                    double sidewaysSpeed = (random.nextDouble() * 2 - 1) * 0.03;

                    // Aplicar movimiento
                    point.add(sidewaysSpeed, upwardSpeed, sidewaysSpeed);

                    // Mostrar partícula
                    loc.getWorld().spawnParticle(
                            Particle.SOUL,
                            point,
                            1, 0, 0, 0, 0
                    );

                    // Algunas partículas de ceniza para efecto de desintegración
                    if (random.nextDouble() > 0.7) {
                        loc.getWorld().spawnParticle(
                                Particle.ASH,
                                point,
                                1, 0.05, 0.05, 0.05, 0
                        );
                    }
                }

                // Hacer el dummy transparente gradualmente
                // Esto sería ideal con SetInvisible(progress) pero no es posible directamente

                // Sonido adicional
                if (ticks == 30) {
                    loc.getWorld().playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 1.5f);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Nuevo efecto: prisión de hielo
     */
    private void spawnIcePrisonEffect(Player victim) {
        final Location loc = victim.getLocation();

        // Sonido inicial
        loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);

        // Efecto inicial de congelación
        loc.getWorld().spawnParticle(
                Particle.SNOWFLAKE,
                loc.clone().add(0, 1, 0),
                30, 0.5, 1, 0.5, 0.05
        );

        // Construir la prisión de hielo
        new BukkitRunnable() {
            private int ticks = 0;
            private final List<Location> iceBlocks = new ArrayList<>();
            private final double radius = 1.0;
            private final double height = 2.5;

            @Override
            public void run() {
                if (ticks == 0) {
                    // Generar posiciones de los bloques de hielo
                    // Base circular
                    double baseY = loc.getY() - 0.5;
                    for (double x = -radius; x <= radius; x += 0.4) {
                        for (double z = -radius; z <= radius; z += 0.4) {
                            if (x*x + z*z <= radius*radius) {
                                iceBlocks.add(new Location(loc.getWorld(),
                                        loc.getX() + x, baseY, loc.getZ() + z));
                            }
                        }
                    }

                    // Paredes circulares
                    int sides = 16;
                    for (int side = 0; side < sides; side++) {
                        double angle = (Math.PI * 2 * side) / sides;
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;

                        for (double y = 0; y < height; y += 0.4) {
                            iceBlocks.add(new Location(loc.getWorld(),
                                    loc.getX() + x, loc.getY() + y, loc.getZ() + z));
                        }
                    }

                    // Techo circular
                    double topY = loc.getY() + height;
                    for (double x = -radius; x <= radius; x += 0.4) {
                        for (double z = -radius; z <= radius; z += 0.4) {
                            if (x*x + z*z <= radius*radius) {
                                iceBlocks.add(new Location(loc.getWorld(),
                                        loc.getX() + x, topY, loc.getZ() + z));
                            }
                        }
                    }
                }

                if (ticks >= 80) {
                    // Efecto de rotura al finalizar
                    loc.getWorld().spawnParticle(
                            Particle.BLOCK_CRACK,
                            loc.clone().add(0, 1, 0),
                            50, 1, 1, 1, 0,
                            Bukkit.createBlockData(Material.ICE)
                    );

                    loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.0f);
                    this.cancel();
                    return;
                }

                // Mostrar la prisión de hielo
                int blocksToShow = Math.min(iceBlocks.size(),
                        (int)(iceBlocks.size() * (ticks / 20.0)));

                for (int i = 0; i < blocksToShow; i++) {
                    Location blockLoc = iceBlocks.get(i);

                    // Efecto visual de bloque de hielo
                    if (ticks < 60 || i % 5 != 0) { // Comenzar a "derretir" después de tick 60
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (p.getWorld() == loc.getWorld() &&
                                    p.getLocation().distance(loc) < 50) {
                                // Enviar efecto de bloque de hielo a jugadores cercanos
                                p.spawnParticle(
                                        Particle.BLOCK_CRACK,
                                        blockLoc,
                                        1, 0.1, 0.1, 0.1, 0,
                                        Bukkit.createBlockData(Material.ICE)
                                );
                            }
                        }
                    }
                }

                // Efectos de congelación alrededor de la víctima
                if (ticks % 5 == 0) {
                    loc.getWorld().spawnParticle(
                            Particle.SNOWFLAKE,
                            loc.clone().add(0, 1, 0),
                            5, 0.3, 0.5, 0.3, 0.02
                    );
                }

                // Sonidos de hielo
                if (ticks % 20 == 0) {
                    loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_PLACE, 0.5f, 1.0f);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Nuevo efecto: explosión de tesoros
     */
    private void spawnTreasureExplosionEffect(Location loc) {
        // Sonido inicial
        loc.getWorld().playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.6f);

        // Partículas iniciales
        loc.getWorld().spawnParticle(
                Particle.VILLAGER_HAPPY,
                loc.clone().add(0, 1, 0),
                20, 0.5, 0.5, 0.5, 0.1
        );

        // Items de "tesoro" que aparecerán
        final Material[] treasureItems = {
                Material.GOLD_INGOT, Material.GOLD_NUGGET,
                Material.DIAMOND, Material.EMERALD
        };

        // Crear explosion de tesoros
        new BukkitRunnable() {
            private int ticks = 0;
            private final int itemCount = 20;
            private final List<ArmorStand> treasureStands = new ArrayList<>();
            private final List<Vector> trajectories = new ArrayList<>();

            @Override
            public void run() {
                if (ticks == 0) {
                    // Inicializar los items y trayectorias
                    for (int i = 0; i < itemCount; i++) {
                        // Crear item flotante (usando armor stands invisibles)
                        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(
                                loc.clone().add(0, 1, 0),
                                EntityType.ARMOR_STAND
                        );
                        stand.setVisible(false);
                        stand.setGravity(false);
                        stand.setSmall(true);
                        stand.setMarker(true);

                        // Darle un item aleatorio de tesoro
                        Material treasureMat = treasureItems[random.nextInt(treasureItems.length)];
                        stand.getEquipment().setItemInMainHand(new ItemStack(treasureMat));

                        treasureStands.add(stand);

                        // Crear trayectoria aleatoria
                        double vx = (random.nextDouble() * 2 - 1) * 0.1;
                        double vy = random.nextDouble() * 0.2 + 0.1;
                        double vz = (random.nextDouble() * 2 - 1) * 0.1;

                        trajectories.add(new Vector(vx, vy, vz));
                    }
                }

                if (ticks >= 60) {
                    // Remover todos los stands al finalizar
                    for (ArmorStand stand : treasureStands) {
                        stand.remove();
                    }
                    this.cancel();
                    return;
                }

                // Animar los tesoros
                for (int i = 0; i < treasureStands.size(); i++) {
                    ArmorStand stand = treasureStands.get(i);
                    Vector trajectory = trajectories.get(i);

                    // Aplicar gravedad a la trayectoria
                    trajectory.setY(trajectory.getY() - 0.01);

                    // Mover el stand
                    stand.teleport(stand.getLocation().add(trajectory));

                    // Rotar items
                    Location rotation = stand.getLocation();
                    rotation.setYaw(rotation.getYaw() + 15);
                    stand.teleport(rotation);

                    // Añadir efecto de brillo alrededor
                    if (ticks % 3 == 0) {
                        loc.getWorld().spawnParticle(
                                Particle.VILLAGER_HAPPY,
                                stand.getLocation().add(0, 0.5, 0),
                                1, 0.1, 0.1, 0.1, 0
                        );
                    }
                }

                // Sonidos de tesoro
                if (ticks % 10 == 0) {
                    float pitch = 0.8f + (ticks / 60.0f);
                    loc.getWorld().playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, pitch);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Nuevo efecto: lanzamiento espacial como cohete
     */
    private void spawnRocketLaunchEffect(Player victim) {
        final Location loc = victim.getLocation();

        // Sonido inicial
        loc.getWorld().playSound(loc, Sound.ENTITY_TNT_PRIMED, 1.0f, 1.2f);

        // Efecto inicial de preparación
        loc.getWorld().spawnParticle(
                Particle.SMOKE_LARGE,
                loc.clone().add(0, 0.1, 0),
                20, 0.3, 0.1, 0.3, 0.05
        );

        // Crear el "cohete" (ArmorStand con la cabeza de jugador)
        ArmorStand rocket = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        rocket.setVisible(false);
        rocket.setGravity(false);
        rocket.setCustomName(victim.getName());
        rocket.setCustomNameVisible(true);

        // Poner cabeza de jugador
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        rocket.getEquipment().setHelmet(head);

        // Animar el lanzamiento
        new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 100) {
                    // Explosión final
                    loc.getWorld().spawnParticle(
                            Particle.EXPLOSION_LARGE,
                            rocket.getLocation(),
                            1, 0, 0, 0, 0
                    );
                    loc.getWorld().playSound(rocket.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);

                    rocket.remove();
                    this.cancel();
                    return;
                }

                // Fase de lanzamiento
                double height;
                if (ticks < 20) {
                    // Fase inicial (temblor)
                    double shake = 0.05;
                    double offsetX = (random.nextDouble() * 2 - 1) * shake;
                    double offsetZ = (random.nextDouble() * 2 - 1) * shake;

                    rocket.teleport(rocket.getLocation().add(offsetX, 0, offsetZ));

                    // Humo en la base
                    loc.getWorld().spawnParticle(
                            Particle.SMOKE_LARGE,
                            loc.clone().add(0, 0.1, 0),
                            5, 0.2, 0.1, 0.2, 0.02
                    );

                    // Sonido de preparación
                    if (ticks % 5 == 0) {
                        loc.getWorld().playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 0.5f);
                    }
                } else {
                    // Fase de elevación
                    double acceleration = 0.05 * (ticks - 19);
                    height = acceleration * acceleration;

                    rocket.teleport(loc.clone().add(0, height, 0));

                    // Estela de fuego y humo
                    for (int i = 0; i < 3; i++) {
                        double offsetY = -i * 0.5; // Partículas hacia abajo
                        Location flameLoc = rocket.getLocation().add(0, offsetY, 0);

                        loc.getWorld().spawnParticle(
                                Particle.FLAME,
                                flameLoc,
                                3, 0.1, 0.1, 0.1, 0.05
                        );

                        loc.getWorld().spawnParticle(
                                Particle.SMOKE_LARGE,
                                flameLoc,
                                5, 0.2, 0.1, 0.2, 0.01
                        );
                    }

                    // Sonido de propulsión
                    if (ticks % 5 == 0) {
                        float pitch = 1.0f + (ticks / 100.0f);
                        loc.getWorld().playSound(rocket.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.5f, pitch);
                    }
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    /**
     * Efecto mejorado: crea un vórtice de partículas
     * Este método reemplaza la implementación anterior para corregir los problemas
     */
    private void spawnVortexEffect(Location loc) {
        // Sonido inicial
        loc.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);

        // Vórtice principal con más partículas y duración
        new BukkitRunnable() {
            private int ticks = 0;
            private final double maxRadius = 3.0;

            @Override
            public void run() {
                if (ticks >= 100) { // Aumentar duración a 5 segundos
                    // Efecto final
                    loc.getWorld().spawnParticle(
                            Particle.EXPLOSION_LARGE,
                            loc.clone().add(0, 1, 0),
                            1, 0, 0, 0, 0
                    );
                    loc.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
                    this.cancel();
                    return;
                }

                // Radio que varía con el tiempo (se contrae)
                double progress = (double) ticks / 100;
                double radius = maxRadius * (1.0 - progress * 0.8);

                // Múltiples anillos a diferentes alturas
                for (double y = 0; y < 3.0; y += 0.3) {
                    double verticalOffset = Math.sin(ticks * 0.1 + y) * 0.5;

                    // Cada anillo gira a diferente velocidad
                    int points = 16;
                    double angleOffset = ticks * 0.05 * (y + 1);

                    for (int i = 0; i < points; i++) {
                        double angle = (Math.PI * 2 * i / points) + angleOffset;
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;

                        // Posición de la partícula
                        Location particleLoc = loc.clone().add(x, y + verticalOffset, z);

                        // Partículas primarias del vórtice
                        loc.getWorld().spawnParticle(
                                Particle.PORTAL,
                                particleLoc,
                                1, 0.02, 0.02, 0.02, 0.01
                        );

                        // Partículas adicionales cada ciertos ticks
                        if (ticks % 5 == 0 && random.nextBoolean()) {
                            Particle extraParticle = random.nextBoolean() ?
                                    Particle.REVERSE_PORTAL : Particle.DRAGON_BREATH;

                            loc.getWorld().spawnParticle(
                                    extraParticle,
                                    particleLoc,
                                    1, 0.05, 0.05, 0.05, 0.01
                            );
                        }
                    }
                }

                // Sonidos periódicos
                if (ticks % 20 == 0) {
                    float pitch = 0.5f + (float)(ticks / 100.0);
                    loc.getWorld().playSound(loc, Sound.BLOCK_PORTAL_AMBIENT, 0.8f, pitch);
                }

                // Efectos hacia el centro del vórtice
                if (ticks % 3 == 0) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double distanceFromCenter = 0.5 + random.nextDouble() * (radius - 0.5);
                    double x = Math.cos(angle) * distanceFromCenter;
                    double z = Math.sin(angle) * distanceFromCenter;

                    Location startLoc = loc.clone().add(x, 1 + random.nextDouble(), z);
                    Vector direction = loc.clone().add(0, 1, 0).subtract(startLoc).toVector().normalize().multiply(0.2);

                    // Partícula que se mueve hacia el centro
                    loc.getWorld().spawnParticle(
                            Particle.ENCHANTMENT_TABLE,
                            startLoc,
                            0, direction.getX(), direction.getY(), direction.getZ(), 0.2
                    );
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Efecto mejorado: crea un agujero negro que absorbe partículas
     */
    private void spawnBlackHoleEffect(Location loc) {
        // Efectos iniciales
        loc.getWorld().playSound(loc, Sound.BLOCK_END_PORTAL_SPAWN, 0.8f, 0.5f);

        // Crear una esfera negra de partículas en el centro
        new BukkitRunnable() {
            private int ticks = 0;
            private final double maxRadius = 2.5;
            private final List<Location> particleOrigins = new ArrayList<>();

            @Override
            public void run() {
                // Inicializar orígenes de partículas en el primer tick
                if (ticks == 0) {
                    // Crear puntos de origen alrededor para la absorción
                    for (int i = 0; i < 20; i++) {
                        double angle = random.nextDouble() * Math.PI * 2;
                        double vertAngle = random.nextDouble() * Math.PI * 2;
                        double distance = 5.0 + random.nextDouble() * 3.0;

                        double x = Math.cos(angle) * Math.sin(vertAngle) * distance;
                        double y = Math.cos(vertAngle) * distance + 1.0;
                        double z = Math.sin(angle) * Math.sin(vertAngle) * distance;

                        particleOrigins.add(loc.clone().add(x, y, z));
                    }
                }

                if (ticks >= 120) {
                    // Explosión final
                    loc.getWorld().spawnParticle(
                            Particle.EXPLOSION_HUGE,
                            loc.clone().add(0, 1, 0),
                            1, 0, 0, 0, 0
                    );

                    // Sonido final
                    loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
                    this.cancel();
                    return;
                }

                // Radio del agujero negro central
                double blackHoleRadius = 0.7 + (Math.sin(ticks * 0.05) * 0.2);

                // Centro del agujero negro
                Location blackHoleCenter = loc.clone().add(0, 1.0, 0);

                // Crear el núcleo del agujero negro
                for (int i = 0; i < 5; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double vertAngle = random.nextDouble() * Math.PI;
                    double r = blackHoleRadius * random.nextDouble();

                    double x = r * Math.sin(vertAngle) * Math.cos(angle);
                    double y = r * Math.cos(vertAngle);
                    double z = r * Math.sin(vertAngle) * Math.sin(angle);

                    Location particleLoc = blackHoleCenter.clone().add(x, y, z);

                    // Partículas para el núcleo negro
                    loc.getWorld().spawnParticle(
                            Particle.SUSPENDED_DEPTH,
                            particleLoc,
                            1, 0, 0, 0, 0
                    );

                    // Partículas adicionales para hacer más visible el núcleo
                    loc.getWorld().spawnParticle(
                            Particle.SMOKE_NORMAL,
                            particleLoc,
                            1, 0.05, 0.05, 0.05, 0
                    );
                }

                // Anillo exterior pulsante
                double ringRadius = blackHoleRadius + 0.5 + Math.sin(ticks * 0.1) * 0.3;
                int ringPoints = 16;
                for (int i = 0; i < ringPoints; i++) {
                    double angle = (Math.PI * 2 * i / ringPoints) + (ticks * 0.02);
                    double x = Math.cos(angle) * ringRadius;
                    double z = Math.sin(angle) * ringRadius;

                    // Partículas del anillo
                    loc.getWorld().spawnParticle(
                            Particle.DRAGON_BREATH,
                            blackHoleCenter.clone().add(x, 0, z),
                            1, 0.05, 0.05, 0.05, 0
                    );
                }

                // Partículas absorbidas desde puntos aleatorios
                for (int i = 0; i < 3; i++) {
                    if (particleOrigins.isEmpty()) continue;

                    Location origin = particleOrigins.get(random.nextInt(particleOrigins.size()));

                    // Vector desde el origen hacia el agujero negro
                    Vector direction = blackHoleCenter.clone().subtract(origin).toVector().normalize().multiply(0.3);

                    // Seleccionar partícula aleatoria para variedad
                    Particle particle;
                    double rand = random.nextDouble();
                    if (rand < 0.3) {
                        particle = Particle.SMOKE_NORMAL;
                    } else if (rand < 0.6) {
                        particle = Particle.DRAGON_BREATH;
                    } else if (rand < 0.9) {
                        particle = Particle.END_ROD;
                    } else {
                        particle = Particle.SPELL_WITCH;
                    }

                    // Lanzar partícula directamente usando velocidades
                    loc.getWorld().spawnParticle(
                            particle,
                            origin,
                            0, direction.getX(), direction.getY(), direction.getZ(), 0.5
                    );
                }

                // Sonidos periódicos
                if (ticks % 15 == 0) {
                    float pitch = 0.5f + (float)(ticks % 5) * 0.1f;
                    Sound sound = (ticks % 30 == 0) ?
                            Sound.BLOCK_PORTAL_AMBIENT :
                            Sound.BLOCK_PORTAL_TRIGGER;

                    loc.getWorld().playSound(blackHoleCenter, sound, 0.6f, pitch);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Efecto mejorado: congela el cuerpo de la víctima temporalmente
     */
    private void spawnFreezeEffect(Location loc, Player victim) {
        // Efectos iniciales
        loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f);

        // Crear prisma de hielo alrededor de la víctima
        new BukkitRunnable() {
            private int ticks = 0;
            private final double radius = 0.8;
            private final double height = 2.0;
            private final Map<Location, Material> blockCache = new HashMap<>();

            @Override
            public void run() {
                if (ticks >= 100) { // 5 segundos de duración
                    // Efecto de rotura
                    loc.getWorld().spawnParticle(
                            Particle.BLOCK_CRACK,
                            loc.clone().add(0, 1.0, 0),
                            50, radius, height/2, radius, 0,
                            Bukkit.createBlockData(Material.ICE)
                    );

                    loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 1.2f);
                    loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);
                    this.cancel();
                    return;
                }

                // Crear prisma de hielo gradualmente
                if (ticks < 20) {
                    int blocksToShow = (int)((ticks / 20.0) * 20);

                    // Efecto de congelación gradual desde abajo hacia arriba
                    for (int i = 0; i <= blocksToShow; i++) {
                        // Altura relativa
                        double y = (i / 20.0) * height;

                        // Crear círculo a esa altura
                        double currentRadius = radius * (1.0 - y/height * 0.3); // Ligeramente cónico
                        int points = 8;

                        for (int p = 0; p < points; p++) {
                            double angle = (Math.PI * 2 * p / points);
                            double x = Math.cos(angle) * currentRadius;
                            double z = Math.sin(angle) * currentRadius;

                            Location iceLoc = loc.clone().add(x, y, z);

                            // Partículas de hielo
                            loc.getWorld().spawnParticle(
                                    Particle.BLOCK_CRACK,
                                    iceLoc,
                                    1, 0.05, 0.05, 0.05, 0,
                                    Bukkit.createBlockData(Material.ICE)
                            );

                            // Partículas de nieve adicionales
                            if (random.nextDouble() > 0.7) {
                                loc.getWorld().spawnParticle(
                                        Particle.SNOWFLAKE,
                                        iceLoc,
                                        1, 0.05, 0.05, 0.05, 0
                                );
                            }
                        }
                    }

                    // Sonido de congelación progresiva
                    if (ticks % 5 == 0) {
                        loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_PLACE, 0.5f, 2.0f);
                    }
                } else {
                    // Mantener el prisma de hielo
                    // Efecto de hielo a lo largo del cuerpo
                    for (double y = 0; y < height; y += 0.5) {
                        double currentRadius = radius * (1.0 - y/height * 0.3);

                        for (int i = 0; i < 3; i++) {
                            double angle = random.nextDouble() * Math.PI * 2;
                            double r = currentRadius * random.nextDouble();
                            double x = Math.cos(angle) * r;
                            double z = Math.sin(angle) * r;

                            Location iceLoc = loc.clone().add(x, y, z);

                            // Partículas internas del hielo
                            Particle particleType = (random.nextBoolean()) ?
                                    Particle.SNOWFLAKE :
                                    Particle.BLOCK_CRACK;

                            if (particleType == Particle.BLOCK_CRACK) {
                                loc.getWorld().spawnParticle(
                                        particleType,
                                        iceLoc,
                                        1, 0.05, 0.05, 0.05, 0,
                                        Bukkit.createBlockData(Material.ICE)
                                );
                            } else {
                                loc.getWorld().spawnParticle(
                                        particleType,
                                        iceLoc,
                                        1, 0.05, 0.05, 0.05, 0
                                );
                            }
                        }
                    }

                    // Efecto de frío alrededor
                    if (ticks % 5 == 0) {
                        double range = radius + 1.0;
                        for (int i = 0; i < 5; i++) {
                            double angle = random.nextDouble() * Math.PI * 2;
                            double r = radius + random.nextDouble() * 0.5;
                            double x = Math.cos(angle) * r;
                            double z = Math.sin(angle) * r;
                            double y = random.nextDouble() * height;

                            Location frostLoc = loc.clone().add(x, y, z);

                            // Partículas de escarcha
                            loc.getWorld().spawnParticle(
                                    Particle.SNOWFLAKE,
                                    frostLoc,
                                    3, 0.1, 0.1, 0.1, 0.01
                            );
                        }

                        // Sonido de hielo crujiendo
                        if (ticks % 20 == 0) {
                            loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_HIT, 0.3f, 0.8f);
                        }
                    }
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Efecto mejorado: crea una tormenta de rayos
     */
    private void spawnThunderStormEffect(Location loc) {
        // Sonido inicial
        loc.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.7f);

        // Nubes y tormenta
        new BukkitRunnable() {
            private int ticks = 0;
            private final int duration = 80; // 4 segundos
            private final double radius = 4.0;
            private final double cloudHeight = 4.0;
            private final List<Location> strikePoints = new ArrayList<>();

            @Override
            public void run() {
                if (ticks == 0) {
                    // Crear puntos de impacto de rayos
                    int strikeCount = 5 + random.nextInt(3); // 5-7 rayos
                    for (int i = 0; i < strikeCount; i++) {
                        double angle = random.nextDouble() * Math.PI * 2;
                        double distance = random.nextDouble() * radius;
                        double x = Math.cos(angle) * distance;
                        double z = Math.sin(angle) * distance;

                        strikePoints.add(loc.clone().add(x, 0, z));
                    }
                }

                if (ticks >= duration) {
                    this.cancel();
                    return;
                }

                // Crear nubes de tormenta
                for (int i = 0; i < 5; i++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double distance = random.nextDouble() * radius;
                    double x = Math.cos(angle) * distance;
                    double z = Math.sin(angle) * distance;
                    double y = cloudHeight + random.nextDouble() - 0.5; // Variar altura ligeramente

                    Location cloudLoc = loc.clone().add(x, y, z);

                    // Partículas de nubes
                    loc.getWorld().spawnParticle(
                            Particle.CLOUD,
                            cloudLoc,
                            1, 0.2, 0.1, 0.2, 0
                    );

                    // Algunas partículas de humo
                    if (random.nextDouble() > 0.7) {
                        loc.getWorld().spawnParticle(
                                Particle.SMOKE_LARGE,
                                cloudLoc,
                                1, 0.2, 0.1, 0.2, 0
                        );
                    }
                }

                // Rayos aleatorios durante la tormenta
                if (random.nextDouble() > 0.8 && !strikePoints.isEmpty()) {
                    // Seleccionar un punto aleatorio para el rayo
                    Location strikePoint = strikePoints.get(random.nextInt(strikePoints.size()));

                    // Relámpago visual (sin daño)
                    loc.getWorld().strikeLightningEffect(strikePoint);

                    // Partículas adicionales en el punto de impacto
                    loc.getWorld().spawnParticle(
                            Particle.LAVA,
                            strikePoint.clone().add(0, 0.2, 0),
                            5, 0.1, 0.1, 0.1, 0
                    );

                    // Sonido adicional
                    if (random.nextDouble() > 0.5) {
                        float volume = 0.5f + random.nextFloat() * 0.5f;
                        float pitch = 0.8f + random.nextFloat() * 0.4f;
                        loc.getWorld().playSound(strikePoint, Sound.ITEM_TRIDENT_THUNDER, volume, pitch);
                    }
                }

                // Sonidos ambientales de tormenta
                if (ticks % 20 == 0) {
                    Sound sound = (random.nextDouble() > 0.7) ?
                            Sound.ENTITY_LIGHTNING_BOLT_THUNDER :
                            Sound.AMBIENT_CAVE;

                    float volume = 0.3f + random.nextFloat() * 0.3f;
                    float pitch = 0.7f + random.nextFloat() * 0.6f;

                    loc.getWorld().playSound(loc, sound, volume, pitch);
                }

                // Lluvia adicional
                if (ticks % 2 == 0) {
                    for (int i = 0; i < 3; i++) {
                        double angle = random.nextDouble() * Math.PI * 2;
                        double distance = random.nextDouble() * radius;
                        double x = Math.cos(angle) * distance;
                        double z = Math.sin(angle) * distance;

                        Location rainLoc = loc.clone().add(x, cloudHeight, z);

                        // Partículas de lluvia
                        loc.getWorld().spawnParticle(
                                Particle.WATER_DROP,
                                rainLoc,
                                1, 0.1, 0, 0.1, 0
                        );
                    }
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Efecto mejorado: crea una caída de meteoro
     */
    private void spawnMeteorEffect(Location loc) {
        // Sonido inicial
        loc.getWorld().playSound(loc, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 0.7f, 0.5f);

        // Crear y lanzar el meteoro
        new BukkitRunnable() {
            private int ticks = 0;
            private final double meteorSize = 1.0;
            private final double meteorHeight = 20.0;
            private Location meteorLoc;
            private boolean hasLanded = false;
            private int postLandingTicks = 0;

            @Override
            public void run() {
                // Inicializar ubicación del meteoro
                if (ticks == 0) {
                    // Pequeño offset aleatorio desde la ubicación objetivo
                    double offsetX = (random.nextDouble() * 2 - 1) * 2;
                    double offsetZ = (random.nextDouble() * 2 - 1) * 2;
                    meteorLoc = loc.clone().add(offsetX, meteorHeight, offsetZ);
                }

                // Fase de aterrizaje
                if (!hasLanded) {
                    // Calcular altura actual con aceleración
                    double progress = Math.min(1.0, Math.pow((double)ticks / 40, 2));
                    double currentHeight = meteorHeight * (1.0 - progress);

                    // Actualizar ubicación del meteoro
                    meteorLoc = loc.clone().add(
                            meteorLoc.getX() - loc.getX(),
                            currentHeight,
                            meteorLoc.getZ() - loc.getZ()
                    );

                    // Dibujar el meteoro (más grande)
                    for (int i = 0; i < 10; i++) {
                        double offset = random.nextDouble() * meteorSize * 0.5;
                        double angle = random.nextDouble() * Math.PI * 2;
                        double x = Math.cos(angle) * offset;
                        double z = Math.sin(angle) * offset;
                        double y = random.nextDouble() * offset * 0.5;

                        Location particleLoc = meteorLoc.clone().add(x, y, z);

                        // Núcleo del meteoro
                        loc.getWorld().spawnParticle(
                                Particle.FLAME,
                                particleLoc,
                                1, 0.1, 0.1, 0.1, 0
                        );

                        // Humo y cenizas siguiendo al meteoro
                        Location trailLoc = particleLoc.clone().add(
                                random.nextDouble() * 0.2 - 0.1,
                                random.nextDouble() * 0.5,
                                random.nextDouble() * 0.2 - 0.1
                        );

                        loc.getWorld().spawnParticle(
                                Particle.SMOKE_LARGE,
                                trailLoc,
                                1, 0.1, 0.1, 0.1, 0
                        );

                        // Ocasionalmente partículas de lava para más efectos
                        if (random.nextDouble() > 0.8) {
                            loc.getWorld().spawnParticle(
                                    Particle.LAVA,
                                    particleLoc,
                                    1, 0.05, 0.05, 0.05, 0
                            );
                        }
                    }

                    // Sonidos durante la caída
                    if (ticks % 5 == 0) {
                        float pitch = 1.0f - (float)(currentHeight / meteorHeight) * 0.5f;
                        Sound sound = (ticks % 10 == 0) ?
                                Sound.ENTITY_BLAZE_SHOOT :
                                Sound.BLOCK_FIRE_AMBIENT;

                        loc.getWorld().playSound(meteorLoc, sound, 0.5f, pitch);
                    }

                    // Verificar si ha aterrizado
                    if (currentHeight <= 0.2) {
                        hasLanded = true;
                        postLandingTicks = 0;

                        // Explosión en el impacto
                        loc.getWorld().spawnParticle(
                                Particle.EXPLOSION_HUGE,
                                loc.clone().add(0, 0.2, 0),
                                1, 0, 0, 0, 0
                        );

                        // Sonido de impacto
                        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
                        loc.getWorld().playSound(loc, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.0f, 0.7f);

                        // Partículas de impacto adicionales
                        loc.getWorld().spawnParticle(
                                Particle.LAVA,
                                loc,
                                20, 1.5, 0.5, 1.5, 0
                        );

                        // Onda expansiva
                        double waveRadius = 3.0;
                        int wavePoints = 32;
                        for (int i = 0; i < wavePoints; i++) {
                            double angle = (Math.PI * 2 * i / wavePoints);
                            double x = Math.cos(angle) * waveRadius;
                            double z = Math.sin(angle) * waveRadius;

                            Location waveLoc = loc.clone().add(x, 0.1, z);

                            // Partículas de la onda
                            loc.getWorld().spawnParticle(
                                    Particle.SMOKE_LARGE,
                                    waveLoc,
                                    1, 0.1, 0.1, 0.1, 0
                            );
                        }
                    }
                } else {
                    // Fase post-impacto: crater y fuego
                    postLandingTicks++;

                    // Partículas de fuego y humo en el cráter
                    for (int i = 0; i < 5; i++) {
                        double range = 1.5;
                        double angle = random.nextDouble() * Math.PI * 2;
                        double distance = random.nextDouble() * range;
                        double x = Math.cos(angle) * distance;
                        double z = Math.sin(angle) * distance;

                        Location fireLoc = loc.clone().add(x, 0.1, z);

                        // Alternar entre fuego y humo
                        if (random.nextBoolean()) {
                            loc.getWorld().spawnParticle(
                                    Particle.FLAME,
                                    fireLoc,
                                    1, 0.05, 0.05, 0.05, 0
                            );
                        } else {
                            loc.getWorld().spawnParticle(
                                    Particle.SMOKE_NORMAL,
                                    fireLoc,
                                    1, 0.05, 0.1, 0.05, 0
                            );
                        }
                    }

                    // Sonidos del fuego
                    if (postLandingTicks % 10 == 0) {
                        loc.getWorld().playSound(loc, Sound.BLOCK_FIRE_AMBIENT, 0.4f, 1.0f);
                    }

                    // Finalizar después de un tiempo
                    if (postLandingTicks >= 60) {
                        this.cancel();
                    }
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Efecto mejorado: crea un fantasma que sale del cuerpo de la víctima
     */
    private void spawnGhostEffect(Location loc) {
        // Sonidos iniciales
        loc.getWorld().playSound(loc, Sound.ENTITY_VEX_AMBIENT, 0.5f, 0.5f);
        loc.getWorld().playSound(loc, Sound.BLOCK_SOUL_SAND_BREAK, 0.5f, 0.5f);

        // Crear el fantasma
        new BukkitRunnable() {
            private int ticks = 0;
            private Location ghostLoc;
            private final double ascendSpeed = 0.05;

            @Override
            public void run() {
                if (ticks == 0) {
                    ghostLoc = loc.clone().add(0, 0.5, 0);
                }

                if (ticks >= 100) {
                    // Efecto de desvanecimiento final
                    for (int i = 0; i < 30; i++) {
                        double angle = random.nextDouble() * Math.PI * 2;
                        double radius = 0.5 + random.nextDouble();
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;
                        double y = random.nextDouble() * 2;

                        loc.getWorld().spawnParticle(
                                Particle.SOUL,
                                ghostLoc.clone().add(x, y, z),
                                1, 0.1, 0.1, 0.1, 0
                        );
                    }

                    // Sonido final
                    loc.getWorld().playSound(ghostLoc, Sound.ENTITY_VEX_DEATH, 0.5f, 0.5f);
                    this.cancel();
                    return;
                }

                // Actualizar posición del fantasma (ascendiendo)
                ghostLoc.add(0, ascendSpeed * (1 + ticks / 50.0), 0);

                // Movimiento oscilatorio lateral
                double oscillationX = Math.sin(ticks * 0.1) * 0.1;
                double oscillationZ = Math.cos(ticks * 0.08) * 0.1;

                Location currentGhostLoc = ghostLoc.clone().add(oscillationX, 0, oscillationZ);

                // Crear el cuerpo del fantasma
                double ghostSize = 0.8;

                // Cabeza
                loc.getWorld().spawnParticle(
                        Particle.SOUL,
                        currentGhostLoc.clone().add(0, ghostSize, 0),
                        3, 0.2, 0.2, 0.2, 0
                );

                // Cuerpo
                for (double y = 0; y < ghostSize; y += 0.2) {
                    // Forma cónica para el cuerpo
                    double bodyWidth = 0.4 - (y / ghostSize * 0.2);

                    for (int i = 0; i < 3; i++) {
                        double angle = random.nextDouble() * Math.PI * 2;
                        double x = Math.cos(angle) * bodyWidth;
                        double z = Math.sin(angle) * bodyWidth;

                        Location bodyLoc = currentGhostLoc.clone().add(x, y, z);

                        // Alternar entre partículas
                        Particle particle = (random.nextDouble() > 0.7) ?
                                Particle.SOUL :
                                Particle.CLOUD;

                        loc.getWorld().spawnParticle(
                                particle,
                                bodyLoc,
                                1, 0.05, 0.05, 0.05, 0
                        );
                    }
                }

                // Rastro de partículas
                if (ticks % 2 == 0) {
                    Location trailLoc = currentGhostLoc.clone().add(
                            (random.nextDouble() * 2 - 1) * 0.3,
                            -0.2 - random.nextDouble() * 0.3,
                            (random.nextDouble() * 2 - 1) * 0.3
                    );

                    loc.getWorld().spawnParticle(
                            Particle.SOUL,
                            trailLoc,
                            1, 0.05, 0.05, 0.05, 0
                    );
                }

                // Sonidos periódicos
                if (ticks % 20 == 0) {
                    float pitch = 0.5f + random.nextFloat() * 0.3f;
                    Sound sound = (random.nextDouble() > 0.7) ?
                            Sound.ENTITY_VEX_AMBIENT :
                            Sound.BLOCK_SOUL_SOIL_HIT;

                    loc.getWorld().playSound(currentGhostLoc, sound, 0.2f, pitch);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Efecto mejorado: efecto de robo del alma - partículas desde la víctima hacia el asesino
     */
    private void spawnSoulStealEffect(Player victim, Player killer) {
        // Obtener ubicaciones
        Location startLoc = victim.getLocation().clone().add(0, 1.0, 0);
        Location endLoc = killer.getLocation().clone().add(0, 1.0, 0);

        // Sonido inicial
        victim.getWorld().playSound(startLoc, Sound.BLOCK_SOUL_SAND_BREAK, 0.8f, 0.5f);

        // Crear efecto de partículas de almas moviéndose
        new BukkitRunnable() {
            private int ticks = 0;
            private final List<Location> soulPoints = new ArrayList<>();
            private final List<Vector> trajectories = new ArrayList<>();
            private final int soulCount = 15;
            private boolean allArrived = false;

            @Override
            public void run() {
                if (ticks == 0) {
                    // Inicializar puntos de alma alrededor de la víctima
                    for (int i = 0; i < soulCount; i++) {
                        // Posición aleatoria alrededor de la víctima
                        double angle = random.nextDouble() * Math.PI * 2;
                        double height = random.nextDouble() * 1.8;
                        double radius = 0.3 + random.nextDouble() * 0.5;

                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;

                        soulPoints.add(startLoc.clone().add(x, height - 1.0, z));

                        // Vector dirección hacia el asesino, ligeramente perturbado
                        Vector direction = endLoc.clone().subtract(startLoc).toVector();
                        direction.normalize();

                        // Añadir perturbación
                        direction.add(new Vector(
                                (random.nextDouble() * 0.4 - 0.2),
                                (random.nextDouble() * 0.4 - 0.2),
                                (random.nextDouble() * 0.4 - 0.2)
                        ));

                        // Velocidad aleatoria
                        double speed = 0.15 + random.nextDouble() * 0.2;
                        direction.multiply(speed);

                        trajectories.add(direction);
                    }
                }

                // Verificar si se completó el efecto
                if (ticks >= 100 || allArrived) {
                    // Efecto final cuando el alma es absorbida
                    if (allArrived) {
                        // Partículas alrededor del asesino
                        killer.getWorld().spawnParticle(
                                Particle.TOTEM,
                                endLoc,
                                20, 0.5, 0.5, 0.5, 0.1
                        );

                        // Sonido de finalización
                        killer.getWorld().playSound(endLoc, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 0.7f);
                    }
                    this.cancel();
                    return;
                }

                // Contar cuántas almas han llegado
                int arrived = 0;

                // Actualizar cada punto de alma
                for (int i = 0; i < soulPoints.size(); i++) {
                    Location soulLoc = soulPoints.get(i);
                    Vector trajectory = trajectories.get(i);

                    // Mover el alma
                    soulLoc.add(trajectory);

                    // Verificar si ha llegado al destino
                    if (soulLoc.distance(endLoc) < 1.0) {
                        arrived++;

                        // Al llegar al destino, crear partículas especiales
                        victim.getWorld().spawnParticle(
                                Particle.SOUL,
                                endLoc,
                                5, 0.3, 0.3, 0.3, 0.02
                        );

                        // No mostrar más el alma
                        continue;
                    }

                    // Mostrar el alma en movimiento
                    Particle particleType = (random.nextDouble() > 0.3) ?
                            Particle.SOUL :
                            Particle.SOUL_FIRE_FLAME;

                    victim.getWorld().spawnParticle(
                            particleType,
                            soulLoc,
                            1, 0.05, 0.05, 0.05, 0
                    );

                    // Rastro fantasmal ocasional
                    if (random.nextDouble() > 0.8) {
                        victim.getWorld().spawnParticle(
                                Particle.CLOUD,
                                soulLoc,
                                1, 0.05, 0.05, 0.05, 0
                        );
                    }
                }

                // Verificar si todas las almas han llegado
                if (arrived >= soulCount) {
                    allArrived = true;
                }

                // Sonidos periódicos
                if (ticks % 10 == 0) {
                    float pitch = 0.5f + (ticks / 100.0f);
                    Sound sound = (random.nextDouble() > 0.5) ?
                            Sound.BLOCK_SOUL_SAND_STEP :
                            Sound.BLOCK_SOUL_SAND_BREAK;

                    victim.getWorld().playSound(startLoc, sound, 0.3f, pitch);

                    // Sonido en el destino a medida que llegan las almas
                    if (arrived > 0) {
                        killer.getWorld().playSound(endLoc, Sound.BLOCK_NOTE_BLOCK_CHIME,
                                0.3f, 0.5f + (arrived / (float)soulCount));
                    }
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Efecto mejorado: muestra un totem animado en la cara del jugador que realizó la eliminación
     */
    private void spawnTotemEffect(Player victim, Player killer) {
        // Efecto visual y sonido de totem activado - USANDO EL EFECTO NATIVO
        killer.getWorld().playSound(killer.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);

        // La posición será frente a la cara del asesino
        Location totemLoc = killer.getEyeLocation().add(killer.getLocation().getDirection().multiply(0.5));

        // IMPORTANTE: Usar el efecto nativo del totem para una experiencia auténtica
        killer.getWorld().spawnParticle(Particle.TOTEM, totemLoc, 100, 0.5, 0.5, 0.5, 0.4);

        // Animación adicional sin sonidos repetitivos
        new BukkitRunnable() {
            private int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 20) { // Solo 1 segundo de duración adicional
                    this.cancel();
                    return;
                }

                // Añadir algunas partículas extra para reforzar el efecto
                if (ticks % 5 == 0) {
                    killer.getWorld().spawnParticle(
                            Particle.TOTEM,
                            totemLoc,
                            20, 0.3, 0.3, 0.3, 0.1
                    );

                    // Pequeños destellos aleatorios alrededor
                    for (int i = 0; i < 3; i++) {
                        Location sparkLoc = totemLoc.clone().add(
                                (random.nextDouble() - 0.5) * 1.2,
                                (random.nextDouble() - 0.3) * 1.2,
                                (random.nextDouble() - 0.5) * 1.2
                        );

                        killer.getWorld().spawnParticle(
                                Particle.VILLAGER_HAPPY,
                                sparkLoc,
                                2, 0.05, 0.05, 0.05, 0
                        );
                    }
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 5L, 1L);
    }

    /**
     * Nuevo efecto: desaparición rápida con efecto de snap
     */
    private void spawnSnapVanishEffect(Player victim) {
        Location loc = victim.getLocation().clone();

        // Sonido inicial de "snap"
        loc.getWorld().playSound(loc, Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.5f);
        loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 0.5f, 1.5f);

        // Desaparición gradual
        new BukkitRunnable() {
            private int ticks = 0;
            private final Map<Location, Particle.DustOptions> dustPoints = new HashMap<>();

            @Override
            public void run() {
                if (ticks == 0) {
                    // Generar puntos de la silueta del jugador
                    for (int i = 0; i < 40; i++) {
                        double y = random.nextDouble() * 1.8;
                        double angle = random.nextDouble() * Math.PI * 2;
                        double r = 0.3 + (y < 0.8 ? 0.1 : 0) + random.nextDouble() * 0.2;

                        double x = Math.cos(angle) * r;
                        double z = Math.sin(angle) * r;

                        Location dustLoc = loc.clone().add(x, y, z);

                        // Color aleatorio, predominantemente gris/blanco
                        float red = 0.7f + random.nextFloat() * 0.3f;
                        float green = red; // Similar al rojo para hacer gris
                        float blue = red;  // Similar para hacer gris

                        Particle.DustOptions dustOptions = new Particle.DustOptions(
                                Color.fromRGB((int)(red * 255), (int)(green * 255), (int)(blue * 255)),
                                0.7f + random.nextFloat() * 0.3f
                        );

                        dustPoints.put(dustLoc, dustOptions);
                    }
                }

                if (ticks >= 40) {
                    // Finalizar efecto
                    this.cancel();
                    return;
                }

                // Calcular cuántos puntos mostrar en este frame
                int pointsToShow = dustPoints.size() - (int)((ticks / 40.0) * dustPoints.size());
                int shown = 0;

                // Mostrar puntos que aún no se han desvanecido
                for (Map.Entry<Location, Particle.DustOptions> entry : dustPoints.entrySet()) {
                    if (shown >= pointsToShow) break;

                    Location dustLoc = entry.getKey();
                    Particle.DustOptions dustOptions = entry.getValue();

                    // Mostrar partícula
                    loc.getWorld().spawnParticle(
                            Particle.REDSTONE,
                            dustLoc,
                            1, 0.02, 0.02, 0.02, 0,
                            dustOptions
                    );

                    shown++;
                }

                // Cada cierto tiempo, añadir efecto visual de desaparición
                if (ticks % 5 == 0) {
                    // Seleccionar ubicación aleatoria
                    List<Location> points = new ArrayList<>(dustPoints.keySet());
                    if (!points.isEmpty()) {
                        Location randomPoint = points.get(random.nextInt(points.size()));

                        // Partículas de desvanecimiento
                        loc.getWorld().spawnParticle(
                                Particle.SMOKE_NORMAL,
                                randomPoint,
                                3, 0.05, 0.05, 0.05, 0.01
                        );

                        // Sonido ocasional
                        if (random.nextDouble() > 0.7) {
                            float pitch = 1.0f + random.nextFloat() * 0.5f;
                            loc.getWorld().playSound(randomPoint, Sound.BLOCK_SAND_HIT, 0.1f, pitch);
                        }
                    }
                }

                // Sonido de desvanecimiento
                if (ticks == 10 || ticks == 25) {
                    loc.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.3f, 1.5f);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
