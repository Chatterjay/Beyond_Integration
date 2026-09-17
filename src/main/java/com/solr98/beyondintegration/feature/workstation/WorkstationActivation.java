package com.solr98.beyondintegration.feature.workstation;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.handler.WorkstationActivationAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 工作台献祭激活（可选平衡项，网络级）。
 * <p>
 * 开启后，除合成台（craft）与 BD 终端（storage）外的工作台需先献祭对应原版工作台物品激活；
 * 激活状态按网络持久化于 {@code NetworkAmmoData}，一次激活后全网络成员可用。
 * 未激活的工作台在 BD 终端点击工作站按钮时不会打开，而是尝试献祭（扣除网络物品）。
 */
public final class WorkstationActivation {

    /** 参与献祭激活的工作台 ID（storage=BD 终端、craft=合成台 不参与） */
    private static final Set<String> ACTIVATABLE = Set.of("anvil", "cut", "grind", "smith", "enchant");

    /** 激活尝试结果 */
    public enum Result { SUCCESS, ALREADY, DISABLED, INVALID, NO_NETWORK, NO_ITEM }

    private WorkstationActivation() {}

    /** 该工作台 ID 是否参与献祭激活 */
    public static boolean isActivatable(String id) {
        return id != null && ACTIVATABLE.contains(id.toLowerCase(Locale.ROOT));
    }

    /** 该网络是否已激活指定工作台（非可激活工作台视为已激活） */
    public static boolean isActivated(DimensionsNet net, String id) {
        if (!isActivatable(id)) return true;
        return net instanceof WorkstationActivationAccessor acc
                && acc.beyond$isWorkstationActivated(id.toLowerCase(Locale.ROOT));
    }

    /** 该网络已激活的工作台 ID 列表 */
    public static List<String> activatedIds(DimensionsNet net) {
        List<String> out = new ArrayList<>();
        if (net instanceof WorkstationActivationAccessor acc) {
            for (String id : ACTIVATABLE) {
                if (acc.beyond$isWorkstationActivated(id)) out.add(id);
            }
        }
        return out;
    }

    /** 献祭成本物品（未配置返回 EMPTY，视为无需献祭） */
    public static ItemStack costOf(String id) {
        return CommandConfig.getWorkstationActivationCost(id == null ? null : id.toLowerCase(Locale.ROOT));
    }

    /** 献祭成本显示名（用于提示文本/tooltip；未配置返回空组件） */
    public static Component costName(String id) {
        ItemStack cost = costOf(id);
        if (cost.isEmpty()) return Component.empty();
        if (cost.getCount() <= 1) return cost.getHoverName();
        return Component.literal(cost.getCount() + "x ").append(cost.getHoverName());
    }

    /**
     * 尝试献祭激活：从网络存储扣除成本物品并激活（网络级）。
     * 成本未配置时直接激活（视为无需献祭）。
     */
    public static Result tryActivate(ServerPlayer player, String id) {
        if (!CommandConfig.isWorkstationActivationEnabled()) return Result.DISABLED;
        if (!isActivatable(id)) return Result.INVALID;
        String key = id.toLowerCase(Locale.ROOT);
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) return Result.NO_NETWORK;
        if (!(net instanceof WorkstationActivationAccessor acc)) return Result.DISABLED;
        if (acc.beyond$isWorkstationActivated(key)) return Result.ALREADY;

        ItemStack cost = costOf(key);
        if (!cost.isEmpty()) {
            ItemStackKey costKey = new ItemStackKey(cost.copyWithCount(1));
            KeyAmount extracted = net.getUnifiedStorage().extract(costKey, cost.getCount(), false, false);
            if (extracted.amount() < cost.getCount()) {
                // 提取不足：把已提取的部分还回网络，避免误扣
                if (extracted.amount() > 0) {
                    net.getUnifiedStorage().insert(costKey, extracted.amount(), false);
                }
                return Result.NO_ITEM;
            }
        }
        acc.beyond$activateWorkstation(key);
        return Result.SUCCESS;
    }

    /**
     * 重置该网络的工作台激活状态（管理命令用）。
     *
     * @param id 工作台 ID；{@code null} 表示全部重置
     * @return 实际被重置的工作台 ID 列表
     */
    public static List<String> reset(DimensionsNet net, String id) {
        List<String> removed = new ArrayList<>();
        if (!(net instanceof WorkstationActivationAccessor acc)) return removed;
        if (id == null) {
            for (String wsId : ACTIVATABLE) {
                if (acc.beyond$isWorkstationActivated(wsId)) {
                    acc.beyond$resetWorkstation(wsId);
                    removed.add(wsId);
                }
            }
        } else {
            String key = id.toLowerCase(Locale.ROOT);
            if (isActivatable(key) && acc.beyond$isWorkstationActivated(key)) {
                acc.beyond$resetWorkstation(key);
                removed.add(key);
            }
        }
        return removed;
    }
}
