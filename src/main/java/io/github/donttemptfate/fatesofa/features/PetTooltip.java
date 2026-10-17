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
	private PetLevels levels;
	private String loadedFor;

	public PetTooltip() {
		super("pet_tooltip", "Pet levels", "Pet tooltips show progress to the next level and the experience left to max level.",
				Category.ITEMS, true, Risk.SAFE);
	}

	@Override
	public void init() {
		ItemTooltipCallback.EVENT.register((st, ctx, flag, lines) -> guard(() -> {
			JsonObject data = Items.data(st);
			if (data == null || !"PET".equals(ItemKey.str(data, "id"))) return;
			JsonObject pet = ItemKey.petInfo(data);
			if (pet == null) return;
			PetLevels pl = levels();
			if (pl == null) return;
			double exp = pet.has("exp") ? pet.get("exp").getAsDouble() : 0;
			PetLevels.Level l = pl.level(ItemKey.str(pet, "type"), ItemKey.str(pet, "tier"), exp);
			if (l.level() >= l.maxLevel()) {
				lines.add(Component.literal("Max level (" + Fmt.shortNum(exp) + " XP)").withStyle(ChatFormatting.GOLD));
				return;
			}
			lines.add(Component.literal("Level " + l.level() + " → " + (l.level() + 1) + ": ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal(Fmt.percent(l.progress())).withStyle(ChatFormatting.AQUA))
					.append(Component.literal(" (" + Fmt.shortNum(l.expIntoLevel()) + " / " + Fmt.shortNum(l.expForLevel()) + ")").withStyle(ChatFormatting.DARK_GRAY)));
			lines.add(Component.literal("To level " + l.maxLevel() + ": ").withStyle(ChatFormatting.GRAY)
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
