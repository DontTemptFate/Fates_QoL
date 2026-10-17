package io.github.donttemptfate.fatesofa.state;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The Hypixel Mod API wire format (public, MIT: github.com/HypixelDev/ModAPI), for the few packets OFA
 * uses: the server's hello, the location event, and the client's event registration. Used when the
 * official Hypixel Mod API mod isn't installed (it has no build for every Minecraft version OFA supports).
 *
 * <p>Clientbound: {@code bool ok, [varint error], varint version, fields…}. Strings are
 * varint length + UTF-8. Optional fields are a bool then the value.
 */
public final class ModApiCodec {
	private ModApiCodec() {}

	public static final String HELLO = "hypixel:hello";
	public static final String REGISTER = "hypixel:register";
	public static final String LOCATION = "hyevent:location";
	public static final int LOCATION_VERSION = 1;
	public static final int REGISTER_VERSION = 1;

	/** A decoded location event. Optional parts are null. */
	public record Location(String serverName, String serverType, String lobbyName, String mode, String map) {
		public boolean skyblock() {
			return "SKYBLOCK".equalsIgnoreCase(serverType);
		}
	}

	/** Decodes a location packet; null when it is an error answer or a version OFA doesn't know. */
	public static Location location(byte[] data) {
		Reader r = new Reader(data);
		if (!r.bool()) return null;
		if (r.varInt() != LOCATION_VERSION) return null;
		String server = r.string(32767);
		String type = r.bool() ? r.string(32767) : null;
		String lobby = r.bool() ? r.string(32767) : null;
		String mode = r.bool() ? r.string(32767) : null;
		String map = r.bool() ? r.string(32767) : null;
		return new Location(server, type, lobby, mode, map);
	}

	/** True when a hello packet decodes (the server speaks the Mod API). */
	public static boolean hello(byte[] data) {
		try {
			Reader r = new Reader(data);
			if (!r.bool()) return false;
			r.varInt(); // environment id
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** The registration packet: which event packets (identifier → version) the client wants. */
	public static byte[] register(Map<String, Integer> events) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writeVarInt(out, REGISTER_VERSION);
		writeVarInt(out, events.size());
		for (Map.Entry<String, Integer> e : events.entrySet()) {
			byte[] s = e.getKey().getBytes(StandardCharsets.UTF_8);
			if (s.length > 20) throw new IllegalArgumentException("identifier too long");
			writeVarInt(out, s.length);
			out.write(s, 0, s.length);
			writeVarInt(out, e.getValue());
		}
		return out.toByteArray();
	}

	static void writeVarInt(ByteArrayOutputStream out, int v) {
		while ((v & ~0x7F) != 0) {
			out.write((v & 0x7F) | 0x80);
			v >>>= 7;
		}
		out.write(v);
	}

	/** Bounds-checked reader; any overrun throws (the packet is then ignored). */
	static final class Reader {
		final byte[] d;
		int i;

		Reader(byte[] d) {
			this.d = d;
		}

		int u8() {
			if (i >= d.length) throw new IllegalArgumentException("packet too short");
			return d[i++] & 0xFF;
		}

		boolean bool() {
			return u8() != 0;
		}

		int varInt() {
			int v = 0, shift = 0, b;
			do {
				b = u8();
				v |= (b & 0x7F) << shift;
				shift += 7;
				if (shift > 35) throw new IllegalArgumentException("varint too long");
			} while ((b & 0x80) != 0);
			return v;
		}

		String string(int maxChars) {
			int len = varInt();
			if (len < 0 || len > maxChars * 3 || i + len > d.length) throw new IllegalArgumentException("bad string length");
			String s = new String(d, i, len, StandardCharsets.UTF_8);
			i += len;
			return s;
		}
	}
}
