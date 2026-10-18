package io.github.donttemptfate.fatesofa.record;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.donttemptfate.fates.common.item.Items;
import io.github.donttemptfate.fates.common.text.Strip;
import io.github.donttemptfate.fates.common.text.Texts;
import io.github.donttemptfate.fatesofa.FatesOfaClient;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.game.ChatBus;
import io.github.donttemptfate.fatesofa.game.Game;
import io.github.donttemptfate.fatesofa.game.StateReader;
import io.github.donttemptfate.fatesofa.state.Island;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The play recorder (/ofa record): while on, writes what OFA's readers read — sidebar, tab list, game chat,
 * action bar, titles, menu contents, location, the held item's data, nearby name tags and the dungeon map —
 * to a file in config/fatesofa/recordings/, so the readers can be checked against real Hypixel text.
 *
 * <p>Never written: anything a player typed (chat, party, guild, messages). Player names are replaced with
 * {me} / {player}. Off at every start; only the player turns it on. Nothing is sent anywhere.
 */
public final class PlayRecorder {
	private PlayRecorder() {}

	static final long MAX_BYTES = 40L * 1024 * 1024;
	static final Pattern TAB_NAME = Pattern.compile("\\[\\d{1,3}] (?:\\S{1,2} )?([A-Za-z0-9_]{3,16})");
	/** Menus about other players (friends, guild, parties, trades, co-ops, profiles): never written. */
	static final Pattern SOCIAL_MENU = Pattern.compile("(?i)^(?:friends?(?: list)?|guild\\b.*|party(?: finder)?\\b.*|co-?op(?! auction| bazaar)\\b.*|recent players|players|visit\\b.*|social\\b.*|.*'s? profile|.*profile viewer|you\\s{2,}.*)$");
	/** Keys in an item's data that identify the item or when/where it was made: left out of recordings. */
	static final java.util.Set<String> PRIVATE_KEYS = java.util.Set.of("uuid", "timestamp", "originTag", "donated_museum", "bossId", "spawnedFor",
			"champion_combat_xp", "farmed_cultivating_owner", "player", "owner", "rarity_upgrade_by", "party_finder");
	/** A name after a rank tag in menu text ("[MVP+] Name"). */
	static final Pattern RANK_NAME = Pattern.compile("\\[(?:VIP|MVP)\\+{0,2}] ([A-Za-z0-9_]{3,16})");

	private static BufferedWriter out;
	private static Path file;
	private static long bytes, lines, startedAt;
	private static int tick;
	private static RecordSanitizer clean = new RecordSanitizer("", List.of());
	private static Set<String> knownNames = Set.of();
	private static List<String> lastSidebar = List.of(), lastTab = List.of();
	private static String lastBar = "", lastLocation = "", lastHeld = "", lastNear = "", lastMap = "";
	private static final List<Object[]> pendingMenus = new ArrayList<>();

	public static boolean on() {
		return out != null;
	}

	public static void register() {
		ChatBus.onChat(m -> safe(() -> {
			if (!on()) return;
			if (clean.typedByPlayer(m.plain())) return; // never written, not even as a placeholder count
			JsonObject o = line("chat");
			o.addProperty("text", clean.clean(Texts.legacy(m.component())));
			write(o);
		}));
		ChatBus.onActionBar(m -> safe(() -> {
			if (!on()) return;
			String t = clean.clean(Texts.legacy(m.component()));
			if (t.equals(lastBar)) return;
			lastBar = t;
			JsonObject o = line("bar");
			o.addProperty("text", t);
			write(o);
		}));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> safe(() -> tick(mc)));
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> safe(() -> {
			if (on() && screen instanceof AbstractContainerScreen<?> s) pendingMenus.add(new Object[] {s, 0, ""});
		}));
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> stop(false));
	}

	/** Called by the title mixin. */
	public static void title(String kind, Component c) {
		safe(() -> {
			if (!on() || c == null) return;
			JsonObject o = line(kind);
			o.addProperty("text", clean.clean(Texts.legacy(c)));
			write(o);
		});
	}

	public static void start() {
		if (on()) {
			Game.info("Already recording to " + file.getFileName() + ". /ofa record off to stop.");
			return;
		}
		try {
			Path dir = Ofa.get().dir.resolve("recordings");
			Files.createDirectories(dir);
			file = dir.resolve("ofa-rec-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".jsonl");
			out = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
			bytes = lines = 0;
			startedAt = System.currentTimeMillis();
			lastSidebar = lastTab = List.of();
			lastBar = lastLocation = lastHeld = lastNear = lastMap = "";
			refreshNames(Minecraft.getInstance());
			JsonObject o = line("start");
			o.addProperty("ofa", Ofa.get().version);
			o.addProperty("mc", FabricLoader.getInstance().getModContainer("minecraft").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?"));
			write(o);
			Game.ok("Recording. Play normally; player chat and names are left out. /ofa record off when done.");
			Game.info("File: config/fatesofa/recordings/" + file.getFileName());
		} catch (IOException e) {
			out = null;
			Game.error("Couldn't start recording: " + e.getMessage());
		}
	}

	public static void stop(boolean say) {
		if (!on()) {
			if (say) Game.info("The recorder isn't on.");
			return;
		}
		try {
			JsonObject o = line("stop");
			o.addProperty("lines", lines);
			write(o);
			out.close();
		} catch (IOException e) {
			FatesOfaClient.LOG.warn("recorder close: {}", e.toString());
		}
		out = null;
		pendingMenus.clear();
		if (say) Game.ok("Recording saved: config/fatesofa/recordings/" + file.getFileName() + " (" + lines + " lines, "
				+ Math.max(1, bytes / 1024) + " KB). Send that file to Fate.");
	}

	public static String status() {
		return on() ? "recording " + file.getFileName() + " · " + lines + " lines · " + Math.max(1, bytes / 1024) + " KB" : "off";
	}

	static void tick(Minecraft mc) {
		if (!on() || mc.level == null || mc.player == null) return;
		tick++;
		if (tick % 40 == 0) refreshNames(mc);
		if (tick % 20 == 0) {
			List<String> sb = StateReader.sidebarRaw(mc);
			List<String> sbClean = sb == null ? List.of() : sb.stream().map(clean::clean).toList();
			if (!sbClean.equals(lastSidebar)) {
				lastSidebar = sbClean;
				write(withLines(line("sidebar"), sbClean));
			}
			List<String> tab = StateReader.tabLines(mc).stream().map(clean::clean).toList();
			if (!tab.equals(lastTab)) {
				lastTab = tab;
				write(withLines(line("tab"), tab));
			}
			var st = Ofa.get().state;
			String loc = st.onSkyBlock() + "|" + (st.island() == null ? "" : st.island().name()) + "|" + Objects.toString(st.area(), "") + "|"
					+ Objects.toString(st.server(), "") + "|" + st.profileType().name();
			if (!loc.equals(lastLocation)) {
				lastLocation = loc;
				JsonObject o = line("location");
				o.addProperty("skyblock", st.onSkyBlock());
				o.addProperty("island", st.island() == null ? null : st.island().name());
				o.addProperty("area", st.area());
				o.addProperty("server", st.server());
				o.addProperty("profile", st.profileType().name());
				write(o);
			}
		}
		if (tick % 10 == 0) held(mc);
		if (tick % 40 == 0) nearby(mc);
		if (tick % 100 == 0 && Ofa.get().state.in(Island.DUNGEON)) map(mc);
		menus();
		if (tick % 100 == 0) {
			try {
				out.flush();
			} catch (IOException e) {
				stop(true);
			}
		}
	}

	static void refreshNames(Minecraft mc) {
		Set<String> names = new LinkedHashSet<>();
		if (mc.level != null) for (Player p : mc.level.players()) names.add(p.getName().getString());
		for (String l : StateReader.tabLines(mc)) {
			Matcher m = TAB_NAME.matcher(Strip.clean(l));
			while (m.find()) names.add(m.group(1));
		}
		String me = mc.player == null ? "" : mc.player.getName().getString();
		knownNames = names;
		clean = new RecordSanitizer(me, names);
	}

	static void held(Minecraft mc) {
		ItemStack st = mc.player.getMainHandItem();
		String data = st.isEmpty() ? "" : String.valueOf(withoutPrivateKeys(Items.data(st)));
		if (data.length() > 4000) data = data.substring(0, 4000);
		if (data.equals(lastHeld)) return;
		lastHeld = data;
		JsonObject o = line("held");
		o.addProperty("name", st.isEmpty() ? "" : clean.clean(Texts.legacy(Items.name(st))));
		o.addProperty("data", clean.clean(data));
		write(o);
	}

	/** A copy of item data without the keys that identify the item or its owner. */
	static com.google.gson.JsonElement withoutPrivateKeys(com.google.gson.JsonElement e) {
		if (e == null) return null;
		if (e.isJsonObject()) {
			JsonObject out = new JsonObject();
			for (var en : e.getAsJsonObject().entrySet()) {
				if (PRIVATE_KEYS.contains(en.getKey())) continue;
				out.add(en.getKey(), withoutPrivateKeys(en.getValue()));
			}
			return out;
		}
		if (e.isJsonArray()) {
			JsonArray out = new JsonArray();
			for (var x : e.getAsJsonArray()) out.add(withoutPrivateKeys(x));
			return out;
		}
		return e;
	}

	static void nearby(Minecraft mc) {
		Set<String> names = new TreeSet<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e == mc.player || e instanceof Player || !e.hasCustomName() || e.distanceToSqr(mc.player) > 144) continue;
			names.add(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath() + ": " + clean.clean(Texts.legacy(e.getCustomName())));
			if (names.size() >= 40) break;
		}
		String joined = String.join("\n", names);
		if (joined.equals(lastNear)) return;
		lastNear = joined;
		write(withLines(line("nearby"), List.copyOf(names)));
	}

	static void map(Minecraft mc) {
		ItemStack st = mc.player.getInventory().getItem(8);
		MapId id = st.get(DataComponents.MAP_ID);
		MapItemSavedData data = id == null ? null : mc.level.getMapData(id);
		if (data == null) return;
		String b64 = Base64.getEncoder().encodeToString(data.colors);
		if (b64.equals(lastMap)) return;
		lastMap = b64;
		JsonObject o = line("map");
		o.addProperty("colors", b64);
		write(o);
	}

	/** Menus are written 10 ticks after opening (Hypixel fills them in a moment later) and again when they change. */
	static void menus() {
		Minecraft mc = Minecraft.getInstance();
		pendingMenus.removeIf(p -> mc.gui.screen() != p[0] || (int) p[1] > 400);
		for (Object[] p : pendingMenus) {
			int age = (int) p[1] + 1;
			p[1] = age;
			if (age < 10 || age % 20 != 10) continue;
			AbstractContainerScreen<?> s = (AbstractContainerScreen<?>) p[0];
			String plainTitle = Strip.clean(s.getTitle().getString()).trim();
			if (SOCIAL_MENU.matcher(plainTitle).matches()) {
				if ("".equals(p[2])) {
					p[2] = "skipped";
					JsonObject o = line("menu");
					o.addProperty("title", "(a menu about other players — not recorded)");
					write(o);
				}
				continue;
			}
			// names that only appear in this menu's text ("Seller: [MVP+] Name") join the name list first
			Set<String> found = new LinkedHashSet<>();
			for (Slot sl : s.getMenu().slots) {
				if (sl.container instanceof Inventory || !sl.hasItem()) continue;
				for (String l : Items.lorePlain(sl.getItem())) {
					Matcher m = RANK_NAME.matcher(l);
					while (m.find()) found.add(m.group(1));
					m = TAB_NAME.matcher(l);
					while (m.find()) found.add(m.group(1));
				}
			}
			if (!found.isEmpty()) {
				found.addAll(knownNames);
				clean = new RecordSanitizer(Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getName().getString(), found);
			}
			JsonArray slots = new JsonArray();
			for (Slot sl : s.getMenu().slots) {
				if (sl.container instanceof Inventory || !sl.hasItem()) continue;
				ItemStack st = sl.getItem();
				JsonObject so = new JsonObject();
				so.addProperty("slot", sl.index);
				String id = Items.id(st);
				if (id != null) so.addProperty("id", id);
				so.addProperty("count", st.getCount());
				so.addProperty("name", clean.clean(Texts.legacy(Items.name(st))));
				JsonArray lore = new JsonArray();
				List<String> ll = Items.loreLegacy(st);
				for (int i = 0; i < Math.min(30, ll.size()); i++) lore.add(clean.clean(ll.get(i)));
				so.add("lore", lore);
				slots.add(so);
			}
			String sig = slots.toString();
			if (sig.equals(p[2])) continue;
			p[2] = sig;
			JsonObject o = line("menu");
			o.addProperty("title", clean.clean(Texts.legacy(s.getTitle())));
			o.add("slots", slots);
			write(o);
		}
	}

	static JsonObject line(String kind) {
		JsonObject o = new JsonObject();
		o.addProperty("t", System.currentTimeMillis() - startedAt);
		o.addProperty("k", kind);
		return o;
	}

	static JsonObject withLines(JsonObject o, List<String> list) {
		JsonArray a = new JsonArray();
		for (String s : list) a.add(s);
		o.add("lines", a);
		return o;
	}

	static void write(JsonObject o) {
		if (out == null) return;
		try {
			String s = o.toString();
			out.write(s);
			out.write('\n');
			bytes += s.length() + 1;
			lines++;
			if (bytes > MAX_BYTES) {
				Game.warn("Recording reached " + (MAX_BYTES >> 20) + " MB and stopped.");
				stop(true);
			}
		} catch (IOException e) {
			FatesOfaClient.LOG.warn("recorder write: {}", e.toString());
			out = null;
		}
	}

	static void safe(Runnable r) {
		try {
			r.run();
		} catch (Throwable e) {
			FatesOfaClient.LOG.warn("recorder error: {}", e.toString());
		}
	}
}
