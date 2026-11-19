package id.zentra.minigames;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/** Daftar perintah "isi blok" yang dieksekusi bertahap per tick supaya server tidak lag. */
public final class Blueprint {
    private record Fill(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {}

    private final List<Fill> fills = new ArrayList<>();

    public Blueprint fill(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        fills.add(new Fill(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2), m));
        return this;
    }

    public Blueprint set(int x, int y, int z, Material m) {
        return fill(x, y, z, x, y, z, m);
    }

    /** Kotak berongga (hanya cangkang). */
    public Blueprint box(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        int a = Math.min(x1, x2), b = Math.min(y1, y2), c = Math.min(z1, z2);
        int A = Math.max(x1, x2), B = Math.max(y1, y2), C = Math.max(z1, z2);
        fill(a, b, c, A, b, C, m);
        fill(a, B, c, A, B, C, m);
        fill(a, b, c, a, B, C, m);
        fill(A, b, c, A, B, C, m);
        fill(a, b, c, A, B, c, m);
        fill(a, b, C, A, B, C, m);
        return this;
    }

    /** true jika titik (dx,dz) berada di cincin: rin < jarak <= rout. rin < 0 berarti cakram penuh. */
    public static boolean inRing(int dx, int dz, int rin, int rout) {
        int d2 = dx * dx + dz * dz;
        return d2 <= rout * rout && (rin < 0 || d2 > rin * rin);
    }

    public Blueprint ring(int cx, int y, int cz, int rin, int rout, Material m) {
        for (int dz = -rout; dz <= rout; dz++) {
            int start = Integer.MIN_VALUE;
            for (int dx = -rout; dx <= rout + 1; dx++) {
                boolean in = dx <= rout && inRing(dx, dz, rin, rout);
                if (in && start == Integer.MIN_VALUE) {
                    start = dx;
                } else if (!in && start != Integer.MIN_VALUE) {
                    fill(cx + start, y, cz + dz, cx + dx - 1, y, cz + dz, m);
                    start = Integer.MIN_VALUE;
                }
            }
        }
        return this;
    }

    public Blueprint disc(int cx, int y, int cz, int r, Material m) {
        return ring(cx, y, cz, -1, r, m);
    }

    /** Kerucut terbalik (dasar pulau melayang). */
    public Blueprint cone(int cx, int ytop, int cz, int r, int depth, Material m) {
        for (int i = 0; i < depth; i++) {
            int rr = Math.max(1, r * (depth - i) / depth);
            disc(cx, ytop - i, cz, rr, m);
        }
        return this;
    }

    public Blueprint cylinder(int cx, int y1, int y2, int cz, int r, Material m) {
        for (int y = y1; y <= y2; y++) disc(cx, y, cz, r, m);
        return this;
    }

    public void build(ZentraMinigames plugin, World w, Runnable done) {
        final int budgetPerTick = Math.max(500, plugin.getConfig().getInt("build.blocks-per-tick", 4000));
        new BukkitRunnable() {
            int fi = 0;
            long off = 0;

            @Override
            public void run() {
                int budget = budgetPerTick;
                while (budget > 0 && fi < fills.size()) {
                    Fill f = fills.get(fi);
                    int dx = f.x2() - f.x1() + 1, dy = f.y2() - f.y1() + 1, dz = f.z2() - f.z1() + 1;
                    long vol = (long) dx * dy * dz;
                    while (off < vol && budget > 0) {
                        int ix = (int) (off % dx);
                        int iy = (int) ((off / dx) % dy);
                        int iz = (int) (off / ((long) dx * dy));
                        var b = w.getBlockAt(f.x1() + ix, f.y1() + iy, f.z1() + iz);
                        if (b.getType() != f.m()) b.setType(f.m(), false);
                        off++;
                        budget--;
                    }
                    if (off >= vol) {
                        fi++;
                        off = 0;
                    }
                }
                if (fi >= fills.size()) {
                    cancel();
                    if (done != null) done.run();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
             }
