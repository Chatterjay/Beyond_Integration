package com.solr98.beyondintegration.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Slot 字段访问器：暴露容器槽位 Slot 的 x/y 坐标字段的修改能力，
 * 供模组用于调整容器界面（如枪械改装台）槽位渲染位置。
 */
@Mixin(Slot.class)
public interface SlotAccessor {
    /** 修改槽位 x 坐标 */
    @Accessor("x") @Mutable
    void setX(int x);

    /** 修改槽位 y 坐标 */
    @Accessor("y") @Mutable
    void setY(int y);
}

