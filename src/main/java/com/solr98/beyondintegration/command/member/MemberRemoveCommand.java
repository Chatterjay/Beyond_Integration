package com.solr98.beyondintegration.command.member;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.solr98.beyondintegration.command.CommandLang;
import com.solr98.beyondintegration.command.util.*;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;

import java.util.Collection;
import java.util.UUID;

/**
 * 成员移除命令
 * 功能：从网络移除成员或管理员
 */
public class MemberRemoveCommand {
    
    /**
     * 注册移除玩家命令
     */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> registerRemovePlayers() {
        return Commands.literal("removePlayers")
            // 默认：从当前玩家的主要网络移除
            .executes(ctx -> {
                ctx.getSource().sendFailure(CommandLang.component("error.players_required"));
                return 0;
            })
            // 指定网络ID
            .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                .executes(ctx -> {
                    ctx.getSource().sendFailure(CommandLang.component("error.players_required"));
                    return 0;
                })
                .then(Commands.argument("players", EntityArgument.players())
                    .executes(ctx -> executeRemovePlayers(ctx, false))));
    }
    
    /**
     * 注册移除管理员命令
     */
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> registerRemoveManagers() {
        return Commands.literal("removeManagers")
            // 默认：从当前玩家的主要网络移除
            .executes(ctx -> {
                ctx.getSource().sendFailure(CommandLang.component("error.players_required"));
                return 0;
            })
            // 指定网络ID
            .then(Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                .executes(ctx -> {
                    ctx.getSource().sendFailure(CommandLang.component("error.players_required"));
                    return 0;
                })
                .then(Commands.argument("players", EntityArgument.players())
                    .executes(ctx -> executeRemovePlayers(ctx, true))));
    }
    
    /**
     * 执行移除玩家命令
     */
    public static int executeRemovePlayers(CommandContext<CommandSourceStack> ctx, boolean removeManagers) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        
        // 检查服务器是否可用
        if (!PermissionChecker.checkServerAvailable(source)) {
            return 0;
        }
        
        // 获取执行者玩家
        ServerPlayer executor = source.getPlayer();
        if (executor == null) {
            source.sendFailure(OutputFormatter.createError("error.player_required"));
            return 0;
        }
        
        // 获取网络ID
        int netId = IntegerArgumentType.getInteger(ctx, "netId");
        
        // 检查网络是否存在
        DimensionsNet net = PermissionChecker.checkNetworkExists(source, netId);
        if (net == null) {
            return 0;
        }
        
        // 检查执行者权限（必须是网络所有者或管理员）
        if (!PermissionChecker.checkNetworkManagementPermission(source, net, executor)) {
            return 0;
        }
        
        // 获取目标玩家
        Collection<ServerPlayer> targetPlayers = EntityArgument.getPlayers(ctx, "players");
        if (targetPlayers.isEmpty()) {
            source.sendFailure(Component.literal(CommandLang.get("network.batchRemove.no_players")));
            return 0;
        }
        
        // 移除每个玩家
        final int[] successCount = {0};
        final StringBuilder[] removedPlayers = {new StringBuilder()};
        
        for (ServerPlayer targetPlayer : targetPlayers) {
            if (removePlayerFromNetwork(source, net, targetPlayer, removeManagers)) {
                successCount[0]++;
                if (removedPlayers[0].length() > 0) {
                    removedPlayers[0].append(", ");
                }
                removedPlayers[0].append(targetPlayer.getGameProfile().getName());
            }
        }
        
        // 发送结果消息
        String roleName = removeManagers ? 
                CommandLang.get("network.myNetworks.permission.manager") :
                CommandLang.get("network.myNetworks.permission.member");
        
        if (successCount[0] > 0) {
            source.sendSuccess(() -> Component.literal(
                CommandLang.get("network.batchRemove.success", 
                    successCount[0], netId, roleName) + 
                (removedPlayers[0].length() > 0 ? " (" + removedPlayers[0].toString() + ")" : "")
            ), false);
        } else {
            source.sendFailure(Component.literal(
                CommandLang.get("network.batchRemove.failed", roleName, netId)
            ));
        }
        
        return successCount[0];
    }
    
    /**
     * 从网络移除玩家
     */
    private static boolean removePlayerFromNetwork(CommandSourceStack source, DimensionsNet net, 
                                                  ServerPlayer player, boolean removeManagers) {
        try {
            UUID playerUuid = player.getUUID();
            
            // 检查玩家是否在网络中
            if (!net.getPlayers().contains(playerUuid)) {
                // 玩家不在网络中
                return false;
            }
            
            // 检查是否是网络所有者（不能移除所有者）
            if (net.isOwner(playerUuid)) {
                source.sendFailure(CommandLang.component("error.cannot_remove_owner"));
                return false;
            }
            
            boolean removed = false;
            
            // 如果是移除管理员
            if (removeManagers) {
                // 检查玩家是否是管理员
                if (net.isManager(playerUuid)) {
                    net.removeManager(playerUuid);
                    removed = true;
                }
            } else {
                // 移除普通成员（如果是管理员，先移除管理员身份）
                if (net.isManager(playerUuid)) {
                    net.removeManager(playerUuid);
                }
                // 从网络移除玩家
                net.removePlayer(playerUuid);
                removed = true;
            }
            
            if (removed) {
                net.setDirty();
            }
            
            return removed;
            
        } catch (Exception e) {
            source.sendFailure(CommandLang.component("error.remove_player_failed", e.getMessage()));
            return false;
        }
    }
}