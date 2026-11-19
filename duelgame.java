package id.zentra.minigames;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.List;

/** Arena Duel: 1v1 dengan kit acak (Sword, Mace, NoDebuff). */
public final class DuelGame extends Game {
    private static final String[] KITS = {"Sword", "Mace", "NoDebuff"};
    private String kit = "Sword";

    public DuelGame(ZentraMinigames pl, int inst, int cx, int by, int cz) {
        super(pl, "duel", "Arena Duel", Material.DIAMOND_SWORD, inst, cx, by, cz, 2, 2, List.of(
                "Duel 1 lawan 1 di arena melayang, kit dipilih acak!",
                "Kit: Sword (klasik), Mace (smash dengan Wind Charge), NoDebuff (potion penyembuh).",
                "Kalah kalau HP habis atau jatuh dari arena. Ender pearl boleh, tapi hati-hati meleset!",
                "Ada 3 detik membeku di awal untuk bersiap."));
        voidY = by - 12;
        pvp = true;
        envDamage = true;
        fallDamage = true;
    }

    @Override
    protected void buildArena(Blueprint b) {
        b.cone(cx, by - 14, cz, 17, 12, Material.BLACKSTONE);
        b.disc(cx, by, cz, 16, Material.DEEPSLATE_TILES);
        b.ring(cx, by, cz, 14, 15, Material.POLISHED_BLACKSTONE_BRICKS);
        b.ring(cx, by, cz, 15, 16, Material.GILDED_BLACKSTONE);
        b.disc(cx, by, cz, 2, Material.CRYING_OBSIDIAN);
        for (int[] o : new int[][]{{7, 0}, {-7, 0}}) b.fill(cx + o[0], by + 1, cz - 2, cx + o[0], by + 3, cz + 2, Material.BLACKSTONE);
        for (int[] o : new int[][]{{0, 8}, {0, -8}}) b.fill(cx - 1, by + 1, cz + o[1], cx + 1, by + 2, cz + o[1], Material.POLISHED_BLACKSTONE_BRICKS);
        for (int[] o : new int[][]{{13, 13}, {-13, 13}, {13, -13}, {-13, -13}}) {
            b.fill(cx + o[0], by + 1, cz + o[1], cx + o[0], by + 5, cz + o[1], Material.CRYING_OBSIDIAN);
            b.set(cx + o[0], by + 6, cz + o[1], Material.SEA_LANTERN);
        }
    }

    @Override
    protected void onStart() {
        kit = KITS[rnd.nextInt(KITS.length)];
        List<Player> ps = alivePlayers();
        spreadRing(ps, 11, by + 1);
        for (Player p : ps) {
            giveKit(p);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 9, false, false));
            Msg.title(p, "&c&lDUEL", "&7Kit: &e" + kit, 5, 50, 10);
        }
        bc("&7Kit terpilih: &e" + kit);
    }

    private void giveKit(Player p) {
        PlayerInventory inv = p.getInventory();
        switch (kit) {
            case "Mace" -> {
                inv.setHelmet(new ItemStack(Material.IRON_HELMET));
                inv.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                inv.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
                inv.setBoots(new ItemStack(Material.IRON_BOOTS));
                inv.setItem(0, new ItemStack(Material.MACE));
                inv.setItem(1, new ItemStack(Material.WIND_CHARGE, 16));
                inv.setItem(2, new ItemStack(Material.GOLDEN_APPLE, 4));
                inv.setItem(3, new ItemStack(Material.WATER_BUCKET));
            }
            case "NoDebuff" -> {
                inv.setHelmet(new ItemStack(Material.DIAMOND_HELMET));
                inv.setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
                inv.setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
                inv.setBoots(new ItemStack(Material.DIAMOND_BOOTS));
                inv.setItem(0, new ItemStack(Material.DIAMOND_SWORD));
                inv.setItem(1, new ItemStack(Material.ENDER_PEARL, 2));
                for (int i = 2; i < 9; i++) inv.setItem(i, healPotion());
                for (int i = 9; i < 18; i++) inv.setItem(i, healPotion());
            }
            default -> {
                inv.setHelmet(new ItemStack(Material.DIAMOND_HELMET));
                inv.setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
                inv.setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
                inv.setBoots(new ItemStack(Material.DIAMOND_BOOTS));
                inv.setItem(0, new ItemStack(Material.DIAMOND_SWORD));
                inv.setItem(1, new ItemStack(Material.GOLDEN_APPLE, 6));
                inv.setItem(2, new ItemStack(Material.ENDER_PEARL, 1));
                inv.setItemInOffHand(new ItemStack(Material.SHIELD));
            }
        }
    }

    private ItemStack healPotion() {
        ItemStack pot = new ItemStack(Material.SPLASH_POTION);
        PotionMeta pm = (PotionMeta) pot.getItemMeta();
        pm.setBasePotionType(PotionType.STRONG_HEALING);
        pot.setItemMeta(pm);
        return pot;
    }

    @Override
    protected String barText() {
        return "&cArena Duel &8| &fKit: &e" + kit;
    }
                                                            }
