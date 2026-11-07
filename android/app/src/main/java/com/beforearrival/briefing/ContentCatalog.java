package com.beforearrival.briefing;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

/** Local catalog; registration never claims to fetch a provider's news. */
final class ContentCatalog {
    static final String STORAGE_KEY = "catalog_v2";
    static final String LOCAL_NOTICE = "이 Android 앱에만 저장 · 브라우저/다른 기기와 동기화 안 됨";
    private JSONArray snapshots;
    private final Set<String> confirmedKeys = new HashSet<>();
    final List<Provider> providers = new ArrayList<>();
    final List<Source> sources = new ArrayList<>();
    final List<Item> savedItems = new ArrayList<>();

    static final class Provider {
        final String id, label, kind, urlPattern, suggestedUrl;
        Provider(JSONObject json) {
            id = json.optString("id"); label = json.optString("label");
            kind = json.optString("kind"); urlPattern = json.optString("urlPattern"); suggestedUrl = json.optString("suggestedUrl");
        }
        boolean accepts(String url) {
            if (url == null || url.length() > 512 || urlPattern.isEmpty()) return false;
            try {
                URI uri = new URI(url);
                return "https".equals(uri.getScheme()) && uri.getHost() != null
                        && uri.getRawUserInfo() == null && uri.getRawFragment() == null
                        && (uri.getPort() == -1 || uri.getPort() == 443) && url.matches(urlPattern);
            } catch (Exception invalid) { return false; }
        }
        @Override public String toString() { return label; }
    }

    static final class Item {
        final String id, sourceId, title, originalUrl, summary, provenance, publishedAt, publishedPrecision;
        final Instant deadlineAt, fetchedAt;
        final int estimatedSeconds;
        Item(JSONObject json) {
            id = json.optString("id"); sourceId = json.optString("sourceId"); title = json.optString("title");
            originalUrl = nullable(json, "originalUrl"); summary = json.optString("summary");
            provenance = json.optString("provenance"); estimatedSeconds = json.optInt("estimatedSeconds");
            publishedAt = nullable(json, "publishedAt"); publishedPrecision = json.optString("publishedPrecision", "unknown"); deadlineAt = instant(json, "deadlineAt");
            fetchedAt = instant(json, "fetchedAt");
        }
        String key() { return originalUrl == null ? sourceId + ":" + id : originalUrl; }
        boolean urgent(Instant now) {
            if (deadlineAt == null) return false;
            Duration remaining = Duration.between(now, deadlineAt);
            return !remaining.isNegative() && remaining.compareTo(Duration.ofMinutes(10)) <= 0;
        }
        JSONObject json() throws JSONException {
            return new JSONObject().put("id", id).put("sourceId", sourceId).put("title", title)
                    .put("originalUrl", value(originalUrl)).put("summary", summary)
                    .put("provenance", provenance).put("estimatedSeconds", estimatedSeconds)
                    .put("publishedAt", value(publishedAt)).put("publishedPrecision", publishedPrecision).put("deadlineAt", value(deadlineAt))
                    .put("fetchedAt", value(fetchedAt));
        }
    }

    static final class Source {
        final String id, name, provider, kind, registrationUrl;
        String status, fetchState, failureMessage;
        Instant lastAttemptAt, lastSuccessAt;
        boolean enabled;
        final List<Item> items = new ArrayList<>();
        Source(JSONObject json) {
            id = json.optString("id"); name = json.optString("name"); provider = json.optString("provider");
            kind = json.optString("kind"); registrationUrl = nullable(json, "registrationUrl");
            status = json.optString("status", "setup_needed"); enabled = json.optBoolean("enabled", true);
            fetchState = json.optString("fetchState", "idle"); failureMessage = json.optString("failureMessage");
            lastAttemptAt = instant(json, "lastAttemptAt"); lastSuccessAt = instant(json, "lastSuccessAt");
            JSONArray entries = json.optJSONArray("items");
            Set<String> seen = new HashSet<>();
            if (entries != null) for (int i = 0; i < Math.min(50, entries.length()); i++) {
                JSONObject entry = entries.optJSONObject(i);
                if (entry == null) continue;
                Item item = new Item(entry);
                if (validItem(item, id) && seen.add(item.key())) items.add(item);
            }
        }
        String statusLabel() {
            if ("stale".equals(fetchState)) return items.isEmpty() ? "가져오기 실패 · 연결된 소식 없음" : "가져오기 실패 · 이전에 가져온 소식 유지";
            if ("empty".equals(fetchState)) return "connected".equals(status) ? "공식 소식 확인 완료 · 새 소식 없음" : "가져온 공식 소식 없음 · 자동 갱신 아님";
            if ("connected".equals(status)) return "공식 소식 연결 · 가져오기 버튼으로 갱신";
            if ("connected_snapshot".equals(status)) return "가져온 공식 소식 · 자동 갱신 아님";
            if ("demo_sample".equals(status)) return "예시 콘텐츠";
            if ("link_saved".equals(status)) return "링크 저장 · 자동 연결 미확인";
            return "설정 필요 · 연결된 소식 없음";
        }
        JSONObject json() throws JSONException {
            JSONArray entries = new JSONArray();
            for (Item item : items) entries.put(item.json());
            return new JSONObject().put("id", id).put("name", name).put("provider", provider).put("kind", kind)
                    .put("registrationUrl", value(registrationUrl)).put("status", status).put("enabled", enabled)
                    .put("items", entries).put("fetchState", fetchState).put("failureMessage", failureMessage)
                    .put("lastAttemptAt", value(lastAttemptAt)).put("lastSuccessAt", value(lastSuccessAt)).put("refreshError", failureMessage);
        }
    }

    static final class Plan {
        final int budgetSeconds, selectedSeconds;
        final List<Item> selected, deferred;
        Plan(int budget, int seconds, List<Item> selected, List<Item> deferred) {
            budgetSeconds = budget; selectedSeconds = seconds;
            this.selected = Collections.unmodifiableList(selected); this.deferred = Collections.unmodifiableList(deferred);
        }
        String narration() {
            StringJoiner text = new StringJoiner(" ");
            for (Item item : selected) text.add(item.summary);
            return text.toString();
        }
    }

    ContentCatalog(String defaults, SharedPreferences preferences) throws JSONException {
        JSONObject base = new JSONObject(defaults);
        snapshots = base.optJSONArray("snapshots");
        JSONArray providerList = base.getJSONArray("providers");
        for (int i = 0; i < providerList.length(); i++) providers.add(new Provider(providerList.getJSONObject(i)));
        JSONObject content = base;
        boolean migrated = true;
        try {
            JSONObject stored = new JSONObject(preferences.getString(STORAGE_KEY, ""));
            if (stored.optInt("schemaVersion") == 2 && stored.optJSONArray("sources") != null) {
                content = stored; migrated = false;
            }
        } catch (JSONException absent) { /* First launch or unreadable local state uses the shared defaults. */ }
        JSONArray sourceList = content.getJSONArray("sources");
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < Math.min(100, sourceList.length()); i++) {
            JSONObject sourceJson = sourceList.optJSONObject(i);
            if (sourceJson == null) continue;
            Source source = new Source(sourceJson);
            Provider registeredProvider = provider(source.provider);
            boolean sample = "sample".equals(source.provider)
                    && java.util.Arrays.asList("sample-game", "sample-schedule", "sample-community").contains(source.id);
            boolean registered = registeredProvider != null && registeredProvider.accepts(source.registrationUrl);
            if (registered && "steam".equals(source.provider)) {
                try { ContentFeedClient.appId(source.registrationUrl); } catch (IllegalArgumentException invalid) { registered = false; }
            }
            if (!source.id.isEmpty() && source.name.length() <= 80 && (sample || registered) && ids.add(source.id)) {
                source.items.removeIf(item -> sample ? !"demo_sample".equals(item.provenance)
                        : "demo_sample".equals(item.provenance) || !acceptsArticle(registeredProvider, source.registrationUrl, item.originalUrl));
                sources.add(source);
            }
        }
        JSONArray saved = content.optJSONArray("savedItems");
        if (saved != null) for (int i = 0; i < Math.min(500, saved.length()); i++) {
            JSONObject itemJson = saved.optJSONObject(i);
            if (itemJson == null) continue;
            Item item = new Item(itemJson);
            if (source(item.sourceId) != null && validItem(item, item.sourceId) && !isSaved(item)
                    && ("demo_sample".equals(item.provenance) ? "sample".equals(source(item.sourceId).provider) : canOpen(item))) {
                savedItems.add(item);
                if (itemJson.optBoolean("confirmed", false)) confirmedKeys.add(item.key());
            }
        }
        if (migrated) migrate(preferences);
        persist(preferences);
    }

    private void migrate(SharedPreferences preferences) {
        for (String legacy : new String[]{"GAME", "SCHEDULE", "COMMUNITY"}) {
            Source source = source("sample-" + legacy.toLowerCase(java.util.Locale.ROOT));
            if (source == null) continue;
            source.enabled = preferences.getBoolean("enabled_" + legacy, source.enabled);
            if (preferences.getStringSet("saved", Collections.emptySet()).contains(legacy) && !source.items.isEmpty()) {
                savedItems.add(source.items.get(0));
            }
        }
        String priority = preferences.getString("priority", "GAME");
        Source preferred = source("sample-" + priority.toLowerCase(java.util.Locale.ROOT));
        if (preferred != null) { sources.remove(preferred); sources.add(0, preferred); }
    }

    Source source(String id) { for (Source source : sources) if (source.id.equals(id)) return source; return null; }
    Provider provider(String id) { for (Provider provider : providers) if (provider.id.equals(id)) return provider; return null; }
    boolean canOpen(Item item) {
        Source source = source(item.sourceId);
        Provider provider = source == null ? null : provider(source.provider);
        return ("official_snapshot".equals(item.provenance) || "official_runtime".equals(item.provenance))
                && provider != null && acceptsArticle(provider, source.registrationUrl, item.originalUrl);
    }
    Source add(String name, String providerId, String url) throws JSONException {
        name = name == null ? "" : name.trim(); url = url == null ? "" : url.trim();
        Provider provider = provider(providerId);
        if (sources.size() >= 100) throw new IllegalArgumentException("출처는 최대 100개까지 등록할 수 있어요.");
        if (name.isEmpty() || name.length() > 80) throw new IllegalArgumentException("출처 이름을 1~80자로 입력해 주세요.");
        if (provider == null || !provider.accepts(url)) throw new IllegalArgumentException("선택한 제공자의 공식 HTTPS 주소를 입력해 주세요.");
        url = "steam".equals(provider.id) ? "https://store.steampowered.com/news/app/" + ContentFeedClient.appId(url) : canonicalUrl(url);
        for (Source source : sources) if (url.equals(canonicalUrl(source.registrationUrl))) throw new IllegalArgumentException("이미 등록한 주소예요.");
        Source source = new Source(new JSONObject().put("id", "url:" + url)
                .put("name", name).put("provider", provider.id).put("kind", provider.kind)
                .put("registrationUrl", url).put("status",
                        java.util.Arrays.asList("youtube", "chzzk", "navercafe").contains(provider.id) ? "setup_needed" : "link_saved").put("enabled", true));
        sources.add(source);
        if (snapshots != null) for (int i = 0; i < snapshots.length(); i++) {
            JSONObject snapshot = snapshots.optJSONObject(i);
            if (snapshot != null && provider.id.equals(snapshot.optString("provider"))
                    && url.equals(canonicalUrl(snapshot.optString("registrationUrl")))) {
                JSONArray items = snapshot.optJSONArray("items");
                if (items != null && items.length() > 0) {
                    Instant fetched = instant(items.optJSONObject(0), "fetchedAt");
                    importSnapshot(source.id, snapshot.toString(), fetched == null ? Instant.now() : fetched, false);
                }
            }
        }
        return source;
    }
    void move(String id, int direction) {
        Source source = source(id); int index = sources.indexOf(source); int target = index + direction;
        if (source != null && target >= 0 && target < sources.size()) Collections.swap(sources, index, target);
    }
    boolean isSaved(Item item) { for (Item saved : savedItems) if (saved.key().equals(item.key())) return true; return false; }
    void toggleSaved(Item item) {
        for (int i = 0; i < savedItems.size(); i++) if (savedItems.get(i).key().equals(item.key())) { savedItems.remove(i); confirmedKeys.remove(item.key()); return; }
        savedItems.add(item);
    }
    static final class DeletedItem {
        final Item item;
        final int index;
        final boolean confirmed;
        DeletedItem(Item item, int index, boolean confirmed) {
            this.item = item; this.index = index; this.confirmed = confirmed;
        }
    }
    DeletedItem deleteSaved(Item item, SharedPreferences preferences) {
        for (int i = 0; i < savedItems.size(); i++) {
            Item saved = savedItems.get(i);
            if (!saved.key().equals(item.key())) continue;
            DeletedItem deleted = new DeletedItem(saved, i, isConfirmed(saved));
            return changeSaved(preferences, () -> {
                savedItems.remove(saved); confirmedKeys.remove(saved.key());
            }) ? deleted : null;
        }
        return null;
    }
    boolean restoreDeleted(DeletedItem deleted, SharedPreferences preferences) {
        if (isSaved(deleted.item)) return true;
        return changeSaved(preferences, () -> {
            savedItems.add(Math.min(deleted.index, savedItems.size()), deleted.item);
            if (deleted.confirmed) confirmedKeys.add(deleted.item.key());
        });
    }
    private boolean changeSaved(SharedPreferences preferences, Runnable change) {
        List<Item> previous = new ArrayList<>(savedItems);
        Set<String> confirmed = new HashSet<>(confirmedKeys);
        String oldStorage;
        try { oldStorage = preferences.getString(STORAGE_KEY, null); }
        catch (RuntimeException failure) { return false; }
        try {
            change.run();
            if (preferences.edit().putString(STORAGE_KEY, storageJson()).commit()) return true;
        } catch (JSONException | RuntimeException failure) { /* Restore and allow retry. */ }
        savedItems.clear(); savedItems.addAll(previous);
        confirmedKeys.clear(); confirmedKeys.addAll(confirmed);
        try {
            if (oldStorage == null) preferences.edit().remove(STORAGE_KEY).commit();
            else preferences.edit().putString(STORAGE_KEY, oldStorage).commit();
        } catch (RuntimeException failure) { /* Best effort restoration; no success is reported. */ }
        return false;
    }
    boolean isConfirmed(Item item) { return isSaved(item) && confirmedKeys.contains(item.key()); }
    int unreadCount() {
        int count = 0;
        for (Item item : savedItems) if (!confirmedKeys.contains(item.key())) count++;
        return count;
    }
    boolean setConfirmed(Item item, boolean confirmed, SharedPreferences preferences) {
        if (!isSaved(item)) return false;
        boolean previous = isConfirmed(item);
        String oldStorage = preferences.getString(STORAGE_KEY, null);
        if (confirmed) confirmedKeys.add(item.key()); else confirmedKeys.remove(item.key());
        try {
            if (preferences.edit().putString(STORAGE_KEY, storageJson()).commit()) return true;
        } catch (JSONException | RuntimeException failure) { /* Keep the prior visible state and allow retry. */ }
        if (previous) confirmedKeys.add(item.key()); else confirmedKeys.remove(item.key());
        // A failed SharedPreferences commit can still update its in-memory map.
        try {
            if (oldStorage == null) preferences.edit().remove(STORAGE_KEY).commit();
            else preferences.edit().putString(STORAGE_KEY, oldStorage).commit();
        } catch (RuntimeException failure) { /* The operation remains unsuccessful. */ }
        return false;
    }
    Plan plan(RouteInput.Snapshot route) {
        long seconds = route.remainingSeconds.orElse(0);
        int budget = (int) Math.min(45, Math.max(0, seconds - 180) / 5);
        List<Item> candidates = new ArrayList<>(); Set<String> seen = new HashSet<>();
        for (Source source : sources) if (source.enabled) for (Item item : source.items) if (seen.add(item.key())) candidates.add(item);
        candidates.sort((left, right) -> Boolean.compare(right.urgent(route.observedAt), left.urgent(route.observedAt)));
        List<Item> selected = new ArrayList<>(), deferred = new ArrayList<>(); int duration = 0;
        for (Item item : candidates) {
            if (item.estimatedSeconds <= budget - duration) { selected.add(item); duration += item.estimatedSeconds; }
            else deferred.add(item);
        }
        return new Plan(budget, duration, selected, deferred);
    }
    /** Atomic official snapshot replacement. Failed/invalid attempts retain the last known good snapshot. */
    boolean importSnapshot(String sourceId, String snapshot, Instant observedAt) {
        return importSnapshot(sourceId, snapshot, observedAt, true);
    }
    boolean importSnapshot(String sourceId, String snapshot, Instant observedAt, boolean runtime) {
        Source source = source(sourceId);
        if (source == null || "sample".equals(source.provider)) return false;
        try {
            Provider provider = provider(source.provider);
            if (provider == null) throw new IllegalArgumentException();
            JSONObject json = new JSONObject(snapshot); JSONArray entries = json.getJSONArray("items");
            if (entries.length() > 50) throw new IllegalArgumentException();
            List<Item> imported = new ArrayList<>(); Set<String> seen = new HashSet<>();
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = new JSONObject(entries.getJSONObject(i).toString());
                entry.put("sourceId", source.id).put("provenance", runtime ? "official_runtime" : "official_snapshot").put("fetchedAt", observedAt.toString());
                Item item = new Item(entry);
                if (!validItem(item, source.id) || !acceptsArticle(provider, source.registrationUrl, item.originalUrl)) throw new IllegalArgumentException();
                if (seen.add(item.key())) imported.add(item);
            }
            if (imported.size() > 20) imported = new ArrayList<>(imported.subList(0, 20));
            source.items.clear(); source.items.addAll(imported); source.status = runtime ? "connected" : "connected_snapshot";
            source.fetchState = imported.isEmpty() ? "empty" : "ready"; source.lastAttemptAt = observedAt; source.lastSuccessAt = observedAt;
            source.failureMessage = ""; return true;
        } catch (Exception invalid) { markFailure(sourceId, observedAt); return false; }
    }
    void markFailure(String sourceId, Instant observedAt) {
        Source source = source(sourceId); if (source == null) return;
        source.fetchState = "stale"; source.lastAttemptAt = observedAt; source.failureMessage = "소식을 가져오지 못했어요.";
    }
    private String storageJson() throws JSONException {
        JSONArray list = new JSONArray(), saved = new JSONArray();
        for (Source source : sources) list.put(source.json());
        for (Item item : savedItems) saved.put(item.json().put("confirmed", isConfirmed(item)));
        return new JSONObject().put("schemaVersion", 2).put("sources", list).put("savedItems", saved).toString();
    }
    void persist(SharedPreferences preferences) {
        try { preferences.edit().putString(STORAGE_KEY, storageJson()).apply(); }
        catch (JSONException impossible) { throw new IllegalStateException(impossible); }
    }
    static String canonicalUrl(String url) {
        if (url == null) return null;
        try {
            URI uri = new URI(url);
            if (uri.getRawQuery() == null && uri.getPath() != null && uri.getPath().length() > 1 && url.endsWith("/"))
                return url.substring(0, url.length() - 1);
        } catch (Exception ignored) { }
        return url;
    }
    private static boolean acceptsArticle(Provider provider, String registration, String url) {
        if (!"steam".equals(provider.id)) return provider.accepts(url);
        return ContentFeedClient.isOfficialArticle(registration, url);
    }
    private static boolean validItem(Item item, String sourceId) {
        return !item.id.trim().isEmpty() && item.id.length() <= 300 && item.sourceId.equals(sourceId) && !item.title.trim().isEmpty()
                && item.title.length() <= 500 && !item.summary.trim().isEmpty() && item.summary.length() <= 2000 && item.estimatedSeconds > 0 && item.estimatedSeconds <= 3600
                && ("demo_sample".equals(item.provenance) || "official_snapshot".equals(item.provenance) || "official_runtime".equals(item.provenance));
    }
    private static String nullable(JSONObject json, String key) { return json.isNull(key) ? null : json.optString(key, null); }
    private static Instant instant(JSONObject json, String key) {
        String raw = nullable(json, key); if (raw == null || raw.isEmpty()) return null;
        try { return Instant.parse(raw); } catch (Exception invalid) { return null; }
    }
    private static Object value(Object value) { return value == null ? JSONObject.NULL : value.toString(); }
}
