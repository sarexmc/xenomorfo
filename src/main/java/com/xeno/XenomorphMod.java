package com.xeno;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.Heightmap;

public class XenomorphMod implements ModInitializer {
	public static final String ID = "xenomorph";

	public static final EntityType<XenomorphEntity> XENOMORPH = Registry.register(Registries.ENTITY_TYPE, Identifier.of(ID, "xenomorph"),
		FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, XenomorphEntity::new).dimensions(EntityDimensions.fixed(1.2f, 1.7f)).trackRangeBlocks(80).build());
	public static final EntityType<AcidProjectileEntity> ACID = Registry.register(Registries.ENTITY_TYPE, Identifier.of(ID, "acid"),
		FabricEntityTypeBuilder.<AcidProjectileEntity>create(SpawnGroup.MISC, AcidProjectileEntity::new).dimensions(EntityDimensions.fixed(0.3f, 0.3f)).trackRangeBlocks(64).trackedUpdateRate(2).build());
	public static final EntityType<AcidPuddleEntity> PUDDLE = Registry.register(Registries.ENTITY_TYPE, Identifier.of(ID, "puddle"),
		FabricEntityTypeBuilder.<AcidPuddleEntity>create(SpawnGroup.MISC, AcidPuddleEntity::new).dimensions(EntityDimensions.fixed(0.5f, 0.1f)).trackRangeBlocks(48).build());
	public static final Item EGG = Registry.register(Registries.ITEM, Identifier.of(ID, "xenomorph_spawn_egg"),
		new SpawnEggItem(XENOMORPH, 0x101a14, 0x6dff3a, new Item.Settings()));

	@Override
	public void onInitialize() {
		FabricDefaultAttributeRegistry.register(XENOMORPH, XenomorphEntity.createXenoAttributes());
		SpawnRestriction.register(XENOMORPH, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, XenomorphEntity::canSpawnUnderground);
		// Aparece solo bajo tierra (cuevas) en el Overworld
		BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER, XENOMORPH, 4, 1, 1);
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(e -> e.add(EGG));
	}
}
