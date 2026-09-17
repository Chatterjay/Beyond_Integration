package com.solr98.beyondintegration.network.payload;

import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.client.gui.WorkstationModeConstants;
import com.solr98.beyondintegration.init.*;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 打开维度存储菜单请求包（客户端 → 服务端）。
 * 按 mode 枚举在服务端为玩家打开对应菜单（存储/铁砧/切割/研磨/锻压/合成/附魔）。
 * 附魔台（ENCHANT）携带客户端持久化偏好 apoth（ClientConfig）：true=神化模式菜单（需加载 Apothic Enchanting），
 * false=原版模式菜单。
 * TYPE: beyond_integration:open_storage_menu；STREAM_CODEC 读写 Mode 枚举与布尔。
 */
public record OpenStorageMenuPayload(WorkstationModeConstants.Mode mode, boolean apoth) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenStorageMenuPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.parse(BeyondIntegration.MODID + ":open_storage_menu"));
    public static final StreamCodec<FriendlyByteBuf, OpenStorageMenuPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public OpenStorageMenuPayload decode(FriendlyByteBuf b) { return new OpenStorageMenuPayload(b.readEnum(WorkstationModeConstants.Mode.class), b.readBoolean()); }
        @Override public void encode(FriendlyByteBuf b, OpenStorageMenuPayload p) { b.writeEnum(p.mode()); b.writeBoolean(p.apoth()); }
    };

    @Override public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(OpenStorageMenuPayload p, IPayloadContext ctx) {
        // 服务端处理：校验工作台可用列表，无主网络则忽略，否则按模式打开对应维度菜单
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                // 服务端权威校验：工作台需在可用列表中（STORAGE 实为 BD 终端界面，不受列表控制）
                String wsId = p.mode().name().toLowerCase(java.util.Locale.ROOT);
                if (p.mode() != WorkstationModeConstants.Mode.STORAGE
                        && !com.solr98.beyondintegration.CommandConfig.isWorkstationEnabled(wsId)) {
                    sp.displayClientMessage(Component.translatable(
                            "message.beyond_integration.workstation.disabled",
                            Component.translatable("gui.beyond_integration.mode." + wsId)), true);
                    return;
                }
                // 献祭激活检查（可选平衡项）：未激活的工作台拒绝打开（客户端点击未激活按钮会改发激活请求）
                if (p.mode() != WorkstationModeConstants.Mode.STORAGE
                        && com.solr98.beyondintegration.CommandConfig.isWorkstationActivationEnabled()
                        && com.solr98.beyondintegration.feature.workstation.WorkstationActivation.isActivatable(wsId)) {
                    DimensionsNet activationNet = DimensionsNet.getPrimaryNetFromPlayer(sp);
                    if (!com.solr98.beyondintegration.feature.workstation.WorkstationActivation
                            .isActivated(activationNet, wsId)) {
                        sp.displayClientMessage(Component.translatable(
                                "message.beyond_integration.workstation.not_activated",
                                Component.translatable("gui.beyond_integration.mode." + wsId),
                                com.solr98.beyondintegration.feature.workstation.WorkstationActivation.costName(wsId)), true);
                        return;
                    }
                }
                DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(sp);
                if (net == null) return;
                var s = net.getUnifiedStorage();
                sp.openMenu(new MenuProvider() {
                    @Override public Component getDisplayName() { return Component.translatable("menu.title.beyonddimensions.dimensionnetmenu"); }
                    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player pl) {
                        return switch (p.mode()) {
                            case STORAGE -> new DimensionsStorageMenu(ModMenus.STORAGE.get(), id, inv, s);
                            case ANVIL -> new DimensionsAnvilMenu(ModMenus.ANVIL.get(), id, inv, s);
                            case CUT -> new DimensionsCutMenu(ModMenus.CUT.get(), id, inv, s);
                            case GRIND -> new DimensionsGrindMenu(ModMenus.GRIND.get(), id, inv, s);
                            case SMITH -> new DimensionsSmithMenu(ModMenus.SMITH.get(), id, inv, s);
                            case CRAFT -> new DimensionsCraftMenu(ModMenus.CRAFT.get(), id, inv, s);
                            case ENCHANT -> openEnchantMenu(id, inv, s, p.apoth());
                        };
                    }
                });
            }
        });
    }

    /** 附魔台：按偏好（客户端配置，需神化已加载）选择原版/神化菜单类型 */
    private static AbstractContainerMenu openEnchantMenu(int id, Inventory inv, AbstractUnorderedStackHandler s, boolean apoth) {
        // TODO 神化附魔工作台开发中，暂时强制原版模式（恢复时取消注释下方分支即可）
        // if (apoth && net.neoforged.fml.ModList.get().isLoaded("apothic_enchanting")) {
        //     return new DimensionsApothEnchantMenu(ModMenus.ENCHANT_APOTH.get(), id, inv, s, inv.player.level());
        // }
        return new DimensionsEnchantMenu(ModMenus.ENCHANT.get(), id, inv, s, inv.player.level());
    }
}