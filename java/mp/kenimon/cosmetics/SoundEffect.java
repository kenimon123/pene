package mp.kenimon.cosmetics;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class SoundEffect implements CosmeticEffect {

    private String id;
    private String name;
    private Material icon;
    private Sound sound;
    private float volume;
    private float pitch;

    public SoundEffect(String id, String name, Material icon, Sound sound) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.sound = sound;
        this.volume = 1.0f;
        this.pitch = 1.0f;
    }

    public SoundEffect(String id, String name, Material icon, Sound sound, float volume, float pitch) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;
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

    public Sound getSound() {
        return sound;
    }

    public float getVolume() {
        return volume;
    }

    public float getPitch() {
        return pitch;
    }

    /**
     * Reproduce el efecto de sonido para el jugador
     */
    public void play(Player player) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    /**
     * Reproduce el sonido cuando un jugador es asesinado, el killer lo escucha
     */
    public void playOnKill(Player killer) {
        killer.playSound(killer.getLocation(), sound, volume, pitch);
    }
}
