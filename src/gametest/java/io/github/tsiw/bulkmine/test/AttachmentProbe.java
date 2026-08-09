package io.github.tsiw.bulkmine.test;

import io.github.tsiw.bulkmine.BulkMine;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.impl.attachment.AttachmentRegistryImpl;

/**
 * Watches for the attachment types being registered too late.
 *
 * <p>They live in a static initialiser, which Java only runs when something
 * first touches the class. Every reference to it in mod init used to be inside
 * a lambda, so the first touch was the first player joining -- by which point
 * the server had already read and discarded that player's saved values,
 * logging "Found unknown attachment type bulkmine:enabled". Nothing about that
 * failure is visible from inside a test that has itself already touched the
 * class, so the check has to happen from a mod initialiser of its own, before
 * the server is up.
 *
 * <p>Development only: this class ships with the game tests, never in the mod.
 */
public final class AttachmentProbe implements ModInitializer {
	/** Whether the types existed by the time the server started. */
	public static volatile boolean registeredBeforeServerStart;

	@Override
	public void onInitialize() {
		ServerLifecycleEvents.SERVER_STARTING.register(server ->
				registeredBeforeServerStart =
						AttachmentRegistryImpl.get(BulkMine.id("enabled")) != null
								&& AttachmentRegistryImpl.get(BulkMine.id("cube_size")) != null);
	}
}
