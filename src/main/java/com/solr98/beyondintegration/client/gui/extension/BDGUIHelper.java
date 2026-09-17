package com.solr98.beyondintegration.client.gui.extension;

import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;

/**
 * BD 界面辅助工具（扩展专用）。
 * 提供 Shift 状态读取与长数字紧凑格式化（K/M/B）两个静态方法。
 */
public final class BDGUIHelper {
    /** 读取 BD 菜单的 Shift 按下状态（反射访问菜单字段，失败兜底 false） */
    public static boolean isShiftDown(DimensionsNetGUI<?> gui) {
        try { var menu = gui.getMenu(); if (menu instanceof DimensionsNetMenu) return ((DimensionsNetMenu) menu).hasShiftDown; } catch (Exception ignored) {}
        return false;
    }

    /** 长数字紧凑格式化：>=1000 转 K、>=1M 转 M、>=1B 转 B（保留一位小数） */
    public static String compactFormat(long value) {
        if (value >= 1_000_000_000L) return String.format("%.1fB", value / 1_000_000_000.0);
        if (value >= 1_000_000L)     return String.format("%.1fM", value / 1_000_000.0);
        if (value >= 1_000L)         return String.format("%.1fK", value / 1_000.0);
        return String.valueOf(value);
    }
}

