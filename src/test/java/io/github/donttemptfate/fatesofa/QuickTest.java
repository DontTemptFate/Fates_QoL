package io.github.donttemptfate.fatesofa;

import com.google.gson.JsonParser;
import io.github.donttemptfate.fatesofa.quick.QuickCommands;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class QuickTest {
	@Test
	public void commandsAreCleanedToOneLine() {
		assertEquals("Warp crimson", QuickCommands.clean("  //Warp   crimson \n")); // case kept: arguments can be case-sensitive
		assertEquals("pets", QuickCommands.clean("/pets§c"));
		assertEquals("", QuickCommands.clean(null));
		assertEquals(QuickCommands.MAX_COMMAND, QuickCommands.clean("a".repeat(500)).length());
	}

	@Test
	public void aliasesAreCheckedAndSaved() {
		List<QuickCommands.Entry> list = List.of(new QuickCommands.Entry("Crimson", "warp crimson", "", true, "ch"));
		assertNull(QuickCommands.aliasProblem("", list, -1));
		assertNull(QuickCommands.aliasProblem("dh", list, -1));
		assertNotNull(QuickCommands.aliasProblem("ch", list, -1)); // taken
		assertNull(QuickCommands.aliasProblem("ch", list, 0));     // its own
		assertNotNull(QuickCommands.aliasProblem("ofa", list, -1));
		assertNotNull(QuickCommands.aliasProblem("bad alias", list, -1));
		assertNotNull(QuickCommands.aliasProblem("1x", list, -1));

		var back = QuickCommands.fromJson(JsonParser.parseString(QuickCommands.toJson(list).toString()));
		assertEquals(list, back);
		assertEquals("paper", back.get(0).icon()); // blank icon gets a default
	}

	@Test
	public void defaultsWhenNothingSavedAndBadEntriesSkipped() {
		assertEquals(QuickCommands.defaults(), QuickCommands.fromJson(null));
		var got = QuickCommands.fromJson(JsonParser.parseString(
				"[{\"name\":\"x\",\"command\":\"\"},5,{\"command\":\"/hub\",\"alias\":\"h\"},{\"command\":\"is\",\"alias\":\"h\"}]"));
		assertEquals(2, got.size());
		assertEquals("hub", got.get(0).command());
		assertEquals("h", got.get(0).alias());
		assertEquals("", got.get(1).alias()); // duplicate alias dropped
		assertTrue(got.get(1).tab());
		assertEquals("/is", got.get(1).label());
	}
}
