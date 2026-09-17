package com.solr98.beyondintegration.command.network;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.solr98.beyondintegration.command.CommandLang;
import com.solr98.beyondintegration.command.util.*;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.component.CustomData;

import java.util.*;

public class NetworkToolsCommand {

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> registerGiveTerminal() {
        return Commands.literal("giveTerminal").requires(s -> CommandUtils.hasOpPermission(s))
                .executes(ctx -> executeGiveTerminal(ctx, -1, 1))
                .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> executeGiveTerminal(ctx, IntegerArgumentType.getInteger(ctx, "netId"), 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                .executes(ctx -> executeGiveTerminal(ctx, IntegerArgumentType.getInteger(ctx, "netId"), IntegerArgumentType.getInteger(ctx, "count")))));
    }

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> registerGiveEnchantedBooks() {
        var cmd = Commands.literal("giveEnchantedBooks").requires(s -> CommandUtils.hasOpPermission(s));
        cmd = cmd.then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> executeGiveEnchantedBooks(ctx, EntityArgument.getPlayer(ctx, "player"), 1, "random", 1, 3))
                .then(buildCountBranch()));
        return cmd;
    }

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> registerBatchCreate() {
        return Commands.literal("batchCreate").requires(s -> CommandUtils.hasOpPermission(s))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> executeBatchCreate(ctx, EntityArgument.getPlayer(ctx, "player"), 1, Long.MAX_VALUE, Integer.MAX_VALUE))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                                .executes(ctx -> executeBatchCreate(ctx, EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "count"), Long.MAX_VALUE, Integer.MAX_VALUE))
                                .then(Commands.argument("slotCapacity", LongArgumentType.longArg(1))
                                        .executes(ctx -> executeBatchCreate(ctx, EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "count"), LongArgumentType.getLong(ctx, "slotCapacity"), Integer.MAX_VALUE))
                                        .then(Commands.argument("slotMaxSize", IntegerArgumentType.integer(1))
                                                .executes(ctx -> executeBatchCreate(ctx, EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "count"), LongArgumentType.getLong(ctx, "slotCapacity"), IntegerArgumentType.getInteger(ctx, "slotMaxSize")))))));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> buildCountBranch() {
        var countArg = Commands.argument("count", IntegerArgumentType.integer(1))
                .executes(ctx -> executeGiveEnchantedBooks(ctx, EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "count"), "random", 1, 3));
        countArg = countArg.then(Commands.literal("random")
                .executes(ctx -> executeGiveEnchantedBooks(ctx, EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "count"), "random", 1, 3))
                .then(Commands.argument("minEnchants", IntegerArgumentType.integer(1))
                        .executes(ctx -> executeGiveEnchantedBooks(ctx, EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "count"), "random", IntegerArgumentType.getInteger(ctx, "minEnchants"), 3))
                        .then(Commands.argument("maxEnchants", IntegerArgumentType.integer(1))
                                .executes(ctx -> executeGiveEnchantedBooks(ctx, EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "count"), "random", IntegerArgumentType.getInteger(ctx, "minEnchants"), IntegerArgumentType.getInteger(ctx, "maxEnchants"))))));
        countArg = countArg.then(Commands.literal("all")
                .executes(ctx -> executeGiveEnchantedBooks(ctx, EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "count"), "all", 1, 1)));
        return countArg;
    }

    private static int executeGiveTerminal(CommandContext<CommandSourceStack> ctx, int netId, int count) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkServerAvailable(source)) return 0;
        ServerPlayer executor = source.getPlayer();
        if (executor == null) { source.sendFailure(OutputFormatter.createError("error.player_required")); return 0; }
        int actualNetId = netId;
        if (actualNetId == -1) {
            DimensionsNet tempNet = DimensionsNet.getNetFromPlayer(executor);
            if (tempNet == null) { source.sendFailure(OutputFormatter.createError("error.not_in_network")); return 0; }
            actualNetId = tempNet.getId();
        }
        DimensionsNet net = PermissionChecker.checkNetworkExists(source, actualNetId);
        if (net == null) return 0;
        var terminalItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse("beyond_dimensions:net_terminal"));
        if (terminalItem == null || terminalItem == Items.AIR) { source.sendFailure(OutputFormatter.createError("error.item_not_found")); return 0; }
        String ownerName = CommandUtils.getNetworkOwnerName(net, source.getServer());
        for (int i = 0; i < count; i++) {
            ItemStack terminalStack = new ItemStack(terminalItem);
            terminalStack.set(DataComponents.CUSTOM_NAME, Component.literal(CommandLang.get("network.giveTerminal.item_name", ownerName, actualNetId)));
            CompoundTag tag = new CompoundTag();
            tag.putString("description", CommandLang.get("network.giveTerminal.item_description", ownerName, actualNetId));
            tag.putInt("NetId", actualNetId);
            terminalStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            if (!executor.getInventory().add(terminalStack)) executor.drop(terminalStack, false);
        }
        int fni = actualNetId;
        source.sendSuccess(() -> Component.literal(CommandLang.get("network.giveTerminal.success", fni, ownerName, count)).withStyle(ChatFormatting.GREEN), false);
        return count;
    }

    private static int executeGiveEnchantedBooks(CommandContext<CommandSourceStack> ctx, ServerPlayer targetPlayer, int count, String enchantType, int minEnchants, int maxEnchants) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkServerAvailable(source)) return 0;
        var server = source.getServer();
        if (server == null) { source.sendFailure(OutputFormatter.createError("error.server_not_available")); return 0; }
        Random random = new Random();
        var enchLookup = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var allEnchantments = enchLookup.listElements().toList();
        if (allEnchantments.isEmpty()) { source.sendFailure(OutputFormatter.createError("error.enchantment_book_not_implemented")); return 0; }
        if (enchantType.equals("all")) {
            source.sendSuccess(() -> Component.literal(CommandLang.get("network.tools.giveEnchantedBooks.all_info", targetPlayer.getGameProfile().getName(), allEnchantments.size())).withStyle(ChatFormatting.YELLOW), false);
        }
        int givenCount = 0;
        for (int i = 0; i < count; i++) {
            ItemStack bookStack = new ItemStack(Items.ENCHANTED_BOOK);
            var mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            int enchCount;
            List<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>> toAdd;
            if (enchantType.equals("all")) {
                enchCount = allEnchantments.size();
                toAdd = new ArrayList<>(allEnchantments);
            } else {
                enchCount = minEnchants + random.nextInt(maxEnchants - minEnchants + 1);
                toAdd = new ArrayList<>();
                var shuffled = new ArrayList<>(allEnchantments);
                Collections.shuffle(shuffled, random);
                for (int j = 0; j < Math.min(enchCount, shuffled.size()); j++) toAdd.add(shuffled.get(j));
            }
            for (var holder : toAdd) {
                int level = Math.max(1, holder.value().getMaxLevel());
                mutable.set(holder, level);
            }
            bookStack.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
            if (!targetPlayer.getInventory().add(bookStack)) targetPlayer.drop(bookStack, false);
            givenCount++;
        }
        int fgc = givenCount;
        source.sendSuccess(() -> Component.literal(CommandLang.get("network.tools.giveEnchantedBooks.success", targetPlayer.getGameProfile().getName(), fgc)).withStyle(ChatFormatting.GREEN), false);
        return givenCount;
    }

    private static int executeBatchCreate(CommandContext<CommandSourceStack> ctx, ServerPlayer targetPlayer, int count, long slotCapacity, int slotMaxSize) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkServerAvailable(source)) return 0;
        var server = source.getServer();
        if (server == null) { source.sendFailure(OutputFormatter.createError("error.server_not_available")); return 0; }
        int createdCount = 0;
        List<String> failed = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            try {
                DimensionsNet net = DimensionsNet.createNewNetForPlayer(targetPlayer, slotCapacity, slotMaxSize);
                if (net == null) { failed.add(CommandLang.get("network.batchCreate.error.creation_failed", i + 1)); continue; }
                DimensionsNet.clearPrimaryNetForPlayer(targetPlayer);
                createdCount++;
            } catch (Exception e) { failed.add(CommandLang.get("network.batchCreate.error.general_failure", i + 1, e.getMessage())); }
        }
        StringBuilder result = new StringBuilder();
        if (createdCount > 0) result.append(CommandLang.get("network.batchCreate.success", createdCount)).append(" for ").append(targetPlayer.getGameProfile().getName()).append("\n");
        for (String f : failed) result.append(f).append("\n");
        if (createdCount == 0 && failed.isEmpty()) result.append(CommandLang.get("network.batchCreate.error.no_networks_created")).append("\n");
        source.sendSuccess(() -> Component.literal(result.toString()), false);
        return createdCount;
    }
}

