package com.solr98.beyondintegration.handler;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.init.ModItems;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import java.util.HashSet;
import java.util.Set;

/**
 * 弹药箱插入处理器：在物品进入维度网络统一存储前拦截弹药箱，
 * 将创造模式弹药箱标记记录到网络、将普通弹药箱中的弹药拆出为弹药实体存入网络。
 */
public class AmmoBoxExtractHandler implements UnifiedStorageBeforeInsertHandler.BeforeInsertHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public @NotNull UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo beforeInsert(
            @NotNull KeyAmount originalInsert, @NotNull KeyAmount tryInsert, DimensionsNet net) {
        // 非物品键直接放行
        if (!(tryInsert.key() instanceof ItemStackKey itemKey))
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);

        // 非弹药箱直接放行
        ItemStack stack = itemKey.copyStackWithCount(1);
        if (!(stack.getItem() instanceof IAmmoBox iAmmoBox))
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);

        // 网络为空则放行
        if (net == null)
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);

        // Creative ammo box -> physical storage + set TACZ creative mark (增量: 全类型置位/单种 add, 不覆盖已有标记)
        if (iAmmoBox.isAllTypeCreative(stack) || iAmmoBox.isCreative(stack)) {
            // 将创造标记写入网络附属数据（全类型或单种弹药类型）
            if (net instanceof TaczCreativeAccessor acc) {
                if (iAmmoBox.isAllTypeCreative(stack)) {
                    acc.setCreativeAllType(true);
                } else {
                    ResourceLocation ammoId = iAmmoBox.getAmmoId(stack);
                    if (ammoId != null) {
                        Set<String> types = new HashSet<>(acc.getTaczCreativeTypes());
                        types.add(ammoId.toString());
                        acc.setTaczCreativeTypes(types);
                    }
                }
            }
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        // Normal ammo box -> extract ammo count to virtual storage
        ResourceLocation ammoId = iAmmoBox.getAmmoId(stack);
        int ammoCount = iAmmoBox.getAmmoCount(stack);
        long boxCount = originalInsert.amount();

        // 弹药类型或数量无效则放行
        if (ammoId == null || boxCount <= 0 || ammoCount <= 0)
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);

        // 总弹药量 = 单箱弹药数 × 箱子数量
        long totalAmmo = (long) ammoCount * boxCount;

        // 构造带弹药类型的弹药实体并插入网络
        ItemStack ammoStack = new ItemStack(ModItems.AMMO.get());
        if (ammoStack.getItem() instanceof IAmmo iAmmo)
            iAmmo.setAmmoId(ammoStack, ammoId);
        long ammoLeft = net.getUnifiedStorage().insert(new ItemStackKey(ammoStack), totalAmmo, false).amount();
        if (ammoLeft > 0) {
            LOGGER.warn("AmmoBoxExtract: network capacity insufficient, {} ammo could not be stored", ammoLeft);
        }

        // 清空弹药箱内容后作为空箱插入网络
        ItemStack emptyBox = itemKey.copyStackWithCount(1);
        if (emptyBox.getItem() instanceof IAmmoBox iEmpty) {
            iEmpty.setAmmoCount(emptyBox, 0);
            iEmpty.setAmmoId(emptyBox, DefaultAssets.EMPTY_AMMO_ID);
        }
        long boxLeft = net.getUnifiedStorage().insert(new ItemStackKey(emptyBox), boxCount, false).amount();
        if (boxLeft > 0) {
            LOGGER.warn("AmmoBoxExtract: network slot capacity insufficient, {} empty boxes could not be stored", boxLeft);
        }
        net.setDirty();

        // 已拆解完毕，返回空键表示本次插入无需写入原始弹药箱
        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(
                new KeyAmount(new ItemStackKey(ItemStack.EMPTY), 0), false);
    }
}
