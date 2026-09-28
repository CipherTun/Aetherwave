import 'dart:convert';
import 'package:http/http.dart' as http;

const jamendoId = String.fromEnvironment('JAMENDO_CLIENT_ID');
const audiusKey = String.fromEnvironment('AUDIUS_API_KEY');
const podcastIndexKey = String.fromEnvironment('PODCASTINDEX_API_KEY');
const podcastIndexSecret = String.fromEnvironment('PODCASTINDEX_API_SECRET');
const appName = 'Aetherwave';

class Track {
  final String id, src, title, artist, image, url, dl;
  final bool preview;
  final String album, genre;
  final int? year, durationMs;
  Track(this.id, this.src, this.title, this.artist, this.image, this.url, this.dl, this.preview,
      {this.album = '', this.genre = '', this.year, this.durationMs});
  factory Track.fromJson(Map j) => Track(
        '${j['id']}', '${j['src'] ?? ''}', '${j['title'] ?? ''}', '${j['artist'] ?? ''}',
        '${j['image'] ?? ''}', '${j['url'] ?? ''}', '${j['dl'] ?? ''}', j['preview'] == true,
        album: '${j['album'] ?? ''}', genre: '${j['genre'] ?? ''}',
        year: j['year'] == null ? null : int.tryParse('${j['year']}'),
        durationMs: j['durationMs'] == null ? null : int.tryParse('${j['durationMs']}'),
      );
  Map<String, dynamic> toJson() => {
        'id': id, 'src': src, 'title': title, 'artist': artist, 'image': image, 'url': url,
        'dl': dl, 'preview': preview, 'album': album, 'genre': genre, 'year': year, 'durationMs': durationMs,
      };
}

class PodcastEpisode {
  final String id, title, show, image, audio, description;
  final int? durationMs;
  PodcastEpisode(this.id, this.title, this.show, this.image, this.audio, this.description, this.durationMs);
}

class Country {
  final String code, name, flag;
  Country(this.code, this.name, this.flag);
}

Future<dynamic> _get(Uri u, {Map<String, String>? headers}) async {
  final r = await http.get(u, headers: {'User-Agent': '$appName/2.0', ...?headers}).timeout(const Duration(seconds: 18));
  if (r.statusCode != 200) throw Exception('HTTP ${r.statusCode}');
  return jsonDecode(utf8.decode(r.bodyBytes));
}

Track _itunes(Map j) => Track(
      'it${j['trackId']}', 'iTunes', j['trackName'] ?? '', j['artistName'] ?? '',
      (j['artworkUrl100'] ?? '').toString().replaceAll('100x100', '600x600'), j['previewUrl'] ?? '', '', true,
      album: j['collectionName'] ?? '', genre: j['primaryGenreName'] ?? '', year: '${j['releaseDate'] ?? ''}'.length >= 4 ? int.tryParse('${j['releaseDate']}'.substring(0, 4)) : null, durationMs: j['trackTimeMillis']);

Future<List<Track>> itunesSearch(String q, String? cc) async {
  final d = await _get(Uri.https('itunes.apple.com', '/search', {'term': q, 'media': 'music', 'entity': 'song', if (cc != null) 'country': cc.toLowerCase(), 'limit': '50'}));
  return [for (final j in d['results']) if ((j['previewUrl'] ?? '') != '') _itunes(j)];
}

Future<List<Track>> appleChart(String cc) async {
  final c = await _get(Uri.https('rss.marketingtools.apple.com', '/api/v2/${cc.toLowerCase()}/music/most-played/50/songs.json'));
  final ids = [for (final r in c['feed']['results']) r['id']].join(',');
  final d = await _get(Uri.https('itunes.apple.com', '/lookup', {'id': ids, 'country': cc.toLowerCase()}));
  return [for (final j in d['results']) if (j['previewUrl'] != null) _itunes(j)];
}

Future<List<Track>> jamendo({String? q, String order = 'popularity_week'}) async {
  if (jamendoId.isEmpty) return [];
  final d = await _get(Uri.https('api.jamendo.com', '/v3.0/tracks', {
    'client_id': jamendoId, 'format': 'json', 'limit': '50', 'imagesize': '600', 'audiodlformat': 'mp32',
    'order': q == null ? order : 'relevance', if (q != null) 'search': q,
  }));
  return [for (final j in d['results']) Track(
    'jm${j['id']}', 'Jamendo', j['name'] ?? '', j['artist_name'] ?? '', j['image'] ?? '', j['audio'] ?? '',
    j['audiodownload_allowed'] == true ? (j['audiodownload'] ?? '') : '', false,
    album: j['album_name'] ?? '', genre: j['genre'] ?? '', durationMs: j['duration'] == null ? null : int.tryParse('${j['duration']}')! * 1000,
  )];
}

Track _audius(Map j) {
  final s = 'https://api.audius.co/v1/tracks/${j['id']}/stream?app_name=$appName${audiusKey.isEmpty ? '' : '&api_key=$audiusKey'}';
  final a = j['artwork'] ?? {};
  return Track('au${j['id']}', 'Audius', j['title'] ?? '', j['user']?['name'] ?? '', a['1000x1000'] ?? a['480x480'] ?? a['150x150'] ?? '', s, '', false,
      album: j['album']?['album_name'] ?? '', genre: j['genre'] ?? '', durationMs: j['duration'] == null ? null : int.tryParse('${j['duration']}')! * 1000);
}

Future<List<Track>> audius({String? q}) async {
  final p = {'app_name': appName, if (audiusKey.isNotEmpty) 'api_key': audiusKey};
  final d = q == null ? await _get(Uri.https('api.audius.co', '/v1/tracks/trending', p)) : await _get(Uri.https('api.audius.co', '/v1/tracks/search', {...p, 'query': q, 'limit': '50'}));
  return [for (final j in d['data']) _audius(j)];
}

Track _deezer(Map j) => Track('dz${j['id']}', 'Deezer', j['title'] ?? '', j['artist']?['name'] ?? '', j['album']?['cover_xl'] ?? j['album']?['cover_medium'] ?? '', j['preview'] ?? '', '', true,
    album: j['album']?['title'] ?? '');

Future<List<Track>> deezer({String? q}) async {
  final d = q == null ? await _get(Uri.https('api.deezer.com', '/chart/0/tracks')) : await _get(Uri.https('api.deezer.com', '/search', {'q': q, 'limit': '50'}));
  return [for (final j in d['data']) if ((j['preview'] ?? '') != '') _deezer(j)];
}

enum Style { hero, cards, rank }
class Shelf {
  final String title;
  final Style style;
  final Future<List<Track>> Function() load;
  Shelf(this.title, this.style, this.load);
}
class Source {
  final String name;
  final Future<List<Track>> Function(String q, String cc) search;
  final List<Shelf> Function(String cc, String label) shelves;
  Source(this.name, this.search, this.shelves);
}

final registry = <Source>[
  Source('iTunes', (q, cc) async => [...await itunesSearch(q, null).catchError((_) => <Track>[]), ...await itunesSearch(q, cc).catchError((_) => <Track>[])], (cc, l) => [Shelf('Top songs in $l', Style.rank, () => appleChart(cc))]),
  Source('Audius', (q, cc) => audius(q: q), (cc, l) => [Shelf('Trending now', Style.hero, () => audius())]),
  Source('Jamendo', (q, cc) => jamendo(q: q), (cc, l) => [Shelf('Fresh indie picks', Style.cards, () => jamendo())]),
  Source('Deezer', (q, cc) => deezer(q: q), (cc, l) => [Shelf('Global top', Style.cards, () => deezer())]),
];

Future<List<Track>> searchAll(String q, String cc) async {
  final r = await Future.wait([for (final s in registry) s.search(q, cc).catchError((_) => <Track>[]) ]);
  final rounds = <Track>[];
  for (var i = 0; r.any((l) => i < l.length); i++) {
    final round = [for (final l in r) if (i < l.length) l[i]]..sort((a, b) => (a.preview ? 1 : 0) - (b.preview ? 1 : 0));
    rounds.addAll(round);
  }
  final seen = <String>{};
  return [for (final t in rounds) if (seen.add('${t.title}|${t.artist}'.toLowerCase())) t];
}

Future<List<Country>> fetchCountries() async {
  final d = await _get(Uri.https('restcountries.com', '/v3.1/all', {'fields': 'name,cca2,flag'}));
  final l = [for (final j in d) Country(j['cca2'], j['name']['common'], j['flag'] ?? '')];
  l.sort((a, b) => a.name.compareTo(b.name));
  return l;
}

Future<String?> fetchLyrics(Track t) async {
  try { final d = await _get(Uri.https('lrclib.net', '/api/get', {'artist_name': t.artist, 'track_name': t.title})); return d['plainLyrics']; } catch (_) { return null; }
}

String _sha1(String input) {
  // Podcast Index requires SHA-1 auth. Implemented without another package.
  final bytes = utf8.encode(input);
  var h0 = 0x67452301, h1 = 0xEFCDAB89, h2 = 0x98BADCFE, h3 = 0x10325476, h4 = 0xC3D2E1F0;
  final bitLen = bytes.length * 8;
  final data = <int>[...bytes, 0x80];
  while ((data.length % 64) != 56) data.add(0);
  for (var i = 7; i >= 0; i--) data.add((bitLen >> (i * 8)) & 0xff);
  for (var o = 0; o < data.length; o += 64) {
    final w = List<int>.filled(80, 0);
    for (var i = 0; i < 16; i++) { final p = o + i * 4; w[i] = (data[p] << 24) | (data[p+1] << 16) | (data[p+2] << 8) | data[p+3]; }
    for (var i = 16; i < 80; i++) w[i] = _rol((w[i-3] ^ w[i-8] ^ w[i-14] ^ w[i-16]) & 0xffffffff, 1);
    var a=h0,b=h1,c=h2,d=h3,e=h4;
    for (var i=0;i<80;i++) { int f,k; if(i<20){f=(b&c)|((~b)&d);k=0x5A827999;} else if(i<40){f=b^c^d;k=0x6ED9EBA1;} else if(i<60){f=(b&c)|(b&d)|(c&d);k=0x8F1BBCDC;} else {f=b^c^d;k=0xCA62C1D6;} final t=(_rol(a,5)+f+e+k+w[i])&0xffffffff;e=d;d=c;c=_rol(b,30);b=a;a=t; }
    h0=(h0+a)&0xffffffff;h1=(h1+b)&0xffffffff;h2=(h2+c)&0xffffffff;h3=(h3+d)&0xffffffff;h4=(h4+e)&0xffffffff;
  }
  return [h0,h1,h2,h3,h4].map((x)=>x.toRadixString(16).padLeft(8,'0')).join();
}
int _rol(int x,int n)=>((x<<n)|(x>>(32-n)))&0xffffffff;

Future<List<PodcastEpisode>> podcastEpisodes(String feedId) async {
  if (podcastIndexKey.isEmpty || podcastIndexSecret.isEmpty) return [];
  final epoch = DateTime.now().millisecondsSinceEpoch ~/ 1000;
  final auth = _sha1('$podcastIndexKey$podcastIndexSecret$epoch');
  final d = await _get(Uri.https('api.podcastindex.org', '/api/1.0/episodes/byfeedid', {'id': feedId, 'max': '30'}), headers: {'X-Auth-Key': podcastIndexKey, 'X-Auth-Date': '$epoch', 'Authorization': auth});
  return [for (final e in (d['items'] as List? ?? [])) if ((e['enclosureUrl'] ?? '') != '') PodcastEpisode('${e['id']}', '${e['title'] ?? ''}', '${e['feedTitle'] ?? ''}', '${e['image'] ?? e['feedImage'] ?? ''}', '${e['enclosureUrl']}', '${e['description'] ?? ''}', e['duration'] is int ? e['duration'] * 1000 : null)];
}

Future<List<PodcastEpisode>> podcastSearch(String q) async {
  if (podcastIndexKey.isEmpty || podcastIndexSecret.isEmpty) return [];
  final epoch = DateTime.now().millisecondsSinceEpoch ~/ 1000;
  final auth = _sha1('$podcastIndexKey$podcastIndexSecret$epoch');
  final d = await _get(Uri.https('api.podcastindex.org', '/api/1.0/search/byterm', {'q': q, 'max': '30'}), headers: {'X-Auth-Key': podcastIndexKey, 'X-Auth-Date': '$epoch', 'Authorization': auth});
  final out=<PodcastEpisode>[];
  for(final f in (d['feeds'] as List? ?? [])) {
    final id='${f['id']}';
    final title='${f['title'] ?? ''}';
    final img='${f['image'] ?? f['artwork'] ?? ''}';
    out.add(PodcastEpisode('feed_$id', title, title, img, '${f['url'] ?? ''}', '${f['description'] ?? ''}', null));
  }
  return out;
}
