package com.solr98.beyondintegration.network.payload;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.client.gui.AbstractEnchantTableGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * 附魔台（神化模式）单槽候选符石数据包（服务端 → 客户端）。
 * 每槽至多发送 stats.clues()+1 个候选附魔；all=true 表示已穷尽全部候选。
 * TYPE: beyond_integration:enchant_clues。
 */
public record EnchantCluesPayload(int containerId, int slot, List<EnchantmentInstance> clues, boolean all) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EnchantCluesPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.parse(BeyondIntegration.MODID + ":enchant_clues"));

    private static final StreamCodec<RegistryFriendlyByteBuf, EnchantmentInstance> ENCH_INST = StreamCodec.composite(
            ByteBufCodecs.holderRegistry(Registries.ENCHANTMENT), i -> i.enchantment,
            ByteBufCodecs.VAR_INT, i -> i.level,
            EnchantmentInstance::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, EnchantCluesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, EnchantCluesPayload::containerId,
            ByteBufCodecs.INT, EnchantCluesPayload::slot,
            ENCH_INST.apply(ByteBufCodecs.list()), EnchantCluesPayload::clues,
            ByteBufCodecs.BOOL, EnchantCluesPayload::all,
            EnchantCluesPayload::new);

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(EnchantCluesPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof AbstractEnchantTableGUI gui && gui.getMenu().containerId == p.containerId()) {
                gui.acceptClues(p.slot(), p.clues(), p.all());
            }
        });
    }
}