package com.solr98.beyondintegration.network;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.IStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import java.util.*;

/**
 * Tacz 枪械台批量合成请求包（客户端 → 服务端）。
 * 指定配方 ID、合成次数与产物去向（toNetwork=true 放入网络，否则丢到脚下），
 * 服务端 handle 优先消耗背包、不足时从网络扣取原料，结束后回发
 * {@link NetworkItemCountsPacket} 同步最新计数与合成结果。
 * TYPE: beyond_integration:tacz_craft；STREAM_CODEC 读写 recipeId/count/toNetwork。
 */
public record TaczCraftPacket(ResourceLocation recipeId, int count, boolean toNetwork) implements CustomPacketPayload {
    public static final Type<TaczCraftPacket> TYPE = new Type<>(ResourceLocation.parse("beyond_integration:tacz_craft"));
    public static final StreamCodec<FriendlyByteBuf, TaczCraftPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override public @NotNull TaczCraftPacket decode(FriendlyByteBuf buf) {
            return new TaczCraftPacket(buf.readResourceLocation(), buf.readVarInt(), buf.readBoolean());
        }
        @Override public void encode(FriendlyByteBuf buf, TaczCraftPacket p) {
            buf.writeResourceLocation(p.recipeId); buf.writeVarInt(p.count); buf.writeBoolean(p.toNetwork);
        }
    };

    public static void handle(final TaczCraftPacket packet, final IPayloadContext context) {
        // 服务端处理：校验网络/配方后执行批量合成，并同步最新物品计数
        context.enqueueWork(() -> {
            var player = context.player();
            if (!(player instanceof ServerPlayer sp)) return;

            if (sp.level() == null) return;

            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(sp);
            if (net == null) {
                sp.sendSystemMessage(Component.translatable("message.beyond_integration.no_network"));
                return;
            }
            RequestNetworkItemsPacket.ensureIndex(sp);

            var recipeOpt = sp.getServer().getRecipeManager().byKey(packet.recipeId);
            if (recipeOpt.isEmpty()) return;
            var holder = recipeOpt.get();
            if (!(holder.value() instanceof GunSmithTableRecipe taczRecipe)) return;
            List<GunSmithTableIngredient> inputs = taczRecipe.getInputs();
            if (inputs == null || inputs.isEmpty()) return;

            // 配方驱动定向扫描：构建材料输入清单（无 NBT → IDENTITY 定向精确键；
            // partial_nbt 候选 → PARTIAL_NBT 先定向、不足时全桶兜底）
            List<com.solr98.beyondintegration.feature.crafting.RecipeMaterialScanner.MaterialInput> materialInputs = new ArrayList<>();
            for (int ii = 0; ii < inputs.size(); ii++) {
                GunSmithTableIngredient gi = inputs.get(ii);
                if (gi == null) continue;
                Ingredient ing = gi.getIngredient();
                if (ing == null || ing.isEmpty()) continue;
                boolean hasNbt = false;
                for (ItemStack m : ing.getItems()) {
                    if (!m.isEmpty() && m.has(DataComponents.CUSTOM_DATA) && !m.get(DataComponents.CUSTOM_DATA).isEmpty()) {
                        hasNbt = true;
                        break;
                    }
                }
                materialInputs.add(new com.solr98.beyondintegration.feature.crafting.RecipeMaterialScanner.MaterialInput(
                        ii, List.of(ing.getItems()),
                        hasNbt ? com.solr98.beyondintegration.feature.crafting.RecipeMaterialScanner.MatchMode.PARTIAL_NBT
                               : com.solr98.beyondintegration.feature.crafting.RecipeMaterialScanner.MatchMode.IDENTITY,
                        ing, gi.getCount()));
            }

            var scanResult = com.solr98.beyondintegration.feature.crafting.RecipeMaterialScanner.scan(
                    net.getUnifiedStorage(), materialInputs);
            Map<Integer, Long> slotTotals = scanResult.slotTotals();
            Map<Integer, List<ItemStackKey>> ingredientKeys = scanResult.slotKeys();
            var storage = net.getUnifiedStorage();

            int crafted = 0;
            int requested = packet.count;

            // 逐次合成：最多 64 次，原料不足时中止并提示
            for (int c = 0; c < 64; c++) {
                if (requested > 0 && crafted >= requested) break;

                String missing = null;
                for (int i = 0; i < inputs.size(); i++) {
                    Ingredient ing = inputs.get(i).getIngredient();
                    int need = inputs.get(i).getCount();
                    if (ing == null || ing.isEmpty() || need <= 0) continue;

                    int inInv = 0;
                    for (int j = 0; j < sp.getInventory().getContainerSize(); j++) {
                        ItemStack stack = sp.getInventory().getItem(j);
                        if (!stack.isEmpty() && ing.test(stack)) inInv += stack.getCount();
                    }

                    long inNet = slotTotals.getOrDefault(i, 0L);
                    if (inInv + inNet < need) {
                        if (missing == null) {
                            ItemStack ex = ing.getItems().length > 0 ? ing.getItems()[0] : ItemStack.EMPTY;
                            missing = Component.translatable("message.beyond_integration.material_insufficient",
                                    ex.isEmpty() ? "?" : ex.getHoverName().getString()).getString();
                        }
                    }
                }

                if (missing != null) {
                    if (crafted == 0) sp.sendSystemMessage(Component.literal(missing));
                    break;
                }

                // 扣料：先扣玩家背包，不足部分从网络存储提取并更新槽位计数
                for (int i = 0; i < inputs.size(); i++) {
                    Ingredient ing = inputs.get(i).getIngredient();
                    int need = inputs.get(i).getCount();
                    if (ing == null || ing.isEmpty() || need <= 0) continue;

                    for (int j = 0; j < sp.getInventory().getContainerSize() && need > 0; j++) {
                        ItemStack stack = sp.getInventory().getItem(j);
                        if (stack.isEmpty() || !ing.test(stack)) continue;
                        int take = Math.min(need, stack.getCount());
                        stack.shrink(take);
                        need -= take;
                    }

                    if (need <= 0) continue;

                    List<ItemStackKey> keys = ingredientKeys.get(i);
                    if (keys != null) {
                        for (ItemStackKey ik : keys) {
                            if (need <= 0) break;
                            long avail = storage.getStackByKey(ik).amount();
                            if (avail <= 0) continue;
                            long take = Math.min(need, avail);
                            KeyAmount extracted = storage.extract(ik, take, false, false);
                            if (extracted.amount() > 0) {
                                need -= extracted.amount();
                                // 扣减槽位网络总量（回传客户端用）
                                slotTotals.merge(i, -extracted.amount(), Long::sum);
                            }
                        }
                    }
                }

                // 产出成品：按 toNetwork 决定放入网络存储或生成掉落物
                ItemStack result = taczRecipe.getResultItem(sp.level().registryAccess());
                if (!result.isEmpty()) {
                    if (packet.toNetwork) {
                        long left = net.getUnifiedStorage()
                                .insert(new ItemStackKey(result), result.getCount(), false).amount();
                        if (left > 0) {
                            // 网络容量不足：余量掉落兜底，避免产物丢失
                            ItemStack drop = result.copy();
                            drop.setCount((int) left);
                            var entity = new net.minecraft.world.entity.item.ItemEntity(
                                    sp.level(), sp.getX(), sp.getY() + 0.5, sp.getZ(), drop);
                            entity.setPickUpDelay(0);
                            sp.level().addFreshEntity(entity);
                        }
                    } else {
                        var entity = new net.minecraft.world.entity.item.ItemEntity(
                                sp.level(), sp.getX(), sp.getY() + 0.5, sp.getZ(), result.copy());
                        entity.setPickUpDelay(0);
                        sp.level().addFreshEntity(entity);
                    }
                }
                crafted++;
            }

            net.setDirty();

            if (sp.containerMenu instanceof com.tacz.guns.inventory.GunSmithTableMenu menu) {
                sp.inventoryMenu.broadcastFullState();
                com.tacz.guns.network.NetworkHandler.sendToClientPlayer(
                        new com.tacz.guns.network.message.ServerMessageCraft(menu.containerId), sp);
            }

            // 通知枪械台刷新界面，并向客户端回发最新物品计数与合成结果
            // 回传键与界面读取一致："recipeId|idx"（slotTotals 为每槽网络可用总量）
            ItemStack resultItem = crafted > 0 ? taczRecipe.getResultItem(sp.level().registryAccess()) : ItemStack.EMPTY;
            Map<String, Long> responseCounts = new HashMap<>();
            for (var e : slotTotals.entrySet()) {
                if (e.getValue() > 0) responseCounts.put(packet.recipeId + "|" + e.getKey(), e.getValue());
            }
            PacketHandler.sendToPlayer(sp, new NetworkItemCountsPacket(responseCounts, true, true,
                    net != null ? net.getId() : -1, net != null ? net.getCustomName() : "", resultItem, crafted));
        });
    }

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}

