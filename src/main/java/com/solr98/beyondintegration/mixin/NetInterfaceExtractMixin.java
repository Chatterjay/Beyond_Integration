package com.solr98.beyondintegration.mixin;
import com.mojang.logging.LogUtils;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import com.wintercogs.beyonddimensions.common.machine.FuzzyMode;
import com.wintercogs.beyonddimensions.common.menu.NetInterfaceAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 网络接口提取 Mixin：拦截 BD 的 NetInterfaceBlockEntity.transferFromNet 中对接口
 * NetInterfaceAccess 静态方法的调用（接口无法直接 Mixin 注入）。
 * 安装 SuperbWarfare 时，让 SW 弹药物品改走虚拟弹药计数提取；其余物品继续走 BD 原逻辑。
 */
// BD 0.7.27 起 transferFromNet 委托到接口静态方法 NetInterfaceAccess.transferFromNet；
// NetInterfaceAccess 是接口（Mixin 无法直接注入），故在 NetInterfaceBlockEntity 内拦截该静态调用，
// 参数全部由 @Redirect handler 传入：SW 弹药物品改走虚拟弹药计数，其余走原逻辑
@Mixin(targets = "com.wintercogs.beyonddimensions.common.block.entity.NetInterfaceBlockEntity", remap = false)
public class NetInterfaceExtractMixin {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 拦截 NetInterfaceAccess.transferFromNet 静态调用：
     * 对 SW 弹药物品（虚拟弹药计数）执行特殊提取逻辑，返回是否有任何变化。
     */
    @Redirect(method = "transferFromNet",
              at = @At(value = "INVOKE",
                       target = "Lcom/wintercogs/beyonddimensions/common/menu/NetInterfaceAccess;transferFromNet(Lcom/wintercogs/beyonddimensions/api/dimensionnet/DimensionsNet;Lcom/wintercogs/beyonddimensions/api/storage/handler/impl/StackHandler;Lcom/wintercogs/beyonddimensions/api/storage/handler/impl/StackHandler;ILcom/wintercogs/beyonddimensions/common/machine/FuzzyMode;)Z"),
              remap = false)
    private static boolean beyond$transferFromNet(DimensionsNet net, StackHandler stackHandler,
                                                  StackHandler fakeStackHandler, int capacity, FuzzyMode fuzzyMode) {
        boolean changed = false;
        try {
            if (ModList.get().isLoaded("superbwarfare") && net instanceof SuperbAmmoAccessor acc) {
                var ammoMap = acc.getSuperbAmmo();
                for (int i = 0; i < capacity; i++) {
                    KeyAmount flag = fakeStackHandler.getStackBySlot(i);
                    if (flag.isEmpty()) continue;
                    KeyAmount current = stackHandler.getStackBySlot(i);
                    if (!current.isEmpty() && !current.key().isSameTypeSameComponents(flag.key())) continue;
                    if (!(flag.key() instanceof ItemStackKey itemKey)) continue;

                    ResourceLocation regId = BuiltInRegistries.ITEM.getKey(itemKey.getSource());
                    if (regId == null || !"superbwarfare".equals(regId.getNamespace())) continue;
                    String ammoTypeName = beyond$matchAmmoType(regId.getPath());
                    if (ammoTypeName == null) continue;

                    long currentAmount = current.isEmpty() ? 0 : current.amount();
                    long missing = flag.key().getVanillaMaxStackSize() - currentAmount;
                    if (missing <= 0) continue;

                    long available = ammoMap.getOrDefault(ammoTypeName, 0L);
                    if (available <= 0) continue;

                    long toExtract = Math.min(available, missing);
                    long remaining = available - toExtract;
                    if (remaining <= 0) ammoMap.remove(ammoTypeName);
                    else ammoMap.put(ammoTypeName, remaining);
                    net.setDirty();

                    KeyAmount stack = new KeyAmount(
                            new ItemStackKey(new ItemStack(itemKey.getSource(), (int) Math.min(toExtract, 9999))),
                            toExtract);
                    KeyAmount rem = stackHandler.insert(i, stack.key(), stack.amount(), false);
                    if (!rem.isEmpty()) {
                        // 放不下的退回虚拟计数
                        ammoMap.merge(ammoTypeName, rem.amount(), Long::sum);
                        net.setDirty();
                    }
                    changed = true;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("NetInterfaceExtractMixin: SW ammo extraction failed", e);
        }
        // 非 SW 弹药部分继续走 BD 原逻辑
        boolean rest = NetInterfaceAccess.transferFromNet(net, stackHandler, fakeStackHandler, capacity, fuzzyMode);
        return changed || rest;
    }

    /** 把 SW 弹药物品注册路径映射为虚拟弹药类型名，无法识别时返回 null */
    @Unique
    private static String beyond$matchAmmoType(String path) {
        if (!ModList.get().isLoaded("superbwarfare")) return null;
        if ("handgun_ammo".equals(path) || "handgun_ammo_box".equals(path)) return "HandgunAmmo";
        if ("rifle_ammo".equals(path) || "rifle_ammo_box".equals(path)) return "RifleAmmo";
        if ("shotgun_ammo".equals(path) || "shotgun_ammo_box".equals(path)) return "ShotgunAmmo";
        if ("sniper_ammo".equals(path) || "sniper_ammo_box".equals(path)) return "SniperAmmo";
        if ("heavy_ammo".equals(path)) return "HeavyAmmo";
        return null;
    }
}
