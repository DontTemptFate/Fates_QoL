package io.github.donttemptfate.fatesofa.ui;

import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.data.Price;
import io.github.donttemptfate.fatesofa.features.PlannerFeature;
import io.github.donttemptfate.fatesofa.hud.Rect;
import io.github.donttemptfate.fatesofa.items.Stacks;
import io.github.donttemptfate.fatesofa.planner.Accessories;
import io.github.donttemptfate.fatesofa.planner.Crafts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** /ofa planner: Magical Power to get next, crafts from what you have, networth. Click a row for the item's page. */
public final class PlannerScreen extends Screen {
	enum Tab { MP("Magical Power"), CRAFTS("Use what you have"), NETWORTH("Networth");
		final String label;

		Tab(String l) {
			label = l;
		}
	}

	private final Screen parent;
	private Tab tab = Tab.MP;
	private int scroll;
	private final Widgets.Hits hits = new Widgets.Hits();
	private int px, py, pw, ph;
	private List<Accessories.Suggestion> mp;
	private List<Crafts.Option> crafts;
	private List<Object[]> worth;
	private double worthTotal;

	public PlannerScreen(Screen parent) {
		super(Component.literal("Planner"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		pw = Math.min(this.width - 16, 380);
		ph = Math.min(this.height - 16, 270);
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
		compute();
	}

	double value(String key) {
		Price p = Ofa.get().prices.value(key, System.currentTimeMillis());
		return p == null ? Double.NaN : p.value();
	}

	void compute() {
		Ofa o = Ofa.get();
		PlannerFeature pf = PlannerFeature.INSTANCE;
		var repo = o.repo.items();
		var misc = o.repo.constant("misc");
		boolean trade = o.state.profileType().canTrade();
		mp = Accessories.missing(repo, pf == null ? java.util.Set.of() : pf.data().accessories(), Accessories.upgrades(misc), Accessories.ignored(misc),
				this::value, trade);
		Map<String, Double> owned = pf == null ? Map.of() : pf.owned();
		crafts = Crafts.fromOwned(repo, owned, this::value, 40);
		worth = new ArrayList<>();
		worthTotal = 0;
		Map<String, Double> all = new java.util.HashMap<>(owned);
		if (pf != null) for (String a : pf.data().accessories()) all.merge(a, 1.0, Double::sum);
		for (Map.Entry<String, Double> e : all.entrySet()) {
			double v = value(e.getKey());
			if (Double.isNaN(v)) continue;
			worth.add(new Object[] {e.getKey(), e.getValue(), v * e.getValue()});
			worthTotal += v * e.getValue();
		}
		worth.sort(Comparator.comparingDouble((Object[] r) -> (Double) r[2]).reversed());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractBackground(g, mouseX, mouseY, delta);
		Ui.frame(g, px, py, pw, ph, 0.94f);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		super.extractRenderState(g, mx, my, delta);
		hits.clear();
		int x = px + 10, y = py + 10, w = pw - 20;
		g.text(font, "PLANNER", x, y, Ui.accent(), true);
		int tx = x + 60;
		for (Tab t : Tab.values()) {
			int tw = Ui.width(Ui.t(t.label, Ui.Role.BODY_BOLD, 0), Ui.Role.BODY_BOLD) + 12;
			Rect r = new Rect(tx, y - 2, tw, 13);
			Widgets.button(g, r.x(), r.y(), r.w(), r.h(), t.label, r.contains(mx, my), t == tab);
			hits.add(r, () -> {
				tab = t;
				scroll = 0;
			}, null);
			tx += tw + 3;
		}
		y += 18;
		g.enableScissor(px + 4, y, px + pw - 4, py + ph - 4);
		int ry = y - scroll;
		var repo = Ofa.get().repo.items();
		switch (tab) {
			case MP -> {
				PlannerFeature pf = PlannerFeature.INSTANCE;
				int have = pf == null ? 0 : pf.data().accessories().size();
				g.text(font, have == 0 ? "Open your Accessory Bag (every page) so OFA knows what you have." : have + " accessories known · cheapest Magical Power first",
						x, ry, Ui.GRAY, true);
				ry += 13;
				for (Accessories.Suggestion s : mp) {
					if (ry > py + ph) break;
					row(g, x, ry, w, mx, my, s.key(), s.name(), "+" + s.mpGain() + " MP",
							Double.isNaN(s.price()) ? (s.craftable() ? "craft" : "—") : Fmt.shortNum(s.price()) + " · " + Fmt.shortNum(s.coinsPerMp()) + "/MP");
					ry += 18;
				}
			}
			case CRAFTS -> {
				g.text(font, "Recipes you can make from items you hold or have stored (value − what the inputs sell for).", x, ry, Ui.GRAY, true);
				ry += 13;
				for (Crafts.Option c : crafts) {
					if (ry > py + ph) break;
					row(g, x, ry, w, mx, my, c.output(), c.times() + "× " + c.name(), Fmt.signedShort(c.gain()), "each " + Fmt.shortNum(c.outputValue()));
					ry += 18;
				}
				if (crafts.isEmpty()) g.text(font, "Nothing yet: open your ender chest pages and backpacks so OFA knows what you have.", x, ry, Ui.GRAY, true);
			}
			case NETWORTH -> {
				new Card.Hero(List.of(Card.seg(Fmt.shortNum(worthTotal), Ui.Role.HERO_M, Ui.GOLD)), null, 0,
						List.of(Card.seg("SEEN BY OFA", Ui.Role.LABEL, Ui.GRAY))).draw(g, x, ry, w);
				ry += 24;
				g.text(font, "Inventory, stored pages and accessories at today's values (no upgrades counted).", x, ry, Ui.GRAY, true);
				ry += 13;
				for (Object[] r : worth) {
					if (ry > py + ph) break;
					String key = (String) r[0];
					var it = repo.get(key);
					row(g, x, ry, w, mx, my, key, Fmt.trimmed((Double) r[1]) + "× " + (it == null ? key : it.plainName()), Fmt.shortNum((Double) r[2]), null);
					ry += 18;
				}
			}
		}
		g.disableScissor();
	}

	void row(GuiGraphicsExtractor g, int x, int y, int w, int mx, int my, String key, String name, String right, String sub) {
		var it = Ofa.get().repo.items().get(key);
		Rect r = new Rect(x, y, w, 17);
		if (r.contains(mx, my)) g.fill(r.x(), r.y(), r.right(), r.bottom(), 0x20FFFFFF);
		if (it != null) g.item(Stacks.of(it), x, y);
		g.text(font, name, x + 20, y + 4, Ui.WHITE, true);
		g.text(font, right, x + w - font.width(right), y + (sub == null ? 4 : 0), Ui.GOLD, true);
		if (sub != null) g.text(font, sub, x + w - font.width(sub), y + 9, Ui.DARK, false);
		if (it != null) hits.add(r, () -> this.minecraft.gui.setScreen(new RecipeScreen(this, key, false)), null);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		Widgets.Hit h = hits.at(e.x(), e.y());
		if (h != null && h.left() != null) {
			h.left().run();
			return true;
		}
		return super.mouseClicked(e, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double h, double v) {
		scroll = Math.max(0, scroll - (int) (v * 18));
		return true;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
