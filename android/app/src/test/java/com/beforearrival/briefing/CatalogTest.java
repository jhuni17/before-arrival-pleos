package com.beforearrival.briefing;

import static org.junit.Assert.*;
import android.content.SharedPreferences;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class CatalogTest {
    private SharedPreferences preferences;
    private String defaults;
    private ContentCatalog catalog;
    private static final Instant NOW = Instant.parse("2026-11-05T12:12:22Z");

    @Before public void setup() throws Exception {
        preferences = RuntimeEnvironment.getApplication().getSharedPreferences("catalog-test", 0);
        preferences.edit().clear().commit();
        try (InputStream input = RuntimeEnvironment.getApplication().getAssets().open("catalog.json");
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096]; int count;
            while ((count = input.read(chunk)) != -1) bytes.write(chunk, 0, count);
            defaults = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
        catalog = new ContentCatalog(defaults, preferences);
    }

    @Test public void migrationRunsOnceAndRetainsEnabledPriorityAndEverySavedEnum() throws Exception {
        preferences.edit().clear().putBoolean("enabled_GAME", false).putString("priority", "COMMUNITY")
                .putStringSet("saved", Set.of("GAME", "SCHEDULE", "COMMUNITY")).commit();
        catalog = new ContentCatalog(defaults, preferences);
        assertEquals("sample-community", catalog.sources.get(0).id);
        assertFalse(catalog.source("sample-game").enabled);
        assertEquals(3, catalog.savedItems.size());
        catalog.move("sample-game", -1); catalog.source("sample-game").enabled = true;
        catalog.persist(preferences);
        ContentCatalog restored = new ContentCatalog(defaults, preferences);
        assertEquals("sample-game", restored.sources.get(0).id);
        assertTrue(restored.source("sample-game").enabled);
        assertEquals(3, restored.savedItems.size());
        assertEquals(2, new JSONObject(preferences.getString(ContentCatalog.STORAGE_KEY, "")).getInt("schemaVersion"));
    }

    @Test public void officialSnapshotRegistrationPlansAndPersistsWithoutFabrication() throws Exception {
        ContentCatalog.Source source = catalog.add("LoL", "lol", "https://www.leagueoflegends.com/ko-kr/news/game-updates/");
        assertEquals("url:https://www.leagueoflegends.com/ko-kr/news/game-updates", source.id);
        assertEquals("connected_snapshot", source.status); assertEquals(1, source.items.size());
        assertEquals(NOW, source.items.get(0).fetchedAt); assertTrue(catalog.canOpen(source.items.get(0)));
        for (int i = 0; i < 3; i++) catalog.move(source.id, -1);
        SampleRouteInput route = new SampleRouteInput();
        ContentCatalog.Plan longPlan = catalog.plan(route.snapshot());
        assertEquals(source.id, longPlan.selected.get(0).sourceId);
        assertTrue(longPlan.narration().startsWith(source.items.get(0).summary));
        route.select(4); ContentCatalog.Plan shortPlan = catalog.plan(route.snapshot());
        assertEquals(12, shortPlan.budgetSeconds); assertEquals(1, shortPlan.selected.size());
        assertEquals("sample-schedule", shortPlan.selected.get(0).sourceId);
        catalog.toggleSaved(source.items.get(0)); catalog.persist(preferences);
        ContentCatalog restored = new ContentCatalog(defaults, preferences);
        assertEquals(source.id, restored.sources.get(0).id); assertEquals(1, restored.savedItems.size());
        assertEquals("official_snapshot", restored.savedItems.get(0).provenance);
    }

    @Test public void dateOnlyOfficialPublicationDoesNotInventTime() throws Exception {
        ContentCatalog.Source source = catalog.add("메이플", "maple", "https://maplestory.nexon.com/news/update");
        assertEquals("2026-10-22", source.items.get(0).publishedAt);
        assertEquals("date", source.items.get(0).publishedPrecision);
        catalog.persist(preferences); catalog = new ContentCatalog(defaults, preferences);
        assertEquals("2026-10-22", catalog.source(source.id).items.get(0).publishedAt);
    }

    @Test public void unsupportedRegistrationRemainsEmptyAndUrlAliasesCannotDuplicate() throws Exception {
        ContentCatalog.Source source = catalog.add("채널", "youtube", "https://www.youtube.com/@news");
        assertEquals("setup_needed", source.status); assertTrue(source.items.isEmpty());
        source = catalog.add("게임", "steam", "https://store.steampowered.com/app/000570/Dota_2/");
        assertEquals("https://store.steampowered.com/news/app/570", source.registrationUrl);
        assertTrue(source.items.isEmpty());
        try { catalog.add("중복", "steam", "https://store.steampowered.com/news/app/570/"); fail(); }
        catch (IllegalArgumentException expected) { }
        for (String invalid : new String[]{"http://store.steampowered.com/app/570", "https://store.steampowered.com.evil.test/app/570",
                "https://user@store.steampowered.com/app/570", "https://store.steampowered.com/app/570#fragment",
                "https://store.steampowered.com/app/0", "https://store.steampowered.com/app/4294967296"}) {
            try { catalog.add("잘못된 주소", "steam", invalid); fail(invalid); } catch (IllegalArgumentException expected) { }
        }
        try { catalog.add("x".repeat(81), "youtube", "https://www.youtube.com/@another"); fail(); }
        catch (IllegalArgumentException expected) { }
        assertEquals(5, catalog.sources.size());
    }

    @Test public void officialRuntimeDedupFailureAndEmptyLifecycleKeepsLastSuccess() throws Exception {
        ContentCatalog.Source source = catalog.add("Steam", "steam", "https://store.steampowered.com/news/app/570");
        String parsed = ContentFeedClient.parse(source.registrationUrl, steamResponse(570, "steam_community_announcements", true));
        assertTrue(catalog.importSnapshot(source.id, parsed, NOW));
        assertEquals("connected", source.status); assertEquals(1, source.items.size());
        assertEquals("official_runtime", source.items.get(0).provenance);
        assertTrue(catalog.canOpen(source.items.get(0))); assertEquals(NOW, source.lastSuccessAt);
        assertEquals("Hello world & everyone", source.items.get(0).summary);
        catalog.markFailure(source.id, NOW.plusSeconds(30));
        assertEquals("stale", source.fetchState); assertEquals(1, source.items.size());
        assertEquals(NOW, source.lastSuccessAt); assertTrue(source.statusLabel().contains("이전"));
        assertFalse(catalog.importSnapshot(source.id, "{\"items\":[{}]}", NOW.plusSeconds(45)));
        assertEquals(1, source.items.size());
        assertTrue(catalog.importSnapshot(source.id, "{\"items\":[]}", NOW.plusSeconds(60)));
        assertEquals("empty", source.fetchState); assertTrue(source.items.isEmpty());
        assertEquals(NOW.plusSeconds(60), source.lastSuccessAt);
        catalog.persist(preferences); catalog = new ContentCatalog(defaults, preferences);
        assertEquals("empty", catalog.source(source.id).fetchState);
        assertTrue(catalog.source(source.id).items.isEmpty());
    }

    @Test public void parserRejectsOtherAppsUnofficialUrlsAndIgnoresExternalFeeds() throws Exception {
        String registration = "https://store.steampowered.com/news/app/570";
        try { ContentFeedClient.parse(registration, steamResponse(730, "steam_community_announcements", false)); fail(); }
        catch (IllegalArgumentException expected) { }
        JSONObject parsed = new JSONObject(ContentFeedClient.parse(registration, steamResponse(570, "pcgamer", false)));
        assertEquals(0, parsed.getJSONArray("items").length());
        assertFalse(ContentFeedClient.isOfficialArticle(registration, "https://store.steampowered.com/news/app/730/view/123"));
        assertFalse(ContentFeedClient.isOfficialArticle(registration, "https://evil.test/games/dota2/announcements/detail/123"));
        assertFalse(ContentFeedClient.isOfficialArticle(registration, "https://steamcommunity.com/games/dota2/announcements/detail/123#fragment"));
        assertTrue(ContentFeedClient.isOfficialArticle(registration, "https://steamcommunity.com/games/dota2/announcements/detail/123"));
    }

    @Test public void catalogDeduplicatesAcrossSourcesAndUnknownRouteNeverSelectsSample() throws Exception {
        ContentCatalog.Source source = catalog.add("Steam", "steam", "https://store.steampowered.com/news/app/570");
        String parsed = ContentFeedClient.parse(source.registrationUrl, steamResponse(570, "steam_community_announcements", true));
        catalog.importSnapshot(source.id, parsed, NOW);
        ContentCatalog.Source second = catalog.add("다른 게임", "steam", "https://store.steampowered.com/news/app/730");
        JSONObject snapshot = new JSONObject(parsed);
        snapshot.getJSONArray("items").getJSONObject(0).put("originalUrl", "https://steamcommunity.com/games/dota2/announcements/detail/123");
        catalog.importSnapshot(source.id, snapshot.toString(), NOW);
        catalog.importSnapshot(second.id, snapshot.toString(), NOW);
        assertEquals(4, catalog.plan(new SampleRouteInput().snapshot()).selected.size());
        assertTrue(catalog.plan(new NavigationRouteInput(ignored -> {}).snapshot()).selected.isEmpty());
    }

    private static String steamResponse(int appId, String feed, boolean duplicate) throws Exception {
        JSONObject item = new JSONObject().put("gid", "123").put("title", "Official update").put("date", 1793880000L)
                .put("url", "https://store.steampowered.com/news/app/" + appId + "/view/123")
                .put("feedname", feed).put("contents", "<p>Hello <b>world</b> &amp; everyone</p>");
        JSONArray entries = new JSONArray().put(item); if (duplicate) entries.put(item);
        return new JSONObject().put("appnews", new JSONObject().put("appid", appId).put("newsitems", entries)).toString();
    }
}
