package io.github.donttemptfate.fatesofa.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.GZIPInputStream;

/**
 * The only way OFA talks to the internet: plain GETs of public data (no cookies, no tokens), with a
 * timeout, a size cap and a clear User-Agent. Never follows redirects to another host.
 */
public final class Http {
	private Http() {}

	public static volatile String userAgent = "FatesOFA (https://github.com/DontTemptFate/Fates_QoL)";

	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	public record Response(int status, byte[] body, String etag, long retryAfterS) {
		public String text() {
			return new String(body, StandardCharsets.UTF_8);
		}

		public boolean ok() {
			return status == 200;
		}
	}

	/** GET {@code url}; reads at most {@code maxBytes} (after gunzip). Only https URLs. */
	public static Response get(String url, long maxBytes, String ifNoneMatch) throws IOException, InterruptedException {
		URI uri = URI.create(url);
		if (!"https".equals(uri.getScheme())) throw new IOException("only https is allowed: " + url);
		HttpRequest.Builder b = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofSeconds(60))
				.header("User-Agent", userAgent)
				.header("Accept-Encoding", "gzip")
				.GET();
		if (ifNoneMatch != null) b.header("If-None-Match", ifNoneMatch);
		HttpResponse<InputStream> r = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
		byte[] body;
		try (InputStream raw = r.body()) {
			boolean gz = r.headers().firstValue("Content-Encoding").map(v -> v.equalsIgnoreCase("gzip")).orElse(false);
			body = readCapped(gz ? new GZIPInputStream(raw) : raw, maxBytes);
		}
		long retry = r.headers().firstValue("Retry-After").map(v -> {
			try {
				return Long.parseLong(v.trim());
			} catch (NumberFormatException e) {
				return 60L;
			}
		}).orElse(0L);
		return new Response(r.statusCode(), body, r.headers().firstValue("ETag").orElse(null), retry);
	}

	/** Streams a GET straight into {@code file} (for big downloads). Returns the status code. */
	public static int download(String url, java.nio.file.Path file, long maxBytes) throws IOException, InterruptedException {
		URI uri = URI.create(url);
		if (!"https".equals(uri.getScheme())) throw new IOException("only https is allowed: " + url);
		HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(5)).header("User-Agent", userAgent).GET().build();
		HttpResponse<InputStream> r = CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
		try (InputStream in = r.body(); var out = java.nio.file.Files.newOutputStream(file)) {
			if (r.statusCode() != 200) return r.statusCode();
			byte[] buf = new byte[64 * 1024];
			long total = 0;
			int n;
			while ((n = in.read(buf)) > 0) {
				total += n;
				if (total > maxBytes) throw new IOException("download larger than " + maxBytes + " bytes");
				out.write(buf, 0, n);
			}
		}
		return 200;
	}

	static byte[] readCapped(InputStream in, long max) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[64 * 1024];
		long total = 0;
		int n;
		while ((n = in.read(buf)) > 0) {
			total += n;
			if (total > max) throw new IOException("response larger than " + max + " bytes");
			out.write(buf, 0, n);
		}
		return out.toByteArray();
	}
}
