package com.solr98.beyondintegration.mixin;

import com.atsuishio.superbwarfare.data.gun.Ammo;
import com.atsuishio.superbwarfare.data.gun.AmmoConsumer;
import com.atsuishio.superbwarfare.data.gun.GunData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.tools.InventoryTool;
import com.solr98.beyondintegration.handler.VehicleNetCache;
import com.solr98.beyondintegration.handler.INetCachedVehicle;
import com.solr98.beyondintegration.handler.SuperbAmmoAccessor;
import com.solr98.beyondintegration.handler.SwAmmoTracker;
import com.solr98.beyondintegration.maid.MaidNetworkHelper;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.SuperbAmmoDeltaS2CPacket;
import com.solr98.beyondintegration.network.SuperbAmmoStatusResponsePacket;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 注入目标：Superb Warfare 的 {@code AmmoConsumer}（枪械弹药消耗逻辑）。
 * 目的：在弹药消耗时优先取用网络（维度网络）中的弹药，支持服务端玩家、载具、
 * 女仆等不同实体来源；消耗后同步推送最新的网络弹药数据包给客户端 HUD。
 */
@Mixin(value = AmmoConsumer.class, remap = false)
public abstract class AmmoConsumerMixin {

    // 已提示过"正在使用网络弹药"的玩家 UUID 与最近提示时间戳（防止频繁刷屏）
    @Unique
    private static final Map<UUID, Long> beyond$notifiedPlayers = new ConcurrentHashMap<>();
    // 网络弹药提示的最短间隔（毫秒）
    @Unique
    private static final long NOTIFY_INTERVAL_MS = 300_000;

    @Shadow(remap = false)
    private AmmoConsumer.AmmoConsumeType type;

    // Shadow 自目标类的弹药类型字段
    @Shadow(remap = false)
    private Ammo playerAmmoType;

    @Shadow(remap = false)
    private int loadAmount;

    @Shadow(remap = false)
    private ItemStack stack;

    // 拦截 consume：根据弹药消耗类型（玩家弹药/物品）分发到对应的网络消耗处理
    @Inject(method = "consume(Lcom/atsuishio/superbwarfare/data/gun/GunData;Lnet/minecraft/world/entity/Entity;I)I",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void onConsume(GunData data, Entity entity, int loads,
                           CallbackInfoReturnable<Integer> cir) {
        if (type == AmmoConsumer.AmmoConsumeType.PLAYER_AMMO) {
            handleConsumePlayerAmmo(entity, loads, cir);
        } else if (type == AmmoConsumer.AmmoConsumeType.ITEM && beyond$isExpAmmo()) {
            // 经验弹药（ammo 配置 "exp"/"exp N"）：玩家经验不足时从网络 XP 流体自动转化补给，
            // 补给后原逻辑（ExpAmmoStrategy）继续扣玩家经验
            handleConsumeExp(entity, loads);
        } else if (type == AmmoConsumer.AmmoConsumeType.ITEM) {
            handleConsumeItem(entity, loads, cir);
        } else if (type == AmmoConsumer.AmmoConsumeType.ENERGY) {
            // 能量武器射击触发点：原逻辑会从武器 FE 槽扣能量；
            // 此处先为玩家装备位（含手持武器）从主网络补能（网络有 FE 时武器常满，射击不中断）
            if (entity instanceof ServerPlayer player) {
                com.solr98.beyondintegration.handler.EnergyAmmoChargeHandler.chargePlayer(player);
            } else if (entity instanceof LivingEntity living) {
                // 女仆开火：从其绑定网络（饰品/女仆饰物背包中的终端）为装备位补能
                com.solr98.beyondintegration.handler.EnergyAmmoChargeHandler.chargeMaid(living);
            }
        }
    }

    /** 判断当前消耗器是否为经验弹药（ammo 字符串以 "exp" 开头，SW ExpAmmoStrategy） */
    @Unique
    private boolean beyond$isExpAmmo() {
        try {
            String ammo = ((AmmoConsumer) (Object) this).getAmmo();
            return ammo != null && ammo.toLowerCase(java.util.Locale.ROOT).startsWith("exp");
        } catch (Exception e) {
            return false;
        }
    }

    /** 经验弹药网络补给：玩家经验不足时从玩家主网络 XP 流体提取并转化为玩家经验（自动转化） */
    @Unique
    private void handleConsumeExp(Entity entity, int loads) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (player.getAbilities().instabuild) return;
        int missing = Math.max(0, loads - player.totalExperience);
        if (missing <= 0) return;
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) return;
        long got = net.getUnifiedStorage()
                .extract(com.solr98.beyondintegration.handler.EnchantmentBookSeparatorHandler.xpFluidKey(),
                        missing * 20L, false, false).amount();
        if (got <= 0) return;
        player.giveExperiencePoints((int) Math.min(got / 20, Integer.MAX_VALUE));
        net.setDirty();
    }

    // 玩家弹药消耗：按实体类型（服务端玩家/载具/女仆）分派
    @Unique
    private void handleConsumePlayerAmmo(Entity entity, int loads,
                                          CallbackInfoReturnable<Integer> cir) {
        if (playerAmmoType == null) return;

        if (entity instanceof ServerPlayer player) {
            consumePlayerAmmoFromServerPlayer(player, loads, cir);
        } else if (entity instanceof VehicleEntity vehicle) {
            consumePlayerAmmoFromVehicle(vehicle, loads, cir);
        } else if (entity instanceof LivingEntity living) {
            consumePlayerAmmoFromMaid(living, loads, cir);
        }
    }

    // 服务端玩家：先扣个人弹药与物品栏弹药，不足时按"主网络优先、其他网络兜底"从网络消耗，并推送更新
    @Unique
    private void consumePlayerAmmoFromServerPlayer(ServerPlayer player, int loads,
                                                    CallbackInfoReturnable<Integer> cir) {
        if (player.getAbilities().instabuild) return;

        String key = playerAmmoType.serializationName;
        int need = loads * loadAmount;

        int personal = playerAmmoType.get(player);
        int fromPersonal = Math.min(personal, need);
        playerAmmoType.add(player, -fromPersonal);
        int remaining = need - fromPersonal;
        int consumed = fromPersonal / loadAmount;

        if (remaining > 0) {
            var handler = player.getCapability(Capabilities.ItemHandler.ENTITY);
            if (handler != null) {
                int needed = remaining / loadAmount;
                int fromInv = InventoryTool.consumeAmmoItem(handler, playerAmmoType, needed);
                consumed += fromInv;
                remaining -= fromInv * loadAmount;
            }
        }

        DimensionsNet usedNet = null;
        if (remaining > 0) {
            // 仅从玩家主网络扣除（寻找范围限制为主网络）
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net instanceof SuperbAmmoAccessor acc) {
                var map = acc.getSuperbAmmo();
                if (map.getOrDefault("__infinite__", 0L) > 0) {
                    consumed = loads;
                    remaining = 0;
                    usedNet = net;
                } else {
                    long avail = map.getOrDefault(key, 0L);
                    if (avail > 0) {
                        long take = Math.min(avail, remaining);
                        map.put(key, avail - take);
                        consumed += (int) (take / loadAmount);
                        remaining -= (int) take;
                        net.setDirty();
                        usedNet = net;
                    }
                }
            }
        }

        cir.setReturnValue(Math.min(consumed, loads));
        if (consumed > 0 && usedNet != null) {
            com.solr98.beyondintegration.handler.PlayerNetUsageTracker.record(player.getUUID(), usedNet.getId());
            pushUpdate(player, usedNet);
            if (shouldNotify(player.getUUID())) {
                var primary = DimensionsNet.getPrimaryNetFromPlayer(player);
                int id = usedNet.getId();
                if (primary != null && primary.getId() == id)
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.beyond_integration.using_primary_net_ammo"));
                else
                    player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.beyond_integration.using_net_ammo", id));
            }
        }
    }

    // 载具：从载具绑定的网络缓存中消耗玩家弹药
    @Unique
    private void consumePlayerAmmoFromVehicle(VehicleEntity vehicle, int loads,
                                               CallbackInfoReturnable<Integer> cir) {
        VehicleNetCache cache = ((INetCachedVehicle) vehicle).getNetCache();
        DimensionsNet boundNet = cache.getNet();
        if (!(boundNet instanceof SuperbAmmoAccessor acc)) return;

        var map = acc.getSuperbAmmo();
        String key = playerAmmoType.serializationName;
        int need = loads * loadAmount;
        int taken = 0;

        if (map.getOrDefault("__infinite__", 0L) > 0) {
            taken = loads;
        } else {
            long avail = map.getOrDefault(key, 0L);
            if (avail > 0) {
                long take = Math.min(avail, need);
                map.put(key, avail - take);
                taken = (int) (take / loadAmount);
                boundNet.setDirty();
            }
        }

        if (taken > 0) {
            cir.setReturnValue(Math.min(taken, loads));
            // 载具扣弹后标记脏：由载具 tick 同步（VehicleNetSyncMixin）以载具侧包推送给乘客
            cache.markDirty();
        }
    }

    // 女仆：通过女仆身边找到的终端网络消耗玩家弹药
    @Unique
    private void consumePlayerAmmoFromMaid(LivingEntity living, int loads,
                                             CallbackInfoReturnable<Integer> cir) {
        DimensionsNet net = MaidNetworkHelper.findTerminal(living);
        if (net == null || !(net instanceof SuperbAmmoAccessor acc)) {
            return;
        }

        var map = acc.getSuperbAmmo();
        String key = playerAmmoType.serializationName;
        int need = loads * loadAmount;
        int taken = 0;

        if (map.getOrDefault("__infinite__", 0L) > 0) {
            taken = loads;
        } else {
            long networkAmmo = map.getOrDefault(key, 0L);
            if (networkAmmo > 0) {
                long take = Math.min(networkAmmo, need);
                map.put(key, networkAmmo - take);
                taken = (int) (take / loadAmount);
                net.setDirty();
            }
        }

        if (taken > 0) cir.setReturnValue(Math.min(taken, loads));
    }

    // 物品消耗：按实体类型（载具/服务端玩家/女仆）分派到对应网络
    @Unique
    private void handleConsumeItem(Entity entity, int loads,
                                   CallbackInfoReturnable<Integer> cir) {
        if (stack.isEmpty()) return;

        if (entity instanceof VehicleEntity vehicle) {
            consumeItemFromVehicle(vehicle, loads, cir);
        } else if (entity instanceof ServerPlayer player) {
            consumeItemFromServerPlayer(player, loads, cir);
        } else if (entity instanceof LivingEntity living) {
            consumeItemFromMaid(living, loads, cir);
        }
    }

    // 载具：从绑定网络提取物品型弹药（无限模式则直接满足）
    @Unique
    private void consumeItemFromVehicle(VehicleEntity vehicle, int loads,
                                         CallbackInfoReturnable<Integer> cir) {
        VehicleNetCache cache = ((INetCachedVehicle) vehicle).getNetCache();
        DimensionsNet net = cache.getNet();
        if (net == null) return;

        int taken = 0;

        if (net instanceof SuperbAmmoAccessor acc && acc.getSuperbAmmo().getOrDefault("__infinite__", 0L) > 0) {
            taken = loads;
        } else {
            KeyAmount extracted = net.getUnifiedStorage().extract(new ItemStackKey(stack), loads, false, false);
            if (extracted.amount() > 0) {
                taken += (int) extracted.amount();
                net.setDirty();
            }
        }

        if (taken > 0) {
            cir.setReturnValue(taken);
            // 载具 ITEM 扣弹后标记脏：由载具 tick 同步以载具侧包推送给乘客
            cache.markDirty();
        }
    }

    // 服务端玩家：先扣物品栏物品，不足时从主网络及其他网络提取
    @Unique
    private void consumeItemFromServerPlayer(ServerPlayer player, int loads,
                                              CallbackInfoReturnable<Integer> cir) {
        if (player.getAbilities().instabuild) return;

        int taken = 0;

        var handler = player.getCapability(Capabilities.ItemHandler.ENTITY);
        if (handler != null) {
            taken = InventoryTool.consumeItem(handler, s -> s.is(stack.getItem()), loads);
        }

        if (taken < loads) {
            ItemStackKey itemKey = new ItemStackKey(stack);
            // 仅从玩家主网络扣除 ITEM 弹药（寻找范围限制为主网络）
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net != null) {
                if (net instanceof SuperbAmmoAccessor acc && acc.getSuperbAmmo().getOrDefault("__infinite__", 0L) > 0) {
                    taken = loads;
                } else {
                    KeyAmount extracted = net.getUnifiedStorage().extract(itemKey, loads - taken, false, false);
                    if (extracted.amount() > 0) {
                        taken += (int) extracted.amount();
                        net.setDirty();
                    }
                }
            }
        }

        if (taken > 0) {
            // 利用 extract 返回值（实际提取量）：扣弹后立即推送弹药快照，HUD 实时更新
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net != null) pushUpdate(player, net);
            cir.setReturnValue(taken);
        }
    }

    // 女仆：从女仆所在终端网络提取物品型弹药
    @Unique
    private void consumeItemFromMaid(LivingEntity living, int loads,
                                      CallbackInfoReturnable<Integer> cir) {
        DimensionsNet maidNet = MaidNetworkHelper.findTerminal(living);
        if (maidNet == null) {
            return;
        }

        int taken = 0;

        if (maidNet instanceof SuperbAmmoAccessor acc && acc.getSuperbAmmo().getOrDefault("__infinite__", 0L) > 0) {
            taken = loads;
        } else {
            KeyAmount extracted = maidNet.getUnifiedStorage().extract(new ItemStackKey(stack), loads, false, false);
            if (extracted.amount() > 0) {
                taken += (int) extracted.amount();
                maidNet.setDirty();
            }
        }

        if (taken > 0) cir.setReturnValue(taken);
    }

    // 向指定玩家推送网络弹药的最新状态（整包全量或增量数据包；统一走轮询服务入口）
    @Unique
    private static void pushUpdate(ServerPlayer player, DimensionsNet net) {
        if (net == null) return;
        SwAmmoTracker tracker = SwAmmoTracker.getOrCreate(net);
        if (tracker == null) return;
        tracker.markDirty();
        com.solr98.beyondintegration.handler.SwAmmoPollingService.pushSnapshotToPlayer(player, net);
    }

    // 限频：同一玩家在间隔时间内只提示一次"正在使用网络弹药"
    @Unique
    private static boolean shouldNotify(UUID uuid) {
        long now = System.currentTimeMillis();
        Long last = beyond$notifiedPlayers.get(uuid);
        if (last != null && now - last < NOTIFY_INTERVAL_MS) return false;
        beyond$notifiedPlayers.put(uuid, now);
        return true;
    }
}
