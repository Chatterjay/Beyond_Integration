package com.solr98.beyondintegration.network.payload;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.init.DimensionsCraftMenu;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 配方填充请求包（客户端 → 服务端，copy 自 BD RecipeFillC2SPacket）。
 * 携带配方原料的 IStackKey 列表与对应数量（保留 NBT 精确匹配），
 * 由 handle 调用打开的 {@link DimensionsCraftMenu} 的 transferRecipe 填充。
 * TYPE: beyond_integration:recipe_fill；STREAM_CODEC 使用 composite 组合编解码。
 */
// 配方填充（copy 自 BD RecipeFillC2SPacket）：IStackKey 传输保留 NBT 精确匹配
public record RecipeFillPayload(List<IStackKey<?>> keys, List<Long> amounts) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RecipeFillPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.parse(BeyondIntegration.MODID + ":recipe_fill"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeFillPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.collection(ArrayList::new, IStackKey.STREAM_CODEC),
                    RecipeFillPayload::keys,
                    ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_LONG),
                    RecipeFillPayload::amounts,
                    RecipeFillPayload::new
            );

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RecipeFillPayload p, IPayloadContext ctx) {
        // 服务端处理：将原料键值列表填入打开的维度合成菜单
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp && sp.containerMenu instanceof DimensionsCraftMenu menu) {
                menu.transferRecipe(p.keys(), p.amounts());
            }
        });
    }
}
