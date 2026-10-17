package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.item.Items;
import io.github.donttemptfate.fates.common.text.Strip;
import io.github.donttemptfate.fates.common.ui.Ui;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.dungeon.TerminalHelper;
import io.github.donttemptfate.fatesofa.dungeon.ThreeWeirdos;
import io.github.donttemptfate.fatesofa.game.ChatBus;
import io.github.donttemptfate.fatesofa.game.Game;
import io.github.donttemptfate.fatesofa.mixin.AbstractContainerScreenAccessor;
import io.github.donttemptfate.fatesofa.state.Island;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Dungeon helpers (HELPER: tolerated by the community, not covered by Hypixel's written rules, so off by
 * default): F7 terminal highlights and the Three Weirdos answer. Highlights and chat notes only; the
 * player does every click.
 */
public final class DungeonHelpers extends Feature {
	final Setting<Boolean> terminals = setting(Setting.bool("helpers.terminals", "F7 terminal highlights", true)
			.help("Outlines the slots a terminal asks for. You click them yourself."));
	final Setting<Boolean> weirdos = setting(Setting.bool("helpers.weirdos", "Three Weirdos answer", true)
			.help("Says in chat whose chest has the reward."));

	public DungeonHelpers() {
		super("dungeon_helpers", "Dungeon puzzle & terminal hints", "Highlights for F7 terminals and the Three Weirdos answer. "
				+ "Hints only: you do every click.", Category.DUNGEONS, false, Risk.HELPER);
	}

	@Override
	public void init() {
		Ofa o = Ofa.get();
		ChatBus.onChat(m -> guard(() -> {
			if (!on(weirdos) || !o.state.in(Island.DUNGEON)) return;
			String who = ThreeWeirdos.answer(m.plain());
			if (who != null) Game.ok("Three Weirdos: the reward is in " + who + "'s chest.");
		}));
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
			ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> guard(() -> {
				if (on(terminals) && o.state.in(Island.DUNGEON)) drawTerminal(acs, g);
			}));
		});
	}

	void drawTerminal(AbstractContainerScreen<?> s, GuiGraphicsExtractor g) {
		List<TerminalHelper.SlotInfo> slots = new ArrayList<>();
		List<Slot> menuSlots = new ArrayList<>();
		for (Slot sl : s.getMenu().slots) {
			if (sl.container instanceof Inventory) continue;
			ItemStack st = sl.getItem();
			menuSlots.add(sl);
			if (st.isEmpty()) continue;
			slots.add(new TerminalHelper.SlotInfo(sl.index, Items.plainName(st), BuiltInRegistries.ITEM.getKey(st.getItem()).toString(),
					st.getCount(), st.hasFoil()));
		}
		List<Integer> hint = TerminalHelper.hint(Strip.clean(s.getTitle().getString()), slots);
		if (hint.isEmpty()) return;
		AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) s;
		for (int i = 0; i < hint.size(); i++) {
			Slot sl = s.getMenu().getSlot(hint.get(i));
			int x = a.fatesofa$leftPos() + sl.x, y = a.fatesofa$topPos() + sl.y;
			int col = i == 0 ? 0xFF55FF55 : i == 1 ? 0xFFFFFF55 : 0xFFFFAA00;
			Ui.ring(g, x - 1, y - 1, 18, 18, 2, col);
		}
	}
}
