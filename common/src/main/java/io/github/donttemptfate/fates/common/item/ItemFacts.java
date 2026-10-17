package io.github.donttemptfate.fates.common.item;

import io.github.donttemptfate.fates.common.text.Strip;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What per-frame code needs to know about a stack (key, uuid, rarity, name, search text), worked out once
 * per stack object and remembered. Slot updates replace stack objects, so a changed item is read again.
 * Render thread only.
 */
public record ItemFacts(String key, String id, String uuid, Rarity rarity, String name, String searchText) {
	private static final Map<ItemStack, ItemFacts> CACHE = new WeakHashMap<>();
	public static final ItemFacts NONE = new ItemFacts(null, null, null, null, "", "");

	public static ItemFacts of(ItemStack st) {
		if (st == null || st.isEmpty()) return NONE;
		ItemFacts f = CACHE.get(st);
		if (f != null) return f;
		String key = Items.key(st);
		String name = Items.plainName(st);
		List<String> lore = Items.lorePlain(st);
		StringBuilder b = new StringBuilder(name);
		for (String l : lore) b.append(' ').append(l);
		if (key != null) b.append(' ').append(key.replace('_', ' '));
		f = new ItemFacts(key, Items.id(st), Items.uuid(st), key == null ? null : Items.rarity(st), name,
				Strip.clean(b.toString()).toLowerCase(Locale.ROOT));
		if (CACHE.size() > 4096) CACHE.clear();
		CACHE.put(st, f);
		return f;
	}

	public boolean skyblock() {
		return id != null;
	}
}
