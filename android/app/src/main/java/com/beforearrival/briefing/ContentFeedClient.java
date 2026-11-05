package com.beforearrival.briefing;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;

/** One public Steam request. No redirects, account credentials or background polling. */
final class ContentFeedClient {
    private static final Pattern REGISTRATION = Pattern.compile("^https://store\\.steampowered\\.com/(?:news/app|app)/([0-9]+)(?:/[^?#]*)?/?$");
    private static final int MAX_BYTES = 256 * 1024;

    static String appId(String registration) {
        Matcher match = REGISTRATION.matcher(registration == null ? "" : registration);
        if (!match.matches()) throw new IllegalArgumentException("공식 Steam 게임 주소를 확인해 주세요.");
        java.math.BigInteger id = new java.math.BigInteger(match.group(1));
        if (id.signum() <= 0 || id.compareTo(new java.math.BigInteger("4294967295")) > 0)
            throw new IllegalArgumentException("Steam 게임 번호를 확인해 주세요.");
        return id.toString();
    }

    static String fetch(String registration) throws Exception {
        String id = appId(registration);
        URI endpoint = new URI("https://api.steampowered.com/ISteamNews/GetNewsForApp/v2/?appid=" + id
                + "&count=5&maxlength=600&format=json&feeds=steam_community_announcements");
        HttpsURLConnection connection = (HttpsURLConnection) endpoint.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(5000); connection.setReadTimeout(5000); connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Cookie", "");
        try {
            if (connection.getResponseCode() != 200 || connection.getContentLengthLong() > MAX_BYTES)
                throw new IllegalStateException("공식 소식을 가져오지 못했어요.");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[4096]; int count;
                while ((count = input.read(chunk)) != -1) {
                    if (bytes.size() + count > MAX_BYTES) throw new IllegalStateException("응답이 너무 커요.");
                    bytes.write(chunk, 0, count);
                }
                return parse(registration, new String(bytes.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally { connection.disconnect(); }
    }

    /** Parses a provider response separately so lifecycle and malformed response cases use fixtures. */
    static String parse(String registration, String response) throws Exception {
        if (response.length() > MAX_BYTES) throw new IllegalArgumentException("응답이 너무 커요.");
        String id = appId(registration);
        JSONObject news = new JSONObject(response).getJSONObject("appnews");
        if (!id.equals(String.valueOf(news.getLong("appid")))) throw new IllegalArgumentException("게임이 일치하지 않아요.");
        JSONArray entries = news.getJSONArray("newsitems"), items = new JSONArray();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.getJSONObject(i);
            if (!"steam_community_announcements".equals(entry.optString("feedname"))) continue;
            String url = entry.getString("url");
            if (!isOfficialArticle(registration, url)) throw new IllegalArgumentException("공식 원문 주소를 확인할 수 없어요.");
            String summary = clean(entry.optString("contents"));
            String title = clean(entry.optString("title"));
            if (summary.isEmpty() || title.isEmpty() || entry.optString("gid").isEmpty() || entry.getLong("date") <= 0)
                throw new IllegalArgumentException("소식 내용이 비어 있어요.");
            items.put(new JSONObject().put("id", "steam-" + entry.getString("gid"))
                    .put("title", title).put("summary", summary).put("originalUrl", url)
                    .put("publishedAt", Instant.ofEpochSecond(entry.getLong("date")).toString())
                    .put("publishedPrecision", "instant").put("deadlineAt", JSONObject.NULL)
                    .put("estimatedSeconds", Math.min(30, Math.max(8, (int) Math.ceil(summary.length() / 6.0)))));
        }
        return new JSONObject().put("items", items).toString();
    }

    static boolean isOfficialArticle(String registration, String url) {
        if (url == null || url.length() > 512) return false;
        try {
            URI uri = new URI(url);
            if (!"https".equals(uri.getScheme()) || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                    || uri.getRawQuery() != null || uri.getPort() != -1) return false;
            String id = appId(registration);
            return ("store.steampowered.com".equals(uri.getHost())
                    && uri.getPath().matches("/news/app/" + id + "/view/[0-9]+/?"))
                    || ("steamcommunity.com".equals(uri.getHost())
                    && uri.getPath().matches("/games/[A-Za-z0-9_-]+/announcements/detail/[0-9]+/?"));
        } catch (Exception invalid) { return false; }
    }

    private static String clean(String raw) {
        String text = raw.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?s)<[^>]*>", " ").replaceAll("\\[/?[A-Za-z][^\\]]*\\]", " ")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
                .replaceAll("\\s+", " ").trim();
        return text.substring(0, Math.min(300, text.length()));
    }
    private ContentFeedClient() { }
}
