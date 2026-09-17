package com.solr98.beyondintegration.command.util;

import com.solr98.beyondintegration.command.CommandLang;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * 网络权限等级枚举：定义无权限/成员/管理员/所有者四个级别，
 * 每个级别关联本地化显示文本键与展示颜色，并支持从玩家与网络
 * 关系解析对应权限。
 */
public enum NetworkPermission {
    NONE("none", "network.info.no_permission", ChatFormatting.GRAY),
    MEMBER("member", "network.myNetworks.permission.member", ChatFormatting.GREEN),
    MANAGER("manager", "network.myNetworks.permission.manager", ChatFormatting.BLUE),
    OWNER("owner", "network.myNetworks.permission.owner", ChatFormatting.RED);

    /** 内部标识键。 */
    private final String key;
    /** 本地化显示文本键。 */
    private final String displayKey;
    /** 展示颜色。 */
    private final ChatFormatting color;

    NetworkPermission(String key, String displayKey, ChatFormatting color) {
        this.key = key;
        this.displayKey = displayKey;
        this.color = color;
    }

    /** 返回内部标识键。 */
    public String getKey() { return key; }
    /** 返回本地化后的权限名称。 */
    public String getDisplay() { return CommandLang.get(displayKey); }
    /** 返回权限对应的展示颜色。 */
    public ChatFormatting getColor() { return color; }

    /** 根据玩家在网络中的角色解析其权限等级（无关系则返回 NONE）。 */
    public static NetworkPermission fromPlayer(ServerPlayer player, DimensionsNet net) {
        if (net == null || player == null) return NONE;
        UUID id = player.getUUID();
        if (net.isOwner(id)) return OWNER;
        if (net.isManager(id)) return MANAGER;
        if (net.getPlayers().contains(id)) return MEMBER;
        return NONE;
    }
}

