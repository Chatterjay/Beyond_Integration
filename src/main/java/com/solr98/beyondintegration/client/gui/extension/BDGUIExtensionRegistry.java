package com.solr98.beyondintegration.client.gui.extension;

import com.solr98.beyondintegration.api.IDimensionsNetGUIExtension;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * BD 界面扩展注册中心。
 * 维护 IDimensionsNetGUIExtension 扩展列表（按优先级排序），
 * 并向扩展提供 BD 界面槽位 Y 坐标换算工具。
 */
public final class BDGUIExtensionRegistry {
    public static final int BD_BUTTON_COUNT = 8; // BD 原有按钮数量（用于槽位坐标偏移）
    private static final List<IDimensionsNetGUIExtension> EXTENSIONS = new ArrayList<>(); // 已注册扩展列表
    private static boolean registered; // 是否已初始化（防重复注册）

    /** 确保扩展只注册一次：注册物品保护扩展（存储打开按钮已移除） */
    public static void ensureRegistered() {
        if (registered) return;
        registered = true;
        register(new ItemProtectExtension());
        // EnchantToggleBtn and ammo panel are handled by DimensionsNetGUIMixin directly
    }

    /** 注册扩展并按优先级升序排序 */
    public static void register(IDimensionsNetGUIExtension ext) {
        EXTENSIONS.add(ext);
        EXTENSIONS.sort(Comparator.comparingInt(IDimensionsNetGUIExtension::priority));
    }

    /** 获取已注册扩展列表（已排序） */
    public static List<IDimensionsNetGUIExtension> getExtensions() { return EXTENSIONS; }

    /** 计算 BD 网络槽位在界面中的 Y 坐标（BD 按钮下方起排） */
    public static int getSlotY(int guiTop, int slot) {
        return guiTop + 6 + 18 * (BD_BUTTON_COUNT + slot);
    }
}

