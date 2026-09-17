package com.solr98.beyondintegration.jade;

import com.wintercogs.beyonddimensions.common.block.NetedBlock;
import com.wintercogs.beyonddimensions.common.block.entity.NetedBlockEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModList;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade 插件主入口：注册 BD 网络方块以及第三方模组（SuperbWarfare 载具/容器、
 * ywzj_vehicle 载具）的服务端数据提供器与客户端提示组件。
 */
@WailaPlugin
public class JadeBDPlugin implements IWailaPlugin {

    // 按类名反射注册载具服务端数据提供器（模组未加载时静默跳过）
    private void registerVehicleProviders(IWailaCommonRegistration registration, String className) {
        try {
            Class<? extends Entity> clazz = Class.forName(className).asSubclass(Entity.class);
            registration.registerEntityDataProvider(VehicleServerProvider.INSTANCE, clazz);
        } catch (ClassNotFoundException ignored) {}
    }

    // 按类名反射注册载具客户端提示组件（模组未加载时静默跳过）
    private void registerVehicleProvidersClient(IWailaClientRegistration registration, String className) {
        try {
            Class<? extends Entity> clazz = Class.forName(className).asSubclass(Entity.class);
            registration.registerEntityComponent(VehicleClientProvider.INSTANCE, clazz);
        } catch (ClassNotFoundException ignored) {}
    }

    // 服务端注册：加载 superbwarfare/ywzj_vehicle 时注册对应提供器，并始终注册 BD 网络方块
    @Override
    public void register(IWailaCommonRegistration registration) {
        if (ModList.get().isLoaded("superbwarfare")) {
            registerVehicleProviders(registration, "com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
            try {
                Class<?> containerBeClass = Class.forName("com.atsuishio.superbwarfare.block.entity.ContainerBlockEntity")
                        .asSubclass(net.minecraft.world.level.block.entity.BlockEntity.class);
                registration.registerBlockDataProvider(ContainerServerProvider.INSTANCE, containerBeClass);
            } catch (ClassNotFoundException ignored) {}
        }
        if (ModList.get().isLoaded("ywzj_vehicle")) {
            registerVehicleProviders(registration, "org.ywzj.vehicle.entity.vehicle.AbstractVehicle");
        }

        registration.registerBlockDataProvider(BlockServerProvider.INSTANCE, NetedBlockEntity.class);
    }

    // 客户端注册：与 register 对应的组件注册
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        if (ModList.get().isLoaded("superbwarfare")) {
            registerVehicleProvidersClient(registration, "com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity");
            try {
                Class<? extends Block> containerBlockClass = Class.forName("com.atsuishio.superbwarfare.block.ContainerBlock")
                        .asSubclass(Block.class);
                registration.registerBlockComponent(ContainerClientProvider.INSTANCE, containerBlockClass);
            } catch (ClassNotFoundException ignored) {}
        }
        if (ModList.get().isLoaded("ywzj_vehicle")) {
            registerVehicleProvidersClient(registration, "org.ywzj.vehicle.entity.vehicle.AbstractVehicle");
        }

        registration.registerBlockComponent(BlockClientProvider.INSTANCE, NetedBlock.class);
    }
}

