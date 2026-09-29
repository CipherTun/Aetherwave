import 'dart:convert';

import 'package:http/http.dart' as http;

const String audiomackRapidApiKey =
    String.fromEnvironment('RAPIDAPI_KEY');

const String audiomackRapidApiHost =
    'audiomack-scraper.p.rapidapi.com';

const String audiomackRapidApiBase =
    'https://audiomack-scraper.p.rapidapi.com';

class AudiomackResult {
  final Map<String, dynamic> data;

  const AudiomackResult(this.data);

  List<dynamic> get results {
    final value = data['results'] ??
        data['data'] ??
        data['tracks'] ??
        data['songs'] ??
        data['items'];

    if (value is List) {
      return value;
    }

    if (value is Map<String, dynamic>) {
      final nested = value['results'] ??
          value['data'] ??
          value['tracks'] ??
          value['songs'] ??
          value['items'];

      if (nested is List) {
        return nested;
      }
    }

    return const [];
  }
}

class AudiomackApi {
  static bool get configured =>
      audiomackRapidApiKey.trim().isNotEmpty;

  static Map<String, String> get _headers => {
        'x-rapidapi-key': audiomackRapidApiKey,
        'x-rapidapi-host': audiomackRapidApiHost,
        'accept': 'application/json',
      };

  static Future<AudiomackResult?> _get(
    String path, {
    Map<String, String>? query,
  }) async {
    if (!configured) {
      return null;
    }

    final uri = Uri.parse(
      '$audiomackRapidApiBase$path',
    ).replace(queryParameters: query);

    final response = await http.get(
      uri,
      headers: _headers,
    );

    if (response.statusCode < 200 ||
        response.statusCode >= 300) {
      return null;
    }

    final decoded = jsonDecode(response.body);

    if (decoded is Map<String, dynamic>) {
      return AudiomackResult(decoded);
    }

    return null;
  }

  static Future<AudiomackResult?> search(
    String query, {
    String sortBy = 'relevant',
    int page = 1,
  }) {
    return _get(
      '/audiomack/search',
      query: {
        'q': query,
        'sortBy': sortBy,
        'page': '$page',
      },
    );
  }

  static Future<AudiomackResult?> songByUrl(
    String url,
  ) {
    return _get(
      '/audiomack/song',
      query: {'url': url},
    );
  }

  static Future<AudiomackResult?> albumByUrl(
    String url,
  ) {
    return _get(
      '/audiomack/album',
      query: {'url': url},
    );
  }

  static Future<AudiomackResult?> playlistByUrl(
    String url,
  ) {
    return _get(
      '/audiomack/playlist',
      query: {'url': url},
    );
  }

  static Future<AudiomackResult?> artist(
    String artistSlug,
  ) {
    return _get(
      '/audiomack/artist/${Uri.encodeComponent(artistSlug)}',
    );
  }

  static Future<AudiomackResult?> albumCharts({
    String country = 'US',
    int page = 1,
    String? genre,
  }) {
    return _get(
      '/audiomack/charts/albums',
      query: {
        'country': country,
        'page': '$page',
        if (genre != null && genre.trim().isNotEmpty)
          'genre': genre,
      },
    );
  }

  static Future<AudiomackResult?> play(
    String songId,
  ) {
    return _get(
      '/audiomack/song/${Uri.encodeComponent(songId)}/play',
    );
  }

  static dynamic _findUrl(dynamic value) {
    if (value is String) {
      final trimmed = value.trim();

      if (trimmed.startsWith('https://') ||
          trimmed.startsWith('http://')) {
        return trimmed;
      }

      return null;
    }

    if (value is List) {
      for (final item in value) {
        final found = _findUrl(item);

        if (found != null) {
          return found;
        }
      }
    }

    if (value is Map) {
      const preferredKeys = [
        'url',
        'stream_url',
        'streamUrl',
        'audio_url',
        'audioUrl',
        'play_url',
        'playUrl',
        'download_url',
        'downloadUrl',
        'file_url',
        'fileUrl',
        'src',
      ];

      for (final key in preferredKeys) {
        if (value.containsKey(key)) {
          final found = _findUrl(value[key]);

          if (found != null) {
            return found;
          }
        }
      }

      for (final entry in value.entries) {
        final found = _findUrl(entry.value);

        if (found != null) {
          return found;
        }
      }
    }

    return null;
  }

  static String? extractUrl(AudiomackResult? response) {
    if (response == null) {
      return null;
    }

    final found = _findUrl(response.data);

    return found is String ? found : null;
  }

  static String? extractSongId(
    Map<String, dynamic> item,
  ) {
    final value = item['id'] ??
        item['song_id'] ??
        item['songId'] ??
        item['music_id'] ??
        item['musicId'];

    if (value == null) {
      return null;
    }

    return '$value';
  }

  static String? extractTitle(
    Map<String, dynamic> item,
  ) {
    final value = item['title'] ??
        item['name'] ??
        item['song_title'];

    return value?.toString();
  }

  static String? extractArtist(
    Map<String, dynamic> item,
  ) {
    final artist = item['artist'] ??
        item['artist_name'] ??
        item['artistName'];

    if (artist is String) {
      return artist;
    }

    if (artist is Map) {
      return (artist['name'] ??
              artist['title'] ??
              artist['username'])
          ?.toString();
    }

    return null;
  }

  static String? extractArtwork(
    Map<String, dynamic> item,
  ) {
    final value = item['image'] ??
        item['artwork'] ??
        item['thumbnail'] ??
        item['cover'] ??
        item['cover_art'] ??
        item['coverArt'];

    if (value is String) {
      return value;
    }

    if (value is Map) {
      return (value['url'] ??
              value['src'] ??
              value['original'] ??
              value['large'] ??
              value['medium'])
          ?.toString();
    }

    return null;
  }

  static String? extractDurationMs(
    Map<String, dynamic> item,
  ) {
    final value = item['duration_ms'] ??
        item['durationMs'] ??
        item['duration'];

    if (value == null) {
      return null;
    }

    final number = num.tryParse('$value');

    if (number == null) {
      return null;
    }

    final milliseconds = number < 10000
        ? (number * 1000).round()
        : number.round();

    return '$milliseconds';
  }
}
