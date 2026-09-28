import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'api.dart';
import 'cloud.dart';
import 'common.dart';
import 'state.dart';

class ArtistPage extends StatelessWidget {
  final String name, image;
  const ArtistPage(this.name, this.image, {super.key});
  @override
  Widget build(BuildContext context) {
    final s = context.watch<AppState>();
    final following = s.follows.contains(name);
    return Scaffold(
      body: FutureBuilder<List<Track>>(
        future: searchAll(name, s.country),
        builder: (c, snap) {
          final all = snap.data ?? [];
          final l = all.where((t) => t.artist.toLowerCase().contains(name.toLowerCase())).toList();
          final list = l.isEmpty ? all : l;
          return CustomScrollView(slivers: [
            SliverAppBar(
              expandedHeight: 280, pinned: true,
              flexibleSpace: FlexibleSpaceBar(
                title: Text(name, style: const TextStyle(fontWeight: FontWeight.w800)),
                background: Stack(fit: StackFit.expand, children: [
                  image.isEmpty ? Container(color: Colors.deepPurple.shade900) : art(image, 400, r: 0),
                  const DecoratedBox(decoration: BoxDecoration(gradient: LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: [Colors.transparent, Colors.black87]))),
                ]),
              ),
            ),
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Row(children: [
                  OutlinedButton(onPressed: () => s.toggleFollow(name), child: Text(following ? 'Following' : 'Follow')),
                  const Spacer(),
                  IconButton(icon: const Icon(Icons.shuffle_rounded), onPressed: list.isEmpty ? null : () { s.shuffle = true; s.play(list, 0); }),
                  IconButton.filled(icon: const Icon(Icons.play_arrow_rounded), onPressed: list.isEmpty ? null : () => s.play(list, 0)),
                ]),
              ),
            ),
            if (snap.connectionState != ConnectionState.done) const SliverToBoxAdapter(child: Padding(padding: EdgeInsets.all(40), child: Center(child: CircularProgressIndicator()))),
            const SliverToBoxAdapter(child: Padding(padding: EdgeInsets.fromLTRB(16, 0, 16, 4), child: Text('Popular', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800)))),
            SliverList.builder(itemCount: list.length, itemBuilder: (_, i) => TrackTile(list, i)),
            const SliverToBoxAdapter(child: SizedBox(height: 120)),
          ]);
        },
      ),
    );
  }
}

class LibraryTab extends StatelessWidget {
  const LibraryTab({super.key});
  @override
  Widget build(BuildContext context) {
    final s = context.watch<AppState>();
    Widget list(List<Track> l, String empty) => l.isEmpty ? Center(child: Text(empty)) : ListView.builder(padding: const EdgeInsets.only(bottom: 120), itemCount: l.length, itemBuilder: (_, i) => TrackTile(l, i));
    return DefaultTabController(
      length: 6,
      child: Column(children: [
        const TabBar(isScrollable: true, tabAlignment: TabAlignment.start, tabs: [Tab(text: 'Liked'), Tab(text: 'Playlists'), Tab(text: 'Downloads'), Tab(text: 'On device'), Tab(text: 'Following'), Tab(text: 'History')]),
        Expanded(
          child: TabBarView(children: [
            list(s.likes.values.toList(), 'No liked songs yet'),
            ListView(children: [
              ListTile(leading: const Icon(Icons.add_box_outlined), title: const Text('New playlist'), onTap: () async { final n = await askName(context); if (n != null) s.createPlaylist(n); }),
              if (cloudOn) ListTile(leading: const Icon(Icons.download_for_offline_outlined), title: const Text('Import shared playlist'), onTap: () async {
                final n = await askName(context, title: 'Paste share code');
                if (n != null && context.mounted) toast(context, await s.importPlaylist(n.trim()) ? 'Playlist imported' : 'Code not found');
              }),
              for (final e in s.playlists.entries)
                ListTile(leading: const Icon(Icons.queue_music), title: Text(e.key), subtitle: Text('${e.value.length} songs'),
                    trailing: IconButton(icon: const Icon(Icons.delete_outline), onPressed: () => s.deletePlaylist(e.key)),
                    onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => PlaylistPage(e.key)))),
            ]),
            list(s.downloads.values.toList(), 'Nothing downloaded yet'),
            Column(children: [
              ListTile(leading: const Icon(Icons.folder_open), title: const Text('Add music from this device'), onTap: s.addLocal),
              Expanded(child: list(s.local, 'Your own MP3, M4A and WAV files show up here')),
            ]),
            s.follows.isEmpty
                ? const Center(child: Text('Follow artists from any track menu'))
                : ListView(children: [for (final a in s.follows) ListTile(leading: CircleAvatar(child: Text(a[0].toUpperCase())), title: Text(a), onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => ArtistPage(a, ''))))]),
            list(s.recent, 'Nothing played yet'),
          ]),
        ),
      ]),
    );
  }
}

class PlaylistPage extends StatelessWidget {
  final String name;
  const PlaylistPage(this.name, {super.key});
  @override
  Widget build(BuildContext context) {
    final s = context.watch<AppState>();
    final l = s.playlists[name] ?? [];
    return Scaffold(
      appBar: AppBar(title: Text(name), actions: [
        if (cloudOn && s.user != null)
          IconButton(icon: const Icon(Icons.ios_share), onPressed: () async {
            final id = await s.sharePlaylist(name);
            if (!context.mounted) return;
            if (id == null) return toast(context, 'Could not share');
            Clipboard.setData(ClipboardData(text: id));
            toast(context, 'Share code copied. Friends can import it in Library.');
          }),
        IconButton(icon: const Icon(Icons.play_arrow), onPressed: l.isEmpty ? null : () => s.play(l, 0)),
      ]),
      body: l.isEmpty
          ? const Center(child: Text('Empty playlist'))
          : ReorderableListView.builder(
              itemCount: l.length,
              onReorder: (a, b) => s.reorder(name, a, b),
              itemBuilder: (_, i) => Dismissible(key: ValueKey(l[i].id), onDismissed: (_) => s.removeFromPlaylist(name, l[i]), background: Container(color: Colors.red), child: TrackTile(l, i)),
            ),
    );
  }
}

class AccountPage extends StatefulWidget {
  const AccountPage({super.key});
  @override
  State<AccountPage> createState() => _AccountPageState();
}

class _AccountPageState extends State<AccountPage> {
  final e = TextEditingController(), p = TextEditingController();
  String? err;
  bool busy = false;
  Future<void> go(bool up) async {
    setState(() => busy = true);
    final r = await context.read<AppState>().auth(e.text.trim(), p.text, up);
    if (mounted) setState(() { err = r; busy = false; });
  }

  @override
  Widget build(BuildContext context) {
    final s = context.watch<AppState>();
    return Scaffold(
      appBar: AppBar(title: const Text('Account')),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: !cloudOn
            ? const Text('Cloud sync is not configured in this build. Add SUPABASE_URL and SUPABASE_ANON_KEY as build secrets. The app works fully as a guest.')
            : s.user != null
                ? Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text('Signed in as ${s.user!.email}', style: const TextStyle(fontSize: 16)),
                    const SizedBox(height: 8),
                    const Text('Likes, playlists and followed artists sync across your devices.'),
                    const SizedBox(height: 24),
                    FilledButton(onPressed: s.signOut, child: const Text('Sign out')),
                  ])
                : Column(children: [
                    const Text('Sign in to sync your library across devices.'),
                    const SizedBox(height: 16),
                    TextField(controller: e, keyboardType: TextInputType.emailAddress, decoration: const InputDecoration(labelText: 'Email')),
                    TextField(controller: p, obscureText: true, decoration: const InputDecoration(labelText: 'Password')),
                    if (err != null) Padding(padding: const EdgeInsets.only(top: 12), child: Text(err!, style: const TextStyle(color: Colors.redAccent))),
                    const SizedBox(height: 20),
                    Row(children: [
                      Expanded(child: FilledButton(onPressed: busy ? null : () => go(false), child: const Text('Sign in'))),
                      const SizedBox(width: 12),
                      Expanded(child: OutlinedButton(onPressed: busy ? null : () => go(true), child: const Text('Create account'))),
                    ]),
                  ]),
      ),
    );
  }
}
