package com.solr98.beyondintegration.client.gui;

import com.solr98.beyondintegration.ClientConfig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 工作站模式常量定义。
 * 定义工作站模式枚举（含存储）与模式切换按钮的
 * 图标、位置坐标等界面常量；提供默认全序与客户端可配置顺序的解析。
 */
public class WorkstationModeConstants {
    /** 工作站模式枚举（STORAGE 为存储界面，其余为各工作站） */
    public enum Mode { STORAGE, ANVIL, CUT, GRIND, SMITH, CRAFT, ENCHANT }

    /** 可切换的工作站模式（默认顺序，不含存储） */
    public static final Mode[] MODES = { Mode.ANVIL, Mode.CUT, Mode.GRIND, Mode.SMITH, Mode.CRAFT, Mode.ENCHANT };
    /** 各模式按钮 X 坐标 */
    public static final int[] MX = {177, 177, 177, 177, 177, 177};
    /** 各模式按钮 Y 坐标（自顶部起，位置索引 i → 0,16,31,46,61,76；重排/隐藏后仍按索引取值保持紧凑） */
    public static final int[] MY = {0, 16, 31, 46, 61, 76};
    /** 各模式按钮图标物品（默认顺序） */
    public static final ItemStack[] ICONS = {
        new ItemStack(Items.ANVIL), new ItemStack(Items.STONECUTTER),
        new ItemStack(Items.GRINDSTONE), new ItemStack(Items.SMITHING_TABLE),
        new ItemStack(Items.CRAFTING_TABLE), new ItemStack(Items.ENCHANTING_TABLE)
    };

    // Mode → 图标映射（供配置重排后按模式取图标）
    private static final Map<Mode, ItemStack> ICON_BY_MODE = new EnumMap<>(Mode.class);
    static {
        for (int i = 0; i < MODES.length; i++) {
            ICON_BY_MODE.put(MODES[i], ICONS[i]);
        }
    }

    private WorkstationModeConstants() {}

    /**
     * 客户端配置的右侧工作站按钮顺序/可见集。
     * 从 ClientConfig.workstationOrder 解析（无效/重复项忽略，STORAGE 恒排除）；
     * 空结果回退默认全序。
     */
    public static List<Mode> configuredModes() {
        List<? extends String> raw = ClientConfig.workstationOrder();
        if (raw == null || raw.isEmpty()) return List.of(MODES);
        LinkedHashSet<Mode> set = new LinkedHashSet<>();
        for (String s : raw) {
            if (s == null) continue;
            try {
                Mode m = Mode.valueOf(s.trim().toUpperCase(Locale.ROOT));
                if (m != Mode.STORAGE) set.add(m);
            } catch (IllegalArgumentException ignored) {
                // 无效模式名忽略
            }
        }
        return set.isEmpty() ? List.of(MODES) : new ArrayList<>(set);
    }

    /**
     * 客户端配置顺序 ∩ 服务端可用列表（workstations.enabled）：
     * 服务端禁用的模式直接从按钮序列中移除（紧凑排列，不留空位）。
     */
    public static List<Mode> availableModes() {
        List<Mode> base = configuredModes();
        List<Mode> out = new ArrayList<>(base.size());
        for (Mode m : base) {
            if (com.solr98.beyondintegration.CommandConfig.isWorkstationEnabled(m.name().toLowerCase(Locale.ROOT))) {
                out.add(m);
            }
        }
        return out;
    }

    /** 按模式取切换按钮图标（配置重排后仍对应正确物品） */
    public static ItemStack iconFor(Mode mode) {
        ItemStack icon = ICON_BY_MODE.get(mode);
        return icon != null ? icon : new ItemStack(Items.BARRIER);
    }

    /** 位置索引 i 的按钮 X（防越界） */
    public static int xFor(int index) { return MX[Math.min(index, MX.length - 1)]; }

    /** 位置索引 i 的按钮 Y（防越界） */
    public static int yFor(int index) { return MY[Math.min(index, MY.length - 1)]; }
}