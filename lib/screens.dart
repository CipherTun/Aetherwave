import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'api.dart';
import 'cloud.dart';
import 'common.dart';
import 'state.dart';
import 'social.dart';

void openPlayer(BuildContext c)=>Navigator.push(c,MaterialPageRoute(builder:(_)=>const PlayerPage()));

class Shell extends StatefulWidget {
  const Shell({super.key});

  @override
  State<Shell> createState() => _ShellState();
}

class _ShellState extends State<Shell> {
  int tab = 0;

  static const pages = <Widget>[
    HomeTab(),
    DiscoverTab(),
    SearchTab(),
    LibraryTab(),
  ];

  @override
  Widget build(BuildContext context) {
    final state = context.watch<AppState>();

    return Scaffold(
      body: Stack(
        children: [
          IndexedStack(
            index: tab,
            children: pages,
          ),
          if (state.current != null)
            const Positioned(
              left: 0,
              right: 0,
              bottom: 82,
              child: MiniPlayer(),
            ),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: tab,
        onDestinationSelected: (index) {
          if (index == tab) return;

          setState(() {
            tab = index;
          });
        },
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.home_outlined),
            selectedIcon: Icon(Icons.home_rounded),
            label: 'Home',
          ),
          NavigationDestination(
            icon: Icon(Icons.explore_outlined),
            selectedIcon: Icon(Icons.explore_rounded),
            label: 'Discover',
          ),
          NavigationDestination(
            icon: Icon(Icons.search_outlined),
            selectedIcon: Icon(Icons.search_rounded),
            label: 'Search',
          ),
          NavigationDestination(
            icon: Icon(Icons.library_music_outlined),
            selectedIcon: Icon(Icons.library_music_rounded),
            label: 'Library',
          ),
        ],
      ),
    );
  }
}

class DiscoverTab extends StatelessWidget {
  const DiscoverTab({super.key});

  @override
  Widget build(BuildContext context) {
    final state = context.watch<AppState>();

    return CustomScrollView(
      slivers: [
        SliverAppBar(
          pinned: true,
          title: const Text('Discover'),
          actions: [
            IconButton(
              tooltip: 'Country',
              icon: const Icon(Icons.public_outlined),
              onPressed: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => const CountryPage(),
                  ),
                );
              },
            ),
          ],
        ),
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(
              20,
              10,
              20,
              8,
            ),
            child: Text(
              'Find something new',
              style: Theme.of(context)
                  .textTheme
                  .headlineSmall
                  ?.copyWith(
                    fontWeight: FontWeight.w800,
                  ),
            ),
          ),
        ),
        SliverToBoxAdapter(
          child: Padding(
            padding: const EdgeInsets.symmetric(
              horizontal: 16,
            ),
            child: Wrap(
              spacing: 8,
              runSpacing: 8,
              children: const [
                ActionChip(
                  avatar: Icon(
                    Icons.trending_up_rounded,
                    size: 18,
                  ),
                  label: Text('Trending'),
                ),
                ActionChip(
                  avatar: Icon(
                    Icons.fiber_new_rounded,
                    size: 18,
                  ),
                  label: Text('New music'),
                ),
                ActionChip(
                  avatar: Icon(
                    Icons.category_outlined,
                    size: 18,
                  ),
                  label: Text('Genres'),
                ),
                ActionChip(
                  avatar: Icon(
                    Icons.radio_rounded,
                    size: 18,
                  ),
                  label: Text('Radio'),
                ),
              ],
            ),
          ),
        ),
        SliverToBoxAdapter(
          child: ShelfView(
            Shelf(
              'Trending now',
              Style.rank,
              () => appleChart(
                state.country,
              ).catchError(
                (_) => <Track>[],
              ),
            ),
          ),
        ),
        SliverToBoxAdapter(
          child: ShelfView(
            Shelf(
              'Fresh discoveries',
              Style.cards,
              () => jamendo().catchError(
                (_) => <Track>[],
              ),
            ),
          ),
        ),
        SliverToBoxAdapter(
          child: ShelfView(
            Shelf(
              'Independent music',
              Style.cards,
              () => freeToUseSearch('music'),
            ),
          ),
        ),
        SliverToBoxAdapter(
          child: ShelfView(
            Shelf(
              'Creative Commons',
              Style.cards,
              () => ccMixterSearch('music'),
            ),
          ),
        ),
        const SliverToBoxAdapter(
          child: SizedBox(height: 150),
        ),
      ],
    );
  }
}

class HomeTab extends StatelessWidget{const HomeTab({super.key});String greet(){final h=DateTime.now().hour;return h<12?'Good morning':h<18?'Good afternoon':'Good evening';}@override Widget build(BuildContext c){final s=c.watch<AppState>();final rec=s.recommendations();return ListView(padding:const EdgeInsets.only(bottom:150),children:[Padding(padding:const EdgeInsets.fromLTRB(20,18,8,10),child:Row(children:[Expanded(child:Text(greet(),style:const TextStyle(fontSize:27,fontWeight:FontWeight.w800))),IconButton(icon:const Icon(Icons.public),tooltip:s.countryLabel,onPressed:()=>Navigator.push(c,MaterialPageRoute(builder:(_)=>const CountryPage()))),IconButton(icon:const Icon(Icons.person_outline),onPressed:()=>Navigator.push(c,MaterialPageRoute(builder:(_)=>const AccountPage()))) ])),Padding(padding:const EdgeInsets.symmetric(horizontal:16),child:FilledButton.tonalIcon(onPressed:()=>Navigator.push(c,MaterialPageRoute(builder:(_)=>const SearchPage())),icon:const Icon(Icons.search),label:const Align(alignment:Alignment.centerLeft,child:Text('Search songs, artists, albums & podcasts')))),if(rec.isNotEmpty)ShelfView(Shelf('Made for you',Style.hero,()=>Future.value(rec))),ShelfView(Shelf('Trending in ${s.countryLabel.isEmpty?s.country:s.countryLabel}',Style.rank,()=>appleChart(s.country).catchError((_) => <Track>[]))),ShelfView(Shelf('Fresh discoveries',Style.cards,()=>jamendo().catchError((_) => <Track>[]))),ShelfView(Shelf('Trending now',Style.cards,()=>audius().catchError((_) => <Track>[]))),const SizedBox(height:20),Card(margin:const EdgeInsets.all(16),child:ListTile(leading:const Icon(Icons.auto_awesome),title:const Text('Tune your recommendations'),subtitle:const Text('Choose genres in Settings and like more music to personalize Home.'),trailing:const Icon(Icons.chevron_right),onTap:()=>Navigator.push(c,MaterialPageRoute(builder:(_)=>const SettingsPage()))))]);}}

class ShelfView extends StatefulWidget {
  final Shelf shelf;
  const ShelfView(this.shelf, {super.key});
  @override State<ShelfView> createState() => _ShelfViewState();
}
class _ShelfViewState extends State<ShelfView> {
  late Future<List<Track>> future = widget.shelf.load();
  @override Widget build(BuildContext context) => FutureBuilder<List<Track>>(
    future: future,
    builder: (context, snap) {
      if (snap.connectionState != ConnectionState.done) return const SizedBox(height: 180, child: Center(child: CircularProgressIndicator()));
      final list = snap.data ?? [];
      if (list.isEmpty) return const SizedBox.shrink();
      final state = context.read<AppState>();
      final rank = widget.shelf.style == Style.rank;
      final hero = widget.shelf.style == Style.hero;
      return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Padding(padding: const EdgeInsets.fromLTRB(20, 22, 16, 10), child: Text(widget.shelf.title, style: const TextStyle(fontSize: 21, fontWeight: FontWeight.w800))),
        if (rank)
          for (var i = 0; i < list.length && i < 10; i++)
            ListTile(
              leading: Row(mainAxisSize: MainAxisSize.min, children: [SizedBox(width: 28, child: Text('${i + 1}', style: const TextStyle(fontWeight: FontWeight.w800))), art(list[i].image, 50)]),
              title: Text(list[i].title, maxLines: 1, overflow: TextOverflow.ellipsis),
              subtitle: Text(list[i].artist),
              onTap: () => state.play(list, i),
              trailing: IconButton(icon: const Icon(Icons.more_horiz), onPressed: () => trackMenu(context, list[i])),
            )
        else
          SizedBox(
            height: hero ? 245 : 195,
            child: ListView.builder(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 14),
              itemCount: list.length,
              itemBuilder: (_, i) => GestureDetector(
                onTap: () => state.play(list, i),
                onLongPress: () => trackMenu(context, list[i]),
                child: SizedBox(
                  width: hero ? 290 : 145,
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 6),
                    child: hero
                      ? ClipRRect(
                          borderRadius: BorderRadius.circular(20),
                          child: Stack(fit: StackFit.expand, children: [
                            art(list[i].image, 290, r: 0),
                            const DecoratedBox(decoration: BoxDecoration(gradient: LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: [Colors.transparent, Colors.black87]))),
                            Positioned(left: 16, right: 10, bottom: 14, child: Text('${list[i].title}\n${list[i].artist}', maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800))),
                          ]),
                        )
                      : Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                          art(list[i].image, 145, r: 14),
                          const SizedBox(height: 7),
                          Text(list[i].title, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700)),
                          Text(list[i].artist, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12, color: Colors.white60)),
                        ]),
                  ),
                ),
              ),
            ),
          ),
      ]);
    },
  );
}

class SearchPage extends StatelessWidget{const SearchPage({super.key});@override Widget build(BuildContext c)=>Scaffold(appBar:AppBar(title:const Text('Search')),body:const SearchTab());}
class SearchTab extends StatefulWidget {
  const SearchTab({super.key});
  @override State<SearchTab> createState() => _SearchTabState();
}
class _SearchTabState extends State<SearchTab> {
  final query = TextEditingController();
  Timer? timer;
  Future<List<Track>>? tracks;
  Future<List<PodcastEpisode>>? podcasts;
  String filter = 'All';
  void run(String value) {
    timer?.cancel();
    final q = value.trim();
    if (q.isEmpty) { setState(() { tracks = null; podcasts = null; }); return; }
    timer = Timer(const Duration(milliseconds: 350), () {
      final state = context.read<AppState>();
      state.addSearch(q);
      setState(() { tracks = searchAll(q, state.country); podcasts = podcastSearch(q); });
    });
  }
  @override Widget build(BuildContext context) {
    final state = context.watch<AppState>();
    return Column(children: [
      Padding(padding: const EdgeInsets.fromLTRB(16, 14, 16, 8), child: TextField(
        controller: query,
        onChanged: run,
        decoration: InputDecoration(prefixIcon: const Icon(Icons.search), hintText: 'Search songs, artists, albums & podcasts', filled: true, fillColor: Colors.white10, border: OutlineInputBorder(borderRadius: BorderRadius.circular(28), borderSide: BorderSide.none)),
      )),
      SingleChildScrollView(scrollDirection: Axis.horizontal, padding: const EdgeInsets.symmetric(horizontal: 12), child: Row(children: [
        for (final x in ['All', 'Songs', 'Artists', 'Albums', 'Podcasts']) Padding(padding: const EdgeInsets.symmetric(horizontal: 4), child: ChoiceChip(label: Text(x), selected: filter == x, onSelected: (_) => setState(() => filter = x))),
      ])),
      Expanded(child: tracks == null && podcasts == null
        ? ListView(children: [
            if (state.searches.isNotEmpty) Padding(padding: const EdgeInsets.fromLTRB(20, 20, 20, 8), child: Row(children: [const Text('Recent searches', style: TextStyle(fontWeight: FontWeight.w800)), const Spacer(), TextButton(onPressed: state.clearSearches, child: const Text('Clear'))])),
            for (final x in state.searches) ListTile(leading: const Icon(Icons.history), title: Text(x), onTap: () { query.text = x; run(x); }),
          ])
        : FutureBuilder<List<Track>>(future: tracks, builder: (context, snap) {
            final list = snap.data ?? [];
            final artists = <String>[];
            final albums = <String>[];
            for (final t in list) { if (!artists.contains(t.artist)) artists.add(t.artist); if (t.album.isNotEmpty && !albums.contains(t.album)) albums.add(t.album); }
            final showTracks = filter == 'All' || filter == 'Songs';
            final showArtists = filter == 'All' || filter == 'Artists';
            final showAlbums = filter == 'All' || filter == 'Albums';
            return ListView(children: [
              if (showArtists && artists.isNotEmpty) Section(title: 'Artists', child: Wrap(spacing: 8, children: [for (final a in artists.take(8)) ActionChip(label: Text(a), onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => ArtistPage(a, list.firstWhere((t) => t.artist == a).image))))])),
              if (showAlbums && albums.isNotEmpty) Section(title: 'Albums', child: Wrap(spacing: 8, children: [for (final a in albums.take(8)) ActionChip(label: Text(a), onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => AlbumPage(a, list.firstWhere((t) => t.album == a).artist))))])),
              if (showTracks) for (var i = 0; i < list.length; i++) TrackTile(list, i),
              if ((filter == 'All' || filter == 'Podcasts') && podcastIndexKey.isNotEmpty) FutureBuilder<List<PodcastEpisode>>(
                future: podcasts,
                builder: (context, p) {
                  final items = p.data ?? [];
                  return Section(title: 'Podcasts', child: Column(children: [for (final e in items) ListTile(leading: art(e.image, 50), title: Text(e.title), subtitle: Text(e.show), onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => PodcastPage(e))))]));
                },
              ),
            ]);
          }),
      ),
    ]);
  }
}

class Section extends StatelessWidget{final String title;final Widget child;const Section({super.key,required this.title,required this.child});@override Widget build(BuildContext c)=>Padding(padding:const EdgeInsets.fromLTRB(16,18,16,4),child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[Text(title,style:const TextStyle(fontSize:18,fontWeight:FontWeight.w800)),const SizedBox(height:8),child]));}

class LibraryTab extends StatelessWidget {
  const LibraryTab({super.key});

  @override
  Widget build(BuildContext context) {
    final state = context.watch<AppState>();
    final items = <_LibraryEntry>[
      _LibraryEntry('Liked', Icons.favorite_border, _list(state.sortedLibrary(state.likes.values), 'No liked songs yet')),
      _LibraryEntry('Downloads', Icons.download_outlined, _list(state.sortedLibrary(state.downloads.values), 'No downloads yet')),
      _LibraryEntry('Playlists', Icons.queue_music_outlined, PlaylistList(state)),
      _LibraryEntry('Tracks', Icons.music_note_outlined, _list(state.sortedLibrary([...state.likes.values, ...state.downloads.values, ...state.recent]), 'Your saved tracks will appear here')),
      _LibraryEntry('Albums', Icons.album_outlined, AlbumLibrary(state)),
      _LibraryEntry('Artists', Icons.person_outline, ArtistLibrary(state)),
      _LibraryEntry('Podcasts', Icons.podcasts_outlined, const PodcastLibrary()),
      _LibraryEntry('Stations', Icons.radio_outlined, const StationLibrary()),
      _LibraryEntry('History', Icons.history, _list(state.sortedLibrary(state.recent), 'Nothing played yet')),
      _LibraryEntry('On device', Icons.folder_open_outlined, Column(children: [ListTile(leading: const Icon(Icons.library_add_outlined), title: const Text('Import audio from this device'), onTap: state.addLocal), Expanded(child: _list(state.local, 'No local audio yet'))])),
    ];

    return SafeArea(
      child: DefaultTabController(
        length: 2,
        child: NestedScrollView(
          headerSliverBuilder: (_, __) => [
            SliverAppBar(
              pinned: true,
              title: const Text('Your Library'),
              actions: [
                IconButton(tooltip: 'Settings', icon: const Icon(Icons.tune_outlined), onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const SettingsPage()))),
              ],
              bottom: const TabBar(tabs: [Tab(text: 'Collections'), Tab(text: 'Activity')]),
            ),
          ],
          body: TabBarView(children: [
            ListView(
              padding: const EdgeInsets.fromLTRB(16, 18, 16, 140),
              children: [
                GridView.builder(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  itemCount: items.length,
                  gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(crossAxisCount: 2, mainAxisSpacing: 12, crossAxisSpacing: 12, childAspectRatio: 1.55),
                  itemBuilder: (_, i) => _LibraryCard(entry: items[i], onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => _LibrarySectionPage(entry: items[i])))),
                ),
              ],
            ),
            _list(state.sortedLibrary(state.recent), 'Your listening activity will appear here'),
          ]),
        ),
      ),
    );
  }
}

class _LibraryEntry {
  final String title;
  final IconData icon;
  final Widget page;
  const _LibraryEntry(this.title, this.icon, this.page);
}

class _LibraryCard extends StatelessWidget {
  final _LibraryEntry entry;
  final VoidCallback onTap;
  const _LibraryCard({required this.entry, required this.onTap});
  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Card(
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
            Icon(entry.icon, size: 27, color: scheme.primary),
            Row(children: [Expanded(child: Text(entry.title, style: const TextStyle(fontWeight: FontWeight.w700))), const Icon(Icons.arrow_forward_ios_rounded, size: 14)]),
          ]),
        ),
      ),
    );
  }
}

class _LibrarySectionPage extends StatelessWidget {
  final _LibraryEntry entry;
  const _LibrarySectionPage({required this.entry});
  @override
  Widget build(BuildContext context) => Scaffold(appBar: AppBar(title: Text(entry.title)), body: entry.page);
}

class PodcastLibrary extends StatelessWidget {
  const PodcastLibrary({super.key});
  @override
  Widget build(BuildContext context) => const Center(child: Padding(padding: EdgeInsets.all(32), child: Text('Followed podcasts and downloaded episodes will appear here.', textAlign: TextAlign.center)));
}

class StationLibrary extends StatelessWidget {
  const StationLibrary({super.key});
  @override
  Widget build(BuildContext context) => const Center(child: Padding(padding: EdgeInsets.all(32), child: Text('Your personalized and genre stations will appear here.', textAlign: TextAlign.center)));
}

Widget _list(List<Track> l,String empty)=>l.isEmpty?Center(child:Text(empty)):ListView.builder(padding:const EdgeInsets.only(bottom:130),itemCount:l.length,itemBuilder:(_,i)=>TrackTile(l,i));
class PlaylistList extends StatelessWidget{final AppState s;const PlaylistList(this.s,{super.key});@override Widget build(BuildContext c)=>ListView(children:[ListTile(leading:const Icon(Icons.add_box_outlined),title:const Text('New playlist'),onTap:()async{final n=await askName(c);if(n!=null)s.createPlaylist(n);}),if(cloudOn)ListTile(leading:const Icon(Icons.cloud_download_outlined),title:const Text('Import shared playlist'),onTap:()async{final n=await askName(c,title:'Paste share code');if(n!=null)toast(c,await s.importPlaylist(n.trim())?'Imported':'Not found');}),for(final e in s.playlists.entries)ListTile(leading:const Icon(Icons.queue_music),title:Text(e.key),subtitle:Text('${e.value.length} songs'),trailing:IconButton(icon:const Icon(Icons.delete_outline),onPressed:()=>s.deletePlaylist(e.key)),onTap:()=>Navigator.push(c,MaterialPageRoute(builder:(_)=>PlaylistPage(e.key))))]);}
class ArtistLibrary extends StatelessWidget{final AppState s;const ArtistLibrary(this.s,{super.key});@override Widget build(BuildContext c)=>s.follows.isEmpty?const Center(child:Text('Follow artists to see them here')):ListView(children:[for(final a in s.follows)ListTile(leading:CircleAvatar(child:Text(a.isEmpty?'?':a[0].toUpperCase())),title:Text(a),trailing:const Icon(Icons.chevron_right),onTap:()=>Navigator.push(c,MaterialPageRoute(builder:(_)=>ArtistPage(a,''))))]);}
class AlbumLibrary extends StatelessWidget{final AppState s;const AlbumLibrary(this.s,{super.key});@override Widget build(BuildContext c){final m=<String,Track>{};for(final t in [...s.likes.values,...s.downloads.values,...s.recent])if(t.album.isNotEmpty)m['${t.album}|${t.artist}']=t;return m.isEmpty?const Center(child:Text('Albums appear as you save music')):ListView(children:[for(final e in m.entries)ListTile(leading:art(e.value.image,52),title:Text(e.value.album),subtitle:Text(e.value.artist),onTap:()=>Navigator.push(c,MaterialPageRoute(builder:(_)=>AlbumPage(e.value.album,e.value.artist))))]);}}

class PlaylistPage extends StatelessWidget {
  final String name;
  const PlaylistPage(this.name, {super.key});
  @override Widget build(BuildContext context) {
    final state = context.watch<AppState>();
    final list = state.playlists[name] ?? [];
    return Scaffold(
      appBar: AppBar(title: Text(name), actions: [
        if (cloudOn && state.user != null) IconButton(icon: const Icon(Icons.share), onPressed: () async {
          final id = await state.sharePlaylist(name);
          if (id != null && context.mounted) { Clipboard.setData(ClipboardData(text: id)); toast(context, 'Share code copied'); }
        }),
        IconButton(icon: const Icon(Icons.play_arrow), onPressed: list.isEmpty ? null : () => state.play(list, 0)),
      ]),
      body: list.isEmpty ? const Center(child: Text('Empty playlist')) : ReorderableListView.builder(
        itemCount: list.length,
        onReorder: (a, b) => state.reorder(name, a, b),
        itemBuilder: (_, i) => Dismissible(
          key: ValueKey(list[i].id),
          onDismissed: (_) => state.removeFromPlaylist(name, list[i]),
          background: Container(color: Colors.red),
          child: TrackTile(list, i),
        ),
      ),
    );
  }
}

class ArtistPage extends StatelessWidget {
  final String name, image;
  const ArtistPage(this.name, this.image, {super.key});
  @override Widget build(BuildContext context) {
    final state = context.watch<AppState>();
    return Scaffold(body: FutureBuilder<List<Track>>(
      future: searchAll(name, state.country),
      builder: (context, snap) {
        final list = (snap.data ?? []).where((t) => t.artist.toLowerCase().contains(name.toLowerCase())).toList();
        return CustomScrollView(slivers: [
          SliverAppBar(expandedHeight: 250, pinned: true, flexibleSpace: FlexibleSpaceBar(
            title: Text(name),
            background: Stack(fit: StackFit.expand, children: [
              image.isEmpty ? _phArtist() : art(image, 400, r: 0),
              const DecoratedBox(decoration: BoxDecoration(gradient: LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: [Colors.transparent, Colors.black87]))),
            ]),
          )),
          SliverToBoxAdapter(child: Padding(padding: const EdgeInsets.all(16), child: Row(children: [
            FilledButton.icon(onPressed: list.isEmpty ? null : () => state.play(list, 0), icon: const Icon(Icons.play_arrow), label: const Text('Play')),
            const SizedBox(width: 10),
            OutlinedButton(onPressed: () => state.toggleFollow(name), child: Text(state.follows.contains(name) ? 'Following' : 'Follow')),
            const Spacer(),
            IconButton(onPressed: list.isEmpty ? null : () => state.startRadio(list.first), icon: const Icon(Icons.radio)),
          ]))),
          SliverToBoxAdapter(child: Padding(padding: const EdgeInsets.fromLTRB(16, 0, 16, 8), child: Text('${list.length} songs', style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w800)))),
          SliverList.builder(itemCount: list.length, itemBuilder: (_, i) => TrackTile(list, i)),
          const SliverToBoxAdapter(child: SizedBox(height: 120)),
        ]);
      },
    ));
  }
}
Widget _phArtist() => Container(color: Colors.deepPurple.shade900, child: const Icon(Icons.person, size: 90, color: Colors.white24));

class AlbumPage extends StatelessWidget {
  final String album, artist;
  const AlbumPage(this.album, this.artist, {super.key});
  @override Widget build(BuildContext context) {
    final state = context.watch<AppState>();
    return Scaffold(appBar: AppBar(title: Text(album)), body: FutureBuilder<List<Track>>(
      future: searchAll('$artist $album', state.country),
      builder: (context, snap) {
        final list = (snap.data ?? []).where((t) => t.album.toLowerCase() == album.toLowerCase() || t.artist.toLowerCase() == artist.toLowerCase()).toList();
        return ListView(children: [
          Padding(padding: const EdgeInsets.all(16), child: FilledButton.icon(onPressed: list.isEmpty ? null : () => state.play(list, 0), icon: const Icon(Icons.play_arrow), label: const Text('Play album'))),
          for (var i = 0; i < list.length; i++) TrackTile(list, i),
        ]);
      },
    ));
  }
}

class PodcastPage extends StatelessWidget {
  final PodcastEpisode feed;
  const PodcastPage(this.feed, {super.key});
  @override Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text(feed.title)),
    body: FutureBuilder<List<PodcastEpisode>>(
      future: podcastEpisodes(feed.id.replaceFirst('feed_', '')),
      builder: (context, snap) {
        final list = snap.data ?? [];
        return ListView(children: [
          Padding(padding: const EdgeInsets.all(16), child: Text(feed.description, maxLines: 8, overflow: TextOverflow.ellipsis)),
          for (final episode in list) ListTile(leading: art(episode.image, 54), title: Text(episode.title), subtitle: Text(episode.show), onTap: () => _playPodcast(context, episode)),
        ]);
      },
    ),
  );
}
Future<void> _playPodcast(BuildContext context, PodcastEpisode episode) async {
  await context.read<AppState>().play([Track('pod${episode.id}', 'Podcast', episode.title, episode.show, episode.image, episode.audio, '', false, durationMs: episode.durationMs)], 0);
}

class CountryPage extends StatefulWidget { const CountryPage({super.key}); @override State<CountryPage> createState() => _CountryPageState(); }
class _CountryPageState extends State<CountryPage> {
  late final future = fetchCountries(); String query = '';
  @override Widget build(BuildContext context) => Scaffold(appBar: AppBar(title: const Text('Country charts')), body: FutureBuilder<List<Country>>(
    future: future,
    builder: (context, snap) {
      if (snap.hasError) return const Center(child: Text('Could not load countries'));
      if (!snap.hasData) return const Center(child: CircularProgressIndicator());
      final list = snap.data!.where((e) => e.name.toLowerCase().contains(query.toLowerCase())).toList();
      return Column(children: [
        Padding(padding: const EdgeInsets.all(12), child: TextField(onChanged: (v) => setState(() => query = v), decoration: const InputDecoration(prefixIcon: Icon(Icons.search), hintText: 'Search country'))),
        Expanded(child: ListView(children: [for (final country in list) ListTile(leading: Text(country.flag, style: const TextStyle(fontSize: 24)), title: Text(country.name), trailing: context.read<AppState>().country == country.code ? const Icon(Icons.check) : null, onTap: () { context.read<AppState>().setCountry(country); Navigator.pop(context); })])),
      ]);
    },
  ));
}

class PlayerPage extends StatelessWidget {
  const PlayerPage({super.key});

  @override
  Widget build(BuildContext context) {
    final state=context.watch<AppState>();
    final track=state.current;

    if(track==null){
      return const Scaffold(
        body:Center(
          child:Text('Nothing playing'),
        ),
      );
    }

    final liked=state.likes.containsKey(track.id);
    final downloaded=state.isDownloaded(track);
    final downloading=state.progress.containsKey(track.id);

    return Scaffold(
      appBar:AppBar(
        title:const Text('Now Playing'),
        centerTitle:true,
        actions:[
          IconButton(
            tooltip:'Queue',
            onPressed:()=>queueSheet(context),
            icon:const Icon(
              Icons.queue_music_outlined,
            ),
          ),
        ],
      ),
      body:SafeArea(
        child:LayoutBuilder(
          builder:(context,constraints){
            final imageSize=
                constraints.maxWidth.clamp(220.0,380.0);

            return SingleChildScrollView(
              padding:const EdgeInsets.fromLTRB(
                22,
                10,
                22,
                28,
              ),
              child:Column(
                children:[
                  Hero(
                    tag:'now-art',
                    child:art(
                      track.image,
                      imageSize,
                      r:24,
                    ),
                  ),

                  const SizedBox(height:24),

                  Text(
                    track.title,
                    textAlign:TextAlign.center,
                    maxLines:2,
                    overflow:TextOverflow.ellipsis,
                    style:const TextStyle(
                      fontSize:25,
                      fontWeight:FontWeight.w800,
                    ),
                  ),

                  const SizedBox(height:6),

                  Text(
                    track.artist,
                    textAlign:TextAlign.center,
                    maxLines:1,
                    overflow:TextOverflow.ellipsis,
                    style:const TextStyle(
                      color:Colors.white60,
                      fontSize:16,
                    ),
                  ),

                  if(track.preview)
                    Padding(
                      padding:const EdgeInsets.only(top:10),
                      child:Chip(
                        avatar:const Icon(
                          Icons.timer_outlined,
                          size:16,
                        ),
                        label:const Text(
                          'Preview',
                        ),
                      ),
                    ),

                  if(downloaded)
                    const Padding(
                      padding:EdgeInsets.only(top:8),
                      child:Chip(
                        avatar:Icon(
                          Icons.download_done_outlined,
                          size:16,
                        ),
                        label:Text(
                          'Downloaded',
                        ),
                      ),
                    ),

                  const SizedBox(height:18),

                  StreamBuilder<Duration>(
                    stream:state.player.positionStream,
                    initialData:state.player.position,
                    builder:(context,snapshot){
                      final position=snapshot.data??Duration.zero;
                      final duration=
                          state.player.duration??Duration.zero;

                      final durationMs=
                          duration.inMilliseconds;

                      final positionMs=
                          position.inMilliseconds
                              .clamp(
                                0,
                                durationMs>0
                                    ?durationMs
                                    :1,
                              );

                      return Column(
                        children:[
                          Slider(
                            value:durationMs<=0
                                ?0
                                :positionMs.toDouble(),
                            max:durationMs<=0
                                ?1
                                :durationMs.toDouble(),
                            onChanged:durationMs<=0
                                ?null
                                :(value){
                                    state.player.seek(
                                      Duration(
                                        milliseconds:
                                            value.round(),
                                      ),
                                    );
                                  },
                          ),
                          Row(
                            mainAxisAlignment:
                                MainAxisAlignment.spaceBetween,
                            children:[
                              Text(_fmt(position)),
                              Text(_fmt(duration)),
                            ],
                          ),
                        ],
                      );
                    },
                  ),

                  const SizedBox(height:8),

                  StreamBuilder<bool>(
                    stream:state.player.playingStream,
                    initialData:state.player.playing,
                    builder:(context,snapshot){
                      final playing=snapshot.data==true;

                      return Row(
                        mainAxisAlignment:
                            MainAxisAlignment.center,
                        children:[
                          IconButton(
                            tooltip:'Previous',
                            onPressed:state.prev,
                            icon:const Icon(
                              Icons.skip_previous_rounded,
                            ),
                            iconSize:40,
                          ),
                          const SizedBox(width:10),
                          IconButton.filled(
                            tooltip:playing
                                ?'Pause'
                                :'Play',
                            onPressed:playing
                                ?state.player.pause
                                :state.player.play,
                            icon:Icon(
                              playing
                                  ?Icons.pause_rounded
                                  :Icons.play_arrow_rounded,
                            ),
                            iconSize:42,
                          ),
                          const SizedBox(width:10),
                          IconButton(
                            tooltip:'Next',
                            onPressed:state.next,
                            icon:const Icon(
                              Icons.skip_next_rounded,
                            ),
                            iconSize:40,
                          ),
                        ],
                      );
                    },
                  ),

                  const SizedBox(height:14),

                  Row(
                    mainAxisAlignment:
                        MainAxisAlignment.spaceEvenly,
                    children:[
                      IconButton(
                        tooltip:'Shuffle',
                        onPressed:state.toggleShuffle,
                        icon:Icon(
                          Icons.shuffle_rounded,
                          color:state.shuffle
                              ?accent
                              :null,
                        ),
                      ),

                      IconButton(
                        tooltip:liked
                            ?'Remove from liked'
                            :'Like',
                        onPressed:()=>state.toggleLike(track),
                        icon:Icon(
                          liked
                              ?Icons.favorite
                              :Icons.favorite_border,
                          color:liked
                              ?accent
                              :null,
                        ),
                      ),

                      if(state.canDownload(track))
                        IconButton(
                          tooltip:downloaded
                              ?'Remove download'
                              :'Download',
                          onPressed:downloading
                              ?null
                              :downloaded
                                  ?()=>state.removeDownload(track)
                                  :()=>state.download(track),
                          icon:Icon(
                            downloaded
                                ?Icons.download_done_outlined
                                :Icons.download_outlined,
                          ),
                        ),

                      IconButton(
                        tooltip:'Lyrics',
                        onPressed:()=>lyricsSheet(
                          context,
                          track,
                        ),
                        icon:const Icon(
                          Icons.lyrics_outlined,
                        ),
                      ),

                      IconButton(
                        tooltip:'Sleep timer',
                        onPressed:()=>sleepSheet(context),
                        icon:const Icon(
                          Icons.bedtime_outlined,
                        ),
                      ),

                      PopupMenuButton<double>(
                        tooltip:'Playback speed',
                        initialValue:state.speed,
                        onSelected:state.setSpeed,
                        itemBuilder:(_)=><double>[
                          0.75,
                          1.0,
                          1.25,
                          1.5,
                          2.0,
                        ].map(
                          (value)=>PopupMenuItem<double>(
                            value:value,
                            child:Text('${value}x'),
                          ),
                        ).toList(),
                        child:const Icon(
                          Icons.speed_outlined,
                        ),
                      ),

                      IconButton(
                        tooltip:'Repeat',
                        onPressed:state.cycleRepeat,
                        icon:Icon(
                          state.repeat==2
                              ?Icons.repeat_one_rounded
                              :Icons.repeat_rounded,
                          color:state.repeat>0
                              ?accent
                              :null,
                        ),
                      ),
                    ],
                  ),

                  const SizedBox(height:10),

                  OutlinedButton.icon(
                    onPressed:()=>queueSheet(context),
                    icon:const Icon(
                      Icons.queue_music_outlined,
                    ),
                    label:Text(
                      'Queue · ${state.queue.length}',
                    ),
                  ),
                ],
              ),
            );
          },
        ),
      ),
    );
  }
}

String _fmt(Duration d) => '${d.inMinutes.remainder(60).toString().padLeft(2, '0')}:${d.inSeconds.remainder(60).toString().padLeft(2, '0')}';
class MiniPlayer extends StatelessWidget {
  const MiniPlayer({super.key});

  @override
  Widget build(BuildContext context) {
    final state=context.watch<AppState>();
    final track=state.current;

    if(track==null){
      return const SizedBox.shrink();
    }

    return Material(
      color:Theme.of(context)
          .colorScheme
          .surfaceContainerHigh,
      elevation:4,
      child:Column(
        mainAxisSize:MainAxisSize.min,
        children:[
          StreamBuilder<Duration>(
            stream:state.player.positionStream,
            initialData:state.player.position,
            builder:(context,snapshot){
              final position=snapshot.data??Duration.zero;
              final duration=
                  state.player.duration??Duration.zero;

              final total=duration.inMilliseconds;
              final value=total<=0
                  ?0.0
                  :(position.inMilliseconds
                          .clamp(0,total))
                      /total;

              return LinearProgressIndicator(
                value:total<=0?null:value,
                minHeight:2,
              );
            },
          ),

          ListTile(
            contentPadding:
                const EdgeInsets.symmetric(
              horizontal:12,
            ),
            leading:Hero(
              tag:'now-art',
              child:art(track.image,48,r:8),
            ),
            title:Text(
              track.title,
              maxLines:1,
              overflow:TextOverflow.ellipsis,
            ),
            subtitle:Text(
              '${track.artist}'
              '${track.preview?' · Preview':''}',
              maxLines:1,
              overflow:TextOverflow.ellipsis,
            ),
            onTap:()=>openPlayer(context),
            trailing:Row(
              mainAxisSize:MainAxisSize.min,
              children:[
                IconButton(
                  tooltip:'Queue',
                  onPressed:()=>queueSheet(context),
                  icon:const Icon(
                    Icons.queue_music_outlined,
                  ),
                ),
                StreamBuilder<bool>(
                  stream:state.player.playingStream,
                  initialData:state.player.playing,
                  builder:(context,snapshot){
                    final playing=snapshot.data==true;

                    return IconButton.filledTonal(
                      tooltip:playing
                          ?'Pause'
                          :'Play',
                      onPressed:playing
                          ?state.player.pause
                          :state.player.play,
                      icon:Icon(
                        playing
                            ?Icons.pause_rounded
                            :Icons.play_arrow_rounded,
                      ),
                    );
                  },
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class SettingsPage extends StatelessWidget {
  const SettingsPage({super.key});
  @override Widget build(BuildContext context) {
    final state = context.watch<AppState>();
    return Scaffold(appBar: AppBar(title: const Text('Settings')), body: ListView(children: [
      const ListTile(title: Text('Personalization', style: TextStyle(fontWeight: FontWeight.w800))),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 16), child: Wrap(spacing: 8, children: [for (final genre in ['Hip-Hop','Pop','R&B','Afrobeats','Electronic','Rock','Jazz','Lo-fi','Classical','Country']) FilterChip(label: Text(genre), selected: state.genres.contains(genre), onSelected: (_) => state.toggleGenre(genre))])), 
      const Divider(),
      SwitchListTile(title: const Text('Smart downloads'), subtitle: const Text('Automatically save permitted tracks from your likes and recent listening.'), value: state.smartDownloads, onChanged: state.setSmartDownloads),
      ListTile(title: const Text('Library sorting'), subtitle: Text(state.sortMode), trailing: DropdownButton<String>(value: state.sortMode, items: const [DropdownMenuItem(value: 'recent', child: Text('Recently played')), DropdownMenuItem(value: 'saved', child: Text('Recently saved')), DropdownMenuItem(value: 'alpha', child: Text('A–Z'))], onChanged: (v) { if (v != null) state.setSort(v); })),
      const Divider(),
      ListTile(leading: const Icon(Icons.cloud_outlined), title: Text(cloudOn ? (state.user?.email ?? 'Sign in / create account') : 'Cloud sync not configured'), onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const AccountPage()))),
      ListTile(leading: const Icon(Icons.notifications_none), title: const Text('Notifications'), onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const NotificationsPage()))),
      ListTile(leading: const Icon(Icons.upload_file), title: const Text('Creator Studio'), subtitle: const Text('Publish your own audio'), onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const CreatorStudioPage()))),
      ListTile(leading: const Icon(Icons.public), title: Text('Chart country: ${state.countryLabel}'), onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const CountryPage()))),
      const ListTile(leading: Icon(Icons.info_outline), title: Text('Aetherwave 2.0'), subtitle: Text('Flutter music discovery, offline library and multi-source playback.')),
    ]));
  }
}

class AccountPage extends StatefulWidget { const AccountPage({super.key}); @override State<AccountPage> createState() => _AccountPageState(); }
class _AccountPageState extends State<AccountPage> {
  final email = TextEditingController(), password = TextEditingController(); String? error; bool busy = false;
  Future<void> auth(bool signUp) async { setState(() => busy = true); final r = await context.read<AppState>().auth(email.text.trim(), password.text, signUp); if (mounted) setState(() { error = r; busy = false; }); }
  @override Widget build(BuildContext context) {
    final state = context.watch<AppState>();
    return Scaffold(appBar: AppBar(title: const Text('Account')), body: Padding(padding: const EdgeInsets.all(24), child: !cloudOn
      ? const Text('Guest mode. Add Supabase build secrets to enable accounts, social features, creator uploads and cloud sync.')
      : state.user != null
        ? Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text('Signed in as ${state.user!.email}', style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700)), const SizedBox(height: 10), const Text('Likes, playlists, follows and preferences can sync across devices.'), const SizedBox(height: 24), FilledButton(onPressed: state.signOut, child: const Text('Sign out'))])
        : Column(children: [const Text('Sign in to sync your Aetherwave library.'), const SizedBox(height: 16), TextField(controller: email, decoration: const InputDecoration(labelText: 'Email')), TextField(controller: password, obscureText: true, decoration: const InputDecoration(labelText: 'Password')), if (error != null) Padding(padding: const EdgeInsets.only(top: 10), child: Text(error!, style: const TextStyle(color: Colors.redAccent))), const SizedBox(height: 18), Row(children: [Expanded(child: FilledButton(onPressed: busy ? null : () => auth(false), child: const Text('Sign in'))), const SizedBox(width: 10), Expanded(child: OutlinedButton(onPressed: busy ? null : () => auth(true), child: const Text('Create account')))])]),
    ));
  }
}
