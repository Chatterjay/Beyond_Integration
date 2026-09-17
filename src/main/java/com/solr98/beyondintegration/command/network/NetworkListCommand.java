package com.solr98.beyondintegration.command.network;

import com.mojang.brigadier.context.CommandContext;
import com.solr98.beyondintegration.command.CommandLang;
import com.solr98.beyondintegration.command.util.*;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public class NetworkListCommand {

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("list")
                .requires(s -> CommandUtils.hasOpPermission(s))
                .executes(ctx -> exec(ctx, null, 1))
                .then(Commands.argument("page", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                        .executes(ctx -> exec(ctx, null, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "page"))))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> exec(ctx, EntityArgument.getPlayer(ctx, "player"), 1))
                        .then(Commands.argument("page", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                .executes(ctx -> exec(ctx, EntityArgument.getPlayer(ctx, "player"), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "page")))));
    }

    private static int exec(CommandContext<CommandSourceStack> ctx, ServerPlayer targetPlayer, int page) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkServerAvailable(source)) return 0;
        var server = source.getServer();
        if (server == null) { source.sendFailure(OutputFormatter.createError("error.server_not_available")); return 0; }
        var executor = source.getPlayer();
        if (executor == null) { source.sendFailure(OutputFormatter.createError("error.player_required")); return 0; }
        if (targetPlayer != null && !executor.getUUID().equals(targetPlayer.getUUID())) {
            if (!PermissionChecker.checkOpPermissionForOthers(source, targetPlayer)) return 0;
        }

        int maxPerPage = com.solr98.beyondintegration.CommandConfig.maxNetworksPerPage();
        int startIndex = (page - 1) * maxPerPage;
        ServerPlayer listPlayer = targetPlayer;
        List<NetInfo> networks = getNetworkList(server, listPlayer);
        int totalNetworks = networks.size();
        int totalPages = (int) Math.ceil((double) totalNetworks / maxPerPage);
        if (page > totalPages && totalPages > 0) { page = totalPages; startIndex = (page - 1) * maxPerPage; }

        MutableComponent msg = buildMessage(networks, listPlayer, page, totalPages, totalNetworks, startIndex, maxPerPage);
        source.sendSuccess(() -> msg, false);
        return totalNetworks;
    }

    private static List<NetInfo> getNetworkList(MinecraftServer server, ServerPlayer player) {
        List<NetInfo> networks = new ArrayList<>();
        for (int netId = 0; netId < 10000; netId++) {
            DimensionsNet net = DimensionsNet.getNetFromId(netId);
            if (net != null && !net.deleted) {
                if (player == null || net.getPlayers().contains(player.getUUID())) {
                    String ownerName = CommandUtils.getNetworkOwnerName(net, server);
                    NetworkPermission permLevel = NetworkPermission.NONE;
                    if (player != null) {
                        permLevel = NetworkPermission.fromPlayer(player, net);
                    }
                    String netName = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";
                    networks.add(new NetInfo(netId, permLevel, ownerName, net.getPlayers().size(), net.getManagers().size(), net.deleted, netName));
                }
            }
        }
        return networks;
    }

    private static MutableComponent buildMessage(List<NetInfo> networks, ServerPlayer player, int page, int totalPages, int totalNetworks, int startIndex, int maxPerPage) {
        MutableComponent msg = Component.empty();

        if (player != null) {
            msg = msg.append(OutputFormatter.createPagedTitle("network.list.player_title", page, player.getGameProfile().getName())).append(Component.literal("\n"));
        } else {
            msg = msg.append(OutputFormatter.createPagedTitle("network.list.all_title", page)).append(Component.literal("\n"));
        }

        msg = msg.append(Component.literal(String.format("%-4s", "ID")).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" | "))
                .append(Component.literal(String.format("%-20s", CommandLang.get("network.list.name"))).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" | "))
                .append(Component.literal(String.format("%-16s", CommandLang.get("network.list.owner"))).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" | "))
                .append(Component.literal(String.format("%3s", CommandLang.get("network.list.players"))).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" | "))
                .append(Component.literal(String.format("%3s", CommandLang.get("network.list.managers"))).withStyle(ChatFormatting.YELLOW));
        if (player != null) {
            msg = msg.append(Component.literal(" | ")).append(Component.literal(String.format("%-3s", CommandLang.get("network.list.permission"))).withStyle(ChatFormatting.YELLOW));
        }
        msg = msg.append(Component.literal("\n"));

        int displayed = 0;
        for (int i = 0; i < networks.size(); i++) {
            if (i >= startIndex && displayed < maxPerPage) {
                msg = msg.append(formatNetInfo(networks.get(i), player != null)).append(Component.literal("\n"));
                displayed++;
            }
        }
        if (displayed == 0) {
            msg = msg.append(Component.literal(CommandLang.get("network.list.none")).withStyle(ChatFormatting.GRAY)).append(Component.literal("\n"));
        }
        if (totalPages > 1) {
            String prefix = player != null ? "/bdtools network list " + player.getGameProfile().getName() : "/bdtools network list";
            msg = msg.append(OutputFormatter.createPagination(page, totalPages, totalNetworks, prefix));
        }
        return msg;
    }

    private static MutableComponent formatNetInfo(NetInfo info, boolean showPermission) {
        MutableComponent line = Component.empty();
        line = line.append(Component.literal(String.format("%-4d", info.netId)).withStyle(ChatFormatting.WHITE));
        String displayName = info.netName.isEmpty() ? CommandLang.get("network.list.name.none") : info.netName;
        line = line.append(Component.literal(" | " + String.format("%-20s", displayName)).withStyle(ChatFormatting.LIGHT_PURPLE));
        line = line.append(Component.literal(" | " + String.format("%-16s", info.ownerName)).withStyle(ChatFormatting.AQUA));
        line = line.append(Component.literal(" | " + String.format("%03d", info.playerCount)).withStyle(ChatFormatting.GREEN));
        line = line.append(Component.literal(" | " + String.format("%03d", info.managerCount)).withStyle(ChatFormatting.BLUE));
        if (showPermission) {
            String pt = info.permissionLevel == NetworkPermission.NONE ? "-" : info.permissionLevel.getDisplay();
            line = line.append(Component.literal(" | " + String.format("%-3s", pt)).withStyle(info.permissionLevel.getColor()));
        }
        if (info.deleted) {
            line = line.append(Component.literal(" " + CommandLang.get("network.list.deleted_mark")).withStyle(ChatFormatting.RED));
        }
        return line;
    }

    private record NetInfo(int netId, NetworkPermission permissionLevel, String ownerName, int playerCount, int managerCount, boolean deleted, String netName) {}
}

