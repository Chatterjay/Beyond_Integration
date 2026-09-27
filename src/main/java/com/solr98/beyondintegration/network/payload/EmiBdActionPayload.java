package com.solr98.beyondintegration.network.payload;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.compat.EmiBdCompat;
import com.solr98.beyondintegration.compat.bd.BdTerminalActions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * BD 终端批量操作请求包（客户端 → 服务端）。
 *
 * <p>承载 EmiLink 移植过来的四类 BD 终端操作，实际执行全部交给
 * {@link BdTerminalActions}：</p>
 * <ol>
 *   <li>从网络提取物品（{@link #ACTION_EXTRACT_ONE} / {@link #ACTION_TRANSFER_NET_TO_INV}）</li>
 *   <li>终端与玩家背包之间的批量转移（{@link #ACTION_TRANSFER_MAIN_TO_NET} /
 *       {@link #ACTION_TRANSFER_HOTBAR_TO_NET} / {@link #ACTION_TRANSFER_ALL_TO_NET}）</li>
 *   <li>合成结果的 Space 点击批量合成（{@link #ACTION_BATCH_CRAFT}）</li>
 *   <li>合成网格清理并返还网络（{@link #ACTION_CLEAN_CRAFT_TO_NETWORK}）</li>
 * </ol>
 *
 * <p><b>刻意不含</b> "从物品管理器取物"：EMI 物品管理器侧的取物不在本次同步范围内。</p>
 *
 * <p>线格式仍是旧版的 {@code (ItemStack, int)}，没有新增字段；旧动作码 1（清理网格）与
 * 3（整个背包存入网络）语义不变，旧动作码 0 由"整个背包存入"细化为"主背包(9..35)存入"，
 * 其余编号为新增动作。</p>
 */
public record EmiBdActionPayload(ItemStack stack, int action) implements CustomPacketPayload {

    // ── 动作码 ──
    /** 背包主区（9..35）中与 {@code stack} 相同的物品批量存入网络。 */
    public static final int ACTION_TRANSFER_MAIN_TO_NET = 0;
    /** 清空合成网格并优先返还网络。 */
    public static final int ACTION_CLEAN_CRAFT_TO_NETWORK = 1;
    /** 从网络提取一组 {@code stack} 到玩家背包（BD 网络槽 Shift+点击）。 */
    public static final int ACTION_EXTRACT_ONE = 2;
    /** 整个背包（0..35）中与 {@code stack} 相同的物品批量存入网络。 */
    public static final int ACTION_TRANSFER_ALL_TO_NET = 3;
    /** 连续合成结果槽（BD 合成界面 Space+点击结果槽）。 */
    public static final int ACTION_BATCH_CRAFT = 4;
    /** 从网络提取所有与 {@code stack} 相同的物品到玩家背包（BD 网络槽 Space+点击）。 */
    public static final int ACTION_TRANSFER_NET_TO_INV = 5;
    /** 快捷栏（0..8）中与 {@code stack} 相同的物品批量存入网络。 */
    public static final int ACTION_TRANSFER_HOTBAR_TO_NET = 6;

    public static final Type<EmiBdActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BeyondIntegration.MODID, "emi_bd_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EmiBdActionPayload> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, EmiBdActionPayload::stack,
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT, EmiBdActionPayload::action,
            EmiBdActionPayload::new);

    public static void handle(EmiBdActionPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            // 冲突门控：存在 EmiLink 时本模组的 BD 终端操作整体让路，服务端同样拒绝执行。
            // 注意这里只能用 "是否冲突" 判定，不能用 EmiBdCompat.enabled()：
            // EMI 是纯客户端模组，专用服务端上 ModList 里根本没有 emi，
            // 用 enabled() 会让专用服务端把全部 BD 终端操作静默丢弃。
            if (EmiBdCompat.conflictDetected()) return;
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            ItemStack target = payload.stack();
            switch (payload.action()) {
                // 1) 从网络提取物品
                case ACTION_EXTRACT_ONE -> {
                    if (!target.isEmpty()) BdTerminalActions.extractOneStack(player, target);
                }
                case ACTION_TRANSFER_NET_TO_INV -> {
                    if (!target.isEmpty()) BdTerminalActions.extractAllMatching(player, target);
                }
                // 2) 终端 ↔ 背包批量转移
                case ACTION_TRANSFER_MAIN_TO_NET -> {
                    if (!target.isEmpty()) BdTerminalActions.depositMatching(player, target, BdTerminalActions.MODE_MAIN);
                }
                case ACTION_TRANSFER_HOTBAR_TO_NET -> {
                    if (!target.isEmpty()) BdTerminalActions.depositMatching(player, target, BdTerminalActions.MODE_HOTBAR);
                }
                case ACTION_TRANSFER_ALL_TO_NET -> {
                    if (!target.isEmpty()) BdTerminalActions.depositMatching(player, target, BdTerminalActions.MODE_ALL);
                }
                // 3) 合成结果批量合成
                case ACTION_BATCH_CRAFT -> BdTerminalActions.batchCraft(player, BdTerminalActions.MAX_BATCH_CRAFT);
                // 4) 合成网格清理并返还网络
                case ACTION_CLEAN_CRAFT_TO_NETWORK -> BdTerminalActions.cleanCraftGridToNetwork(player);
                default -> { }
            }
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
