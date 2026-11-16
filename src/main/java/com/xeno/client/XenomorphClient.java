package com.xeno.client;

import com.xeno.XenomorphMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.EmptyEntityRenderer;

public class XenomorphClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRendererRegistry.register(XenomorphMod.XENOMORPH, XenomorphRenderer::new);
		EntityRendererRegistry.register(XenomorphMod.ACID, EmptyEntityRenderer::new);
		EntityRendererRegistry.register(XenomorphMod.PUDDLE, EmptyEntityRenderer::new);
	}
}
