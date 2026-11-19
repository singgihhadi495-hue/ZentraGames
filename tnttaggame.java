package id.zentra.minigames;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/** Bom Estafet: oper bom ke pemain lain sebelum meledak. */
public final class TntTagGame extends Game {
    private final Set<UUID> its = new HashSet<>();
    private final Map<UUID, Long> tagCd = new HashMap<>();
    private int round, phase, timer, roundLen;

    public TntTagGame(ZentraMinigames pl, int inst, int cx, int by, int cz) {
        super(pl, "tnttag", "Bom Estafet", Material.TNT, inst, cx, by, cz, 3, 16, List.of(
                "Jangan jadi pembawa bom saat sumbunya habis!",
                "Pembawa bom ditandai helm TNT dan efek menyala. Pukul orang lain untuk memindahkan bom.",
                "Setiap ronde pembawa bom meledak dan tersingkir. Ronde makin singkat.",
                "Bom yang baru dioper punya perlindungan 1,5 detik. Bertahan sampai terakhir!"));
        voidY = by - 10;
    }

    @Override
    protected void buildArena(Blueprint b) {
        b.cone(cx, by - 14, cz, 17, 12, Material.BLACKSTONE);
        b.disc(cx, by, cz, 17, Material.GRAY_CONCRETE);
        b.ring(cx, by, cz, 6, 7, Material.ORANGE_CONCRETE);
        b.ring(cx, by, cz, 11, 12, Material.ORANGE_CONCRETE);
        b.ring(cx, by, cz, 16, 17, Material.RED_CONCRETE);
        b.disc(cx, by, cz, 2, Material.YELLOW_CONCRETE);
        b.set(cx, by + 1, cz, Material.TNT);
        for (int[] o : new int[][]{{9, 0}, {-9, 0}, {0, 9}, {0, -9}}) {
            boolean alongX = o[0] == 0;
            int x = cx + o[0], z = cz + o[1];
            if (alongX) b.fill(x - 2, by + 1, z, x + 2, by + 3, z, Material.POLISHED_BLACKSTONE_BRICKS);
            else b.fill(x, by + 1, z - 2, x, by + 3, z + 2, Material.POLISHED_BLACKSTONE_BRICKS);
        }
        for (int[] o : new int[][]{{12, 12}, {-12, 12}, {12, -12}, {-12, -12}}) {
            int x = cx + o[0], z = cz + o[1];
            b.fill(x, by + 1, z, x, by + 6, z, Material.OBSIDIAN);
            b.set(x, by + 7, z, Material.SEA_LANTERN);
        }
    }

    @Override
    protected void onStart() {
        its.clear();
        tagCd.clear();
        round = 0;
        phase = 0;
        timer = 5;
        spreadRing(alivePlayers(), 12, by + 1);
        bc("&cBom pertama dipilih dalam 5 detik...");
    }

    @Override
    protected void onSecond() {
        timer--;
        if (phase == 0) {
            if (timer <= 0) startRound();
            else for (Player p : alivePlayers()) Msg.bar(p, "&eRonde berikutnya dalam &f" + timer + "s");
            return;
        }
        for (Player p : alivePlayers()) {
            if (its.contains(p.getUniqueId())) {
                Msg.bar(p, "&c&lKAMU BOM! &f" + timer + "s &7- pukul orang lain!");
                float pitch = 0.5f + (1f - Math.max(0, timer) / (float) roundLen) * 1.5f;
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, pitch);
            } else {
                Msg.bar(p, "&aHindari pembawa bom! &7" + timer + "s");
            }
        }
        if (timer <= 0) explode();
    }

    private void startRound() {
        round++;
        roundLen = Math.max(12, 30 - (round - 1) * 3);
        List<Player> ps = alivePlayers();
        Collections.shuffle(ps, rnd);
        int n = Math.max(1, ps.size() / 4);
        its.clear();
        for (int i = 0; i < n && i < ps.size(); i++) setIt(ps.get(i), true);
        phase = 1;
        timer = roundLen;
        StringBuilder sb = new StringBuilder();
        for (UUID u : its) sb.append(Bukkit.getOfflinePlayer(u).getName()).append(" ");
        bc("&6Ronde &f" + round + " &6- bom dibawa: &c" + sb.toString().trim() + " &8(" + roundLen + "s)");
        sound(Sound.ENTITY_TNT_PRIMED, 1f, 1f);
    }

    private void setIt(Player p, boolean on) {
        if (on) {
            its.add(p.getUniqueId());
            p.getInventory().setHelmet(new ItemStack(Material.TNT));
            p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 60, 0, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 60, 1, false, false));
            Msg.title(p, "&c&lKAMU BOM!", "&7Pukul seseorang!", 0, 30, 10);
        } else {
            its.remove(p.getUniqueId());
            p.getInventory().setHelmet(null);
            p.removePotionEffect(PotionEffectType.GLOWING);
            p.removePotionEffect(PotionEffectType.SPEED);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 60, 0, false, false));
        }
    }

    private void explode() {
        List<Player> boom = new ArrayList<>();
        for (UUID u : new ArrayList<>(its)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) boom.add(p);
        }
        its.clear();
        for (Player p : boom) {
            world.spawnParticle(Particle.EXPLOSION_EMITTER, p.getLocation(), 1);
            world.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 1f);
            eliminate(p, null, "meledak jadi serpihan");
        }
        if (state == State.RUNNING) {
            for (Player p : alivePlayers()) {
                p.getInventory().setHelmet(null);
                p.removePotionEffect(PotionEffectType.GLOWING);
            }
            phase = 0;
            timer = 4;
        }
    }

    @Override
    protected void onHit(EntityDamageEvent e, Player v, Player d) {
        e.setCancelled(true);
        if (phase != 1) return;
        if (!its.contains(d.getUniqueId()) || its.contains(v.getUniqueId())) return;
        long now = System.currentTimeMillis();
        if (now - tagCd.getOrDefault(d.getUniqueId(), 0L) < 1500) return;
        tagCd.put(v.getUniqueId(), now);
        setIt(d, false);
        setIt(v, true);
        world.playSound(v.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.6f);
    }

    @Override
    protected void onEnd() {
        its.clear();
    }

    @Override
    protected String barText() {
        return "&cBom Estafet &8| &fRonde &e" + round + " &8| &fHidup: &a" + alive.size();
    }
              }
