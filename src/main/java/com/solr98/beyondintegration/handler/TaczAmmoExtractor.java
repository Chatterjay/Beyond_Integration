package com.solr98.beyondintegration.handler;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.init.ModItems;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tacz 模组弹药查询/消耗工具：从维度网络中查找弹药数量、按需消耗弹药，
 * 支持创造弹药箱（无限弹药）判定，并可汇总网络中全部弹药清单。
 * 查询走 BD API 直查（物理弹药无服务端缓存）；扣减由 extract 返回值驱动。
 */
public class TaczAmmoExtractor {

    /** 创造弹药箱哨兵值：表示无限弹药（Integer.MAX_VALUE）。 */
    public static final int CREATIVE_SENTINEL = Integer.MAX_VALUE;
    /** long 转 int 的安全上限（避免溢出）。 */
    public static final int MAX_SAFE_INT = Integer.MAX_VALUE - 1;

    /** 将 long 安全截断为 int（超出上限返回 MAX_SAFE_INT）。 */
    public static int safeLongToInt(long value) {
        if (value >= MAX_SAFE_INT) return MAX_SAFE_INT;
        return (int) value;
    }

    /** 由枪械物品获取其弹药 ID（服务端数据）。 */
    public static ResourceLocation getAmmoId(ItemStack gunStack) {
        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun == null) return null;
        ResourceLocation gunId = iGun.getGunId(gunStack);
        if (gunId == null) return null;
        var opt = TimelessAPI.getCommonGunIndex(gunId);
        if (opt.isEmpty()) return null;
        return opt.get().getGunData().getAmmoId();
    }

    /** 由枪械物品获取其弹药 ID（客户端数据，用于客户端显示）。 */
    public static ResourceLocation getAmmoIdClient(ItemStack gunStack) {
        IGun iGun = IGun.getIGunOrNull(gunStack);
        if (iGun == null) return null;
        ResourceLocation gunId = iGun.getGunId(gunStack);
        if (gunId == null) return null;
        var opt = TimelessAPI.getClientGunIndex(gunId);
        if (opt.isEmpty()) return null;
        return opt.get().getGunData().getAmmoId();
    }

    /** 统计指定网络中该枪械所需弹药的数量（API 直查：创造箱缓存 O(1) + 存储按弹药 ID 累加）。 */
    public static int countAmmoInNetwork(ItemStack gunStack, DimensionsNet net) {
        if (net == null) return 0;
        ResourceLocation ammoId = getAmmoId(gunStack);
        if (ammoId == null) return 0;
        return countAmmoInNetworkByAmmoId(ammoId, net);
    }

    /** 按弹药 ID 统计指定网络中的弹药数量（API 精确查询：创造箱缓存 O(1) + reference key getStackByKey）。 */
    public static int countAmmoInNetworkByAmmoId(ResourceLocation ammoId, DimensionsNet net) {
        if (ammoId == null || net == null) return 0;
        if (TaczAmmoTracker.isInfinite(net, ammoId)) return Integer.MAX_VALUE;
        ItemStackKey refKey = new ItemStackKey(buildAmmoStack(ammoId));
        KeyAmount found = net.getUnifiedStorage().getStackByKey(refKey);
        return safeLongToInt(found.amount());
    }

    /** 统计玩家主网络中的弹药总数（仅主网络）。 */
    public static int countAmmoFromAll(ServerPlayer player, ItemStack gunStack) {
        DimensionsNet primary = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (primary == null) return 0;
        return countAmmoInNetwork(gunStack, primary);
    }

    /** 从玩家主网络尝试消耗弹药，成功后记录网络使用情况（仅主网络）。 */
    public static int tryConsumeFromAll(ServerPlayer player, ItemStack gunStack, int neededAmount) {
        DimensionsNet primary = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (primary == null) return 0;
        int taken = consumeAmmoDirectly(gunStack, neededAmount, primary);
        if (taken > 0) {
            PlayerNetUsageTracker.record(player.getUUID(), primary.getId());
            return taken;
        }
        return 0;
    }

    /** 统计女仆（touhou_little_maid）终端所在网络的弹药数量（模组未加载时返回 0）。 */
    public static int countAmmoFromMaid(LivingEntity entity, ItemStack gunStack) {
        if (!ModList.get().isLoaded("touhou_little_maid")) return 0;
        DimensionsNet net = com.solr98.beyondintegration.maid.MaidNetworkHelper.findTerminal(entity);
        if (net == null) return 0;
        return countAmmoInNetwork(gunStack, net);
    }

    /** 从女仆终端所在网络尝试消耗弹药（模组未加载时返回 0）。 */
    public static int tryConsumeFromMaid(LivingEntity entity, ItemStack gunStack, int neededAmount) {
        if (!ModList.get().isLoaded("touhou_little_maid")) return 0;
        DimensionsNet net = com.solr98.beyondintegration.maid.MaidNetworkHelper.findTerminal(entity);
        if (net == null) return 0;
        return consumeAmmoDirectly(gunStack, neededAmount, net);
    }

    /** 直接从指定网络提取该枪械所需弹药（网络含创造箱时视为满足需求；只扣精确 reference key，返回值驱动）。 */
    public static int consumeAmmoDirectly(ItemStack gunStack, int neededAmount, DimensionsNet net) {
        if (hasCreativeAmmoBoxInNetwork(gunStack, net)) return neededAmount;
        ResourceLocation ammoId = getAmmoId(gunStack);
        if (ammoId == null) return 0;

        ItemStackKey refKey = new ItemStackKey(buildAmmoStack(ammoId));
        KeyAmount r = net.getUnifiedStorage().extract(refKey, neededAmount, false, false);
        if (r.amount() > 0) {
            net.setDirty();
            return safeLongToInt(r.amount());
        }
        return 0;
    }

    /** 按弹药 ID 直接从指定网络消耗（公开 API，供其他模组/女仆调用；创造箱无限直接满足，返回值驱动）。 */
    public static int consumeAmmoByAmmoId(ResourceLocation ammoId, int neededAmount, DimensionsNet net) {
        if (ammoId == null || net == null || neededAmount <= 0) return 0;
        if (TaczAmmoTracker.isInfinite(net, ammoId)) return neededAmount;

        ItemStackKey refKey = new ItemStackKey(buildAmmoStack(ammoId));
        KeyAmount r = net.getUnifiedStorage().extract(refKey, neededAmount, false, false);
        if (r.amount() > 0) {
            net.setDirty();
            return safeLongToInt(r.amount());
        }
        return 0;
    }

    /** 判断网络中是否存在覆盖该弹药 ID 的创造弹药箱（O(1) 只读虚拟计数）。 */
    public static boolean hasCreativeAmmoBoxInNetwork(ResourceLocation ammoId, DimensionsNet net) {
        return TaczAmmoTracker.isInfinite(net, ammoId);
    }

    /** 判断网络中是否存在覆盖该枪械所需弹药的创造弹药箱（O(1) 只读虚拟计数）。 */
    public static boolean hasCreativeAmmoBoxInNetwork(ItemStack gunStack, DimensionsNet net) {
        ResourceLocation ammoId = getAmmoId(gunStack);
        return ammoId != null && TaczAmmoTracker.isInfinite(net, ammoId);
    }

    /** 汇总网络中全部弹药数量（弹药类型 ID → 数量）；创造箱为 Integer.MAX_VALUE，全类型创造返回 "*"。
     *  遍历时顺带对账创造箱虚拟计数（只增不删，变化落盘），使创造弹更新不依赖 delta 事件，
     *  随快照周期自动收敛。 */
    public static Map<String, Integer> countAllAmmoInNetwork(DimensionsNet net) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (net == null) return result;

        Map<String, Integer> creativeCounts = net instanceof TaczCreativeAccessor acc
                ? acc.getTaczCreativeCounts() : null;
        boolean creativeChanged = false;

        // 单次遍历统一存储：物理弹药按弹药 ID 累加 + 创造箱对账/无限标记
        UnifiedStorage storage = net.getUnifiedStorage();
        for (KeyAmount ka : storage.getStorage()) {
            if (!(ka.key() instanceof ItemStackKey ik)) continue;
            ItemStack stack = ik.getReadOnlyStack();

            if (stack.getItem() instanceof IAmmoBox box) {
                if (box.isAllTypeCreative(stack)) {
                    if (creativeCounts != null && creativeCounts.getOrDefault("*", 0) <= 0) {
                        creativeCounts.put("*", 1);
                        creativeChanged = true;
                    }
                    result.clear();
                    result.put("*", Integer.MAX_VALUE);
                    if (creativeChanged) NetworkAmmoData.get().setDirty();
                    return result;
                }
                ResourceLocation boxAmmoId = box.getAmmoId(stack);
                if (box.isCreative(stack) && boxAmmoId != null) {
                    if (creativeCounts != null && creativeCounts.getOrDefault(boxAmmoId.toString(), 0) <= 0) {
                        creativeCounts.put(boxAmmoId.toString(), 1);
                        creativeChanged = true;
                    }
                    result.put(boxAmmoId.toString(), Integer.MAX_VALUE);
                }
            } else if (stack.getItem() instanceof IAmmo iAmmo) {
                ResourceLocation ammoId = iAmmo.getAmmoId(stack);
                if (ammoId != null) {
                    String idStr = ammoId.toString();
                    Integer existing = result.get(idStr);
                    if (existing == null || existing != Integer.MAX_VALUE) {
                        long count = ka.amount();
                        if (count > 0) {
                            // 同弹药 id 可能分布多个 key（不同 NBT），按 id 累加而非覆盖
                            long sum = (existing == null ? 0L : (long) existing) + count;
                            result.put(idStr, (int) Math.min(sum, Integer.MAX_VALUE));
                        }
                    }
                }
            }
        }

        // 虚拟计数中的创造箱标记并入结果（delta 维护的条目，遍历兜底已补齐）
        if (creativeCounts != null) {
            if (creativeCounts.getOrDefault("*", 0) > 0) {
                result.clear();
                result.put("*", Integer.MAX_VALUE);
                return result;
            }
            for (var entry : creativeCounts.entrySet()) {
                if (!"*".equals(entry.getKey()) && entry.getValue() > 0) {
                    result.put(entry.getKey(), Integer.MAX_VALUE);
                }
            }
        }

        if (creativeChanged) NetworkAmmoData.get().setDirty();
        return result;
    }

    /** 构造带指定弹药 ID 的 tacz:ammo 物品栈（作为查询/提取的参考物）。 */
    private static ItemStack buildAmmoStack(ResourceLocation ammoId) {
        ItemStack ref = new ItemStack(ModItems.AMMO.get());
        if (ref.getItem() instanceof IAmmo iAmmo) {
            iAmmo.setAmmoId(ref, ammoId);
        }
        return ref;
    }
}


