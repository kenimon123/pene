package mp.kenimon.cosmetics;

import org.bukkit.Material;
import org.bukkit.entity.Player;

public interface CosmeticEffect {

    /**
     * Obtiene el ID único del efecto
     */
    String getId();

    /**
     * Obtiene el nombre visible del efecto
     */
    String getName();

    /**
     * Obtiene el material para mostrar en el menú
     */
    Material getIcon();
}
