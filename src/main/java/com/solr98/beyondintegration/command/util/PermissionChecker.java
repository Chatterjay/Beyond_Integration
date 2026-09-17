package com.solr98.beyondintegration.command.util;

import com.solr98.beyondintegration.command.CommandLang;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

/**
 * 命令权限与前置条件校验工具类：集中提供服务器可用性、玩家来源、
 * OP 权限、网络存在性、网络访问/管理权限、归属校验及参数合法性等
 * 各类检查，检查失败时自动向命令发送者输出错误提示。
 */
public class PermissionChecker {

    /** 检查命令源关联的服务器可用（不可用时发送错误并返回 false）。 */
    public static boolean checkServerAvailable(CommandSourceStack s) {
        if (s.getServer() == null) {
            s.sendFailure(CommandLang.component("error.server_not_available"));
            return false;
        }
        return true;
    }

    /** 获取命令执行者对应的服务器玩家，非玩家执行时返回 null 并提示。 */
    public static ServerPlayer checkPlayer(CommandSourceStack source) {
        try {
            return source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(CommandLang.component("error.player_required"));
            return null;
        }
    }

    /** 检查命令源是否具备 OP 权限。 */
    public static boolean checkOpPermission(CommandSourceStack source) {
        if (!CommandUtils.hasOpPermission(source)) {
            source.sendFailure(CommandLang.component("error.op_required"));
            return false;
        }
        return true;
    }

    /** 操作他人对象时校验 OP 权限（非 OP 仅能操作自己）。 */
    public static boolean checkOpPermissionForOthers(CommandSourceStack source, ServerPlayer targetPlayer) {
        if (!CommandUtils.hasOpPermission(source) && !source.getPlayer().getUUID().equals(targetPlayer.getUUID())) {
            source.sendFailure(CommandLang.component("error.op_required_for_others"));
            return false;
        }
        return true;
    }

    /** 按 ID 查找网络，不存在时发送错误并返回 null。 */
    public static DimensionsNet checkNetworkExists(CommandSourceStack source, int netId) {
        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) {
            source.sendFailure(CommandLang.component("error.network_not_found"));
            return null;
        }
        return net;
    }

    /** 校验玩家对网络的访问（浏览）权限，委托 CommandUtils。 */
    public static boolean checkNetworkAccessPermission(CommandSourceStack source, DimensionsNet net, ServerPlayer player) {
        return CommandUtils.checkNetworkAccessPermission(source, net, player);
    }

    /** 校验玩家对网络的管理权限，委托 CommandUtils。 */
    public static boolean checkNetworkManagementPermission(CommandSourceStack source, DimensionsNet net, ServerPlayer player) {
        return CommandUtils.checkNetworkManagementPermission(source, net, player);
    }

    /** 校验玩家是否为网络所有者。 */
    public static boolean checkOwner(CommandSourceStack s, DimensionsNet net, ServerPlayer p) {
        if (net.isOwner(p.getUUID())) return true;
        s.sendFailure(CommandLang.component("network.batchAdd.error.owner_required", net.getId()));
        return false;
    }

    /** 校验玩家是否为网络所有者或管理员。 */
    public static boolean checkOwnerOrManager(CommandSourceStack s, DimensionsNet net, ServerPlayer p) {
        UUID id = p.getUUID();
        if (net.isOwner(id) || net.isManager(id)) return true;
        s.sendFailure(CommandLang.component("network.batchAdd.error.owner_or_manager_required", net.getId()));
        return false;
    }

    /** 校验添加管理员权限：OP 或网络所有者可操作。 */
    public static boolean checkAddManagerPermission(CommandSourceStack source, DimensionsNet net, ServerPlayer player) {
        if (CommandUtils.hasOpPermission(source)) return true;
        if (!CommandUtils.isNetworkOwner(player, net)) {
            source.sendFailure(CommandLang.component("network.batchAdd.error.owner_required", net.getId()));
            return false;
        }
        return true;
    }

    /** 校验添加成员权限：OP 或网络管理级别以上可操作。 */
    public static boolean checkAddMemberPermission(CommandSourceStack source, DimensionsNet net, ServerPlayer player) {
        if (CommandUtils.hasOpPermission(source)) return true;
        if (!CommandUtils.hasNetworkManagementPermission(player, net)) {
            source.sendFailure(CommandLang.component("network.batchAdd.error.owner_or_manager_required", net.getId()));
            return false;
        }
        return true;
    }

    /** 移除成员权限校验：与添加成员权限规则一致。 */
    public static boolean checkRemoveMemberPermission(CommandSourceStack source, DimensionsNet net, ServerPlayer player) {
        return checkAddMemberPermission(source, net, player);
    }

    /** 校验网络 ID 是否在合法范围内（0~9999）。 */
    public static boolean checkNetworkIdValid(CommandSourceStack source, int netId) {
        if (!CommandUtils.isValidNetworkId(netId)) {
            source.sendFailure(Component.literal("Invalid network ID: " + netId));
            return false;
        }
        return true;
    }

    /** 校验数量参数必须为正数。 */
    public static boolean checkAmountPositive(CommandSourceStack source, long amount) {
        if (amount <= 0) {
            source.sendFailure(CommandLang.component("error.amount_must_be_positive"));
            return false;
        }
        return true;
    }

    /** 校验玩家列表非空。 */
    public static boolean checkPlayerListNotEmpty(CommandSourceStack source, List<ServerPlayer> players) {
        if (players == null || players.isEmpty()) {
            source.sendFailure(CommandLang.component("network.batchAdd.no_players"));
            return false;
        }
        return true;
    }

    /** 校验网络 ID 列表非空。 */
    public static boolean checkNetworkListNotEmpty(CommandSourceStack source, List<Integer> netIds) {
        if (netIds == null || netIds.isEmpty()) {
            source.sendFailure(CommandLang.component("network.batchAddPlayer.no_networks"));
            return false;
        }
        return true;
    }

    /** 校验资源类型参数（items/fluids/energy/mixed/all）是否合法。 */
    public static boolean checkResourceTypeValid(CommandSourceStack source, String resourceType) {
        String[] valid = {"items", "fluids", "energy", "mixed", "all"};
        for (String v : valid) if (v.equalsIgnoreCase(resourceType)) return true;
        source.sendFailure(CommandLang.component("error.invalid_resource_type", resourceType));
        return false;
    }
}

