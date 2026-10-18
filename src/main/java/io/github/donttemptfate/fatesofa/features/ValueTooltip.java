package io.github.donttemptfate.fatesofa.features;

import io.github.donttemptfate.fates.common.item.Items;
import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fatesofa.Ofa;
import io.github.donttemptfate.fatesofa.config.Setting;
import io.github.donttemptfate.fatesofa.core.Category;
import io.github.donttemptfate.fatesofa.core.Feature;
import io.github.donttemptfate.fatesofa.data.Price;
import io.github.donttemptfate.fatesofa.data.PriceTables;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Value lines under SkyBlock item tooltips: market value, stack value, NPC price, bazaar buy/sell. */
public final class ValueTooltip extends Feature {
	final Setting<String> lines = setting(Setting.lines("valuetip.lines", "Lines and order", io.github.donttemptfate.fatesofa.core.Display.lines(
			"value", "Market value", "bazaar", "Bazaar buy / sell", "stack", "Whole stack's value", "auction", "Auction details (lowest BIN, median, sales/day)",
			"npc", "NPC sell price", "key", "The item's id"),
			java.util.List.of("value", "bazaar", "stack", "npc")).help("Tick the lines to show under tooltips; arrows change the order."));
	final Setting<Boolean> shift = setting(Setting.bool("valuetip.shift", "Only while Shift is held", false));
	final Setting<Boolean> shortNumbers = setting(Setting.bool("valuetip.short", "Short numbers (1.2M)", true).advanced());
	final Setting<Integer> minValue = setting(Setting.integer("valuetip.min", "Hide for items worth less than (coins)", 0, 0, 100_000).advanced()
			.help("Keeps cheap items' tooltips short. 0 = always show."));
	final Setting<Boolean> blank = setting(Setting.bool("valuetip.blank", "Blank line before the prices", false).advanced());

	public ValueTooltip() {
		super("value_tooltip", "Price lines in tooltips", "Market value, NPC price, bazaar and auction prices under SkyBlock item tooltips.",
				Category.ITEMS, true, Risk.SAFE);
	}

	@Override
	public java.util.Set<String> keywords() {
		return java.util.Set.of("price", "lbin", "worth", "value", "npc");
	}

	String n(double v) {
		return on(shortNumbers) ? Fmt.shortNum(v) : Fmt.full(Math.round(v));
	}

	@Override
	public void init() {
		ItemTooltipCallback.EVENT.register((st, ctx, flag, out) -> guard(() -> {
			if (on(shift) && !net.minecraft.client.Minecraft.getInstance().hasShiftDown()) return;
			Ofa o = Ofa.get();
			String key = io.github.donttemptfate.fates.common.item.ItemFacts.of(st).key(); // cached per stack: no NBT copy per frame
			if (key == null) return;
			long now = System.currentTimeMillis();
			PriceTables.Bz bz = o.prices.bazaarQuote(key, now);
			Price p = o.prices.value(key, now);
			if (get(minValue) > 0 && (p == null || p.value() < get(minValue))) return;
			java.util.List<Component> add = new java.util.ArrayList<>();
			int count = st.getCount();
			for (String id : io.github.donttemptfate.fatesofa.config.Values.lines(get(lines), lines.options)) {
				switch (id) {
					case "value" -> {
						if (bz == null && p != null && p.kind() != Price.Kind.NPC) {
							MutableComponent l = line("Value ", n(p.value()), null, null);
							l.append(Component.literal(" (" + p.kind().label + ")").withStyle(ChatFormatting.DARK_GRAY));
							add.add(l);
						}
					}
					case "bazaar" -> {
						if (bz != null) add.add(line("Bazaar buy ", n(bz.instantBuy()), " · sell ", n(bz.instantSell())));
					}
					case "stack" -> {
						if (count > 1 && bz != null) add.add(line("Stack sells for ", n(bz.instantSell() * count), null, null));
						else if (count > 1 && p != null && p.kind() != Price.Kind.NPC) add.add(line("Stack ", n(p.value() * count), null, null));
					}
					case "auction" -> {
						PriceTables.Ah a = o.prices.ahQuote(key);
						if (a != null) {
							add.add(line("Lowest BIN ", n(a.lbin()), " · median ", n(a.median())));
							if (a.salesPerDay() > 0) add.add(Component.literal("Sells ~" + Fmt.trimmed(a.salesPerDay()) + " a day").withStyle(ChatFormatting.DARK_GRAY));
						}
					}
					case "npc" -> {
						Double np = o.prices.npcPrice(key);
						if (np != null && np > 0) add.add(line("NPC sell ", n(np) + (count > 1 ? " (" + n(np * count) + ")" : ""), null, null));
					}
					case "key" -> add.add(Component.literal(key).withStyle(ChatFormatting.DARK_GRAY));
					default -> {
					}
				}
			}
			if (add.isEmpty()) return;
			if (on(blank)) out.add(Component.empty());
			out.addAll(add);
		}));
	}

	static MutableComponent line(String a, String b, String c, String d) {
		MutableComponent l = Component.literal(a).withStyle(ChatFormatting.GRAY).append(Component.literal(b).withStyle(ChatFormatting.GOLD));
		if (c != null) l.append(Component.literal(c).withStyle(ChatFormatting.GRAY)).append(Component.literal(d).withStyle(ChatFormatting.GOLD));
		return l;
	}
}
