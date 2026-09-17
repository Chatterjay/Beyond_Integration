package com.solr98.beyondintegration.network;

import com.solr98.beyondintegration.BeyondIntegration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** 网络包注册与发送中心：统一创建 SimpleChannel，注册全部数据包并封装向服务端/客户端发送的方法 */
public class PacketHandler {

    /** 网络协议版本号，客户端与服务端不一致时拒绝连接 */
    private static final String PROTOCOL_VERSION = "1";
    /** 自增的包 ID 计数器 */
    private static int id = 0;

    /** 模组主网络通道实例 */
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(BeyondIntegration.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    /** 注册所有数据包；superbwarfare 与 tacz 相关包仅在对应模组加载时注册 */
    public static void register() {
        INSTANCE.registerMessage(id++, RecipeFillPacket.class,
                RecipeFillPacket::encode,
                RecipeFillPacket::decode,
                RecipeFillPacket::handle);

        INSTANCE.registerMessage(id++, ProtectItemPacket.class,
                ProtectItemPacket::encode,
                ProtectItemPacket::decode,
                ProtectItemPacket::handle);

        INSTANCE.registerMessage(id++, NetworkItemCountsPacket.class,
                NetworkItemCountsPacket::encode,
                NetworkItemCountsPacket::decode,
                NetworkItemCountsPacket::handle);

        INSTANCE.registerMessage(id++, SetAnvilNamePacket.class,
                SetAnvilNamePacket::encode,
                SetAnvilNamePacket::decode,
                SetAnvilNamePacket::handle);

        INSTANCE.registerMessage(id++, OpenStorageMenuPacket.class,
                OpenStorageMenuPacket::encode,
                OpenStorageMenuPacket::decode,
                OpenStorageMenuPacket::handle);

        INSTANCE.registerMessage(id++, CleanWorkstationPacket.class,
                CleanWorkstationPacket::encode,
                CleanWorkstationPacket::decode,
                CleanWorkstationPacket::handle);

        INSTANCE.registerMessage(id++, ToggleEnchantSeparationPacket.class,
                ToggleEnchantSeparationPacket::encode,
                ToggleEnchantSeparationPacket::decode,
                ToggleEnchantSeparationPacket::handle);

        INSTANCE.registerMessage(id++, EnchantSeparationSyncPacket.class,
                EnchantSeparationSyncPacket::encode,
                EnchantSeparationSyncPacket::decode,
                EnchantSeparationSyncPacket::handle);

        INSTANCE.registerMessage(id++, RequestEnchantSeparationPacket.class,
                RequestEnchantSeparationPacket::encode,
                RequestEnchantSeparationPacket::decode,
                RequestEnchantSeparationPacket::handle);

        INSTANCE.registerMessage(id++, ToggleEnergyChargePacket.class,
                ToggleEnergyChargePacket::encode,
                ToggleEnergyChargePacket::decode,
                ToggleEnergyChargePacket::handle);

        INSTANCE.registerMessage(id++, EnergyChargeSyncPacket.class,
                EnergyChargeSyncPacket::encode,
                EnergyChargeSyncPacket::decode,
                EnergyChargeSyncPacket::handle);

        INSTANCE.registerMessage(id++, RequestEnergyChargePacket.class,
                RequestEnergyChargePacket::encode,
                RequestEnergyChargePacket::decode,
                RequestEnergyChargePacket::handle);

        INSTANCE.registerMessage(id++, RefreshEnchantPacket.class,
                RefreshEnchantPacket::encode,
                RefreshEnchantPacket::decode,
                RefreshEnchantPacket::handle);

        INSTANCE.registerMessage(id++, EnchantCluesPacket.class,
                EnchantCluesPacket::encode,
                EnchantCluesPacket::decode,
                EnchantCluesPacket::handle);

        INSTANCE.registerMessage(id++, ActivateWorkstationPacket.class,
                ActivateWorkstationPacket::encode,
                ActivateWorkstationPacket::decode,
                ActivateWorkstationPacket::handle);

        INSTANCE.registerMessage(id++, RequestWorkstationActivationPacket.class,
                RequestWorkstationActivationPacket::encode,
                RequestWorkstationActivationPacket::decode,
                RequestWorkstationActivationPacket::handle);

        INSTANCE.registerMessage(id++, WorkstationActivationSyncPacket.class,
                WorkstationActivationSyncPacket::encode,
                WorkstationActivationSyncPacket::decode,
                WorkstationActivationSyncPacket::handle);

        INSTANCE.registerMessage(id++, ExtractNetworkItemPacket.class,
                ExtractNetworkItemPacket::encode,
                ExtractNetworkItemPacket::decode,
                ExtractNetworkItemPacket::handle);



        if (ModList.get().isLoaded("superbwarfare")) {
            INSTANCE.registerMessage(id++, SuperbAmmoStatusResponsePacket.class,
                    SuperbAmmoStatusResponsePacket::encode,
                    SuperbAmmoStatusResponsePacket::decode,
                    SuperbAmmoStatusResponsePacket::handle);
            INSTANCE.registerMessage(id++, RequestSuperbAmmoStatusPacket.class,
                    RequestSuperbAmmoStatusPacket::encode,
                    RequestSuperbAmmoStatusPacket::decode,
                    RequestSuperbAmmoStatusPacket::handle);
            INSTANCE.registerMessage(id++, RequestSuperbAmmoExtractPacket.class,
                    RequestSuperbAmmoExtractPacket::encode,
                    RequestSuperbAmmoExtractPacket::decode,
                    RequestSuperbAmmoExtractPacket::handle);
            INSTANCE.registerMessage(id++, SuperbAmmoDeltaS2CPacket.class,
                    SuperbAmmoDeltaS2CPacket::encode,
                    SuperbAmmoDeltaS2CPacket::decode,
                    SuperbAmmoDeltaS2CPacket::handle);
            INSTANCE.registerMessage(id++, RequestItemAmmoPacket.class,
                    RequestItemAmmoPacket::encode,
                    RequestItemAmmoPacket::decode,
                    RequestItemAmmoPacket::handle);
            INSTANCE.registerMessage(id++, ItemAmmoResponsePacket.class,
                    ItemAmmoResponsePacket::encode,
                    ItemAmmoResponsePacket::decode,
                    ItemAmmoResponsePacket::handle);
        }

        if (ModList.get().isLoaded("tacz")) {
            INSTANCE.registerMessage(id++, RequestNetworkItemsPacket.class,
                    RequestNetworkItemsPacket::encode,
                    RequestNetworkItemsPacket::decode,
                    RequestNetworkItemsPacket::handle);
            INSTANCE.registerMessage(id++, TaczCraftPacket.class,
                    TaczCraftPacket::encode,
                    TaczCraftPacket::decode,
                    TaczCraftPacket::handle);
            INSTANCE.registerMessage(id++, RequestAmmoCountPacket.class,
                    RequestAmmoCountPacket::encode,
                    RequestAmmoCountPacket::decode,
                    RequestAmmoCountPacket::handle);
            INSTANCE.registerMessage(id++, AmmoCountResponsePacket.class,
                    AmmoCountResponsePacket::encode,
                    AmmoCountResponsePacket::decode,
                    AmmoCountResponsePacket::handle);
            INSTANCE.registerMessage(id++, TaczAmmoPushS2CPacket.class,
                    TaczAmmoPushS2CPacket::encode,
                    TaczAmmoPushS2CPacket::decode,
                    TaczAmmoPushS2CPacket::handle);
        }
    }

    /** 客户端 → 服务端发送 */
    public static void sendToServer(Object msg) {
        INSTANCE.sendToServer(msg);
    }

    /** 服务端 → 指定玩家发送 */
    public static void sendToPlayer(ServerPlayer player, Object msg) {
        INSTANCE.sendTo(msg, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
}
