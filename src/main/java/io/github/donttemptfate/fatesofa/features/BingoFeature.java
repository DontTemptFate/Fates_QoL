package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.item.Items;
import io.github.donttemptfate.fates.common.text.Strip;
import io.github.donttemptfate.fates.common.ui.Card;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.hud.Anchor;
import io.github.donttemptfate.fatesofa.hud.Hud;
import io.github.donttemptfate.fatesofa.hud.HudElement;
import io.github.donttemptfate.fatesofa.hud.HudPos;
import io.github.donttemptfate.fatesofa.state.ProfileType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Bingo: remembers your card when you open it and keeps the goals still to do on the HUD. */
public final class BingoFeature extends Feature {
	private final List<String> open = new ArrayList<>();
	private int done;
	private int tick;

	public BingoFeature() {
		super("bingo", "Bingo card", "Open your Bingo Card once and the goals still to do stay on your HUD.", Category.BINGO, true, Risk.SAFE);
	}

	@Override
	public void init() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> guard(() -> {
			if (++tick % 20 != 0) return;
			if (mc.gui.screen() instanceof AbstractContainerScreen<?> s && Strip.clean(s.getTitle().getString()).equals("Bingo Card")) read(s);
		}));
		Hud.register(new HudElement("bingo", "Bingo card", this, HudPos.at(Anchor.LEFT, 4, -60), 20, this::card, this::sample,
				() -> Ofa.get().state.profileType() == ProfileType.BINGO && !open.isEmpty()));
	}

	void read(AbstractContainerScreen<?> s) {
		List<String> todo = new ArrayList<>();
		int complete = 0;
		for (Slot sl : s.getMenu().slots) {
			if (sl.container instanceof Inventory) continue;
			ItemStack st = sl.getItem();
			if (st.isEmpty()) continue;
			List<String> lore = Items.lorePlain(st);
			boolean isGoal = lore.stream().anyMatch(l -> l.contains("Personal Goal") || l.contains("Community Goal"));
			if (!isGoal) continue;
			boolean finished = lore.stream().anyMatch(l -> l.equals("GOAL REACHED") || l.contains("COMPLETE"));
			if (finished) complete++;
			else todo.add(Items.plainName(st));
		}
		if (todo.isEmpty() && complete == 0) return;
		synchronized (open) {
			open.clear();
			open.addAll(todo);
		}
		done = complete;
	}

	Card card() {
		Card c = Trackers.card("BINGO", done + " DONE");
		Card.KeyValues kv = Trackers.rows();
		synchronized (open) {
			for (String g : open) kv.row(Card.seg("• " + g, Ui.Role.SMALL, Ui.WHITE));
		}
		c.add(kv);
		return c;
	}

	Card sample() {
		Card c = Trackers.card("BINGO", "11 DONE");
		Card.KeyValues kv = Trackers.rows();
		kv.row(Card.seg("• Obtain a Sea Emperor kill", Ui.Role.SMALL, Ui.WHITE));
		kv.row(Card.seg("• Reach Combat XV", Ui.Role.SMALL, Ui.WHITE));
		c.add(kv);
		return c;
	}
}
