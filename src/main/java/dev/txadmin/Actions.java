package dev.txadmin;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Aksi admin yang dipakai bersama oleh command, keybind, dan menu GUI. */
public final class Actions {
    private final TxAdmin p;

    public Actions(TxAdmin p) { this.p = p; }

    private String n(CommandSender s) { return s instanceof Player pl ? pl.getName() : "Console"; }

    // ---------- util tampilan ----------
    public void sound(Player t, String path) {
        String key = p.getConfig().getString(path, "");
        if (key == null || key.isBlank()) return;
        try { t.playSound(Sound.sound(Key.key(key), Sound.Source.MASTER, 1f, 1f)); } catch (Exception ignored) { }
    }

    /** Banner di atas layar (bossbar), mirip banner pengumuman txAdmin. */
    public void banner(Collection<? extends Player> to, Component text, BossBar.Color color, int seconds) {
        List<Player> list = new ArrayList<>(to);
        BossBar bar = BossBar.bossBar(text, 1f, color, BossBar.Overlay.PROGRESS);
        list.forEach(x -> x.showBossBar(bar));
        Bukkit.getScheduler().runTaskLater(p, () -> list.forEach(x -> x.hideBossBar(bar)), Math.max(1, seconds) * 20L);
    }

    private int bannerSeconds() { return p.getConfig().getInt("banner-seconds", 10); }

    // ---------- mode pemain ----------
    public void god(CommandSender by, Player t) {
        boolean on = p.modes.toggleGod(t);
        by.sendMessage(p.msg(on ? "god-on" : "god-off", "player", t.getName()));
    }

    /** NoClip + efek petir (visual saja, tanpa damage/api). */
    public void noclip(Player a) {
        boolean on = p.modes.toggleNoclip(a);
        a.getWorld().strikeLightningEffect(a.getLocation());
        a.sendMessage(p.msg(on ? "noclip-on" : "noclip-off"));
    }

    public void superjump(CommandSender by, Player t) {
        boolean on = p.modes.toggleJump(t);
        by.sendMessage(p.msg(on ? "jump-on" : "jump-off", "player", t.getName()));
    }

    public void heal(CommandSender by, Player t) {
        p.modes.heal(t);
        by.sendMessage(p.msg("healed", "player", t.getName()));
    }

    public void feed(CommandSender by, Player t) {
        t.setFoodLevel(20);
        t.setSaturation(20f);
        by.sendMessage(p.msg("fed", "player", t.getName()));
    }

    public void freeze(CommandSender by, Player t) {
        boolean on = p.modes.toggleFrozen(t);
        by.sendMessage(p.msg(on ? "freeze-on" : "freeze-off", "player", t.getName()));
    }

    public void tp(Player a, Player t) {
        p.modes.back.put(a.getUniqueId(), a.getLocation());
        a.teleport(t.getLocation());
        a.sendMessage(p.msg("tp", "player", t.getName()));
    }

    public void bring(Player a, Player t) {
        p.modes.back.put(t.getUniqueId(), t.getLocation());
        t.teleport(a.getLocation());
        a.sendMessage(p.msg("bring", "player", t.getName()));
    }

    public void back(Player a) {
        Location l = p.modes.back.remove(a.getUniqueId());
        if (l == null) { a.sendMessage(p.msg("no-back")); return; }
        a.teleport(l);
        a.sendMessage(p.msg("back"));
    }

    public void spectate(Player a, Player t) {
        p.modes.enterSpectate(a);
        a.setSpectatorTarget(t);
        a.sendMessage(p.msg("spectate", "player", t.getName()));
    }

    // ---------- moderasi ----------
    public void warn(CommandSender by, Player t, String reason) {
        p.store.addHistory(t.getUniqueId(), "WARN", n(by), reason);
        p.warnings.show(t, n(by), reason);
        by.sendMessage(p.msg("warn-sent", "player", t.getName(), "reason", reason));
    }

    public void kick(CommandSender by, Player t, String reason) {
        p.store.addHistory(t.getUniqueId(), "KICK", n(by), reason);
        t.kick(p.plain("kick-screen", "reason", reason, "by", n(by)));
        by.sendMessage(p.msg("kicked", "player", t.getName(), "reason", reason));
    }

    /** durationMs = 0 berarti permanen. */
    public void ban(CommandSender by, OfflinePlayer t, String name, long durationMs, String reason) {
        long until = durationMs == 0 ? 0 : System.currentTimeMillis() + durationMs;
        String banId = "B-" + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x1000, 0xFFFF)).toUpperCase();
        p.store.ban(t.getUniqueId(), name, reason, until, n(by), banId);
        Player online = t.getPlayer();
        if (online != null) {
            online.kick(p.plain("ban-screen", "reason", reason, "until", Store.untilText(until), "by", n(by), "id", banId));
        }
        by.sendMessage(p.msg("banned", "player", name, "until", Store.untilText(until), "reason", reason, "id", banId));
    }

    public void unban(CommandSender by, OfflinePlayer t, String name) {
        by.sendMessage(p.msg(p.store.unban(t.getUniqueId()) ? "unbanned" : "not-banned", "player", name));
    }

    // ---------- pengumuman / DM ----------
    public void announce(CommandSender by, String message) {
        String admin = n(by);
        Bukkit.broadcast(p.plain("announce-chat", "admin", admin, "message", message));
        banner(Bukkit.getOnlinePlayers(), p.plain("announce-banner", "admin", admin, "message", message),
                BossBar.Color.RED, bannerSeconds());
        for (Player x : Bukkit.getOnlinePlayers()) sound(x, "sounds.announce");
    }

    public void dm(CommandSender by, Player t, String message) {
        String admin = n(by);
        t.sendMessage(p.plain("dm-chat", "admin", admin, "message", message));
        banner(List.of(t), p.plain("dm-banner", "admin", admin, "message", message), BossBar.Color.YELLOW, bannerSeconds());
        sound(t, "sounds.dm");
        by.sendMessage(p.msg("dm-sent", "player", t.getName(), "message", message));
    }

    // ---------- efek ----------
    public void effect(CommandSender by, Player t, String id, int level, int seconds) {
        if (!id.matches("[a-z_:]+")) { by.sendMessage(p.msg("usage", "usage", "/tx effect <pemain> <efek> [level] [detik]")); return; }
        level = Math.max(1, Math.min(level, 255));
        String dur = seconds <= 0 ? "infinite" : String.valueOf(Math.min(seconds, 1_000_000));
        p.modes.cmd("effect give " + t.getName() + " " + id + " " + dur + " " + (level - 1) + " true");
        by.sendMessage(p.msg("effect-given", "effect", id, "player", t.getName()));
    }

    public void clearEffects(CommandSender by, Player t) {
        p.modes.fx.remove(t.getUniqueId());
        p.modes.jump.remove(t.getUniqueId());
        p.modes.cmd("effect clear " + t.getName());
        by.sendMessage(p.msg("effects-cleared", "player", t.getName()));
    }

    // ---------- server ----------
    public void restart(CommandSender by, int seconds) {
        p.restart.begin(seconds);
        by.sendMessage(p.msg("restart-started"));
    }

    public void cancelRestart(CommandSender by) {
        p.restart.cancel();
        Bukkit.broadcast(p.plain("restart-cancel"));
    }

    public void toggleWhitelist(CommandSender by) {
        boolean on = !Bukkit.hasWhitelist();
        Bukkit.setWhitelist(on);
        by.sendMessage(p.msg(on ? "whitelist-on" : "whitelist-off"));
    }

    /** Per admin: hanya admin yang menekan toggle ini yang melihat ID pemain lain. */
    public void toggleIds(Player a) {
        a.sendMessage(p.msg(p.ids.toggle(a) ? "ids-on" : "ids-off"));
    }

    /** Sembunyikan / tampilkan nametag asli pemain lain, khusus untuk admin ini. */
    public void toggleNames(Player a) {
        a.sendMessage(p.msg(p.ids.toggleNames(a) ? "names-on" : "names-off"));
    }

    public void toggleKeys(Player a) {
        a.sendMessage(p.msg(p.keys.toggle(a) ? "keys-on" : "keys-off"));
    }

    public void reload(CommandSender by) {
        p.reloadConfig();
        by.sendMessage(p.msg("reloaded"));
    }

    public static String durationLabel(String s) {
        if (s.equalsIgnoreCase("perm") || s.equals("0")) return "Permanen";
        try {
            long n = Long.parseLong(s.substring(0, s.length() - 1));
            return switch (Character.toLowerCase(s.charAt(s.length() - 1))) {
                case 'm' -> n + " menit";
                case 'h' -> n + " jam";
                case 'd' -> n + " hari";
                default -> s;
            };
        } catch (Exception e) { return s; }
    }

    public static long parseDuration(String s) {
        if (s.equalsIgnoreCase("perm") || s.equals("0")) return 0;
        try {
            long n = Long.parseLong(s.substring(0, s.length() - 1));
            if (n <= 0) return -1;
            return switch (Character.toLowerCase(s.charAt(s.length() - 1))) {
                case 'm' -> n * 60_000L;
                case 'h' -> n * 3_600_000L;
                case 'd' -> n * 86_400_000L;
                default -> -1;
            };
        } catch (Exception e) { return -1; }
    }
}
