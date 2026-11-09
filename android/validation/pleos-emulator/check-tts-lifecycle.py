#!/usr/bin/env python3
"""Compile the real candidate against deterministic doubles; no SDK/audio trial."""
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
FILES = {
    'android/content/Context.java': 'package android.content; public class Context { public String getPackageName(){return "com.beforearrival.briefing";} }',
    'android/os/Looper.java': 'package android.os; public class Looper { public static Looper getMainLooper(){return new Looper();} }',
    'android/os/SystemClock.java': 'package android.os; public class SystemClock { public static long elapsedRealtime(){return Handler.now;} }',
    'android/util/Log.java': 'package android.util; public class Log { public static int count; public static int d(String a,String b){count++;return 0;} }',
    'android/os/Handler.java': '''package android.os;
public class Handler {
 private record Task(Handler owner, Runnable callback, long due, long order) {}
 private static final java.util.PriorityQueue<Task> queue = new java.util.PriorityQueue<>(
     java.util.Comparator.comparingLong(Task::due).thenComparingLong(Task::order));
 public static long now;
 private static long sequence;
 public Handler(Looper l) {}
 public boolean post(Runnable r){return postDelayed(r,0);}
 public boolean postDelayed(Runnable r,long delay){queue.add(new Task(this,r,now+delay,sequence++));return true;}
 public void removeCallbacksAndMessages(Object token){
  if(token!=null)throw new AssertionError("only null token is supported");
  queue.removeIf(task -> task.owner()==this);
 }
 public static void drain(){advanceBy(0);}
 public static void advanceBy(long milliseconds){
  if(milliseconds<0)throw new AssertionError("time cannot go backwards");
  long target=now+milliseconds;
  while(!queue.isEmpty() && queue.peek().due()<=target){
   Task task=queue.remove();now=task.due();task.callback().run();
  }
  now=target;
 }
}''',
    'android/media/AudioAttributes.java': '''package android.media; public class AudioAttributes {
 public static final int USAGE_MEDIA=1, CONTENT_TYPE_SPEECH=1;
 public static class Builder { public Builder setUsage(int n){return this;}
 public Builder setContentType(int n){return this;} public AudioAttributes build(){return new AudioAttributes();} }
}''',
    'ai/pleos/playground/tts/constant/Mode.java': 'package ai.pleos.playground.tts.constant; public enum Mode { ON_DEVICE }',
    'ai/pleos/playground/tts/constant/ErrorType.java': 'package ai.pleos.playground.tts.constant; public enum ErrorType { ERROR }',
    'ai/pleos/playground/tts/listener/OnInitializeListener.java': '''package ai.pleos.playground.tts.listener;
import ai.pleos.playground.tts.constant.ErrorType;
public interface OnInitializeListener { void onSuccess(); void onFailure(ErrorType e); }''',
    'ai/pleos/playground/tts/listener/EventListener.java': '''package ai.pleos.playground.tts.listener;
import ai.pleos.playground.tts.constant.ErrorType;
public interface EventListener { void onReady(); void onStart(); void onDone(); void onStop(); void onError(ErrorType e); void onUpdatedRms(double r); }''',
    'ai/pleos/playground/tts/TextToSpeech.java': '''package ai.pleos.playground.tts;
import ai.pleos.playground.tts.listener.*;
import ai.pleos.playground.tts.constant.Mode;
public class TextToSpeech {
 public static TextToSpeech last; public static boolean synchronous;
 public OnInitializeListener init; public EventListener events;
 public boolean storage, initialized, registered, locale, attributes, released;
 public int speaks; public String spoken, speaker;
 public TextToSpeech(android.content.Context c, Mode m){if(m!=Mode.ON_DEVICE)throw new AssertionError();last=this;}
 public void initialize(OnInitializeListener l){init=l;storage=true;if(synchronous)success();}
 public void success(){initialized=true;init.onSuccess();}
 public void addEventListener(EventListener e){if(!storage)throw new AssertionError("listener before initialize");events=e;registered=true;}
 public void setLocale(java.util.Locale l){locale=java.util.Locale.KOREA.equals(l);}
 public void setSpeakerType(String s){speaker=s;}
 public void setAudioAttributes(android.media.AudioAttributes a){attributes=true;}
 public void speak(String s,boolean end){if(!initialized||!registered||!locale||!attributes||!"seoyoon".equals(speaker)||!end||released)throw new AssertionError("premature speak");speaks++;spoken=s;}
 public void stop(){} public void removeEventListener(EventListener e){registered=false;}
 public void release(){released=true;}
}''',
    'com/beforearrival/briefing/BuildConfig.java': 'package com.beforearrival.briefing; final class BuildConfig { static final boolean DEBUG=Boolean.parseBoolean(System.getProperty("debug", "true")); static final String TTS_COMPARISON=System.getProperty("comparison", "off"); }',
    'com/beforearrival/briefing/LifecycleCheck.java': '''package com.beforearrival.briefing;
import ai.pleos.playground.tts.TextToSpeech;
import ai.pleos.playground.tts.listener.EventListener;
import ai.pleos.playground.tts.constant.ErrorType;
import android.os.Handler;
public class LifecycleCheck {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  String mode=BuildConfig.TTS_COMPARISON;
  check(ComparisonFactory.productionBackend(),"Pleos production backend required");
  check(ComparisonFixture.enabled(new android.content.Context()) == (BuildConfig.DEBUG && !"off".equals(mode)), "comparison must be explicit and debug only");
  check(!ComparisonFixture.enabled(new android.content.Context(){public String getPackageName(){return "com.beforearrival.briefing.sample";}}), "sample must never inject comparison fixture");
  for(boolean sync:new boolean[]{false,true}){
   TextToSpeech.synchronous=sync; int[] callbacks={0,0,0,0};
   ComparisonSpeech candidate=ComparisonFactory.create(new android.content.Context(),()->callbacks[0]++,()->callbacks[1]++,()->callbacks[2]++);
   TextToSpeech sdk=TextToSpeech.last;
   if(sync) Handler.drain();
   candidate.start("first");
   if(!sync){check(sdk.speaks==0,"must wait for success");sdk.success();Handler.drain();}
   check(sdk.speaks==1 && "first".equals(sdk.spoken),"first request without onReady");
   sdk.success(); sdk.events.onReady(); sdk.events.onReady(); Handler.drain();
   check(sdk.speaks==1,"duplicate success/ready must not resubmit");
   EventListener old=sdk.events;
   candidate.stop(()->callbacks[3]++);
   old.onStart();old.onDone();Handler.drain();
   check(callbacks[0]==0 && callbacks[1]==0 && callbacks[3]==0,"stopping must wait for onStop");
   old.onStop();old.onStop();Handler.drain();
   check(callbacks[3]==1 && sdk.released,"duplicate stop completes once");
   Handler.advanceBy(5001);
   check(callbacks[2]==0 && callbacks[3]==1,"successful stop must cancel timeout");
   ComparisonSpeech next=ComparisonFactory.create(new android.content.Context(),()->callbacks[0]++,()->callbacks[1]++,()->callbacks[2]++);
   next.start("next"); if(!sync)TextToSpeech.last.success();Handler.drain();
   old.onStart();old.onDone();old.onError(ErrorType.ERROR);old.onReady();sdk.success();Handler.drain();
   check(callbacks[0]==0 && callbacks[1]==0 && callbacks[2]==0,"released callbacks must be ignored");
   check(TextToSpeech.last.speaks==1,"old callbacks must not affect next instance");next.release();
  }
  TextToSpeech.synchronous=false;
  ComparisonSpeech cancelled=ComparisonFactory.create(new android.content.Context(),()->{throw new AssertionError();},()->{throw new AssertionError();},()->{throw new AssertionError();});
  TextToSpeech sdk=TextToSpeech.last;cancelled.start("cancelled");cancelled.release();sdk.success();sdk.events.onReady();Handler.drain();
  check(sdk.speaks==0,"late initialization after release must not speak");
  Handler.advanceBy(5001);
  for(boolean sync:new boolean[]{false,true}){
   TextToSpeech.synchronous=sync;
   int[] failures={0}, continuations={0};
   ComparisonSpeech timed=ComparisonFactory.create(new android.content.Context(),
       ()->{throw new AssertionError("unexpected start callback");},
       ()->{throw new AssertionError("unexpected done callback");},()->failures[0]++);
   TextToSpeech timedSdk=TextToSpeech.last;
   timed.start("timeout");if(!sync)timedSdk.success();Handler.drain();
   check(timedSdk.speaks==1,"timeout case must have submitted speech");
   EventListener timedEvents=timedSdk.events;
   timed.stop(()->continuations[0]++);
   Handler.drain();
   check(failures[0]==0 && continuations[0]==0,"stop must not fail immediately");
   Handler.advanceBy(4999);
   check(failures[0]==0 && continuations[0]==0,"must wait the full five seconds");
   Handler.advanceBy(1);
   check(failures[0]==1 && continuations[0]==0,"missing onStop must fail without next topic");
   // Model the service's failure cleanup explicitly; this is not a service test.
   timed.release();
   timedEvents.onStop();timedEvents.onError(ErrorType.ERROR);Handler.drain();
   Handler.advanceBy(5001);
   check(failures[0]==1 && continuations[0]==0,"released timeout case ignores late callbacks");

   int[] releasedFailures={0}, liveFailures={0};
   ComparisonSpeech released=ComparisonFactory.create(new android.content.Context(),()->{},()->{},()->releasedFailures[0]++);
   TextToSpeech releasedSdk=TextToSpeech.last;
   released.start("release before timeout");if(!sync)releasedSdk.success();Handler.drain();
   released.stop(()->{throw new AssertionError("released instance continued");});
   Handler.advanceBy(1000);
   ComparisonSpeech live=ComparisonFactory.create(new android.content.Context(),()->{},()->{},()->liveFailures[0]++);
   TextToSpeech liveSdk=TextToSpeech.last;
   live.start("independent owner");if(!sync)liveSdk.success();Handler.drain();
   live.stop(()->{throw new AssertionError("missing onStop continued");});
   released.release();
   Handler.advanceBy(4000);
   check(releasedFailures[0]==0 && liveFailures[0]==0,"released timeout is cancelled, live timeout not due");
   Handler.advanceBy(999);
   check(liveFailures[0]==0,"live owner's timeout must retain its deadline");
   Handler.advanceBy(1);
   check(releasedFailures[0]==0 && liveFailures[0]==1,"release must preserve another owner's timeout");
   live.release();Handler.advanceBy(5001);
   check(releasedFailures[0]==0 && liveFailures[0]==1,"cleanup must not produce later failures");
  }
  check((android.util.Log.count>0)==(BuildConfig.DEBUG && !"off".equals(mode)), "comparison logs must be opt-in debug only");
  System.out.println("PASS: candidate lifecycle (sync/async initialization, no onReady, duplicate/stale callbacks, five-second stop timeout, success/release cancellation, owner-specific cleanup)");
 }
}''',
}


def check_permission_sources():
    """Source-set check only; Gradle merged manifests remain a separate gate."""
    import xml.etree.ElementTree as ET
    source = ROOT / 'app/src'
    permission = 'pleos.car.permission.TTS_SERVICE'
    name = '{http://schemas.android.com/apk/res/android}name'
    for flavor in ['pleos', 'sample']:
        for build_type in ['Debug', 'Release']:
            declarations = []
            for layer in ['main', flavor, build_type.lower(), flavor + build_type]:
                manifest = source / layer / 'AndroidManifest.xml'
                if manifest.exists():
                    declarations.extend(node.get(name) for node in
                                        ET.parse(manifest).getroot().findall('uses-permission'))
            assert declarations.count(permission) == (1 if flavor == 'pleos' else 0), (flavor, build_type)
    print('PASS: source-set TTS permission isolation (not merged-manifest evidence)', flush=True)


def main():
    if not shutil.which('javac') or not shutil.which('java'):
        raise SystemExit('BLOCKED: JDK 17+ (javac and java) is required; no checks executed')
    check_permission_sources()
    with tempfile.TemporaryDirectory(prefix='tts-lifecycle-') as directory:
        work = Path(directory)
        for name, content in FILES.items():
            path = work / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding='utf-8')
        source = ROOT / 'app/src'
        for relative in ['pleos/java/com/beforearrival/briefing/ComparisonFactory.java',
                         'main/java/com/beforearrival/briefing/ComparisonSpeech.java',
                         'main/java/com/beforearrival/briefing/ComparisonFixture.java']:
            shutil.copy(source / relative, work / 'com/beforearrival/briefing')
        subprocess.run(['javac', '--release', '17', '-encoding', 'UTF-8', '-d', str(work / 'classes'),
                        *map(str, sorted(work.rglob('*.java')))], check=True)
        for debug, mode in [('true', 'off'), ('false', 'off'), ('true', 'baseline'),
                            ('true', 'candidate'), ('false', 'candidate')]:
            print(f'Checking debug={debug} comparison={mode}', flush=True)
            subprocess.run(['java', f'-Ddebug={debug}', f'-Dcomparison={mode}',
                            '-cp', str(work / 'classes'),
                            'com.beforearrival.briefing.LifecycleCheck'], check=True)


if __name__ == '__main__':
    main()
