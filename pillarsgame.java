package id.zentra.minigames;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

/** Pillars of Fortune: tiap pemain di pilar tinggi, item acak turun dari langit. */
public final class PillarsGame extends Game {
    private static final int N = 12, RAD = 14, TOP = 18;
    private static final Material[] COL = {Material.RED_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.YELLOW_TERRACOTTA,
            Material.LIME_TERRACOTTA, Material.GREEN_TERRACOTTA, Material.CYAN_TERRACOTTA, Material.LIGHT_BLUE_TERRACOTTA,
            Material.BLUE_TERRACOTTA, Material.PURPLE_TERRACOTTA, Material.MAGENTA_TERRACOTTA, Material.PINK_TERRACOTTA,
            Material.WHITE_TERRACOTTA};
    private static final Material[] BLOCKS = {Material.COBBLESTONE, Material.OAK_PLANKS, Material.STONE_BRICKS, Material.SANDSTONE,
            Material.WHITE_WOOL, Material.DEEPSLATE_BRICKS, Material.GLASS, Material.OAK_LOG, Material.NETHERRACK};

    private final int[] px = new int[N], pz = new int[N];
    private final Set<Block> placed = new HashSet<>();
    private int itemEvery = 8;
    private boolean fullClear;

    public PillarsGame(ZentraMinigames pl, int inst, int cx, int by, int cz) {
        super(pl, "pillars", "Pillars of Fortune", Material.QUARTZ_PILLAR, inst, cx, by, cz, 2, 12, List.of(
                "Bertahan di atas pilar, rampas keberuntungan, tendang lawan ke void!",
                "Setiap 8 detik kamu menerima item acak. Susun jembatan atau serang lawan.",
                "Tiap 45 detik terjadi KEJADIAN KACAU: gravitasi rendah, hujan TNT, banjir item, dan lainnya.",
                "Setelah 5 menit item datang tiap 3 detik (Sudden Death). Orang terakhir menang."));
        voidY = by - 12;
        pvp = true;
        envDamage = true;
        fallDamage = true;
        canBuild = true;
        lockInv = false;
        for (int i = 0; i < N; i++) {
            double a = Math.PI * 2 * i / N;
            px[i] = cx + (int) Math.round(Math.cos(a) * RAD);
            pz[i] = cz + (int) Math.round(Math.sin(a) * RAD);
        }
    }

    @Override
    protected void buildArena(Blueprint b) {
        if (fullClear) {
            b.fill(cx - 26, by - 13, cz - 26, cx + 26, by + TOP + 30, cz + 26, Material.AIR);
            fullClear = false;
        }
        b.cone(cx, by - 14, cz, 18, 12, Material.OBSIDIAN);
        for (int i = 0; i < N; i++) {
            b.fill(px[i], by - 8, pz[i], px[i], by + TOP - 1, pz[i], COL[i % COL.length]);
            b.fill(px[i] - 1, by + TOP, pz[i] - 1, px[i] + 1, by + TOP, pz[i] + 1, Material.QUARTZ_BLOCK);
            b.set(px[i], by + TOP, pz[i], Material.GOLD_BLOCK);
            for (int[] c : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) b.set(px[i] + c[0], by + TOP, pz[i] + c[1], Material.SEA_LANTERN);
        }
        b.fill(cx - 1, by - 10, cz - 1, cx + 1, by + TOP + 4, cz + 1, Material.AMETHYST_BLOCK);
        b.set(cx, by + TOP + 5, cz, Material.SEA_LANTERN);
        b.fill(cx, by + TOP + 6, cz, cx, by + TOP + 9, cz, Material.END_ROD);
    }

    @Override
    protected void onStart() {
        placed.clear();
        itemEvery = 8;
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < N; i++) idx.add(i);
        Collections.shuffle(idx, rnd);
        List<Player> ps = alivePlayers();
        for (int i = 0; i < ps.size(); i++) {
            int k = idx.get(i);
            Location l = new Location(world, px[k] + 0.5, by + TOP + 1, pz[k] + 0.5);
            l.setDirection(new Vector(cx - px[k], 0, cz - pz[k]));
            ps.get(i).teleport(l);
            ps.get(i).getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 4));
        }
        bc("&eItem pertama turun dalam 3 detik. Kejadian kacau tiap 45 detik!");
    }

    @Override
    protected void onSecond() {
        if (runSecs >= 3 && runSecs % itemEvery == 0) {
            for (Player p : alivePlayers()) giveRandom(p);
            sound(Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
        }
        if (runSecs > 0 && runSecs % 45 == 0) chaos();
        if (runSecs == 300) {
            itemEvery = 3;
            bc("&4&lSUDDEN DEATH! &cItem datang tiap 3 detik dan semua pemain bercahaya!");
            for (Player p : alivePlayers()) p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20 * 600, 0, false, false));
        }
        if (runSecs >= 480) end(alivePlayers());
    }

    private void giveRandom(Player p) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(randomItem());
        for (ItemStack it : left.values()) p.getWorld().dropItem(p.getLocation(), it);
    }

    private ItemStack randomItem() {
        int r = rnd.nextInt(100);
        if (r < 30) return new ItemStack(BLOCKS[rnd.nextInt(BLOCKS.length)], 8 + rnd.nextInt(9));
        if (r < 42) {
            Material[] w = {Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.STONE_AXE, Material.IRON_AXE, Material.DIAMOND_SWORD};
            return new ItemStack(w[rnd.nextInt(w.length)]);
        }
        if (r < 52) {
            Material[] a = {Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_BOOTS, Material.IRON_HELMET,
                    Material.IRON_LEGGINGS, Material.IRON_CHESTPLATE, Material.DIAMOND_BOOTS, Material.SHIELD};
            return new ItemStack(a[rnd.nextInt(a.length)]);
        }
        if (r < 60) return rnd.nextInt(4) == 0 ? new ItemStack(Material.GOLDEN_APPLE) : new ItemStack(Material.COOKED_PORKCHOP, 4);
        if (r < 66) return rnd.nextBoolean() ? new ItemStack(Material.BOW) : new ItemStack(Material.ARROW, 10);
        if (r < 72) return new ItemStack(Material.ENDER_PEARL, 1 + rnd.nextInt(2));
        if (r < 77) return rnd.nextBoolean() ? new ItemStack(Material.TNT, 3) : new ItemStack(Material.FLINT_AND_STEEL);
        if (r < 82) return new ItemStack(Material.WATER_BUCKET);
        if (r < 86) {
            ItemStack st = item(Material.STICK, 1, "&e&lTongkat Tendang");
            st.addUnsafeEnchantment(Enchantment.KNOCKBACK, 2);
            return st;
        }
        if (r < 90) return new ItemStack(Material.COBWEB, 4);
        if (r < 94) return new ItemStack(Material.WIND_CHARGE, 4);
        if (r < 97) return new ItemStack(Material.SNOWBALL, 12);
        return new ItemStack(Material.LAVA_BUCKET);
    }

    private void chaos() {
        List<Player> ps = alivePlayers();
        switch (rnd.nextInt(5)) {
            case 0 -> {
                bc("&d&l[KEJADIAN] &fGravitasi Rendah! &7(15 detik)");
                for (Player p : ps) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 300, 3));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 300, 0));
                }
            }
            case 1 -> {
                bc("&d&l[KEJADIAN] &fHujan TNT! &7Awas kepala!");
                for (Player p : ps) {
                    Location l = p.getLocation().add(rnd.nextInt(5) - 2, 8, rnd.nextInt(5) - 2);
                    TNTPrimed t = world.spawn(l, TNTPrimed.class);
                    t.setFuseTicks(50);
                }
            }
            case 2 -> {
                bc("&d&l[KEJADIAN] &fSpeed Rush! &7(15 detik)");
                for (Player p : ps) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 2));
            }
            case 3 -> {
                bc("&d&l[KEJADIAN] &fBanjir Item! &7Semua dapat 3 item.");
                for (Player p : ps) for (int i = 0; i < 3; i++) giveRandom(p);
            }
            default -> {
                bc("&d&l[KEJADIAN] &fSorotan! &7Semua pemain bercahaya 10 detik.");
                for (Player p : ps) p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, false, false));
            }
        }
        sound(Sound.ENTITY_WITHER_SPAWN, 0.4f, 1.6f);
    }

    @Override
    public boolean allowPlace(Block b, Player p) {
        if (state != State.RUNNING || b.getY() > by + TOP + 40) return false;
        placed.add(b);
        return true;
    }

    @Override
    public boolean allowBreak(Block b, Player p) {
        return state == State.RUNNING && placed.remove(b);
    }

    @Override
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(bl -> !placed.contains(bl));
        placed.removeAll(e.blockList());
    }

    @Override
    protected void onReset() {
        placed.clear();
        fullClear = true;
    }

    @Override
    protected String barText() {
        return "&bPillars of Fortune &8| &fHidup: &a" + alive.size() + " &8| &fItem tiap &e" + itemEvery + "s";
    }
              }
