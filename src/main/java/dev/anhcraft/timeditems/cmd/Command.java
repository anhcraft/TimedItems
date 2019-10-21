package dev.anhcraft.timeditems.cmd;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import dev.anhcraft.confighelper.utils.EnumUtil;
import dev.anhcraft.craftkit.chat.Chat;
import dev.anhcraft.craftkit.utils.ItemUtil;
import dev.anhcraft.timeditems.TimedItems;
import dev.anhcraft.timeditems.api.TimedAPI;
import dev.anhcraft.timeditems.util.TimeUnit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.stream.Collectors;

@CommandAlias("ti|timeditems")
public class Command extends BaseCommand {
    private TimedItems plugin;

    public Command(TimedItems plugin) {
        this.plugin = plugin;
    }

    @CatchUnknown
    @Default
    public void help(CommandSender sender){
        Chat.noPrefix().message(sender, "&a/ti set <unit> <duration>:&f add expiry date to an item")
                .message(sender, "&a/ti del:&f remove the expiry date")
                .message(sender, "&a/ti unit:&f view all supported time unit")
                .message(sender, "&a/ti reload:&f reload the configuration");
    }

    @Subcommand("set")
    @CommandPermission("timeditems.cmd.set")
    @CommandCompletion("@unit")
    public void set(Player player, String unit, int duration){
        ItemStack item = player.getInventory().getItemInMainHand();
        if (ItemUtil.isNull(item)) {
            plugin.chat.message(player, "&cPlease hold an item on your hand!");
            return;
        }
        TimeUnit u = (TimeUnit) EnumUtil.findEnum(TimeUnit.class, unit);
        if (u != null) {
            player.getInventory().setItemInMainHand(plugin.api.setTimed(item, u, duration));
        } else {
            plugin.chat.message(player, "&cTime unit not found. &fUse /ti unit for all supported units.");
        }
    }

    @Subcommand("del")
    @CommandPermission("timeditems.cmd.del")
    public void del(Player player){
        ItemStack item = player.getInventory().getItemInMainHand();
        if (ItemUtil.isNull(item)) {
            plugin.chat.message(player, "&cPlease hold an item on your hand!");
            return;
        }
        player.getInventory().setItemInMainHand(plugin.api.removeTimed(item));
    }

    @Subcommand("unit")
    @CommandPermission("timeditems.cmd.unit")
    public void unit(CommandSender sender){
        Chat.noPrefix().message(sender, Arrays.stream(TimeUnit.values())
                .map(e -> e.name().toLowerCase())
                .collect(Collectors.joining(" ")));
    }

    @Subcommand("reload")
    @CommandPermission("timeditems.cmd.reload")
    public void reload(CommandSender sender){
        plugin.reload();
        plugin.chat.message(sender, "&aConfiguration has been reloaded!");
    }
}
