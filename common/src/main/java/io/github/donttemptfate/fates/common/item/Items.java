package io.github.donttemptfate.fates.common.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.donttemptfate.fates.common.text.Strip;
import io.github.donttemptfate.fates.common.text.Texts;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/**
 * Reading SkyBlock items: the item's own data ({@code minecraft:custom_data}, what Hypixel used to call
 * ExtraAttributes), its key, uuid, name and lore. Never relies on the display name for identity.
 */
public final class Items {
	private Items() {}

	/** The SkyBlock data compound, or null for items without a SkyBlock id. */
	public static CompoundTag tag(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return null;
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return null;
		CompoundTag tag = data.copyTag();
		return tag.getStringOr("id", "").isEmpty() ? null : tag;
	}

	/** The SkyBlock data as JSON (for the plain-Java parts), or null. */
	public static JsonObject data(ItemStack stack) {
		CompoundTag tag = tag(stack);
		if (tag == null) return null;
		try {
			JsonElement e = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag);
			return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** NEU-style key ("HYPERION", "ENDER_DRAGON;4"), or null. */
	public static String key(ItemStack stack) {
		return ItemKey.of(data(stack));
	}

	/** The SkyBlock id ("HYPERION"), or null. */
	public static String id(ItemStack stack) {
		CompoundTag tag = tag(stack);
		return tag == null ? null : tag.getStringOr("id", null);
	}

	/** The item's SkyBlock uuid without dashes, lower case; null for stackables. */
	public static String uuid(ItemStack stack) {
		CompoundTag tag = tag(stack);
		if (tag == null) return null;
		String u = tag.getStringOr("uuid", "");
		if (u.isEmpty()) return null;
		return u.replace("-", "").toLowerCase(java.util.Locale.ROOT);
	}

	public static Component name(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return Component.empty();
		Component custom = stack.get(DataComponents.CUSTOM_NAME);
		return custom != null ? custom : stack.getHoverName();
	}

	public static String plainName(ItemStack stack) {
		return Strip.clean(name(stack).getString());
	}

	/** The item's own lore lines (not lines other mods add to tooltips). */
	public static List<Component> lore(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return List.of();
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines();
	}

	public static List<String> loreLegacy(ItemStack stack) {
		List<String> out = new ArrayList<>();
		for (Component c : lore(stack)) out.add(Texts.legacy(c));
		return out;
	}

	public static List<String> lorePlain(ItemStack stack) {
		List<String> out = new ArrayList<>();
		for (Component c : lore(stack)) out.add(Strip.clean(c.getString()));
		return out;
	}

	/** The rarity from the last lore line that reads as one, or null. */
	public static Rarity rarity(ItemStack stack) {
		List<Component> lore = lore(stack);
		for (int i = lore.size() - 1; i >= 0 && i >= lore.size() - 3; i--) {
			Rarity r = Rarity.fromLine(Texts.legacy(lore.get(i)));
			if (r != null) return r;
		}
		return null;
	}
}
