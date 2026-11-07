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
        assertEquals(3, catalog.unreadCount());
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

    @Test public void capturedRealCdnFeedReplaysAndRejectsIdentityOrPathChanges() throws Exception {
        String captured;
        try (InputStream input = RuntimeEnvironment.getApplication().getAssets().open("evidence/steam-real-response.json");
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096]; int count;
            while ((count = input.read(chunk)) != -1) bytes.write(chunk, 0, count);
            captured = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
        ContentCatalog.Source source = catalog.add("Dota 2", "steam", "https://store.steampowered.com/news/app/570");
        assertTrue(catalog.importSnapshot(source.id, ContentFeedClient.parse(source.registrationUrl, captured), NOW));
        assertEquals(5, source.items.size());
        assertEquals("Inside the Trove of Terror", source.items.get(0).title);
        assertEquals("https://steamstore-a.akamaihd.net/news/externalpost/steam_community_announcements/1846018067924633", source.items.get(0).originalUrl);
        for (int i = 0; i < 3; i++) catalog.move(source.id, -1);
        SampleRouteInput route = new SampleRouteInput();
        assertEquals(source.id, catalog.plan(route.snapshot()).selected.get(0).sourceId);
        route.select(4); assertEquals("sample-schedule", catalog.plan(route.snapshot()).selected.get(0).sourceId);
        JSONObject first = new JSONObject(captured).getJSONObject("appnews").getJSONArray("newsitems").getJSONObject(0);
        String url = first.getString("url"), gid = first.getString("gid");
        for (String unsafe : java.util.List.of(url.replace(".net/", ".net.evil.test/"), url.replace("steamstore-a.", "other.steamstore-a."),
                url.replace("steam_community_announcements", "pcgamer"), url.replace("/" + gid, "/%31" + gid.substring(1)),
                url.replace("/news/externalpost/", "/news/ignored/../externalpost/"), url + "?token=secret", url + "#fragment",
                url.replace(".net/", ".net:443/"), url.replace("https://", "https://user@"), url.replace(gid, "0"),
                url.replace(gid, "18446744073709551616"))) assertFalse(ContentFeedClient.isOfficialArticle(source.registrationUrl, unsafe, gid));
        for (String key : java.util.List.of("gid", "appid", "url")) {
            JSONObject invalid = new JSONObject(first.toString());
            invalid.put(key, key.equals("gid") ? "999" : key.equals("appid") ? 730 : url.replace(gid, "999"));
            String mixed = new JSONObject().put("appnews", new JSONObject().put("appid", 570)
                    .put("newsitems", new JSONArray().put(first).put(invalid))).toString();
            try { ContentFeedClient.parse(source.registrationUrl, mixed); fail("Invalid mixed batch must be rejected"); }
            catch (IllegalArgumentException expected) { }
            assertEquals(5, source.items.size());
            assertEquals(NOW, source.lastSuccessAt);
        }
    }

    @Test public void deletionRestoresExactDataAndPositionWithoutRevertingInterveningChanges() throws Exception {
        ContentCatalog.Item a = catalog.sources.get(0).items.get(0);
        ContentCatalog.Item b = catalog.sources.get(1).items.get(0);
        ContentCatalog.Item c = catalog.sources.get(2).items.get(0);
        catalog.toggleSaved(a); catalog.toggleSaved(b); catalog.persist(preferences);
        assertTrue(catalog.setConfirmed(a, true, preferences));
        ContentCatalog.DeletedItem deleted = catalog.deleteSaved(a, preferences);
        assertNotNull(deleted);
        catalog.toggleSaved(c); catalog.sources.get(0).enabled = false;
        assertTrue(catalog.setConfirmed(b, true, preferences));
        assertTrue(catalog.restoreDeleted(deleted, preferences));
        assertSame(a, catalog.savedItems.get(0));
        assertEquals(a.json().toString(), catalog.savedItems.get(0).json().toString());
        assertEquals(java.util.List.of(a, b, c), catalog.savedItems);
        assertTrue(catalog.isConfirmed(a)); assertTrue(catalog.isConfirmed(b));
        ContentCatalog restored = new ContentCatalog(defaults, preferences);
        assertEquals(3, restored.savedItems.size()); assertEquals(1, restored.unreadCount());
        assertFalse(restored.sources.get(0).enabled);
        assertEquals(a.json().toString(), restored.savedItems.get(0).json().toString());
        deleted = catalog.deleteSaved(c, preferences);
        assertNotNull(catalog.deleteSaved(b, preferences));
        assertTrue(catalog.restoreDeleted(deleted, preferences));
        assertSame(c, catalog.savedItems.get(1)); // Clamp the old index to the current size.
        deleted = catalog.deleteSaved(a, preferences);
        catalog.toggleSaved(a); catalog.persist(preferences);
        java.util.List<ContentCatalog.Item> before = new java.util.ArrayList<>(catalog.savedItems);
        assertTrue(catalog.restoreDeleted(deleted, preferences));
        assertEquals(before, catalog.savedItems); assertFalse(catalog.isConfirmed(a));
    }

    @Test public void deletionAndRestoreFailuresRollbackEvenWhenCommitMutatesStorage() throws Exception {
        ContentCatalog.Item item = catalog.sources.get(0).items.get(0);
        catalog.toggleSaved(item); assertTrue(catalog.setConfirmed(item, true, preferences));
        String stored = preferences.getString(ContentCatalog.STORAGE_KEY, null);
        assertNull(catalog.deleteSaved(item, failingCommits(preferences, true)));
        assertTrue(catalog.isConfirmed(item)); assertEquals(stored, preferences.getString(ContentCatalog.STORAGE_KEY, null));
        assertNull(catalog.deleteSaved(item, failingCommits(preferences)));
        assertTrue(catalog.isConfirmed(item)); assertEquals(stored, preferences.getString(ContentCatalog.STORAGE_KEY, null));
        ContentCatalog.DeletedItem deleted = catalog.deleteSaved(item, preferences);
        assertNotNull(deleted);
        stored = preferences.getString(ContentCatalog.STORAGE_KEY, null);
        assertFalse(catalog.restoreDeleted(deleted, failingCommits(preferences, true)));
        assertFalse(catalog.isSaved(item)); assertEquals(stored, preferences.getString(ContentCatalog.STORAGE_KEY, null));
        assertFalse(catalog.restoreDeleted(deleted, failingCommits(preferences)));
        assertFalse(catalog.isSaved(item)); assertEquals(stored, preferences.getString(ContentCatalog.STORAGE_KEY, null));
        assertTrue(catalog.restoreDeleted(deleted, preferences));
        assertTrue(new ContentCatalog(defaults, preferences).isConfirmed(item));
    }

    static SharedPreferences failingCommits(SharedPreferences real) {
        return failingCommits(real, false);
    }

    static SharedPreferences failingCommits(SharedPreferences real, boolean throwsAfterWrite) {
        return (SharedPreferences) java.lang.reflect.Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(), new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("edit")) return method.invoke(real, args);
                    SharedPreferences.Editor editor = real.edit();
                    return java.lang.reflect.Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),
                            new Class<?>[]{SharedPreferences.Editor.class}, (ep, em, ea) -> {
                                if (em.getName().equals("commit")) {
                                    editor.commit();
                                    if (throwsAfterWrite) throw new IllegalStateException("storage failure");
                                    return false;
                                }
                                Object result = em.invoke(editor, ea);
                                return result == editor ? ep : result;
                            });
                });
    }

    @Test public void existingSavedJsonWithoutConfirmationStartsUnread() throws Exception {
        catalog.toggleSaved(catalog.sources.get(0).items.get(0));
        catalog.persist(preferences);
        JSONObject stored = new JSONObject(preferences.getString(ContentCatalog.STORAGE_KEY, ""));
        stored.getJSONArray("savedItems").getJSONObject(0).remove("confirmed");
        preferences.edit().putString(ContentCatalog.STORAGE_KEY, stored.toString()).commit();
        catalog = new ContentCatalog(defaults, preferences);
        assertEquals(1, catalog.unreadCount());
        assertFalse(catalog.isConfirmed(catalog.savedItems.get(0)));
    }

    @Test public void confirmationIsSavedOnlyDurableReversibleAndAtomic() throws Exception {
        ContentCatalog.Item item = catalog.sources.get(0).items.get(0);
        ContentCatalog.Item second = catalog.sources.get(1).items.get(0);
        catalog.toggleSaved(item); catalog.toggleSaved(second); catalog.persist(preferences);
        String source = catalog.sources.get(0).json().toString();
        String payload = item.json().toString();
        String narration = catalog.plan(new SampleRouteInput().snapshot()).narration();
        assertEquals(2, catalog.unreadCount());
        assertFalse(catalog.setConfirmed(item, true, failingCommits(preferences)));
        assertFalse(catalog.isConfirmed(item)); assertEquals(2, catalog.unreadCount());
        assertTrue(catalog.setConfirmed(item, true, preferences));
        assertEquals(1, catalog.unreadCount());
        assertEquals(source, catalog.sources.get(0).json().toString());
        assertEquals(payload, catalog.savedItems.get(0).json().toString());
        assertEquals(second.key(), catalog.savedItems.get(1).key());
        assertEquals(narration, catalog.plan(new SampleRouteInput().snapshot()).narration());
        catalog = new ContentCatalog(defaults, preferences);
        assertTrue(catalog.isConfirmed(item)); assertEquals(1, catalog.unreadCount());
        assertTrue(catalog.setConfirmed(item, false, preferences));
        catalog = new ContentCatalog(defaults, preferences);
        assertFalse(catalog.isConfirmed(item)); assertEquals(2, catalog.unreadCount());
        catalog.setConfirmed(item, true, preferences);
        catalog.toggleSaved(item);
        assertFalse(catalog.setConfirmed(item, true, preferences));
        assertEquals(1, catalog.savedItems.size());
        catalog.toggleSaved(item);
        assertFalse(catalog.isConfirmed(item)); assertEquals(2, catalog.unreadCount());
    }

    private static String steamResponse(int appId, String feed, boolean duplicate) throws Exception {
        JSONObject item = new JSONObject().put("gid", "123").put("title", "Official update").put("date", 1793880000L)
                .put("url", "https://store.steampowered.com/news/app/" + appId + "/view/123")
                .put("feedname", feed).put("contents", "<p>Hello <b>world</b> &amp; everyone</p>");
        JSONArray entries = new JSONArray().put(item); if (duplicate) entries.put(item);
        return new JSONObject().put("appnews", new JSONObject().put("appid", appId).put("newsitems", entries)).toString();
    }
}
