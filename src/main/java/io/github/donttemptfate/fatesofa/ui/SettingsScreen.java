package io.github.donttemptfate.fatesofa.ui;

import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Theme;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.config.Presets;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.config.Settings;
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
	private Setting<String> editing;
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
		search = new EditBox(this.font, cx, py + 12, cw, 14, Component.literal("Search"));
		search.setHint(Component.literal("Search all settings…"));
		search.setValue(old);
		search.setResponder(s -> scroll = 0);
		addRenderableWidget(search);
		textEdit = new EditBox(this.font, 0, -100, 100, 14, Component.literal("Value"));
		textEdit.setVisible(false);
		addRenderableWidget(textEdit);
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
			for (Feature f : Features.all()) {
				boolean fm = f.enabled.matches(q) || f.keywords().stream().anyMatch(k -> k.toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT)));
				List<Setting<?>> subs = new ArrayList<>();
				for (Setting<?> s : f.settings()) if (fm || s.matches(q)) subs.add(s);
				if (!fm && subs.isEmpty()) continue;
				out.add(featureRow(f));
				for (Setting<?> s : subs) out.add(settingRow(s));
				found++;
			}
			for (Setting<?> s : General.globals()) if (s.matches(q)) {
				out.add(settingRow(s));
				found++;
			}
			if (found == 0) out.add(text("Nothing matches \"" + q + "\".", Ui.GRAY));
			return out;
		}
		if (page.category() != null) {
			for (Feature f : Features.all()) {
				if (f.category != page.category()) continue;
				out.add(featureRow(f));
				for (Setting<?> s : f.settings()) out.add(settingRow(s));
			}
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
		out.add(rule("SHARE YOUR SETTINGS"));
		out.add(buttons("Copy settings to clipboard", () -> {
			this.minecraft.keyboardHandler.setClipboard(ofa.settings.export());
			setNote("Copied. Paste it to a friend; they use \"Paste settings\".");
		}, "Paste settings", () -> {
			int n = ofa.settings.importLine(this.minecraft.keyboardHandler.getClipboard());
			setNote(n < 0 ? "The clipboard doesn't hold OFA settings." : "Applied " + n + " settings.");
		}));
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
				+ "Downloaded once, kept on your computer, checked for updates hourly.", Ui.GRAY));
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

	Row featureRow(Feature f) {
		return new Row() {
			List<FormattedCharSequence> desc(int w) {
				return font.split(Ui.t(f.description, Ui.Role.SMALL, Ui.GRAY), w - 30);
			}

			public int height(int w) {
				return 14 + desc(w).size() * 10 + (f.failed() ? 10 : 0) + 2;
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
				}, null);
				int ly = y + 14;
				for (FormattedCharSequence l : desc(w)) {
					g.text(font, l, x + 2, ly, Ui.WHITE, true);
					ly += 10;
				}
				if (f.failed()) g.text(font, "Switched off after errors: " + f.errors().lastError(), x + 2, ly, Ui.RED, true);
			}
		};
	}

	@SuppressWarnings("unchecked")
	Row settingRow(Setting<?> s) {
		return new Row() {
			public int height(int w) {
				return 14;
			}

			public void draw(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my) {
				Settings st = ofa.settings;
				Rect row = new Rect(x + 10, y, w - 10, 14);
				boolean hover = row.contains(mx, my);
				if (hover) g.fill(row.x() - 2, row.y(), row.right(), row.bottom(), 0x14FFFFFF);
				g.text(font, s.label, x + 12, y + 3, st.isDefault(s) ? 0xFFCCCCCC : Ui.WHITE, true);
				if (hover && !s.help.isEmpty()) g.setTooltipForNextFrame(font, font.split(Component.literal(s.help), 220), mx, my);
				int right = x + w;
				switch (s.type) {
					case BOOL -> {
						Setting<Boolean> b = (Setting<Boolean>) s;
						Rect t = new Rect(right - 22, y + 2, 20, 10);
						Widgets.toggle(g, t.x(), t.y(), st.on(b), t.contains(mx, my));
						hits.add(row, () -> st.set(b, !st.on(b)), () -> st.reset(b));
					}
					case INT, DOUBLE -> {
						double v = ((Number) st.get(s)).doubleValue();
						double frac = (v - s.min) / Math.max(1e-9, s.max - s.min);
						String shown = s.type == Setting.Type.INT ? Fmt.shortNum(v) : Fmt.trimmed(v);
						int sw = Math.min(110, w / 3);
						Rect sl = new Rect(right - sw, y + 2, sw, 10);
						g.text(font, shown, sl.x() - 6 - font.width(shown), y + 3, Ui.GOLD, true);
						Widgets.slider(g, sl.x(), sl.y(), sl.w(), frac, sl.contains(mx, my));
						Widgets.Slider set = f -> {
							double nv = s.min + f * (s.max - s.min);
							if (s.type == Setting.Type.INT) st.set((Setting<Integer>) s, (int) Math.round(nv));
							else st.set((Setting<Double>) s, Math.round(nv * 100) / 100.0);
						};
						hits.slider(sl, set);
						hits.add(row, null, () -> st.reset(s));
					}
					case ENUM -> {
						Setting<String> e = (Setting<String>) s;
						String v = st.get(e);
						String shown = prettyOption(v);
						int bw = Math.max(60, font.width(shown) + 12);
						Rect br = new Rect(right - bw, y + 1, bw, 12);
						Widgets.button(g, br.x(), br.y(), br.w(), br.h(), shown, br.contains(mx, my), false);
						hits.add(row, () -> cycle(e, 1), () -> cycle(e, -1));
					}
					case STRING -> {
						Setting<String> t = (Setting<String>) s;
						String v = st.get(t);
						int bw = Math.min(160, w / 2);
						Rect br = new Rect(right - bw, y + 1, bw, 12);
						if (editing == t) {
							textEdit.setX(br.x());
							textEdit.setY(br.y());
							textEdit.setWidth(br.w());
						} else {
							String shown = v.isEmpty() ? "(empty)" : v;
							while (shown.length() > 1 && font.width(shown) > bw - 8) shown = shown.substring(0, shown.length() - 2) + "…";
							Widgets.button(g, br.x(), br.y(), br.w(), br.h(), shown, br.contains(mx, my), false);
							hits.add(row, () -> startEditing(t), () -> st.reset(t));
						}
					}
					case COLOR -> {
						Setting<Integer> c = (Setting<Integer>) s;
						int v = st.get(c);
						Rect sw = new Rect(right - 22, y + 2, 20, 10);
						g.fill(sw.x(), sw.y(), sw.right(), sw.bottom(), v | 0xFF000000);
						Ui.ring(g, sw.x(), sw.y(), sw.w(), sw.h(), 1, Ui.WHITE);
						hits.add(row, () -> st.set(c, nextColor(v, 1)), () -> st.set(c, nextColor(v, -1)));
					}
					case KEY -> {
					}
				}
			}
		};
	}

	static final int[] PALETTE = {0xFFFF55FF, 0xFF55FFFF, 0xFF55FF55, 0xFFFFFF55, 0xFFFFAA00, 0xFFFF5555, 0xFF5555FF, 0xFFFFFFFF, 0xFFAAAAAA};

	static int nextColor(int v, int dir) {
		int i = 0;
		for (int k = 0; k < PALETTE.length; k++) if (PALETTE[k] == (v | 0xFF000000)) i = k;
		return PALETTE[Math.floorMod(i + dir, PALETTE.length)];
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

	void startEditing(Setting<String> t) {
		editing = t;
		textEdit.setValue(ofa.settings.get(t));
		textEdit.setVisible(true);
		setFocused(textEdit);
		textEdit.setFocused(true);
	}

	void stopEditing() {
		if (editing != null) {
			ofa.settings.set(editing, textEdit.getValue().trim());
			editing = null;
		}
		textEdit.setVisible(false);
		textEdit.setY(-100);
	}

	// ==== input ========================================================================================

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (editing != null && !textEdit.isMouseOver(e.x(), e.y())) stopEditing();
		if (super.mouseClicked(e, doubleClick)) return true;
		Widgets.Hit h = hits.at(e.x(), e.y());
		if (h == null) return false;
		if (h.slider() != null && e.button() == 0) {
			activeSlider = h.slider();
			activeSliderRect = h.rect();
			activeSlider.set((e.x() - h.rect().x()) / Math.max(1.0, h.rect().w()));
			return true;
		}
		Runnable r = e.button() == 1 ? h.right() : e.button() == 0 ? h.left() : null;
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
	public boolean keyPressed(KeyEvent e) {
		if (editing != null && (e.key() == 257 || e.key() == 335)) { // Enter
			stopEditing();
			return true;
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
