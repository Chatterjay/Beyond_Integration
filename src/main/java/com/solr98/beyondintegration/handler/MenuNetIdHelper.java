package com.solr98.beyondintegration.handler;

import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import com.wintercogs.beyonddimensions.common.menu.NetControlMenu;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * 菜单网络 ID 辅助工具：根据玩家当前打开的容器菜单
 * 推导其关联的维度网络。
 */
public class MenuNetIdHelper {

    /** 从玩家当前菜单获取关联网络（无相关菜单返回 null） */
    @Nullable
    public static DimensionsNet getNetFromMenu(ServerPlayer player) {
        var menu = player.containerMenu;
        if (menu == null) return null;

        // 维度网络存储菜单：直接取存储所属网络
        if (menu instanceof DimensionsNetMenu dnm && dnm.storage instanceof UnifiedStorage us) {
            return us.getNet();
        }

        // 网络控制菜单：取玩家当前主网络
        if (menu instanceof NetControlMenu) {
            return DimensionsNet.getNetFromPlayer(player);
        }

        return null;
    }
}
