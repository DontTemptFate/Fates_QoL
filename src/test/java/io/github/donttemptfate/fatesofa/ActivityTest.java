package io.github.donttemptfate.fatesofa;

import com.google.gson.JsonParser;
import io.github.donttemptfate.fatesofa.activity.Drops;
import io.github.donttemptfate.fatesofa.activity.FishingTracker;
import io.github.donttemptfate.fatesofa.activity.SlayerTracker;
import io.github.donttemptfate.fatesofa.activity.TabWidgets;
import io.github.donttemptfate.fatesofa.state.ScoreboardInfo;
import io.github.donttemptfate.fatesofa.state.TabInfo;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ActivityTest {
	@Test
	public void drops() {
		Drops.Drop d = Drops.parse("VERY RARE DROP! (Enchanted Book) (+123 ✯ Magic Find)");
		assertNotNull(d);
		assertEquals("Enchanted Book", d.item());
		assertEquals(123, (int) d.magicFind());
		Drops.Drop e = Drops.parse("RARE DROP! Revenant Viscera x2 (+80% ✯ Magic Find)");
		assertEquals("Revenant Viscera", e.item());
		assertEquals(2, e.amount());
		assertEquals("Foul Flesh", Drops.parse("RARE DROP! Foul Flesh").item());
		assertNull(Drops.parse("You dropped your sword"));
	}

	@Test
	public void slayer() {
		SlayerTracker t = new SlayerTracker();
		t.chat("  SLAYER QUEST STARTED!", 1000);
		t.scoreboard(ScoreboardInfo.parse("SKYBLOCK", List.of("Slayer Quest", "Revenant Horror IV", "Slay the boss!")), 61_000);
		t.chat("RARE DROP! Revenant Viscera (+80% ✯ Magic Find)", 70_000);
		t.chat("  SLAYER QUEST COMPLETE!", 91_000);
		assertEquals(1, t.kills().size());
		assertEquals(60_000, t.kills().get(0).spawnMs());
		assertEquals(30_000, t.kills().get(0).killMs());
		assertEquals("Revenant Horror IV", t.kills().get(0).boss());
		assertEquals(1, (int) t.drops().get("Revenant Viscera"));
		t.chat("  SLAYER QUEST STARTED!", 100_000);
		t.chat("  SLAYER QUEST FAILED!", 200_000);
		assertEquals(1, t.fails());
	}

	@Test
	public void fishing() {
		FishingTracker f = new FishingTracker();
		f.load(JsonParser.parseString("{PARK:{chat_color:'§b',sea_creatures:{'Night Squid':{chat_message:'Pitch darkness reveals a Night Squid.',rarity:'COMMON'}}},"
				+ "WATER:{sea_creatures:{'Sea Emperor':{chat_message:'The Sea Emperor arises from the depths.',rarity:'LEGENDARY'}}}}").getAsJsonObject());
		assertEquals(2, f.knownCreatures());
		assertEquals("Night Squid", f.chat("Pitch darkness reveals a Night Squid.", 0).name());
		f.chat("It's a Double Hook! Woot woot!", 10);
		f.chat("The Sea Emperor arises from the depths.", 20);
		f.chat("TROPHY FISH! You caught a Blobfish BRONZE.", 30);
		assertEquals(2, f.total());
		assertEquals(1, f.doubleHooks());
		assertEquals(1, (int) f.trophies().get("Blobfish BRONZE"));
	}

	@Test
	public void widgets() {
		TabInfo t = TabInfo.parse(List.of("Area: Dwarven Mines", "", "Commissions:", " Goblin Slayer: 45%", " Mithril Miner: DONE", "",
				"Powders:", " Mithril: 1,234,567", " Gemstone: 89k", "", "Visitors: (2)", " Jacob", " Rhys", "", "Pests:", " Alive: 3",
				" Infested Plots: 2, 5"));
		var c = TabWidgets.commissions(t);
		assertEquals(2, c.size());
		assertEquals("Goblin Slayer", c.get(0).name());
		assertEquals(true, c.get(1).done());
		assertEquals(1_234_567, TabWidgets.powders(t).get("Mithril"), 0);
		assertEquals(89_000, TabWidgets.powders(t).get("Gemstone"), 0);
		assertEquals(List.of("Jacob", "Rhys"), TabWidgets.visitors(t));
		assertEquals(3, TabWidgets.pests(t).alive());
		assertEquals("2, 5", TabWidgets.pests(t).plots());
	}
}
