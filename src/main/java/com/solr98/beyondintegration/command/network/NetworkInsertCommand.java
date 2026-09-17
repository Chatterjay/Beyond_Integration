package com.solr98.beyondintegration.command.network;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import com.solr98.beyondintegration.command.CommandLang;
import com.solr98.beyondintegration.command.util.*;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;

import java.util.ArrayList;
import java.util.List;

/**
 * 网络插入命令
 * 功能：向网络插入物品、流体或能量
 */
public class NetworkInsertCommand {
    
    /**
     * 注册命令
     */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> register(net.minecraft.commands.CommandBuildContext context) {
        return Commands.literal("insert")
            // 物品插入命令
            .then(buildItemInsertCommand(context))
            // 物品标签批量插入命令
            .then(buildTagInsertCommand(context))
            // 流体插入命令
            .then(buildFluidInsertCommand(context))
            // 能量插入命令
            .then(buildEnergyInsertCommand());
    }
    
    /**
     * 构建物品插入命令
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildItemInsertCommand(
            net.minecraft.commands.CommandBuildContext context) {
        return Commands.literal("item")
            // 默认：插入到当前玩家的主要网络，数量为1
            .executes(ctx -> {
                ctx.getSource().sendFailure(CommandLang.component("error.item_required"));
                return 0;
            })
            // 指定物品，默认网络和数量
            .then(Commands.argument("item", ItemArgument.item(context))
                .executes(ctx -> executeInsertItemDefault(ctx, 
                        ItemArgument.getItem(ctx, "item").createItemStack(1, false), 1))
                // 指定物品和数量，默认网络
                .then(Commands.argument("count", LongArgumentType.longArg(1, Long.MAX_VALUE))
                    .executes(ctx -> executeInsertItemDefault(ctx,
                            ItemArgument.getItem(ctx, "item").createItemStack(1, false),
                            LongArgumentType.getLong(ctx, "count")))
                    // 指定网络、物品和数量
                    .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> executeInsertItem(ctx, IntegerArgumentType.getInteger(ctx, "netId"),
                                ItemArgument.getItem(ctx, "item").createItemStack(1, false),
                                LongArgumentType.getLong(ctx, "count"))))))
            // 指定网络，默认物品和数量（需要物品参数）
            .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                .executes(ctx -> {
                    ctx.getSource().sendFailure(CommandLang.component("error.item_required"));
                    return 0;
                })
                .then(Commands.argument("item", ItemArgument.item(context))
                    .executes(ctx -> executeInsertItem(ctx, IntegerArgumentType.getInteger(ctx, "netId"), 
                            ItemArgument.getItem(ctx, "item").createItemStack(1, false), 1))
                    .then(Commands.argument("count", LongArgumentType.longArg(1, Long.MAX_VALUE))
                        .executes(ctx -> executeInsertItem(ctx, IntegerArgumentType.getInteger(ctx, "netId"),
                                ItemArgument.getItem(ctx, "item").createItemStack(1, false),
                                LongArgumentType.getLong(ctx, "count"))))));
    }
    
    /**
     * 构建物品标签批量插入命令
     * 用法：insert tag <#标签> [每种数量] [netId] 或 insert tag <netId> <#标签> [每种数量]
     * 标签内每种物品各插入 count 个，空间不足的种类自动跳过
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildTagInsertCommand(
            net.minecraft.commands.CommandBuildContext context) {
        return Commands.literal("tag")
            // 缺少标签参数
            .executes(ctx -> {
                ctx.getSource().sendFailure(CommandLang.component("error.tag_required"));
                return 0;
            })
            // 指定标签，默认网络和数量（每种1个）
            .then(Commands.argument("tag", ResourceOrTagKeyArgument.resourceOrTagKey(Registries.ITEM))
                .executes(ctx -> executeInsertTagDefault(ctx, getItemTag(ctx, "tag"), 1))
                // 指定标签和数量，默认网络
                .then(Commands.argument("count", LongArgumentType.longArg(1, Long.MAX_VALUE))
                    .executes(ctx -> executeInsertTagDefault(ctx, getItemTag(ctx, "tag"),
                            LongArgumentType.getLong(ctx, "count")))
                    // 指定网络、标签和数量
                    .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> executeInsertTag(ctx, IntegerArgumentType.getInteger(ctx, "netId"),
                                getItemTag(ctx, "tag"), LongArgumentType.getLong(ctx, "count"))))))
            // 指定网络，默认标签和数量
            .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                .executes(ctx -> {
                    ctx.getSource().sendFailure(CommandLang.component("error.tag_required"));
                    return 0;
                })
                .then(Commands.argument("tag", ResourceOrTagKeyArgument.resourceOrTagKey(Registries.ITEM))
                    .executes(ctx -> executeInsertTag(ctx, IntegerArgumentType.getInteger(ctx, "netId"),
                            getItemTag(ctx, "tag"), 1))
                    .then(Commands.argument("count", LongArgumentType.longArg(1, Long.MAX_VALUE))
                        .executes(ctx -> executeInsertTag(ctx, IntegerArgumentType.getInteger(ctx, "netId"),
                                getItemTag(ctx, "tag"), LongArgumentType.getLong(ctx, "count"))))));
    }

    /** 从命令上下文解析物品标签；输入为单个物品 ID（无 # 前缀）时提示需要标签 */
    private static TagKey<Item> getItemTag(CommandContext<CommandSourceStack> ctx, String name)
            throws CommandSyntaxException {
        var result = ResourceOrTagKeyArgument.getResourceOrTagKey(ctx, name, Registries.ITEM,
                new DynamicCommandExceptionType(arg -> CommandLang.component("error.tag_required")));
        if (result.unwrap().right().isEmpty()) {
            throw new SimpleCommandExceptionType(CommandLang.component("error.tag_required")).create();
        }
        return result.unwrap().right().get();
    }

    /**
     * 构建流体插入命令
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildFluidInsertCommand(
            net.minecraft.commands.CommandBuildContext context) {
        return Commands.literal("fluid")
            // 默认：插入到当前玩家的主要网络，数量为1000mB
            .executes(ctx -> {
                ctx.getSource().sendFailure(CommandLang.component("error.fluid_required"));
                return 0;
            })
            // 指定流体，默认网络和数量（1000mB）
            .then(Commands.argument("fluid", ResourceArgument.resource(context, Registries.FLUID))
                .executes(ctx -> {
                    var fluidHolder = ResourceArgument.getResource(ctx, "fluid", Registries.FLUID);
                    return executeInsertFluidDefault(ctx, fluidHolder.value(), 1000L);
                })
                // 指定流体和数量，默认网络
                .then(Commands.argument("amount", LongArgumentType.longArg(1, Long.MAX_VALUE))
                    .executes(ctx -> {
                        var fluidHolder = ResourceArgument.getResource(ctx, "fluid", Registries.FLUID);
                        return executeInsertFluidDefault(ctx, fluidHolder.value(), 
                                LongArgumentType.getLong(ctx, "amount"));
                    })
                    // 指定网络、流体和数量
                    .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> {
                            var fluidHolder = ResourceArgument.getResource(ctx, "fluid", Registries.FLUID);
                            return executeInsertFluid(ctx, IntegerArgumentType.getInteger(ctx, "netId"),
                                    fluidHolder.value(), LongArgumentType.getLong(ctx, "amount"));
                        }))))
            // 指定网络，默认流体和数量（需要流体参数）
            .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                .executes(ctx -> {
                    ctx.getSource().sendFailure(CommandLang.component("error.fluid_required"));
                    return 0;
                })
                .then(Commands.argument("fluid", ResourceArgument.resource(context, Registries.FLUID))
                    .executes(ctx -> {
                        var fluidHolder = ResourceArgument.getResource(ctx, "fluid", Registries.FLUID);
                        return executeInsertFluid(ctx, IntegerArgumentType.getInteger(ctx, "netId"),
                                fluidHolder.value(), 1000L);
                    })
                    .then(Commands.argument("amount", LongArgumentType.longArg(1, Long.MAX_VALUE))
                        .executes(ctx -> {
                            var fluidHolder = ResourceArgument.getResource(ctx, "fluid", Registries.FLUID);
                            return executeInsertFluid(ctx, IntegerArgumentType.getInteger(ctx, "netId"),
                                    fluidHolder.value(), LongArgumentType.getLong(ctx, "amount"));
                        }))));
    }
    
    /**
     * 构建能量插入命令
     * 用法：energy [fe数量] [netId]（数量在前，网络 ID 在后；省略则数量 1000、主要网络）
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> buildEnergyInsertCommand() {
        return Commands.literal("energy")
            // 默认：插入到当前玩家的主要网络，数量为1000FE
            .executes(ctx -> executeInsertEnergyDefault(ctx, 1000L))
            // 指定数量，默认网络
            .then(Commands.argument("amount", LongArgumentType.longArg(1, Long.MAX_VALUE))
                .executes(ctx -> executeInsertEnergyDefault(ctx, LongArgumentType.getLong(ctx, "amount")))
                // 指定数量和网络
                .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                    .executes(ctx -> executeInsertEnergy(ctx, IntegerArgumentType.getInteger(ctx, "netId"),
                            LongArgumentType.getLong(ctx, "amount")))));
    }
    
    // ========== 物品插入方法 ==========
    
    /**
     * 执行插入物品到默认网络（当前玩家的主要网络）
     */
    private static int executeInsertItemDefault(CommandContext<CommandSourceStack> ctx, 
                                               ItemStack itemStack, long count) {
        CommandSourceStack source = ctx.getSource();
        
        // 检查OP权限
        if (!PermissionChecker.checkOpPermission(source)) {
            return 0;
        }
        
        // 获取执行者玩家
        ServerPlayer executor = source.getPlayer();
        if (executor == null) {
            source.sendFailure(OutputFormatter.createError("error.player_required"));
            return 0;
        }
        
        // 获取当前玩家的主要网络
        DimensionsNet primaryNet = DimensionsNet.getPrimaryNetFromPlayer(executor);
        if (primaryNet == null) {
            source.sendFailure(OutputFormatter.createError("error.not_in_network"));
            return 0;
        }
        
        int netId = primaryNet.getId();
        return executeInsertItemInternal(source, primaryNet, netId, itemStack, count);
    }
    
    /**
     * 执行插入物品命令
     */
    private static int executeInsertItem(CommandContext<CommandSourceStack> ctx, int netId, 
                                        ItemStack itemStack, long count) {
        CommandSourceStack source = ctx.getSource();
        
        // 检查OP权限
        if (!PermissionChecker.checkOpPermission(source)) {
            return 0;
        }
        
        // 检查网络是否存在
        DimensionsNet net = PermissionChecker.checkNetworkExists(source, netId);
        if (net == null) {
            return 0;
        }
        
        return executeInsertItemInternal(source, net, netId, itemStack, count);
    }
    
    /**
     * 内部：执行物品插入
     */
    private static int executeInsertItemInternal(CommandSourceStack source, DimensionsNet net, 
                                                int netId, ItemStack itemStack, long count) {
        // 检查数量是否为正数
        if (!PermissionChecker.checkAmountPositive(source, count)) {
            return 0;
        }
        
        // 创建物品键
        ItemStackKey itemKey = new ItemStackKey(itemStack.copyWithCount(1));
        
        // 检查存储空间
        if (!NetworkUtils.hasEnoughStorageForItem(net, itemKey, count)) {
            source.sendFailure(OutputFormatter.createError("error.insufficient_storage"));
            return 0;
        }
        
        // 插入物品
        KeyAmount remaining = net.getUnifiedStorage().insert(itemKey, count, false);
        
        if (remaining.amount() > 0) {
            source.sendFailure(OutputFormatter.createError("error.insert_failed", remaining.amount()));
            return 0;
        }
        
        // 标记网络为脏数据
        net.setDirty();
        
        // 发送成功消息
        String itemName = itemStack.getHoverName().getString();
        source.sendSuccess(() -> Component.literal(
            CommandLang.get("network.insert.item.success", count, itemName, netId)
        ), false);
        
        return 1;
    }
    
    // ========== 物品标签插入方法 ==========
    
    /**
     * 执行插入标签物品到默认网络（当前玩家的主要网络）
     */
    private static int executeInsertTagDefault(CommandContext<CommandSourceStack> ctx,
                                               TagKey<Item> tag, long count) {
        CommandSourceStack source = ctx.getSource();
        
        // 检查OP权限
        if (!PermissionChecker.checkOpPermission(source)) {
            return 0;
        }
        
        // 获取执行者玩家
        ServerPlayer executor = source.getPlayer();
        if (executor == null) {
            source.sendFailure(OutputFormatter.createError("error.player_required"));
            return 0;
        }
        
        // 获取当前玩家的主要网络
        DimensionsNet primaryNet = DimensionsNet.getPrimaryNetFromPlayer(executor);
        if (primaryNet == null) {
            source.sendFailure(OutputFormatter.createError("error.not_in_network"));
            return 0;
        }
        
        return executeInsertTagInternal(source, primaryNet, primaryNet.getId(), tag, count);
    }
    
    /**
     * 执行插入标签物品命令
     */
    private static int executeInsertTag(CommandContext<CommandSourceStack> ctx, int netId,
                                        TagKey<Item> tag, long count) {
        CommandSourceStack source = ctx.getSource();
        
        // 检查OP权限
        if (!PermissionChecker.checkOpPermission(source)) {
            return 0;
        }
        
        // 检查网络是否存在
        DimensionsNet net = PermissionChecker.checkNetworkExists(source, netId);
        if (net == null) {
            return 0;
        }
        
        return executeInsertTagInternal(source, net, netId, tag, count);
    }
    
    /**
     * 内部：按物品标签批量插入，结果以列表逐种输出。
     * 标签内每种物品各插入 count 个；单种空间不足时跳过该种（不影响其他种类）。
     * 输出：标题行 + 每种物品一行（成功显示物品与数量，跳过显示原因）+ 汇总行。
     */
    private static int executeInsertTagInternal(CommandSourceStack source, DimensionsNet net,
                                                int netId, TagKey<Item> tag, long count) {
        // 检查数量是否为正数
        if (!PermissionChecker.checkAmountPositive(source, count)) {
            return 0;
        }
        
        // 收集标签内全部物品
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
            
            // 空间不足的种类跳过，不影响其他种类
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
            
            // 成功行（物品名 x实际插入数量）；未插入任何数量时按跳过提示
            final long shown = inserted;
            if (inserted > 0) {
                source.sendSuccess(() -> OutputFormatter.createItemDisplay(stack, shown), false);
            } else {
                source.sendSuccess(() -> OutputFormatter.createWarning("network.insert.tag.line.skipped",
                        stack.getHoverName().getString()), false);
            }
        }
        
        if (totalInserted <= 0) {
            source.sendFailure(OutputFormatter.createError("error.insufficient_storage"));
            return 0;
        }
        
        // 标记网络为脏数据
        net.setDirty();
        
        // 汇总行
        final int okTypes = insertedTypes;
        final int skipTypes = skippedTypes;
        final long sumInserted = totalInserted;
        source.sendSuccess(() -> OutputFormatter.createSuccess("network.insert.tag.summary",
                okTypes, items.size(), count, sumInserted, skipTypes), false);
        
        return 1;
    }
    
    // ========== 流体插入方法 ==========
    
    /**
     * 执行插入流体到默认网络（当前玩家的主要网络）
     */
    private static int executeInsertFluidDefault(CommandContext<CommandSourceStack> ctx, 
                                                Fluid fluid, long amount) {
        CommandSourceStack source = ctx.getSource();
        
        // 检查OP权限
        if (!PermissionChecker.checkOpPermission(source)) {
            return 0;
        }
        
        // 获取执行者玩家
        ServerPlayer executor = source.getPlayer();
        if (executor == null) {
            source.sendFailure(OutputFormatter.createError("error.player_required"));
            return 0;
        }
        
        // 获取当前玩家的主要网络
        DimensionsNet primaryNet = DimensionsNet.getPrimaryNetFromPlayer(executor);
        if (primaryNet == null) {
            source.sendFailure(OutputFormatter.createError("error.not_in_network"));
            return 0;
        }
        
        int netId = primaryNet.getId();
        return executeInsertFluidInternal(source, primaryNet, netId, fluid, amount);
    }
    
    /**
     * 执行插入流体命令
     */
    private static int executeInsertFluid(CommandContext<CommandSourceStack> ctx, int netId, 
                                         Fluid fluid, long amount) {
        CommandSourceStack source = ctx.getSource();
        
        // 检查OP权限
        if (!PermissionChecker.checkOpPermission(source)) {
            return 0;
        }
        
        // 检查网络是否存在
        DimensionsNet net = PermissionChecker.checkNetworkExists(source, netId);
        if (net == null) {
            return 0;
        }
        
        return executeInsertFluidInternal(source, net, netId, fluid, amount);
    }
    
    /**
     * 内部：执行流体插入
     */
    private static int executeInsertFluidInternal(CommandSourceStack source, DimensionsNet net, 
                                                 int netId, Fluid fluid, long amount) {
        // 检查数量是否为正数
        if (!PermissionChecker.checkAmountPositive(source, amount)) {
            return 0;
        }
        
        // 创建流体堆栈和键
        FluidStack fluidStack = new FluidStack(fluid, 1);
        FluidStackKey fluidKey = new FluidStackKey(fluidStack);
        
        // 检查存储空间
        if (!NetworkUtils.hasEnoughStorageForFluid(net, fluidKey, amount)) {
            source.sendFailure(OutputFormatter.createError("error.insufficient_storage"));
            return 0;
        }
        
        // 插入流体
        KeyAmount remaining = net.getUnifiedStorage().insert(fluidKey, amount, false);
        
        if (remaining.amount() > 0) {
            source.sendFailure(OutputFormatter.createError("error.insert_failed", remaining.amount()));
            return 0;
        }
        
        // 标记网络为脏数据
        net.setDirty();
        
        // 发送成功消息
        String fluidName = fluid.getFluidType().getDescription().getString();
        source.sendSuccess(() -> Component.literal(
            CommandLang.get("network.insert.fluid.success", amount, fluidName, netId)
        ), false);
        
        return 1;
    }
    
    // ========== 能量插入方法 ==========
    
    /**
     * 执行插入能量到默认网络（当前玩家的主要网络）
     */
    private static int executeInsertEnergyDefault(CommandContext<CommandSourceStack> ctx, long amount) {
        CommandSourceStack source = ctx.getSource();
        
        // 检查OP权限
        if (!PermissionChecker.checkOpPermission(source)) {
            return 0;
        }
        
        // 获取执行者玩家
        ServerPlayer executor = source.getPlayer();
        if (executor == null) {
            source.sendFailure(OutputFormatter.createError("error.player_required"));
            return 0;
        }
        
        // 获取当前玩家的主要网络
        DimensionsNet primaryNet = DimensionsNet.getPrimaryNetFromPlayer(executor);
        if (primaryNet == null) {
            source.sendFailure(OutputFormatter.createError("error.not_in_network"));
            return 0;
        }
        
        int netId = primaryNet.getId();
        return executeInsertEnergyInternal(source, primaryNet, netId, amount);
    }
    
    /**
     * 执行插入能量命令
     */
    private static int executeInsertEnergy(CommandContext<CommandSourceStack> ctx, int netId, long amount) {
        CommandSourceStack source = ctx.getSource();
        
        // 检查OP权限
        if (!PermissionChecker.checkOpPermission(source)) {
            return 0;
        }
        
        // 检查网络是否存在
        DimensionsNet net = PermissionChecker.checkNetworkExists(source, netId);
        if (net == null) {
            return 0;
        }
        
        return executeInsertEnergyInternal(source, net, netId, amount);
    }
    
    /**
     * 内部：执行能量插入
     */
    private static int executeInsertEnergyInternal(CommandSourceStack source, DimensionsNet net, 
                                                  int netId, long amount) {
        // 检查数量是否为正数
        if (!PermissionChecker.checkAmountPositive(source, amount)) {
            return 0;
        }
        
        // 检查存储空间
        if (!NetworkUtils.hasEnoughStorageForEnergy(net, EnergyStackKey.INSTANCE, amount)) {
            source.sendFailure(OutputFormatter.createError("error.insufficient_storage"));
            return 0;
        }
        
        // 插入能量
        KeyAmount remaining = net.getUnifiedStorage().insert(EnergyStackKey.INSTANCE, amount, false);
        
        if (remaining.amount() > 0) {
            source.sendFailure(OutputFormatter.createError("error.insert_failed", remaining.amount()));
            return 0;
        }
        
        // 标记网络为脏数据
        net.setDirty();
        
        // 发送成功消息
        source.sendSuccess(() -> Component.literal(
            CommandLang.get("network.insert.energy.success", amount, netId)
        ), false);
        
        return 1;
    }
}