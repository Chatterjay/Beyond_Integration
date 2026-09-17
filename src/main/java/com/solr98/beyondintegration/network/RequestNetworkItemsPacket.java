package com.solr98.beyondintegration.network;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 网络物品计数请求包（客户端 → 服务端，空载荷）。
 * 服务端 handle 扫描玩家主网络内与 Tacz 枪械台配方相关的物品，
 * 以 {@link NetworkItemCountsPacket} 回传各配方原料数量；无网络时回传空数据。
 * TYPE: beyond_integration:request_network_items；STREAM_CODEC 为无字段的 unit 编解码器。
 */
public record RequestNetworkItemsPacket() implements CustomPacketPayload {
    public static final Type<RequestNetworkItemsPacket> TYPE = new Type<>(ResourceLocation.parse("beyond_integration:request_network_items"));
    public static final StreamCodec<FriendlyByteBuf, RequestNetworkItemsPacket> STREAM_CODEC = StreamCodec.unit(new RequestNetworkItemsPacket());

    // 索引：配方材料(注册名) → 相关 Tacz 配方条目列表
    static final Map<String, List<TaczIngredient>> TACZ_INDEX = new HashMap<>();
    // 索引按 server 实例 + 配方集合大小校验，datapack reload/换档后自动重建
    private static net.minecraft.server.MinecraftServer indexServer = null;
    private static int indexRecipeCount = -1;

    // Tacz 配方中的单个材料条目：配方 ID、槽位索引、材料判定、是否要求 NBT 精确匹配
    record TaczIngredient(ResourceLocation recipeId, int idx, Ingredient ingredient, boolean hasNbt) {}

    /** 校验索引缓存是否仍有效（server 实例或配方数量变化时重建） */
    static void ensureIndex(ServerPlayer sp) {
        if (sp.getServer() == null) return;
        int count = sp.getServer().getRecipeManager().getRecipes().size();
        if (indexServer != sp.getServer() || indexRecipeCount != count) {
            buildIndex(sp);
        }
    }

    public static void handle(RequestNetworkItemsPacket packet, IPayloadContext ctx) {
        // 服务端处理：无主网络时回发空数据，否则扫描网络物品并回发计数
        ctx.enqueueWork(() -> {
            var player = ctx.player();
            if (!(player instanceof ServerPlayer sp)) return;

            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(sp);
            if (net == null) {
                PacketHandler.sendToPlayer(sp, new NetworkItemCountsPacket(new HashMap<>(), true, false, -1, ""));
                return;
            }
            ensureIndex(sp);
            PacketHandler.sendToPlayer(sp, new NetworkItemCountsPacket(scanNetworkItems(net), true, true, net.getId(), net.getCustomName()));        });
    }

    /**
     * 配方驱动定向扫描网络物品（与合成侧一致）：
     * TACZ_INDEX 无 partial_nbt（hasNbt）条目 → 全部 itemId 定向精确键（O(材料) 个 O(1)，零桶扫描）；
     * 存在 hasNbt 条目 → 全桶遍历按 ingredient.test 聚合（partial_nbt 子集匹配，保证正确）。
     */
    static Map<String, Long> scanNetworkItems(DimensionsNet net) {
        Map<String, Long> counts = new HashMap<>();
        boolean anyNbt = TACZ_INDEX.values().stream()
                .flatMap(List::stream)
                .anyMatch(TaczIngredient::hasNbt);

        if (!anyNbt) {
            // 定向：每个配方材料 itemId 精确键 O(1)
            for (String itemId : TACZ_INDEX.keySet()) {
                ResourceLocation id = ResourceLocation.tryParse(itemId);
                if (id == null) continue;
                var item = BuiltInRegistries.ITEM.get(id);
                if (item == null || item == Items.AIR) continue;
                long amount = net.getUnifiedStorage()
                        .getStackByKey(new ItemStackKey(new ItemStack(item))).amount();
                if (amount <= 0) continue;
                for (TaczIngredient ti : TACZ_INDEX.get(itemId)) {
                    counts.merge(ti.recipeId() + "|" + ti.idx(), amount, Long::sum);
                }
            }
            return counts;
        }

        // 全桶兜底（hasNbt 条目存在时）
        net.getUnifiedStorage().getBucket(ItemStackKey.ID).ifPresent(bucket -> {
            for (int i = 0; i < bucket.size(); i++) {
                IStackKey<?> rawKey = bucket.get(i);
                if (!(rawKey instanceof ItemStackKey ik)) continue;
                long amount = net.getUnifiedStorage().getStackByKey(ik).amount();
                if (amount <= 0) continue;
                ItemStack stored = ik.getReadOnlyStack();
                if (stored.isEmpty()) continue;
                List<TaczIngredient> related = TACZ_INDEX.get(stored.getItem().toString());
                if (related != null) {
                    for (var ti : related) {
                        if (ti.ingredient().test(stored))
                            counts.merge(ti.recipeId() + "|" + ti.idx(), amount, Long::sum);
                    }
                }
            }
        });
        return counts;
    }

    /** 重建 Tacz 配方索引：遍历所有 GunSmithTableRecipe，按材料注册名分组记录 */
    static void buildIndex(ServerPlayer player) {
        TACZ_INDEX.clear();
        for (var holder : player.getServer().getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof GunSmithTableRecipe taczRecipe)) continue;
            ResourceLocation rid = holder.id();
            List<GunSmithTableIngredient> inputs = taczRecipe.getInputs();
            if (inputs == null) continue;
            for (int idx = 0; idx < inputs.size(); idx++) {
                GunSmithTableIngredient gi = inputs.get(idx);
                if (gi == null) continue;
                Ingredient ing = gi.getIngredient();
                if (ing == null || ing.isEmpty()) continue;
                boolean hasNbt = false;
                for (ItemStack m : ing.getItems()) {
                    if (!m.isEmpty() && m.has(DataComponents.CUSTOM_DATA) && !m.get(DataComponents.CUSTOM_DATA).isEmpty()) { hasNbt = true; break; }
                }
                for (ItemStack m : ing.getItems()) {
                    if (m.isEmpty()) continue;
                    TACZ_INDEX.computeIfAbsent(m.getItem().toString(), k -> new ArrayList<>())
                        .add(new TaczIngredient(rid, idx, ing, hasNbt));
                }
            }
        }
        indexServer = player.getServer();
        indexRecipeCount = player.getServer().getRecipeManager().getRecipes().size();
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}

