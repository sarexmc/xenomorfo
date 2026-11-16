package com.xeno.client;

import com.xeno.XenomorphEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public class XenomorphRenderer extends EntityRenderer<XenomorphEntity> {
	private static final Identifier TEX = Identifier.of("xenomorph", "textures/entity/xenomorph.png");

	public XenomorphRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
		this.shadowRadius = 1.0f;
	}

	@Override public Identifier getTexture(XenomorphEntity e) { return TEX; }

	@Override
	public void render(XenomorphEntity e, float yaw, float td, MatrixStack m, VertexConsumerProvider vcp, int light) {
		m.push();
		boolean wall = e.isClimbing();
		// En la pared se orienta mirando hacia ella; en el suelo, hacia donde camina.
		float bodyYaw = wall ? e.getWallYaw() : MathHelper.lerpAngleDegrees(td, e.prevBodyYaw, e.bodyYaw);
		m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - bodyYaw));

		if (e.deathTime > 0) {
			float f = Math.min(1f, MathHelper.sqrt((e.deathTime + td - 1f) / 20f * 1.6f));
			m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(f * 90f));
		}
		if (wall) {
			// Escalada "acostado": el vientre pegado a la pared, la cabeza hacia arriba.
			m.translate(0f, WALL_PIVOT_Y, WALL_PIVOT_Z);
			m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));
			m.translate(0f, -WALL_PIVOT_Y, 0f);
		}

		int st = e.getState();
		String anim;
		float t = (e.age + td) / 20f;
		if (st == XenomorphEntity.SCREAM || st == XenomorphEntity.SPIT) { anim = "roar"; t = (e.age - e.animStart + td) / 20f; }
		else if (st == XenomorphEntity.GRAB) anim = "idle2";
		else if (st == XenomorphEntity.STUN) { anim = "idle2"; t = (e.age - e.animStart + td) / 20f; }
		else {
			float sp = e.limbAnimator.getSpeed(td);
			anim = sp > 0.55f ? "sprinting" : sp > 0.08f ? "running" : "idle";
			if (wall && sp > 0.02f) anim = "running";
		}

		VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEX));
		int overlay = OverlayTexture.packUv(OverlayTexture.getU(0f), OverlayTexture.getV(e.hurtTime > 0 || e.deathTime > 0));
		XenoModel.render(anim, t, m.peek(), vc, light, overlay);
		m.pop();
		super.render(e, yaw, td, m, vcp, light);
	}

	// Ajusta estos dos valores si en la pared el modelo queda muy separado o clavado.
	private static final float WALL_PIVOT_Y = 0.8f;
	private static final float WALL_PIVOT_Z = 0.18f;
}
