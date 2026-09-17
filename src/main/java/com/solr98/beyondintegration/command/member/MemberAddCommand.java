package com.solr98.beyondintegration.command.member;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.solr98.beyondintegration.command.CommandLang;
import com.solr98.beyondintegration.command.util.*;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * 成员添加命令：提供 /bdtools member addMembers / addManagers，
 * 可将目标玩家批量添加为指定网络（或执行者主要网络）的成员/管理员。
 */
public class MemberAddCommand {

    /** 注册 addMembers 命令（添加成员，支持 to 指定多个网络） */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> registerAddMembers() {
        return Commands.literal("addMembers")
                .then(Commands.argument("players", EntityArgument.players())
                        .executes(ctx -> executeAddMembersToDefault(ctx, false))
                        .then(Commands.literal("to")
                                .then(buildNetworkChain(5, false))));
    }

    /** 注册 addManagers 命令（添加管理员，支持 to 指定多个网络） */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> registerAddManagers() {
        return Commands.literal("addManagers")
                .then(Commands.argument("players", EntityArgument.players())
                        .executes(ctx -> executeAddMembersToDefault(ctx, true))
                        .then(Commands.literal("to")
                                .then(buildNetworkChain(5, true))));
    }

    /** 构建连续的网络 ID 参数链（最多 maxNetworks 个），逐级执行添加 */
    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> buildNetworkChain(int maxNetworks, boolean isManager) {
        var builder = Commands.argument("netId1", IntegerArgumentType.integer(0, 9999))
                .executes(ctx -> executeAddMembers(ctx, isManager));
        var next = buildNetworkChainRecursive(2, maxNetworks, isManager);
        if (next != null) builder = builder.then(next);
        return builder;
    }

    /** 递归构建网络 ID 参数链的下一层 */
    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> buildNetworkChainRecursive(int current, int max, boolean isManager) {
        if (current > max) return null;
        var builder = Commands.argument("netId" + current, IntegerArgumentType.integer(0, 9999))
                .executes(ctx -> executeAddMembers(ctx, isManager));
        var next = buildNetworkChainRecursive(current + 1, max, isManager);
        if (next != null) builder = builder.then(next);
        return builder;
    }

    /** 未指定网络时执行：将玩家添加到执行者的主要网络 */
    private static int executeAddMembersToDefault(CommandContext<CommandSourceStack> ctx, boolean isManager) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkServerAvailable(source)) return 0;
        ServerPlayer executor = source.getPlayer();
        if (executor == null) { source.sendFailure(OutputFormatter.createError("error.player_required")); return 0; }
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "players");
        if (targets.isEmpty()) { source.sendFailure(Component.literal(CommandLang.get("network.batchAdd.no_players"))); return 0; }
        DimensionsNet primaryNet = DimensionsNet.getPrimaryNetFromPlayer(executor);
        if (primaryNet == null) { source.sendFailure(OutputFormatter.createError("error.not_in_network")); return 0; }
        int netId = primaryNet.getId();
        if (!PermissionChecker.checkNetworkManagementPermission(source, primaryNet, executor)) return 0;
        int[] sc = {0};
        StringBuilder[] ap = {new StringBuilder()};
        for (ServerPlayer tp : targets) {
            if (addPlayerToNetwork(source, primaryNet, tp, isManager)) {
                sc[0]++;
                if (ap[0].length() > 0) ap[0].append(", ");
                ap[0].append(tp.getGameProfile().getName());
            }
        }
        String role = isManager ? CommandLang.get("network.myNetworks.permission.manager") : CommandLang.get("network.myNetworks.permission.member");
        if (sc[0] > 0) {
            int fi = sc[0]; int ti = targets.size(); int ni = netId; String rn = role;
            source.sendSuccess(() -> Component.literal(CommandLang.get("network.batchAddPlayer.success", fi, ti, rn, ni) + (ap[0].length() > 0 ? " (" + ap[0].toString() + ")" : "")), false);
        } else {
            source.sendFailure(Component.literal(CommandLang.get("network.batchAddPlayer.failed", role, netId)));
        }
        return sc[0];
    }

    /** 指定网络时执行：将玩家批量添加到全部指定的网络（逐一校验权限） */
    private static int executeAddMembers(CommandContext<CommandSourceStack> ctx, boolean isManager) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkServerAvailable(source)) return 0;
        ServerPlayer executor = source.getPlayer();
        if (executor == null) { source.sendFailure(OutputFormatter.createError("error.player_required")); return 0; }
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "players");
        if (targets.isEmpty()) { source.sendFailure(Component.literal(CommandLang.get("network.batchAdd.no_players"))); return 0; }
        Set<Integer> networkIds = new HashSet<>();
        for (int i = 1; i <= 5; i++) {
            try { networkIds.add(IntegerArgumentType.getInteger(ctx, "netId" + i)); } catch (IllegalArgumentException e) { break; }
        }
        if (networkIds.isEmpty()) { source.sendFailure(Component.literal(CommandLang.get("network.batchAddPlayer.no_networks"))); return 0; }
        int[] sc = {0};
        int totalOps = targets.size() * networkIds.size();
        StringBuilder[] ap = {new StringBuilder()};
        for (int netId : networkIds) {
            DimensionsNet net = PermissionChecker.checkNetworkExists(source, netId);
            if (net == null) continue;
            if (!PermissionChecker.checkNetworkManagementPermission(source, net, executor)) continue;
            for (ServerPlayer tp : targets) {
                if (addPlayerToNetwork(source, net, tp, isManager)) {
                    sc[0]++;
                    if (ap[0].length() > 0) ap[0].append(", ");
                    ap[0].append(tp.getGameProfile().getName());
                }
            }
        }
        String role = isManager ? CommandLang.get("network.myNetworks.permission.manager") : CommandLang.get("network.myNetworks.permission.member");
        if (sc[0] > 0) {
            int fi = sc[0]; int ti = totalOps; String rn = role;
            source.sendSuccess(() -> Component.literal(CommandLang.get("network.batchAddToNetworks.success", fi, ti, rn) + (ap[0].length() > 0 ? " (" + ap[0].toString() + ")" : "")), false);
        } else {
            source.sendFailure(Component.literal(CommandLang.get("network.batchAdd.failed", role)));
        }
        return sc[0];
    }

    /** 将单个玩家添加到网络；已是成员时仅按需补充管理员身份，返回是否发生添加 */
    private static boolean addPlayerToNetwork(CommandSourceStack source, DimensionsNet net, ServerPlayer player, boolean isManager) {
        try {
            UUID puid = player.getUUID();
            if (net.getPlayers().contains(puid)) {
                if (isManager && !net.isManager(puid)) { net.addManager(puid); net.setDirty(); return true; }
                return false;
            }
            net.addPlayer(puid);
            if (isManager) net.addManager(puid);
            net.setDirty();
            return true;
        } catch (Exception e) {
            source.sendFailure(CommandLang.component("error.add_player_failed", e.getMessage()));
            return false;
        }
    }
}

