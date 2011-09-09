package net.krinsoft.ktriggers.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.krinsoft.ktriggers.TriggerPlugin;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.util.config.ConfigurationNode;

/**
 *
 * @author krinsdeath
 */
public class KTCommandHandler {

    private TriggerPlugin plugin;

    public KTCommandHandler(TriggerPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean executeCommand(CommandSender sender, List<String> command) {
        // make sure we have this command mapped
        if (plugin.getCommandNode(command.get(0)) == null) { return false; }
        boolean cancel = false, override = false;
        String type = plugin.getCommandNode(command.get(0)).getString("type");
        if (type != null) {
            // check the command type
            override = type.equalsIgnoreCase("override");
            cancel = type.equalsIgnoreCase("cancel");
        }
        // this command will only get executed if:
        // sender has permission
        // command overrides default functionality
        // command cancels default functionality
        if (hasPermission(sender, command.get(0)) || override || cancel) {
            if (hasPermission(sender, command.get(0)) && cancel) {
                // this user has permission to use this command's normal functionality
                return false;
            }
            // fetch the sender's name
            // will deprecate in CB 1091+ due to 'CommandSender.getName()'
            String t = getName(sender);
            // build the parameter replacement string
            StringBuilder paramString = null;
            if (command.size() > 1) {
                paramString = new StringBuilder(command.get(1));
                for (int i = 2; i < command.size(); i++) {
                    paramString.append(" ").append(command.get(i));
                }
            }
            // get the configuration node for this command
            ConfigurationNode node = plugin.getCommandNode(command.get(0));
            // check that the player hasn't already run his runOnce setting
            if (node.getBoolean("runOnce", false) && node.getStringList("runOnceList", null).contains(t)) {
                return true;
            }
            List<String> execution = node.getStringList("execute", null);
            List<String> message = node.getStringList("message", null);
            String person = node.getString("executeAs", "<<triggerer>>").replaceAll("<<([^>]+)>>", "$1");
            // make sure the command has execute lines
            // make sure the triggerer exists
            if (!execution.isEmpty() && person != null) {
                CommandSender executor = sender;
                if (person.equalsIgnoreCase("console")) {
                    // execute the command as the Console
                    executor = new ConsoleCommandSender(plugin.getServer());
                }
                // iterate through the command executions
                for (String line : execution) {
                    // replace relevant information on each line
                    line = line.replaceAll("(?i)&([0-F])", "\u00A7$1");
                    line = line.replaceAll("<<triggerer>>", t);
                    if (paramString != null) {
                        line = line.replaceAll("<<params>>", paramString.toString());
                    }
                    if (executor instanceof Player) {
                        ((Player)executor).chat(line);
                    } else {
                        if (line.startsWith("/")) {
                            line = line.substring(1);
                        }
                        plugin.getServer().getPluginManager().callEvent(new ServerCommandEvent((ConsoleCommandSender)executor, line));
                    }
                    plugin.debug(line);
                }
            }
            // make sure message isn't empty
            if (!message.isEmpty()) {
                String target = node.getString("target");
                // build a list of targets to send this message to
                List<CommandSender> targets = buildTargetList(sender, target);
                for (CommandSender dest : targets) {
                    String name = getName(dest);
                    for (String line : message) {
                        line = line.replaceAll("(?i)&([0-F])", "\u00A7$1");
                        line = line.replaceAll("<<triggerer>>", t);
                        line = line.replaceAll("<<recipient>>", name);
                        dest.sendMessage(line);
                    }
                }
            }
            // fill this sender's runOnce requirements
            if (node.getBoolean("runOnce", false)) {
                List<String> runners = node.getStringList("runOnceList", null);
                runners.add(t);
                node.setProperty("runOnceList", runners);
                plugin.getConfiguration().save();
            }
            return true;
        }
        return false;
    }

    public String getName(CommandSender sender) {
        String name = null;
        if (sender instanceof Player) {
            name = ((Player) sender).getName();
        } else {
            name = "Console";
        }
        return name;
    }

    public List<CommandSender> buildTargetList(CommandSender sender, String target) {
        List<CommandSender> targets = new ArrayList<CommandSender>();
        if (target != null) {
            if (target.equalsIgnoreCase("<<everyone>>")) {
                targets.addAll(Arrays.asList(plugin.getServer().getOnlinePlayers()));
            } else if (target.equalsIgnoreCase("<<triggerer>")) {
                targets.addAll(Arrays.asList(sender));
            } else if (target.startsWith("<<groups")) {
                try {
                    List<String> groups = new ArrayList<String>(Arrays.asList(target.replaceAll("<<groups:([^>]+)>>", "$1").split(",")));
                    for (Player p : plugin.getServer().getOnlinePlayers()) {
                        if (hasAnyGroup(p, groups)) {
                            targets.add(p);
                        }
                    }
                } catch (Exception e) {
                }
            }
        } else {
            targets.addAll(Arrays.asList(sender));
        }
        return targets;
    }

    private boolean hasPermission(CommandSender sender, String node) {
        String perm = "ktrigger.command." + node;
        boolean has = sender.hasPermission(perm);
        boolean set = sender.isPermissionSet(perm);
        if (has) {
            return true;
        } else if (set && !has) {
            return false;
        } else if (!set && sender.hasPermission("ktrigger.command.*")) {
            return true;
        } else {
            return false;
        }
    }

    public boolean hasGroupPermission(CommandSender sender, String node) {
        return sender.hasPermission("group." + node);
    }

    public boolean hasAnyGroup(CommandSender sender, List<String> nodes) {
        for (String node : nodes) {
            if (hasGroupPermission(sender, node)) {
                return true;
            }
        }
        return false;
    }
}
