package dev.littlegame.battleroyale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LittleGamePlugin extends JavaPlugin implements Listener, TabExecutor {
    private static final int WAIT_SECONDS = 5 * 60;
    private static final int DEVELOP_SECONDS = 45 * 60;
    private static final int FIGHT_SECONDS = 30 * 60;
    private static final double LOBBY_BORDER = 10.0;
    private static final double GAME_BORDER = 1000.0;
    private static final String ADMIN_PERMISSION = "littlegame.admin";

    private final Map<String, BattleTeam> teams = new HashMap<>();
    private final Map<UUID, String> playerTeams = new HashMap<>();
    private final Map<UUID, String> invites = new ConcurrentHashMap<>();
    private int maxTeamSize = 4;
    private GamePhase phase = GamePhase.LOBBY;
    private BossBar bossBar;
    private BukkitRunnable timerTask;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("start").setExecutor(this);
        getCommand("start").setTabCompleter(this);
        getCommand("teams").setExecutor(this);
        getCommand("teams").setTabCompleter(this);
        setupLobbyBorder();
        getLogger().info("LittleGame battle royale enabled.");
    }

    @Override
    public void onDisable() {
        if (timerTask != null) timerTask.cancel();
        if (bossBar != null) bossBar.removeAll();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("start")) {
            if (!sender.hasPermission(ADMIN_PERMISSION)) return deny(sender);
            startGame();
            return true;
        }
        if (args.length == 0) {
            sendTeamsHelp(sender);
            return true;
        }
        return handleTeams(sender, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("teams")) return Collections.emptyList();
        if (args.length == 1) return List.of("create", "invite", "accept", "leave", "info", "setlimit");
        if (args.length == 2 && args[0].equalsIgnoreCase("invite")) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return Collections.emptyList();
    }

    private void startGame() {
        if (phase != GamePhase.LOBBY) {
            broadcast(Component.text("Игра уже запущена!", NamedTextColor.RED));
            return;
        }
        startPhase(GamePhase.WAITING, WAIT_SECONDS, BarColor.BLUE, "⏳ Сбор игроков");
    }

    private void startPhase(GamePhase nextPhase, int seconds, BarColor color, String title) {
        phase = nextPhase;
        if (timerTask != null) timerTask.cancel();
        if (bossBar != null) bossBar.removeAll();
        bossBar = Bukkit.createBossBar("", color, BarStyle.SEGMENTED_20);
        Bukkit.getOnlinePlayers().forEach(bossBar::addPlayer);
        broadcast(Component.text("✦ " + title + " начался!", NamedTextColor.GOLD, TextDecoration.BOLD));
        timerTask = new BukkitRunnable() {
            int left = seconds;
            @Override public void run() {
                if (left <= 0) { cancel(); completePhase(); return; }
                bossBar.setTitle(title + " §7| §f" + format(left));
                bossBar.setProgress(Math.max(0.0, Math.min(1.0, left / (double) seconds)));
                left--;
            }
        };
        timerTask.runTaskTimer(this, 0L, 20L);
    }

    private void completePhase() {
        if (phase == GamePhase.WAITING) {
            setBorder(GAME_BORDER, 0);
            Bukkit.getOnlinePlayers().forEach(p -> p.getInventory().addItem(new ItemStack(Material.CARROT, 20)));
            broadcast(Component.text("🥕 Всем выдано по 20 морковок. PvP выключен на развитие!", NamedTextColor.GREEN));
            startPhase(GamePhase.DEVELOPMENT, DEVELOP_SECONDS, BarColor.GREEN, "🌿 Этап развития — PvP запрещен");
        } else if (phase == GamePhase.DEVELOPMENT) {
            setBorder(LOBBY_BORDER, FIGHT_SECONDS);
            startPhase(GamePhase.FIGHTING, FIGHT_SECONDS, BarColor.RED, "⚔ Этап сражений — граница сжимается");
        } else if (phase == GamePhase.FIGHTING) {
            phase = GamePhase.DEATHMATCH;
            if (bossBar != null) {
                bossBar.setColor(BarColor.PURPLE);
                bossBar.setStyle(BarStyle.SOLID);
                bossBar.setProgress(1.0);
                bossBar.setTitle("☠ СМЕРТЕЛЬНЫЙ БОЙ — выживет сильнейший!");
            }
            broadcast(Component.text("☠ Смертельный бой начался! Времени больше нет.", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
        }
    }

    private void setupLobbyBorder() { setBorder(LOBBY_BORDER, 0); }
    private void setBorder(double size, int seconds) {
        for (World world : Bukkit.getWorlds()) {
            WorldBorder border = world.getWorldBorder();
            border.setCenter(world.getSpawnLocation());
            border.setSize(size, Duration.ofSeconds(seconds));
            world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        }
    }

    private boolean handleTeams(CommandSender sender, String[] args) {
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("setlimit")) {
            if (!sender.hasPermission(ADMIN_PERMISSION)) return deny(sender);
            if (args.length < 2) return usage(sender, "/teams setlimit <количество>");
            maxTeamSize = Math.max(1, Integer.parseInt(args[1]));
            sender.sendMessage("§d§lLittleGame §8» §fЛимит команды: §d" + maxTeamSize);
            return true;
        }
        if (!(sender instanceof Player player)) { sender.sendMessage("Только игрок."); return true; }
        switch (sub) {
            case "create" -> { if (args.length < 2) return usage(sender, "/teams create <название>"); createTeam(player, args[1]); }
            case "invite" -> { if (args.length < 2) return usage(sender, "/teams invite <ник>"); invite(player, args[1]); }
            case "accept" -> accept(player);
            case "leave" -> leave(player);
            case "info" -> info(player);
            default -> sendTeamsHelp(sender);
        }
        return true;
    }

    private void createTeam(Player player, String rawName) {
        String name = rawName.replaceAll("[^A-Za-zА-Яа-я0-9_\\-]", "");
        if (name.isBlank() || name.length() > 12) { player.sendMessage("§cНазвание: 1-12 символов."); return; }
        if (playerTeams.containsKey(player.getUniqueId())) { player.sendMessage("§cВы уже в команде."); return; }
        String key = name.toLowerCase(Locale.ROOT);
        if (teams.containsKey(key)) { player.sendMessage("§cТакая команда уже есть."); return; }
        BattleTeam team = new BattleTeam(name, player.getUniqueId());
        team.members.add(player.getUniqueId()); teams.put(key, team); playerTeams.put(player.getUniqueId(), key);
        updateVisualTeam(team); player.sendMessage("§d§lКоманда §8» §fСоздана команда §d" + name);
    }

    private void invite(Player owner, String targetName) {
        BattleTeam team = getTeam(owner).orElse(null);
        if (team == null || !team.owner.equals(owner.getUniqueId())) { owner.sendMessage("§cПриглашать может только создатель команды."); return; }
        if (team.members.size() >= maxTeamSize) { owner.sendMessage("§cКоманда заполнена."); return; }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) { owner.sendMessage("§cИгрок не найден."); return; }
        invites.put(target.getUniqueId(), team.key());
        target.sendMessage("§d§lПриглашение §8» §fКоманда §d" + team.name + "§f приглашает вас. §7/teams accept");
        owner.sendMessage("§aПриглашение отправлено игроку " + target.getName());
    }

    private void accept(Player player) {
        String key = invites.remove(player.getUniqueId());
        if (key == null || !teams.containsKey(key)) { player.sendMessage("§cУ вас нет приглашений."); return; }
        if (playerTeams.containsKey(player.getUniqueId())) { player.sendMessage("§cСначала выйдите из текущей команды."); return; }
        BattleTeam team = teams.get(key);
        if (team.members.size() >= maxTeamSize) { player.sendMessage("§cКоманда уже заполнена."); return; }
        team.members.add(player.getUniqueId()); playerTeams.put(player.getUniqueId(), key); updateVisualTeam(team);
        broadcast(Component.text("✦ " + player.getName() + " вступил в команду " + team.name, NamedTextColor.LIGHT_PURPLE));
    }

    private void leave(Player player) {
        BattleTeam team = getTeam(player).orElse(null);
        if (team == null) { player.sendMessage("§cВы не в команде."); return; }
        team.members.remove(player.getUniqueId()); playerTeams.remove(player.getUniqueId()); resetVisual(player);
        if (team.members.isEmpty() || team.owner.equals(player.getUniqueId())) { disband(team); }
        else player.sendMessage("§fВы вышли из команды §d" + team.name);
    }

    private void disband(BattleTeam team) {
        team.members.forEach(id -> { playerTeams.remove(id); Player p = Bukkit.getPlayer(id); if (p != null) resetVisual(p); });
        teams.remove(team.key());
        Team scoreboardTeam = board().getTeam(team.scoreboardName()); if (scoreboardTeam != null) scoreboardTeam.unregister();
    }

    private void info(Player player) {
        getTeam(player).ifPresentOrElse(t -> player.sendMessage("§d§l" + t.name + " §8» §f" + t.members.size() + "/" + maxTeamSize + " игроков"), () -> player.sendMessage("§cВы не в команде."));
    }

    private Optional<BattleTeam> getTeam(Player player) { return Optional.ofNullable(playerTeams.get(player.getUniqueId())).map(teams::get); }
    private void updateVisualTeam(BattleTeam battleTeam) {
        Team team = board().getTeam(battleTeam.scoreboardName());
        if (team == null) team = board().registerNewTeam(battleTeam.scoreboardName());
        team.prefix(Component.text("[" + battleTeam.name + "] ", NamedTextColor.LIGHT_PURPLE));
        team.setAllowFriendlyFire(false);
        for (UUID id : battleTeam.members) { Player p = Bukkit.getPlayer(id); if (p != null) team.addEntry(p.getName()); }
    }
    private void resetVisual(Player player) { for (Team team : board().getTeams()) team.removeEntry(player.getName()); }
    private Scoreboard board() { return Bukkit.getScoreboardManager().getMainScoreboard(); }

    @EventHandler public void onJoin(PlayerJoinEvent event) { if (bossBar != null) bossBar.addPlayer(event.getPlayer()); getTeam(event.getPlayer()).ifPresent(this::updateVisualTeam); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { invites.remove(event.getPlayer().getUniqueId()); }
    @EventHandler public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker)) return;
        if (phase == GamePhase.DEVELOPMENT || sameTeam(victim, attacker)) event.setCancelled(true);
    }
    @EventHandler public void onChat(AsyncPlayerChatEvent event) {
        String prefix = getTeam(event.getPlayer()).map(t -> "§d[" + t.name + "] ").orElse("");
        event.setFormat(prefix + "§f%1$s §8» §7%2$s");
    }
    @EventHandler public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Player killer = player.getKiller();
        String text = killer == null ? "☠ " + player.getName() + " погиб: " + event.getDeathMessage() : "⚔ " + killer.getName() + " уничтожил " + player.getName();
        event.deathMessage(Component.text(text, killer == null ? NamedTextColor.GRAY : NamedTextColor.RED));
    }
    private boolean sameTeam(Player a, Player b) { return playerTeams.getOrDefault(a.getUniqueId(), "a").equals(playerTeams.getOrDefault(b.getUniqueId(), "b")); }
    private void sendTeamsHelp(CommandSender s) { s.sendMessage("§d§lКоманды LittleGame\n§f/teams create <название> §7— создать\n§f/teams invite <ник> §7— пригласить\n§f/teams accept §7— принять\n§f/teams leave §7— выйти\n§f/teams info §7— информация\n§f/teams setlimit <число> §7— лимит, админ"); }
    private boolean usage(CommandSender s, String msg) { s.sendMessage("§cИспользование: " + msg); return true; }
    private boolean deny(CommandSender s) { s.sendMessage("§cНет прав."); return true; }
    private void broadcast(Component c) { Bukkit.broadcast(c); }
    private String format(int seconds) { return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60); }

    private enum GamePhase { LOBBY, WAITING, DEVELOPMENT, FIGHTING, DEATHMATCH }
    private record BattleTeam(String name, UUID owner, Set<UUID> members) {
        BattleTeam(String name, UUID owner) { this(name, owner, ConcurrentHashMap.newKeySet()); }
        String key() { return name.toLowerCase(Locale.ROOT); }
        String scoreboardName() { return ("lg_" + key()).substring(0, Math.min(16, ("lg_" + key()).length())); }
    }
}
