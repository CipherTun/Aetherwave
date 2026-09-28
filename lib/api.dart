import 'dart:convert';
import 'package:http/http.dart' as http;

// Keys are injected at build time (--dart-define). Nothing is hard coded.
const jamendoId = String.fromEnvironment('JAMENDO_CLIENT_ID');
const audiusKey = String.fromEnvironment('AUDIUS_API_KEY');
const appName = 'Aetherwave';

class Track {
  final String id, src, title, artist, image, url, dl;
  final bool preview; // true = 30s preview only (iTunes/Deezer)
  Track(this.id, this.src, this.title, this.artist, this.image, this.url, this.dl, this.preview);
  factory Track.fromJson(Map j) => Track(j['id'], j['src'], j['title'], j['artist'], j['image'], j['url'], j['dl'], j['preview']);
  Map toJson() => {'id': id, 'src': src, 'title': title, 'artist': artist, 'image': image, 'url': url, 'dl': dl, 'preview': preview};
}

class Country {
  final String code, name, flag;
  Country(this.code, this.name, this.flag);
}

Future<dynamic> _get(Uri u) async {
  final r = await http.get(u, headers: {'User-Agent': '$appName/1.0'}).timeout(const Duration(seconds: 15));
  if (r.statusCode != 200) throw Exception('HTTP ${r.statusCode}');
  return jsonDecode(utf8.decode(r.bodyBytes));
}

Track _itunes(Map j) => Track('it${j['trackId']}', 'iTunes', j['trackName'] ?? '', j['artistName'] ?? '',
    (j['artworkUrl100'] ?? '').toString().replaceAll('100x100', '400x400'), j['previewUrl'] ?? '', '', true);

Future<List<Track>> itunesSearch(String q, String? cc) async {
  final d = await _get(Uri.https('itunes.apple.com', '/search',
      {'term': q, 'media': 'music', 'entity': 'song', if (cc != null) 'country': cc.toLowerCase(), 'limit': '30'}));
  return [for (final j in d['results']) if ((j['previewUrl'] ?? '') != '') _itunes(j)];
}

/// Apple's official per-country "most played" chart, resolved to playable previews.
Future<List<Track>> appleChart(String cc) async {
  final c = await _get(Uri.https('rss.marketingtools.apple.com', '/api/v2/${cc.toLowerCase()}/music/most-played/50/songs.json'));
  final ids = [for (final r in c['feed']['results']) r['id']].join(',');
  final d = await _get(Uri.https('itunes.apple.com', '/lookup', {'id': ids, 'country': cc.toLowerCase()}));
  return [for (final j in d['results']) if (j['previewUrl'] != null) _itunes(j)];
}

Future<List<Track>> jamendo({String? q, String order = 'popularity_week'}) async {
  if (jamendoId.isEmpty) return [];
  final d = await _get(Uri.https('api.jamendo.com', '/v3.0/tracks', {
    'client_id': jamendoId, 'format': 'json', 'limit': '30', 'imagesize': '300', 'audiodlformat': 'mp32',
    'order': q == null ? order : 'relevance', if (q != null) 'search': q,
  }));
  return [
    for (final j in d['results'])
      Track('jm${j['id']}', 'Jamendo', j['name'], j['artist_name'], j['image'] ?? '', j['audio'],
          (j['audiodownload_allowed'] == true) ? (j['audiodownload'] ?? '') : '', false)
  ];
}

Track _audius(Map j) {
  final s = 'https://api.audius.co/v1/tracks/${j['id']}/stream?app_name=$appName${audiusKey.isEmpty ? '' : '&api_key=$audiusKey'}';
  final a = j['artwork'] ?? {};
  return Track('au${j['id']}', 'Audius', j['title'] ?? '', j['user']?['name'] ?? '', a['480x480'] ?? a['150x150'] ?? '', s, '', false);
}

Future<List<Track>> audius({String? q}) async {
  final p = {'app_name': appName, if (audiusKey.isNotEmpty) 'api_key': audiusKey};
  final d = q == null
      ? await _get(Uri.https('api.audius.co', '/v1/tracks/trending', p))
      : await _get(Uri.https('api.audius.co', '/v1/tracks/search', {...p, 'query': q}));
  return [for (final j in d['data']) _audius(j)];
}

Track _deezer(Map j) => Track('dz${j['id']}', 'Deezer', j['title'] ?? '', j['artist']?['name'] ?? '',
    j['album']?['cover_medium'] ?? '', j['preview'] ?? '', '', true);

Future<List<Track>> deezer({String? q}) async {
  final d = q == null ? await _get(Uri.https('api.deezer.com', '/chart/0/tracks')) : await _get(Uri.https('api.deezer.com', '/search', {'q': q}));
  return [for (final j in d['data']) if ((j['preview'] ?? '') != '') _deezer(j)];
}

enum Style { hero, cards, rank }

class Shelf {
  final String title;
  final Style style;
  final Future<List<Track>> Function() load;
  Shelf(this.title, this.style, this.load);
}

/// Every catalogue plugs in here. Home shelves and search are built from this registry,
/// so adding a source means adding one entry, with no UI changes.
class Source {
  final String name;
  final Future<List<Track>> Function(String q, String cc) search;
  final List<Shelf> Function(String cc, String label) shelves;
  Source(this.name, this.search, this.shelves);
}

final registry = <Source>[
  Source('iTunes', (q, cc) async {
    final r = await Future.wait([itunesSearch(q, null).catchError((_) => <Track>[]), itunesSearch(q, cc).catchError((_) => <Track>[])]);
    return [...r[0], ...r[1]];
  }, (cc, l) => [Shelf('Top songs in $l', Style.rank, () => appleChart(cc))]),
  Source('Audius', (q, cc) => audius(q: q), (cc, l) => [Shelf('Trending now', Style.hero, () => audius())]),
  Source('Jamendo', (q, cc) => jamendo(q: q), (cc, l) => [Shelf('Fresh indie picks', Style.cards, () => jamendo())]),
  Source('Deezer', (q, cc) => deezer(q: q), (cc, l) => [Shelf('Global top', Style.cards, () => deezer())]),
];

/// Worldwide search across every registered source (no country or source filtering),
/// merged, ranked full-length first, and de-duplicated.
Future<List<Track>> searchAll(String q, String cc) async {
  final r = await Future.wait([for (final s in registry) s.search(q, cc).catchError((_) => <Track>[])]);
  final rounds = <Track>[];
  for (var i = 0; r.any((l) => i < l.length); i++) {
    final round = [for (final l in r) if (i < l.length) l[i]]..sort((a, b) => (a.preview ? 1 : 0) - (b.preview ? 1 : 0));
    rounds.addAll(round);
  }
  final seen = <String>{};
  return [for (final t in rounds) if (seen.add('${t.title}|${t.artist}'.toLowerCase())) t];
}

/// Country list comes from a live API (no hard coded list).
Future<List<Country>> fetchCountries() async {
  final d = await _get(Uri.https('restcountries.com', '/v3.1/all', {'fields': 'name,cca2,flag'}));
  final l = [for (final j in d) Country(j['cca2'], j['name']['common'], j['flag'] ?? '')];
  l.sort((a, b) => a.name.compareTo(b.name));
  return l;
}

Future<String?> fetchLyrics(Track t) async {
  try {
    final d = await _get(Uri.https('lrclib.net', '/api/get', {'artist_name': t.artist, 'track_name': t.title}));
    return d['plainLyrics'];
  } catch (_) {
    return null;
  }
}
