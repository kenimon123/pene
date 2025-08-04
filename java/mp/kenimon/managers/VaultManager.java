package mp.kenimon.managers;

import mp.kenimon.Kenicompetitivo;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultManager {

    private final Kenicompetitivo plugin;
    private Economy economy;
    private boolean enabled;

    public VaultManager(Kenicompetitivo plugin) {
        this.plugin = plugin;
        this.enabled = setupEconomy();
    }

    private boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault no encontrado. La economía no estará disponible.");
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("No se encontró un proveedor de economía compatible con Vault.");
            return false;
        }

        economy = rsp.getProvider();
        plugin.getLogger().info("Sistema de economía Vault configurado correctamente.");
        return economy != null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean has(Player player, double amount) {
        if (!enabled || economy == null) return false;
        return economy.has(player, amount);
    }

    public boolean withdraw(Player player, double amount) {
        if (!enabled || economy == null) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(Player player, double amount) {
        if (!enabled || economy == null) return false;
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    public double getBalance(Player player) {
        if (!enabled || economy == null) return 0;
        return economy.getBalance(player);
    }
}
