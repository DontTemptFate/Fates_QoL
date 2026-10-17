package io.github.donttemptfate.fatesofa.items;

import io.github.donttemptfate.fatesofa.data.ItemRepo;
import io.github.donttemptfate.fatesofa.data.RepoItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Search over the item repo: every word must appear in the item's name, key or lore; names that match
 * rank first. Built once per repo version; queries are cached.
 */
public final class ItemIndex {
	record Entry(RepoItem item, String name, String text) {
	}

	private final List<Entry> entries;
	private final ItemRepo repo;
	private String lastQuery;
	private List<RepoItem> lastResult = List.of();

	public ItemIndex(ItemRepo repo) {
		this.repo = repo;
		List<Entry> e = new ArrayList<>(repo.size());
		for (RepoItem it : repo.all()) {
			if (it.key().startsWith("MAP-") || it.plainName().isEmpty()) continue;
			e.add(new Entry(it, it.plainName().toLowerCase(Locale.ROOT), it.searchText()));
		}
		e.sort(Comparator.comparing(Entry::name).thenComparing(x -> x.item().key()));
		this.entries = List.copyOf(e);
	}

	public ItemRepo repo() {
		return repo;
	}

	public int size() {
		return entries.size();
	}

	public synchronized List<RepoItem> search(String query) {
		String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		if (q.equals(lastQuery)) return lastResult;
		List<RepoItem> out;
		if (q.isEmpty()) {
			out = entries.stream().map(Entry::item).toList();
		} else {
			String[] words = q.split("\\s+");
			List<Entry> hits = new ArrayList<>();
			for (Entry e : entries) {
				boolean all = true;
				for (String w : words) {
					if (!e.text().contains(w)) {
						all = false;
						break;
					}
				}
				if (all) hits.add(e);
			}
			hits.sort(Comparator.comparingInt((Entry e) -> rank(e, q, words)).thenComparing(Entry::name));
			out = hits.stream().map(Entry::item).toList();
		}
		lastQuery = q;
		lastResult = out;
		return out;
	}

	static int rank(Entry e, String q, String[] words) {
		if (e.name().equals(q)) return 0;
		if (e.name().startsWith(q)) return 1;
		if (e.name().contains(q)) return 2;
		boolean allInName = true;
		for (String w : words) if (!e.name().contains(w)) allInName = false;
		return allInName ? 3 : 4;
	}
}
