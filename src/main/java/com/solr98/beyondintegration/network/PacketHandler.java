package com.solr98.beyondintegration.network;
import com.solr98.beyondintegration.BeyondIntegration;
import com.solr98.beyondintegration.network.payload.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络包注册中心（NeoForge PayloadRegistrar）。
 * 统一注册全部 C2S/S2C 双向 payload 及其解码器、处理器，并提供便捷发送入口。
 */
@EventBusSubscriber(modid = BeyondIntegration.MODID)
public class PacketHandler {
    /** 注册所有自定义 payload 的收发方向与编解码器（版本 "1"） */
    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        registrar.playBidirectional(SuperbAmmoStatusResponsePacket.TYPE, SuperbAmmoStatusResponsePacket.STREAM_CODEC,
                new DirectionalPayloadHandler<>(SuperbAmmoStatusResponsePacket::handle, SuperbAmmoStatusResponsePacket::handle));
        registrar.playToServer(RequestSuperbAmmoStatusPacket.TYPE, RequestSuperbAmmoStatusPacket.STREAM_CODEC,
                RequestSuperbAmmoStatusPacket::handle);
        registrar.playToServer(RequestSuperbAmmoExtractPacket.TYPE, RequestSuperbAmmoExtractPacket.STREAM_CODEC,
                RequestSuperbAmmoExtractPacket::handle);
        registrar.playToClient(SuperbAmmoDeltaS2CPacket.TYPE, SuperbAmmoDeltaS2CPacket.STREAM_CODEC,
                SuperbAmmoDeltaS2CPacket::handle);
        registrar.playToServer(RequestItemAmmoPacket.TYPE, RequestItemAmmoPacket.STREAM_CODEC,
                RequestItemAmmoPacket::handle);
        registrar.playToClient(ItemAmmoResponsePacket.TYPE, ItemAmmoResponsePacket.STREAM_CODEC,
                ItemAmmoResponsePacket::handle);
        registrar.playToServer(ToggleEnchantSeparationPacket.TYPE, ToggleEnchantSeparationPacket.STREAM_CODEC,
                ToggleEnchantSeparationPacket::handle);
        registrar.playToClient(EnchantSeparationSyncPacket.TYPE, EnchantSeparationSyncPacket.STREAM_CODEC,
                EnchantSeparationSyncPacket::handle);
        registrar.playToServer(RequestEnchantSeparationPacket.TYPE, RequestEnchantSeparationPacket.STREAM_CODEC,
                RequestEnchantSeparationPacket::handle);
        registrar.playToServer(ToggleEnergyChargePacket.TYPE, ToggleEnergyChargePacket.STREAM_CODEC,
                ToggleEnergyChargePacket::handle);
        registrar.playToClient(EnergyChargeSyncPacket.TYPE, EnergyChargeSyncPacket.STREAM_CODEC,
                EnergyChargeSyncPacket::handle);
        registrar.playToServer(RequestEnergyChargePacket.TYPE, RequestEnergyChargePacket.STREAM_CODEC,
                RequestEnergyChargePacket::handle);
        registrar.playToServer(RequestAmmoCountPacket.TYPE, RequestAmmoCountPacket.STREAM_CODEC,
                RequestAmmoCountPacket::handle);
        registrar.playToClient(AmmoCountResponsePacket.TYPE, AmmoCountResponsePacket.STREAM_CODEC,
                AmmoCountResponsePacket::handle);
        registrar.playToClient(TaczAmmoPushPayload.TYPE, TaczAmmoPushPayload.STREAM_CODEC,
                TaczAmmoPushPayload::handle);
        registrar.playToServer(RequestNetworkItemsPacket.TYPE, RequestNetworkItemsPacket.STREAM_CODEC,
                RequestNetworkItemsPacket::handle);
        registrar.playToClient(NetworkItemCountsPacket.TYPE, NetworkItemCountsPacket.STREAM_CODEC,
                NetworkItemCountsPacket::handle);
        registrar.playToServer(TaczCraftPacket.TYPE, TaczCraftPacket.STREAM_CODEC,
                TaczCraftPacket::handle);
        registrar.playToClient(YwzjVehicleDataResponsePacket.TYPE, YwzjVehicleDataResponsePacket.STREAM_CODEC,
                YwzjVehicleDataResponsePacket::handle);
        registrar.playToServer(OpenStorageMenuPayload.TYPE, OpenStorageMenuPayload.STREAM_CODEC, OpenStorageMenuPayload::handle);
        registrar.playToServer(SetAnvilNamePayload.TYPE, SetAnvilNamePayload.STREAM_CODEC, SetAnvilNamePayload::handle);
        registrar.playToServer(RecipeFillPayload.TYPE, RecipeFillPayload.STREAM_CODEC, RecipeFillPayload::handle);
        registrar.playToServer(ProtectItemPayload.TYPE, ProtectItemPayload.STREAM_CODEC, ProtectItemPayload::handle);
        registrar.playToServer(com.solr98.beyondintegration.network.payload.CleanWorkstationPayload.TYPE, com.solr98.beyondintegration.network.payload.CleanWorkstationPayload.STREAM_CODEC, com.solr98.beyondintegration.network.payload.CleanWorkstationPayload::handle);
        registrar.playToServer(RefreshEnchantPayload.TYPE, RefreshEnchantPayload.STREAM_CODEC, RefreshEnchantPayload::handle);
        registrar.playToClient(EnchantCluesPayload.TYPE, EnchantCluesPayload.STREAM_CODEC, EnchantCluesPayload::handle);
        registrar.playToServer(ActivateWorkstationPayload.TYPE, ActivateWorkstationPayload.STREAM_CODEC, ActivateWorkstationPayload::handle);
        registrar.playToServer(RequestWorkstationActivationPayload.TYPE, RequestWorkstationActivationPayload.STREAM_CODEC, RequestWorkstationActivationPayload::handle);
        registrar.playToClient(WorkstationActivationSyncPayload.TYPE, WorkstationActivationSyncPayload.STREAM_CODEC, WorkstationActivationSyncPayload::handle);
        registrar.playToServer(ExtractNetworkItemPacket.TYPE, ExtractNetworkItemPacket.STREAM_CODEC, ExtractNetworkItemPacket::handle);
        registrar.playToServer(EmiBdActionPayload.TYPE, EmiBdActionPayload.STREAM_CODEC, EmiBdActionPayload::handle);
    }

    /** 向服务端发送任意自定义 payload */
    public static void sendToServer(CustomPacketPayload packet) {
        PacketDistributor.sendToServer(packet);
    }

    /** 向指定服务端玩家发送任意自定义 payload */
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }
}

