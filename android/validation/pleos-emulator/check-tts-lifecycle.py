#!/usr/bin/env python3
"""Compile the real candidate against deterministic doubles; no SDK/audio trial."""
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
FILES = {
    'android/content/Context.java': 'package android.content; public class Context {}',
    'android/os/Looper.java': 'package android.os; public class Looper { public static Looper getMainLooper(){return new Looper();} }',
    'android/os/SystemClock.java': 'package android.os; public class SystemClock { public static long elapsedRealtime(){return 0;} }',
    'android/util/Log.java': 'package android.util; public class Log { public static int d(String a,String b){return 0;} }',
    'android/os/Handler.java': '''package android.os;
public class Handler {
 public static final java.util.Queue<Runnable> queue = new java.util.ArrayDeque<>();
 public Handler(Looper l) {} public boolean post(Runnable r){queue.add(r);return true;}
 public boolean postDelayed(Runnable r,long delay){return true;}
 public void removeCallbacksAndMessages(Object token){queue.clear();}
 public static void drain(){while(!queue.isEmpty()) queue.remove().run();}
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
 public int speaks; public String spoken;
 public TextToSpeech(android.content.Context c, Mode m){if(m!=Mode.ON_DEVICE)throw new AssertionError();last=this;}
 public void initialize(OnInitializeListener l){init=l;storage=true;if(synchronous)success();}
 public void success(){initialized=true;init.onSuccess();}
 public void addEventListener(EventListener e){if(!storage)throw new AssertionError("listener before initialize");events=e;registered=true;}
 public void setLocale(java.util.Locale l){locale=java.util.Locale.KOREA.equals(l);}
 public void setAudioAttributes(android.media.AudioAttributes a){attributes=true;}
 public void speak(String s,boolean end){if(!initialized||!registered||!locale||!attributes||!end||released)throw new AssertionError("premature speak");speaks++;spoken=s;}
 public void stop(){} public void removeEventListener(EventListener e){registered=false;}
 public void release(){released=true;}
}''',
    'com/beforearrival/briefing/BuildConfig.java': 'package com.beforearrival.briefing; final class BuildConfig { static final boolean DEBUG=true; }',
    'com/beforearrival/briefing/LifecycleCheck.java': '''package com.beforearrival.briefing;
import ai.pleos.playground.tts.TextToSpeech;
import ai.pleos.playground.tts.listener.EventListener;
import ai.pleos.playground.tts.constant.ErrorType;
import android.os.Handler;
public class LifecycleCheck {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
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
  System.out.println("PASS: candidate lifecycle (sync/async initialization, no onReady, duplicate/stale callbacks)");
 }
}''',
}


def main():
    if not shutil.which('javac') or not shutil.which('java'):
        raise SystemExit('BLOCKED: JDK 17+ (javac and java) is required; no checks executed')
    with tempfile.TemporaryDirectory(prefix='tts-lifecycle-') as directory:
        work = Path(directory)
        for name, content in FILES.items():
            path = work / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding='utf-8')
        source = ROOT / 'app/src'
        for relative in ['pleos/java/com/beforearrival/briefing/ComparisonFactory.java',
                         'main/java/com/beforearrival/briefing/ComparisonSpeech.java']:
            shutil.copy(source / relative, work / 'com/beforearrival/briefing')
        subprocess.run(['javac', '--release', '17', '-encoding', 'UTF-8', '-d', str(work / 'classes'),
                        *map(str, sorted(work.rglob('*.java')))], check=True)
        subprocess.run(['java', '-cp', str(work / 'classes'),
                        'com.beforearrival.briefing.LifecycleCheck'], check=True)


if __name__ == '__main__':
    main()
