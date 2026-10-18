package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.item.Items;
import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.text.Strip;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.planner.ProfileData;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Progression planner: reads your accessory bag and skills menu as you open them, then /ofa planner shows the
 * cheapest Magical Power you're missing, crafts you can make from what you already have, and your networth
 * from everything OFA has seen.
 */
public final class PlannerFeature extends Feature {
	public static PlannerFeature INSTANCE;
	static final Pattern BAG = Pattern.compile("^Accessory Bag(?: \\((?<page>\\d+)/(?<of>\\d+)\\))?$");
	static final Pattern SKILL = Pattern.compile("^(?<name>Farming|Mining|Combat|Foraging|Fishing|Enchanting|Alchemy|Carpentry|Runecrafting|Social|Taming|Hunting) (?<lvl>[IVXLC]+|\\d+)$");

	public final io.github.donttemptfate.fatesofa.config.Setting<Integer> maxPriceM = setting(io.github.donttemptfate.fatesofa.config.Setting.integer("planner.maxPrice", "Skip accessories costing more than (M coins)", 0, 0, 5000)
			.group("Magical Power").help("0 = list every price."));
	public final io.github.donttemptfate.fatesofa.config.Setting<Boolean> craftableOnly = setting(io.github.donttemptfate.fatesofa.config.Setting.bool("planner.craftableOnly", "Only accessories you can craft", false).group("Magical Power"));
	public final io.github.donttemptfate.fatesofa.config.Setting<Integer> craftsListed = setting(io.github.donttemptfate.fatesofa.config.Setting.integer("planner.crafts", "Crafts listed", 40, 10, 150).group("Crafts").advanced());
	public final io.github.donttemptfate.fatesofa.config.Setting<Integer> minGainK = setting(io.github.donttemptfate.fatesofa.config.Setting.integer("planner.minGain", "Only crafts that gain at least (k coins)", 0, 0, 100_000).group("Crafts"));
	public final io.github.donttemptfate.fatesofa.config.Setting<Integer> worthMinK = setting(io.github.donttemptfate.fatesofa.config.Setting.integer("planner.worthMin", "Hide items worth less than (k coins)", 0, 0, 100_000).group("Networth"));
	private ProfileData data;
	private int tick;

	public PlannerFeature() {
		super("planner", "Progression planner", "/ofa planner: the cheapest Magical Power you're missing, crafts you can make from what you have, "
				+ "and your networth. Open your accessory bag (every page) and skills menu once so it knows them.", Category.PLANNER, true, Risk.SAFE);
		INSTANCE = this;
	}

	public ProfileData data() {
		return data;
	}

	@Override
	public void init() {
		data = new ProfileData(Ofa.get().dir.resolve("profiles"));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> guard(() -> tick(mc)));
	}

	void tick(Minecraft mc) {
		if (++tick % 20 != 0) return;
		Ofa o = Ofa.get();
		if (!o.state.onSkyBlock()) return;
		if (o.state.profileName() != null) data.profile(o.state.profileName());
		if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> s)) {
			if (tick % 1200 == 0) data.save();
			return;
		}
		String title = Strip.clean(s.getTitle().getString());
		Matcher bag = BAG.matcher(title);
		long now = System.currentTimeMillis();
		if (bag.matches()) {
			Set<String> keys = new HashSet<>();
			for (Slot sl : s.getMenu().slots) {
				if (sl.container instanceof Inventory) continue;
				String k = Items.key(sl.getItem());
				if (k != null) keys.add(k);
			}
			boolean first = bag.group("page") == null || bag.group("page").equals("1");
			if (!keys.isEmpty()) data.addAccessories(keys, first, now);
		} else if (title.equals("Your Skills")) {
			for (Slot sl : s.getMenu().slots) {
				if (sl.container instanceof Inventory) continue;
				Matcher m = SKILL.matcher(Items.plainName(sl.getItem()));
				if (m.matches()) data.skill(m.group("name"), Fmt.level(m.group("lvl")), now);
			}
		}
	}

	/** Counts of items OFA has seen you hold: inventory, armour, stored pages, accessory bag. */
	public java.util.Map<String, Double> owned() {
		java.util.Map<String, Double> out = new java.util.HashMap<>();
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			Inventory inv = mc.player.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack st = inv.getItem(i);
				String k = Items.key(st);
				if (k != null) out.merge(k, (double) st.getCount(), Double::sum);
			}
		}
		if (StorageFeature.INSTANCE != null && StorageFeature.INSTANCE.cache() != null) {
			for (var p : StorageFeature.INSTANCE.cache().pages()) for (var it : p.items()) if (it.key() != null) out.merge(it.key(), (double) it.count(), Double::sum);
		}
		return out;
	}
}
