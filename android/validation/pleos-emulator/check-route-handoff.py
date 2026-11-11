#!/usr/bin/env python3
"""Offline shared-session observers; no Activity/service/SDK runtime evidence."""
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True

SPEC = importlib.util.spec_from_file_location(
    'route_lifecycle', Path(__file__).with_name('check-route-lifecycle.py'))
LIFECYCLE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(LIFECYCLE)
SOURCES = [*LIFECYCLE.SOURCES,
           'main/java/com/beforearrival/briefing/LiveRouteSession.java',
           'pleos/java/com/beforearrival/briefing/LiveConnectionFactory.java']
HARNESS = '''package com.beforearrival.briefing;
import android.os.*;
import ai.pleos.playground.navi.helper.NaviHelper;
import ai.pleos.playground.navi.data.DestinationInfo;
import java.util.*;
import java.util.function.Consumer;
public class RouteHandoffCheck {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static class Observer implements Consumer<RouteInput.Snapshot> {
  final List<Long> values=new ArrayList<>(); boolean detachOnEmpty;
  public void accept(RouteInput.Snapshot s){
   check(s.source==RouteInput.Source.NAVI_HELPER,"unexpected SAMPLE");
   values.add(s.remainingSeconds.orElse(-1));
   if(detachOnEmpty && s.remainingSeconds.isEmpty()) LiveRouteSession.shared.detach(this);
  }
  void last(long expected){check(!values.isEmpty() && values.get(values.size()-1)==expected,"observer ETA "+expected+": "+values);}
 }
 static final LiveRouteSession session=LiveRouteSession.shared;
 static final android.content.Context context=new android.content.Context();
 static void identity(NaviHelper sdk,int requests){
  check(NaviHelper.last==sdk,"helper replaced");check(sdk.requests==requests,"query count "+requests);
  check(sdk.releases==0 && sdk.removals==0,"premature cleanup");
 }
 static void reply(NaviHelper sdk,int raw){sdk.listener.onDestinationInfo(new DestinationInfo(raw));Handler.drain();}
 static void cleaned(NaviHelper sdk){
  check(sdk.listener==null && sdk.removals==1 && sdk.releases==1,"cleanup exactly once");
  check(Handler.scheduled()==0,"scheduled tasks remain");
 }
 public static void main(String[] args){
  Build.FINGERPRINT=args[0];context.info.flags=2;
  check(LiveConnectionFactory.isSdkIncluded(),"Pleos factory required");
  Observer screen=new Observer(),media=new Observer();
  session.attach(context,screen);NaviHelper sdk=NaviHelper.last;
  sdk.success();Handler.drain();identity(sdk,1);reply(sdk,1920);screen.last(1920);
  int screenCount=screen.values.size();
  session.attach(context,media);media.last(1920);
  check(media.values.equals(List.of(1920L)),"second observer immediate ETA exactly once");
  check(screen.values.size()==screenCount,"attach republished to screen");identity(sdk,1);
  System.out.println("PASS synthetic second subscriber: immediate ETA 1920; same helper; requests=1");
  session.detach(screen);Handler.advanceBy(4999);identity(sdk,1);
  Handler.advanceBy(1);identity(sdk,2);reply(sdk,1860);
  media.last(1860);screen.last(1920);check(screen.values.size()==screenCount,"detached screen updated");
  check(media.values.equals(List.of(1920L,1860L)),"media update sequence");identity(sdk,2);
  session.attach(context,screen);screen.last(1860);
  check(screen.values.size()==screenCount+1,"screen replay exactly once");identity(sdk,2);
  System.out.println("PASS synthetic screen departure/return: media-only ETA 1860; same helper; release=0; requests=2");
  session.detach(media);int mediaCount=media.values.size();
  Handler.advanceBy(5000);identity(sdk,3);reply(sdk,1680);screen.last(1680);
  check(media.values.size()==mediaCount,"detached media updated");media.last(1860);
  check(screen.values.size()==screenCount+2,"screen next update exactly once");
  session.detach(screen);Handler.drain();cleaned(sdk);
  Handler.advanceBy(60000);cleaned(sdk);check(sdk.requests==3,"poll after final detach");
  System.out.println("PASS synthetic media departure/final detach: screen ETA 1680; remove/release=1; tasks=0");
  // Same real shared singleton, fresh connection; cancellation mutates observers during publish.
  Observer nextScreen=new Observer(),nextMedia=new Observer();
  session.attach(context,nextScreen);NaviHelper next=NaviHelper.last;
  check(next!=sdk,"fresh scenario needs fresh helper");next.success();Handler.drain();
  reply(next,1920);nextScreen.last(1920);session.attach(context,nextMedia);nextMedia.last(1920);
  session.detach(nextScreen);int before=nextScreen.values.size();
  nextMedia.detachOnEmpty=true;next.listener.onRouteCancelled();Handler.drain();
  nextMedia.last(-1);check(Collections.frequency(nextMedia.values,-1L)==1,"empty notification exactly once");
  check(nextScreen.values.size()==before,"cancel reached detached screen");cleaned(next);
  Handler.advanceBy(60000);cleaned(next);check(next.requests==1,"cancelled poll survived");
  System.out.println("PASS synthetic cancellation self-detach: empty ETA once; publish returned; remove/release=1; tasks=0");
 }
}'''


def main():
    if not shutil.which('javac') or not shutil.which('java'):
        raise SystemExit('BLOCKED: JDK 17+ (javac and java) required; no checks executed')
    evidence = json.loads(Path(__file__).with_name('runtime-evidence.json').read_text())
    fingerprint = evidence['environment']['fingerprint']
    print('Synthetic responses under observed fingerprint/debug gate: ' + fingerprint, flush=True)
    doubles = dict(LIFECYCLE.DOUBLES)
    # Platform API needed by the unchanged Pleos factory; no connection decisions here.
    doubles['android/content/Context.java'] = doubles['android/content/Context.java'].replace(
        'public class Context {',
        'public class Context { public Context getApplicationContext(){return this;}')
    with tempfile.TemporaryDirectory(prefix='route-handoff-') as directory:
        work = Path(directory)
        for name, content in {**doubles, 'com/beforearrival/briefing/RouteHandoffCheck.java': HARNESS}.items():
            target = work / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content, encoding='utf-8')
        for relative in SOURCES:
            source = LIFECYCLE.ANDROID / 'app/src' / relative
            target = work / 'com/beforearrival/briefing' / source.name
            shutil.copyfile(source, target)
            if source.read_bytes() != target.read_bytes():
                raise AssertionError('Source copy differs: ' + relative)
            print(f'Compile input: {source} -> {target}; bytes identical=True', flush=True)
        inputs = sorted(work.rglob('*.java'))
        subprocess.run(['javac', '--release', '17', '-encoding', 'UTF-8', '-d',
                        str(work / 'classes'), *map(str, inputs)], check=True)
        subprocess.run(['java', '-cp', str(work / 'classes'),
                        'com.beforearrival.briefing.RouteHandoffCheck', fingerprint], check=True)
    print('PASS: offline shared-session synthetic observers only; no Activity/service/SDK runtime/voice/APK evidence')


if __name__ == '__main__':
    main()
