package io.github.donttemptfate.fatesofa.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Floor 7 terminal hints: which slots to look at, from the terminal window's title and items. Highlight
 * only — the player clicks every slot themselves.
 */
public final class TerminalHelper {
	private TerminalHelper() {}

	/** A slot as the helper sees it: index, plain name, Minecraft item id, stack count, enchant glint. */
	public record SlotInfo(int index, String name, String itemId, int count, boolean glint) {
	}

	static final Pattern STARTS = Pattern.compile("^What starts with: '(?<l>\\w)'\\?$");
	static final Pattern SELECT = Pattern.compile("^Select all the (?<colour>[\\w ]+?) items!$");
	static final Pattern ORDER = Pattern.compile("^Click in order!$");
	static final Pattern PANES = Pattern.compile("^Correct all the panes!$");

	/** The slots to highlight, in order (for "click in order", the next ones first). Empty when not a known terminal. */
	public static List<Integer> hint(String title, List<SlotInfo> slots) {
		List<Integer> out = new ArrayList<>();
		String t = title.trim();
		Matcher m;
		if (ORDER.matcher(t).matches()) {
			// red panes count up; the next is the lowest stack size still red
			List<SlotInfo> red = new ArrayList<>();
			for (SlotInfo s : slots) if (s.itemId().contains("red_stained_glass_pane")) red.add(s);
			red.sort((a, b) -> a.count() - b.count());
			for (int i = 0; i < Math.min(3, red.size()); i++) out.add(red.get(i).index());
		} else if ((m = STARTS.matcher(t)).matches()) {
			String l = m.group("l").toLowerCase(Locale.ROOT);
			for (SlotInfo s : slots) if (!s.glint() && s.name().toLowerCase(Locale.ROOT).startsWith(l) && !s.itemId().contains("glass_pane")) out.add(s.index());
		} else if ((m = SELECT.matcher(t)).matches()) {
			String colour = m.group("colour").toLowerCase(Locale.ROOT).replace(' ', '_');
			for (SlotInfo s : slots) {
				if (s.glint()) continue;
				String id = s.itemId().toLowerCase(Locale.ROOT);
				boolean match = id.contains(colour + "_") || (colour.equals("silver") && id.contains("light_gray_"))
						|| s.name().toLowerCase(Locale.ROOT).startsWith(m.group("colour").toLowerCase(Locale.ROOT));
				if (match && !id.contains("black_stained_glass_pane")) out.add(s.index());
			}
		} else if (PANES.matcher(t).matches()) {
			for (SlotInfo s : slots) if (s.itemId().contains("red_stained_glass_pane")) out.add(s.index());
		}
		return out;
	}
}
