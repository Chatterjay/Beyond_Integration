package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.feature.crafting.*;
import com.solr98.beyondintegration.init.ModMenus;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** 打开维度存储菜单请求包（C2S）：客户端请求服务端为玩家打开指定类型的维度机器菜单（存储/铁砧/切石/研磨/锻造/合成/附魔台） */
public record OpenStorageMenuPacket(Type type) {
    /**
     * 要打开的菜单类型；id 为服务端配置（workstations.enabled）使用的稳定标识。
     * 注意：STORAGE 实为 BD 终端界面（网络存储/合成终端），并非工作台，
     * 不受工作台可用列表（workstations.enabled）控制。
     */
    public enum Type {
        STORAGE("storage"), ANVIL("anvil"), CUT("cut"), GRIND("grind"),
        SMITH("smith"), CRAFT("craft"), ENCHANT("enchant");

        private final String id;
        Type(String id) { this.id = id; }
        /** 配置/语言键使用的稳定 ID */
        public String id() { return id; }
    }
    /** 将菜单类型枚举写入缓冲区 */
    public static void encode(OpenStorageMenuPacket m, FriendlyByteBuf b){b.writeEnum(m.type);}
    /** 从缓冲区读取菜单类型并还原数据包 */
    public static OpenStorageMenuPacket decode(FriendlyByteBuf b){return new OpenStorageMenuPacket(b.readEnum(Type.class));}
    /** 服务端执行：校验工作台启用状态，获取玩家主网络，按类型打开对应菜单 */
    public static void handle(OpenStorageMenuPacket m, Supplier<NetworkEvent.Context> ctx){
        ctx.get().enqueueWork(()->{
            ServerPlayer p=ctx.get().getSender();if(p==null)return;
            // 服务端权威校验：工作台需在可用列表中（STORAGE 实为 BD 终端界面，不受列表控制）
            if (m.type() != Type.STORAGE
                    && !com.solr98.beyondintegration.CommandConfig.isWorkstationEnabled(m.type().id())) {
                p.displayClientMessage(Component.translatable(
                        "message.beyond_integration.workstation.disabled",
                        Component.translatable("gui.beyond_integration.mode." + m.type().id())), true);
                return;
            }
            // 献祭激活检查（可选平衡项）：未激活的工作台拒绝打开（客户端点击未激活按钮会改发激活请求）
            if (m.type() != Type.STORAGE
                    && com.solr98.beyondintegration.CommandConfig.isWorkstationActivationEnabled()
                    && com.solr98.beyondintegration.feature.workstation.WorkstationActivation.isActivatable(m.type().id())) {
                DimensionsNet activationNet = DimensionsNet.getPrimaryNetFromPlayer(p);
                if (!com.solr98.beyondintegration.feature.workstation.WorkstationActivation
                        .isActivated(activationNet, m.type().id())) {
                    p.displayClientMessage(Component.translatable(
                            "message.beyond_integration.workstation.not_activated",
                            Component.translatable("gui.beyond_integration.mode." + m.type().id()),
                            com.solr98.beyondintegration.feature.workstation.WorkstationActivation.costName(m.type().id())), true);
                    return;
                }
            }
            DimensionsNet net=DimensionsNet.getPrimaryNetFromPlayer(p);if(net==null)return;
            var s=net.getUnifiedStorage();
            p.openMenu(new MenuProvider(){
                @Override public Component getDisplayName(){return Component.translatable("menu.title.beyonddimensions.dimensionnetmenu");}
                @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player pl){
                    return switch(m.type){
                        case STORAGE -> new DimensionsStorageMenu(ModMenus.STORAGE.get(),id,inv,s);
                        case ANVIL -> new DimensionsAnvilMenu(ModMenus.ANVIL.get(),id,inv,s);
                        case CUT -> new DimensionsCutMenu(ModMenus.CUT.get(),id,inv,s);
                        case GRIND -> new DimensionsGrindMenu(ModMenus.GRIND.get(),id,inv,s);
                        case SMITH -> new DimensionsSmithMenu(ModMenus.SMITH.get(),id,inv,s);
                        case CRAFT -> new DimensionsCraftMenu(ModMenus.CRAFT.get(),id,inv,s);
                        case ENCHANT -> new DimensionsEnchantMenu(ModMenus.ENCHANT.get(),id,inv,s);
                    };
                }
            });
        });ctx.get().setPacketHandled(true);
    }
}
