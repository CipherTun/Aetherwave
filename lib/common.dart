import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'api.dart';
import 'cloud.dart';
import 'more.dart';
import 'state.dart';

const accent = Color(0xFFB39DFF);

Widget art(String url, double s, {double r = 8}) {
  Widget ph() => Container(width: s, height: s, decoration: BoxDecoration(borderRadius: BorderRadius.circular(r), gradient: const LinearGradient(colors: [Color(0xFF3B2A78), Color(0xFF17122E)])), child: const Icon(Icons.music_note, color: Colors.white38));
  return ClipRRect(borderRadius: BorderRadius.circular(r), child: CachedNetworkImage(imageUrl: url, width: s, height: s, fit: BoxFit.cover, fadeInDuration: const Duration(milliseconds: 200), placeholder: (_, __) => ph(), errorWidget: (_, __, ___) => ph()));
}

void toast(BuildContext c, String m) => ScaffoldMessenger.of(c).showSnackBar(SnackBar(content: Text(m), duration: const Duration(seconds: 2)));

void trackMenu(BuildContext context, Track t) {
  final s = context.read<AppState>();
  showModalBottomSheet(
    context: context,
    showDragHandle: true,
    builder: (_) => SafeArea(
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        ListTile(leading: art(t.image, 44), title: Text(t.title, maxLines: 1), subtitle: Text('${t.artist} · ${t.src}${t.preview ? ' · 30s preview' : ''}')),
        ListTile(leading: const Icon(Icons.queue_play_next), title: const Text('Play next'), onTap: () { s.playNext(t); Navigator.pop(context); toast(context, 'Added to play next'); }),
        ListTile(leading: const Icon(Icons.playlist_add), title: const Text('Add to queue'), onTap: () { s.addToQueue(t); Navigator.pop(context); toast(context, 'Added to queue'); }),
        ListTile(leading: const Icon(Icons.library_add), title: const Text('Add to playlist'), onTap: () { Navigator.pop(context); pickPlaylist(context, t); }),
        ListTile(leading: const Icon(Icons.radio), title: const Text('Start radio'), onTap: () { Navigator.pop(context); s.startRadio(t); }),
        ListTile(leading: const Icon(Icons.person_outline), title: const Text('Go to artist'), onTap: () { Navigator.pop(context); Navigator.push(context, MaterialPageRoute(builder: (_) => ArtistPage(t.artist, t.image))); }),
        if (s.canDownload(t))
          ListTile(
              leading: Icon(s.isDownloaded(t) ? Icons.delete_outline : Icons.download),
              title: Text(s.isDownloaded(t) ? 'Remove download' : 'Download'),
              onTap: () { s.isDownloaded(t) ? s.removeDownload(t) : s.download(t); Navigator.pop(context); })
        else
          const ListTile(leading: Icon(Icons.info_outline), title: Text('Download not permitted by this source')),
      ]),
    ),
  );
}

void pickPlaylist(BuildContext context, Track t) {
  final s = context.read<AppState>();
  showModalBottomSheet(
    context: context,
    showDragHandle: true,
    builder: (_) => SafeArea(
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        ListTile(leading: const Icon(Icons.add), title: const Text('New playlist'), onTap: () async {
          Navigator.pop(context);
          final n = await askName(context);
          if (n != null && n.trim().isNotEmpty) { s.createPlaylist(n); s.addToPlaylist(n.trim(), t); }
        }),
        for (final n in s.playlists.keys) ListTile(leading: const Icon(Icons.queue_music), title: Text(n), onTap: () { s.addToPlaylist(n, t); Navigator.pop(context); toast(context, 'Added to $n'); }),
      ]),
    ),
  );
}

Future<String?> askName(BuildContext context, {String title = 'Playlist name'}) {
  final c = TextEditingController();
  return showDialog<String>(
    context: context,
    builder: (_) => AlertDialog(
      title: Text(title),
      content: TextField(controller: c, autofocus: true),
      actions: [TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')), FilledButton(onPressed: () => Navigator.pop(context, c.text), child: const Text('OK'))],
    ),
  );
}

class TrackTile extends StatelessWidget {
  final List<Track> list;
  final int i;
  const TrackTile(this.list, this.i, {super.key});
  @override
  Widget build(BuildContext context) {
    final s = context.watch<AppState>();
    final t = list[i];
    final liked = s.likes.containsKey(t.id);
    final playing = s.current?.id == t.id;
    return ListTile(
      leading: art(t.image, 50),
      title: Text(t.title, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(color: playing ? accent : null, fontWeight: FontWeight.w600)),
      subtitle: Row(children: [
        if (s.isDownloaded(t)) const Padding(padding: EdgeInsets.only(right: 4), child: Icon(Icons.download_done, size: 14, color: Colors.greenAccent)),
        Expanded(child: Text('${t.artist} · ${t.src}', maxLines: 1, overflow: TextOverflow.ellipsis)),
      ]),
      onTap: () => s.play(list, i),
      trailing: Row(mainAxisSize: MainAxisSize.min, children: [
        s.progress.containsKey(t.id)
            ? SizedBox(width: 24, height: 24, child: CircularProgressIndicator(strokeWidth: 2, value: s.progress[t.id]))
            : IconButton(icon: Icon(liked ? Icons.favorite : Icons.favorite_border, color: liked ? Colors.pinkAccent : null), onPressed: () => s.toggleLike(t)),
        IconButton(icon: const Icon(Icons.more_vert), onPressed: () => trackMenu(context, t)),
      ]),
    );
  }
}

class CountryPage extends StatefulWidget {
  const CountryPage({super.key});
  @override
  State<CountryPage> createState() => _CountryPageState();
}

class _CountryPageState extends State<CountryPage> {
  late final Future<List<Country>> f = fetchCountries();
  String q = '';
  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('Choose your country')),
        body: FutureBuilder<List<Country>>(
          future: f,
          builder: (c, snap) {
            if (snap.hasError) return const Center(child: Text('Could not load countries. Check your connection.'));
            if (!snap.hasData) return const Center(child: CircularProgressIndicator());
            final l = snap.data!.where((e) => e.name.toLowerCase().contains(q.toLowerCase())).toList();
            return Column(children: [
              Padding(padding: const EdgeInsets.all(12), child: TextField(decoration: const InputDecoration(prefixIcon: Icon(Icons.search), hintText: 'Search country'), onChanged: (v) => setState(() => q = v))),
              Expanded(
                child: ListView.builder(
                  itemCount: l.length,
                  itemBuilder: (_, i) => ListTile(
                    leading: Text(l[i].flag, style: const TextStyle(fontSize: 24)),
                    title: Text(l[i].name),
                    trailing: context.read<AppState>().country == l[i].code ? const Icon(Icons.check, color: accent) : null,
                    onTap: () { context.read<AppState>().setCountry(l[i]); Navigator.pop(context); },
                  ),
                ),
              ),
            ]);
          },
        ),
      );
}

void settingsSheet(BuildContext context) {
  final s = context.read<AppState>();
  showModalBottomSheet(
    context: context,
    showDragHandle: true,
    builder: (_) => SafeArea(
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        const ListTile(title: Text('Appearance', style: TextStyle(fontWeight: FontWeight.bold))),
        for (final m in ThemeMode.values) ListTile(title: Text(m.name[0].toUpperCase() + m.name.substring(1)), leading: Icon(s.theme == m ? Icons.radio_button_checked : Icons.radio_button_off), onTap: () { s.setTheme(m); Navigator.pop(context); }),
        ListTile(leading: const Icon(Icons.cloud_outlined), title: Text(cloudOn ? (s.user?.email ?? 'Sign in to sync') : 'Account (guest mode)'), onTap: () { Navigator.pop(context); Navigator.push(context, MaterialPageRoute(builder: (_) => const AccountPage())); }),
        const ListTile(leading: Icon(Icons.info_outline), title: Text('Aetherwave 1.0.0'), subtitle: Text('Sources: iTunes, Jamendo, Audius, Deezer (search covers all, worldwide). Preview-only sources play 30 seconds.')),
      ]),
    ),
  );
}

void queueSheet(BuildContext context) {
  final s = context.read<AppState>();
  showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (_) => DraggableScrollableSheet(
      expand: false,
      initialChildSize: 0.7,
      builder: (_, sc) => ListView.builder(
        controller: sc,
        itemCount: s.queue.length,
        itemBuilder: (_, i) => ListTile(
          leading: art(s.queue[i].image, 44),
          title: Text(s.queue[i].title, maxLines: 1, style: TextStyle(color: i == s.index ? accent : null)),
          subtitle: Text(s.queue[i].artist, maxLines: 1),
          onTap: () { s.play(s.queue, i); Navigator.pop(context); },
        ),
      ),
    ),
  );
}

void lyricsSheet(BuildContext context, Track t) {
  showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    builder: (_) => DraggableScrollableSheet(
      expand: false,
      initialChildSize: 0.8,
      builder: (_, sc) => FutureBuilder<String?>(
        future: fetchLyrics(t),
        builder: (c, snap) {
          if (snap.connectionState != ConnectionState.done) return const Center(child: CircularProgressIndicator());
          final l = snap.data;
          return SingleChildScrollView(controller: sc, padding: const EdgeInsets.all(24), child: Text(l == null || l.isEmpty ? 'No lyrics found for this track.' : l, style: const TextStyle(fontSize: 18, height: 1.6)));
        },
      ),
    ),
  );
}

void sleepSheet(BuildContext context) {
  final s = context.read<AppState>();
  showModalBottomSheet(
    context: context,
    showDragHandle: true,
    builder: (_) => SafeArea(
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        const ListTile(title: Text('Sleep timer', style: TextStyle(fontWeight: FontWeight.bold))),
        for (final m in [15, 30, 45, 60]) ListTile(title: Text('$m minutes'), onTap: () { s.setSleep(m); Navigator.pop(context); }),
        ListTile(title: const Text('Off'), onTap: () { s.setSleep(null); Navigator.pop(context); }),
      ]),
    ),
  );
}
