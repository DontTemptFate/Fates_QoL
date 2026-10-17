package io.github.donttemptfate.fatesofa.dungeon;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Three Weirdos puzzle: the statements that are true mark the chest with the reward (chat hint only). */
public final class ThreeWeirdos {
	private ThreeWeirdos() {}

	static final Pattern NPC = Pattern.compile("^\\[NPC] (?<name>\\w+): (?<text>.+)$");
	static final List<Pattern> TRUE = List.of(
			Pattern.compile("^The reward is not in my chest!$"),
			Pattern.compile("^At least one of them is lying, and the reward is not in \\w+'s chest[!.]?$"),
			Pattern.compile("^My chest doesn't have the reward\\. We are all telling the truth\\.$"),
			Pattern.compile("^My chest has the reward and I'm telling the truth!$"),
			Pattern.compile("^The reward isn't in any of our chests\\.$"),
			Pattern.compile("^Both of them are telling the truth\\. Also, \\w+ has the reward in their chest!$"));

	/** The name of the weirdo whose chest has the reward, when this line says so; else null. */
	public static String answer(String plain) {
		Matcher m = NPC.matcher(plain.trim());
		if (!m.matches()) return null;
		for (Pattern p : TRUE) if (p.matcher(m.group("text").trim()).matches()) return m.group("name");
		return null;
	}
}
