package io.github.donttemptfate.fatesofa.items;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.donttemptfate.fates.common.text.Texts;
import io.github.donttemptfate.fatesofa.data.RepoItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Display stacks for repo items and stored items: the right base item, name, lore, SkyBlock id, the
 * SkyBlock resource pack's item model and skull textures, so they look like the real thing on Hypixel.
 * Built once per key and cached.
 */
public final class Stacks {
	private Stacks() {}

	private static final Map<String, ItemStack> CACHE = new ConcurrentHashMap<>();
	static final Pattern MODEL = Pattern.compile("ItemModel:\"([a-z0-9_.\\-]+:[a-z0-9_./\\-]+)\"");
	static final Pattern TEXTURE = Pattern.compile("Value:\"([A-Za-z0-9+/=]{20,})\"");

	public static ItemStack of(RepoItem it) {
		if (it == null) return ItemStack.EMPTY;
		return CACHE.computeIfAbsent(it.key(), k -> build(it.itemId(), it.key(), it.displayName(), it.lore(), it.nbt(), 1)).copy();
	}

	public static void clear() {
		CACHE.clear();
	}

	/** A display stack from stored parts (storage cache): base item id, SkyBlock key, name, lore, model, texture. */
	public static ItemStack build(String itemId, String key, String name, List<String> lore, String nbt, int count) {
		Item item = item(itemId);
		ItemStack st = new ItemStack(item, Math.max(1, Math.min(99, count)));
		try {
			st.set(DataComponents.CUSTOM_NAME, Texts.fromLegacy(name));
			List<Component> lines = new ArrayList<>();
			for (String l : lore) lines.add(Texts.fromLegacy(l));
			st.set(DataComponents.LORE, new ItemLore(lines));
			if (key != null) {
				CompoundTag tag = new CompoundTag();
				String id = key.contains(";") ? key.substring(0, key.indexOf(';')) : key;
				tag.putString("id", id.replace('-', ':'));
				st.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
			}
			if (nbt != null) {
				Matcher m = MODEL.matcher(nbt);
				if (m.find()) st.set(DataComponents.ITEM_MODEL, Identifier.parse(m.group(1)));
				Matcher t = TEXTURE.matcher(nbt);
				if (t.find() && item == Items.PLAYER_HEAD) {
					ResolvableProfile p = profile(t.group(1));
					if (p != null) st.set(DataComponents.PROFILE, p);
				}
			}
		} catch (RuntimeException ignored) {
			// a plain stack is better than none
		}
		return st;
	}

	/** A stored item (storage cache) as a display stack. */
	public static ItemStack stored(io.github.donttemptfate.fatesofa.storage.StoredItem si) {
		ItemStack st = build(si.itemId(), si.key(), si.name(), si.lore(), null, si.count());
		try {
			if (si.model() != null) st.set(DataComponents.ITEM_MODEL, Identifier.parse(si.model()));
			if (si.profile() != null) {
				com.google.gson.JsonElement j = com.google.gson.JsonParser.parseString(si.profile());
				ResolvableProfile.CODEC.parse(JsonOps.INSTANCE, j).result().ifPresent(p -> st.set(DataComponents.PROFILE, p));
			}
		} catch (RuntimeException ignored) {
			// plain stack
		}
		return st;
	}

	/** The parts of a live stack worth remembering (storage cache). */
	public static io.github.donttemptfate.fatesofa.storage.StoredItem remember(int slot, ItemStack st) {
		String model = null, profile = null;
		Identifier m = st.get(DataComponents.ITEM_MODEL);
		if (m != null) model = m.toString();
		ResolvableProfile p = st.get(DataComponents.PROFILE);
		if (p != null) {
			profile = ResolvableProfile.CODEC.encodeStart(JsonOps.INSTANCE, p).result().map(Object::toString).orElse(null);
			if (profile != null && profile.length() > 4000) profile = null;
		}
		return new io.github.donttemptfate.fatesofa.storage.StoredItem(slot, BuiltInRegistries.ITEM.getKey(st.getItem()).toString(),
				io.github.donttemptfate.fates.common.item.Items.key(st), Texts.legacy(io.github.donttemptfate.fates.common.item.Items.name(st)),
				io.github.donttemptfate.fates.common.item.Items.loreLegacy(st), model, profile, st.getCount());
	}

	static Item item(String id) {
		try {
			String clean = id == null ? "minecraft:barrier" : id;
			if (clean.equals("minecraft:skull")) clean = "minecraft:player_head";
			Item it = BuiltInRegistries.ITEM.getValue(Identifier.parse(clean));
			return it == null || it == Items.AIR ? Items.BARRIER : it;
		} catch (RuntimeException e) {
			return Items.BARRIER;
		}
	}

	/** A skull profile carrying only a texture, made through the component's own codec. */
	static ResolvableProfile profile(String textureBase64) {
		JsonObject prop = new JsonObject();
		prop.addProperty("name", "textures");
		prop.addProperty("value", textureBase64);
		JsonArray props = new JsonArray();
		props.add(prop);
		JsonObject o = new JsonObject();
		o.add("properties", props);
		return ResolvableProfile.CODEC.parse(JsonOps.INSTANCE, o).result().orElse(null);
	}
}
