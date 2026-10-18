package io.github.donttemptfate.fatesofa.game;

import io.github.donttemptfate.fatesofa.FatesOfaClient;
import io.github.donttemptfate.fatesofa.state.ModApiCodec;
import io.github.donttemptfate.fatesofa.state.SkyBlockState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * Speaks the Hypixel Mod API protocol directly for location updates (which island, which server), so OFA
 * doesn't depend on the official Mod API mod (no build for every Minecraft version). When the official
 * mod is installed it owns these channels and OFA falls back to the scoreboard / tab list.
 */
public final class ModApi {
	private ModApi() {}

	/** Raw bytes on one channel. */
	public record Raw(CustomPacketPayload.Type<Raw> type, byte[] data) implements CustomPacketPayload {
	}

	static CustomPacketPayload.Type<Raw> type(String id) {
		return new CustomPacketPayload.Type<>(Identifier.parse(id));
	}

	static StreamCodec<FriendlyByteBuf, Raw> codec(CustomPacketPayload.Type<Raw> type) {
		return CustomPacketPayload.codec((Raw p, FriendlyByteBuf buf) -> buf.writeBytes(p.data()), buf -> {
			int n = Math.min(buf.readableBytes(), 1 << 16);
			byte[] d = new byte[n];
			buf.readBytes(d);
			buf.skipBytes(buf.readableBytes());
			return new Raw(type, d);
		});
	}

	static final CustomPacketPayload.Type<Raw> HELLO = type(ModApiCodec.HELLO);
	static final CustomPacketPayload.Type<Raw> LOCATION = type(ModApiCodec.LOCATION);
	static final CustomPacketPayload.Type<Raw> REGISTER = type(ModApiCodec.REGISTER);

	private static boolean active;

	public static boolean active() {
		return active;
	}

	/**
	 * Claims the channels once every mod has started, so a mod that implements the same protocol (or bundles the
	 * official one) always gets them first and nothing crashes; OFA then falls back to the scoreboard and tab list.
	 */
	static void register(SkyBlockState state) {
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(mc -> claim(state));
	}

	static void claim(SkyBlockState state) {
		FabricLoader fl = FabricLoader.getInstance();
		for (var mod : fl.getAllMods()) {
			String id = mod.getMetadata().getId().toLowerCase(java.util.Locale.ROOT);
			if (id.contains("hypixel") && (id.contains("api") || id.contains("mod-api"))) {
				FatesOfaClient.LOG.info("{} present: OFA reads location from the scoreboard and tab list", id);
				return;
			}
		}
		try {
			PayloadTypeRegistry.clientboundPlay().register(HELLO, codec(HELLO));
			PayloadTypeRegistry.clientboundPlay().register(LOCATION, codec(LOCATION));
			PayloadTypeRegistry.serverboundPlay().register(REGISTER, codec(REGISTER));
			ClientPlayNetworking.registerGlobalReceiver(HELLO, (p, ctx) -> {
				if (!ModApiCodec.hello(p.data())) return;
				// ask for location events (the server answers with one at once and on every server change)
				ClientPlayNetworking.send(new Raw(REGISTER, ModApiCodec.register(Map.of(ModApiCodec.LOCATION, ModApiCodec.LOCATION_VERSION))));
			});
			ClientPlayNetworking.registerGlobalReceiver(LOCATION, (p, ctx) -> {
				try {
					ModApiCodec.Location l = ModApiCodec.location(p.data());
					if (l == null) return;
					long now = System.currentTimeMillis();
					if (l.skyblock()) state.fromModApi(l.serverName(), l.mode(), now);
					else state.notSkyBlock(now);
				} catch (RuntimeException e) {
					FatesOfaClient.LOG.debug("odd location packet ignored: {}", e.toString());
				}
			});
			active = true;
		} catch (RuntimeException e) {
			// another mod already speaks this protocol: let it, read the scoreboard instead
			FatesOfaClient.LOG.info("Hypixel location channel taken by another mod ({}); using the scoreboard and tab list", e.toString());
		}
	}
}
