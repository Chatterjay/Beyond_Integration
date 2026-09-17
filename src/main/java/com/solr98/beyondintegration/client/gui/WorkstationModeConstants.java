package com.solr98.beyondintegration.client.gui;

import com.solr98.beyondintegration.ClientConfig;
import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.network.OpenStorageMenuPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 工作站模式常量：定义各工作站模式对应的网络包类型、图标与按钮坐标；
 * 提供客户端可配置顺序（ClientConfig.workstationOrder）与服务端可用列表
 * （CommandConfig workstations.enabled）的解析与求交。
 */
public class WorkstationModeConstants {
    /** 各工作站模式的网络包类型（铁砧/切割/磨石/锻造/合成/附魔台，默认顺序） */
    public static final OpenStorageMenuPacket.Type[] MODES = {
        OpenStorageMenuPacket.Type.ANVIL, OpenStorageMenuPacket.Type.CUT,
        OpenStorageMenuPacket.Type.GRIND, OpenStorageMenuPacket.Type.SMITH,
        OpenStorageMenuPacket.Type.CRAFT, OpenStorageMenuPacket.Type.ENCHANT
    };
    /** 图标按钮 X 坐标（统一为 177） */
    public static final int[] MX = {177, 177, 177, 177, 177, 177};
    /** 图标按钮 Y 坐标（自上而下：0,16,31,46,61,76；重排/隐藏后仍按索引取值保持紧凑） */
    public static final int[] MY = {0, 16, 31, 46, 61, 76};
    /** 对应工作站的物品图标（铁砧/切石机/磨石/锻造台/工作台/附魔台） */
    public static final ItemStack[] ICONS = {
        new ItemStack(Items.ANVIL), new ItemStack(Items.STONECUTTER),
        new ItemStack(Items.GRINDSTONE), new ItemStack(Items.SMITHING_TABLE),
        new ItemStack(Items.CRAFTING_TABLE), new ItemStack(Items.ENCHANTING_TABLE)
    };

    // Type → 图标映射（供配置重排后按模式取图标）
    private static final Map<OpenStorageMenuPacket.Type, ItemStack> ICON_BY_MODE =
            new EnumMap<>(OpenStorageMenuPacket.Type.class);
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
    public static List<OpenStorageMenuPacket.Type> configuredModes() {
        List<? extends String> raw = ClientConfig.workstationOrder();
        if (raw == null || raw.isEmpty()) return List.of(MODES);
        LinkedHashSet<OpenStorageMenuPacket.Type> set = new LinkedHashSet<>();
        for (String s : raw) {
            if (s == null) continue;
            try {
                OpenStorageMenuPacket.Type t = OpenStorageMenuPacket.Type.valueOf(s.trim().toUpperCase(Locale.ROOT));
                if (t != OpenStorageMenuPacket.Type.STORAGE) set.add(t);
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
    public static List<OpenStorageMenuPacket.Type> availableModes() {
        List<OpenStorageMenuPacket.Type> base = configuredModes();
        List<OpenStorageMenuPacket.Type> out = new ArrayList<>(base.size());
        for (OpenStorageMenuPacket.Type t : base) {
            if (CommandConfig.isWorkstationEnabled(t.id())) out.add(t);
        }
        return out;
    }

    /** 按模式取切换按钮图标（配置重排后仍对应正确物品） */
    public static ItemStack iconFor(OpenStorageMenuPacket.Type mode) {
        ItemStack icon = ICON_BY_MODE.get(mode);
        return icon != null ? icon : new ItemStack(Items.BARRIER);
    }

    /** 位置索引 i 的按钮 X（防越界） */
    public static int xFor(int index) { return MX[Math.min(index, MX.length - 1)]; }

    /** 位置索引 i 的按钮 Y（防越界） */
    public static int yFor(int index) { return MY[Math.min(index, MY.length - 1)]; }
}
