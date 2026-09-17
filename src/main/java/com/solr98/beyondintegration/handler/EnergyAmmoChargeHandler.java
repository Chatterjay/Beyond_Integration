package com.solr98.beyondintegration.handler;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.core.energy.ChargePlatform;
import com.solr98.beyondintegration.maid.MaidNetworkHelper;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/**
 * 通用装备位网络充电服务（平台无关主体）。
 * 充能范围（仅装备位，背包内物品不充）：
 *  1) 盔甲槽（头/胸/腿/脚）中带能量槽（maxEnergyStored>0）的装备；
 *  2) 主手/副手中带能量槽的工具或武器（平台工具/武器标签与类型、SW 枪械、带主手攻击伤害属性的模组道具、
 *     配置白名单中的物品 ID 或 #标签）；
 *  3) Curios 饰品栏中带能量槽的饰品（未安装 Curios 时跳过）；
 *  4) TLM 女仆专用饰物栏（EntityMaid#getMaidBauble）中带能量槽的饰品（未安装东方小女仆时跳过）。
 * 玩家按配置间隔从主网络能量库存（EnergyStackKey）抽取 FE 充入；
 * 女仆等非玩家实体使用其绑定网络（MaidNetworkHelper），由 MaidTickEvent 节流充电。
 * 平台能力（能量 capability / 模组判定 / 属性判定）见 {@link ChargePlatform}。
 */
public final class EnergyAmmoChargeHandler {

    /** 盔甲槽位：穿戴在身上的可充电装备 */
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    /** 手持槽位：仅工具/武器会被充能 */
    private static final EquipmentSlot[] HAND_SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };

    private EnergyAmmoChargeHandler() {}

    /** 服务端 tick：按间隔为在线玩家的装备位物品从主网络充电（女仆见 MaidEnergyChargeHandler） */
    public static void tick(MinecraftServer server) {
        if (server == null) return;
        if (!CommandConfig.energyAmmoChargeEnabled()) return;
        int interval = CommandConfig.energyAmmoChargeInterval();
        if (interval <= 0) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.tickCount % interval != 0) continue;
            chargePlayer(player);
        }
    }

    /** 玩家充电入口：主网络 FE → 穿戴盔甲 + 主副手工具/武器（受 charge_enabled 开关控制） */
    public static void chargePlayer(ServerPlayer player) {
        if (player == null) return;
        if (!CommandConfig.energyAmmoChargeEnabled()) return;
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) return;
        if (!isNetworkChargeEnabled(net)) return;
        chargeEquipment(player, net);
    }

    /** 女仆等非玩家实体充电入口：绑定网络 FE → 穿戴盔甲 + 主副手工具/武器（受 charge_enabled 开关控制） */
    public static void chargeMaid(LivingEntity entity) {
        if (entity == null) return;
        if (!CommandConfig.energyAmmoChargeEnabled()) return;
        DimensionsNet net = MaidNetworkHelper.findTerminal(entity);
        if (net == null) return;
        if (!isNetworkChargeEnabled(net)) return;
        chargeEquipment(entity, net);
    }

    /** 网络级自动充电开关（网络终端左侧按钮控制；无访问器视为开启） */
    private static boolean isNetworkChargeEnabled(DimensionsNet net) {
        return !(net instanceof com.solr98.beyondintegration.handler.EnergyChargeAccessor acc)
                || acc.beyond$isEnergyChargeEnabled();
    }

    /** 遍历装备位：盔甲槽直充（能量 capability 自行过滤），手持槽仅工具/武器，饰品槽直充 */
    private static void chargeEquipment(LivingEntity entity, DimensionsNet net) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            charge(entity.getItemBySlot(slot), net);
        }
        for (EquipmentSlot slot : HAND_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty() || !isToolOrWeapon(stack)) continue;
            charge(stack, net);
        }
        chargeCurios(entity, net);
        chargeMaidBaubles(entity, net);
    }

    /** Curios 饰品栏充电：饰品槽内带能量槽且可接收的饰品（未安装 Curios 时直接跳过） */
    private static void chargeCurios(LivingEntity entity, DimensionsNet net) {
        if (!CommandConfig.energyAmmoChargeCuriosEnabled()) return;
        if (!ChargePlatform.get().isModLoaded("curios")) return;
        try {
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(entity).ifPresent(handler -> {
                for (var stacksHandler : handler.getCurios().values()) {
                    var stacks = stacksHandler.getStacks();
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        ItemStack stack = stacks.getStackInSlot(i);
                        if (stack.isEmpty()) continue;
                        if (charge(stack, net)) {
                            // 回写触发 Curios 槽同步（客户端能量显示即时刷新）
                            stacks.setStackInSlot(i, stack.copy());
                        }
                    }
                }
            });
        } catch (NoClassDefFoundError ignored) {}
    }

    /** TLM 女仆专用饰物栏充电：饰物栏内带能量槽且可接收的饰品（未安装东方小女仆时直接跳过） */
    private static void chargeMaidBaubles(LivingEntity entity, DimensionsNet dimNet) {
        if (!CommandConfig.energyAmmoChargeMaidBaublesEnabled()) return;
        if (!ChargePlatform.get().isModLoaded("touhou_little_maid")) return;
        try {
            if (!(entity instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid)) return;
            var baubles = maid.getMaidBauble();
            for (int i = 0; i < baubles.getSlots(); i++) {
                ItemStack stack = baubles.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                if (charge(stack, dimNet)) {
                    // 回写触发 TLM 饰物栏同步（客户端能量显示即时刷新）
                    baubles.setStackInSlot(i, stack.copy());
                }
            }
        } catch (NoClassDefFoundError ignored) {}
    }

    /** 判定工具或武器：配置白名单（物品 ID / #标签）优先，其次平台工具/武器标签与类型 + SW 枪械 + 主手攻击伤害属性 */
    private static boolean isToolOrWeapon(ItemStack stack) {
        if (isChargeWhitelisted(stack)) return true;
        if (ChargePlatform.get().isToolTag(stack)) return true;
        if (stack.getItem() instanceof SwordItem || stack.getItem() instanceof DiggerItem) return true;
        if (stack.getItem() instanceof TridentItem || stack.getItem() instanceof ProjectileWeaponItem) return true;
        // SW 枪械（模组未加载时短路，SW 类不会被解析）
        if (ChargePlatform.get().isModLoaded("superbwarfare")
                && stack.getItem() instanceof com.atsuishio.superbwarfare.item.gun.GunItem) {
            return true;
        }
        return ChargePlatform.get().hasMainHandAttackDamage(stack);
    }

    /** 配置白名单命中：支持物品 ID（如 minecraft:diamond_sword）与 #标签（如 #forge:tools），命中即视为可充能目标 */
    private static boolean isChargeWhitelisted(ItemStack stack) {
        for (String entry : CommandConfig.energyAmmoChargeWhitelist()) {
            if (entry == null || entry.isBlank()) continue;
            try {
                if (entry.startsWith("#")) {
                    ResourceLocation rl = ResourceLocation.tryParse(entry.substring(1));
                    if (rl != null && stack.is(TagKey.create(Registries.ITEM, rl))) return true;
                } else {
                    ResourceLocation rl = ResourceLocation.tryParse(entry);
                    if (rl == null) continue;
                    var item = BuiltInRegistries.ITEM.get(rl);
                    if (item != Items.AIR && stack.is(item)) return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    /** 按配置模式计算单次充电量：RATE=固定值；PERCENTAGE=能量容量上限百分比；PERCENTAGE_PLUS_RATE=百分比 + 固定值 */
    private static int computeChargeAmount(int maxStored) {
        int rate = CommandConfig.energyAmmoChargeRate();
        switch (CommandConfig.energyAmmoChargeMode()) {
            case PERCENTAGE:
                return percentagePart(maxStored);
            case PERCENTAGE_PLUS_RATE:
                return (int) Math.min((long) rate + percentagePart(maxStored), Integer.MAX_VALUE);
            case RATE:
            default:
                return rate;
        }
    }

    /** 能量容量上限的百分比部分（向上取整） */
    private static int percentagePart(int maxStored) {
        long value = (long) Math.ceil(maxStored * CommandConfig.energyAmmoChargePercentage() / 100.0);
        return (int) Math.min(Math.max(value, 0L), Integer.MAX_VALUE);
    }

    /** 从网络能量库存提取 FE 充入物品：带能量槽且可接收才生效；返回是否实际充入 */
    private static boolean charge(ItemStack stack, DimensionsNet net) {
        if (stack.isEmpty()) return false;
        ChargePlatform.EnergyHandle energy = ChargePlatform.get().energy(stack);
        if (energy == null) return false;
        int needed = energy.maxStored() - energy.stored();
        if (energy.maxStored() <= 0 || needed <= 0 || !energy.canReceive()) return false;

        int want = Math.min(needed, computeChargeAmount(energy.maxStored()));
        if (want <= 0) return false;

        long got = net.getUnifiedStorage().extract(EnergyStackKey.INSTANCE, want, false, false).amount();
        if (got <= 0) return false;

        int offered = (int) Math.min(got, Integer.MAX_VALUE);
        int accepted = energy.receive(offered);
        int leftover = offered - Math.max(0, accepted);
        if (leftover > 0) {
            // 物品单次接收上限（如 Mekanism chargeRate）导致未接收：退还网络，避免能量凭空消失
            net.getUnifiedStorage().insert(EnergyStackKey.INSTANCE, leftover, false);
        }
        if (accepted <= 0) return false;
        net.setDirty();
        return true;
    }
}
