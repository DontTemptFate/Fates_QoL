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
	final Setting<Boolean> stack = setting(Setting.bool("valuetip.stack", "Show the whole stack's value", true));
	final Setting<Boolean> npc = setting(Setting.bool("valuetip.npc", "Show the NPC sell price", true));
	final Setting<Boolean> bazaar = setting(Setting.bool("valuetip.bazaar", "Show bazaar buy / sell", true));
	final Setting<Boolean> debugKey = setting(Setting.bool("valuetip.key", "Show the item's id", false)
			.help("Adds the SkyBlock id under the tooltip (handy for reporting problems)."));

	public ValueTooltip() {
		super("value_tooltip", "Price lines in tooltips", "Market value, NPC price and bazaar prices under SkyBlock item tooltips.",
				Category.ITEMS, true, Risk.SAFE);
	}

	@Override
	public java.util.Set<String> keywords() {
		return java.util.Set.of("price", "lbin", "worth", "value", "npc");
	}

	@Override
	public void init() {
		ItemTooltipCallback.EVENT.register((st, ctx, flag, lines) -> guard(() -> {
			Ofa o = Ofa.get();
			String key = io.github.donttemptfate.fates.common.item.ItemFacts.of(st).key(); // cached per stack: no NBT copy per frame
			if (key == null) return;
			long now = System.currentTimeMillis();
			PriceTables.Bz bz = on(bazaar) ? o.prices.bazaarQuote(key, now) : null;
			if (bz != null) {
				lines.add(line("Bazaar buy ", Fmt.shortNum(bz.instantBuy()), " · sell ", Fmt.shortNum(bz.instantSell())));
				if (on(stack) && st.getCount() > 1) lines.add(line("Stack sells for ", Fmt.shortNum(bz.instantSell() * st.getCount()), null, null));
			} else {
				Price p = o.prices.value(key, now);
				if (p != null && p.kind() != Price.Kind.NPC) {
					MutableComponent l = line("Value ", Fmt.shortNum(p.value()), null, null);
					l.append(Component.literal(" (" + p.kind().label + ")").withStyle(ChatFormatting.DARK_GRAY));
					lines.add(l);
					if (on(stack) && st.getCount() > 1) lines.add(line("Stack ", Fmt.shortNum(p.value() * st.getCount()), null, null));
				}
			}
			Double n = on(npc) ? o.prices.npcPrice(key) : null;
			if (n != null && n > 0) lines.add(line("NPC sell ", Fmt.shortNum(n) + (st.getCount() > 1 ? " (" + Fmt.shortNum(n * st.getCount()) + ")" : ""), null, null));
			if (on(debugKey)) lines.add(Component.literal(key).withStyle(ChatFormatting.DARK_GRAY));
		}));
	}

	static MutableComponent line(String a, String b, String c, String d) {
		MutableComponent l = Component.literal(a).withStyle(ChatFormatting.GRAY).append(Component.literal(b).withStyle(ChatFormatting.GOLD));
		if (c != null) l.append(Component.literal(c).withStyle(ChatFormatting.GRAY)).append(Component.literal(d).withStyle(ChatFormatting.GOLD));
		return l;
	}
}
