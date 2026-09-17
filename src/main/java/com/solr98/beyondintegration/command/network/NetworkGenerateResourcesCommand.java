package com.solr98.beyondintegration.command.network;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.solr98.beyondintegration.command.CommandLang;
import com.solr98.beyondintegration.command.util.*;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.EnergyStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.FluidStackKey;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.*;
import java.util.stream.Collectors;

public class NetworkGenerateResourcesCommand {

    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("generateResources").requires(s -> CommandUtils.hasOpPermission(s))
                .executes(ctx -> execute(ctx, -1, 100, 100, 300, false, false, "mixed"))
                .then(buildNetIdBranch());
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> buildNetIdBranch() {
        return Commands.argument("netId", IntegerArgumentType.integer(0, 9999))
                .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"), 100, 100, 300, false, false, "mixed"))
                .then(buildTypeCountBranch());
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> buildTypeCountBranch() {
        return Commands.argument("typeCount", IntegerArgumentType.integer(1))
                .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"), IntegerArgumentType.getInteger(ctx, "typeCount"), 100, 300, false, false, "mixed"))
                .then(buildMinAmountBranch());
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> buildMinAmountBranch() {
        return Commands.argument("minAmount", IntegerArgumentType.integer(1))
                .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"), IntegerArgumentType.getInteger(ctx, "typeCount"), IntegerArgumentType.getInteger(ctx, "minAmount"), 300, false, false, "mixed"))
                .then(buildMaxAmountBranch());
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> buildMaxAmountBranch() {
        var maxArg = Commands.argument("maxAmount", IntegerArgumentType.integer(1))
                .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"), IntegerArgumentType.getInteger(ctx, "typeCount"), IntegerArgumentType.getInteger(ctx, "minAmount"), IntegerArgumentType.getInteger(ctx, "maxAmount"), false, false, "mixed"));

        maxArg = maxArg.then(Commands.literal("items")
                .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"), IntegerArgumentType.getInteger(ctx, "typeCount"), IntegerArgumentType.getInteger(ctx, "minAmount"), IntegerArgumentType.getInteger(ctx, "maxAmount"), false, false, "items"))
                .then(buildEnchantAndNbtBranch("items")));
        maxArg = maxArg.then(Commands.literal("fluids")
                .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"), IntegerArgumentType.getInteger(ctx, "typeCount"), IntegerArgumentType.getInteger(ctx, "minAmount"), IntegerArgumentType.getInteger(ctx, "maxAmount"), false, false, "fluids")));
        maxArg = maxArg.then(Commands.literal("energy")
                .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"), IntegerArgumentType.getInteger(ctx, "typeCount"), IntegerArgumentType.getInteger(ctx, "minAmount"), IntegerArgumentType.getInteger(ctx, "maxAmount"), false, false, "energy")));
        maxArg = maxArg.then(Commands.literal("mixed")
                .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"), IntegerArgumentType.getInteger(ctx, "typeCount"), IntegerArgumentType.getInteger(ctx, "minAmount"), IntegerArgumentType.getInteger(ctx, "maxAmount"), false, false, "mixed"))
                .then(buildEnchantAndNbtBranch("mixed")));
        maxArg = maxArg.then(Commands.literal("all")
                .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"), IntegerArgumentType.getInteger(ctx, "typeCount"), IntegerArgumentType.getInteger(ctx, "minAmount"), IntegerArgumentType.getInteger(ctx, "maxAmount"), false, false, "all"))
                .then(buildEnchantAndNbtBranch("all")));

        return maxArg;
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> buildEnchantAndNbtBranch(String type) {
        return Commands.argument("withEnchantments", BoolArgumentType.bool())
                .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"), IntegerArgumentType.getInteger(ctx, "typeCount"), IntegerArgumentType.getInteger(ctx, "minAmount"), IntegerArgumentType.getInteger(ctx, "maxAmount"), BoolArgumentType.getBool(ctx, "withEnchantments"), false, type))
                .then(Commands.argument("withNbt", BoolArgumentType.bool())
                        .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "netId"), IntegerArgumentType.getInteger(ctx, "typeCount"), IntegerArgumentType.getInteger(ctx, "minAmount"), IntegerArgumentType.getInteger(ctx, "maxAmount"), BoolArgumentType.getBool(ctx, "withEnchantments"), BoolArgumentType.getBool(ctx, "withNbt"), type)));
    }

    private static int execute(CommandContext<CommandSourceStack> ctx, int netId, int typeCount, int minAmount, int maxAmount, boolean withEnchantments, boolean withNbt, String resourceType) {
        CommandSourceStack source = ctx.getSource();
        if (!PermissionChecker.checkServerAvailable(source)) return 0;
        var server = source.getServer();
        if (server == null) { source.sendFailure(OutputFormatter.createError("error.server_not_available")); return 0; }
        if (netId == -1) {
            ServerPlayer player = source.getPlayer();
            if (player == null) { source.sendFailure(OutputFormatter.createError("error.player_required")); return 0; }
            DimensionsNet net = DimensionsNet.getNetFromPlayer(player);
            if (net == null) { source.sendFailure(OutputFormatter.createError("error.not_in_network")); return 0; }
            netId = net.getId();
        }
        DimensionsNet net = PermissionChecker.checkNetworkExists(source, netId);
        if (net == null) return 0;
        if (!PermissionChecker.checkOpPermission(source)) return 0;
        GenerationResult result = generateResources(net, typeCount, minAmount, maxAmount, withEnchantments, withNbt, resourceType, server);
        sendGenerationResult(source, netId, result, resourceType);
        return (int) result.totalResources;
    }

    private static GenerationResult generateResources(DimensionsNet net, int typeCount, int minAmount, int maxAmount, boolean withEnchantments, boolean withNbt, String resourceType, net.minecraft.server.MinecraftServer server) {
        GenerationResult result = new GenerationResult();
        Random random = new Random();
        switch (resourceType.toLowerCase()) {
            case "items" -> generateItems(net, typeCount, minAmount, maxAmount, withEnchantments, withNbt, random, server, result);
            case "fluids" -> generateFluids(net, typeCount, minAmount, maxAmount, random, server, result);
            case "energy" -> generateEnergy(net, typeCount, minAmount, maxAmount, random, result);
            case "mixed" -> {
                int ic = typeCount / 3, fc = typeCount / 3, ec = typeCount - ic - fc;
                if (ic > 0) generateItems(net, ic, minAmount, maxAmount, withEnchantments, withNbt, random, server, result);
                if (fc > 0) generateFluids(net, fc, minAmount, maxAmount, random, server, result);
                if (ec > 0) generateEnergy(net, ec, minAmount, maxAmount, random, result);
            }
            case "all" -> generateAllResources(net, minAmount, maxAmount, withEnchantments, withNbt, random, server, result);
        }
        return result;
    }

    private static void generateItems(DimensionsNet net, int typeCount, int minAmount, int maxAmount, boolean withEnchantments, boolean withNbt, Random random, net.minecraft.server.MinecraftServer server, GenerationResult result) {
        List<Item> allItems = BuiltInRegistries.ITEM.stream().toList();
        List<Item> shuffled = new ArrayList<>(allItems);
        Collections.shuffle(shuffled, random);
        var enchLookup = server != null ? server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT) : null;
        int generated = 0;
        for (Item item : shuffled) {
            if (generated >= typeCount) break;
            if (item == Items.AIR) continue;
            try {
                ItemStack stack = new ItemStack(item);
                int amount = random.nextInt(maxAmount - minAmount + 1) + minAmount;
                if (withEnchantments && enchLookup != null) {
                    int ec = addRandomEnchantments(stack, random, enchLookup);
                    if (ec > 0) { result.itemsWithEnchantments++; result.totalEnchantments += ec; }
                }
                if (withNbt) {
                    addRandomNbt(stack, random);
                    result.itemsWithNbt++;
                    result.estimatedNbtSize += 800 + random.nextInt(650);
                }
                ItemStackKey key = new ItemStackKey(stack);
                var remainder = net.getUnifiedStorage().insert(key, amount, false);
                long inserted = amount - remainder.amount();
                result.itemTypes++;
                result.itemTotal += amount;
                result.totalResources += amount;
                generated++;
            } catch (Exception ignored) {}
        }
    }

    private static void generateFluids(DimensionsNet net, int typeCount, int minAmount, int maxAmount, Random random, net.minecraft.server.MinecraftServer server, GenerationResult result) {
        List<Fluid> allFluids = BuiltInRegistries.FLUID.stream().toList();
        List<Fluid> shuffled = new ArrayList<>(allFluids);
        Collections.shuffle(shuffled, random);
        int generated = 0;
        for (Fluid fluid : shuffled) {
            if (generated >= typeCount) break;
            if (fluid == Fluids.EMPTY) continue;
            try {
                FluidStack stack = new FluidStack(fluid, 1000);
                int amount = random.nextInt(maxAmount - minAmount + 1) + minAmount;
                FluidStackKey key = new FluidStackKey(stack);
                var remainder = net.getUnifiedStorage().insert(key, amount, false);
                long inserted = amount - remainder.amount();
                result.fluidTypes++;
                result.fluidTotal += amount;
                result.totalResources += amount;
                generated++;
            } catch (Exception ignored) {}
        }
    }

    private static void generateEnergy(DimensionsNet net, int typeCount, int minAmount, int maxAmount, Random random, GenerationResult result) {
        for (int i = 0; i < typeCount; i++) {
            try {
                int amount = random.nextInt(maxAmount - minAmount + 1) + minAmount;
                var remainder = net.getUnifiedStorage().insert(EnergyStackKey.INSTANCE, amount, false);
                long inserted = amount - remainder.amount();
                result.energyTypes++;
                result.energyTotal += amount;
                result.totalResources += amount;
            } catch (Exception ignored) {}
        }
    }

    private static void generateAllResources(DimensionsNet net, int minAmount, int maxAmount, boolean withEnchantments, boolean withNbt, Random random, net.minecraft.server.MinecraftServer server, GenerationResult result) {
        generateItems(net, Integer.MAX_VALUE, minAmount, maxAmount, withEnchantments, withNbt, random, server, result);
        generateFluids(net, Integer.MAX_VALUE, minAmount, maxAmount, random, server, result);
        generateEnergy(net, 1, minAmount, maxAmount, random, result);
    }

    private static int addRandomEnchantments(ItemStack stack, Random random, net.minecraft.core.HolderLookup.RegistryLookup<Enchantment> enchLookup) {
        var holders = enchLookup.listElements().toList();
        if (holders.isEmpty()) return 0;
        var shuffled = new java.util.ArrayList<>(holders);
        Collections.shuffle(shuffled, random);
        int maxEnchant = Math.min(5, shuffled.size());
        int count = random.nextInt(maxEnchant) + 1;
        int added = 0;
        for (int i = 0; i < count && i < shuffled.size(); i++) {
            var holder = shuffled.get(i);
            int maxLvl = holder.value().getMaxLevel();
            int lvl = random.nextInt(maxLvl * 2) + 1;
            stack.enchant(holder, lvl);
            added++;
        }
        return added;
    }

    private static void addRandomNbt(ItemStack stack, Random random) {
        var customData = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = customData != null ? customData.copyTag() : new CompoundTag();
        tag.putString("generated_by", "bdtools");
        tag.putLong("timestamp", System.currentTimeMillis());
        tag.putInt("random_seed", random.nextInt(1000000));
        tag.putInt("generation_id", random.nextInt(10000));
        String[] prefixes = {"Super", "Mega", "Ultra", "Hyper", "Epic", "Legendary", "Mythic", "Divine"};
        String[] suffixes = {"Item", "Tool", "Gear", "Artifact", "Relic", "Treasure", "Wonder"};
        String displayName = prefixes[random.nextInt(prefixes.length)] + " " + suffixes[random.nextInt(suffixes.length)] + " #" + random.nextInt(9999);
        tag.putString("CustomName", "{\"text\":\"" + displayName + "\",\"color\":\"gold\",\"bold\":true}");
        ListTag lore = new ListTag();
        int loreCount = random.nextInt(5) + 2;
        String[] loreTexts = {"Generated by BD Tools", "Power Level: " + (random.nextInt(100) + 1), "Rarity: " + (random.nextInt(10) + 1) + "/10", "Magic: " + random.nextInt(1000), "Durability: " + random.nextInt(100) + "%"};
        for (int i = 0; i < loreCount; i++) lore.add(StringTag.valueOf("{\"text\":\"" + loreTexts[random.nextInt(loreTexts.length)] + "\",\"color\":\"gray\",\"italic\":true}"));
        CompoundTag display = new CompoundTag();
        display.put("Lore", lore);
        tag.put("display", display);
        tag.putInt("custom_int_1", random.nextInt(10000));
        tag.putDouble("custom_double_1", random.nextDouble() * 100);
        tag.putBoolean("custom_bool_1", random.nextBoolean());
        CompoundTag nested1 = new CompoundTag();
        nested1.putString("nested_type", "type_" + random.nextInt(10));
        nested1.putInt("nested_value", random.nextInt(1000));
        tag.put("nested_data_1", nested1);
        ListTag randomList = new ListTag();
        int listSize = random.nextInt(10) + 5;
        for (int i = 0; i < listSize; i++) {
            CompoundTag li = new CompoundTag();
            li.putInt("id", i);
            li.putInt("value", random.nextInt(1000));
            randomList.add(li);
        }
        tag.put("random_data_list", randomList);
        tag.putBoolean("is_generated", true);
        stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
    }

    private static void sendGenerationResult(CommandSourceStack source, int netId, GenerationResult result, String resourceType) {
        String typeDisplay = switch (resourceType.toLowerCase()) {
            case "items" -> CommandLang.get("network.generateItems.resource_type.items");
            case "fluids" -> CommandLang.get("network.generateItems.resource_type.fluids");
            case "energy" -> CommandLang.get("network.generateItems.resource_type.energy");
            case "mixed" -> CommandLang.get("network.generateItems.resource_type.mixed");
            case "all" -> CommandLang.get("network.generateItems.resource_type.all");
            default -> resourceType;
        };
        MutableComponent msg = OutputFormatter.createTitle("network.generate.result.title", netId, typeDisplay).append(Component.literal("\n"));
        if (result.itemTypes > 0) msg = msg.append(Component.literal(CommandLang.get("network.generate.result.items", result.itemTypes, result.itemTotal)).withStyle(ChatFormatting.GREEN)).append(Component.literal("\n"));
        if (result.fluidTypes > 0) msg = msg.append(Component.literal(CommandLang.get("network.generate.result.fluids", result.fluidTypes, result.fluidTotal)).withStyle(ChatFormatting.AQUA)).append(Component.literal("\n"));
        if (result.energyTypes > 0) msg = msg.append(Component.literal(CommandLang.get("network.generate.result.energy", result.energyTypes, result.energyTotal)).withStyle(ChatFormatting.LIGHT_PURPLE)).append(Component.literal("\n"));
        if (result.itemsWithEnchantments > 0) msg = msg.append(Component.literal(CommandLang.get("network.generate.result.enchantments", result.itemsWithEnchantments, result.totalEnchantments)).withStyle(ChatFormatting.LIGHT_PURPLE)).append(Component.literal("\n"));
        if (result.itemsWithNbt > 0) {
            String sz = formatFileSize(result.estimatedNbtSize);
            msg = msg.append(Component.literal(CommandLang.get("network.generate.result.nbt_size", result.itemsWithNbt, sz)).withStyle(ChatFormatting.YELLOW)).append(Component.literal("\n"));
            if (result.estimatedNbtSize > 10240) msg = msg.append(Component.literal(CommandLang.get("network.generate.result.nbt_warning")).withStyle(ChatFormatting.RED)).append(Component.literal("\n"));
        }
        int totalTypes = result.itemTypes + result.fluidTypes + result.energyTypes;
        msg = msg.append(Component.literal(CommandLang.get("network.generateResources.detailed_total", totalTypes, result.totalResources)).withStyle(ChatFormatting.GOLD)).append(Component.literal("\n"));
        Component finalMsg = msg;
        source.sendSuccess(() -> finalMsg, false);
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), pre);
    }

    private static class GenerationResult {
        int itemTypes = 0; long itemTotal = 0;
        int fluidTypes = 0; long fluidTotal = 0;
        int energyTypes = 0; long energyTotal = 0;
        long totalResources = 0;
        int itemsWithNbt = 0; long estimatedNbtSize = 0;
        int itemsWithEnchantments = 0; int totalEnchantments = 0;
    }
}

