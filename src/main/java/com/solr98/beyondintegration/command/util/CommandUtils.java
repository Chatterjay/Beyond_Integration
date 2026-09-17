package com.solr98.beyondintegration.command.util;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.solr98.beyondintegration.command.CommandLang;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.util.PlayerNameHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 命令通用工具类：提供执行者解析、OP 权限判定、网络归属/管理权限检查、
 * 玩家名称解析、大数格式化、物品名称获取及网络 ID/玩家名合法性校验等
 * 命令层共用的工具方法。
 */
public class CommandUtils {

    /** 操作网络所需的 OP 权限等级。 */
    public static final int OP_LEVEL = 2;

    /** 获取命令执行者对应的服务器玩家（无则抛出命令异常）。 */
    public static ServerPlayer getExecutorPlayer(CommandSourceStack source) throws CommandSyntaxException {
        return source.getPlayerOrException();
    }

    /** 判断命令源是否具备 OP 权限。 */
    public static boolean hasOpPermission(CommandSourceStack source) {
        return source.hasPermission(OP_LEVEL);
    }

    /** 判断玩家是否为网络成员。 */
    public static boolean isPlayerInNetwork(ServerPlayer player, DimensionsNet net) {
        return net != null && net.getPlayers().contains(player.getUUID());
    }

    /** 判断玩家是否为网络所有者。 */
    public static boolean isNetworkOwner(ServerPlayer player, DimensionsNet net) {
        return net != null && net.isOwner(player.getUUID());
    }

    /** 判断玩家是否为网络管理员。 */
    public static boolean isNetworkManager(ServerPlayer player, DimensionsNet net) {
        return net != null && net.isManager(player.getUUID());
    }

    /** 判断玩家是否具备网络管理权限（所有者或管理员）。 */
    public static boolean hasNetworkManagementPermission(ServerPlayer player, DimensionsNet net) {
        return isNetworkOwner(player, net) || isNetworkManager(player, net);
    }

    /** 按 ID 获取网络，不存在时发送错误并返回 null。 */
    public static DimensionsNet getNetOrFail(CommandSourceStack source, int netId) {
        DimensionsNet net = DimensionsNet.getNetFromId(netId);
        if (net == null) {
            source.sendFailure(CommandLang.component("error.network_not_found"));
            return null;
        }
        return net;
    }

    /** 校验网络访问（浏览）权限：OP 或网络成员可访问。 */
    public static boolean checkNetworkAccessPermission(CommandSourceStack source, DimensionsNet net, ServerPlayer player) {
        if (hasOpPermission(source)) return true;
        if (!isPlayerInNetwork(player, net)) {
            source.sendFailure(CommandLang.component("network.open.error.no_permission", player.getGameProfile().getName(), net.getId()));
            return false;
        }
        return true;
    }

    /** 校验网络管理权限：OP 或网络所有者/管理员可管理。 */
    public static boolean checkNetworkManagementPermission(CommandSourceStack source, DimensionsNet net, ServerPlayer player) {
        if (hasOpPermission(source)) return true;
        if (!hasNetworkManagementPermission(player, net)) {
            source.sendFailure(CommandLang.component("network.open.error.no_permission_control", player.getGameProfile().getName(), net.getId()));
            return false;
        }
        return true;
    }

    /** 按 UUID 获取玩家名（优先使用 PlayerNameHelper，其次查询服务器）。 */
    public static String getPlayerNameByUUID(UUID playerUuid, MinecraftServer server) {
        if (server != null) {
            return PlayerNameHelper.getPlayerNameByUUID(playerUuid, server);
        }
        return getPlayerNameFromServer(playerUuid, server);
    }

    /** 从服务器在线玩家或皮肤缓存解析玩家名，解析失败返回"未知"。 */
    private static String getPlayerNameFromServer(UUID uuid, MinecraftServer server) {
        if (server == null) return CommandLang.get("network.info.unknown");
        var player = server.getPlayerList().getPlayer(uuid);
        if (player != null) return player.getName().getString();
        var profile = server.getProfileCache().get(uuid);
        return profile.map(p -> p.getName()).orElse(CommandLang.get("network.info.unknown"));
    }

    /** 获取网络所有者的玩家名（无所有者时返回"未知"）。 */
    public static String getNetworkOwnerName(DimensionsNet net, MinecraftServer server) {
        if (net == null || net.getOwner() == null) return CommandLang.get("network.info.unknown");
        return getPlayerNameFromServer(net.getOwner(), server);
    }

    /** 大数格式化：按 K/M/G/T/P/E 单位缩写显示（小于 1000 原样输出）。 */
    public static String formatBigNumber(BigInteger number) {
        if (number.compareTo(BigInteger.valueOf(1_000_000_000_000_000_000L)) >= 0)
            return number.divide(BigInteger.valueOf(1_000_000_000_000_000_000L)) + "E";
        if (number.compareTo(BigInteger.valueOf(1_000_000_000_000_000L)) >= 0)
            return number.divide(BigInteger.valueOf(1_000_000_000_000_000L)) + "P";
        if (number.compareTo(BigInteger.valueOf(1_000_000_000_000L)) >= 0)
            return number.divide(BigInteger.valueOf(1_000_000_000_000L)) + "T";
        if (number.compareTo(BigInteger.valueOf(1_000_000_000L)) >= 0)
            return number.divide(BigInteger.valueOf(1_000_000_000L)) + "G";
        if (number.compareTo(BigInteger.valueOf(1_000_000L)) >= 0)
            return number.divide(BigInteger.valueOf(1_000_000L)) + "M";
        if (number.compareTo(BigInteger.valueOf(1_000L)) >= 0)
            return number.divide(BigInteger.valueOf(1_000L)) + "K";
        return number.toString();
    }

    /** long 数值的大数缩写格式化。 */
    public static String formatLongNumber(long number) {
        return formatBigNumber(BigInteger.valueOf(number));
    }

    /** 获取物品的注册 ID 文本（空物品返回"空"提示，未知物品返回显示名）。 */
    public static String getItemName(ItemStack stack) {
        if (stack.isEmpty()) return CommandLang.get("display.empty_item");
        var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null ? id.toString() : stack.getDisplayName().getString();
    }

    /** 校验网络 ID 是否在合法范围（0~9999）。 */
    public static boolean isValidNetworkId(int netId) {
        return netId >= 0 && netId < 10000;
    }

    /** 过滤出列表中的合法网络 ID。 */
    public static List<Integer> getValidNetworkIds(List<Integer> netIds) {
        List<Integer> valid = new ArrayList<>();
        for (int id : netIds) if (isValidNetworkId(id)) valid.add(id);
        return valid;
    }

    /** 校验玩家名格式是否合法（3~16 位字母数字下划线）。 */
    public static boolean isValidPlayerName(String name) {
        return Pattern.compile("^[a-zA-Z0-9_]{3,16}$").matcher(name).matches();
    }
}

