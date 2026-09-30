import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:xml/xml.dart';
import 'audiomack_api.dart';

const jamendoId = String.fromEnvironment('JAMENDO_CLIENT_ID');
const audiusKey = String.fromEnvironment('AUDIUS_API_KEY');
const openverseToken = String.fromEnvironment('OPENVERSE_API_TOKEN');
const podcastIndexKey = String.fromEnvironment('PODCASTINDEX_API_KEY');
const podcastIndexSecret = String.fromEnvironment('PODCASTINDEX_API_SECRET');
const appName = 'Aetherwave';

const freeToUseBase = 'https://api.freetouse.com/v3';
const audiusBase = 'https://api.audius.co/v1';
const ccMixterBase = 'https://ccmixter.org/api/query';


class Track {
  final String id, src, title, artist, image, url, dl;
  final bool preview;
  final String album, genre;
  final String license, licenseUrl;

  final int? year, durationMs;
  Track(this.id, this.src, this.title, this.artist, this.image, this.url, this.dl, this.preview,
      {this.album = '', this.genre = '', this.year, this.durationMs, this.license = '', this.licenseUrl = ''});
  factory Track.fromJson(Map j) => Track(
        '${j['id']}', '${j['src'] ?? ''}', '${j['title'] ?? ''}', '${j['artist'] ?? ''}',
        '${j['image'] ?? ''}', '${j['url'] ?? ''}', '${j['dl'] ?? ''}', j['preview'] == true,
        album: '${j['album'] ?? ''}', genre: '${j['genre'] ?? ''}',
        year: j['year'] == null ? null : int.tryParse('${j['year']}'),
        durationMs: j['durationMs'] == null ? null : int.tryParse('${j['durationMs']}'),
        license: '${j['license'] ?? ''}', licenseUrl: '${j['licenseUrl'] ?? ''}',
      );
  Map<String, dynamic> toJson() => {
        'id': id, 'src': src, 'title': title, 'artist': artist, 'image': image, 'url': url,
        'dl': dl, 'preview': preview, 'album': album, 'genre': genre, 'year': year, 'durationMs': durationMs,
        'license': license, 'licenseUrl': licenseUrl,
      };

  Track copyWith({
    String? id,
    String? src,
    String? title,
    String? artist,
    String? image,
    String? url,
    String? dl,
    bool? preview,
    String? album,
    String? genre,
    int? year,
    int? durationMs,
    String? license,
    String? licenseUrl,
  }) =>
      Track(
        id ?? this.id,
        src ?? this.src,
        title ?? this.title,
        artist ?? this.artist,
        image ?? this.image,
        url ?? this.url,
        dl ?? this.dl,
        preview ?? this.preview,
        album: album ?? this.album,
        genre: genre ?? this.genre,
        year: year ?? this.year,
        durationMs: durationMs ?? this.durationMs,
        license: license ?? this.license,
        licenseUrl: licenseUrl ?? this.licenseUrl,
      );
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
  final id = '${j['id'] ?? ''}';

  final streamable =
      j['isStreamable'] == true ||
      j['isStreamable'] == 'true';

  final downloadable =
      j['downloadable'] == true ||
      j['downloadable'] == 'true' ||
      j['isDownloadable'] == true;

  final s = id.isEmpty || !streamable
      ? ''
      : '$audiusBase/tracks/$id/stream?app_name=$appName'
        '${audiusKey.isEmpty ? '' : '&api_key=$audiusKey'}';

  final download = id.isEmpty || !downloadable
      ? ''
      : '$audiusBase/tracks/$id/download'
        '${audiusKey.isEmpty ? '' : '?api_key=$audiusKey'}';

  final a = j['artwork'] is Map
      ? j['artwork'] as Map
      : <dynamic, dynamic>{};

  return Track(
    'au$id',
    'Audius',
    j['title'] ?? '',
    j['user']?['name'] ?? '',
    a['1000x1000'] ??
        a['_1000x1000'] ??
        a['480x480'] ??
        a['_480x480'] ??
        a['150x150'] ??
        '',
    s,
    download,
    false,
    album: j['album']?['album_name'] ?? '',
    genre: j['genre'] ?? '',
    durationMs: j['duration'] == null
        ? null
        : int.tryParse('${j['duration']}')! * 1000,
  );
}

Future<List<Track>> audius({String? q}) async {
  final p = {'app_name': appName, if (audiusKey.isNotEmpty) 'api_key': audiusKey};
  final d = q == null ? await _get(Uri.https('api.audius.co', '/v1/tracks/trending', p)) : await _get(Uri.https('api.audius.co', '/v1/tracks/search', {...p, 'query': q, 'limit': '50'}));
  return [for (final j in d['data']) _audius(j)];
}

Track _deezer(Map j) => Track('dz${j['id']}', 'Deezer', j['title'] ?? '', j['artist']?['name'] ?? '', j['album']?['cover_xl'] ?? j['album']?['cover_medium'] ?? '', j['preview'] ?? '', '', true,
    album: j['album']?['title'] ?? '');

Track _freeToUse(Map<String, dynamic> j) {
  final artists = (j['artists'] as List? ?? []);
  final artistNames = <String>[];

  for (final entry in artists) {
    if (entry is List && entry.length > 1) {
      final artist = entry[1];
      if (artist is Map) {
        final name = '${artist['name'] ?? ''}'.trim();
        if (name.isNotEmpty) {
          artistNames.add(name);
        }
      }
    }
  }

  final thumbnails =
      Map<String, dynamic>.from((j['thumbnails'] as Map?) ?? const {});
  final files =
      Map<String, dynamic>.from((j['files'] as Map?) ?? const {});

  final audioUrl = '${files['mp3'] ?? j['file_url'] ?? ''}';
  final releaseDate = '${j['release_date'] ?? ''}';

  return Track(
    'ftu${j['id']}',
    'FreeToUse',
    '${j['title'] ?? ''}',
    artistNames.join(', '),
    '${thumbnails['lg'] ?? thumbnails['md'] ?? thumbnails['sm'] ?? ''}',
    audioUrl,
    audioUrl,
    false,
    genre: '${j['genre'] ?? ''}',
    durationMs: j['duration'] is num
        ? ((j['duration'] as num) * 1000).round()
        : null,
    year: releaseDate.length >= 4
        ? int.tryParse(releaseDate.substring(0, 4))
        : null,
    license: 'Free To Use License',
    licenseUrl: 'https://freetouse.com/license',
  );
}

Future<List<Track>> freeToUse({String? q}) async {
  final path =
      q == null ? '/music/tracks/all' : '/music/tracks/search';

  final query = <String, String>{
    'limit': '50',
    'order': q == null ? 'release_date' : 'plays',
    'sort': 'desc',
    if (q != null) 'query': q,
  };

  final d = await _get(
    Uri.https(
      'api.freetouse.com',
      '/v3$path',
      query,
    ),
  );

  final data = (d['data'] as List? ?? []);

  return [
    for (final raw in data)
      if (raw is Map &&
          '${raw['status'] ?? 1}' == '1' &&
          '${raw['files']?['mp3'] ?? ''}'.isNotEmpty)
        _freeToUse(
          Map<String, dynamic>.from(raw),
        ),
  ];
}

Future<List<Track>> deezer({String? q}) async {
  final d = q == null ? await _get(Uri.https('api.deezer.com', '/chart/0/tracks')) : await _get(Uri.https('api.deezer.com', '/search', {'q': q, 'limit': '50'}));
  return [for (final j in d['data']) if ((j['preview'] ?? '') != '') _deezer(j)];
}



Track _openverse(Map j) {
  final license = '${j['license'] ?? ''}'.toLowerCase();
  final downloadable = license == 'cc0' || license == 'pdm' || license == 'publicdomain';
  final genres = (j['genres'] as List? ?? []).map((e) => '$e').where((e) => e.isNotEmpty).join(', ');
  return Track(
    'ov${j['id']}', 'Openverse', j['title'] ?? '', j['creator'] ?? '',
    j['thumbnail'] ?? '', j['url'] ?? '', downloadable ? (j['url'] ?? '') : '', false,
    genre: genres,
    durationMs: j['duration'] is num ? (j['duration'] as num).round() : null,
    license: j['license'] ?? '', licenseUrl: j['license_url'] ?? '',
  );
}

Future<List<Track>> openverseSearch(String q) async {
  final headers = <String, String>{};
  if (openverseToken.isNotEmpty) headers['Authorization'] = 'Bearer $openverseToken';
  final d = await _get(Uri.https('api.openverse.org', '/v1/audio/', {
    'q': q,
    'page_size': '20',
    'license': 'cc0,pdm',
    'mature': 'false',
  }), headers: headers);
  return [for (final j in (d['results'] as List? ?? []))
    if ('${j['url'] ?? ''}'.isNotEmpty) _openverse(j)];
}

Future<List<Track>> archiveSearch(String q) async {
  final safeQ = q.replaceAll('"', ' ');
  final d = await _get(Uri.https('archive.org', '/advancedsearch.php', {
    'q': 'mediatype:audio AND (title:"$safeQ" OR creator:"$safeQ")',
    'fl[]': 'identifier,title,creator,description,licenseurl,year',
    'rows': '12',
    'page': '1',
    'output': 'json',
  }));
  final docs = ((d['response']?['docs'] as List?) ?? []);
  final out = <Track>[];
  for (final raw in docs) {
    final j = Map<String, dynamic>.from(raw as Map);
    final id = '${j['identifier'] ?? ''}';
    if (id.isEmpty) continue;
    final licenseUrl = '${j['licenseurl'] ?? ''}';
    final lowLicense = licenseUrl.toLowerCase();
    if (!(lowLicense.contains('creativecommons.org') || lowLicense.contains('publicdomain'))) continue;
    try {
      final meta = await _get(Uri.https('archive.org', '/metadata/$id'));
      final files = (meta['files'] as List? ?? []);
      Map? audio;
      for (final f in files) {
        final m = Map<String, dynamic>.from(f as Map);
        final name = '${m['name'] ?? ''}'.toLowerCase();
        final format = '${m['format'] ?? ''}'.toLowerCase();
        if (name.endsWith('.mp3') || name.endsWith('.ogg') || name.endsWith('.flac') || format.contains('mp3') || format.contains('vorbis') || format.contains('flac')) {
          if (!name.contains('_thumb') && !name.contains('_files.xml')) { audio = m; break; }
        }
      }
      if (audio == null) continue;
      final name = Uri.encodeComponent('${audio['name']}');
      final url = 'https://archive.org/download/$id/$name';
      final canDownload = lowLicense.contains('/zero/') || lowLicense.contains('publicdomain');
      out.add(Track(
        'ia$id', 'Internet Archive', '${j['title'] ?? id}', '${j['creator'] ?? ''}',
        'https://archive.org/services/img/$id', url, canDownload ? url : '', false,
        year: int.tryParse('${j['year'] ?? ''}'), license: licenseUrl, licenseUrl: licenseUrl,
      ));
    } catch (_) {}
  }
  return out;
}


Track _audiomack(Map<String, dynamic> j) {
  final id = '${j['id'] ?? ''}';

  final uploader =
      j['uploader'] is Map
          ? j['uploader'] as Map
          : <dynamic, dynamic>{};

  final artist =
      '${j['artist'] ?? uploader['name'] ?? ''}';

  final release =
      int.tryParse('${j['released'] ?? ''}');

  final year = release == null
      ? null
      : DateTime.fromMillisecondsSinceEpoch(
          release * 1000,
        ).year;

  return Track(
    'am$id',
    'Audiomack',
    '${j['title'] ?? ''}',
    artist,
    '${j['image'] ?? uploader['image'] ?? ''}',
    '',
    '',
    false,
    album: '${j['album'] ?? ''}',
    genre: '${j['genre'] ?? ''}',
    year: year,
  );
}

Future<List<Track>> audiomackTracks(String q) async {
  final response = await AudiomackApi.search(
    q,
    sortBy: 'relevant',
    page: 1,
  );

  final results = response?.results ?? const [];

  return [
    for (final item in results)
      if (item is Map &&
          '${item['id'] ?? ''}'.isNotEmpty &&
          '${item['title'] ?? ''}'.trim().isNotEmpty)
        _audiomack(Map<String, dynamic>.from(item)),
  ];
}

Future<List<Track>> audiomackTrendingTracks() async {
  final response = await AudiomackApi.search(
    'music',
    sortBy: 'popular',
    page: 1,
  );

  final results = response?.results ?? const [];

  return [
    for (final item in results)
      if (item is Map &&
          '${item['id'] ?? ''}'.isNotEmpty &&
          '${item['title'] ?? ''}'.trim().isNotEmpty)
        _audiomack(Map<String, dynamic>.from(item)),
  ];
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
  Source(
    'Audiomack',
    (q, cc) => audiomackTracks(q),
    (cc, l) => [
      Shelf(
        'Audiomack trending',
        Style.cards,
        () => audiomackTrendingTracks(),
      ),
    ],
  ),

  Source(
    'ccMixter',
    (q, cc) => ccMixterSearch(q),
    (cc, l) => [
      Shelf(
        'Creative Commons music',
        Style.cards,
        () => ccMixterSearch('music'),
      ),
    ],
  ),

  Source('iTunes', (q, cc) async => [...await itunesSearch(q, null).catchError((_) => <Track>[]), ...await itunesSearch(q, cc).catchError((_) => <Track>[])], (cc, l) => [Shelf('Top songs in $l', Style.rank, () => appleChart(cc))]),
  Source('Audius', (q, cc) => audius(q: q), (cc, l) => [Shelf('Trending now', Style.hero, () => audius())]),
  Source('Jamendo', (q, cc) => jamendo(q: q), (cc, l) => [Shelf('Fresh indie picks', Style.cards, () => jamendo())]),
  Source('Deezer', (q, cc) => deezer(q: q), (cc, l) => [Shelf('Global top', Style.cards, () => deezer())]),
  Source('Openverse', (q, cc) => openverseSearch(q), (cc, l) => [Shelf('Open music', Style.cards, () => openverseSearch('music'))]),
  Source('Internet Archive', (q, cc) => archiveSearch(q), (cc, l) => [Shelf('Archive audio', Style.cards, () => archiveSearch('music'))]),
  Source('FreeToUse', (q, cc) => freeToUse(q: q), (cc, l) => [Shelf('Royalty-free picks', Style.cards, () => freeToUse())]),
];



Future<List<Track>> freeToUseSearch(String query) async {
  try {
    final uri = Uri.parse(
      '$freeToUseBase/music/tracks/search',
    ).replace(
      queryParameters: {
        'query': query,
        'limit': '50',
        'order': 'release_date',
        'sort': 'desc',
      },
    );

    final response = await http.get(uri);

    if (response.statusCode != 200) return [];

    final body = jsonDecode(response.body);
    final data = body is Map ? body['data'] : body;

    if (data is! List) return [];

    return data.whereType<Map>().map((j) {
      final files = j['files'] is Map
          ? j['files'] as Map
          : <dynamic, dynamic>{};

      final mp3 = '${files['mp3'] ?? j['file_url'] ?? ''}';

      final thumbnails = j['thumbnails'] is Map
          ? j['thumbnails'] as Map
          : <dynamic, dynamic>{};

      final artwork =
          '${thumbnails['large'] ?? thumbnails['medium'] ?? thumbnails['small'] ?? ''}';

      final artists = j['artists'];

      String artist;

      if (artists is List) {
        artist = artists.map((e) {
          if (e is Map) return '${e['name'] ?? ''}';
          return '$e';
        }).where((e) => e.trim().isNotEmpty).join(', ');
      } else {
        artist = '${j['artist'] ?? ''}';
      }

      final duration = num.tryParse('${j['duration'] ?? ''}');

      return Track(
        'freetouse:${j['id'] ?? ''}',
        'freetouse',
        '${j['title'] ?? ''}',
        artist,
        artwork,
        mp3.isNotEmpty ? mp3 : '${j['url'] ?? ''}',
        mp3,
        false,
        album: '',
        genre: '${j['genre'] ?? ''}',
        year: j['release_date'] == null
            ? null
            : int.tryParse('${j['release_date']}'.substring(0, 4)),
        durationMs: duration == null
            ? null
            : (duration * 1000).round(),
        license: '${j['license'] ?? ''}',
        licenseUrl: '${j['license_url'] ?? ''}',
      );
    }).where((track) {
      return track.title.trim().isNotEmpty &&
          track.artist.trim().isNotEmpty;
    }).toList();
  } catch (_) {
    return [];
  }
}



Future<List<Track>> audiusFullSearch(String query) async {
  try {
    final uri = Uri.parse('$audiusBase/tracks/search').replace(
      queryParameters: {
        'query': query,
        'limit': '50',
        'sortMethod': 'relevant',
      },
    );

    final response = await http.get(
      uri,
      headers: audiusKey.isEmpty
          ? {}
          : {'Authorization': 'Bearer $audiusKey'},
    );

    if (response.statusCode != 200) return [];

    final body = jsonDecode(response.body);
    final data = body is Map ? body['data'] : null;

    if (data is! List) return [];

    return data.whereType<Map>().map((j) {
      final id = '${j['id'] ?? ''}';
      final title = '${j['title'] ?? ''}';

      final user = j['user'] is Map
          ? j['user'] as Map
          : <dynamic, dynamic>{};

      final artist =
          '${user['name'] ?? user['handle'] ?? ''}';

      final artwork = j['artwork'] is Map
          ? '${(j['artwork'] as Map)['_1000x1000'] ?? (j['artwork'] as Map)['_480x480'] ?? ''}'
          : '';

      final duration = num.tryParse('${j['duration'] ?? ''}');

      final streamable =
          j['isStreamable'] == true ||
          j['isStreamable'] == 'true';

      final downloadable =
          j['downloadable'] == true ||
          j['downloadable'] == 'true' ||
          j['isDownloadable'] == true;

      final streamUrl = id.isEmpty
          ? ''
          : '$audiusBase/tracks/$id/stream?app_name=$appName${audiusKey.isEmpty ? '' : '&api_key=$audiusKey'}';

      return Track(
        'audius:$id',
        'audius',
        title,
        artist,
        artwork,
        streamable ? streamUrl : '',
        downloadable && id.isNotEmpty
            ? '$audiusBase/tracks/$id/download'
            : '',
        !streamable,
        album: '',
        genre: '${j['genre'] ?? ''}',
        year: j['releaseDate'] == null
            ? null
            : int.tryParse(
                '${j['releaseDate']}'.substring(0, 4),
              ),
        durationMs: duration == null
            ? null
            : (duration * 1000).round(),
        license: '',
        licenseUrl: '',
      );
    }).where((track) {
      return track.title.trim().isNotEmpty &&
          track.artist.trim().isNotEmpty;
    }).toList();
  } catch (_) {
    return [];
  }
}



Future<List<Track>> ccMixterSearch(String q) async {
  try {
    final uri = Uri.parse(
      ccMixterBase,
    ).replace(
      queryParameters: {
        'search': q,
        'search_type': 'match',
        'f': 'rss',
        'limit': '30',
      },
    );

    final response = await http.get(
      uri,
      headers: {
        'Accept': 'application/rss+xml, application/xml',
        'User-Agent': '$appName/2.2',
      },
    ).timeout(const Duration(seconds: 18));

    if (response.statusCode < 200 ||
        response.statusCode >= 300) {
      return [];
    }

    final document = XmlDocument.parse(
      utf8.decode(response.bodyBytes),
    );

    final result = <Track>[];

    for (final item in document.findAllElements('item')) {
      final title = item
          .getElement('title')
          ?.innerText
          .trim() ?? '';

      final link = item
          .getElement('link')
          ?.innerText
          .trim() ?? '';

      String artist = '';

      for (final node in item.children) {
        if (node is! XmlElement) continue;

        final localName =
            node.name.local.toLowerCase();

        if (localName == 'creator' ||
            localName == 'author') {
          artist = node.innerText.trim();

          if (artist.isNotEmpty) {
            break;
          }
        }
      }

      final enclosure =
          item.getElement('enclosure');

      final mediaUrl =
          enclosure?.getAttribute('url')?.trim() ?? '';

      if (title.isEmpty || mediaUrl.isEmpty) {
        continue;
      }

      String license = '';
      String licenseUrl = '';

      for (final node in item.children) {
        if (node is! XmlElement) continue;

        if (node.name.local.toLowerCase() != 'license') {
          continue;
        }

        license = node.innerText.trim();

        licenseUrl =
            node.getAttribute('rdf:resource')?.trim() ??
            node.getAttribute('resource')?.trim() ??
            '';

        break;
      }

      final normalizedLicense =
          '$license $licenseUrl'.toLowerCase();

      final downloadable =
          normalizedLicense.contains('creativecommons.org/licenses/by/') ||
          normalizedLicense.contains('creativecommons.org/licenses/by-sa/') ||
          normalizedLicense.contains('creativecommons.org/licenses/by-nd/') ||
          normalizedLicense.contains('creativecommons.org/publicdomain/') ||
          normalizedLicense.contains('public domain');

      result.add(
        Track(
          'ccmixter:${mediaUrl.hashCode}',
          'ccMixter',
          title,
          artist.isEmpty
              ? 'ccMixter artist'
              : artist,
          '',
          link.isEmpty ? mediaUrl : link,
          downloadable ? mediaUrl : '',
          false,
          license: license,
          licenseUrl: licenseUrl,
        ),
      );
    }

    return result;
  } catch (_) {
    return [];
  }
}

Future<List<Track>> searchAll(String q, String cc) async {
  final normalizedQuery = q.trim();
  if (normalizedQuery.isEmpty) return [];

  String norm(String value) {
    return value
        .toLowerCase()
        .replaceAll(RegExp(r'[^a-z0-9]+'), ' ')
        .trim();
  }

  final normalized = norm(normalizedQuery);

  final results = await Future.wait([
    audiomackTracks(normalizedQuery).catchError((_) => <Track>[]),
    audiusFullSearch(normalizedQuery).catchError((_) => <Track>[]),
    freeToUseSearch(normalizedQuery).catchError((_) => <Track>[]),
    for (final source in registry)
      source.search(normalizedQuery, cc).catchError((_) => <Track>[]),
  ]);

  final all = <Track>[];

  for (final list in results) {
    all.addAll(list);
  }

  int score(Track track) {
    final title = norm(track.title);
    final artist = norm(track.artist);
    final album = norm(track.album);

    var score = 0;

    // Exact identity matches.
    if (title == normalized) score += 140;
    if (artist == normalized) score += 70;
    if (album == normalized) score += 35;

    // Partial identity matches.
    if (title.contains(normalized)) score += 50;
    if (artist.contains(normalized)) score += 35;
    if (album.contains(normalized)) score += 15;

    // Real playback is preferred.
    if (track.url.trim().isNotEmpty) score += 30;

    // Full tracks are preferred over previews.
    if (!track.preview) score += 35;

    // Explicit provider-authorized download.
    if (track.dl.trim().isNotEmpty) score += 25;

    // Complete metadata.
    if (track.album.trim().isNotEmpty) score += 5;
    if (track.image.trim().isNotEmpty) score += 4;
    if ((track.durationMs ?? 0) > 0) score += 3;

    return score;
  }

  all.sort((a, b) => score(b).compareTo(score(a)));

  // Collapse identical title/artist results while retaining
  // the highest-ranked provider result.
  final selected = <String, Track>{};

  for (final track in all) {
    final title = norm(track.title);
    final artist = norm(track.artist);

    if (title.isEmpty) continue;

    final key = '$title|$artist';
    final previous = selected[key];

    if (previous == null || score(track) > score(previous)) {
      selected[key] = track;
    }
  }

  final output = selected.values.toList();
  output.sort((a, b) => score(b).compareTo(score(a)));

  return output;
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
