package com.solr98.beyondintegration.command.network;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.solr98.beyondintegration.command.CommandLang;
import com.solr98.beyondintegration.command.util.*;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public class NetworkInsertCommand {

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> register(CommandBuildContext context) {
        return Commands.literal("insert")
                .then(buildItemInsertCommand(context))
                .then(buildTagInsertCommand(context))
                .then(buildFluidInsertCommand(context))
                .then(buildEnergyInsertCommand());
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildItemInsertCommand(CommandBuildContext context) {
        return Commands.literal("item")
                .executes(ctx -> { ctx.getSource().sendFailure(CommandLang.component("error.item_required")); return 0; })
                .then(Commands.argument("item", ItemArgument.item(context))
                        .executes(ctx -> executeInsertItemDefault(ctx, ItemArgument.getItem(ctx, "item").createItemStack(1, false), 1))
                        .then(Commands.argument("count", LongArgumentType.longArg(1, Long.MAX_VALUE))
                                .executes(ctx -> executeInsertItemDefault(ctx, ItemArgument.getItem(ctx, "item").createItemStack(1, false), LongArgumentType.getLong(ctx, "count")))
                                .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                                        .executes(ctx -> executeInsertItem(ctx, IntegerArgumentType.getInteger(ctx, "netId"), ItemArgument.getItem(ctx, "item").createItemStack(1, false), LongArgumentType.getLong(ctx, "count"))))))
                .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> { ctx.getSource().sendFailure(CommandLang.component("error.item_required")); return 0; })
                        .then(Commands.argument("item", ItemArgument.item(context))
                                .executes(ctx -> executeInsertItem(ctx, IntegerArgumentType.getInteger(ctx, "netId"), ItemArgument.getItem(ctx, "item").createItemStack(1, false), 1))
                                .then(Commands.argument("count", LongArgumentType.longArg(1, Long.MAX_VALUE))
                                        .executes(ctx -> executeInsertItem(ctx, IntegerArgumentType.getInteger(ctx, "netId"), ItemArgument.getItem(ctx, "item").createItemStack(1, false), LongArgumentType.getLong(ctx, "count"))))));
    }

    /**
     * 物品标签批量插入命令
     * 用法：insert tag <#标签> [每种数量] [netId] 或 insert tag <netId> <#标签> [每种数量]
     * 标签内每种物品各插入 count 个，空间不足的种类自动跳过，结果以列表逐种输出
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildTagInsertCommand(CommandBuildContext context) {
        return Commands.literal("tag")
                .executes(ctx -> { ctx.getSource().sendFailure(CommandLang.component("error.tag_required")); return 0; })
                .then(Commands.argument("tag", ResourceOrTagKeyArgument.resourceOrTagKey(Registries.ITEM))
                        .executes(ctx -> executeInsertTagDefault(ctx, getItemTag(ctx, "tag"), 1))
                        .then(Commands.argument("count", LongArgumentType.longArg(1, Long.MAX_VALUE))
                                .executes(ctx -> executeInsertTagDefault(ctx, getItemTag(ctx, "tag"), LongArgumentType.getLong(ctx, "count")))
                                .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                                        .executes(ctx -> executeInsertTag(ctx, IntegerArgumentType.getInteger(ctx, "netId"), getItemTag(ctx, "tag"), LongArgumentType.getLong(ctx, "count"))))))
                .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> { ctx.getSource().sendFailure(CommandLang.component("error.tag_required")); return 0; })
                        .then(Commands.argument("tag", ResourceOrTagKeyArgument.resourceOrTagKey(Registries.ITEM))
                                .executes(ctx -> executeInsertTag(ctx, IntegerArgumentType.getInteger(ctx, "netId"), getItemTag(ctx, "tag"), 1))
                                .then(Commands.argument("count", LongArgumentType.longArg(1, Long.MAX_VALUE))
                                        .executes(ctx -> executeInsertTag(ctx, IntegerArgumentType.getInteger(ctx, "netId"), getItemTag(ctx, "tag"), LongArgumentType.getLong(ctx, "count"))))));
    }

    /** 从命令上下文解析物品标签；输入为单个物品 ID（无 # 前缀）时提示需要标签 */
    private static TagKey<Item> getItemTag(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        var result = ResourceOrTagKeyArgument.getResourceOrTagKey(ctx, name, Registries.ITEM,
                new DynamicCommandExceptionType(arg -> CommandLang.component("error.tag_required")));
        if (result.unwrap().right().isEmpty()) {
            throw new SimpleCommandExceptionType(CommandLang.component("error.tag_required")).create();
        }
        return result.unwrap().right().get();
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildFluidInsertCommand(CommandBuildContext context) {
        return Commands.literal("fluid")
                .executes(ctx -> { ctx.getSource().sendFailure(CommandLang.component("error.fluid_required")); return 0; })
                .then(Commands.argument("fluid", ResourceArgument.resource(context, Registries.FLUID))
                        .executes(ctx -> { var h = ResourceArgument.getResource(ctx, "fluid", Registries.FLUID); return executeInsertFluidDefault(ctx, h.value(), 1000L); })
                        .then(Commands.argument("amount", LongArgumentType.longArg(1, Long.MAX_VALUE))
                                .executes(ctx -> { var h = ResourceArgument.getResource(ctx, "fluid", Registries.FLUID); return executeInsertFluidDefault(ctx, h.value(), LongArgumentType.getLong(ctx, "amount")); })
                                .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                                        .executes(ctx -> { var h = ResourceArgument.getResource(ctx, "fluid", Registries.FLUID); return executeInsertFluid(ctx, IntegerArgumentType.getInteger(ctx, "netId"), h.value(), LongArgumentType.getLong(ctx, "amount")); }))))
                .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> { ctx.getSource().sendFailure(CommandLang.component("error.fluid_required")); return 0; })
                        .then(Commands.argument("fluid", ResourceArgument.resource(context, Registries.FLUID))
                                .executes(ctx -> { var h = ResourceArgument.getResource(ctx, "fluid", Registries.FLUID); return executeInsertFluid(ctx, IntegerArgumentType.getInteger(ctx, "netId"), h.value(), 1000L); })
                                .then(Commands.argument("amount", LongArgumentType.longArg(1, Long.MAX_VALUE))
                                        .executes(ctx -> { var h = ResourceArgument.getResource(ctx, "fluid", Registries.FLUID); return executeInsertFluid(ctx, IntegerArgumentType.getInteger(ctx, "netId"), h.value(), LongArgumentType.getLong(ctx, "amount")); }))));
    }

    /**
     * 能量插入命令
     * 用法：energy [fe数量] [netId]（数量在前，网络 ID 在后；省略则数量 1000、主要网络）
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildEnergyInsertCommand() {
        return Commands.literal("energy")
                .executes(ctx -> executeInsertEnergyDefault(ctx, 1000L))
                .then(Commands.argument("amount", LongArgumentType.longArg(1, Long.MAX_VALUE))
                        .executes(ctx -> executeInsertEnergyDefault(ctx, LongArgumentType.getLong(ctx, "amount")))
                        .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                                .executes(ctx -> executeInsertEnergy(ctx, IntegerArgumentType.getInteger(ctx, "netId"), LongArgumentType.getLong(ctx, "amount")))));
    }

    // ========== Item ==========

    private static int executeInsertItemDefault(CommandContext<CommandSourceStack> ctx, ItemStack stack, long count) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        ServerPlayer executor = source.getPlayer();
        if (executor == null) { source.sendFailure(OutputFormatter.createError("error.player_required")); return 0; }
        DimensionsNet primaryNet = DimensionsNet.getPrimaryNetFromPlayer(executor);
        if (primaryNet == null) { source.sendFailure(OutputFormatter.createError("error.not_in_network")); return 0; }
        return executeInsertItemInternal(source, primaryNet, primaryNet.getId(), stack, count);
    }

    private static int executeInsertItem(CommandContext<CommandSourceStack> ctx, int netId, ItemStack stack, long count) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        DimensionsNet net = PermissionChecker.checkNetworkExists(source, netId);
        if (net == null) return 0;
        return executeInsertItemInternal(source, net, netId, stack, count);
    }

    private static int executeInsertItemInternal(CommandSourceStack source, DimensionsNet net, int netId, ItemStack stack, long count) {
        if (!PermissionChecker.checkAmountPositive(source, count)) return 0;
        ItemStackKey key = new ItemStackKey(stack.copyWithCount(1));
        if (!NetworkUtils.hasEnoughStorageForItem(net, key, count)) { source.sendFailure(OutputFormatter.createError("network.transfer.insufficient_storage")); return 0; }
        KeyAmount remaining = net.getUnifiedStorage().insert(key, count, false);
        if (remaining.amount() > 0) { source.sendFailure(OutputFormatter.createError("error.insert_failed", remaining.amount())); return 0; }
        net.setDirty();
        String name = stack.getHoverName().getString();
        source.sendSuccess(() -> Component.literal(CommandLang.get("network.insert.item.success", count, name, netId)), false);
        return 1;
    }

    // ========== Tag ==========

    private static int executeInsertTagDefault(CommandContext<CommandSourceStack> ctx, TagKey<Item> tag, long count) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        ServerPlayer executor = source.getPlayer();
        if (executor == null) { source.sendFailure(OutputFormatter.createError("error.player_required")); return 0; }
        DimensionsNet primaryNet = DimensionsNet.getPrimaryNetFromPlayer(executor);
        if (primaryNet == null) { source.sendFailure(OutputFormatter.createError("error.not_in_network")); return 0; }
        return executeInsertTagInternal(source, primaryNet, primaryNet.getId(), tag, count);
    }

    private static int executeInsertTag(CommandContext<CommandSourceStack> ctx, int netId, TagKey<Item> tag, long count) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        DimensionsNet net = PermissionChecker.checkNetworkExists(source, netId);
        if (net == null) return 0;
        return executeInsertTagInternal(source, net, netId, tag, count);
    }

    /**
     * 按物品标签批量插入，结果以列表逐种输出：
     * 标题行 + 每种物品一行（成功显示物品与数量，跳过显示原因）+ 汇总行。
     */
    private static int executeInsertTagInternal(CommandSourceStack source, DimensionsNet net, int netId, TagKey<Item> tag, long count) {
        if (!PermissionChecker.checkAmountPositive(source, count)) return 0;

        List<Item> items = new ArrayList<>();
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            items.add(holder.value());
        }
        if (items.isEmpty()) {
            source.sendFailure(OutputFormatter.createError("error.tag_empty", tag.location().toString()));
            return 0;
        }

        String tagName = "#" + tag.location();
        int insertedTypes = 0;
        int skippedTypes = 0;
        long totalInserted = 0;

        // 标题行
        source.sendSuccess(() -> OutputFormatter.createTitle("network.insert.tag.title", netId, tagName), false);

        // 逐种物品插入并输出列表行
        for (Item item : items) {
            ItemStack stack = new ItemStack(item);
            ItemStackKey key = new ItemStackKey(stack.copyWithCount(1));

            if (!NetworkUtils.hasEnoughStorageForItem(net, key, count)) {
                skippedTypes++;
                source.sendSuccess(() -> OutputFormatter.createWarning("network.insert.tag.line.skipped",
                        stack.getHoverName().getString()), false);
                continue;
            }

            KeyAmount remaining = net.getUnifiedStorage().insert(key, count, false);
            long inserted = count - remaining.amount();
            if (inserted > 0) {
                insertedTypes++;
                totalInserted += inserted;
            }
            if (remaining.amount() > 0) {
                skippedTypes++;
            }

            final long shown = inserted;
            if (inserted > 0) {
                source.sendSuccess(() -> OutputFormatter.createItemDisplay(stack, shown), false);
            } else {
                source.sendSuccess(() -> OutputFormatter.createWarning("network.insert.tag.line.skipped",
                        stack.getHoverName().getString()), false);
            }
        }

        if (totalInserted <= 0) {
            source.sendFailure(OutputFormatter.createError("network.transfer.insufficient_storage"));
            return 0;
        }

        net.setDirty();

        // 汇总行
        final int okTypes = insertedTypes;
        final int skipTypes = skippedTypes;
        final long sumInserted = totalInserted;
        source.sendSuccess(() -> OutputFormatter.createSuccess("network.insert.tag.summary",
                okTypes, items.size(), count, sumInserted, skipTypes), false);

        return 1;
    }

    // ========== Fluid ==========

    private static int executeInsertFluidDefault(CommandContext<CommandSourceStack> ctx, Fluid fluid, long amount) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        ServerPlayer executor = source.getPlayer();
        if (executor == null) { source.sendFailure(OutputFormatter.createError("error.player_required")); return 0; }
        DimensionsNet primaryNet = DimensionsNet.getPrimaryNetFromPlayer(executor);
        if (primaryNet == null) { source.sendFailure(OutputFormatter.createError("error.not_in_network")); return 0; }
        return executeInsertFluidInternal(source, primaryNet, primaryNet.getId(), fluid, amount);
    }

    private static int executeInsertFluid(CommandContext<CommandSourceStack> ctx, int netId, Fluid fluid, long amount) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        DimensionsNet net = PermissionChecker.checkNetworkExists(source, netId);
        if (net == null) return 0;
        return executeInsertFluidInternal(source, net, netId, fluid, amount);
    }

    private static int executeInsertFluidInternal(CommandSourceStack source, DimensionsNet net, int netId, Fluid fluid, long amount) {
        if (!PermissionChecker.checkAmountPositive(source, amount)) return 0;
        FluidStack fs = new FluidStack(fluid, 1);
        FluidStackKey key = new FluidStackKey(fs);
        if (!NetworkUtils.hasEnoughStorageForFluid(net, key, amount)) { source.sendFailure(OutputFormatter.createError("network.transfer.insufficient_storage")); return 0; }
        KeyAmount remaining = net.getUnifiedStorage().insert(key, amount, false);
        if (remaining.amount() > 0) { source.sendFailure(OutputFormatter.createError("error.insert_failed", remaining.amount())); return 0; }
        net.setDirty();
        source.sendSuccess(() -> Component.literal(CommandLang.get("network.insert.fluid.success", amount, fluid.getFluidType().getDescription().getString(), netId)), false);
        return 1;
    }

    // ========== Energy ==========

    private static int executeInsertEnergyDefault(CommandContext<CommandSourceStack> ctx, long amount) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        ServerPlayer executor = source.getPlayer();
        if (executor == null) { source.sendFailure(OutputFormatter.createError("error.player_required")); return 0; }
        DimensionsNet primaryNet = DimensionsNet.getPrimaryNetFromPlayer(executor);
        if (primaryNet == null) { source.sendFailure(OutputFormatter.createError("error.not_in_network")); return 0; }
        return executeInsertEnergyInternal(source, primaryNet, primaryNet.getId(), amount);
    }

    private static int executeInsertEnergy(CommandContext<CommandSourceStack> ctx, int netId, long amount) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        DimensionsNet net = PermissionChecker.checkNetworkExists(source, netId);
        if (net == null) return 0;
        return executeInsertEnergyInternal(source, net, netId, amount);
    }

    private static int executeInsertEnergyInternal(CommandSourceStack source, DimensionsNet net, int netId, long amount) {
        if (!PermissionChecker.checkAmountPositive(source, amount)) return 0;
        if (!NetworkUtils.hasEnoughStorageForEnergy(net, EnergyStackKey.INSTANCE, amount)) { source.sendFailure(OutputFormatter.createError("network.transfer.insufficient_storage")); return 0; }
        KeyAmount remaining = net.getUnifiedStorage().insert(EnergyStackKey.INSTANCE, amount, false);
        if (remaining.amount() > 0) { source.sendFailure(OutputFormatter.createError("error.insert_failed", remaining.amount())); return 0; }
        net.setDirty();
        source.sendSuccess(() -> Component.literal(CommandLang.get("network.insert.energy.success", amount, netId)), false);
        return 1;
    }
}

