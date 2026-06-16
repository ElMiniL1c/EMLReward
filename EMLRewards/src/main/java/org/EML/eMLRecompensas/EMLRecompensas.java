package org.EML.eMLRecompensas;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class EMLRecompensas extends JavaPlugin implements CommandExecutor, TabCompleter {

    private static final String DEFAULT_ADMIN_PERMISSION = "emlrew.admin";
    private static final String DEFAULT_PERMISSION_PREFIX = "emlrew";

    private File dataFile;
    private FileConfiguration data;
    private FileConfiguration language;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("lang/en.yml", false);
        saveResource("lang/es.yml", false);
        loadData();
        loadLanguage();

        if (getCommand("emlrew") != null) {
            getCommand("emlrew").setExecutor(this);
            getCommand("emlrew").setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        saveData();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return handleConsoleCommand(sender, args);
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            sendRewardInfo(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission(adminPermission())) {
                player.sendMessage(msg("no-permission"));
                return true;
            }

            reloadConfig();
            loadData();
            loadLanguage();
            player.sendMessage(msg("reload"));
            return true;
        }

        if (args[0].equalsIgnoreCase("reset")) {
            if (!player.hasPermission(adminPermission())) {
                player.sendMessage(msg("no-permission"));
                return true;
            }

            resetReward(player, args);
            return true;
        }

        if (!args[0].equalsIgnoreCase("take") || args.length < 2) {
            player.sendMessage(msg("usage"));
            return true;
        }

        String rewardId = findRewardId(args[1]);
        if (rewardId == null) {
            player.sendMessage(msg("unknown-reward"));
            return true;
        }

        claimReward(player, rewardId);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("info");
            completions.add("take");
            if (sender.hasPermission(adminPermission())) {
                completions.add("reload");
                completions.add("reset");
            }
            return filter(completions, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("take")) {
            completions.addAll(commandRewardNames());
            return filter(completions, args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("reset") && sender.hasPermission(adminPermission())) {
            completions.add("all");
            completions.addAll(commandRewardNames());
            return filter(completions, args[2]);
        }

        return completions;
    }

    private boolean handleConsoleCommand(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            loadData();
            loadLanguage();
            sender.sendMessage(msg("reload"));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reset")) {
            resetReward(sender, args);
            return true;
        }

        sender.sendMessage(msg("only-players"));
        return true;
    }

    private void claimReward(Player player, String rewardId) {
        String rewardPath = "rewards." + rewardId + ".";

        if (!getConfig().getBoolean(rewardPath + "enabled", true)) {
            player.sendMessage(msg("reward-disabled"));
            return;
        }

        String permission = getConfig().getString(rewardPath + "permission", "");
        if (!isValidConfiguredPermission(permission)) {
            getLogger().warning("Reward '" + rewardId + "' has an invalid permission. Custom permissions must start with '" + permissionPrefix() + "'.");
            player.sendMessage(msg("no-permission"));
            return;
        }

        if (!permission.isBlank() && !player.hasPermission(permission)) {
            player.sendMessage(msg("no-permission"));
            return;
        }

        if (!meetsRequirements(player, rewardId)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        String basePath = "players." + uuid + ".";

        long now = Instant.now().toEpochMilli();
        boolean once = getConfig().getBoolean(rewardPath + "claim.once", false);
        long cooldownMillis = getConfig().getLong(rewardPath + "claim.cooldown-hours", 0L) * 60L * 60L * 1000L;
        long lastClaim = data.getLong(basePath + "last-claim." + rewardId, 0L);
        long nextClaim = lastClaim + cooldownMillis;

        if (once && data.getBoolean(basePath + "claimed." + rewardId, false)) {
            player.sendMessage(msg("already-claimed-once"));
            return;
        }

        if (lastClaim > 0L && now < nextClaim) {
            player.sendMessage(msg("cooldown", "%time%", formatDurationMillis(nextClaim - now)));
            return;
        }

        giveReward(player, rewardId);
        data.set(basePath + "name", player.getName());
        data.set(basePath + "last-claim." + rewardId, now);
        if (once) {
            data.set(basePath + "claimed." + rewardId, true);
        }
        saveData();
        player.sendMessage(msg("claimed", "%reward%", displayName(rewardId)));
    }

    private void giveReward(Player player, String rewardId) {
        List<String> commands = getConfig().getStringList("rewards." + rewardId + ".commands");
        for (String rewardCommand : commands) {
            String parsedCommand = rewardCommand
                .replace("%player%", player.getName())
                .replace("%uuid%", player.getUniqueId().toString())
                .replace("%reward%", rewardId)
                .replace("%amount%", String.valueOf(getConfig().getInt("rewards." + rewardId + ".amount", 0)));

            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCommand);
        }
    }

    private boolean meetsRequirements(Player player, String rewardId) {
        String requirementsPath = "rewards." + rewardId + ".requirements.";

        String requiredPermission = getConfig().getString(requirementsPath + "permission", "");
        if (!isValidConfiguredPermission(requiredPermission)) {
            getLogger().warning("Reward '" + rewardId + "' has an invalid requirement permission. Custom permissions must start with '" + permissionPrefix() + "'.");
            player.sendMessage(msg("no-permission"));
            return false;
        }

        if (!requiredPermission.isBlank() && !player.hasPermission(requiredPermission)) {
            player.sendMessage(msg("requirement-permission"));
            return false;
        }

        String requiredWorld = getConfig().getString(requirementsPath + "world", "");
        if (!requiredWorld.isBlank() && !player.getWorld().getName().equalsIgnoreCase(requiredWorld)) {
            player.sendMessage(msg("requirement-world", "%world%", requiredWorld));
            return false;
        }

        int minimumLevel = getConfig().getInt(requirementsPath + "minimum-level", 0);
        if (minimumLevel > 0 && player.getLevel() < minimumLevel) {
            player.sendMessage(msg("requirement-minimum-level", "%level%", String.valueOf(minimumLevel)));
            return false;
        }

        int maximumLevel = getConfig().getInt(requirementsPath + "maximum-level", -1);
        if (maximumLevel >= 0 && player.getLevel() > maximumLevel) {
            player.sendMessage(msg("requirement-maximum-level", "%level%", String.valueOf(maximumLevel)));
            return false;
        }

        long requiredPlaytimeHours = getConfig().getLong(requirementsPath + "playtime-hours", 0L);
        if (requiredPlaytimeHours > 0L) {
            long requiredTicks = requiredPlaytimeHours * 60L * 60L * 20L;
            long playedTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
            if (playedTicks < requiredTicks) {
                player.sendMessage(msg("playtime-missing", "%time%", formatDurationTicks(requiredTicks - playedTicks)));
                return false;
            }
        }

        return true;
    }

    private void sendRewardInfo(Player player) {
        player.sendMessage(msg("header"));
        player.sendMessage(msg("info-command"));
        for (String rewardId : rewardIds()) {
            if (!getConfig().getBoolean("rewards." + rewardId + ".enabled", true)) {
                continue;
            }

            String permission = getConfig().getString("rewards." + rewardId + ".permission", "");
            if (!isValidConfiguredPermission(permission) || !permission.isBlank() && !player.hasPermission(permission)) {
                continue;
            }

            player.sendMessage(msg("info-line",
                "%key%", commandName(rewardId),
                "%reward%", displayName(rewardId),
                "%amount%", String.valueOf(getConfig().getInt("rewards." + rewardId + ".amount", 0))));
        }
    }

    private void resetReward(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(msg("reset-usage"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(msg("player-not-found", "%player%", args[1]));
            return;
        }

        String rewardInput = args[2];
        UUID uuid = target.getUniqueId();
        String basePath = "players." + uuid + ".";

        if (rewardInput.equalsIgnoreCase("all")) {
            data.set(basePath + "claimed", null);
            data.set(basePath + "last-claim", null);
            saveData();
            sender.sendMessage(msg("reset-all", "%player%", target.getName() == null ? args[1] : target.getName()));
            return;
        }

        String rewardId = findRewardId(rewardInput);
        if (rewardId == null) {
            sender.sendMessage(msg("unknown-reward"));
            return;
        }

        data.set(basePath + "claimed." + rewardId, null);
        data.set(basePath + "last-claim." + rewardId, null);
        saveData();
        sender.sendMessage(msg("reset-reward",
            "%player%", target.getName() == null ? args[1] : target.getName(),
            "%reward%", displayName(rewardId)));
    }

    private List<String> rewardIds() {
        ConfigurationSection rewards = getConfig().getConfigurationSection("rewards");
        if (rewards == null) {
            return List.of();
        }

        return new ArrayList<>(rewards.getKeys(false));
    }

    private List<String> commandRewardNames() {
        List<String> names = new ArrayList<>();
        for (String rewardId : rewardIds()) {
            names.add(commandName(rewardId));
        }

        return names;
    }

    private String findRewardId(String input) {
        for (String rewardId : rewardIds()) {
            if (rewardId.equalsIgnoreCase(input) || commandName(rewardId).equalsIgnoreCase(input)) {
                return rewardId;
            }
        }

        return null;
    }

    private String displayName(String rewardId) {
        return getConfig().getString("rewards." + rewardId + ".name", rewardId);
    }

    private String commandName(String rewardId) {
        return getConfig().getString("rewards." + rewardId + ".command-name", rewardId);
    }

    private boolean isValidConfiguredPermission(String permission) {
        return permission == null || permission.isBlank() || permission.toLowerCase(Locale.ROOT).startsWith(permissionPrefix());
    }

    private String adminPermission() {
        return getConfig().getString("permissions.admin", DEFAULT_ADMIN_PERMISSION);
    }

    private String permissionPrefix() {
        return getConfig().getString("permissions.custom-prefix", DEFAULT_PERMISSION_PREFIX).toLowerCase(Locale.ROOT);
    }

    private void loadData() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException exception) {
                getLogger().severe("Could not create data.yml: " + exception.getMessage());
            }
        }

        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void loadLanguage() {
        String languageCode = getConfig().getString("settings.language", "en").toLowerCase(Locale.ROOT);
        File languageFile = new File(getDataFolder(), "lang/" + languageCode + ".yml");

        if (!languageFile.exists()) {
            String fallbackLanguage = getConfig().getString("settings.fallback-language", "en").toLowerCase(Locale.ROOT);
            getLogger().warning("Language file lang/" + languageCode + ".yml was not found. Using " + fallbackLanguage + ".yml instead.");
            languageFile = new File(getDataFolder(), "lang/" + fallbackLanguage + ".yml");
        }

        language = YamlConfiguration.loadConfiguration(languageFile);
    }

    private void saveData() {
        if (data == null || dataFile == null) {
            return;
        }

        try {
            data.save(dataFile);
        } catch (IOException exception) {
            getLogger().severe("Could not save data.yml: " + exception.getMessage());
        }
    }

    private String formatDurationMillis(long millis) {
        Duration duration = Duration.ofMillis(Math.max(0L, millis));
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();

        if (days > 0L) {
            return days + "d " + hours + "h";
        }

        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }

        return Math.max(1L, minutes) + "m";
    }

    private String formatDurationTicks(long ticks) {
        return formatDurationMillis((ticks / 20L) * 1000L);
    }

    private List<String> filter(List<String> values, String input) {
        String lowerInput = input.toLowerCase(Locale.ROOT);
        return values.stream()
            .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowerInput))
            .toList();
    }

    private String color(String message) {
        return message == null ? "" : ChatColor.translateAlternateColorCodes('&', message);
    }

    private String msg(String path, String... replacements) {
        String message = language == null ? path : language.getString(path, path);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }

        return color(message);
    }
}
