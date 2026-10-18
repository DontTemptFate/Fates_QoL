package io.github.donttemptfate.fatesofa;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.donttemptfate.fatesofa.chat.ChatFilter;
import io.github.donttemptfate.fatesofa.data.GitRepoAccess;
import io.github.donttemptfate.fatesofa.data.Ingredient;
import io.github.donttemptfate.fatesofa.data.Price;
import io.github.donttemptfate.fatesofa.data.PriceService;
import io.github.donttemptfate.fatesofa.data.PriceTables;
import io.github.donttemptfate.fatesofa.data.Recipe;
import io.github.donttemptfate.fatesofa.protect.Protection;
import io.github.donttemptfate.fatesofa.state.ModApiCodec;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DataTest {
	@Test
	public void recipes() {
		JsonObject hyp = JsonParser.parseString("{internalname:'HYPERION',recipe:{A1:'GIANT_FRAGMENT_LASER:1',A2:'GIANT_FRAGMENT_LASER:1',A3:'GIANT_FRAGMENT_LASER:1',"
				+ "B1:'GIANT_FRAGMENT_LASER:1',B2:'NECRON_BLADE:1',B3:'GIANT_FRAGMENT_LASER:1',C1:'GIANT_FRAGMENT_LASER:1',C2:'GIANT_FRAGMENT_LASER:1',C3:'GIANT_FRAGMENT_LASER:1'}}").getAsJsonObject();
		List<Recipe> r = Recipe.fromItem("HYPERION", hyp);
		assertEquals(1, r.size());
		assertEquals(Recipe.Type.CRAFTING, r.get(0).type());
		assertEquals(List.of(new Ingredient("GIANT_FRAGMENT_LASER", 8), new Ingredient("NECRON_BLADE", 1)), r.get(0).inputs());
		JsonObject forge = JsonParser.parseString("{recipes:[{type:'forge',inputs:['GOLDEN_PLATE:1','REFINED_MITHRIL:5'],count:1,overrideOutputId:'MITHRIL_PLATE',duration:64800},{type:'weird'}]}").getAsJsonObject();
		List<Recipe> f = Recipe.fromItem("MITHRIL_PLATE", forge);
		assertEquals(1, f.size());
		assertEquals(64800, f.get(0).durationS());
		assertEquals(new Ingredient("SKYBLOCK_COIN", 1000), Ingredient.parse("SKYBLOCK_COIN:1000"));
		assertEquals(new Ingredient("ODD", 1), Ingredient.parse("ODD"));
	}

	@Test
	public void prices() {
		Map<String, PriceTables.Bz> bz = PriceTables.bazaar("{success:true,products:{ENCHANTED_DIAMOND:{quick_status:{buyPrice:180,sellPrice:170,buyVolume:5,sellVolume:6,buyMovingWeek:1,sellMovingWeek:2}},"
				+ "ENCHANTMENT_ULTIMATE_WISE_5:{quick_status:{buyPrice:2000000,sellPrice:1900000,buyVolume:1,sellVolume:1,buyMovingWeek:1,sellMovingWeek:1}}}}");
		Map<String, Double> npc = PriceTables.npc("{items:[{id:'ENCHANTED_DIAMOND',npc_sell_price:1280},{id:'HYPERION'},{id:'INK_SACK:3',npc_sell_price:2}]}");
		Map<String, PriceTables.Ah> ah = PriceTables.ah("{v:1,generated_at:1,ah:{HYPERION:{lbin:1.1e9,median:1.2e9,sales_day:4,last_sale_at:5},CHEAP:{lbin:1,median:null}}}");
		PriceService ps = new PriceService(Runnable::run, s -> {
		});
		ps.enabled = true;
		ps.load(bz, npc, ah, 10);
		ps.ahEnabled = false;
		// no network in the test: mark everything fresh
		Price d = ps.value("ENCHANTED_DIAMOND", 10);
		assertEquals(170, d.value(), 0);
		assertEquals(Price.Kind.BAZAAR_INSTANT_SELL, d.kind());
		assertEquals(1_900_000, ps.value("ULTIMATE_WISE;5", 10).value(), 0);
		assertEquals(1.1e9, ps.value("HYPERION", 10).value(), 0);
		ps.ahMode = PriceService.AhMode.MEDIAN;
		assertEquals(1.2e9, ps.value("HYPERION", 10).value(), 0);
		assertEquals(2, ps.npcPrice("INK_SACK-3"), 0);
		assertNull(ps.value("NOTHING", 10));
		// NPC price wins when the market pays less
		PriceService p2 = new PriceService(Runnable::run, x -> {
		});
		p2.ahEnabled = false;
		p2.load(Map.of(), Map.of("X", 50.0), Map.of("X", new PriceTables.Ah(10, Double.NaN, 1, 1)), 10);
		assertEquals(Price.Kind.NPC, p2.value("X", 10).kind());
	}

	@Test
	public void modApi() {
		ByteArrayOutputStream b = new ByteArrayOutputStream();
		b.write(1); // ok
		b.write(1); // version
		str(b, "mini123");
		b.write(1);
		str(b, "SKYBLOCK");
		b.write(0); // no lobby
		b.write(1);
		str(b, "crystal_hollows");
		b.write(0);
		ModApiCodec.Location l = ModApiCodec.location(b.toByteArray());
		assertEquals("mini123", l.serverName());
		assertTrue(l.skyblock());
		assertEquals("crystal_hollows", l.mode());
		assertNull(l.lobbyName());
		assertNull(ModApiCodec.location(new byte[] {0, 3})); // error answer
		try {
			ModApiCodec.location(new byte[] {1, 1, 50});
			fail("short packet accepted");
		} catch (IllegalArgumentException expected) {
		}
		byte[] reg = ModApiCodec.register(Map.of("hyevent:location", 1));
		assertArrayEquals(new byte[] {1, 1, 16, 'h', 'y', 'e', 'v', 'e', 'n', 't', ':', 'l', 'o', 'c', 'a', 't', 'i', 'o', 'n', 1}, reg);
		assertTrue(ModApiCodec.hello(new byte[] {1, 0}));
	}

	static void str(ByteArrayOutputStream b, String s) {
		byte[] x = s.getBytes(StandardCharsets.UTF_8);
		b.write(x.length);
		b.write(x, 0, x.length);
	}

	@Test
	public void protection() {
		Protection p = new Protection();
		Protection.Facts hype = new Protection.Facts("u1", "HYPERION", "Hyperion", 1e9, 3);
		assertEquals(Protection.Verdict.CONFIRM, p.decide(Protection.Action.NPC_SELL, hype, 0).verdict());
		assertEquals(Protection.Verdict.CONFIRM, p.decide(Protection.Action.NPC_SELL, hype, 120).verdict()); // a double-click isn't a confirmation
		assertEquals(Protection.Verdict.ALLOW, p.decide(Protection.Action.NPC_SELL, hype, 1000).verdict());
		assertEquals(Protection.Verdict.CONFIRM, p.decide(Protection.Action.NPC_SELL, hype, 10_000).verdict()); // window passed
		assertTrue(p.toggleItem("u1", "HYPERION"));
		assertEquals(Protection.Verdict.BLOCK, p.decide(Protection.Action.DROP, hype, 20_000).verdict());
		assertEquals(Protection.Verdict.BLOCK, p.decide(Protection.Action.NPC_SELL, hype, 20_000).verdict());
		Protection.Facts dirt = new Protection.Facts(null, "DIRT", "Dirt", 1, 7);
		assertEquals(Protection.Verdict.ALLOW, p.decide(Protection.Action.DROP, dirt, 0).verdict());
		assertEquals(Protection.Verdict.BLOCK, p.decide(Protection.Action.AUCTION, hype, 30_000).verdict());
		assertEquals(Protection.Verdict.BLOCK, p.decide(Protection.Action.BAZAAR, hype, 30_000).verdict());
		assertEquals(Protection.Verdict.BLOCK, p.decide(Protection.Action.TRADE, hype, 30_000).verdict());
		p.toggleSlot(7);
		// which windows count as selling / listing / trading; storage and the like are fine
		assertEquals(Protection.Action.AUCTION, Protection.actionFor("Create BIN Auction", java.util.List.of()));
		assertEquals(Protection.Action.AUCTION, Protection.actionFor("Co-op Auction House", java.util.List.of()));
		assertEquals(Protection.Action.BAZAAR, Protection.actionFor("Bazaar ➜ Farming", java.util.List.of()));
		assertEquals(Protection.Action.TRADE, Protection.actionFor("You            Steve", java.util.List.of()));
		assertEquals(Protection.Action.NPC_SELL, Protection.actionFor("Farm Merchant", java.util.List.of("Sell Item", "Wheat")));
		assertEquals(Protection.Action.SALVAGE, Protection.actionFor("Salvage Items", java.util.List.of()));
		assertNull(Protection.actionFor("Ender Chest (1/9)", java.util.List.of()));
		assertNull(Protection.actionFor("Wardrobe (1/2)", java.util.List.of()));
		assertNull(Protection.actionFor("Large Chest", java.util.List.of()));
		Protection q = new Protection();
		q.fromJson(p.toJson());
		assertTrue(q.itemLocked("u1", null));
		assertTrue(q.slotLocked(7));
		assertFalse(q.slotLocked(8));
	}

	@Test
	public void chatFilter() {
		ChatFilter f = new ChatFilter();
		f.groups(g -> g.equals("watchdog") || g.equals("sacks"));
		assertEquals(ChatFilter.Result.HIDE, f.decide("[WATCHDOG ANNOUNCEMENT]", 0).result());
		assertEquals(ChatFilter.Result.SHOW, f.decide("Hello there", 0).result());
		assertEquals(ChatFilter.Result.SHOW, f.decide("[Sacks] +12 items.", 0).result());
		ChatFilter.Decision d = f.decide("[Sacks] +12 items.", 1000);
		assertEquals(ChatFilter.Result.COMPACT, d.result());
		assertEquals(2, d.count());
		assertEquals(ChatFilter.Result.SHOW, f.decide("[Sacks] +12 items.", 200_000).result()); // too late to fold
		assertEquals(ChatFilter.Result.SHOW, f.decide("You earned 5 GEXP", 0).result()); // group off
	}

	@Test
	public void gitRepo() throws Exception {
		String listing = "001e# service=git-upload-pack\n0000015bb19b032b79527c1999e278535160f411ad95bece HEAD\0multi_ack\n003fb19b032b79527c1999e278535160f411ad95bece refs/heads/master\n";
		assertEquals("b19b032b79527c1999e278535160f411ad95bece", GitRepoAccess.ref(listing, "refs/heads/master"));
		assertNull(GitRepoAccess.ref(listing, "refs/heads/main"));
		ByteArrayOutputStream zip = new ByteArrayOutputStream();
		try (ZipOutputStream z = new ZipOutputStream(zip)) {
			put(z, "repo-abc/items/A.json", "{}");
			put(z, "repo-abc/README.md", "x");
			put(z, "repo-abc/items/../../evil.json", "{}");
		}
		Path dir = Files.createTempDirectory("ofa");
		try {
			GitRepoAccess.unzip(new ByteArrayInputStream(zip.toByteArray()), dir, p -> p.startsWith("items/"));
			fail("zip slip not refused");
		} catch (java.io.IOException expected) {
		}
		Path dir2 = Files.createTempDirectory("ofa");
		ByteArrayOutputStream ok = new ByteArrayOutputStream();
		try (ZipOutputStream z = new ZipOutputStream(ok)) {
			put(z, "repo-abc/items/A.json", "{}");
			put(z, "repo-abc/README.md", "x");
		}
		assertEquals(1, GitRepoAccess.unzip(new ByteArrayInputStream(ok.toByteArray()), dir2, p -> p.startsWith("items/")));
		assertTrue(Files.exists(dir2.resolve("items/A.json")));
		assertFalse(Files.exists(dir2.resolve("README.md")));
	}

	static void put(ZipOutputStream z, String name, String body) throws Exception {
		z.putNextEntry(new ZipEntry(name));
		z.write(body.getBytes(StandardCharsets.UTF_8));
		z.closeEntry();
	}
}
