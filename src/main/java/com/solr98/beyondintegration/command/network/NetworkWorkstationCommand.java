package com.solr98.beyondintegration.command.network;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.solr98.beyondintegration.command.CommandLang;
import com.solr98.beyondintegration.command.util.*;
import com.solr98.beyondintegration.feature.workstation.WorkstationActivation;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 工作台献祭激活管理命令（OP）：
 * <pre>
 * /bdtools network workstation reset &lt;工作台ID|all&gt; [netId]  重置激活状态（单独 / 全部）
 * </pre>
 * netId 省略时作用于执行者的主要网络；查看激活状态见 /bdtools network info。
 */
public class NetworkWorkstationCommand {

    /** 工作台 ID 补全（anvil/cut/grind/smith/enchant） */
    private static final SuggestionProvider<CommandSourceStack> ID_SUGGESTIONS = (ctx, builder) -> {
        for (String id : new String[]{"anvil", "cut", "grind", "smith", "enchant"}) builder.suggest(id);
        return builder.buildFuture();
    };

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("workstation")
            .requires(source -> CommandUtils.hasOpPermission(source))
            // 重置激活状态
            .then(Commands.literal("reset")
                // 全部重置
                .then(Commands.literal("all")
                    .executes(ctx -> executeReset(ctx, null, -1))
                    .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> executeReset(ctx, null, IntegerArgumentType.getInteger(ctx, "netId")))))
                // 单独重置
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests(ID_SUGGESTIONS)
                    .executes(ctx -> executeReset(ctx, StringArgumentType.getString(ctx, "id"), -1))
                    .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> executeReset(ctx, StringArgumentType.getString(ctx, "id"),
                                IntegerArgumentType.getInteger(ctx, "netId"))))));
    }

    /** 解析目标网络：netId < 0 时用执行者主网络 */
    private static DimensionsNet resolveNet(CommandSourceStack source, int netId) {
        if (netId >= 0) return PermissionChecker.checkNetworkExists(source, netId);
        ServerPlayer executor = source.getPlayer();
        if (executor == null) {
            source.sendFailure(OutputFormatter.createError("error.player_required"));
            return null;
        }
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(executor);
        if (net == null) {
            source.sendFailure(OutputFormatter.createError("error.not_in_network"));
            return null;
        }
        return net;
    }

    /** 执行重置：id=null 全部重置，否则单独重置 */
    private static int executeReset(CommandContext<CommandSourceStack> ctx, String id, int netId) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        if (id != null && !WorkstationActivation.isActivatable(id)) {
            source.sendFailure(OutputFormatter.createError("network.workstation.invalid_id", id));
            return 0;
        }
        DimensionsNet net = resolveNet(source, netId);
        if (net == null) return 0;

        List<String> removed = WorkstationActivation.reset(net, id);
        if (removed.isEmpty()) {
            source.sendSuccess(() -> Component.literal(CommandLang.get(
                    id == null ? "network.workstation.reset.all_none" : "network.workstation.reset.none",
                    net.getId(), id)), false);
            return 0;
        }
        if (id == null) {
            source.sendSuccess(() -> Component.literal(CommandLang.get(
                    "network.workstation.reset.all", net.getId(), removed.size(), String.join(", ", removed))), true);
        } else {
            source.sendSuccess(() -> Component.literal(CommandLang.get(
                    "network.workstation.reset.one", net.getId(), id)), true);
        }
        return 1;
    }
}
