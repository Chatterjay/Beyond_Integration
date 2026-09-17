package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.handler.IFakePlayerNetId;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 注入目标：NeoForge 的 {@code FakePlayer}（模拟玩家实体）。
 * 目的：为 FakePlayer 附加一个网络 ID 字段，使模拟玩家可绑定维度网络，
 * 供枪械弹药消耗等逻辑识别该 FakePlayer 对应的网络。
 */
@Mixin(FakePlayer.class)
public class FakePlayerMixin implements IFakePlayerNetId {
    // 附加字段：绑定的网络 ID（-1 表示未绑定）
    @Unique private int beyond$netId = -1;

    // 获取绑定网络 ID
    @Override
    public int beyond$getNetId() { return beyond$netId; }

    // 设置绑定网络 ID
    @Override
    public void beyond$setNetId(int netId) { beyond$netId = netId; }
}

