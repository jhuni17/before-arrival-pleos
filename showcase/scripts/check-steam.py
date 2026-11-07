#!/usr/bin/env python3
"""Record one official Steam feed connection attempt; obey inherited HTTPS proxy."""
import datetime
import json
import pathlib
import urllib.request
url = 'https://api.steampowered.com/ISteamNews/GetNewsForApp/v2/?appid=570&count=5&maxlength=0&feeds=steam_community_announcements'
result = {'checkedAt': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'requestUrl': url, 'officialHub': 'https://store.steampowered.com/news/app/570', 'liveIntegration': False}
try:
    with urllib.request.urlopen(url, timeout=20) as response:
        payload = json.load(response)
    items = payload.get('appnews', {}).get('newsitems', [])
    official = [item for item in items if item.get('feedname') == 'steam_community_announcements']
    result.update(status='metadata-reachable' if official else 'no-official-items', items=[{'title': item['title'], 'url': item['url'], 'publishedAt': datetime.datetime.fromtimestamp(item['date'], datetime.timezone.utc).isoformat(), 'feedname': item['feedname']} for item in official])
except Exception as error:
    result.update(status='connection-failed', error=str(error), items=[])
result['fallback'] = {'mode': 'sample', 'title': '이번 주말, 협동 모드가 열려요', 'publishedAt': None, 'sourceArticle': None, 'notice': '가상 패치 소식. 실제 게시물·게시 시각으로 주장하지 않음. 브라우저 시연은 네트워크 호출 없이 이 샘플을 사용함.'}
path = pathlib.Path('evidence/steam-source.json')
path.parent.mkdir(exist_ok=True)
path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
print(f"{result['status']}: {path}")
