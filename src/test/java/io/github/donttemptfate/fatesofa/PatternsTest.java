package io.github.donttemptfate.fatesofa;

import com.google.gson.JsonParser;
import io.github.donttemptfate.fatesofa.data.Patterns;
import org.junit.Test;

import java.util.regex.Matcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PatternsTest {
	@Test
	public void colourCodesRemoved() {
		assertEquals("\\[BOSS] Sadan: ENOUGH!", Patterns.colourless("§c\\[BOSS] Sadan§r§f: ENOUGH!"));
		assertEquals("(?<name>\\S*) chose the wrong .*", Patterns.colourless("(?:§.)*(?<name>\\S*) (?:§.)*chose the wrong .*").trim()
				.replace("  ", " "));
		assertEquals("(?<health>[\\d,]+)/[\\d,]+.*", Patterns.colourless("§[c6](?<health>[\\d,]+)/[\\d,]+.*"));
	}

	@Test
	public void repoFirstThenFallback() {
		Patterns p = new Patterns().define("slayer.complete", "SLAYER QUEST COMPLETE!").define("only.mine", "hello (?<who>\\w+)");
		p.load(JsonParser.parseString("{sourceLabel:'x',regexes:{'slayer.complete':'\\\\s*SLAYER QUEST COMPLETE!','broken':'(('}}").getAsJsonObject());
		assertTrue(p.get("slayer.complete").matcher("  SLAYER QUEST COMPLETE!").matches());
		Matcher m = p.get("only.mine").matcher("hello Fate");
		assertTrue(m.matches());
		assertEquals("Fate", m.group("who"));
		assertNull(p.get("broken"));
		assertNull(p.get("nothing"));
		assertNotNull(p.get("only.mine"));
	}
}
