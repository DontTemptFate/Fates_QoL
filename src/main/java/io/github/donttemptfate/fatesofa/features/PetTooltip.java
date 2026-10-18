package io.github.donttemptfate.fatesofa.features;

import com.google.gson.JsonObject;
import io.github.donttemptfate.fates.common.item.ItemKey;
import io.github.donttemptfate.fates.common.item.Items;
import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.items.PetLevels;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Pet tooltips: exact level, progress to the next level and the experience still needed for max level. */
public final class PetTooltip extends Feature {
	final io.github.donttemptfate.fatesofa.config.Setting<Boolean> progress = setting(io.github.donttemptfate.fatesofa.config.Setting
			.bool("pettip.progress", "Progress to the next level", true));
	final io.github.donttemptfate.fatesofa.config.Setting<Boolean> toMax = setting(io.github.donttemptfate.fatesofa.config.Setting
			.bool("pettip.toMax", "Experience left to max level", true));
	final io.github.donttemptfate.fatesofa.config.Setting<Boolean> value = setting(io.github.donttemptfate.fatesofa.config.Setting
			.bool("pettip.value", "Value at its level (auction prices)", true));
	final io.github.donttemptfate.fatesofa.config.Setting<Boolean> shift = setting(io.github.donttemptfate.fatesofa.config.Setting
			.bool("pettip.shift", "Only while Shift is held", false).advanced());
	private PetLevels levels;
	private String loadedFor;

	public PetTooltip() {
		super("pet_tooltip", "Pet levels", "Pet tooltips show progress to the next level and the experience left to max level.",
				Category.ITEMS, true, Risk.SAFE);
	}

	@Override
	public void init() {
		ItemTooltipCallback.EVENT.register((st, ctx, flag, lines) -> guard(() -> {
			if (!"PET".equals(io.github.donttemptfate.fates.common.item.ItemFacts.of(st).id())) return; // cheap check before reading NBT
			if (on(shift) && !net.minecraft.client.Minecraft.getInstance().hasShiftDown()) return;
			JsonObject data = Items.data(st);
			if (data == null || !"PET".equals(ItemKey.str(data, "id"))) return;
			JsonObject pet = ItemKey.petInfo(data);
			if (pet == null) return;
			PetLevels pl = levels();
			if (pl == null) return;
			double exp = pet.has("exp") ? pet.get("exp").getAsDouble() : 0;
			PetLevels.Level l = pl.level(ItemKey.str(pet, "type"), ItemKey.str(pet, "tier"), exp);
			if (on(value)) {
				var pv = Ofa.get().prices.petValue(io.github.donttemptfate.fates.common.item.ItemFacts.of(st).key(), l.level(), System.currentTimeMillis());
				if (pv != null) lines.add(Component.literal("Worth at level " + l.level() + ": ").withStyle(ChatFormatting.GRAY)
						.append(Component.literal(Fmt.shortNum(pv.value())).withStyle(ChatFormatting.GOLD)));
			}
			if (l.level() >= l.maxLevel()) {
				if (on(progress)) lines.add(Component.literal("Max level (" + Fmt.shortNum(exp) + " XP)").withStyle(ChatFormatting.GOLD));
				return;
			}
			if (on(progress)) lines.add(Component.literal("Level " + l.level() + " → " + (l.level() + 1) + ": ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal(Fmt.percent(l.progress())).withStyle(ChatFormatting.AQUA))
					.append(Component.literal(" (" + Fmt.shortNum(l.expIntoLevel()) + " / " + Fmt.shortNum(l.expForLevel()) + ")").withStyle(ChatFormatting.DARK_GRAY)));
			if (on(toMax)) lines.add(Component.literal("To level " + l.maxLevel() + ": ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal(Fmt.shortNum(l.expToMax()) + " XP").withStyle(ChatFormatting.GOLD)));
		}));
	}

	PetLevels levels() {
		Ofa o = Ofa.get();
		String commit = o.repo.items().commit();
		if (levels == null || commit != null && !commit.equals(loadedFor)) {
			JsonObject c = o.repo.constant("pets");
			if (c == null) return null;
			levels = new PetLevels(c);
			loadedFor = commit;
		}
		return levels;
	}
}
