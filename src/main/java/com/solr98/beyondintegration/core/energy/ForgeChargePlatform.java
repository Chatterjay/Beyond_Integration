package com.solr98.beyondintegration.core.energy;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.ModList;

/** Forge 平台充电能力实现（Forge capability / ModList / 属性表 / tools 聚合标签）。 */
public final class ForgeChargePlatform implements ChargePlatform {

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public EnergyHandle energy(ItemStack stack) {
        var energyOpt = stack.getCapability(ForgeCapabilities.ENERGY).resolve();
        if (energyOpt.isEmpty()) return null;
        IEnergyStorage storage = energyOpt.get();
        return new EnergyHandle() {
            @Override public int maxStored() { return storage.getMaxEnergyStored(); }

            @Override public int stored() { return storage.getEnergyStored(); }

            @Override public boolean canReceive() { return storage.canReceive(); }

            @Override public int receive(int amount) { return storage.receiveEnergy(amount, false); }
        };
    }

    @Override
    public boolean hasMainHandAttackDamage(ItemStack stack) {
        return stack.getAttributeModifiers(EquipmentSlot.MAINHAND).containsKey(Attributes.ATTACK_DAMAGE);
    }

    @Override
    public boolean isToolTag(ItemStack stack) {
        return stack.is(ItemTags.TOOLS) || ChargePlatform.super.isToolTag(stack);
    }
}
