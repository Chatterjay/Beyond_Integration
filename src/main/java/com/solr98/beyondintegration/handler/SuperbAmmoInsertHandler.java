package com.solr98.beyondintegration.handler;
import com.atsuishio.superbwarfare.data.gun.Ammo;
import com.atsuishio.superbwarfare.item.ammo.AmmoBoxItem;
import com.atsuishio.superbwarfare.item.ammo.AmmoSupplierItem;
import com.atsuishio.superbwarfare.item.ammo.CreativeAmmoBoxItem;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.helper.UnifiedStorageBeforeInsertHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EmptyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * SW 弹药入库处理器：拦截存入维度网络的 SW 弹药类物品，
 * 将弹药转入网络的虚拟弹药存储（SuperbAmmoAccessor），创造弹药箱则标记无限弹药。
 */
public class SuperbAmmoInsertHandler implements UnifiedStorageBeforeInsertHandler.BeforeInsertHandler {
    /**
     * 插入前拦截：按物品种类（AmmoSupplierItem / CreativeAmmoBoxItem / AmmoBoxItem）
     * 将弹药写入虚拟存储，并决定是否接受本次物理插入。
     */
    @Override
    public @NotNull UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo beforeInsert(
            @NotNull KeyAmount originalInsert, @NotNull KeyAmount tryInsert, DimensionsNet net) {
        if (!(tryInsert.key() instanceof ItemStackKey itemKey) || net == null) {
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }
        if (!(net instanceof SuperbAmmoAccessor acc)) {
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        ItemStack stack = itemKey.getReadOnlyStack();
        var item = stack.getItem();
        var map = acc.getSuperbAmmo();

        // AmmoSupplierItem: 5 virtual ammo types + dedicated ammo box (e.g. handgun_ammo_box)
        if (item instanceof AmmoSupplierItem supplier) {
            map.merge(supplier.getType().serializationName, tryInsert.amount() * supplier.getAmmoToAdd(), Long::sum);
            net.setDirty();
            return accept();
        }

        // CreativeAmmoBoxItem: store to physical storage + mark infinite ammo
        if (item instanceof CreativeAmmoBoxItem) {
            map.put("__infinite__", 1L);
            net.setDirty();
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
        }

        // Generic AmmoBoxItem (ammo_box): extract ammo to virtual storage, clear ammo data for normal insert
        if (item instanceof AmmoBoxItem) {
            ItemStack boxStack = stack.copyWithCount(1);
            boolean any = false;
            for (Ammo type : Ammo.values()) {
                int count = type.get(boxStack);
                if (count > 0) {
                    map.merge(type.serializationName, (long) count, Long::sum);
                    boxStack.remove(type.dataComponent);
                    any = true;
                }
            }
            if (!any) {
                return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
            }
            net.setDirty();
            return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(
                    new KeyAmount(new ItemStackKey(boxStack), tryInsert.amount()), false);
        }

        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(tryInsert, false);
    }

    /** 返回"接受插入"的结果（以空栈占位，完全吸收原插入）。 */
    private static UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo accept() {
        return new UnifiedStorageBeforeInsertHandler.BeforeInsertHandlerReturnInfo(
                new KeyAmount(EmptyStackKey.INSTANCE, 0), false);
    }
}
