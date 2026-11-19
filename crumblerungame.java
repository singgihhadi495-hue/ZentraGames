package id.zentra.minigames;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/** Crumble Run: lantai tiga lapis yang hancur di bawah kakimu. */
public final class CrumbleRunGame extends Game {
    private static final int R = 16;
    private static final Material[] L = {Material.ORANGE_TERRACOTTA, Material.YELLOW_TERRACOTTA, Material.WHITE_TERRACOTTA};
    private final Map<Block, Integer> pending = new HashMap<>();

    public CrumbleRunGame(ZentraMinigames pl, int inst, int cx, int by, int cz) {
        super(pl, "crumble", "Crumble Run", Material.ORANGE_TERRACOTTA, inst, cx, by, cz, 2, 14, List.of(
                "Terus bergerak! Lantai hancur di bawah kakimu!",
                "Ada 3 lapis lantai. Jatuh ke lapis bawah masih boleh, jatuh ke void berarti tersingkir.",
                "Kamu punya 3 Bulu Dash: klik kanan untuk melesat ke depan dan melompati celah.",
                "Pemain terakhir yang masih hidup menang."));
        voidY = by - 24;
    }

    private boolean isLayer(Material m) {
        for (Material l : L) if (l == m) return true;
        return false;
    }

    @Override
    protected void buildArena(Blueprint b) {
        b.cone(cx, by - 26, cz, 15, 12, Material.SMOOTH_BASALT);
        for (int i = 0; i < 3; i++) {
            b.disc(cx, by - 6 * i, cz, R, L[i]);
            b.ring(cx, by - 6 * i, cz, R - 1, R, Material.RED_TERRACOTTA);
        }
        for (int k = 0; k < 8; k++) {
            double a = Math.PI * 2 * k / 8;
            int x = cx + (int) Math.round(Math.cos(a) * 19), z = cz + (int) Math.round(Math.sin(a) * 19);
            b.fill(x, by - 26, z, x, by + 8, z, Material.CRYING_OBSIDIAN);
            b.set(x, by + 9, z, Material.SHROOMLIGHT);
        }
    }

    @Override
    protected void onStart() {
        pending.clear();
        for (Player p : alivePlayers()) {
            double a = rnd.nextDouble() * Math.PI * 2, d = rnd.nextDouble() * 12;
            p.teleport(new Location(world, cx + 0.5 + Math.cos(a) * d, by + 1, cz + 0.5 + Math.sin(a) * d));
            p.getInventory().setItem(0, item(Material.FEATHER, 3, "&b&lBulu Dash", "&7Klik kanan untuk melesat!"));
        }
        bc("&eLantai mulai hancur saat kamu menginjaknya. Lari!");
    }

    @Override
    protected void onFast() {
        for (Player p : alivePlayers()) {
            Location l = p.getLocation();
            for (double ox : new double[]{-0.3, 0.3}) {
                for (double oz : new double[]{-0.3, 0.3}) {
                    Block b = world.getBlockAt((int) Math.floor(l.getX() + ox), (int) Math.floor(l.getY() - 0.1), (int) Math.floor(l.getZ() + oz));
                    if (isLayer(b.getType()) && !pending.containsKey(b)) pending.put(b, 3);
                }
            }
        }
        Iterator<Map.Entry<Block, Integer>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Block, Integer> en = it.next();
            en.setValue(en.getValue() - 1);
            if (en.getValue() <= 0) {
                Block b = en.getKey();
                b.setType(Material.AIR, false);
                world.spawnParticle(Particle.CLOUD, b.getLocation().add(0.5, 0.8, 0.5), 2, 0.2, 0.1, 0.2, 0.01);
                it.remove();
            }
        }
    }

    @Override
    public void onInteract(PlayerInteractEvent e) {
        if (state != State.RUNNING) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it == null || it.getType() != Material.FEATHER) return;
        Player p = e.getPlayer();
        if (!alive.contains(p.getUniqueId())) return;
        e.setCancelled(true);
        it.setAmount(it.getAmount() - 1);
        p.setVelocity(p.getLocation().getDirection().multiply(0.9).setY(0.75));
        p.playSound(p.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1f, 1.3f);
    }

    @Override
    protected void onReset() {
        pending.clear();
    }

    @Override
    protected String barText() {
        return "&6Crumble Run &8| &fHidup: &a" + alive.size();
    }
              }
