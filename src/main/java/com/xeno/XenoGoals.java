package com.xeno;

import java.util.EnumSet;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;

public class XenoGoals {
	/** Inmoviliza al alien mientras agarra o está aturdido. */
	public static class Hold extends Goal {
		final XenomorphEntity m;
		public Hold(XenomorphEntity m) { this.m = m; setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP)); }
		@Override public boolean canStart() { return m.getState() == XenomorphEntity.GRAB || m.getState() == XenomorphEntity.STUN; }
		@Override public boolean shouldContinue() { return canStart(); }
		@Override public void tick() { m.getNavigation().stop(); }
	}

	/** Grita al ver a su presa (la oscurece y la ralentiza). */
	public static class Scream extends Goal {
		final XenomorphEntity m;
		public Scream(XenomorphEntity m) { this.m = m; setControls(EnumSet.of(Control.MOVE, Control.LOOK)); }
		@Override public boolean canStart() {
			LivingEntity t = m.getTarget();
			return t != null && t.isAlive() && m.getState() == XenomorphEntity.IDLE && m.screamCd <= 0 && m.squaredDistanceTo(t) > 36 && m.canSee(t);
		}
		@Override public void start() { m.startScream(); }
		@Override public boolean shouldContinue() { return m.getState() == XenomorphEntity.SCREAM; }
		@Override public void tick() { LivingEntity t = m.getTarget(); if (t != null) m.getLookControl().lookAt(t, 30, 30); }
	}

	/** Escupe ráfagas de ácido a media distancia. */
	public static class Spit extends Goal {
		final XenomorphEntity m;
		public Spit(XenomorphEntity m) { this.m = m; setControls(EnumSet.of(Control.MOVE, Control.LOOK)); }
		@Override public boolean canStart() {
			LivingEntity t = m.getTarget();
			if (t == null || !t.isAlive() || m.getState() != XenomorphEntity.IDLE || m.spitCd > 0) return false;
			double d = m.squaredDistanceTo(t);
			return d > 36 && d < 28 * 28 && m.canSee(t);
		}
		@Override public void start() { m.startSpit(); }
		@Override public boolean shouldContinue() { return m.getState() == XenomorphEntity.SPIT; }
		@Override public void tick() { LivingEntity t = m.getTarget(); if (t != null) m.getLookControl().lookAt(t, 40, 40); }
	}

	/** Persigue a la presa y, si es un jugador, lo agarra. */
	public static class Grab extends Goal {
		final XenomorphEntity m;
		public Grab(XenomorphEntity m) { this.m = m; setControls(EnumSet.of(Control.MOVE, Control.LOOK)); }
		@Override public boolean canStart() { LivingEntity t = m.getTarget(); return t != null && t.isAlive() && m.getState() == XenomorphEntity.IDLE; }
		@Override public boolean shouldContinue() { return canStart(); }
		@Override public boolean shouldRunEveryTick() { return true; }
		@Override public void tick() {
			LivingEntity t = m.getTarget();
			if (t == null) return;
			m.getLookControl().lookAt(t, 30, 30);
			m.getNavigation().startMovingTo(t, 1.45);
			double d = m.squaredDistanceTo(t);
			if (t instanceof PlayerEntity p) {
				if (d <= 5.5 && m.grabCd <= 0 && m.canSee(t)) m.startGrab(p);
			} else if (d <= 4.0 && m.age % 20 == 0) m.tryAttack(t);
		}
	}
}
