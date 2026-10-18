package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fatescommon.text.LegacyText;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.items.Stacks;
import io.github.donttemptfate.fatesofa.storage.StorageCache;
import io.github.donttemptfate.fatesofa.storage.StoredItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Remembers your ender chest pages and backpacks as you open them, so /ofa storage shows them all and
 * /ofa find searches them from anywhere.
 */
public final class StorageFeature extends Feature {
	public static StorageFeature INSTANCE;
	static final java.util.Set<String> NAV = java.util.Set.of("Next Page", "Previous Page", "Go Back", "Close", "Back", "Ender Chest");
	private StorageCache cache;
	private int tick;

	public StorageFeature() {
		super("storage", "Storage overview & search", "Remembers your ender chest pages and backpacks when you open them: browse them all "
				+ "with /ofa storage and search them with /ofa find, from anywhere.", Category.STORAGE, true, Risk.SAFE);
		INSTANCE = this;
	}

	@Override
	public java.util.Set<String> keywords() {
		return java.util.Set.of("ender chest", "backpack", "find", "search");
	}

	public StorageCache cache() {
		return cache;
	}

	@Override
	public void init() {
		cache = new StorageCache(Ofa.get().dir.resolve("storage"));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> guard(() -> tick(mc)));
	}

	void tick(Minecraft mc) {
		if (++tick % 10 != 0) return;
		Ofa o = Ofa.get();
		if (!o.state.onSkyBlock()) return;
		String profile = o.state.profileName();
		if (profile != null) cache.profile(profile);
		Screen s = mc.gui.screen();
		if (s instanceof AbstractContainerScreen<?> acs) {
			String title = LegacyText.clean(acs.getTitle().getString());
			String id = StorageCache.pageId(title);
			if (id != null) snapshot(acs, id, title);
		}
		if (tick % 600 == 0) cache.save();
	}

	private Object openedScreen;
	private long openedAt;

	void snapshot(AbstractContainerScreen<?> s, String id, String title) {
		// give Hypixel a moment to fill a window that just opened
		if (s != openedScreen) {
			openedScreen = s;
			openedAt = System.currentTimeMillis();
			return;
		}
		if (System.currentTimeMillis() - openedAt < 750) return;
		List<StoredItem> items = new ArrayList<>();
		int i = 0;
		for (Slot sl : s.getMenu().slots) {
			if (sl.container instanceof Inventory) continue;
			int index = i++;
			ItemStack st = sl.getItem();
			if (st.isEmpty()) continue;
			String name = LegacyText.clean(st.getHoverName().getString());
			// Hypixel's page frame: blank glass panes and navigation buttons aren't storage contents
			if (name.isBlank() || NAV.contains(name)) continue;
			items.add(Stacks.remember(index, st));
		}
		cache.put(id, title, items, System.currentTimeMillis());
	}

	public void saveNow() {
		if (cache != null) cache.save();
	}
}
