package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.item.Items;
import io.github.donttemptfate.fates.common.item.Rarity;
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
			if (st.isEmpty() || Items.tag(st) == null) return;
			Rarity r = Items.rarity(st);
			if (r == null) return;
			int a = (int) Math.round(f.get(f.opacity) * 2.55);
			g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, (a << 24) | r.rgb);
		});
	}
}
