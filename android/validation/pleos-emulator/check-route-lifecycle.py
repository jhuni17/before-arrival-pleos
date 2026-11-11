#!/usr/bin/env python3
"""Offline adapter assertions, not an Android/SDK runtime trial (JDK 17+)."""
import json
from pathlib import Path
import shutil
import subprocess
import tempfile

ANDROID = Path(__file__).resolve().parents[2]
SOURCES = [
    'pleos/java/com/beforearrival/briefing/PleosRouteConnection.java',
    *('main/java/com/beforearrival/briefing/' + name + '.java' for name in (
        'NaviTimeContract', 'NavigationRouteInput', 'RouteInput', 'RouteConnection',
        'PleosVerificationTrace')),
]
# Only platform/SDK boundaries are doubled. All route decisions run in SOURCES.
DOUBLES = {
    'android/content/pm/ApplicationInfo.java': '''package android.content.pm;
public class ApplicationInfo { public static final int FLAG_DEBUGGABLE=2; public int flags=2; }''',
    'android/content/pm/PackageManager.java': 'package android.content.pm; public class PackageManager { public static final int PERMISSION_GRANTED=0; }',
    'android/content/Context.java': '''package android.content;
public class Context {
 public final android.content.pm.ApplicationInfo info=new android.content.pm.ApplicationInfo();
 public int checkSelfPermission(String p){return 0;}
 public android.content.pm.ApplicationInfo getApplicationInfo(){return info;}
 public String getPackageName(){return "com.beforearrival.briefing";}
}''',
    'android/os/Build.java': 'package android.os; public class Build { public static String FINGERPRINT; }',
    'android/os/Looper.java': 'package android.os; public class Looper { public static Looper getMainLooper(){return new Looper();} }',
    'android/util/Log.java': '''package android.util; public class Log {
 public static final int DEBUG=3; public static boolean isLoggable(String t,int l){return true;}
 public static int d(String t,String m){return 0;}
 public static int w(String t,String m,Throwable e){return 0;}
}''',
    'android/os/Handler.java': '''package android.os;
public class Handler {
 private record Task(Handler owner,Runnable callback,long due,long order) {}
 private static final java.util.PriorityQueue<Task> queue=new java.util.PriorityQueue<>(
  java.util.Comparator.comparingLong(Task::due).thenComparingLong(Task::order));
 private static long now,sequence;
 public Handler(Looper l){}
 public boolean post(Runnable r){return postDelayed(r,0);}
 public boolean postDelayed(Runnable r,long delay){queue.add(new Task(this,r,now+delay,sequence++));return true;}
 public void removeCallbacks(Runnable r){queue.removeIf(t->t.owner()==this && t.callback()==r);}
 public static int scheduled(){return queue.size();}
 public static void drain(){advanceBy(0);}
 public static void advanceBy(long ms){
  if(ms<0)throw new AssertionError("clock reversal"); long target=now+ms;
  while(!queue.isEmpty() && queue.peek().due()<=target){Task t=queue.remove();now=t.due();t.callback().run();}
  now=target;
 }
}''',
    'kotlin/Unit.java': 'package kotlin; public final class Unit { public static final Unit INSTANCE=new Unit(); }',
    'kotlin/jvm/functions/Function0.java': 'package kotlin.jvm.functions; public interface Function0<R> { R invoke(); }',
    'kotlin/jvm/functions/Function1.java': 'package kotlin.jvm.functions; public interface Function1<P,R> { R invoke(P p); }',
    'ai/pleos/playground/navi/constants/NaviErrorCode.java': 'package ai.pleos.playground.navi.constants; public enum NaviErrorCode { ERROR }',
    'ai/pleos/playground/navi/data/DestinationInfo.java': '''package ai.pleos.playground.navi.data;
public class DestinationInfo { private final int raw; public DestinationInfo(int r){raw=r;} public int getRemainTime(){return raw;} }''',
    'ai/pleos/playground/navi/data/DestinationArrivedInfo.java': 'package ai.pleos.playground.navi.data; public class DestinationArrivedInfo {}',
    'ai/pleos/playground/navi/helper/listener/NaviHelperEventListener.java': '''package ai.pleos.playground.navi.helper.listener;
import ai.pleos.playground.navi.data.*;
import ai.pleos.playground.navi.constants.NaviErrorCode;
public interface NaviHelperEventListener {
 void onDestinationInfo(DestinationInfo i); void onRouteCancelled();
 void onDestinationArrived(DestinationArrivedInfo i); void onDestinationChanged(); void onError(NaviErrorCode c);
}''',
    'ai/pleos/playground/navi/helper/NaviHelper.java': '''package ai.pleos.playground.navi.helper;
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener;
public class NaviHelper {
 public static NaviHelper last;
 public NaviHelperEventListener listener; public int requests,removals,releases;
 private kotlin.jvm.functions.Function0<kotlin.Unit> ready;
 public NaviHelper(android.content.Context c){last=this;}
 public void initialize(kotlin.jvm.functions.Function0<kotlin.Unit> ok,
  kotlin.jvm.functions.Function1<Object,kotlin.Unit> error){ready=ok;}
 public void success(){ready.invoke();}
 public void addListener(NaviHelperEventListener l){if(listener!=null)throw new AssertionError();listener=l;}
 public void getDestinationInfo(){if(listener==null || releases!=0)throw new AssertionError();requests++;}
 public void removeListener(NaviHelperEventListener l){if(l!=listener)throw new AssertionError();listener=null;removals++;}
 public void release(){releases++;}
}''',
}
HARNESS = '''package com.beforearrival.briefing;
import android.os.*;
import ai.pleos.playground.navi.helper.NaviHelper;
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener;
import ai.pleos.playground.navi.data.DestinationInfo;
import java.util.*;
public class RouteLifecycleCheck {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static class Session {
  final List<RouteInput.Snapshot> emitted=new ArrayList<>();
  final NavigationRouteInput route=new NavigationRouteInput(s->{
   check(s.source==RouteInput.Source.NAVI_HELPER,"SAMPLE must never be published");emitted.add(s);
  });
  final PleosRouteConnection connection;
  final NaviHelper sdk;
  final NaviHelperEventListener old;
  Session(String fingerprint,boolean debug){this(new NavigationContext(),fingerprint,debug);}
  Session(NavigationContext context,String fingerprint,boolean debug){
   Build.FINGERPRINT=fingerprint;context.info.flags=debug?2:0;
   check(Handler.scheduled()==0,"previous scenario leaked tasks");
   connection=new PleosRouteConnection(context,route);connection.start();sdk=NaviHelper.last;
   sdk.success();Handler.drain();old=sdk.listener;
   check(old!=null && sdk.requests==1,"initialize must query once");
  }
  void reply(int raw){old.onDestinationInfo(new DestinationInfo(raw));Handler.drain();}
  void eta(long value){check(route.snapshot().remainingSeconds.orElse(-1)==value,"ETA expected "+value);}
  void empty(){check(route.snapshot().remainingSeconds.isEmpty(),"ETA must be empty");}
  void disposed(){check(sdk.removals==1 && sdk.releases==1 && sdk.listener==null,"helper cleanup exactly once");}
  void quiet(){check(Handler.scheduled()==0,"Handler tasks must be cleared");}
  void close(){connection.close();Handler.drain();disposed();quiet();}
 }
 static class NavigationContext extends android.content.Context {}
 public static void main(String[] args){
  String fingerprint=args[0];
  for(int i=1;i<args.length;i++){
   int raw=Integer.parseInt(args[i]);Session s=new Session(fingerprint,true);s.reply(raw);
   if(raw==0){s.empty();s.disposed();s.quiet();}else{s.eta(raw);}
   System.out.println("PASS observed-value replay (independent query): raw="+raw+" -> "+
    (raw==0?"no ETA":"NAVI_HELPER seconds="+raw));s.close();
  }
  for(boolean debug:new boolean[]{true,false}){
   for(String image:new String[]{fingerprint,"unverified-image"}){
   for(int raw:new int[]{1920,1860,1680}){
    Session s=new Session(image,debug);s.reply(raw);
    if(debug && image.equals(fingerprint))s.eta(raw);
    else{s.empty();s.disposed();s.quiet();}
    s.close();
   }
   }
  }
  System.out.println("PASS synthetic gate matrix: verified debug accepted; release/other image rejected");
  Session cancel=new Session(fingerprint,true);cancel.reply(1920);cancel.eta(1920);
  cancel.old.onRouteCancelled();Handler.drain();cancel.empty();cancel.disposed();cancel.quiet();
  int count=cancel.emitted.size();cancel.reply(1860);cancel.empty();
  check(cancel.emitted.size()==count,"late cancellation reply published");
  Handler.advanceBy(60000);cancel.quiet();check(cancel.sdk.requests==1,"cancelled poll survived");cancel.close();
  System.out.println("PASS synthetic cancellation: valid ETA cleared; stale reply ignored; no SAMPLE");
  Session timed=new Session(fingerprint,true);timed.reply(1920);timed.eta(1920);
  Handler.advanceBy(4999);check(timed.sdk.requests==1,"poll before 5000ms");timed.eta(1920);
  Handler.advanceBy(1);check(timed.sdk.requests==2,"poll at 5000ms");
  Handler.advanceBy(9999);timed.eta(1920);check(timed.sdk.requests==2,"duplicate pending query");
  Handler.advanceBy(1);timed.empty();timed.disposed();timed.quiet();
  count=timed.emitted.size();timed.reply(1680);timed.empty();
  check(timed.emitted.size()==count,"late timeout reply published");
  Handler.advanceBy(60000);check(timed.sdk.requests==2,"timeout must stop polling");timed.quiet();timed.close();
  System.out.println("PASS synthetic clock: poll 4999/5000ms; response timeout 9999/10000ms; late reply ignored");
  Session first=new Session(fingerprint,true);first.reply(1920);first.close();
  // A new adapter shares the actual NavigationRouteInput, as a reconnect does.
  Build.FINGERPRINT=fingerprint;
  PleosRouteConnection next=new PleosRouteConnection(new NavigationContext(),first.route);
  next.start();NaviHelper fresh=NaviHelper.last;fresh.success();Handler.drain();
  count=first.emitted.size();first.reply(1860);first.empty();
  check(first.emitted.size()==count && fresh.requests==1,"old listener affected reconnect");
  fresh.listener.onDestinationInfo(new DestinationInfo(1680));Handler.drain();first.eta(1680);
  check(first.emitted.size()==count+1,"new listener did not publish once");
  next.close();Handler.drain();first.empty();first.disposed();
  check(fresh.removals==1 && fresh.releases==1 && fresh.listener==null,"new helper cleanup");
  first.quiet();Handler.advanceBy(60000);check(fresh.requests==1,"closed reconnect polled");
  System.out.println("PASS synthetic reconnect: old listener ignored; new ETA accepted; helpers/tasks cleaned");
 }
}'''


def main():
    if not shutil.which('javac') or not shutil.which('java'):
        raise SystemExit('BLOCKED: JDK 17+ (javac and java) required; no checks executed')
    evidence = json.loads((Path(__file__).with_name('runtime-evidence.json')).read_text())
    fingerprint = evidence['environment']['fingerprint']
    values = [item['rawRemainTime'] for item in evidence['destinationCallbacks']]
    if sorted(values) != [0, 1680, 1860, 1920]:
        raise SystemExit('Unexpected observed fixture values; review evidence before replay')
    print('Observed fixture fingerprint: ' + fingerprint, flush=True)
    with tempfile.TemporaryDirectory(prefix='route-lifecycle-') as directory:
        work = Path(directory)
        files = {**DOUBLES, 'com/beforearrival/briefing/RouteLifecycleCheck.java': HARNESS}
        for name, content in files.items():
            target = work / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content, encoding='utf-8')
        for relative in SOURCES:
            source = ANDROID / 'app/src' / relative
            target = work / 'com/beforearrival/briefing' / source.name
            shutil.copyfile(source, target)
            if source.read_bytes() != target.read_bytes():
                raise AssertionError('Source copy differs: ' + relative)
            print('Compile unchanged production source: android/app/src/' + relative, flush=True)
        inputs = sorted(work.rglob('*.java'))
        subprocess.run(['javac', '--release', '17', '-encoding', 'UTF-8', '-d',
                        str(work / 'classes'), *map(str, inputs)], check=True)
        subprocess.run(['java', '-cp', str(work / 'classes'),
                        'com.beforearrival.briefing.RouteLifecycleCheck', fingerprint,
                        *map(str, values)], check=True)
    print('PASS: offline route adapter checks only; no SDK runtime/UI/audio/APK evidence')


if __name__ == '__main__':
    main()
