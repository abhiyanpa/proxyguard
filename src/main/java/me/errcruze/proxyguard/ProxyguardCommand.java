package me.errcruze.proxyguard;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.ChatColor;
import java.util.Set;
import java.util.stream.Collectors;

public class ProxyguardCommand extends Command {
    private final Proxyguard plugin;

    public ProxyguardCommand(Proxyguard plugin) {
        super("proxyguard", "proxyguard.admin");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        String action = args[0].toLowerCase();

        switch (action) {
            case "add":
                if (args.length < 2) {
                    sender.sendMessage(new TextComponent("[INFO] Usage: proxyguard add <username>"));
                    return;
                }
                String addUsername = args[1].toLowerCase();
                if (plugin.isPremium(addUsername)) {
                    sender.sendMessage(new TextComponent("[INFO] " + addUsername + " is already in premium list!"));
                } else {
                    plugin.addPremiumPlayer(addUsername);
                    sender.sendMessage(new TextComponent("[INFO] Added " + addUsername + " to premium list"));
                }
                break;

            case "remove":
                if (args.length < 2) {
                    sender.sendMessage(new TextComponent("[INFO] Usage: proxyguard remove <username>"));
                    return;
                }
                String removeUsername = args[1].toLowerCase();
                if (!plugin.isPremium(removeUsername)) {
                    sender.sendMessage(new TextComponent("[INFO] There is no player named " + removeUsername + " in premium list!"));
                } else {
                    plugin.removePremiumPlayer(removeUsername);
                    sender.sendMessage(new TextComponent("[INFO] Removed " + removeUsername + " from premium list"));
                }
                break;

            case "check":
                if (args.length < 2) {
                    sender.sendMessage(new TextComponent("[INFO] Usage: proxyguard check <username>"));
                    return;
                }
                String checkUsername = args[1].toLowerCase();
                boolean isPremium = plugin.isPremium(checkUsername);
                sender.sendMessage(new TextComponent("[INFO] " + checkUsername + " is " +
                        (isPremium ? "a premium" : "not a premium") + " player"));
                break;

            case "list":
                Set<String> players = plugin.getPremiumPlayers();
                if (players.isEmpty()) {
                    sender.sendMessage(new TextComponent("[INFO] No premium players in the list"));
                } else {
                    String playerList = players.stream()
                            .collect(Collectors.joining(", "));
                    sender.sendMessage(new TextComponent("[INFO] Premium players: " + playerList));
                }
                break;

            default:
                sendHelp(sender);
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(new TextComponent("[INFO] Proxyguard Commands:"));
        sender.sendMessage(new TextComponent("[INFO] /proxyguard add <username> - Add a premium player"));
        sender.sendMessage(new TextComponent("[INFO] /proxyguard remove <username> - Remove a premium player"));
        sender.sendMessage(new TextComponent("[INFO] /proxyguard check <username> - Check if a player is premium"));
        sender.sendMessage(new TextComponent("[INFO] /proxyguard list - List all premium players"));
    }
}