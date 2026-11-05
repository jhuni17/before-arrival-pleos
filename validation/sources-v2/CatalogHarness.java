package com.beforearrival.briefing;
import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import org.json.*;
public final class CatalogHarness {
  static int assertions;
  static void check(boolean result, String message) { assertions++; if (!result) throw new AssertionError(message); }
  static SharedPreferences prefs(Map<String,Object> data) {
    final SharedPreferences.Editor[] edit = new SharedPreferences.Editor[1];
    edit[0] = (SharedPreferences.Editor) Proxy.newProxyInstance(CatalogHarness.class.getClassLoader(), new Class<?>[]{SharedPreferences.Editor.class}, (p,m,a) -> {
      if (m.getName().startsWith("put")) { data.put((String)a[0], a[1]); return edit[0]; }
      if (m.getName().equals("clear")) { data.clear(); return edit[0]; }
      if (m.getName().equals("commit")) return true;
      return null;
    });
    return (SharedPreferences) Proxy.newProxyInstance(CatalogHarness.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(p,m,a)-> {
      if (m.getName().equals("edit")) return edit[0];
      if (m.getName().equals("contains")) return data.containsKey(a[0]);
      if (m.getName().startsWith("get") && a != null && a.length==2) return data.getOrDefault(a[0],a[1]);
      return null;
    });
  }
  public static void main(String[] args) throws Exception {
    String defaults = Files.readString(Path.of(args[0]));
    Map<String,Object> state = new HashMap<>(); SharedPreferences preferences = prefs(state);
    ContentCatalog c = new ContentCatalog(defaults,preferences); SampleRouteInput route = new SampleRouteInput();
    check(c.sources.size()==3,"default three sources"); check(c.plan(route.snapshot()).selectedSeconds==32,"45 second ETA selects32");
    check(c.plan(route.snapshot()).selected.get(0).sourceId.equals("sample-game"),"default game priority");
    route.select(4); check(c.plan(route.snapshot()).budgetSeconds==12,"short ETA budget12");
    check(c.plan(route.snapshot()).selected.get(0).sourceId.equals("sample-schedule"),"deadline overrides source order");
    ContentCatalog.Source lol=c.add("LoL","lol","https://www.leagueoflegends.com/ko-kr/news/game-updates/");
    check(lol.status.equals("connected_snapshot"),"official imported snapshot"); check(lol.items.size()==1,"snapshot item");
    check(c.canOpen(lol.items.get(0)),"safe official article");
    for(int i=0;i<3;i++)c.move(lol.id,-1); route.select(18);
    check(c.plan(route.snapshot()).selected.get(0).sourceId.equals(lol.id),"user source order");
    check(c.plan(route.snapshot()).narration().startsWith(lol.items.get(0).summary),"spoken catalog selection");
    c.toggleSaved(lol.items.get(0)); c.persist(preferences); c=new ContentCatalog(defaults,preferences);
    check(c.sources.get(0).id.equals(lol.id),"persist order"); check(c.savedItems.size()==1,"persist save");
    check(c.savedItems.get(0).provenance.equals("official_snapshot"),"persist provenance");
    ContentCatalog.Source maple=c.add("메이플","maple","https://maplestory.nexon.com/news/update");
    check(maple.items.get(0).publishedAt.equals("2026-10-22"),"date precision preserved");
    ContentCatalog.Source youtube=c.add("채널","youtube","https://www.youtube.com/@news");
    check(youtube.status.equals("setup_needed")&&youtube.items.isEmpty(),"unsupported does not fabricate");
    ContentCatalog.Source steam=c.add("Steam","steam","https://store.steampowered.com/app/000570/Dota_2/");
    check(steam.registrationUrl.equals("https://store.steampowered.com/news/app/570"),"Steam alias canonicalization");
    try { c.add("dup","steam","https://store.steampowered.com/news/app/570/"); throw new AssertionError("duplicate accepted"); } catch(IllegalArgumentException expected) { assertions++; }
    for(String url:List.of("https://store.steampowered.com/app/0","https://store.steampowered.com/app/4294967296","https://user@store.steampowered.com/app/570","https://store.steampowered.com/app/570#evil","https://store.steampowered.com.evil.test/app/570")) {
      try { c.add("bad","steam",url); throw new AssertionError(url); } catch(IllegalArgumentException expected) { assertions++; }
    }
    JSONObject item = new JSONObject().put("gid","123").put("title","Official news").put("contents","<p>Hello <b>world</b> &amp; everyone</p>")
      .put("url","https://store.steampowered.com/news/app/570/view/123").put("date",1793880000L).put("feedname","steam_community_announcements");
    String fixture=new JSONObject().put("appnews",new JSONObject().put("appid",570).put("newsitems",new JSONArray().put(item).put(item))).toString();
    String parsed=ContentFeedClient.parse(steam.registrationUrl,fixture); Instant now=Instant.parse("2026-11-05T12:12:22Z");
    check(c.importSnapshot(steam.id,parsed,now),"official response imported"); check(steam.items.size()==1,"dedup official response");
    check(steam.items.get(0).summary.equals("Hello world & everyone"),"stripped feed HTML");
    check(steam.status.equals("connected")&&steam.items.get(0).provenance.equals("official_runtime"),"runtime provenance");
    c.markFailure(steam.id,now.plusSeconds(30)); check(steam.items.size()==1&&steam.fetchState.equals("stale"),"failure preserves previous");
    check(steam.lastSuccessAt.equals(now),"failure keeps last success");
    check(!c.importSnapshot(steam.id,"{\"items\":[{}]}",now.plusSeconds(40)),"invalid rejected atomically");
    check(steam.items.size()==1,"invalid preserves previous");
    check(c.importSnapshot(steam.id,"{\"items\":[]}",now.plusSeconds(60)),"empty success");
    check(steam.items.isEmpty()&&steam.fetchState.equals("empty"),"empty clears stale content");
    JSONArray many = new JSONArray();
    for(int i=0;i<25;i++)many.put(new JSONObject().put("id","bulk"+i).put("title","Bulk item").put("summary","Public fixture").put("estimatedSeconds",8).put("originalUrl","https://store.steampowered.com/news/app/570/view/"+(1000+i)));
    check(c.importSnapshot(steam.id,new JSONObject().put("items",many).toString(),now.plusSeconds(70)),"bounded bulk accepted");
    check(steam.items.size()==20,"cache limited to20");
    JSONArray tooMany = new JSONArray();for(int i=0;i<51;i++)tooMany.put(item);
    try {ContentFeedClient.parse(steam.registrationUrl,new JSONObject().put("appnews",new JSONObject().put("appid",570).put("newsitems",tooMany)).toString());throw new AssertionError("oversized item count accepted");}catch(IllegalArgumentException expected){assertions++;}
    state.clear();state.put("enabled_GAME",false);state.put("priority","COMMUNITY");state.put("saved",Set.of("GAME","SCHEDULE","COMMUNITY"));
    c=new ContentCatalog(defaults,preferences); check(c.sources.get(0).id.equals("sample-community"),"legacy priority");
    check(!c.source("sample-game").enabled&&c.savedItems.size()==3,"legacy enabled and saved");
    c.source("sample-game").enabled=true;c.persist(preferences);c=new ContentCatalog(defaults,preferences);
    check(c.source("sample-game").enabled&&c.savedItems.size()==3,"migration runs once");
    JSONObject corrupted=new JSONObject((String)state.get(ContentCatalog.STORAGE_KEY));
    corrupted.getJSONArray("sources").put(new JSONObject().put("id","evil").put("name","evil").put("provider","steam").put("registrationUrl","https://evil.test/app/570"));
    state.put(ContentCatalog.STORAGE_KEY,corrupted.toString()); c=new ContentCatalog(defaults,preferences);
    check(c.source("evil")==null,"corrupted persisted URL rejected");
    System.out.println("PASS "+assertions+" catalog/Steam fixture assertions (public API35 JVM classes; no SDK/device/runtime feed claim)");
  }
}
