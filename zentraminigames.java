package id.zentra.minigames;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class ZentraMinigames extends JavaPlugin {
    private GameManager manager;
    private NamespacedKey leaveKey;
    private File dataFile;
    private YamlConfiguration data;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        leaveKey = new NamespacedKey(this, "leave_item");
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        manager = new GameManager(this);
        getServer().getPluginManager().registerEvents(new GameListener(this, manager), this);
        MinigamesCommand cmd = new MinigamesCommand(this, manager);
        getCommand("zm").setExecutor(cmd);
        getCommand("zm").setTabCompleter(cmd);
        // Tunggu Multiverse selesai memuat world
        Bukkit.getScheduler().runTaskLater(this, () -> tryInit(0), 60L);
    }

    private void tryInit(int attempt) {
        String wn = getConfig().getString("world", "pvp");
        World w = Bukkit.getWorld(wn);
        if (w == null) {
            if (attempt < 30) {
                Bukkit.getScheduler().runTaskLater(this, () -> tryInit(attempt + 1), 100L);
            } else {
                getLogger().severe("World '" + wn + "' tidak ditemukan. Pastikan world itu sudah dibuat di Multiverse, lalu restart server.");
            }
            return;
        }
        manager.init(w);
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
    }

    public GameManager manager() { return manager; }

    // ---------- data pembangunan arena ----------
    public YamlConfiguration data() { return data; }
    public boolean isBuilt(String id) { return data.getBoolean("built." + id, false); }
    public void markBuilt(String id, boolean v) { data.set("built." + id, v); saveData(); }
    public void saveData() {
        try { data.save(dataFile); } catch (IOException ex) { getLogger().warning("Gagal menyimpan data.yml: " + ex.getMessage()); }
    }

    // ---------- util ----------
    public void setScale(Player p, double v) {
        Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("scale"));
        if (a == null) return;
        AttributeInstance ai = p.getAttribute(a);
        if (ai != null && ai.getBaseValue() != v) ai.setBaseValue(v);
    }

    public ItemStack leaveItem() {
        ItemStack it = new ItemStack(Material.RED_DYE);
        ItemMeta m = it.getItemMeta();
        m.displayName(Msg.c("&c&lKeluar &7(klik kanan)"));
        m.getPersistentDataContainer().set(leaveKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    public boolean isLeaveItem(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(leaveKey, PersistentDataType.BYTE);
    }
    }
