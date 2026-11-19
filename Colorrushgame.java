package id.zentra.minigames;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Color Rush: berdiri di warna yang benar sebelum lantai lain lenyap. */
public final class ColorRushGame extends Game {
    private static final String[] C = {"WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK", "CYAN", "PURPLE", "BLUE", "GREEN", "RED"};
    private static final String[] N = {"Putih", "Oranye", "Magenta", "Biru Muda", "Kuning", "Hijau Muda", "Pink", "Sian", "Ungu", "Biru", "Hijau", "Merah"};
    private static final Material[] M = new Material[12];
    static {
        for (int i = 0; i < 12; i++) M[i] = Material.valueOf(C[i] + "_CONCRETE");
    }
    private static final int HALF = 18, TILE = 3, T = 12;

    private final int[][] tiles = new int[T][T];
    private int phase, timer, round, target;

    public ColorRushGame(ZentraMinigames pl, int inst, int cx, int by, int cz) {
        super(pl, "colorrush", "Color Rush", Material.LIME_CONCRETE, inst, cx, by, cz, 2, 16, List.of(
                "Berdiri di warna yang diminta sebelum musik berhenti!",
                "Tiap ronde sebuah warna ditampilkan di hotbar dan layarmu. Warna lain akan lenyap.",
                "Waktu berpikir makin singkat setiap ronde. Dorong lawan dari warna aman dengan pukulan!",
                "Yang jatuh ke void tersingkir. Bertahan sampai ronde ke-25 atau jadi satu-satunya."));
        voidY = by - 8;
    }

    private void randomTiles() {
        for (int x = 0; x < T; x++) for (int z = 0; z < T; z++) tiles[x][z] = rnd.nextInt(12);
    }

    @Override
    protected void buildArena(Blueprint b) {
        randomTiles();
        b.cone(cx, by - 16, cz, 19, 12, Material.AMETHYST_BLOCK);
        for (int tx = 0; tx < T; tx++) {
            for (int tz = 0; tz < T; tz++) {
                int x0 = cx - HALF + tx * TILE, z0 = cz - HALF + tz * TILE;
                b.fill(x0, by, z0, x0 + TILE - 1, by, z0 + TILE - 1, M[tiles[tx][tz]]);
            }
        }
        for (int[] o : new int[][]{{24, 24}, {-24, 24}, {24, -24}, {-24, -24}}) {
            int x = cx + o[0], z = cz + o[1];
            b.fill(x, by - 12, z, x, by + 8, z, Material.BLACK_CONCRETE);
            b.set(x, by + 9, z, Material.NOTE_BLOCK);
            b.set(x, by + 10, z, Material.SEA_LANTERN);
        }
    }

    private void paintFloor() {
        randomTiles();
        for (int tx = 0; tx < T; tx++)
            for (int tz = 0; tz < T; tz++)
                for (int dx = 0; dx < TILE; dx++)
                    for (int dz = 0; dz < TILE; dz++)
                        world.getBlockAt(cx - HALF + tx * TILE + dx, by, cz - HALF + tz * TILE + dz).setType(M[tiles[tx][tz]], false);
    }

    private void clearOthers() {
        for (int tx = 0; tx < T; tx++)
            for (int tz = 0; tz < T; tz++) {
                if (tiles[tx][tz] == target) continue;
                for (int dx = 0; dx < TILE; dx++)
                    for (int dz = 0; dz < TILE; dz++)
                        world.getBlockAt(cx - HALF + tx * TILE + dx, by, cz - HALF + tz * TILE + dz).setType(Material.AIR, false);
            }
    }

    @Override
    protected void onStart() {
        round = 0;
        phase = 0;
        timer = 3;
        paintFloor();
        spreadRing(alivePlayers(), 12, by + 1);
    }

    @Override
    protected void onSecond() {
        timer--;
        if (phase == 0) {
            if (timer <= 0) {
                round++;
                target = tiles[rnd.nextInt(T)][rnd.nextInt(T)];
                phase = 1;
                timer = Math.max(2, 8 - round / 2);
                for (Player p : alivePlayers()) {
                    ItemStack it = item(M[target], 1, "&f&lBerdiri di: " + N[target]);
                    for (int i = 0; i < 9; i++) p.getInventory().setItem(i, it);
                    Msg.title(p, "&e&lRONDE " + round, "&fBerdiri di warna &b&l" + N[target], 0, 30, 5);
                }
                sound(Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1.2f);
            } else {
                for (Player p : alivePlayers()) Msg.bar(p, "&eBersiap...");
            }
        } else if (phase == 1) {
            for (Player p : alivePlayers()) {
                Msg.bar(p, "&fBerdiri di &b&l" + N[target] + " &8| &c" + Math.max(0, timer) + "s");
                p.setLevel(Math.max(0, timer));
            }
            sound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1f + (1f - timer / 8f));
            if (timer <= 0) {
                clearOthers();
                sound(Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
                phase = 2;
                timer = 3;
            }
        } else {
            if (timer <= 0) {
                if (round >= 25) {
                    end(alivePlayers());
                    return;
                }
                paintFloor();
                phase = 0;
                timer = 2;
                for (Player p : alivePlayers()) {
                    p.getInventory().clear();
                    p.setLevel(0);
                }
            }
        }
    }

    @Override
    protected void onHit(org.bukkit.event.entity.EntityDamageEvent e, Player v, Player d) {
        e.setDamage(0);
    }

    @Override
    protected String barText() {
        return "&dColor Rush &8| &fRonde &e" + round + " &8| &fHidup: &a" + alive.size();
    }
                  }
