package io.github.donttemptfate.fatesofa.data;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * A public GitHub data repository (NEU-REPO, SkyHanni-REPO): finds the newest commit of a branch with
 * git's own ref listing (no API key, no API rate limit), downloads that commit as a zip and unpacks the
 * wanted folders. Data only: nothing from it is ever run.
 */
public final class GitRepo {
	public final String owner;
	public final String name;
	public final String branch;
	private final Predicate<String> keep;

	static final long MAX_ZIP = 120L * 1024 * 1024;
	static final long MAX_ENTRY = 16L * 1024 * 1024;
	static final long MAX_TOTAL = 400L * 1024 * 1024;
	static final Pattern SHA = Pattern.compile("^[0-9a-f]{40}$");

	public GitRepo(String owner, String name, String branch, Predicate<String> keep) {
		this.owner = owner;
		this.name = name;
		this.branch = branch;
		this.keep = keep;
	}

	public String label() {
		return owner + "/" + name;
	}

	/** The commit the branch points at now. */
	public String latestCommit() throws IOException, InterruptedException {
		Http.Response r = Http.get("https://github.com/" + owner + "/" + name + ".git/info/refs?service=git-upload-pack", 4L * 1024 * 1024, null);
		if (!r.ok()) throw new IOException(label() + ": ref listing answered " + r.status());
		String sha = refFromListing(r.text(), "refs/heads/" + branch);
		if (sha == null) throw new IOException(label() + ": branch " + branch + " not found");
		return sha;
	}

	/** Reads a ref's commit from git's smart-HTTP ref advertisement. */
	static String refFromListing(String listing, String ref) {
		Matcher m = Pattern.compile("([0-9a-f]{40}) " + Pattern.quote(ref) + "(?:\\x00|\\n|$)").matcher(listing);
		return m.find() ? m.group(1) : null;
	}

	/**
	 * Downloads {@code commit} and unpacks the kept files into {@code target} (replaced as a whole only
	 * once everything unpacked fine).
	 */
	public void download(String commit, Path target) throws IOException, InterruptedException {
		if (!SHA.matcher(commit).matches()) throw new IOException("bad commit id");
		Files.createDirectories(target.getParent());
		Path zip = target.resolveSibling(target.getFileName() + ".zip.part");
		Path tmp = target.resolveSibling(target.getFileName() + ".part");
		try {
			int status = Http.download("https://codeload.github.com/" + owner + "/" + name + "/zip/" + commit, zip, MAX_ZIP);
			if (status != 200) throw new IOException(label() + ": download answered " + status);
			deleteTree(tmp);
			Files.createDirectories(tmp);
			try (InputStream in = Files.newInputStream(zip)) {
				unzip(in, tmp, keep);
			}
		} finally {
			Files.deleteIfExists(zip);
		}
		Files.writeString(tmp.resolve(".commit"), commit, StandardCharsets.UTF_8);
		deleteTree(target);
		Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
	}

	/** Unpacks a GitHub zip (one top folder) keeping paths {@code keep} accepts; refuses paths leaving {@code dir}. */
	static int unzip(InputStream in, Path dir, Predicate<String> keep) throws IOException {
		int files = 0;
		long total = 0;
		Path root = dir.toAbsolutePath().normalize();
		try (ZipInputStream z = new ZipInputStream(in)) {
			ZipEntry e;
			byte[] buf = new byte[64 * 1024];
			while ((e = z.getNextEntry()) != null) {
				String n = e.getName();
				int slash = n.indexOf('/');
				if (slash < 0 || e.isDirectory()) continue;
				String rel = n.substring(slash + 1);
				if (rel.isEmpty() || !keep.test(rel)) continue;
				Path out = root.resolve(rel).normalize();
				if (!out.startsWith(root)) throw new IOException("zip entry outside the folder: " + n);
				Files.createDirectories(out.getParent());
				long size = 0;
				try (var os = Files.newOutputStream(out)) {
					int k;
					while ((k = z.read(buf)) > 0) {
						size += k;
						total += k;
						if (size > MAX_ENTRY || total > MAX_TOTAL) throw new IOException("zip too large at " + n);
						os.write(buf, 0, k);
					}
				}
				files++;
			}
		}
		return files;
	}

	public static void deleteTree(Path p) throws IOException {
		if (!Files.exists(p)) return;
		try (Stream<Path> s = Files.walk(p)) {
			for (Path f : s.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(f);
		}
	}

	/** The commit of an unpacked copy, or null. */
	public static String commitOf(Path dir) {
		try {
			String c = Files.readString(dir.resolve(".commit"), StandardCharsets.UTF_8).trim();
			return SHA.matcher(c).matches() ? c : null;
		} catch (IOException e) {
			return null;
		}
	}
}
