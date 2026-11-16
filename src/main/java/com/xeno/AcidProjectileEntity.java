package com.xeno;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Globo de ácido que vuela en arco y deja un charco corrosivo. */
public class AcidProjectileEntity extends ProjectileEntity {
	public AcidProjectileEntity(EntityType<? extends AcidProjectileEntity> type, World world) { super(type, world); }

	@Override protected void initDataTracker(DataTracker.Builder builder) {}

	@Override
	public void tick() {
		super.tick();
		HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
		if (hit.getType() != HitResult.Type.MISS) { onCollision(hit); if (isRemoved()) return; }
		Vec3d v = getVelocity();
		setPosition(getX() + v.x, getY() + v.y, getZ() + v.z);
		setVelocity(v.multiply(0.99).add(0, -0.05, 0));
		if (getWorld().isClient) {
			getWorld().addParticle(ParticleTypes.ITEM_SLIME, getX(), getY(), getZ(), 0, 0, 0);
			getWorld().addParticle(ParticleTypes.ITEM_SLIME, getX(), getY(), getZ(), 0, 0, 0);
		} else if (age > 200) discard();
	}

	@Override
	protected void onEntityHit(EntityHitResult r) {
		Entity e = r.getEntity();
		if (!getWorld().isClient && e instanceof LivingEntity le && !(e instanceof XenomorphEntity)) {
			Entity owner = getOwner();
			le.damage(getDamageSources().mobProjectile(this, owner instanceof LivingEntity lo ? lo : null), 5.0f);
			le.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 100, 1));
			splash(e.getX(), e.getY(), e.getZ());
		}
	}

	@Override
	protected void onBlockHit(BlockHitResult r) {
		super.onBlockHit(r);
		if (!getWorld().isClient) splash(getX(), getY(), getZ());
	}

	private void splash(double x, double y, double z) {
		AcidPuddleEntity p = new AcidPuddleEntity(XenomorphMod.PUDDLE, getWorld());
		p.setPosition(x, y, z);
		getWorld().spawnEntity(p);
		getWorld().playSound(null, x, y, z, net.minecraft.sound.SoundEvents.BLOCK_LAVA_EXTINGUISH, net.minecraft.sound.SoundCategory.HOSTILE, 0.6f, 1.6f);
		discard();
	}
}
