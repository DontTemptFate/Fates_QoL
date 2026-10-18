package io.github.donttemptfate.fatesofa.ui;

import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Theme;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.config.Presets;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.config.Settings;
import io.github.donttemptfate.fatesofa.config.Values;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.core.Features;
import io.github.donttemptfate.fatesofa.features.General;
import io.github.donttemptfate.fatesofa.game.Game;
import io.github.donttemptfate.fatesofa.hud.HudEditorScreen;
import io.github.donttemptfate.fatesofa.hud.Rect;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * OFA settings: grouped by activity, searchable, every switch with a one-line explanation. Also the
 * first-launch presets, theme and HUD editor, data status, diagnostics and credits.
 */
public final class SettingsScreen extends Screen {
	/** Pages in the sidebar: special pages plus one per category that has features. */
	record Page(String label, Category category, String special) {
	}

	static final String START = "start", DATA = "data", DIAG = "diag", CREDITS = "credits";

	private final Screen parent;
	private final Ofa ofa;
	private final List<Page> pages = new ArrayList<>();
	private Page page;
	private EditBox search;
	private EditBox textEdit;
	private final Widgets.Hits hits = new Widgets.Hits();
	private Widgets.Slider activeSlider;
	private Rect activeSliderRect;
	private int scroll;
	private int contentH;
	private String note = "";
	private long noteAt;

	// layout (computed in init)
	private int px, py, pw, ph, sideW, cx, cy, cw, ch;

	public SettingsScreen(Screen parent) {
		this(parent, null);
	}

	public SettingsScreen(Screen parent, Category open) {
		super(Component.literal("Fates OFA settings"));
		this.parent = parent;
		this.ofa = Ofa.get();
		pages.add(new Page("Start", null, START));
		for (Category c : Category.values()) {
			boolean any = Features.all().stream().anyMatch(f -> f.category == c);
			if (any) pages.add(new Page(c.label, c, null));
		}
		pages.add(new Page("Data & Prices", null, DATA));
		pages.add(new Page("Diagnostics", null, DIAG));
		pages.add(new Page("Credits", null, CREDITS));
		page = pages.get(0);
		if (open != null) {
			for (Page p : pages) {
				if (p.category() == open) page = p;
			}
		}
	}

	@Override
	protected void init() {
		pw = Math.min(this.width - 16, 560);
		ph = this.height - 16;
		px = (this.width - pw) / 2;
		py = 8;
		sideW = Math.min(120, pw / 4);
		cx = px + sideW + 16;
		cy = py + 34;
		cw = pw - sideW - 26;
		ch = ph - 44;
		String old = search == null ? "" : search.getValue();
		search = new EditBox(this.font, cx, py + 12, cw - 136, 14, Component.literal("Search"));
		search.setHint(Component.literal("Search all settings…"));
		search.setValue(old);
		search.setResponder(s -> scroll = 0);
		addRenderableWidget(search);
		textEdit = new EditBox(this.font, 0, -100, 100, 14, Component.literal("Value"));
		textEdit.setVisible(false);
		addRenderableWidget(textEdit);
	}

	/** For the in-game test: the open page's name. */
	public String pageLabel() {
		return page.label();
	}

	/** For the in-game test: a point 80% along the first slider drawn last frame (GUI pixels), or null. */
	public double[] firstSliderPoint() {
		for (Widgets.Hit h : hits.list) {
			if (h.slider() != null) return new double[] {h.rect().x() + h.rect().w() * 0.8, h.rect().y() + h.rect().h() / 2.0};
		}
		return null;
	}

	/** For the in-game test: the centre of a sidebar entry, in GUI pixels (null if not listed). */
	public double[] sidebarPoint(String label) {
		int sy = cy;
		for (Page p : pages) {
			if (p.label().equals(label)) return new double[] {px + 8 + sideW / 2.0, sy + 7};
			sy += 15;
		}
		return null;
	}

	String query() {
		return search == null ? "" : search.getValue().trim();
	}

	// ==== drawing =====================================================================================

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractBackground(g, mouseX, mouseY, delta);
		Ui.frame(g, px, py, pw, ph, 0.94f);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		hits.clear();
		// header
		Ui.draw(g, Ui.t("FATES OFA", Ui.Role.TITLE, Ui.accent()), Ui.Role.TITLE, px + 10, py + 22);
		Ui.draw(g, Ui.t("v" + ofa.version, Ui.Role.LABEL, Ui.DARK), Ui.Role.LABEL, px + 10, py + 31);
		// view switches next to the search box
		Rect advR = new Rect(cx + cw - 130, py + 13, 64, 12);
		Rect chgR = new Rect(cx + cw - 62, py + 13, 62, 12);
		boolean adv = ofa.settings.on(General.ADVANCED);
		Widgets.chip(g, font, advR.x(), advR.y(), advR.w(), "Advanced", adv, advR.contains(mouseX, mouseY));
		Widgets.chip(g, font, chgR.x(), chgR.y(), chgR.w(), "Changed", changedOnly, chgR.contains(mouseX, mouseY));
		if (advR.contains(mouseX, mouseY)) g.setTooltipForNextFrame(font, font.split(Component.literal("Show every option of every feature (otherwise the less common ones stay behind \"+N advanced\")."), 200), mouseX, mouseY);
		if (chgR.contains(mouseX, mouseY)) g.setTooltipForNextFrame(font, font.split(Component.literal("Only list settings you changed from their defaults."), 200), mouseX, mouseY);
		// sidebar
		int sy = cy;
		boolean searching = !query().isEmpty();
		for (Page p : pages) {
			boolean active = !searching && p == page;
			Rect r = new Rect(px + 8, sy, sideW, 14);
			boolean hover = r.contains(mouseX, mouseY);
			if (active) g.fill(r.x(), r.y(), r.right(), r.bottom(), (Ui.accent() & 0xFFFFFF) | 0x40000000);
			else if (hover) g.fill(r.x(), r.y(), r.right(), r.bottom(), 0x20FFFFFF);
			if (active) g.fill(r.x(), r.y(), r.x() + 2, r.bottom(), Ui.accent());
			Ui.draw(g, Ui.t(p.label(), Ui.Role.BODY, active ? Ui.WHITE : hover ? 0xFFDDDDDD : Ui.GRAY), Ui.Role.BODY, r.x() + 6, r.y() + 10);
			hits.add(r, () -> {
				page = p;
				scroll = 0;
				search.setValue("");
				stopEditing();
			}, null);
			sy += 15;
			if (sy > py + ph - 20) break;
		}
		// content
		g.enableScissor(cx - 2, cy, cx + cw + 2, cy + ch);
		int y = cy - scroll;
		List<Row> rows = rows();
		for (Row row : rows) {
			int h = row.height(cw);
			if (y + h >= cy && y <= cy + ch) row.draw(g, cx, y, cw, mouseX, mouseY);
			y += h + 4;
		}
		g.disableScissor();
		contentH = y + scroll - cy;
		if (contentH > ch) {
			int trackH = ch;
			int thumbH = Math.max(12, trackH * ch / contentH);
			int thumbY = cy + (trackH - thumbH) * scroll / Math.max(1, contentH - ch);
			g.fill(cx + cw + 4, cy, cx + cw + 6, cy + trackH, Ui.rule());
			g.fill(cx + cw + 4, thumbY, cx + cw + 6, thumbY + thumbH, Ui.accent());
		}
		if (!note.isEmpty() && System.currentTimeMillis() - noteAt < 6000) {
			Ui.draw(g, Ui.t(note, Ui.Role.SMALL, Ui.YELLOW), Ui.Role.SMALL, cx, py + ph - 6);
		}
		// keep clicks outside the content area from reaching hidden rows
		hits.list.removeIf(h -> h.rect().x() >= cx - 2 && (h.rect().bottom() < cy || h.rect().y() > cy + ch));
		hits.named.values().removeIf(r -> r.x() >= cx - 2 && (r.bottom() < cy || r.y() > cy + ch));
		hits.add(advR, () -> ofa.settings.set(General.ADVANCED, !ofa.settings.on(General.ADVANCED)), null, "view:advanced");
		hits.add(chgR, () -> {
			changedOnly = !changedOnly;
			scroll = 0;
		}, null, "view:changed");

	}

	void setNote(String s) {
		note = s;
		noteAt = System.currentTimeMillis();
	}

	// ==== rows =========================================================================================

	interface Row {
		int height(int w);

		void draw(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my);
	}

	List<Row> rows() {
		List<Row> out = new ArrayList<>();
		String q = query();
		if (!q.isEmpty()) {
			int found = 0;
			for (Feature f : Features.all()) if (featureRows(f, out, q)) found++;
			for (Setting<?> s : General.globals()) if (s.matches(q) && (!changedOnly || !ofa.settings.isDefault(s))) {
				out.add(settingRow(s));
				found++;
			}
			if (found == 0) out.add(text("Nothing matches \"" + q + "\".", Ui.GRAY));
			return out;
		}
		if (page.category() != null) {
			int shown = 0;
			for (Feature f : Features.all()) if (f.category == page.category() && featureRows(f, out, "")) shown++;
			if (shown == 0 && changedOnly) out.add(text("Nothing changed on this page.", Ui.GRAY));
			return out;
		}
		switch (page.special()) {
			case START -> startPage(out);
			case DATA -> dataPage(out);
			case DIAG -> diagPage(out);
			case CREDITS -> creditsPage(out);
			default -> {
			}
		}
		return out;
	}

	/** Groups opened by the player (feature id + "/" + group), kept while the game runs. */
	static final java.util.Set<String> OPEN_GROUPS = new java.util.HashSet<>();
	/** Features whose advanced settings the player opened. */
	static final java.util.Set<String> OPEN_ADVANCED = new java.util.HashSet<>();
	/** Editors (colour, sound, islands, lines, rules) the player opened, by setting key. */
	static final java.util.Set<String> OPEN_EDITORS = new java.util.HashSet<>();
	static boolean changedOnly;

	boolean advancedFor(Feature f) {
		return ofa.settings.on(General.ADVANCED) || OPEN_ADVANCED.contains(f.id);
	}

	/** Adds a feature's card, its direct settings and its groups. Returns false when nothing of it is shown. */
	boolean featureRows(Feature f, List<Row> out, String q) {
		boolean searching = !q.isEmpty();
		boolean fm = !searching || f.enabled.matches(q) || f.keywords().stream().anyMatch(k -> k.toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT)));
		boolean adv = advancedFor(f) || searching;
		List<Setting<?>> vis = new ArrayList<>();
		int advTotal = 0;
		for (Setting<?> s : f.settings()) {
			if (s.hidden) continue;
			if (s.advanced) advTotal++;
			if (changedOnly && ofa.settings.isDefault(s) && (s.rainbow == null || ofa.settings.isDefault(s.rainbow))) continue;
			if (searching && !fm && !s.matches(q)) continue;
			if (s.advanced && !adv && !changedOnly) continue;
			vis.add(s);
		}
		if (searching && !fm && vis.isEmpty()) return false;
		if (changedOnly && vis.isEmpty() && ofa.settings.isDefault(f.enabled)) return false;
		out.add(featureRow(f, ofa.settings.on(General.ADVANCED) || searching ? 0 : advTotal));
		java.util.Map<String, List<Setting<?>>> groups = new java.util.LinkedHashMap<>();
		for (Setting<?> s : vis) {
			if (s.group == null) out.add(settingRow(s));
			else groups.computeIfAbsent(s.group, k -> new ArrayList<>()).add(s);
		}
		for (var e : groups.entrySet()) {
			String id = f.id + "/" + e.getKey();
			boolean open = searching || changedOnly || OPEN_GROUPS.contains(id);
			out.add(groupRow(id, e.getKey(), e.getValue().size(), open));
			if (open) for (Setting<?> s : e.getValue()) out.add(settingRow(s));
		}
		return true;
	}

	Row groupRow(String id, String label, int count, boolean open) {
		return new Row() {
			public int height(int w) {
				return 13;
			}

			public void draw(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
				Rect r = new Rect(x + 10, y, w - 10, 13);
				boolean hover = r.contains(mx, my);
				if (hover) g.fill(r.x() - 2, r.y(), r.right(), r.bottom(), 0x14FFFFFF);
				g.text(font, (open ? "▾ " : "▸ ") + label, x + 12, y + 3, hover ? Ui.WHITE : Ui.accent(), true);
				String n = count + (count == 1 ? " option" : " options");
				g.text(font, n, r.right() - font.width(n) - 2, y + 3, Ui.DARK, true);
				g.fill(x + 12, y + 12, r.right(), y + 13, Ui.rule());
				hits.add(r, () -> {
					if (!OPEN_GROUPS.remove(id)) OPEN_GROUPS.add(id);
				}, null, "group:" + id);
			}
		};
	}

	void startPage(List<Row> out) {
		out.add(rule("WELCOME"));
		out.add(text("Fates OFA puts the SkyBlock tools you'd normally need several mods for into one. It only shows "
				+ "things and protects you from mistakes; it never plays for you. No account needed.", Ui.GRAY));
		out.add(rule("WHAT DO YOU MOSTLY DO?"));
		out.add(text("Pick one to switch on the matching features (you can change any of them later).", Ui.GRAY));
		List<String> names = new ArrayList<>(Presets.PRESETS.keySet());
		for (int i = 0; i < names.size(); i += 2) {
			String a = names.get(i);
			String b = i + 1 < names.size() ? names.get(i + 1) : null;
			out.add(buttons(a, () -> applyPreset(a), b, b == null ? null : () -> applyPreset(b)));
		}
		out.add(rule("LOOK"));
		for (Setting<?> s : General.lookSettings()) out.add(settingRow(s));
		out.add(buttons("Open HUD editor", () -> this.minecraft.gui.setScreen(new HudEditorScreen(this)), null, null));
		out.add(settingRow(General.ADVANCED));
		out.add(rule("KEYS"));
		out.add(text("Click a key, then press the new one (Esc: none). Right-click: unbind. Also in Options → Controls.", Ui.GRAY));
		for (net.minecraft.client.KeyMapping k : io.github.donttemptfate.fatesofa.game.Keys.all()) out.add(keyRow(k));
		out.add(rule("SHARE YOUR SETTINGS"));
		out.add(buttons("Copy settings to clipboard", () -> {
			this.minecraft.keyboardHandler.setClipboard(ofa.settings.export());
			setNote("Copied. Paste it to a friend; they use \"Paste settings\".");
		}, "Paste settings", () -> {
			int n = ofa.settings.importLine(this.minecraft.keyboardHandler.getClipboard());
			setNote(n < 0 ? "The clipboard doesn't hold OFA settings." : "Applied " + n + " settings.");
		}));
	}

	Row keyRow(net.minecraft.client.KeyMapping k) {
		return new Row() {
			public int height(int w) {
				return 14;
			}

			public void draw(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
				Rect row = new Rect(x + 10, y, w - 10, 14);
				boolean hover = row.contains(mx, my);
				if (hover) g.fill(row.x() - 2, row.y(), row.right(), row.bottom(), 0x14FFFFFF);
				g.text(font, Component.translatable(k.getName()).getString(), x + 12, y + 3, 0xFFCCCCCC, true);
				boolean cap = capturingMapping == k;
				String shown = cap ? "Press a key… (Esc: none)" : k.isUnbound() ? "Not bound" : k.getTranslatedKeyMessage().getString();
				int bw = Math.max(70, font.width(shown) + 12);
				Rect br = new Rect(x + w - bw, y + 1, bw, 12);
				Widgets.button(g, br.x(), br.y(), br.w(), br.h(), shown, br.contains(mx, my), cap);
				hits.add(row, () -> {
					capturingMapping = k;
					capturing = null;
				}, () -> io.github.donttemptfate.fatesofa.game.Keys.rebind(k, -1), "key:" + k.getName());
			}
		};
	}

	void applyPreset(String name) {
		var cats = Presets.categories(name);
		for (Feature f : Features.all()) {
			boolean on = cats.contains(f.category) && f.enabled.def;
			ofa.settings.set(f.enabled, on);
		}
		ofa.settings.set(General.ONBOARDED, true);
		setNote("Preset \"" + name + "\" applied.");
	}

	void dataPage(List<Row> out) {
		out.add(rule("ITEM DATA"));
		out.add(text("Items, recipes and constants come from the NotEnoughUpdates-REPO and SkyHanni-REPO on GitHub (MIT). "
				+ "Downloaded once, kept on your computer, checked for updates daily (change it below).", Ui.GRAY));
		out.add(kv("Status", ofa.repo.state().name().toLowerCase(Locale.ROOT).replace('_', ' ') + " · " + ofa.repo.message()));
		out.add(buttons("Check for an update now", () -> {
			ofa.repo.checkNow();
			setNote("Checking…");
		}, null, null));
		out.add(rule("PRICES"));
		out.add(text("Bazaar and NPC prices come from Hypixel's public API; auction-house values from the public price "
				+ "file on fatesflipper.com. No login, nothing personal is sent.", Ui.GRAY));
		long now = System.currentTimeMillis();
		out.add(kv("Bazaar", ofa.prices.bazaarCount() == 0 ? "not loaded" : ofa.prices.bazaarCount() + " products · " + Fmt.duration(now - ofa.prices.bazaarAt()) + " ago"));
		out.add(kv("Auction house", ofa.prices.ahCount() == 0 ? "not available yet" : ofa.prices.ahCount() + " items · " + Fmt.duration(now - ofa.prices.ahAt()) + " ago"));
		for (Setting<?> s : General.dataSettings()) out.add(settingRow(s));
	}

	void diagPage(List<Row> out) {
		out.add(rule("PERFORMANCE"));
		var costs = io.github.donttemptfate.fatesofa.core.Diagnostics.costs();
		out.add(kv("Frames per second", String.valueOf(Math.round(io.github.donttemptfate.fatesofa.core.Diagnostics.fps()))));
		out.add(kv("OFA per frame (all features)", ms(io.github.donttemptfate.fatesofa.core.Diagnostics.totalMs())));
		if (costs.isEmpty()) out.add(text("Measuring… (updates every second)", Ui.GRAY));
		for (int i = 0; i < Math.min(12, costs.size()); i++) {
			var c = costs.get(i);
			out.add(kv(c.feature().name, ms(c.msPerFrame()) + "  ·  peak " + ms(io.github.donttemptfate.fatesofa.core.Diagnostics.peak(c.feature().id))));
		}
		out.add(buttons("Copy diagnostics", () -> {
			this.minecraft.keyboardHandler.setClipboard(diagnosticsText());
			setNote("Copied. Paste it into a bug report on GitHub.");
		}, null, null));
		out.add(rule("FEATURES"));
		out.add(text("A feature that keeps failing switches itself off for the session instead of crashing the game. "
				+ "Switching it on again gives it a fresh start.", Ui.GRAY));
		int n = 0;
		for (Feature f : Features.all()) {
			if (f.errors().total() == 0) continue;
			n++;
			out.add(kv(f.name, (f.failed() ? "SWITCHED OFF · " : "") + f.errors().total() + " error(s): " + f.errors().lastError()));
		}
		if (n == 0) out.add(text("No errors this session.", Ui.GREEN));
		out.add(rule("LOCATION"));
		var st = ofa.state;
		out.add(kv("On SkyBlock", st.onSkyBlock() ? "yes" : "no"));
		out.add(kv("Island", st.island() == null ? "?" : st.island().display + (st.islandObserved() != null ? " (" + st.islandObserved().source().name().toLowerCase(Locale.ROOT) + ")" : "")));
		out.add(kv("Area", st.area() == null ? "?" : st.area()));
		out.add(kv("Profile", (st.profileName() == null ? "?" : st.profileName()) + " · " + st.profileType().name().toLowerCase(Locale.ROOT)));
		out.add(kv("Hypixel location channel", io.github.donttemptfate.fatesofa.game.ModApi.active() ? "in use" : "not used (scoreboard / tab list)"));
	}

	static String ms(double v) {
		return v < 0.01 ? "<0.01 ms" : String.format(Locale.ROOT, "%.2f ms", v);
	}

	/** Everything a bug report needs, as plain text: versions, mods, features on, errors, timings, location, data. */
	String diagnosticsText() {
		StringBuilder b = new StringBuilder();
		var loader = net.fabricmc.loader.api.FabricLoader.getInstance();
		String mcv = loader.getModContainer("minecraft").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
		b.append("Fates OFA ").append(ofa.version).append(" on Minecraft ").append(mcv).append('\n');
		b.append("Java ").append(System.getProperty("java.version")).append(" · ").append(System.getProperty("os.name")).append('\n');
		b.append("Mods: ");
		List<String> mods = new ArrayList<>();
		for (var m : loader.getAllMods()) {
			String id = m.getMetadata().getId();
			if (id.startsWith("fabric-") && !id.equals("fabric-api")) continue;
			if (id.equals("java") || id.equals("minecraft") || id.equals("fabricloader") || id.equals("mixinextras")) continue;
			mods.add(id + " " + m.getMetadata().getVersion().getFriendlyString());
		}
		mods.sort(null);
		b.append(String.join(", ", mods)).append('\n');
		List<String> on = new ArrayList<>();
		for (Feature f : Features.all()) if (ofa.settings.on(f.enabled)) on.add(f.id + (f.failed() ? "(OFF after errors)" : ""));
		b.append("Features on: ").append(String.join(", ", on)).append('\n');
		for (Feature f : Features.all()) {
			if (f.errors().total() > 0) b.append("Errors ").append(f.id).append(": ").append(f.errors().total()).append(" · ").append(f.errors().lastError()).append('\n');
		}
		b.append(String.format(Locale.ROOT, "FPS %.0f · OFA %.2f ms/frame%n", io.github.donttemptfate.fatesofa.core.Diagnostics.fps(),
				io.github.donttemptfate.fatesofa.core.Diagnostics.totalMs()));
		for (var c : io.github.donttemptfate.fatesofa.core.Diagnostics.costs()) {
			b.append(String.format(Locale.ROOT, "  %s %.3f ms (peak %.3f)%n", c.feature().id, c.msPerFrame(),
					io.github.donttemptfate.fatesofa.core.Diagnostics.peak(c.feature().id)));
		}
		var st = ofa.state;
		b.append("SkyBlock: ").append(st.onSkyBlock()).append(" · island ").append(st.island()).append(" · area ").append(st.area())
				.append(" · location channel ").append(io.github.donttemptfate.fatesofa.game.ModApi.active() ? "on" : "off").append('\n');
		b.append("Item data: ").append(ofa.repo.state()).append(" · ").append(ofa.repo.message()).append('\n');
		b.append("Prices: bazaar ").append(ofa.prices.bazaarCount()).append(" · auction ").append(ofa.prices.ahCount()).append('\n');
		b.append("Changed settings: ").append(ofa.settings.export().length()).append(" chars exported (not included)\n");
		return b.toString();
	}

	void creditsPage(List<Row> out) {
		out.add(rule("FATES OFA"));
		out.add(text("Made by DontTemptFate. Open source under GPL-3.0: github.com/DontTemptFate/Fates_QoL", Ui.GRAY));
		out.add(rule("DATA AND ASSETS"));
		out.add(text("NotEnoughUpdates-REPO (MIT) — items, recipes and constants.", Ui.WHITE));
		out.add(text("SkyHanni-REPO (MIT) — constants.", Ui.WHITE));
		out.add(text("Hypixel Mod API protocol (MIT, Hypixel Inc.) — location updates.", Ui.WHITE));
		out.add(text("Chakra Petch font (SIL Open Font License 1.1) — Cadson Demak.", Ui.WHITE));
		out.add(text("Prices: Hypixel public API and fatesflipper.com. Not affiliated with Hypixel or Mojang.", Ui.GRAY));
	}

	// ---- row builders ----------------------------------------------------------------------------------

	Row rule(String label) {
		return new Row() {
			public int height(int w) {
				return 12;
			}

			public void draw(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
				new Card.Rule(label, Ui.accent(), null).draw(g, x, y + 3, w);
			}
		};
	}

	Row text(String s, int color) {
		return new Row() {
			List<FormattedCharSequence> lines(int w) {
				return font.split(Ui.t(s, Ui.Role.SMALL, color), w);
			}

			public int height(int w) {
				return lines(w).size() * 10;
			}

			public void draw(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
				int ly = y;
				for (FormattedCharSequence l : lines(w)) {
					g.text(font, l, x, ly, Ui.WHITE, true);
					ly += 10;
				}
			}
		};
	}

	Row kv(String key, String value) {
		return new Row() {
			public int height(int w) {
				return 10 + (font.width(key) + font.width(value) + 12 > w ? font.split(Component.literal(value), w - 10).size() * 10 : 0);
			}

			public void draw(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
				g.text(font, key, x, y, Ui.GRAY, true);
				if (font.width(key) + font.width(value) + 12 <= w) g.text(font, value, x + w - font.width(value), y, Ui.WHITE, true);
				else {
					int ly = y + 10;
					for (FormattedCharSequence l : font.split(Component.literal(value), w - 10)) {
						g.text(font, l, x + 10, ly, Ui.WHITE, true);
						ly += 10;
					}
				}
			}
		};
	}

	Row buttons(String a, Runnable ra, String b, Runnable rb) {
		return new Row() {
			public int height(int w) {
				return 16;
			}

			public void draw(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
				int bw = b == null ? Math.min(w, 200) : (w - 6) / 2;
				Rect r1 = new Rect(x, y, bw, 16);
				Widgets.button(g, r1.x(), r1.y(), r1.w(), r1.h(), a, r1.contains(mx, my), false);
				hits.add(r1, ra, null);
				if (b != null) {
					Rect r2 = new Rect(x + bw + 6, y, bw, 16);
					Widgets.button(g, r2.x(), r2.y(), r2.w(), r2.h(), b, r2.contains(mx, my), false);
					hits.add(r2, rb, null);
				}
			}
		};
	}

	Row featureRow(Feature f, int advCount) {
		boolean advOpen = OPEN_ADVANCED.contains(f.id);
		boolean bar = !f.actions().isEmpty() || advCount > 0;
		return new Row() {
			List<FormattedCharSequence> desc(int w) {
				return font.split(Ui.t(f.description, Ui.Role.SMALL, Ui.GRAY), w - 30);
			}

			public int height(int w) {
				return 14 + desc(w).size() * 10 + (f.failed() ? 10 : 0) + (bar ? 17 : 0) + 2;
			}

			public void draw(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
				g.fill(x - 2, y - 1, x + w + 2, y + height(w) - 1, 0x18FFFFFF);
				boolean on = ofa.settings.on(f.enabled);
				int nameColor = f.failed() ? Ui.RED : on ? Ui.WHITE : Ui.GRAY;
				Ui.draw(g, Ui.t(f.name, Ui.Role.BODY_BOLD, nameColor), Ui.Role.BODY_BOLD, x + 2, y + 10);
				int tagX = x + 6 + Ui.width(Ui.t(f.name, Ui.Role.BODY_BOLD, nameColor), Ui.Role.BODY_BOLD);
				if (f.risk == Feature.Risk.HELPER) {
					Ui.draw(g, Ui.t("HELPER", Ui.Role.LABEL, Ui.ORANGE), Ui.Role.LABEL, tagX, y + 9);
				}
				Rect t = new Rect(x + w - 22, y + 2, 20, 10);
				Widgets.toggle(g, t.x(), t.y(), on, t.contains(mx, my));
				hits.add(new Rect(x, y, w, 14), () -> {
					boolean next = !ofa.settings.on(f.enabled);
					ofa.settings.set(f.enabled, next);
					if (next) f.resetErrors();
				}, null, "feature:" + f.id);
				int ly = y + 14;
				for (FormattedCharSequence l : desc(w)) {
					g.text(font, l, x + 2, ly, Ui.WHITE, true);
					ly += 10;
				}
				if (f.failed()) {
					g.text(font, "Switched off after errors: " + f.errors().lastError(), x + 2, ly, Ui.RED, true);
					ly += 10;
				}
				int bx = x + 2;
				for (Feature.Action a : f.actions()) {
					int bw = Ui.width(Ui.t(a.label(), Ui.Role.BODY_BOLD, 0), Ui.Role.BODY_BOLD) + 14;
					Rect br = new Rect(bx, ly + 2, bw, 13);
					Widgets.button(g, br.x(), br.y(), br.w(), br.h(), a.label(), br.contains(mx, my), false);
					hits.add(br, a.run(), null);
					bx += bw + 4;
				}
				if (advCount > 0) {
					String label = advOpen ? "Fewer options" : "+" + advCount + " advanced";
					int bw = font.width(label) + 12;
					Rect br = new Rect(x + w - bw - 2, ly + 2, bw, 13);
					boolean hv = br.contains(mx, my);
					g.text(font, label, br.x() + 6, br.y() + 3, hv ? Ui.WHITE : Ui.accent(), true);
					if (hv) g.fill(br.x() + 6, br.y() + 12, br.right() - 6, br.y() + 13, Ui.accent());
					hits.add(br, () -> {
						if (!OPEN_ADVANCED.remove(f.id)) OPEN_ADVANCED.add(f.id);
					}, null, "advanced:" + f.id);
				}
			}
		};
	}

	/** Lines of problems the setting's checker (or unknown placeholders) reports, at most three. */
	List<String> problems(Setting<?> s) {
		if (!s.type.textual()) return List.of();
		String v = String.valueOf(ofa.settings.get(s));
		List<String> out = new ArrayList<>();
		if (s.type == Setting.Type.TEMPLATE) {
			List<String> unknown = Values.unknownPlaceholders(v, s.placeholders);
			if (!unknown.isEmpty()) out.add("Unknown: {" + String.join("}, {", unknown) + "}");
		}
		if (s.checker != null) {
			try {
				out.addAll(s.checker.apply(v));
			} catch (RuntimeException ignored) {
				// a checker's problem never breaks the screen
			}
		}
		return out.size() > 3 ? out.subList(0, 3) : out;
	}

	static boolean expandable(Setting<?> s) {
		return switch (s.type) {
			case COLOR, SOUND, ISLANDS, LINES, RULES -> true;
			default -> false;
		};
	}

	@SuppressWarnings("unchecked")
	Row settingRow(Setting<?> s) {
		return new Row() {
			/** Sliders and text boxes go on a second line when the label leaves no room for them. */
			boolean twoLines(int w) {
				int control = switch (s.type) {
					case INT, DOUBLE -> Math.min(110, w / 3) + 40;
					case STRING, TEMPLATE -> Math.min(160, w / 2) + 6;
					case ENUM, KEY, SOUND, ISLANDS, LINES, RULES -> 90;
					default -> 26;
				};
				return font.width(s.label) + 16 + control > w;
			}

			int head(int w) {
				return twoLines(w) ? 26 : 14;
			}

			boolean open() {
				return expandable(s) && OPEN_EDITORS.contains(s.key);
			}

			int extra(int w) {
				int h = 0;
				if (s.type == Setting.Type.TEMPLATE && !s.placeholders.isEmpty()) h += 10;
				h += problems(s).size() * 10;
				if (open()) h += editorHeight(s, w - 24) + 4;
				return h;
			}

			public int height(int w) {
				return head(w) + extra(w);
			}

			public void draw(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
				Settings st = ofa.settings;
				Rect row = new Rect(x + 10, y, w - 10, head(w));
				int cyo = twoLines(w) ? 12 : 0; // controls' vertical offset
				boolean hover = row.contains(mx, my);
				if (hover || open()) g.fill(row.x() - 2, row.y(), row.right(), y + height(w), open() ? 0x10FFFFFF : 0x14FFFFFF);
				boolean changed = !st.isDefault(s) || (s.rainbow != null && !st.isDefault(s.rainbow));
				g.text(font, s.label, x + 12, y + 3, changed ? Ui.WHITE : 0xFFCCCCCC, true);
				if (changed) g.fill(x + 8, y + 3, x + 9, y + 10, Ui.accent());
				if (hover && !s.help.isEmpty()) g.setTooltipForNextFrame(font, font.split(Component.literal(s.help), 220), mx, my);
				int right = x + w;
				String name = "setting:" + s.key;
				switch (s.type) {
					case BOOL -> {
						Setting<Boolean> b = (Setting<Boolean>) s;
						Rect t = new Rect(right - 22, y + 2 + cyo, 20, 10);
						Widgets.toggle(g, t.x(), t.y(), st.on(b), t.contains(mx, my));
						hits.add(row, () -> st.set(b, !st.on(b)), () -> st.reset(b), name);
					}
					case INT, DOUBLE -> {
						double v = ((Number) st.get(s)).doubleValue();
						double frac = (v - s.min) / Math.max(1e-9, s.max - s.min);
						String shown = s.type == Setting.Type.INT ? Fmt.shortNum(v) : Fmt.trimmed(v);
						int sw = Math.min(110, w / 3);
						Rect sl = new Rect(right - sw, y + 2 + cyo, sw, 10);
						g.text(font, shown, sl.x() - 6 - font.width(shown), y + 3 + cyo, Ui.GOLD, true);
						Widgets.slider(g, sl.x(), sl.y(), sl.w(), frac, sl.contains(mx, my));
						Widgets.Slider set = f -> {
							double nv = s.min + f * (s.max - s.min);
							if (s.type == Setting.Type.INT) st.set((Setting<Integer>) s, (int) Math.round(nv));
							else st.set((Setting<Double>) s, Math.round(nv * 100) / 100.0);
						};
						// the row first: the last added wins, so the slider must come after its row
						hits.add(row, null, () -> st.reset(s), name);
						hits.slider(sl, set);
					}
					case ENUM -> {
						Setting<String> e = (Setting<String>) s;
						String shown = optionName(e, st.get(e));
						int bw = Math.max(60, font.width(shown) + 12);
						Rect br = new Rect(right - bw, y + 1 + cyo, bw, 12);
						Widgets.button(g, br.x(), br.y(), br.w(), br.h(), shown, br.contains(mx, my), false);
						hits.add(row, () -> cycle(e, 1), () -> cycle(e, -1), name);
					}
					case STRING, TEMPLATE -> {
						Setting<String> t = (Setting<String>) s;
						String v = st.get(t);
						int bw = Math.min(160, w / 2);
						Rect br = new Rect(right - bw, y + 1 + cyo, bw, 12);
						if (s.key.equals(editId)) placeEdit(br);
						else {
							Widgets.button(g, br.x(), br.y(), br.w(), br.h(), fit(v.isEmpty() ? "(empty)" : v, bw - 8), br.contains(mx, my), false);
							hits.add(row, () -> startEdit(s.key, st.get(t), s.maxLength, nv -> st.set(t, nv.trim())), () -> st.reset(t), name);
						}
					}
					case COLOR -> {
						Setting<Integer> c = (Setting<Integer>) s;
						int v = st.get(c);
						boolean rb = c.rainbow != null && st.on(c.rainbow);
						Rect sw = new Rect(right - 22, y + 2 + cyo, 20, 10);
						checker(g, sw);
						g.fill(sw.x(), sw.y(), sw.right(), sw.bottom(), Values.color(v, rb, System.currentTimeMillis()));
						Ui.ring(g, sw.x(), sw.y(), sw.w(), sw.h(), 1, open() ? Ui.accent() : Ui.WHITE);
						String hx = rb ? "Rainbow" : Values.hex(v);
						g.text(font, hx, sw.x() - 6 - font.width(hx), y + 3 + cyo, Ui.GRAY, true);
						hits.add(row, () -> toggleEditor(s), () -> {
							st.reset(c);
							if (c.rainbow != null) st.reset(c.rainbow);
						}, name);
					}
					case KEY -> {
						Setting<Integer> k = (Setting<Integer>) s;
						boolean cap = capturing == s;
						String shown = cap ? "Press a key… (Esc: none)" : io.github.donttemptfate.fatesofa.game.Keys.keyName(st.get(k));
						int bw = Math.max(70, font.width(shown) + 12);
						Rect br = new Rect(right - bw, y + 1 + cyo, bw, 12);
						Widgets.button(g, br.x(), br.y(), br.w(), br.h(), shown, br.contains(mx, my), cap);
						hits.add(row, () -> {
							capturing = s;
							capturingMapping = null;
						}, () -> st.reset(k), name);
					}
					case SOUND, ISLANDS, LINES, RULES -> {
						String shown = summary(s);
						int bw = Math.max(70, Math.min(w / 2, font.width(shown) + 22));
						Rect br = new Rect(right - bw, y + 1 + cyo, bw, 12);
						Widgets.button(g, br.x(), br.y(), br.w(), br.h(), fit(shown, bw - 16) + (open() ? " ▴" : " ▾"), br.contains(mx, my), open());
						hits.add(row, () -> toggleEditor(s), () -> st.reset(s), name);
					}
				}
				int ly = y + head(w);
				if (s.type == Setting.Type.TEMPLATE && !s.placeholders.isEmpty()) {
					g.text(font, fit("Fills in: {" + String.join("} {", s.placeholders) + "}   & = colour code", w - 26), x + 14, ly, Ui.DARK, true);
					ly += 10;
				}
				for (String pr : problems(s)) {
					g.text(font, fit(pr, w - 26), x + 14, ly, Ui.RED, true);
					ly += 10;
				}
				if (open()) drawEditor(g, s, x + 14, ly + 2, w - 24, mx, my);
			}
		};
	}

	String summary(Setting<?> s) {
		String v = String.valueOf(ofa.settings.get(s));
		return switch (s.type) {
			case SOUND -> {
				Values.Sound snd = Values.sound(v);
				yield snd.silent() ? "None" : Values.soundName(snd.id());
			}
			case ISLANDS -> {
				var is = Values.islands(v);
				if (is.isEmpty()) yield "Everywhere";
				if (is.size() == 1) yield is.iterator().next().display;
				yield is.size() + " islands";
			}
			case LINES -> Values.lines(v, s.options).size() + " of " + s.options.size() + " lines";
			case RULES -> {
				int n = Values.rules(v).size();
				yield n == 0 ? "No rules" : n + (n == 1 ? " rule" : " rules");
			}
			default -> v;
		};
	}

	void toggleEditor(Setting<?> s) {
		if (!OPEN_EDITORS.remove(s.key)) OPEN_EDITORS.add(s.key);
	}

	String fit(String v, int w) {
		String shown = v;
		while (shown.length() > 1 && font.width(shown) > w) shown = shown.substring(0, shown.length() - 2) + "…";
		return shown;
	}

	/** A grey checkerboard behind a colour swatch, so transparency shows. */
	static void checker(GuiGraphicsExtractor g, Rect r) {
		for (int yy = r.y(); yy < r.bottom(); yy += 3) {
			for (int xx = r.x(); xx < r.right(); xx += 3) {
				boolean dark = ((xx - r.x()) / 3 + (yy - r.y()) / 3) % 2 == 0;
				g.fill(xx, yy, Math.min(xx + 3, r.right()), Math.min(yy + 3, r.bottom()), dark ? 0xFF666666 : 0xFF999999);
			}
		}
	}

	// ---- inline editors ----------------------------------------------------------------------------------

	static final int[] PALETTE = {0xFF000000, 0xFF0000AA, 0xFF00AA00, 0xFF00AAAA, 0xFFAA0000, 0xFFAA00AA, 0xFFFFAA00, 0xFFAAAAAA,
			0xFF555555, 0xFF5555FF, 0xFF55FF55, 0xFF55FFFF, 0xFFFF5555, 0xFFFF55FF, 0xFFFFFF55, 0xFFFFFFFF};

	int editorHeight(Setting<?> s, int w) {
		return switch (s.type) {
			case COLOR -> 14 + 4 * 12 + (s.rainbow != null ? 13 : 0);
			case SOUND -> 3 * 13;
			case ISLANDS -> islandChips(w).isEmpty() ? 0 : islandChips(w).get(islandChips(w).size() - 1).rect().bottom() + 2;
			case LINES -> s.options.size() * 12;
			case RULES -> Values.rules(String.valueOf(ofa.settings.get(s))).size() * 12 + 14;
			default -> 0;
		};
	}

	record Chip(io.github.donttemptfate.fatesofa.state.Island island, Rect rect) {
	}

	/** Island chips laid out in rows for width {@code w}, relative to (0, 0); the first is "Everywhere" (island null). */
	List<Chip> islandChips(int w) {
		List<Chip> out = new ArrayList<>();
		int x = 0, y = 0;
		List<io.github.donttemptfate.fatesofa.state.Island> all = new ArrayList<>();
		all.add(null);
		for (var i : io.github.donttemptfate.fatesofa.state.Island.values()) if (i != io.github.donttemptfate.fatesofa.state.Island.OTHER) all.add(i);
		for (var i : all) {
			int cw = font.width(i == null ? "Everywhere" : i.display) + 10;
			if (x > 0 && x + cw > w) {
				x = 0;
				y += 14;
			}
			out.add(new Chip(i, new Rect(x, y, cw, 11)));
			x += cw + 3;
		}
		return out;
	}

	@SuppressWarnings("unchecked")
	void drawEditor(GuiGraphicsExtractor g, Setting<?> s, int x, int y, int w, int mx, int my) {
		Settings st = ofa.settings;
		switch (s.type) {
			case COLOR -> {
				Setting<Integer> c = (Setting<Integer>) s;
				int v = st.get(c);
				int px0 = x;
				for (int i = 0; i < PALETTE.length; i++) {
					Rect r = new Rect(px0 + i * 12, y, 10, 10);
					if (r.right() > x + w - 70) break;
					int pc = (PALETTE[i] & 0xFFFFFF) | (v & 0xFF000000);
					g.fill(r.x(), r.y(), r.right(), r.bottom(), PALETTE[i]);
					Ui.ring(g, r.x(), r.y(), r.w(), r.h(), 1, r.contains(mx, my) || (v | 0xFF000000) == PALETTE[i] ? Ui.WHITE : 0xFF333333);
					hits.add(r, () -> st.set(c, pc), null);
				}
				Rect hexR = new Rect(x + w - 64, y - 1, 64, 12);
				String hexId = s.key + "#hex";
				if (hexId.equals(editId)) placeEdit(hexR);
				else {
					Widgets.button(g, hexR.x(), hexR.y(), hexR.w(), hexR.h(), Values.hex(v), hexR.contains(mx, my), false);
					hits.add(hexR, () -> startEdit(hexId, Values.hex(v), 9, t -> {
						Integer parsed = Values.parseHex(t);
						if (parsed != null) st.set(c, parsed);
						else setNote("\"" + t + "\" isn't a colour. Use #RRGGBB or #AARRGGBB.");
					}), null, "hex:" + s.key);
				}
				String[] names = {"Red", "Green", "Blue", "Opacity"};
				int[] shifts = {16, 8, 0, 24};
				int sy = y + 14;
				for (int i = 0; i < 4; i++) {
					int shift = shifts[i];
					int val = (v >>> shift) & 0xFF;
					g.text(font, names[i], x, sy + 1, Ui.GRAY, true);
					String num = shift == 24 ? Math.round(val * 100 / 255f) + "%" : String.valueOf(val);
					g.text(font, num, x + w - font.width(num), sy + 1, Ui.GOLD, true);
					Rect sl = new Rect(x + 50, sy, w - 50 - 30, 10);
					Widgets.slider(g, sl.x(), sl.y(), sl.w(), val / 255.0, sl.contains(mx, my));
					hits.slider(sl, f -> st.set(c, Values.withChannel(st.get(c), shift, (int) Math.round(f * 255))));
					sy += 12;
				}
				if (c.rainbow != null) {
					Rect t = new Rect(x, sy + 1, w, 11);
					Widgets.toggle(g, x, sy + 1, st.on(c.rainbow), t.contains(mx, my));
					g.text(font, "Rainbow (cycles through every colour)", x + 26, sy + 2, Ui.GRAY, true);
					hits.add(t, () -> st.set(c.rainbow, !st.on(c.rainbow)), null, "rainbow:" + s.key);
				}
			}
			case SOUND -> {
				Setting<String> t = (Setting<String>) s;
				Values.Sound snd = Values.sound(st.get(t));
				Rect prev = new Rect(x, y, 14, 12);
				Rect next = new Rect(x + w - 60 - 18, y, 14, 12);
				Rect nameR = new Rect(prev.right() + 4, y, next.x() - prev.right() - 8, 12);
				Rect test = new Rect(x + w - 56, y, 56, 12);
				Widgets.button(g, prev.x(), prev.y(), prev.w(), prev.h(), "◀", prev.contains(mx, my), false);
				Widgets.button(g, nameR.x(), nameR.y(), nameR.w(), nameR.h(), snd.silent() ? "None" : Values.soundName(snd.id()), nameR.contains(mx, my), false);
				Widgets.button(g, next.x(), next.y(), next.w(), next.h(), "▶", next.contains(mx, my), false);
				Widgets.button(g, test.x(), test.y(), test.w(), test.h(), "▶ Test", test.contains(mx, my), false);
				Runnable fwd = () -> {
					st.set(t, Values.nextSound(st.get(t), 1));
					io.github.donttemptfate.fatesofa.game.Alerts.play(Values.sound(st.get(t)));
				};
				Runnable back = () -> {
					st.set(t, Values.nextSound(st.get(t), -1));
					io.github.donttemptfate.fatesofa.game.Alerts.play(Values.sound(st.get(t)));
				};
				hits.add(prev, back, null);
				hits.add(nameR, fwd, back, "sound-next:" + s.key);
				hits.add(next, fwd, null);
				hits.add(test, () -> io.github.donttemptfate.fatesofa.game.Alerts.play(Values.sound(st.get(t))), null);
				String[] names = {"Volume", "Pitch"};
				for (int i = 0; i < 2; i++) {
					int sy = y + 13 * (i + 1) + 1;
					boolean vol = i == 0;
					float val = vol ? snd.volume() : snd.pitch();
					float lo = vol ? 0f : 0.5f, hi = 2f;
					g.text(font, names[i], x, sy + 1, Ui.GRAY, true);
					String num = vol ? Math.round(val * 100) + "%" : Fmt.trimmed(val);
					g.text(font, num, x + w - font.width(num), sy + 1, Ui.GOLD, true);
					Rect sl = new Rect(x + 50, sy, w - 50 - 34, 10);
					Widgets.slider(g, sl.x(), sl.y(), sl.w(), (val - lo) / (hi - lo), sl.contains(mx, my));
					hits.slider(sl, f -> {
						Values.Sound cur = Values.sound(st.get(t));
						if (cur.id().isEmpty()) return;
						float nv = Math.round((lo + (float) f * (hi - lo)) * 20) / 20f;
						st.set(t, (vol ? new Values.Sound(cur.id(), nv, cur.pitch()) : new Values.Sound(cur.id(), cur.volume(), nv)).encode());
					});
				}
			}
			case ISLANDS -> {
				Setting<String> t = (Setting<String>) s;
				String v = st.get(t);
				var chosen = Values.islands(v);
				for (Chip c : islandChips(w)) {
					Rect r = new Rect(x + c.rect().x(), y + c.rect().y(), c.rect().w(), c.rect().h());
					boolean on = c.island() == null ? chosen.isEmpty() : chosen.contains(c.island());
					Widgets.chip(g, font, r.x(), r.y(), r.w(), c.island() == null ? "Everywhere" : c.island().display, on, r.contains(mx, my));
					hits.add(r, () -> st.set(t, c.island() == null ? "" : Values.toggleIsland(st.get(t), c.island())), null,
							"island:" + s.key + ":" + (c.island() == null ? "ALL" : c.island().name()));
				}
			}
			case LINES -> {
				Setting<String> t = (Setting<String>) s;
				List<String> shown = Values.lines(st.get(t), s.options);
				List<String> order = new ArrayList<>(shown);
				for (String o : s.options) if (!order.contains(o)) order.add(o);
				int ly = y;
				for (String id : order) {
					boolean on = shown.contains(id);
					Rect r = new Rect(x, ly, w - 34, 11);
					Widgets.checkbox(g, x, ly + 1, on, r.contains(mx, my));
					g.text(font, s.optionLabel(id), x + 14, ly + 2, on ? Ui.WHITE : Ui.GRAY, true);
					hits.add(r, () -> st.set(t, Values.toggleLine(st.get(t), s.options, id)), null, "line:" + s.key + ":" + id);
					if (on) {
						int idx = shown.indexOf(id);
						Rect up = new Rect(x + w - 30, ly, 14, 11);
						Rect dn = new Rect(x + w - 14, ly, 14, 11);
						if (idx > 0) {
							Widgets.button(g, up.x(), up.y(), up.w(), up.h(), "▲", up.contains(mx, my), false);
							hits.add(up, () -> st.set(t, Values.moveLine(st.get(t), s.options, id, -1)), null, "up:" + s.key + ":" + id);
						}
						if (idx < shown.size() - 1) {
							Widgets.button(g, dn.x(), dn.y(), dn.w(), dn.h(), "▼", dn.contains(mx, my), false);
							hits.add(dn, () -> st.set(t, Values.moveLine(st.get(t), s.options, id, 1)), null, "down:" + s.key + ":" + id);
						}
					}
					ly += 12;
				}
			}
			case RULES -> {
				Setting<String> t = (Setting<String>) s;
				List<String> rules = Values.rules(st.get(t));
				int ly = y;
				for (int i = 0; i < rules.size(); i++) {
					int idx = i;
					Rect r = new Rect(x, ly, w - 18, 11);
					Rect del = new Rect(x + w - 14, ly, 14, 11);
					String rid = s.key + "#" + i;
					if (rid.equals(editId)) placeEdit(r);
					else {
						boolean hv = r.contains(mx, my);
						if (hv) g.fill(r.x(), r.y(), r.right(), r.bottom(), 0x20FFFFFF);
						g.text(font, fit(rules.get(i), r.w() - 4), x + 2, ly + 2, Ui.WHITE, true);
						hits.add(r, () -> startEdit(rid, rules.get(idx), 300, nv -> st.set(t, Values.setRule(st.get(t), idx, nv))), null);
					}
					Widgets.button(g, del.x(), del.y(), del.w(), del.h(), "✕", del.contains(mx, my), false);
					hits.add(del, () -> st.set(t, Values.setRule(st.get(t), idx, "")), null, "rule-del:" + s.key + ":" + i);
					ly += 12;
				}
				Rect add = new Rect(x, ly + 1, Math.min(w, 120), 12);
				String aid = s.key + "#new";
				if (aid.equals(editId)) placeEdit(new Rect(x, ly + 1, w, 12));
				else {
					Widgets.button(g, add.x(), add.y(), add.w(), add.h(), "+ Add a rule", add.contains(mx, my), false);
					hits.add(add, () -> startEdit(aid, "", 300, nv -> st.set(t, Values.setRule(st.get(t), -1, nv))), null, "rule-add:" + s.key);
				}
			}
			default -> {
			}
		}
	}

	String optionName(Setting<String> e, String v) {
		if (e.optionLabels.containsKey(v)) return e.optionLabels.get(v);
		return prettyOption(v);
	}

	static String prettyOption(String v) {
		Theme t = Theme.byId(v);
		if (t.id().equals(v)) return t.name();
		String s = v.replace('_', ' ').toLowerCase(Locale.ROOT);
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	void cycle(Setting<String> e, int dir) {
		List<String> opts = e.options;
		int i = opts.indexOf(ofa.settings.get(e));
		ofa.settings.set(e, opts.get(Math.floorMod(i + dir, opts.size())));
	}

	// ---- text editing and key capture --------------------------------------------------------------------

	/** Which row holds the text box (setting key, or key#index for a rule), and what to do with the result. */
	private String editId;
	private Consumer<String> editCommit;
	/** A KEY setting or an OFA key binding waiting for a key press. */
	private Setting<?> capturing;
	private net.minecraft.client.KeyMapping capturingMapping;

	void startEdit(String id, String initial, int maxLen, Consumer<String> commit) {
		stopEditing();
		editId = id;
		editCommit = commit;
		textEdit.setMaxLength(Math.max(1, maxLen));
		textEdit.setValue(initial == null ? "" : initial);
		textEdit.setVisible(true);
		setFocused(textEdit);
		textEdit.setFocused(true);
	}

	void placeEdit(Rect r) {
		textEdit.setX(r.x());
		textEdit.setY(r.y());
		textEdit.setWidth(r.w());
	}

	void stopEditing() {
		if (editId != null) {
			Consumer<String> c = editCommit;
			String v = textEdit.getValue();
			editId = null;
			editCommit = null;
			if (c != null) c.accept(v);
		}
		textEdit.setVisible(false);
		textEdit.setY(-100);
	}

	void cancelEditing() {
		editId = null;
		editCommit = null;
		textEdit.setVisible(false);
		textEdit.setY(-100);
	}

	/** For the in-game test: the centre of a named control drawn last frame (GUI pixels), or null. */
	public double[] point(String name) {
		Rect r = hits.named.get(name);
		return r == null ? null : new double[] {r.x() + r.w() / 2.0, r.y() + r.h() / 2.0};
	}

	/** For the in-game test: types into the search box. */
	public void searchFor(String text) {
		search.setValue(text);
		scroll = 0;
	}

	/** For the in-game test: closes every group and editor. */
	public static void resetView() {
		OPEN_GROUPS.clear();
		OPEN_ADVANCED.clear();
		OPEN_EDITORS.clear();
		changedOnly = false;
	}

	public boolean editing() {
		return editId != null;
	}

	// ==== input ========================================================================================

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (editId != null && !textEdit.isMouseOver(e.x(), e.y())) stopEditing();
		if (capturing != null || capturingMapping != null) {
			// a click while waiting for a key: stop waiting
			capturing = null;
			capturingMapping = null;
		}
		if (super.mouseClicked(e, doubleClick)) return true;
		Widgets.Hit h = hits.at(e.x(), e.y());
		if (h == null) return false;
		if (h.slider() != null && Mouse.left(e.button())) {
			activeSlider = h.slider();
			activeSliderRect = h.rect();
			activeSlider.set((e.x() - h.rect().x()) / Math.max(1.0, h.rect().w()));
			return true;
		}
		Runnable r = Mouse.right(e.button()) ? h.right() : Mouse.left(e.button()) ? h.left() : null;
		if (r != null) {
			r.run();
			playClick();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (activeSlider != null) {
			activeSlider.set((e.x() - activeSliderRect.x()) / Math.max(1.0, activeSliderRect.w()));
			return true;
		}
		return super.mouseDragged(e, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		activeSlider = null;
		return super.mouseReleased(e);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double h, double v) {
		int max = Math.max(0, contentH - ch);
		scroll = (int) Math.max(0, Math.min(max, scroll - v * 20));
		return true;
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean keyPressed(KeyEvent e) {
		int esc = com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE;
		if (capturing != null) {
			Setting<Integer> k = (Setting<Integer>) capturing;
			ofa.settings.set(k, e.key() == esc ? -1 : e.key());
			capturing = null;
			return true;
		}
		if (capturingMapping != null) {
			io.github.donttemptfate.fatesofa.game.Keys.rebind(capturingMapping, e.key() == esc ? -1 : e.key());
			capturingMapping = null;
			return true;
		}
		if (editId != null) {
			if (e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) {
				stopEditing();
				return true;
			}
			if (e.key() == esc) {
				cancelEditing();
				return true;
			}
		}
		return super.keyPressed(e);
	}

	void playClick() {
		// quiet on purpose: settings toggles shouldn't make noise
	}

	@Override
	public void onClose() {
		stopEditing();
		try {
			ofa.settings.save();
		} catch (IOException ex) {
			Game.error("Couldn't save settings: " + ex.getMessage());
		}
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Opens the settings (from a command or key). */
	public static void open(Category c) {
		Game.openNextTick(parent -> new SettingsScreen(parent, c));
	}

	static void noop(Consumer<String> c) {
	}
}
