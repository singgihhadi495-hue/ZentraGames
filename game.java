package id.zentra.minigames;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.Predicate;

/** Kerangka dasar semua minigame: antrean, hitung mundur, eliminasi, pemenang, reset arena. */
public abstract class Game {
    public enum State { WAITING, COUNTDOWN, RUNNING, ENDING, RESETTING }

    static final int LOBBY_UP = 50;

    private record Saved(ItemStack[] inv, Location loc, GameMode gm, double hp, int food, float sat,
                         float exp, int lvl, List<PotionEffect> fx, boolean fly) {}

    protected final ZentraMinigames plugin;
    protected final World world;
    public final String key, name;
    public final Material icon;
    public final int instance, cx, by, cz;
    public final List<String> rules;
    public int min, max;
    public State state = State.RESETTING;
    public boolean open = true;

    protected boolean pvp, envDamage, fallDamage, canBuild, lockInv = true;
    protected int voidY;
    protected int countdown, cdFull, runSecs;

    public final Set<UUID> players = new LinkedHashSet<>();
    public final Set<UUID> alive = new LinkedHashSet<>();
    protected final Map<UUID, UUID> lastHit = new HashMap<>();
    private final Map<UUID, Saved> saved = new HashMap<>();
    protected final Random rnd = new Random();
    protected final BossBar bar;
    private long ticks;
    private BukkitTask task;

    protected Game(ZentraMinigames plugin, String key, String name, Material icon, int instance,
                   int cx, int by, int cz, int min, int max, List<String> rules) {
        this.plugin = plugin;
        this.world = plugin.manager().world();
        this.key = key;
        this.name = name;
        this.icon = icon;
        this.instance = instance;
        this.cx = cx;
        this.by = by;
        this.cz = cz;
        this.min = min;
        this.max = max;
        this.rules = rules;
        this.voidY = by - 15;
        this.bar = Bukkit.createBossBar("", BarColor.BLUE, BarStyle.SOLID);
    }

    // ---------- hook yang dioverride tiap game ----------
    protected abstract void buildArena(Blueprint b);
    protected abstract void onStart();
    protected void onSecond() {}
    protected void onFast() {}
    protected void onEnd() {}
    protected void onReset() {}
    protected void onLeave(Player p, boolean wasAlive) {}
    protected void onHit(EntityDamageEvent e, Player victim, Player damager) { e.setCancelled(true); }
    protected String barText() { return "&b" + name + " &8| &fHidup: &a" + alive.size(); }
    public void onInteract(PlayerInteractEvent e) {}
    public boolean allowBreak(Block b, Player p) { return false; }
    public boolean allowPlace(Block b, Player p) { return false; }
    public void onExplode(EntityExplodeEvent e) { e.blockList().clear(); }

    // ---------- info ----------
    public String id() { return key + "-" + instance; }
    public boolean canBuild() { return canBuild; }
    public boolean lockInv() { return lockInv; }
    public boolean joinable() {
        return open && (state == State.WAITING || state == State.COUNTDOWN) && players.size() < max;
    }
    public Location lobby() { return new Location(world, cx + 0.5, by + LOBBY_UP + 1, cz + 0.5); }
    public Location specSpawn() { return new Location(world, cx + 0.5, by + 30, cz + 0.5); }

    // ---------- siklus hidup ----------
    public void activate() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::loop, 20L, 5L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (UUID u : new ArrayList<>(players)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) restore(p);
        }
        players.clear();
        alive.clear();
        bar.removeAll();
    }

    public void build(Runnable done) {
        state = State.RESETTING;
        Blueprint b = new Blueprint();
        buildLobby(b);
        buildArena(b);
        b.build(plugin, world, done);
    }

    private void buildLobby(Blueprint b) {
        int y = by + LOBBY_UP;
        b.cone(cx, y - 1, cz, 9, 9, Material.DEEPSLATE);
        b.disc(cx, y, cz, 8, Material.WHITE_CONCRETE);
        b.ring(cx, y, cz, 5, 6, Material.LIGHT_BLUE_CONCRETE);
        b.disc(cx, y, cz, 2, Material.SEA_LANTERN);
        for (int h = 1; h <= 3; h++) b.ring(cx, y + h, cz, 7, 8, Material.LIGHT_BLUE_STAINED_GLASS);
        for (int[] o : new int[][]{{7, 0}, {-7, 0}, {0, 7}, {0, -7}}) {
            b.fill(cx + o[0], y + 1, cz + o[1], cx + o[0], y + 4, cz + o[1], Material.QUARTZ_PILLAR);
            b.set(cx + o[0], y + 5, cz + o[1], Material.SEA_LANTERN);
        }
    }

    private void loop() {
        ticks++;
        if (state == State.RESETTING) return;
        if (ticks % 4 == 0) second();
        if (state == State.RUNNING) onFast();
        for (UUID u : new ArrayList<>(players)) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            double y = p.getLocation().getY();
            if (state == State.WAITING || state == State.COUNTDOWN) {
                if (y < by + LOBBY_UP - 8) p.teleport(lobby());
            } else if (y < voidY) {
                if (state == State.RUNNING && alive.contains(u)) onOut(p, lastHitter(p), "jatuh ke void");
                else p.teleport(specSpawn());
            }
        }
    }

    private void second() {
        if (state == State.WAITING) {
            if (players.size() >= min) {
                state = State.COUNTDOWN;
                cdFull = Math.max(5, plugin.getConfig().getInt("countdown", 20));
                countdown = cdFull;
            } else if (!players.isEmpty()) {
                setBar("&7Menunggu pemain &8(" + players.size() + "/" + min + ")", 1, BarColor.WHITE);
            }
        } else if (state == State.COUNTDOWN) {
            if (players.size() < min) {
                state = State.WAITING;
                bc("&cPemain kurang, hitung mundur dibatalkan.");
                return;
            }
            if (players.size() >= max && countdown > 5) countdown = 5;
            if (countdown <= 0) {
                begin();
                return;
            }
            setBar("&eMulai dalam &f" + countdown + "s &8(" + players.size() + "/" + max + ")",
                    countdown / (double) cdFull, BarColor.YELLOW);
            if (countdown <= 5 || countdown == 10 || countdown == 15) {
                for (Player p : online()) {
                    Msg.title(p, "&e&l" + countdown, "&7Bersiaplah!", 0, 20, 5);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                }
            }
            countdown--;
        } else if (state == State.RUNNING) {
            runSecs++;
            setBar(barText(), 1, BarColor.BLUE);
            onSecond();
        }
    }

    public void forceStart() {
        if ((state == State.WAITING || state == State.COUNTDOWN) && !players.isEmpty()) begin();
    }

    private void begin() {
        state = State.RUNNING;
        runSecs = 0;
        alive.clear();
        alive.addAll(players);
        lastHit.clear();
        for (Player p : online()) {
            resetPlayer(p, canBuild ? GameMode.SURVIVAL : GameMode.ADVENTURE);
            Msg.title(p, "&b&l" + name.toUpperCase(), "&7" + rules.get(0), 5, 50, 10);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.2f);
        }
        onStart();
    }

    // ---------- pemain masuk / keluar ----------
    public boolean join(Player p) {
        if (!joinable()) return false;
        UUID u = p.getUniqueId();
        ItemStack[] copy = p.getInventory().getContents().clone();
        for (int i = 0; i < copy.length; i++) if (copy[i] != null) copy[i] = copy[i].clone();
        saved.put(u, new Saved(copy, p.getLocation().clone(), p.getGameMode(), p.getHealth(), p.getFoodLevel(),
                p.getSaturation(), p.getExp(), p.getLevel(), new ArrayList<>(p.getActivePotionEffects()), p.getAllowFlight()));
        players.add(u);
        p.leaveVehicle();
        resetPlayer(p, GameMode.ADVENTURE);
        p.teleport(lobby());
        p.getInventory().setItem(8, plugin.leaveItem());
        bar.addPlayer(p);
        bc("&a" + p.getName() + " &7bergabung &8(" + players.size() + "/" + max + ")");
        Msg.send(p, "&b" + name + " &7- Aturan main:");
        for (String r : rules) p.sendMessage(Msg.c("&8 \u2022 &f" + r));
        return true;
    }

    public void leave(Player p) {
        UUID u = p.getUniqueId();
        if (!players.remove(u)) return;
        boolean wasAlive = alive.remove(u);
        lastHit.remove(u);
        restore(p);
        if (state == State.ENDING || state == State.RESETTING) return;
        bc("&c" + p.getName() + " &7keluar &8(" + players.size() + ")");
        if (state == State.RUNNING) {
            onLeave(p, wasAlive);
            checkWin();
        } else if (state == State.COUNTDOWN && players.size() < min) {
            state = State.WAITING;
        }
    }

    /** Dipanggil kalau pemain mati lewat jalur lain (misal /kill). */
    public void playerDied(Player p) {
        if (state == State.RUNNING && alive.contains(p.getUniqueId())) onOut(p, lastHitter(p), "tewas");
        else p.teleport(state == State.WAITING || state == State.COUNTDOWN ? lobby() : specSpawn());
    }

    protected void resetPlayer(Player p, GameMode gm) {
        p.getInventory().clear();
        p.setGameMode(gm);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setExp(0f);
        p.setLevel(0);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setAllowFlight(false);
    }

    private void restore(Player p) {
        Saved s = saved.remove(p.getUniqueId());
        bar.removePlayer(p);
        plugin.setScale(p, 1.0);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.getInventory().clear();
        if (s == null) {
            p.setGameMode(GameMode.SURVIVAL);
            p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            return;
        }
        p.teleport(s.loc());
        p.setGameMode(s.gm());
        p.getInventory().setContents(s.inv());
        p.setHealth(Math.max(1.0, Math.min(s.hp(), p.getMaxHealth())));
        p.setFoodLevel(s.food());
        p.setSaturation(s.sat());
        p.setExp(s.exp());
        p.setLevel(s.lvl());
        for (PotionEffect e : s.fx()) p.addPotionEffect(e);
        p.setAllowFlight(s.fly() || s.gm() == GameMode.CREATIVE || s.gm() == GameMode.SPECTATOR);
        p.setFireTicks(0);
        p.setFallDistance(0f);
    }

    // ---------- damage ----------
    public void onDamage(EntityDamageEvent e, Player v, Player d) {
        UUID vu = v.getUniqueId();
        if (state != State.RUNNING || !alive.contains(vu)) {
            e.setCancelled(true);
            return;
        }
        EntityDamageEvent.DamageCause c = e.getCause();
        if (c == EntityDamageEvent.DamageCause.VOID) {
            e.setCancelled(true);
            onOut(v, lastHitter(v), "jatuh ke void");
            return;
        }
        if (c == EntityDamageEvent.DamageCause.FALL && !fallDamage) {
            e.setCancelled(true);
            return;
        }
        if (d != null) {
            if (!alive.contains(d.getUniqueId())) {
                e.setCancelled(true);
                return;
            }
            lastHit.put(vu, d.getUniqueId());
            if (!pvp) {
                onHit(e, v, d);
                return;
            }
        } else if (!envDamage && c != EntityDamageEvent.DamageCause.FALL) {
            e.setCancelled(true);
            return;
        }
        if (!e.isCancelled() && v.getHealth() - e.getFinalDamage() <= 0) {
            e.setCancelled(true);
            onOut(v, lastHitter(v), "tewas");
        }
    }

    protected Player lastHitter(Player v) {
        UUID u = lastHit.get(v.getUniqueId());
        if (u == null) return null;
        Player p = Bukkit.getPlayer(u);
        return p != null && players.contains(u) ? p : null;
    }

    // ---------- eliminasi & pemenang ----------
    protected void onOut(Player p, Player killer, String reason) {
        eliminate(p, killer, reason);
    }

    protected void eliminate(Player p, Player killer, String reason) {
        if (!alive.remove(p.getUniqueId())) return;
        String m = killer != null
                ? "&c" + p.getName() + " &7dikalahkan oleh &e" + killer.getName()
                : "&c" + p.getName() + " &7" + reason;
        bc(m + " &8(" + alive.size() + " tersisa)");
        p.getInventory().clear();
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.teleport(specSpawn());
        p.setGameMode(GameMode.SPECTATOR);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_DEATH, 1f, 1f);
        Msg.title(p, "&c&lTERSINGKIR", "&7Posisi #" + (alive.size() + 1), 5, 40, 10);
        checkWin();
    }

    protected void checkWin() {
        if (state != State.RUNNING) return;
        if (alive.size() <= 1) end(alivePlayers());
    }

    protected void end(List<Player> winners) {
        if (state == State.ENDING || state == State.RESETTING) return;
        state = State.ENDING;
        onEnd();
        List<String> ns = new ArrayList<>();
        for (Player w : winners) ns.add(w.getName());
        String names = ns.isEmpty() ? "Tidak ada" : String.join("&7, &a", ns);
        for (Player p : online()) {
            Msg.title(p, "&6&lPEMENANG", "&a" + names, 5, 80, 20);
            p.sendMessage(Msg.c(Msg.PREFIX + "&6Pemenang: &a" + names));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        List<String> cmds = plugin.getConfig().getStringList("rewards.win");
        for (Player w : winners) {
            w.setGameMode(GameMode.ADVENTURE);
            w.setAllowFlight(true);
            for (int k = 0; k < 4; k++) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (w.isOnline()) fireworkAt(w.getLocation());
                }, k * 15L);
            }
            for (String cmd : cmds) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", w.getName()));
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::finish, 140L);
    }

    private void finish() {
        for (Player p : online()) restore(p);
        for (UUID u : players) plugin.manager().forget(u);
        players.clear();
        alive.clear();
        saved.clear();
        bar.removeAll();
        state = State.RESETTING;
        onReset();
        for (Entity e : world.getNearbyEntities(new Location(world, cx, by + 20, cz), 130, 120, 130)) {
            if (!(e instanceof Player)) e.remove();
        }
        build(() -> state = State.WAITING);
    }

    // ---------- helper ----------
    protected void bc(String s) {
        for (Player p : online()) p.sendMessage(Msg.c(Msg.PREFIX + s));
    }

    protected void setBar(String text, double prog, BarColor col) {
        bar.setTitle(ChatColor.translateAlternateColorCodes('&', text));
        bar.setProgress(Math.max(0, Math.min(1, prog)));
        bar.setColor(col);
    }

    protected List<Player> online() {
        List<Player> l = new ArrayList<>();
        for (UUID u : players) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) l.add(p);
        }
        return l;
    }

    protected List<Player> alivePlayers() {
        return aliveWhere(u -> true);
    }

    protected List<Player> aliveWhere(Predicate<UUID> f) {
        List<Player> l = new ArrayList<>();
        for (UUID u : alive) {
            if (!f.test(u)) continue;
            Player p = Bukkit.getPlayer(u);
            if (p != null) l.add(p);
        }
        return l;
    }

    protected void spreadRing(List<Player> ps, double radius, double y) {
        int n = ps.size();
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / Math.max(1, n);
            double x = cx + 0.5 + Math.cos(a) * radius, z = cz + 0.5 + Math.sin(a) * radius;
            Location l = new Location(world, x, y, z);
            l.setDirection(new Vector(cx + 0.5 - x, 0, cz + 0.5 - z));
            ps.get(i).teleport(l);
        }
    }

    protected ItemStack item(Material m, int amt, String nm, String... lore) {
        ItemStack it = new ItemStack(m, amt);
        ItemMeta mm = it.getItemMeta();
        mm.displayName(Msg.c(nm));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(Msg.c(s));
            mm.lore(l);
        }
        it.setItemMeta(mm);
        return it;
    }

    protected void fireworkAt(Location loc) {
        Firework fw = (Firework) world.spawnEntity(loc, EntityType.FIREWORK_ROCKET);
        FireworkMeta m = fw.getFireworkMeta();
        m.addEffect(FireworkEffect.builder().withColor(Color.AQUA, Color.WHITE, Color.YELLOW)
                .with(FireworkEffect.Type.BALL_LARGE).withFlicker().build());
        m.setPower(1);
        fw.setFireworkMeta(m);
    }

    protected void sound(Sound s, float vol, float pitch) {
        for (Player p : online()) p.playSound(p.getLocation(), s, vol, pitch);
    }
                   }
