package com.solr98.beyondintegration.command.util;

import com.solr98.beyondintegration.handler.NetworkNameProvider;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 网络查询与统计工具类：提供维度网络（DimensionsNet）的资源统计、
 * 库存容量判断、玩家列表获取、玩家权限等级查询及玩家所属网络列表
 * 等命令展示所需的数据查询能力。
 */
public class NetworkUtils {

    /** 网络资源统计结果：记录物品/流体/能量的类型数与总量。 */
    public static class NetworkStats {
        public int itemTypes = 0;
        public int fluidTypes = 0;
        public int energyTypes = 0;
        public BigInteger itemTotal = BigInteger.ZERO;
        public BigInteger fluidTotal = BigInteger.ZERO;
        public BigInteger energyTotal = BigInteger.ZERO;

        /** 资源类型总数（三类之和）。 */
        public int getTotalTypes() { return itemTypes + fluidTypes + energyTypes; }
        /** 三类资源总量之和。 */
        public BigInteger getTotalResources() { return itemTotal.add(fluidTotal).add(energyTotal); }
    }

    /** 网络成员玩家列表：按所有者/管理员/成员角色分类存储玩家名。 */
    public static class PlayerList {
        public String owner = "";
        public final List<String> owners = new ArrayList<>();
        public final List<String> managers = new ArrayList<>();
        public final List<String> members = new ArrayList<>();
        public boolean hasPlayers() { return !owner.isEmpty() || !managers.isEmpty() || !members.isEmpty(); }
        public int getTotalPlayers() {
            int c = owner.isEmpty() ? 0 : 1;
            c += managers.size();
            c += members.size();
            return c;
        }
    }

    /** 网络概览信息：ID、权限权重与等级、所有者名、人数及自定义名称。 */
    public static class NetInfo {
        public int netId;
        public int permissionWeight;
        public NetworkPermission permissionLevel;
        public String ownerName;
        public int playerCount;
        public int managerCount;
        public String netName;

        public NetInfo(int netId, int permissionWeight, NetworkPermission permissionLevel, String ownerName, int playerCount, int managerCount, String netName) {
            this.netId = netId;
            this.permissionWeight = permissionWeight;
            this.permissionLevel = permissionLevel;
            this.ownerName = ownerName;
            this.playerCount = playerCount;
            this.managerCount = managerCount;
            this.netName = netName;
        }
    }

    /** 统计网络的物品/流体/能量类型数与总量（已删除或空网络返回空统计）。 */
    public static NetworkStats getNetworkStats(DimensionsNet net) {
        NetworkStats stats = new NetworkStats();
        if (net == null || net.deleted) return stats;
        for (KeyAmount ka : net.getUnifiedStorage().getStorage()) {
            Object key = ka.key();
            long amount = ka.amount();
            if (key instanceof ItemStackKey) {
                stats.itemTypes++;
                stats.itemTotal = stats.itemTotal.add(BigInteger.valueOf(amount));
            } else if (key instanceof FluidStackKey) {
                stats.fluidTypes++;
                stats.fluidTotal = stats.fluidTotal.add(BigInteger.valueOf(amount));
            } else if (key instanceof EnergyStackKey) {
                stats.energyTypes++;
                stats.energyTotal = stats.energyTotal.add(BigInteger.valueOf(amount));
            }
        }
        return stats;
    }

    /** 查询网络中指定物品的可用数量。 */
    public static long getAvailableItemCount(DimensionsNet net, ItemStackKey key) {
        return net.getUnifiedStorage().getStackByKey(key).amount();
    }

    /** 查询网络中指定流体的可用数量。 */
    public static long getAvailableFluidCount(DimensionsNet net, FluidStackKey key) {
        return net.getUnifiedStorage().getStackByKey(key).amount();
    }

    /** 查询网络中指定能量类型的可用数量。 */
    public static long getAvailableEnergyCount(DimensionsNet net, EnergyStackKey key) {
        return net.getUnifiedStorage().getStackByKey(key).amount();
    }

    /** 判断网络是否有足够容量再存入指定数量物品。 */
    public static boolean hasEnoughStorageForItem(DimensionsNet net, ItemStackKey key, long amountToAdd) {
        long current = net.getUnifiedStorage().getStackByKey(key).amount();
        long cap = net.getUnifiedStorage().getSlotCapacity(0);
        if (cap <= 0) cap = Long.MAX_VALUE;
        return current + amountToAdd <= cap;
    }

    /** 判断网络是否有足够容量再存入指定数量流体。 */
    public static boolean hasEnoughStorageForFluid(DimensionsNet net, FluidStackKey key, long amountToAdd) {
        long current = net.getUnifiedStorage().getStackByKey(key).amount();
        long cap = net.getUnifiedStorage().getSlotCapacity(0);
        if (cap <= 0) cap = Long.MAX_VALUE;
        return current + amountToAdd <= cap;
    }

    /** 判断网络是否有足够容量再存入指定数量能量。 */
    public static boolean hasEnoughStorageForEnergy(DimensionsNet net, EnergyStackKey key, long amountToAdd) {
        long current = net.getUnifiedStorage().getStackByKey(key).amount();
        long cap = net.getUnifiedStorage().getSlotCapacity(0);
        if (cap <= 0) cap = Long.MAX_VALUE;
        return current + amountToAdd <= cap;
    }

    /** 获取网络的玩家列表（所有者/管理员/成员），名称通过服务器查询解析。 */
    public static PlayerList getNetworkPlayerList(DimensionsNet net, MinecraftServer server) {
        PlayerList list = new PlayerList();
        if (net == null || server == null) return list;

        UUID ownerUuid = net.getOwner();
        if (ownerUuid != null) {
            String name = CommandUtils.getPlayerNameByUUID(ownerUuid, server);
            if (name != null && !name.isEmpty()) {
                list.owner = name;
                list.owners.add(name);
            }
        }
        for (UUID muid : net.getManagers()) {
            if (ownerUuid != null && muid.equals(ownerUuid)) continue;
            String name = CommandUtils.getPlayerNameByUUID(muid, server);
            if (name != null && !name.isEmpty()) list.managers.add(name);
        }
        for (UUID puid : net.getPlayers()) {
            if (ownerUuid != null && puid.equals(ownerUuid)) continue;
            if (net.getManagers().contains(puid)) continue;
            String name = CommandUtils.getPlayerNameByUUID(puid, server);
            if (name != null && !name.isEmpty()) list.members.add(name);
        }
        return list;
    }

    /** 获取玩家对指定网络的权限等级。 */
    public static NetworkPermission getPlayerPermissionLevel(ServerPlayer player, DimensionsNet net) {
        return NetworkPermission.fromPlayer(player, net);
    }

    /** 获取权限等级的本地化显示文本。 */
    public static String getPermissionLevelDisplay(NetworkPermission permissionLevel) {
        return permissionLevel.getDisplay();
    }

    /** 获取玩家的所有网络，主网络排在最前。 */
    public static List<DimensionsNet> getPlayerNetsPrimaryFirst(ServerPlayer player) {
        List<DimensionsNet> result = new ArrayList<>();
        DimensionsNet primary = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (primary != null) result.add(primary);
        for (var net : DimensionsNet.getAllNetFromPlayer(player)) {
            if (primary == null || net.getId() != primary.getId()) result.add(net);
        }
        return result;
    }

    /**
     * 获取玩家所属的全部网络信息，按权限权重降序（所有者 &gt; 管理员 &gt; 成员）
     * 再按网络 ID 升序排列。
     */
    public static List<NetInfo> getPlayerNetworks(ServerPlayer player, MinecraftServer server) {
        List<NetInfo> networks = new ArrayList<>();
        if (server == null) return networks;
        for (int netId = 0; netId < 10000; netId++) {
            DimensionsNet net = DimensionsNet.getNetFromId(netId);
            if (net != null && !net.deleted && net.getPlayers().contains(player.getUUID())) {
                int permissionWeight;
                NetworkPermission permissionLevel;
                if (net.isOwner(player.getUUID())) {
                    permissionWeight = 3;
                    permissionLevel = NetworkPermission.OWNER;
                } else if (net.isManager(player.getUUID())) {
                    permissionWeight = 2;
                    permissionLevel = NetworkPermission.MANAGER;
                } else {
                    permissionWeight = 1;
                    permissionLevel = NetworkPermission.MEMBER;
                }
                String ownerName = CommandUtils.getNetworkOwnerName(net, server);
                String netName = net instanceof NetworkNameProvider nnp ? nnp.getCustomName() : "";
                networks.add(new NetInfo(netId, permissionWeight, permissionLevel, ownerName, net.getPlayers().size(), net.getManagers().size(), netName));
            }
        }
        networks.sort((a, b) -> {
            int wc = Integer.compare(b.permissionWeight, a.permissionWeight);
            return wc != 0 ? wc : Integer.compare(a.netId, b.netId);
        });
        return networks;
    }

    /**
     * 通过反射读取网络内部累计时间，计算水晶生成剩余刻数
     * （负数表示功能关闭或读取失败）。
     */
    public static int getCrystalRemainingTime(DimensionsNet net) {
        try {
            int crystalGenerateTime = com.wintercogs.beyonddimensions.config.ServerConfigRuntime.crystalGenerateTime;
            if (crystalGenerateTime <= 0) return -1;
            var field = DimensionsNet.class.getDeclaredField("currentTime");
            field.setAccessible(true);
            int elapsedTime = field.getInt(net);
            return Math.max(0, crystalGenerateTime * 20 - elapsedTime);
        } catch (Exception e) {
            return -1;
        }
    }
}

