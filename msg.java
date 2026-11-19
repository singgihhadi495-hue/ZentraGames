package id.zentra.minigames;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

public final class Msg {
    public static final String PREFIX = "&8[&bZentra&3Minigames&8] &7";

    private Msg() {}

    public static Component c(String s) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    public static void send(Player p, String s) {
        p.sendMessage(c(PREFIX + s));
    }

    public static void title(Player p, String t, String sub, int inTicks, int stayTicks, int outTicks) {
        p.showTitle(Title.title(c(t), c(sub), Title.Times.times(
                Duration.ofMillis(inTicks * 50L), Duration.ofMillis(stayTicks * 50L), Duration.ofMillis(outTicks * 50L))));
    }

    public static void bar(Player p, String s) {
        p.sendActionBar(c(s));
    }
  }
