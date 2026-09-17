package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.handler.EnchantSeparationAccessor;
import com.solr98.beyondintegration.handler.EnergyChargeAccessor;
import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.solr98.beyondintegration.handler.WorkstationActivationAccessor;
import com.solr98.beyondintegration.feature.ammo.common.NetworkAmmoData;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.solr98.beyondintegration.handler.TaczCreativeAccessor;
import com.solr98.beyondintegration.core.subscribe.BdSubscriptionHub;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * 注入 BeyondDimensions 的 {@link DimensionsNet}，为其实现四个本模组扩展接口：
 * SuperbAmmoAccessor（SW 网络弹药存取）、NetworkNameProvider（网络名称）、
 * EnchantSeparationAccessor（附魔分离开关）、TaczCreativeAccessor（TACZ 创造弹药计数）、
 * EnergyChargeAccessor（自动充电开关）、WorkstationActivationAccessor（工作台献祭激活），
 * 数据统一挂在 NetworkAmmoData 上，并订阅存储变化以同步创造模式弹药。
 */
@Mixin(targets = "com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet", remap = false)
public class DimensionsNetMixin implements SuperbAmmoAccessor, NetworkNameProvider, EnchantSeparationAccessor, TaczCreativeAccessor, EnergyChargeAccessor, WorkstationActivationAccessor {

    /** 增量钩子是否已初始化成功的标记（只初始化一次；订阅成功后才置位，失败可重试） */
    @Unique
    private boolean beyond$deltaInit = false;

    /** 存档反序列化深度（前提条件）：load 期间禁止建立存储订阅，避免经 getNetFromId 重入加载 */
    @Unique
    private static final ThreadLocal<Integer> beyond$LOAD_DEPTH = ThreadLocal.withInitial(() -> 0);

    // load 开始：标记加载中（前提条件依据）
    @Inject(method = "load", at = @At("HEAD"), remap = false)
    private static void beyond$onLoadHead(CompoundTag tag, CallbackInfoReturnable<DimensionsNet> cir) {
        beyond$LOAD_DEPTH.set(beyond$LOAD_DEPTH.get() + 1);
    }

    // load 结束：还原深度；最外层结束时对该网络补建延迟订阅（此时订阅安全且不回查加载）
    @Inject(method = "load", at = @At("RETURN"), remap = false)
    private static void beyond$onLoadReturn(CompoundTag tag, CallbackInfoReturnable<DimensionsNet> cir) {
        int depth = Math.max(0, beyond$LOAD_DEPTH.get() - 1);
        beyond$LOAD_DEPTH.set(depth);
        if (depth == 0 && cir.getReturnValue() instanceof SuperbAmmoAccessor acc) {
            acc.beyond$ensureDeltaHook(); // 安全时机补订阅
        }
    }

    /** 将 this 强转为 DimensionsNet（Mixin 惯用法） */
    private DimensionsNet self() { return (DimensionsNet) (Object) this; }

    /** 订阅统一存储的增量事件：把进出的创造模式弹药（TACZ 弹盒 / SW 创造弹药）同步进网络弹药缓存 */
    @Unique
    private synchronized void beyond$initDeltaHook() {
        if (beyond$deltaInit) return;
        // 前提条件：存档加载（反序列化）期间不建立订阅——插入钩子链会在此时段调用本方法，
        // 若在此通过 getNetFromId 回查同一网络会重入 DimensionsNet.load 造成 StackOverflowError。
        // 延迟到 load 结束后（beyond$onLoadReturn 补建）或下一次正常调用时再订阅。
        if (beyond$LOAD_DEPTH.get() > 0) return;
        // 经统一订阅中心注册（弱引用订阅 + 网络销毁/服务器停止时自动清理）
        // 注意：必须用"按对象"重载传 self()，不可用按 id 重载（getNetFromId 会触发加载）
        AutoCloseable handle = BdSubscriptionHub.subscribe(self(), this, (key, size, insert) -> {
                if (!(key instanceof ItemStackKey ik)) return;
                ItemStack stack = ik.getReadOnlyStack();

                if (ModList.get().isLoaded("tacz")) {
                    try {
                        Class<?> iaBbox = Class.forName("com.tacz.guns.api.item.IAmmoBox");
                        if (iaBbox.isInstance(stack.getItem())) {
                            Map<String, Integer> counts = getTaczCreativeCounts();
                            String typeKey;
                            Boolean allType = (Boolean) iaBbox.getMethod("isAllTypeCreative", ItemStack.class).invoke(stack.getItem(), stack);
                            if (allType) {
                                typeKey = "*";
                            } else {
                                Boolean creative = (Boolean) iaBbox.getMethod("isCreative", ItemStack.class).invoke(stack.getItem(), stack);
                                if (creative) {
                                    ResourceLocation ammoId = (ResourceLocation) iaBbox.getMethod("getAmmoId", ItemStack.class).invoke(stack.getItem(), stack);
                                    if (ammoId == null) return;
                                    typeKey = ammoId.toString();
                                } else {
                                    return;
                                }
                            }
                            if (insert) {
                                counts.merge(typeKey, (int) size, Integer::sum);
                            } else {
                                counts.computeIfPresent(typeKey, (k, v) -> v <= (int) size ? null : v - (int) size);
                            }
                            NetworkAmmoData.markDirty();
                            return;
                        }
                    } catch (Exception ignored) {}
                }

                ResourceLocation regKey = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
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
                    NetworkAmmoData.markDirty();
                }
            });
        // 订阅成功才置位；失败（如 storage 未就绪）保留重试机会
        if (handle != null) beyond$deltaInit = true;
    }

    /** 延迟补建订阅（load 结束时安全时机调用；亦可被任何后续访问触发） */
    @Override
    public void beyond$ensureDeltaHook() {
        beyond$initDeltaHook();
    }

    @Override
    public Map<String, Long> getSuperbAmmo() {
        beyond$initDeltaHook();
        return NetworkAmmoData.getOrCreate(self().getId()).getSuperbAmmo();
    }

    @Override
    public void setSuperbAmmo(Map<String, Long> map) {
        beyond$initDeltaHook();
        NetworkAmmoData.getOrCreate(self().getId()).setSuperbAmmo(map);
    }

    @Override
    public boolean beyond$isEnchantSeparationEnabled() {
        return NetworkAmmoData.getOrCreate(self().getId()).isEnchantSeparation();
    }

    @Override
    public void beyond$setEnchantSeparationEnabled(boolean v) {
        NetworkAmmoData.getOrCreate(self().getId()).setEnchantSeparation(v);
        NetworkAmmoData.markDirty();
    }

    @Override
    public boolean beyond$isEnergyChargeEnabled() {
        return NetworkAmmoData.getOrCreate(self().getId()).isEnergyCharge();
    }

    @Override
    public void beyond$setEnergyChargeEnabled(boolean v) {
        NetworkAmmoData.getOrCreate(self().getId()).setEnergyCharge(v);
        NetworkAmmoData.markDirty();
    }

    @Override
    public Map<String, Integer> getTaczCreativeCounts() {
        beyond$initDeltaHook();
        return NetworkAmmoData.getOrCreate(self().getId()).getTaczCreativeTypeCounts();
    }

    @Override
    public void setTaczCreativeCounts(Map<String, Integer> counts) {
        beyond$initDeltaHook();
        Map<String, Integer> map = NetworkAmmoData.getOrCreate(self().getId()).getTaczCreativeTypeCounts();
        map.clear();
        if (counts != null) map.putAll(counts);
    }

    @Override
    public boolean beyond$isWorkstationActivated(String id) {
        return id != null && NetworkAmmoData.getOrCreate(self().getId()).getActivatedWorkstations().contains(id);
    }

    @Override
    public void beyond$activateWorkstation(String id) {
        if (id == null) return;
        if (NetworkAmmoData.getOrCreate(self().getId()).getActivatedWorkstations().add(id)) {
            NetworkAmmoData.markDirty();
        }
    }

    @Override
    public void beyond$resetWorkstation(String id) {
        if (id == null) return;
        if (NetworkAmmoData.getOrCreate(self().getId()).getActivatedWorkstations().remove(id)) {
            NetworkAmmoData.markDirty();
        }
    }
}
