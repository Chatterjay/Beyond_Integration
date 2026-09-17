package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.handler.SentryNetIdAccessor;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 哨戒臂网络绑定 Mixin（Pseudo）：为哨戒机械臂模组的 SentryArmBlockEntity
 * 混入维度网络 ID 存取（实现 SentryNetIdAccessor），并随方块实体 NBT 读写该绑定。
 */
@Pseudo
@Mixin(targets = "euphy.upo.sentrymechanicalarm.content.SentryArmBlockEntity", remap = false)
public class SentryArmNetMixin implements SentryNetIdAccessor {

    /** 混入的哨戒臂绑定网络 ID，-1 表示未绑定 */
    @Unique
    private int beyond$netId = -1;

    /** 方块实体写 NBT 时，附带持久化网络绑定 ID */
    @Inject(method = "write", at = @At("RETURN"))
    private void beyond$onWrite(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        if (beyond$netId >= 0) compound.putInt("beyond$netId", beyond$netId);
    }

    /** 方块实体读 NBT 时，恢复网络绑定 ID */
    @Inject(method = "read", at = @At("RETURN"))
    private void beyond$onRead(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        if (compound.contains("beyond$netId")) beyond$netId = compound.getInt("beyond$netId");
    }

    @Override public int getSentryNetId() { return beyond$netId; }

    @Override
    public void clearSentryBinding() {
        this.beyond$netId = -1;
    }
}
