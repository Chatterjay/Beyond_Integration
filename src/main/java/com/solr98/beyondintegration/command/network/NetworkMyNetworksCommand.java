package com.solr98.beyondintegration.command.network;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.solr98.beyondintegration.command.CommandLang;
import com.solr98.beyondintegration.command.util.*;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class NetworkMyNetworksCommand {

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("myNetworks")
                .executes(ctx -> executeDefault(ctx, null))
                .then(Commands.literal("list")
                        .executes(ctx -> executeList(ctx, null, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> executeList(ctx, null, IntegerArgumentType.getInteger(ctx, "page")))))
                .then(Commands.literal("info")
                        .executes(ctx -> executeInfo(ctx, -1, null))
                        .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                                .executes(ctx -> executeInfo(ctx, IntegerArgumentType.getInteger(ctx, "netId"), null))))
                .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> executeInfo(ctx, IntegerArgumentType.getInteger(ctx, "netId"), null)));
    }

    private static int executeDefault(CommandContext<CommandSourceStack> ctx, ServerPlayer targetPlayer) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkServerAvailable(source)) return 0;
        var executor = source.getPlayer();
        if (executor == null) { source.sendFailure(OutputFormatter.createError("error.player_required")); return 0; }
        ServerPlayer subject = executor;
        DimensionsNet primaryNet = DimensionsNet.getPrimaryNetFromPlayer(subject);
        if (primaryNet != null) return executeInfo(ctx, primaryNet.getId(), subject);
        else return executeList(ctx, subject, 1);
    }

    private static int executeList(CommandContext<CommandSourceStack> ctx, ServerPlayer targetPlayer, int page) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkServerAvailable(source)) return 0;
        var server = source.getServer();
        if (server == null) { source.sendFailure(OutputFormatter.createError("error.server_not_available")); return 0; }
        var executor = source.getPlayer();
        if (executor == null) { source.sendFailure(OutputFormatter.createError("error.player_required")); return 0; }
        ServerPlayer subject = executor;

        List<com.solr98.beyondintegration.command.util.NetworkUtils.NetInfo> networks = NetworkUtils.getPlayerNetworks(subject, server);
        int total = networks.size();
        if (total == 0) { source.sendSuccess(() -> Component.literal(CommandLang.get("network.myNetworks.none")).withStyle(ChatFormatting.GRAY), false); return 0; }

        int maxPerPage = com.solr98.beyondintegration.CommandConfig.maxNetworksPerPage();
        int totalPages = (int) Math.ceil((double) total / maxPerPage);
        if (page > totalPages && totalPages > 0) page = totalPages;
        int startIndex = (page - 1) * maxPerPage;

        MutableComponent msg = Component.empty();
        msg = msg.append(OutputFormatter.createPagedTitle("network.myNetworks.title.self", page)).append(Component.literal("\n"));
        msg = msg.append(Component.literal(String.format("%-4s", "ID")).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" | ")).append(Component.literal(String.format("%-8s", CommandLang.get("network.list.permission"))).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" | ")).append(Component.literal(String.format("%-20s", CommandLang.get("network.list.name"))).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" | ")).append(Component.literal(String.format("%-16s", CommandLang.get("network.list.owner"))).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" | ")).append(Component.literal(String.format("%3s", CommandLang.get("network.list.players"))).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" | ")).append(Component.literal(String.format("%3s", CommandLang.get("network.list.managers"))).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal("\n"));

        int displayed = 0;
        for (int i = 0; i < networks.size(); i++) {
            if (i >= startIndex && displayed < maxPerPage) {
                var info = networks.get(i);
                ChatFormatting permColor = info.permissionLevel.getColor();
                String displayName = info.netName.isEmpty() ? CommandLang.get("network.list.name.none") : info.netName;
                msg = msg.append(Component.literal(String.format("%-4d", info.netId)).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" | " + String.format("%-8s", info.permissionLevel.getDisplay())).withStyle(permColor))
                        .append(Component.literal(" | " + String.format("%-20s", displayName)).withStyle(ChatFormatting.LIGHT_PURPLE))
                        .append(Component.literal(" | " + String.format("%-16s", info.ownerName)).withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" | " + String.format("%03d", info.playerCount)).withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" | " + String.format("%03d", info.managerCount)).withStyle(ChatFormatting.BLUE))
                        .append(Component.literal("\n"));
                displayed++;
            }
        }
        if (totalPages > 1) {
            msg = msg.append(OutputFormatter.createPagination(page, totalPages, total, "/bdtools myNetworks list"));
        }
        Component fm = msg;
        source.sendSuccess(() -> fm, false);
        return total;
    }

    private static int executeInfo(CommandContext<CommandSourceStack> ctx, int netId, ServerPlayer targetPlayer) {
        return NetworkInfoCommand.exec(ctx, netId, null);
    }
}

