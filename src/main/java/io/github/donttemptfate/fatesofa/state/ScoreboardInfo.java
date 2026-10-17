package io.github.donttemptfate.fatesofa.state;

import io.github.donttemptfate.fates.common.text.Fmt;
import io.github.donttemptfate.fates.common.text.Strip;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the SkyBlock sidebar says, read from its cleaned lines (top to bottom). Every field is null
 * when its line isn't there; nothing is guessed.
 */
public record ScoreboardInfo(
		boolean skyblock,
		String server,
		String area,
		String date,
		String time,
		Double purse,
		Double bits,
		Double motes,
		Double copper,
		String dungeonFloor,
		Integer clearedPercent,
		String slayerBoss,
		String slayerProgress,
		List<String> objective,
		List<String> lines) {

	static final Pattern SERVER = Pattern.compile("^\\d{1,2}/\\d{1,2}/\\d{2,4}\\s+(\\S+)");
	static final Pattern MONEY = Pattern.compile("^(Purse|Piggy|Bits|Motes|Copper):\\s*([\\d,.]+[kMB]?)");
	static final Pattern FLOOR = Pattern.compile("(?:The Catacombs|Catacombs)\\s*\\(([FM]\\d|E)\\)");
	static final Pattern KUUDRA = Pattern.compile("Kuudra's Hollow\\s*\\((T\\d)\\)");
	static final Pattern CLEARED = Pattern.compile("^Cleared:\\s*(\\d{1,3})%");
	static final Pattern DATE = Pattern.compile("^(Early |Late )?(Spring|Summer|Autumn|Winter)\\s+\\d{1,2}(st|nd|rd|th)$");
	static final Pattern TIME = Pattern.compile("^\\d{1,2}:\\d{2}\\s*[ap]m.*");
	static final Pattern SLAYER_BOSS = Pattern.compile("^(Revenant Horror|Tarantula Broodfather|Sven Packmaster|Voidgloom Seraph|Inferno Demonlord|Riftstalker Bloodfiend)\\s+([IVX]+)$");

	/**
	 * @param title the objective's display title (e.g. "SKYBLOCK CO-OP")
	 * @param raw   the sidebar lines top to bottom, formatting included
	 */
	public static ScoreboardInfo parse(String title, List<String> raw) {
		String t = Strip.clean(title).toUpperCase(Locale.ROOT);
		boolean sb = t.contains("SKYBLOCK") || t.contains("SKIBLOCK");
		List<String> lines = new ArrayList<>();
		for (String r : raw) lines.add(Strip.clean(r));
		String server = null, area = null, date = null, time = null, floor = null, boss = null, progress = null;
		Double purse = null, bits = null, motes = null, copper = null;
		Integer cleared = null;
		List<String> objective = new ArrayList<>();
		boolean inObjective = false;
		for (int i = 0; i < lines.size(); i++) {
			String l = lines.get(i);
			if (l.isEmpty()) {
				inObjective = false;
				continue;
			}
			Matcher m;
			if (server == null && (m = SERVER.matcher(l)).find()) {
				server = m.group(1);
				continue;
			}
			if (l.startsWith("⏣") || l.startsWith("ф")) {
				area = l.substring(1).trim();
				if ((m = FLOOR.matcher(l)).find() || (m = KUUDRA.matcher(l)).find()) floor = m.group(1);
				continue;
			}
			if (date == null && DATE.matcher(l).matches()) {
				date = l;
				continue;
			}
			if (time == null && TIME.matcher(l).matches()) {
				time = l.replaceAll("[^0-9:apm ]", "").trim();
				continue;
			}
			if ((m = MONEY.matcher(l)).find()) {
				double v = Fmt.parse(m.group(2));
				switch (m.group(1)) {
					case "Purse", "Piggy" -> purse = v;
					case "Bits" -> bits = v;
					case "Motes" -> motes = v;
					case "Copper" -> copper = v;
					default -> {
					}
				}
				continue;
			}
			if ((m = FLOOR.matcher(l)).find()) {
				floor = m.group(1);
				continue;
			}
			if ((m = KUUDRA.matcher(l)).find()) {
				floor = m.group(1);
				continue;
			}
			if ((m = CLEARED.matcher(l)).find()) {
				cleared = Integer.parseInt(m.group(1));
				continue;
			}
			if ((m = SLAYER_BOSS.matcher(l)).find()) {
				boss = l;
				if (i + 1 < lines.size()) progress = lines.get(i + 1);
				continue;
			}
			if (l.equals("Objective")) {
				inObjective = true;
				continue;
			}
			if (inObjective) objective.add(l);
		}
		return new ScoreboardInfo(sb, server, area, date, time, purse, bits, motes, copper, floor, cleared, boss, progress,
				List.copyOf(objective), List.copyOf(lines));
	}
}
