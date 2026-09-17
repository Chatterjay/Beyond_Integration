package com.solr98.beyondintegration.maid;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.common.init.BDDataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 女仆网络助手：在东方 little maid 模组加载时，为女仆查找其饰品栏中
 * 绑定的便携网络终端，并通过 MaidNetworkCache 做短期缓存。
 */
public class MaidNetworkHelper {
    /** 查找实体（女仆）所绑定的维度网络，结果带缓存；未安装模组或未找到时返回 null */
    public static DimensionsNet findTerminal(LivingEntity entity) {
        if (!ModList.get().isLoaded("touhou_little_maid")) return null;
        var cached = MaidNetworkCache.get(entity);
        if (cached != null) return cached;
        try {
            if (!(entity instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid)) return null;
            DimensionsNet net = scanInv(maid.getMaidBauble());
            if (net != null) return cache(entity, net);
            MaidNetworkCache.remove(maid.getUUID());
        } catch (NoClassDefFoundError ignored) {}
        return null;
    }
    /** 遍历饰品栏，找到第一个携带网络 ID 数据且仍存在的网络 */
    private static DimensionsNet scanInv(IItemHandler inv) {
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack st = inv.getStackInSlot(i);
            if (!st.isEmpty()) {
                int id = st.getOrDefault(BDDataComponents.NET_ID_DATA.get(), -1);
                if (id >= 0) { DimensionsNet n = DimensionsNet.getNetFromId(id); if (n != null) return n; }
            }
        }
        return null;
    }
    /** 将实体与其网络 ID 写入缓存并返回网络 */
    private static DimensionsNet cache(LivingEntity e, DimensionsNet n) { MaidNetworkCache.put(e.getUUID(), n.getId()); return n; }
}

