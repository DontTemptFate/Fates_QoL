package io.github.donttemptfate.fatesofa.hud;

import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Ui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Move and resize every OFA HUD element: drag to move (it re-pins to the nearest screen corner or edge),
 * mouse wheel to scale, right-click to hide/show, middle-click to reset. Shows example content for
 * elements that have nothing to show right now.
 */
public final class HudEditorScreen extends Screen {
	private final Screen parent;
	private List<Hud.Placed> placed = List.of();
	private Hud.Placed dragging;
	private double grabX, grabY;
	private int dragX, dragY;
	private boolean showAll;

	public HudEditorScreen(Screen parent) {
		super(Component.literal("Fates OFA — HUD editor"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Hud.editing(true);
	}

	/** No blur or dimming: the editor shows the game as it is. */
	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractRenderState(g, mouseX, mouseY, delta);
		if (dragging == null) placed = Hud.layout(this.width, this.height, true, showAll);
		// guides: thirds, so the anchor zones are visible
		int c = 0x22FFFFFF;
		g.fill(this.width / 3, 0, this.width / 3 + 1, this.height, c);
		g.fill(this.width * 2 / 3, 0, this.width * 2 / 3 + 1, this.height, c);
		g.fill(0, this.height / 3, this.width, this.height / 3 + 1, c);
		g.fill(0, this.height * 2 / 3, this.width, this.height * 2 / 3 + 1, c);
		for (Rect r : Hud.reserved(this.width, this.height)) g.fill(r.x(), r.y(), r.right(), r.bottom(), 0x30FF5555);
		for (Hud.Placed p : placed) {
			Rect r = p.rect();
			int x = r.x(), y = r.y();
			if (p == dragging) {
				x = dragX;
				y = dragY;
			}
			boolean hidden = Hud.hidden(p.element()) || !p.element().owner.active();
			boolean hover = new Rect(x, y, r.w(), r.h()).contains(mouseX, mouseY);
			try {
				p.card().draw(g, x, y, p.scale());
			} catch (RuntimeException ignored) {
				// one broken sample never breaks the editor
			}
			if (hidden) g.fill(x, y, x + r.w(), y + r.h(), 0xA0000000);
			Ui.ring(g, x - 1, y - 1, r.w() + 2, r.h() + 2, 1, hover || p == dragging ? Ui.accent() : 0x80FFFFFF);
			String label = p.element().name + (!p.element().owner.active() ? " (feature off)" : Hud.hidden(p.element()) ? " (hidden)" : "") + (p.scale() != 1f ? " ×" + Math.round(p.scale() * 100) / 100f : "");
			g.text(this.font, label, x, Math.max(0, y - 10), hidden ? 0xFF888888 : 0xFFFFFFFF, true);
		}
		Card help = new Card(0.94f, 0, 300).add(Card.Line.of(Card.seg("HUD EDITOR", Ui.Role.LABEL, Ui.accent())))
				.add(new Card.Text(Ui.t("Drag to move · wheel to resize · right-click to hide · middle-click to reset · A: show switched-off features · Esc to save",
						Ui.Role.SMALL, Ui.GRAY), Ui.Role.SMALL, 0, 3));
		help.draw(g, (this.width - help.width()) / 2, this.height / 2 - help.height() / 2, 1f);
	}

	Hud.Placed at(double x, double y) {
		for (int i = placed.size() - 1; i >= 0; i--) if (placed.get(i).rect().contains(x, y)) return placed.get(i);
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		Hud.Placed p = at(e.x(), e.y());
		if (p == null) return super.mouseClicked(e, doubleClick);
		int b = e.button();
		if (io.github.donttemptfate.fatesofa.ui.Mouse.left(b)) {
			dragging = p;
			grabX = e.x() - p.rect().x();
			grabY = e.y() - p.rect().y();
			dragX = p.rect().x();
			dragY = p.rect().y();
		} else if (io.github.donttemptfate.fatesofa.ui.Mouse.right(b)) {
			Hud.setHidden(p.element(), !Hud.hidden(p.element()));
		} else if (io.github.donttemptfate.fatesofa.ui.Mouse.middle(b)) {
			Hud.setPos(p.element(), null);
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (dragging == null) return super.mouseDragged(e, dx, dy);
		Rect r = dragging.rect();
		dragX = (int) Math.round(Math.max(0, Math.min(this.width - r.w(), e.x() - grabX)));
		dragY = (int) Math.round(Math.max(0, Math.min(this.height - r.h(), e.y() - grabY)));
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		if (dragging == null) return super.mouseReleased(e);
		Rect r = dragging.rect();
		Anchor a = Anchor.nearest(dragX + r.w() / 2.0, dragY + r.h() / 2.0, this.width, this.height);
		HudPos old = Hud.pos(dragging.element());
		Hud.setPos(dragging.element(), HudPos.fromScreen(a, dragX, dragY, r.w(), r.h(), this.width, this.height, old.scale()));
		dragging = null;
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double h, double v) {
		Hud.Placed p = at(x, y);
		if (p == null) return false;
		HudPos old = Hud.pos(p.element());
		float s = Math.max(0.5f, Math.min(3f, Math.round((old.scale() + (v > 0 ? 0.1f : -0.1f)) * 10) / 10f));
		Hud.setPos(p.element(), old.withScale(s));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_A) {
			showAll = !showAll;
			return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public void onClose() {
		Hud.editing(false);
		io.github.donttemptfate.fatesofa.Ofa o = io.github.donttemptfate.fatesofa.Ofa.get();
		if (o != null) {
			try {
				o.settings.save();
			} catch (java.io.IOException ignored) {
				// saved again later
			}
		}
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public void removed() {
		Hud.editing(false);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
