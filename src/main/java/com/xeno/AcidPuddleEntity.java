package com.xeno;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/** Charco de ácido: daña a todo lo que lo pise durante 8 segundos. */
public class AcidPuddleEntity extends Entity {
	public AcidPuddleEntity(EntityType<? extends AcidPuddleEntity> type, World world) { super(type, world); this.noClip = true; }

	@Override protected void initDataTracker(DataTracker.Builder builder) {}
	@Override protected void readCustomDataFromNbt(NbtCompound nbt) { age = nbt.getInt("Age"); }
	@Override protected void writeCustomDataToNbt(NbtCompound nbt) { nbt.putInt("Age", age); }

	@Override
	public void tick() {
		super.tick();
		World w = getWorld();
		if (w.isClient) {
			for (int i = 0; i < 2; i++)
				w.addParticle(ParticleTypes.ITEM_SLIME, getX() + (random.nextDouble() - .5) * 2.8, getY() + .05, getZ() + (random.nextDouble() - .5) * 2.8, 0, 0.03, 0);
			return;
		}
		if (age % 10 == 0) {
			Box box = new Box(getX() - 1.5, getY() - 0.3, getZ() - 1.5, getX() + 1.5, getY() + 1.0, getZ() + 1.5);
			for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, x -> !(x instanceof XenomorphEntity)))
				e.damage(getDamageSources().magic(), 2.0f);
		}
		if (age > 160) discard();
	}
}
