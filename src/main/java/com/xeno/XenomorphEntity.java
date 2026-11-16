package com.xeno;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.SpiderNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;

public class XenomorphEntity extends HostileEntity {
	public static final int IDLE = 0, SCREAM = 1, SPIT = 2, GRAB = 3, STUN = 4;
	public static final String[] KEYS = {"W", "A", "S", "D"};
	private static final TrackedData<Integer> STATE = DataTracker.registerData(XenomorphEntity.class, TrackedDataHandlerRegistry.INTEGER);
	private static final TrackedData<Boolean> CLIMBING = DataTracker.registerData(XenomorphEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
	private static final TrackedData<Float> WALL_YAW = DataTracker.registerData(XenomorphEntity.class, TrackedDataHandlerRegistry.FLOAT);

	public int animStart;          // cliente: tick en que cambió el estado
	int stateTicks, spitCd = 60, screamCd = 0, grabCd = 0;
	private PlayerEntity grabbed;
	private int[] seq = new int[5];
	private int seqIdx, stepTicks, lastKey = -1;

	public XenomorphEntity(EntityType<? extends HostileEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 40;
	}

	public static DefaultAttributeContainer.Builder createXenoAttributes() {
		return HostileEntity.createHostileAttributes()
			.add(EntityAttributes.GENERIC_MAX_HEALTH, 60.0)
			.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.32)
			.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6.0)
			.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
			.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.5);
	}

	public static boolean canSpawnUnderground(EntityType<XenomorphEntity> type, ServerWorldAccess world, SpawnReason reason, BlockPos pos, Random random) {
		return world.getDifficulty() != Difficulty.PEACEFUL && pos.getY() < 48 && !world.isSkyVisible(pos)
			&& random.nextInt(3) == 0 && MobEntity.canMobSpawn(type, world, reason, pos, random);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder b) {
		super.initDataTracker(b);
		b.add(STATE, IDLE); b.add(CLIMBING, false); b.add(WALL_YAW, 0f);
	}

	@Override protected EntityNavigation createNavigation(World w) { return new SpiderNavigation(this, w); }

	@Override
	protected void initGoals() {
		goalSelector.add(0, new SwimGoal(this));
		goalSelector.add(0, new XenoGoals.Hold(this));
		goalSelector.add(1, new XenoGoals.Scream(this));
		goalSelector.add(2, new XenoGoals.Spit(this));
		goalSelector.add(3, new XenoGoals.Grab(this));
		goalSelector.add(5, new WanderAroundFarGoal(this, 0.8));
		goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 16f));
		goalSelector.add(7, new LookAroundGoal(this));
		targetSelector.add(1, new RevengeGoal(this));
		// Mecánica de ruido: te oye más lejos si corres, y casi nada si vas agachado.
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, false, false, this::canHear));
	}

	private boolean canHear(LivingEntity e) {
		if (!(e instanceof PlayerEntity p) || p.isCreative() || p.isSpectator()) return false;
		double r = p.isSprinting() ? 48 : p.isSneaking() ? 6 : 22;
		return squaredDistanceTo(p) < r * r;
	}

	public int getState() { return dataTracker.get(STATE); }
	void setState(int s) { dataTracker.set(STATE, s); stateTicks = 0; }
	public float getWallYaw() { return dataTracker.get(WALL_YAW); }
	@Override public boolean isClimbing() { return dataTracker.get(CLIMBING); }
	public boolean isGrabbing() { return getState() == GRAB && grabbed != null; }
	@Override public Box getVisibilityBoundingBox() { return getBoundingBox().expand(3.0); }

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (STATE.equals(data)) animStart = age;
	}

	@Override
	public void tick() {
		super.tick();
		if (getWorld().isClient) return;
		stateTicks++;
		if (spitCd > 0) spitCd--; if (screamCd > 0) screamCd--; if (grabCd > 0) grabCd--;
		boolean wall = horizontalCollision && !isOnGround();
		dataTracker.set(CLIMBING, horizontalCollision);
		if (horizontalCollision) {
			for (Direction d : Direction.Type.HORIZONTAL) {
				BlockPos p = BlockPos.ofFloored(getX() + d.getOffsetX() * 0.9, getY() + 0.6, getZ() + d.getOffsetZ() * 0.9);
				if (!getWorld().getBlockState(p).getCollisionShape(getWorld(), p).isEmpty()) { dataTracker.set(WALL_YAW, d.asRotation()); break; }
			}
		}
		switch (getState()) {
			case SCREAM -> { if (stateTicks >= 80) setState(IDLE); }
			case SPIT -> {
				if (stateTicks == 14 || stateTicks == 20 || stateTicks == 26) fireAcid();
				if (stateTicks >= 34) setState(IDLE);
			}
			case STUN -> { if (stateTicks >= 40) setState(IDLE); }
			case GRAB -> tickGrab();
			default -> {}
		}
	}

	// ---------- GRITO ----------
	void startScream() {
		setState(SCREAM);
		getNavigation().stop();
		screamCd = 900 + random.nextInt(600);
		World w = getWorld();
		playSound(SoundEvents.ENTITY_RAVAGER_ROAR, 4f, 0.6f);
		playSound(SoundEvents.ENTITY_GHAST_SCREAM, 4f, 1.7f);
		playSound(SoundEvents.ENTITY_WARDEN_ROAR, 2f, 1.4f);
		for (PlayerEntity p : w.getEntitiesByClass(PlayerEntity.class, getBoundingBox().expand(24), x -> true)) {
			p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 80, 0));
			p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 1));
		}
	}

	// ---------- ÁCIDO ----------
	void startSpit() { setState(SPIT); getNavigation().stop(); spitCd = 70 + random.nextInt(70); }

	private void fireAcid() {
		LivingEntity t = getTarget();
		if (t == null) return;
		AcidProjectileEntity a = new AcidProjectileEntity(XenomorphMod.ACID, getWorld());
		a.setOwner(this);
		Vec3d look = getRotationVec(1f);
		a.setPosition(getX() + look.x * 1.3, getEyeY() - 0.2, getZ() + look.z * 1.3);
		double dx = t.getX() - a.getX(), dy = t.getBodyY(0.35) - a.getY(), dz = t.getZ() - a.getZ();
		double h = Math.sqrt(dx * dx + dz * dz);
		a.setVelocity(dx, dy + h * 0.2, dz, 1.4f, 3f);
		getWorld().spawnEntity(a);
		playSound(SoundEvents.ENTITY_LLAMA_SPIT, 1.5f, 0.6f);
	}

	// ---------- AGARRE (minijuego W A S D) ----------
	void startGrab(PlayerEntity p) {
		if (!p.startRiding(this, true)) return;
		grabbed = p;
		setState(GRAB);
		getNavigation().stop();
		for (int i = 0; i < seq.length; i++) seq[i] = random.nextInt(4);
		seqIdx = 0; stepTicks = 0; lastKey = -1;
		playSound(SoundEvents.ENTITY_RAVAGER_ATTACK, 2f, 0.7f);
	}

	private static int readKey(PlayerEntity p) {
		float f = p.forwardSpeed, s = p.sidewaysSpeed;   // W=+f, S=-f, A=+s, D=-s
		if (Math.abs(f) < 0.5f && Math.abs(s) < 0.5f) return -1;
		if (Math.abs(f) >= Math.abs(s)) return f > 0 ? 0 : 2;
		return s > 0 ? 1 : 3;
	}

	private void tickGrab() {
		if (grabbed == null || grabbed.isRemoved() || !grabbed.isAlive()) { endGrab(false); return; }
		if (grabbed.getVehicle() != this) grabbed.startRiding(this, true);   // no te dejamos agacharte para escapar
		getNavigation().stop();
		setVelocity(0, getVelocity().y, 0);
		int key = readKey(grabbed);
		stepTicks++;
		if (key != -1 && key != lastKey) {
			if (key == seq[seqIdx]) {
				seqIdx++; stepTicks = 0;
				playSound(SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 1.2f);
				if (seqIdx >= seq.length) { endGrab(true); return; }
			} else {
				grabbed.damage(getDamageSources().mobAttack(this), 2f);   // tecla equivocada
				playSound(SoundEvents.ENTITY_RAVAGER_ATTACK, 1f, 1.3f);
			}
		}
		lastKey = key;
		if (stepTicks >= 30) { stepTicks = 0; grabbed.damage(getDamageSources().mobAttack(this), 4f); }   // muy lento
		if (age % 20 == 0) grabbed.damage(getDamageSources().mobAttack(this), 1.5f);
		StringBuilder bar = new StringBuilder();
		for (int i = 0; i < seq.length; i++) bar.append(i < seqIdx ? "■" : "□");
		grabbed.sendMessage(Text.literal("¡AGARRADO!  Pulsa  [ " + KEYS[seq[seqIdx]] + " ]   " + bar), true);
	}

	private void endGrab(boolean escaped) {
		PlayerEntity p = grabbed;
		grabbed = null;
		grabCd = 100;
		if (p != null) {
			p.stopRiding();
			if (escaped) {
				p.sendMessage(Text.literal("¡Te soltaste! ¡CORRE!"), true);
				p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 80, 1));
				Vec3d away = getPos().subtract(p.getPos()).multiply(1, 0, 1).normalize();
				setVelocity(away.x * 0.9, 0.4, away.z * 0.9);
				velocityDirty = true;
			}
		}
		setState(escaped ? STUN : IDLE);
		spitCd = 100;
	}

	@Override
	protected void updatePassengerPosition(Entity passenger, PositionUpdater updater) {
		if (!hasPassenger(passenger)) return;
		float yaw = getBodyYaw() * MathHelper.RADIANS_PER_DEGREE;
		updater.accept(passenger, getX() - MathHelper.sin(yaw) * 1.3, getY() + 0.9, getZ() + MathHelper.cos(yaw) * 1.3);
	}

	@Override public void onDeath(net.minecraft.entity.damage.DamageSource s) { if (grabbed != null) endGrab(false); super.onDeath(s); }

	@Override protected SoundEvent getAmbientSound() { return SoundEvents.ENTITY_CAT_HISS; }
	@Override protected SoundEvent getHurtSound(net.minecraft.entity.damage.DamageSource s) { return SoundEvents.ENTITY_SPIDER_HURT; }
	@Override protected SoundEvent getDeathSound() { return SoundEvents.ENTITY_RAVAGER_DEATH; }
	@Override public int getMinAmbientSoundDelay() { return 200; }
}
