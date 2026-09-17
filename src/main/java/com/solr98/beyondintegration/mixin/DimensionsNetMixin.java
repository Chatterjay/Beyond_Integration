package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.core.subscribe.BdSubscriptionHub;
import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.solr98.beyondintegration.handler.EnergyChargeAccessor;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.solr98.beyondintegration.handler.NetworkAmmoData;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.solr98.beyondintegration.handler.TaczCreativeAccessor;
import com.solr98.beyondintegration.handler.WorkstationActivationAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * 注入目标：Beyond Dimensions 的 {@code DimensionsNet}（维度网络）。
 * 目的：为网络附加集成接口，提供 Superb Warfare 弹药存取、附魔分离开关、
 * 网络名称、TacZ 创造模式弹药类型以及工作台献祭激活状态的持久化读写；
 * 并通过统一存储的增量订阅自动同步"创造弹药盒/无限弹药"等特殊条目。
 */
@Mixin(targets = "com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet", remap = false)
public class DimensionsNetMixin implements SuperbAmmoAccessor, NetworkNameProvider, EnchantSeparationAccessor, TaczCreativeAccessor, EnergyChargeAccessor, WorkstationActivationAccessor {

    // 增量订阅钩子是否已初始化（订阅成功后才置位，失败可重试）
    @Unique
    private boolean beyond$deltaInit = false;

    // 存档反序列化深度（前提条件）：load 期间禁止建立存储订阅，避免经 getNetFromId 重入加载
    @Unique
    private static final ThreadLocal<Integer> beyond$LOAD_DEPTH = ThreadLocal.withInitial(() -> 0);

    // load 开始：标记加载中（前提条件依据）；0.7.30 的 load 带 HolderLookup.Provider 参数
    @Inject(method = "load", at = @At("HEAD"), remap = false)
    private static void beyond$onLoadHead(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries,
                                          CallbackInfoReturnable<DimensionsNet> cir) {
        beyond$LOAD_DEPTH.set(beyond$LOAD_DEPTH.get() + 1);
    }

    // load 结束：还原深度；最外层结束时对该网络补建延迟订阅（此时订阅安全且不回查加载）
    @Inject(method = "load", at = @At("RETURN"), remap = false)
    private static void beyond$onLoadReturn(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries,
                                            CallbackInfoReturnable<DimensionsNet> cir) {
        int depth = Math.max(0, beyond$LOAD_DEPTH.get() - 1);
        beyond$LOAD_DEPTH.set(depth);
        if (depth == 0 && cir.getReturnValue() instanceof SuperbAmmoAccessor acc) {
            acc.beyond$ensureDeltaHook(); // 安全时机补订阅
        }
    }

    private DimensionsNet self() { return (DimensionsNet) (Object) this; }

    // 注册统一存储增量订阅：拦截弹药盒（TacZ 全类型/单类型）与创造模式物品（Superb WarFare 无限弹药）的进出
    // 注意：必须按对象订阅（self()），不能按 ID 订阅——按 ID 会调 getNetFromId → DataStorage.get，
    // 在存档加载期间会重入 getNetFromId 导致无限递归崩溃；且加载期间（前提条件）直接跳过订阅。
    @Unique
    private synchronized void beyond$initDeltaHook() {
        if (beyond$deltaInit) return;
        // 前提条件：反序列化（load）期间不建立订阅，待 load 结束或下次访问时补建
        if (beyond$LOAD_DEPTH.get() > 0) return;
        // 经统一订阅中心注册（弱引用订阅 + 网络销毁/服务器停止时自动清理）
        AutoCloseable handle = BdSubscriptionHub.subscribe(self(), this, (key, size, insert) -> {
                if (!(key instanceof ItemStackKey ik)) return;
                ItemStack stack = ik.getReadOnlyStack();

                if (ModList.get().isLoaded("tacz")) {
                    try {
                        Class<?> iaBbox = Class.forName("com.tacz.guns.api.item.IAmmoBox");
                        if (iaBbox.isInstance(stack.getItem())) {
                            boolean allType = (Boolean) iaBbox.getMethod("isAllTypeCreative", ItemStack.class).invoke(stack.getItem(), stack);
                            String typeKey;
                            if (allType) {
                                typeKey = "*";
                            } else {
                                Boolean creative = (Boolean) iaBbox.getMethod("isCreative", ItemStack.class).invoke(stack.getItem(), stack);
                                if (!creative) return;
                                ResourceLocation ammoId = (ResourceLocation) iaBbox.getMethod("getAmmoId", ItemStack.class).invoke(stack.getItem(), stack);
                                if (ammoId == null) return;
                                typeKey = ammoId.toString();
                            }
                            // 全类型：boolean true/false；单种：insert 覆盖(add)、extract 清理(remove)
                            if (allType) {
                                setCreativeAllType(insert);
                            } else {
                                java.util.Set<String> types = getTaczCreativeTypes();
                                if (insert) types.add(typeKey);
                                else types.remove(typeKey);
                            }
                            NetworkAmmoData.get().setDirty();
                            return;
                        }
                    } catch (Exception ignored) {}
                }

                ResourceLocation regKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (regKey != null && "superbwarfare".equals(regKey.getNamespace()) && regKey.getPath().contains("creative")) {
                    Map<String, Long> superbAmmo = getSuperbAmmo();
                    long current = superbAmmo.getOrDefault("__infinite__", 0L);
                    if (insert) {
                        superbAmmo.put("__infinite__", current + size);
                    } else {
                        long remaining = current - size;
                        if (remaining <= 0) superbAmmo.remove("__infinite__");
                        else superbAmmo.put("__infinite__", remaining);
                    }
                    NetworkAmmoData.get().setDirty();
                }
            });
        // 订阅成功才置位；失败保留重试机会
        if (handle != null) beyond$deltaInit = true;
    }

    // 延迟补建订阅（load 结束时安全时机调用；亦可被任何后续访问触发）
    @Override
    public void beyond$ensureDeltaHook() {
        beyond$initDeltaHook();
    }

    // 读取该网络的 Superb Warfare 弹药映射（惰性初始化增量订阅）
    @Override
    public Map<String, Long> getSuperbAmmo() {
        beyond$initDeltaHook();
        return NetworkAmmoData.get().getSuperbAmmo(self().getId());
    }

    // 写入该网络的 Superb Warfare 弹药映射
    @Override
    public void setSuperbAmmo(Map<String, Long> map) {
        beyond$initDeltaHook();
        NetworkAmmoData.get().setSuperbAmmo(self().getId(), map);
    }

    // 读取附魔分离开关状态
    @Override
    public boolean beyond$isEnchantSeparationEnabled() {
        return NetworkAmmoData.get().isEnchantSeparation(self().getId());
    }

    // 写入附魔分离开关状态并标记数据已变更
    @Override
    public void beyond$setEnchantSeparationEnabled(boolean v) {
        NetworkAmmoData.get().setEnchantSeparation(self().getId(), v);
        NetworkAmmoData.get().setDirty();
    }

    // 读取自动充电开关状态
    @Override
    public boolean beyond$isEnergyChargeEnabled() {
        return NetworkAmmoData.get().getEnergyCharge(self().getId());
    }

    // 写入自动充电开关状态并标记数据已变更
    @Override
    public void beyond$setEnergyChargeEnabled(boolean v) {
        NetworkAmmoData.get().setEnergyCharge(self().getId(), v);
        NetworkAmmoData.get().setDirty();
    }

    @Override
    public java.util.Set<String> getTaczCreativeTypes() {
        beyond$initDeltaHook();
        return NetworkAmmoData.get().getCreativeTypesForNet(self().getId());
    }

    @Override
    public void setTaczCreativeTypes(java.util.Set<String> types) {
        beyond$initDeltaHook();
        NetworkAmmoData.get().setCreativeTypesForNet(self().getId(), types);
    }

    @Override
    public boolean getCreativeAllType() {
        beyond$initDeltaHook();
        return NetworkAmmoData.get().getCreativeAllTypeForNet(self().getId());
    }

    @Override
    public void setCreativeAllType(boolean allType) {
        beyond$initDeltaHook();
        NetworkAmmoData.get().setCreativeAllTypeForNet(self().getId(), allType);
    }

    @Override
    public Map<String, Integer> getTaczCreativeCounts() {
        beyond$initDeltaHook();
        return NetworkAmmoData.get().getTaczCreativeTypeCounts(self().getId());
    }

    @Override
    public void setTaczCreativeCounts(Map<String, Integer> counts) {
        beyond$initDeltaHook();
        NetworkAmmoData.get().setTaczCreativeTypeCounts(self().getId(), counts);
    }

    // 查询该网络是否已献祭激活指定工作台
    @Override
    public boolean beyond$isWorkstationActivated(String id) {
        return id != null && NetworkAmmoData.get().isWorkstationActivated(self().getId(), id);
    }

    // 献祭激活指定工作台（网络级持久化）
    @Override
    public void beyond$activateWorkstation(String id) {
        NetworkAmmoData.get().activateWorkstation(self().getId(), id);
    }

    // 重置指定工作台的激活状态（网络级持久化）
    @Override
    public void beyond$resetWorkstation(String id) {
        if (id == null) return;
        if (NetworkAmmoData.get().getActivatedWorkstations(self().getId()).remove(id)) {
            NetworkAmmoData.get().setDirty();
        }
    }
}
