package id.zentra.minigames;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

import java.util.*;

/** Void Sumo: platform bercincin yang runtuh dari luar, sistem "Panas" ala smash. */
public final class SumoGame extends Game {
    private static final int R = 14, RINGS = 7;
    private int stage;
    private final Map<UUID, Integer> heat = new HashMap<>();

    public SumoGame(ZentraMinigames pl, int inst, int cx, int by, int cz) {
        super(pl, "sumo", "Void Sumo", Material.SLIME_BALL, inst, cx, by, cz, 2, 12, List.of(
                "Dorong semua lawan jatuh ke void!",
                "Pukulanmu tidak melukai, tapi menaikkan PANAS lawan. Makin panas, makin jauh terpental.",
                "Tiap 25 detik satu cincin platform runtuh. Ruang gerak makin sempit!",
                "Orang terakhir yang berdiri menang."));
        voidY = by - 8;
    }

    @Override
    protected void buildArena(Blueprint b) {
        Material[] m = {Material.RED_CONCRETE, Material.ORANGE_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE,
                Material.CYAN_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.PURPLE_CONCRETE};
        b.cone(cx, by - 10, cz, 15, 12, Material.DEEPSLATE_BRICKS);
        for (int i = 0; i < RINGS; i++) b.ring(cx, by, cz, i == RINGS - 1 ? -1 : R - 2 * (i + 1), R - 2 * i, m[i]);
        for (int[] o : new int[][]{{16, 0}, {-16, 0}, {0, 16}, {0, -16}}) {
            int x = cx + o[0], z = cz + o[1];
            b.fill(x, by - 10, z, x, by + 9, z, Material.CRYING_OBSIDIAN);
            b.set(x, by + 10, z, Material.SEA_LANTERN);
        }
        b.ring(cx, by + 10, cz, 15, 16, Material.AMETHYST_BLOCK);
    }

    @Override
    protected void onStart() {
        heat.clear();
        stage = 0;
        List<Player> ps = alivePlayers();
        spreadRing(ps, 9, by + 1);
        for (Player p : ps) p.getInventory().setItem(0, item(Material.STICK, 1, "&e&lTongkat Sumo", "&7Dorong lawanmu!"));
        bc("&eCincin luar runtuh tiap 25 detik. Bertahan!");
    }

    @Override
    protected void onHit(EntityDamageEvent e, Player v, Player d) {
        e.setDamage(0);
        int h = heat.merge(v.getUniqueId(), 1, Integer::sum);
        Vector dir = v.getLocation().toVector().subtract(d.getLocation().toVector()).setY(0);
        if (dir.lengthSquared() > 0.01) {
            Vector push = dir.normalize().multiply(Math.min(1.6, 0.35 + 0.07 * h)).setY(0.25);
            Bukkit.getScheduler().runTask(plugin, () -> v.setVelocity(v.getVelocity().add(push)));
        }
        Msg.bar(v, "&cPanas: &f" + (h * 8) + "%");
        d.playSound(d.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 1f);
    }

    @Override
    protected void onSecond() {
        if (stage < RINGS - 1) {
            int t = runSecs % 25;
            if (t == 20) bc("&cPlatform menyusut dalam &f5 &cdetik!");
            if (t == 0 && runSecs > 0) removeRing(stage++);
        }
        if (runSecs >= 240) end(alivePlayers());
    }

    private void removeRing(int i) {
        int rin = R - 2 * (i + 1), rout = R - 2 * i;
        for (int dx = -rout; dx <= rout; dx++) {
            for (int dz = -rout; dz <= rout; dz++) {
                if (!Blueprint.inRing(dx, dz, rin, rout)) continue;
                Block bl = world.getBlockAt(cx + dx, by, cz + dz);
                if (bl.getType() != Material.AIR) {
                    bl.setType(Material.AIR, false);
                    if (rnd.nextInt(5) == 0) world.spawnParticle(Particle.CLOUD, bl.getLocation().add(0.5, 1, 0.5), 1, 0, 0, 0, 0);
                }
            }
        }
        bc("&6Satu cincin platform runtuh!");
        sound(Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.7f);
    }

    @Override
    protected String barText() {
        return "&6Void Sumo &8| &fHidup: &a" + alive.size() + " &8| &fCincin tersisa: &e" + (RINGS - stage);
    }
  }
