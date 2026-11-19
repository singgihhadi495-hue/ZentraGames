package id.zentra.minigames;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class GameListener implements Listener {
    private final ZentraMinigames plugin;
    private final GameManager mgr;

    public GameListener(ZentraMinigames plugin, GameManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Game g = mgr.of(p);
        if (g == null) return;
        Player d = null;
        if (e instanceof EntityDamageByEntityEvent be) {
            Entity de = be.getDamager();
            if (de instanceof Player pl) d = pl;
            else if (de instanceof Projectile pr && pr.getShooter() instanceof Player s) d = s;
        }
        if (d != null && d.equals(p)) d = null;
        g.onDamage(e, p, d);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (mgr.of(p) == null) return;
        e.setKeepInventory(true);
        e.setKeepLevel(true);
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.deathMessage(null);
        Bukkit.getScheduler().runTask(plugin, () -> p.spigot().respawn());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Game g = mgr.of(p);
        if (g == null) return;
        e.setRespawnLocation(g.specSpawn());
        Bukkit.getScheduler().runTaskLater(plugin, () -> g.playerDied(p), 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        mgr.leave(e.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Game g = mgr.of(e.getPlayer());
        if (g == null) return;
        if (!g.allowBreak(e.getBlock(), e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Game g = mgr.of(e.getPlayer());
        if (g == null) return;
        if (!g.allowPlace(e.getBlock(), e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        Game g = mgr.of(e.getPlayer());
        if (g != null && !g.canBuild()) e.setCancelled(true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Game g = mgr.of(p);
        if (g == null || e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a == Action.PHYSICAL) return;
        ItemStack it = e.getItem();
        if (plugin.isLeaveItem(it) && (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK)) {
            e.setCancelled(true);
            mgr.leave(p);
            Msg.send(p, "&7Kamu keluar dari game.");
            return;
        }
        g.onInteract(e);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && mgr.of(p) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Game g = mgr.of(e.getPlayer());
        if (g != null && !g.canBuild()) e.setCancelled(true);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p) {
            Game g = mgr.of(p);
            if (g != null && !g.canBuild()) e.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Game g = mgr.of(e.getPlayer());
        if (g != null && g.lockInv()) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof GameManager.MenuHolder h) {
            e.setCancelled(true);
            if (e.getClickedInventory() != e.getInventory()) return;
            String k = h.keys.get(e.getSlot());
            if (k == null || !(e.getWhoClicked() instanceof Player p)) return;
            p.closeInventory();
            if (k.equals("_leave")) {
                if (mgr.leave(p)) Msg.send(p, "&7Kamu keluar dari game.");
            } else mgr.join(p, k);
            return;
        }
        if (e.getWhoClicked() instanceof Player p) {
            Game g = mgr.of(p);
            if (g != null && g.lockInv()) e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof GameManager.MenuHolder) { e.setCancelled(true); return; }
        if (e.getWhoClicked() instanceof Player p) {
            Game g = mgr.of(p);
            if (g != null && g.lockInv()) e.setCancelled(true);
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        Game g = mgr.gameAt(e.getLocation());
        if (g != null) g.onExplode(e);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (mgr.of(p) == null || p.hasPermission("zentra.minigames.admin")) return;
        String m = e.getMessage().toLowerCase();
        String base = m.startsWith("/") ? m.substring(1) : m;
        base = base.split(" ")[0];
        if (base.contains(":")) base = base.substring(base.indexOf(':') + 1);
        if (plugin.getConfig().getStringList("allowed-commands").contains(base)) return;
        e.setCancelled(true);
        Msg.send(p, "&cTidak bisa memakai command itu saat bermain. Pakai &e/zm leave &cuntuk keluar.");
    }
              }
