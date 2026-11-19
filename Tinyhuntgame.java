package id.zentra.minigames;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/** Tiny Hunt: petak umpet di Kota Mainan. Pelarian menyusut jadi mungil. */
public final class TinyHuntGame extends Game {
    private static final int HALF = 22;
    private final Set<UUID> seekers = new HashSet<>();
    private final Map<UUID, Long> radarCd = new HashMap<>();
    private int phase, timer;

    public TinyHuntGame(ZentraMinigames pl, int inst, int cx, int by, int cz) {
        super(pl, "tinyhunt", "Tiny Hunt: Kota Mainan", Material.SPYGLASS, inst, cx, by, cz, 3, 16, List.of(
                "Pelarian menyusut jadi mungil dan bersembunyi, Pemburu mencari!",
                "Pelarian bisa menyelinap lewat lubang 1 blok di rumah mainan. Pemburu tidak muat!",
                "Pemburu buta 25 detik di awal. Kompas Radar menunjukkan arah pelarian terdekat (jeda 15 detik).",
                "Pelarian punya 1 Bom Asap (kecepatan kilat). Tiap 30 detik semua pelarian berpendar sesaat.",
                "Pelarian menang kalau ada yang bertahan 3 menit. Pemburu menang kalau semua tertangkap."));
        voidY = by - 10;
    }

    @Override
    protected void buildArena(Blueprint b) {
        b.cone(cx, by - 1, cz, 30, 18, Material.DIRT);
        b.fill(cx - HALF, by, cz - HALF, cx + HALF, by, cz + HALF, Material.MOSS_BLOCK);
        b.fill(cx - HALF, by, cz - 1, cx + HALF, by, cz + 1, Material.GRAY_CONCRETE);
        b.fill(cx - 1, by, cz - HALF, cx + 1, by, cz + HALF, Material.GRAY_CONCRETE);
        b.disc(cx, by, cz, 5, Material.QUARTZ_BLOCK);
        b.disc(cx, by, cz, 2, Material.GOLD_BLOCK);
        Material glass = Material.LIGHT_BLUE_STAINED_GLASS;
        b.fill(cx - HALF - 1, by + 1, cz - HALF - 1, cx + HALF + 1, by + 8, cz - HALF - 1, glass);
        b.fill(cx - HALF - 1, by + 1, cz + HALF + 1, cx + HALF + 1, by + 8, cz + HALF + 1, glass);
        b.fill(cx - HALF - 1, by + 1, cz - HALF - 1, cx - HALF - 1, by + 8, cz + HALF + 1, glass);
        b.fill(cx + HALF + 1, by + 1, cz - HALF - 1, cx + HALF + 1, by + 8, cz + HALF + 1, glass);

        Random rr = new Random(777L + instance * 31L);
        Material[] cols = {Material.RED_TERRACOTTA, Material.YELLOW_TERRACOTTA, Material.LIGHT_BLUE_CONCRETE, Material.PINK_CONCRETE,
                Material.ORANGE_CONCRETE, Material.LIME_CONCRETE, Material.PURPLE_CONCRETE, Material.WHITE_CONCRETE};
        for (int i = 0; i < 26; i++) {
            int w = 4 + rr.nextInt(4), d = 4 + rr.nextInt(4), h = 3 + rr.nextInt(5);
            int x = cx - HALF + 3 + rr.nextInt(2 * HALF - 10), z = cz - HALF + 3 + rr.nextInt(2 * HALF - 10);
            if (Math.abs(x - cx) < 8 && Math.abs(z - cz) < 8) continue;
            if (Math.abs(x + w / 2 - cx) < 4 || Math.abs(z + d / 2 - cz) < 4) continue;
            Material m = cols[rr.nextInt(cols.length)];
            b.box(x, by + 1, z, x + w - 1, by + h, z + d - 1, m);
            b.set(x + w / 2, by + 1, z, Material.AIR); // pintu 1 blok, hanya muat pelarian mungil
            b.fill(x, by + h + 1, z, x + w - 1, by + h + 1, z + d - 1, Material.SMOOTH_QUARTZ_SLAB);
        }
        for (int i = 0; i < 12; i++) {
            int x = cx - HALF + 3 + rr.nextInt(2 * HALF - 6), z = cz - HALF + 3 + rr.nextInt(2 * HALF - 6);
            if (Math.abs(x - cx) < 6 && Math.abs(z - cz) < 6) continue;
            if (Math.abs(x - cx) < 3 || Math.abs(z - cz) < 3) continue;
            int sh = 3 + rr.nextInt(2);
            b.fill(x, by + 1, z, x, by + sh, z, Material.MUSHROOM_STEM);
            b.disc(x, by + sh + 1, z, 2, Material.RED_MUSHROOM_BLOCK);
        }
        for (int i = 0; i < 10; i++) {
            int x = cx - HALF + 3 + rr.nextInt(2 * HALF - 6), z = cz - HALF + 3 + rr.nextInt(2 * HALF - 6);
            if (Math.abs(x - cx) < 6 && Math.abs(z - cz) < 6) continue;
            if (Math.abs(x - cx) < 3 || Math.abs(z - cz) < 3) continue;
            int th = 5 + rr.nextInt(3);
            b.fill(x, by + 1, z, x, by + th, z, Material.OAK_LOG);
            b.fill(x - 2, by + th - 1, z - 2, x + 2, by + th, z + 2, Material.OAK_LEAVES);
            b.fill(x - 1, by + th + 1, z - 1, x + 1, by + th + 1, z + 1, Material.OAK_LEAVES);
        }
    }

    private Location randomSpot() {
        for (int i = 0; i < 30; i++) {
            int x = cx - HALF + 2 + rnd.nextInt(2 * HALF - 3), z = cz - HALF + 2 + rnd.nextInt(2 * HALF - 3);
            if (world.getBlockAt(x, by + 1, z).isEmpty() && world.getBlockAt(x, by + 2, z).isEmpty()
                    && world.getBlockAt(x, by, z).getType().isSolid()) {
                return new Location(world, x + 0.5, by + 1, z + 0.5);
            }
        }
        return new Location(world, cx + 0.5, by + 1, cz + 7.5);
    }

    @Override
    protected void onStart() {
        seekers.clear();
        radarCd.clear();
        List<Player> ps = alivePlayers();
        Collections.shuffle(ps, rnd);
        int n = Math.max(1, ps.size() / 5);
        for (int i = 0; i < ps.size(); i++) {
            Player p = ps.get(i);
            if (i < n) {
                seekers.add(p.getUniqueId());
                p.teleport(new Location(world, cx + 0.5, by + 1, cz + 0.5));
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 26, 0, false, false));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 26, 9, false, false));
                Msg.title(p, "&c&lPEMBURU", "&7Tunggu 25 detik, lalu buru mereka!", 5, 60, 10);
            } else {
                plugin.setScale(p, 0.4);
                p.teleport(randomSpot());
                p.getInventory().setItem(0, item(Material.GUNPOWDER, 1, "&7&lBom Asap", "&7Klik kanan: kecepatan kilat 5 detik"));
                Msg.title(p, "&a&lPELARIAN", "&7Kamu mungil! Sembunyi 25 detik!", 5, 60, 10);
            }
        }
        phase = 0;
        timer = 25;
    }

    @Override
    protected void onSecond() {
        timer--;
        if (phase == 0) {
            if (timer <= 0) releaseSeekers();
            return;
        }
        if (timer <= 0) {
            end(aliveWhere(u -> !seekers.contains(u)));
            return;
        }
        if (timer % 30 == 0) {
            bc("&e&lPULSE! &fSemua pelarian berpendar sesaat!");
            for (Player p : aliveWhere(u -> !seekers.contains(u))) p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0, false, false));
        }
    }

    private void releaseSeekers() {
        phase = 1;
        timer = 180;
        for (Player p : aliveWhere(seekers::contains)) {
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            p.getInventory().setItem(0, item(Material.IRON_SWORD, 1, "&c&lPedang Pemburu"));
            p.getInventory().setItem(1, item(Material.COMPASS, 1, "&b&lRadar", "&7Klik kanan: arah pelarian terdekat"));
            Msg.title(p, "&c&lBURU MEREKA!", "&7Pelarian menang jika bertahan 3 menit", 5, 40, 10);
        }
        bc("&cPara pemburu sudah dilepas! &7Waktu: 3 menit.");
        sound(Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.5f);
    }

    @Override
    protected void onHit(EntityDamageEvent e, Player v, Player d) {
        e.setCancelled(true);
        if (phase != 1) return;
        if (seekers.contains(d.getUniqueId()) && !seekers.contains(v.getUniqueId())) {
            plugin.setScale(v, 1.0);
            eliminate(v, d, "tertangkap");
        }
    }

    @Override
    public void onInteract(PlayerInteractEvent e) {
        if (state != State.RUNNING) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        Player p = e.getPlayer();
        if (it == null || !alive.contains(p.getUniqueId())) return;
        if (it.getType() == Material.GUNPOWDER && !seekers.contains(p.getUniqueId())) {
            e.setCancelled(true);
            it.setAmount(it.getAmount() - 1);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 2));
            world.spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.3, 0), 30, 0.3, 0.2, 0.3, 0.02);
            p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXTINGUISH_FIRE, 1f, 1f);
        } else if (it.getType() == Material.COMPASS && seekers.contains(p.getUniqueId()) && phase == 1) {
            e.setCancelled(true);
            long now = System.currentTimeMillis();
            long left = 15000 - (now - radarCd.getOrDefault(p.getUniqueId(), 0L));
            if (left > 0) {
                Msg.bar(p, "&cRadar isi ulang &f" + (left / 1000 + 1) + "s");
                return;
            }
            Player best = null;
            double bd = Double.MAX_VALUE;
            for (Player h : aliveWhere(u -> !seekers.contains(u))) {
                double d = h.getLocation().distanceSquared(p.getLocation());
                if (d < bd) { bd = d; best = h; }
            }
            if (best == null) return;
            radarCd.put(p.getUniqueId(), now);
            double dx = best.getX() - p.getX(), dz = best.getZ() - p.getZ();
            double diff = Math.toDegrees(Math.atan2(-dx, dz)) - p.getLocation().getYaw();
            while (diff > 180) diff -= 360;
            while (diff < -180) diff += 360;
            String[] arrows = {"\u2191", "\u2197", "\u2192", "\u2198", "\u2193", "\u2199", "\u2190", "\u2196"};
            int k = (int) Math.round(diff / 45.0);
            String ar = arrows[((k % 8) + 8) % 8];
            Msg.bar(p, "&bRadar: pelarian terdekat &f" + (int) Math.sqrt(bd) + "m &e" + ar);
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.5f);
        }
    }

    @Override
    protected void checkWin() {
        if (state != State.RUNNING) return;
        long h = alive.stream().filter(u -> !seekers.contains(u)).count();
        long s = alive.size() - h;
        if (h == 0) end(aliveWhere(seekers::contains));
        else if (s == 0) end(aliveWhere(u -> !seekers.contains(u)));
    }

    @Override
    protected void onEnd() {
        for (Player p : online()) {
            plugin.setScale(p, 1.0);
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.removePotionEffect(PotionEffectType.SLOWNESS);
        }
    }

    @Override
    protected String barText() {
        long h = alive.stream().filter(u -> !seekers.contains(u)).count();
        return phase == 0 ? "&aSembunyi! &8| &fPemburu dilepas dalam &e" + timer + "s"
                : "&6Tiny Hunt &8| &fPelarian: &a" + h + " &8| &fSisa waktu: &e" + timer + "s";
    }
}
