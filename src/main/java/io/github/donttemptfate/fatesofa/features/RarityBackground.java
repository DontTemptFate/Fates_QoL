package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fatescommon.skyblock.Items;
import io.github.donttemptfate.fatescommon.skyblock.Rarity;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** A soft rarity-coloured square behind SkyBlock items in inventory slots. */
public final class RarityBackground extends Feature {
	public static RarityBackground INSTANCE;
	final Setting<Integer> opacity = setting(Setting.integer("rarity.opacity", "Strength (%)", 35, 5, 100));
	final Setting<String> style = setting(Setting.choice("rarity.style", "Style", "square", java.util.List.of("square", "outline", "corner", "bottom")));
	final Setting<String> from = setting(Setting.choice("rarity.from", "From rarity", "COMMON", java.util.Arrays.stream(Rarity.values()).map(Enum::name).toList())
			.help("Only colour items of this rarity or better."));
	final Setting<Boolean> inventory = setting(Setting.bool("rarity.inventory", "Also in your own inventory", true).advanced());

	public RarityBackground() {
		super("rarity_bg", "Rarity backgrounds", "Colours the slot behind each SkyBlock item by its rarity.", Category.ITEMS, true, Risk.SAFE);
		INSTANCE = this;
	}

	/** Called from the slot-drawing mixin, before the item is drawn (so the colour sits behind it). */
	public static void draw(GuiGraphicsExtractor g, Slot slot) {
		RarityBackground f = INSTANCE;
		if (f == null || !f.active()) return;
		f.guard(() -> {
			ItemStack st = slot.getItem();
			if (st.isEmpty()) return;
			var facts = io.github.donttemptfate.fatescommon.skyblock.ItemFacts.of(st);
			if (!facts.skyblock()) return;
			Rarity r = facts.rarity();
			if (r == null || r.ordinal() < Rarity.valueOf(f.get(f.from)).ordinal()) return;
			if (!f.on(f.inventory) && slot.container instanceof net.minecraft.world.entity.player.Inventory) return;
			int a = (int) Math.round(f.get(f.opacity) * 2.55);
			int c = (a << 24) | r.rgb;
			int x = slot.x, y = slot.y;
			switch (f.get(f.style)) {
				case "outline" -> {
					g.fill(x, y, x + 16, y + 1, c);
					g.fill(x, y + 15, x + 16, y + 16, c);
					g.fill(x, y + 1, x + 1, y + 15, c);
					g.fill(x + 15, y + 1, x + 16, y + 15, c);
				}
				case "corner" -> {
					for (int i = 0; i < 6; i++) g.fill(x, y + i, x + 6 - i, y + i + 1, c);
				}
				case "bottom" -> g.fill(x, y + 14, x + 16, y + 16, c);
				default -> g.fill(x, y, x + 16, y + 16, c);
			}
		});
	}
}
