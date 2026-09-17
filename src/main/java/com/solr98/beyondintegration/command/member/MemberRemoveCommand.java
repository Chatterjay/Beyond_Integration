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

import java.util.Collection;
import java.util.UUID;

/**
 * 成员移除命令：提供 /bdtools member removePlayers / removeManagers，
 * 将指定玩家从网络的成员或管理员列表中移除，并进行权限校验。
 */
public class MemberRemoveCommand {

    /** 注册 removePlayers 命令（移除成员） */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> registerRemovePlayers() {
        return Commands.literal("removePlayers")
                .executes(ctx -> { ctx.getSource().sendFailure(CommandLang.component("error.players_required")); return 0; })
                .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> { ctx.getSource().sendFailure(CommandLang.component("error.players_required")); return 0; })
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(ctx -> executeRemovePlayers(ctx, false))));
    }

    /** 注册 removeManagers 命令（移除管理员） */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> registerRemoveManagers() {
        return Commands.literal("removeManagers")
                .executes(ctx -> { ctx.getSource().sendFailure(CommandLang.component("error.players_required")); return 0; })
                .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                        .executes(ctx -> { ctx.getSource().sendFailure(CommandLang.component("error.players_required")); return 0; })
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(ctx -> executeRemovePlayers(ctx, true))));
    }

    /** 执行批量移除：校验权限后逐个移除目标玩家，返回成功移除数量 */
    public static int executeRemovePlayers(CommandContext<CommandSourceStack> ctx, boolean removeManagers) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkServerAvailable(source)) return 0;

        ServerPlayer executor = source.getPlayer();
        if (executor == null) { source.sendFailure(OutputFormatter.createError("error.player_required")); return 0; }

        int netId = IntegerArgumentType.getInteger(ctx, "netId");
        DimensionsNet net = PermissionChecker.checkNetworkExists(source, netId);
        if (net == null) return 0;

        if (!PermissionChecker.checkNetworkManagementPermission(source, net, executor)) return 0;

        Collection<ServerPlayer> targetPlayers = EntityArgument.getPlayers(ctx, "players");
        if (targetPlayers.isEmpty()) { source.sendFailure(Component.literal(CommandLang.get("network.batchRemove.no_players"))); return 0; }

        int[] successCount = {0};
        StringBuilder[] removedPlayers = {new StringBuilder()};

        for (ServerPlayer tp : targetPlayers) {
            if (removePlayerFromNetwork(source, net, tp, removeManagers)) {
                successCount[0]++;
                if (removedPlayers[0].length() > 0) removedPlayers[0].append(", ");
                removedPlayers[0].append(tp.getGameProfile().getName());
            }
        }

        String roleName = removeManagers ? CommandLang.get("network.myNetworks.permission.manager") : CommandLang.get("network.myNetworks.permission.member");
        if (successCount[0] > 0) {
            String finalRole = roleName; int finalCount = successCount[0];
            source.sendSuccess(() -> Component.literal(CommandLang.get("network.batchRemove.success", finalCount, netId) + (removedPlayers[0].length() > 0 ? " (" + removedPlayers[0].toString() + ")" : "")), false);
        } else {
            source.sendFailure(Component.literal(CommandLang.get("network.batchRemove.failed", roleName, netId)));
        }
        return successCount[0];
    }

    /** 从网络移除单个玩家；removeManagers 为 true 时仅移除管理员身份，否则移除成员（若为管理员则一并移除） */
    private static boolean removePlayerFromNetwork(CommandSourceStack source, DimensionsNet net, ServerPlayer player, boolean removeManagers) {
        try {
            UUID puid = player.getUUID();
            if (!net.getPlayers().contains(puid)) return false;
            if (net.isOwner(puid)) {
                source.sendFailure(CommandLang.component("error.cannot_remove_owner"));
                return false;
            }
            boolean removed = false;
            if (removeManagers) {
                if (net.isManager(puid)) { net.removeManager(puid); removed = true; }
            } else {
                if (net.isManager(puid)) net.removeManager(puid);
                net.removePlayer(puid);
                removed = true;
            }
            if (removed) net.setDirty();
            return removed;
        } catch (Exception e) {
            source.sendFailure(CommandLang.component("error.remove_player_failed", e.getMessage()));
            return false;
        }
    }
}

