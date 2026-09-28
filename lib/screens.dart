import 'dart:async';
import 'dart:ui';
import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'api.dart';
import 'common.dart';
import 'more.dart';
import 'state.dart';

void openPlayer(BuildContext c) => Navigator.push(
    c,
    PageRouteBuilder(
      opaque: false,
      transitionDuration: const Duration(milliseconds: 320),
      pageBuilder: (_, __, ___) => const PlayerPage(),
      transitionsBuilder: (_, a, __, child) => SlideTransition(position: Tween(begin: const Offset(0, 1), end: Offset.zero).animate(CurvedAnimation(parent: a, curve: Curves.easeOutCubic)), child: child),
    ));

class Shell extends StatefulWidget {
  const Shell({super.key});
  @override
  State<Shell> createState() => _ShellState();
}

class _ShellState extends State<Shell> {
  int tab = 0;
  @override
  Widget build(BuildContext context) => Scaffold(
        extendBody: true,
        body: SafeArea(bottom: false, child: IndexedStack(index: tab, children: [HomeTab(onSearch: () => setState(() => tab = 1)), const SearchTab(), const LibraryTab()])),
        bottomNavigationBar: Column(mainAxisSize: MainAxisSize.min, children: [
          const MiniPlayer(),
          NavigationBar(
            backgroundColor: Theme.of(context).scaffoldBackgroundColor.withValues(alpha: 0.96),
            indicatorColor: accent.withValues(alpha: 0.25),
            selectedIndex: tab,
            onDestinationSelected: (i) => setState(() => tab = i),
            destinations: const [
              NavigationDestination(icon: Icon(Icons.home_outlined), selectedIcon: Icon(Icons.home_rounded), label: 'Home'),
              NavigationDestination(icon: Icon(Icons.search), selectedIcon: Icon(Icons.search_rounded), label: 'Search'),
              NavigationDestination(icon: Icon(Icons.library_music_outlined), selectedIcon: Icon(Icons.library_music), label: 'Library'),
            ],
          ),
        ]),
      );
}

class HomeTab extends StatelessWidget {
  final VoidCallback onSearch;
  const HomeTab({super.key, required this.onSearch});
  String greet() {
    final h = DateTime.now().hour;
    return h < 12 ? 'Good morning' : h < 18 ? 'Good afternoon' : 'Good evening';
  }

  @override
  Widget build(BuildContext context) {
    final s = context.watch<AppState>();
    final quick = [...s.recent, ...s.likes.values].fold<Map<String, Track>>({}, (m, t) => m..putIfAbsent(t.id, () => t)).values.take(6).toList();
    return Container(
      decoration: BoxDecoration(gradient: LinearGradient(begin: Alignment.topCenter, end: Alignment.center, colors: [accent.withValues(alpha: 0.28), Colors.transparent])),
      child: ListView(padding: const EdgeInsets.only(bottom: 120), children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 16, 8, 8),
          child: Row(children: [
            Expanded(child: Text(greet(), style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w800, letterSpacing: -0.5))),
            IconButton(icon: const Icon(Icons.public), tooltip: s.countryLabel, onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const CountryPage()))),
            IconButton(icon: const Icon(Icons.settings_outlined), onPressed: () => settingsSheet(context)),
          ]),
        ),
        GestureDetector(
          onTap: onSearch,
          child: Container(
            margin: const EdgeInsets.fromLTRB(16, 4, 16, 12),
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
            decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.1), borderRadius: BorderRadius.circular(28)),
            child: const Row(children: [Icon(Icons.search, size: 20), SizedBox(width: 10), Text('Search songs, artists, anything')]),
          ),
        ),
        if (quick.isNotEmpty)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: GridView.count(
              shrinkWrap: true, physics: const NeverScrollableScrollPhysics(), crossAxisCount: 2, childAspectRatio: 3.4, mainAxisSpacing: 8, crossAxisSpacing: 8,
              children: [
                for (var i = 0; i < quick.length; i++)
                  InkWell(
                    borderRadius: BorderRadius.circular(10),
                    onTap: () => s.play(quick, i),
                    child: Container(
                      decoration: BoxDecoration(color: Colors.white.withValues(alpha: 0.08), borderRadius: BorderRadius.circular(10)),
                      child: Row(children: [art(quick[i].image, 56, r: 10), const SizedBox(width: 10), Expanded(child: Text(quick[i].title, maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13)))]),
                    ),
                  ),
              ],
            ),
          ),
        for (final src in registry)
          for (final sh in src.shelves(s.country, s.countryLabel.replaceFirst(RegExp(r'^\S+\s'), '')))
            ShelfView(sh, key: ValueKey('${sh.title}${s.country}')),
      ]),
    );
  }
}

class ShelfView extends StatefulWidget {
  final Shelf shelf;
  const ShelfView(this.shelf, {super.key});
  @override
  State<ShelfView> createState() => _ShelfViewState();
}

class _ShelfViewState extends State<ShelfView> {
  late Future<List<Track>> f = widget.shelf.load();
  Widget skeleton(double h) => Container(height: h, margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8), decoration: BoxDecoration(color: Colors.white10, borderRadius: BorderRadius.circular(16)));
  @override
  Widget build(BuildContext context) => FutureBuilder<List<Track>>(
        future: f,
        builder: (c, snap) {
          if (snap.connectionState != ConnectionState.done) return skeleton(140);
          final l = snap.data ?? [];
          if (l.isEmpty) return const SizedBox.shrink();
          final s = context.read<AppState>();
          final st = widget.shelf.style;
          return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Padding(padding: const EdgeInsets.fromLTRB(20, 22, 16, 10), child: Text(widget.shelf.title, style: const TextStyle(fontSize: 21, fontWeight: FontWeight.w800, letterSpacing: -0.3))),
            if (st == Style.rank)
              for (var i = 0; i < l.length && i < 10; i++)
                ListTile(
                  onTap: () => s.play(l, i),
                  onLongPress: () => trackMenu(context, l[i]),
                  leading: Row(mainAxisSize: MainAxisSize.min, children: [SizedBox(width: 30, child: Text('${i + 1}', textAlign: TextAlign.center, style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800, color: i < 3 ? accent : Colors.white54))), art(l[i].image, 52, r: 10)]),
                  title: Text(l[i].title, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w600)),
                  subtitle: Text(l[i].artist, maxLines: 1),
                  trailing: IconButton(icon: const Icon(Icons.more_horiz), onPressed: () => trackMenu(context, l[i])),
                )
            else
              SizedBox(
                height: st == Style.hero ? 250 : 200,
                child: ListView.builder(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.symmetric(horizontal: 14),
                  itemCount: l.length,
                  itemBuilder: (_, i) => GestureDetector(
                    onTap: () => s.play(l, i),
                    onLongPress: () => trackMenu(context, l[i]),
                    child: st == Style.hero
                        ? Container(
                            width: 300, margin: const EdgeInsets.symmetric(horizontal: 6),
                            child: ClipRRect(
                              borderRadius: BorderRadius.circular(20),
                              child: Stack(fit: StackFit.expand, children: [
                                CachedNetworkImage(imageUrl: l[i].image, fit: BoxFit.cover, errorWidget: (_, __, ___) => Container(color: Colors.deepPurple.shade900)),
                                const DecoratedBox(decoration: BoxDecoration(gradient: LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: [Colors.transparent, Colors.black87]))),
                                Positioned(left: 16, right: 16, bottom: 14, child: Row(children: [
                                  Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisSize: MainAxisSize.min, children: [
                                    Text(l[i].title, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
                                    Text(l[i].artist, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(color: Colors.white70)),
                                  ])),
                                  const CircleAvatar(backgroundColor: accent, foregroundColor: Colors.black, child: Icon(Icons.play_arrow_rounded)),
                                ])),
                              ]),
                            ),
                          )
                        : Container(
                            width: 140, margin: const EdgeInsets.symmetric(horizontal: 6),
                            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                              art(l[i].image, 140, r: 14),
                              const SizedBox(height: 8),
                              Text(l[i].title, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700)),
                              Text(l[i].artist, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12, color: Colors.white60)),
                            ]),
                          ),
                  ),
                ),
              ),
          ]);
        },
      );
}

class SearchTab extends StatefulWidget {
  const SearchTab({super.key});
  @override
  State<SearchTab> createState() => _SearchTabState();
}

class _SearchTabState extends State<SearchTab> {
  final c = TextEditingController();
  Future<List<Track>>? f;
  Timer? _d;
  void run(String v) {
    _d?.cancel();
    v = v.trim();
    if (v.isEmpty) return setState(() => f = null);
    _d = Timer(const Duration(milliseconds: 400), () {
      context.read<AppState>().addSearch(v);
      setState(() => f = searchAll(v, context.read<AppState>().country));
    });
  }

  @override
  Widget build(BuildContext context) {
    final s = context.watch<AppState>();
    return Column(children: [
      Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
        child: TextField(
          controller: c,
          onChanged: run,
          textInputAction: TextInputAction.search,
          decoration: InputDecoration(
            hintText: 'What do you want to hear?', prefixIcon: const Icon(Icons.search), filled: true, fillColor: Colors.white10,
            suffixIcon: c.text.isEmpty ? null : IconButton(icon: const Icon(Icons.close), onPressed: () { c.clear(); run(''); }),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(28), borderSide: BorderSide.none),
          ),
        ),
      ),
      Expanded(
        child: f == null
            ? ListView(padding: const EdgeInsets.all(16), children: [
                if (s.searches.isNotEmpty) ...[
                  Row(children: [const Expanded(child: Text('Recent searches', style: TextStyle(fontWeight: FontWeight.w800, fontSize: 18))), TextButton(onPressed: s.clearSearches, child: const Text('Clear'))]),
                  Wrap(spacing: 8, children: [for (final q in s.searches) ActionChip(avatar: const Icon(Icons.history, size: 16), label: Text(q), onPressed: () { c.text = q; run(q); })]),
                ] else
                  const Padding(padding: EdgeInsets.only(top: 80), child: Center(child: Text('Search the whole world of music'))),
              ])
            : FutureBuilder<List<Track>>(
                future: f,
                builder: (_, snap) {
                  if (snap.connectionState != ConnectionState.done) return const Center(child: CircularProgressIndicator());
                  final l = snap.data ?? [];
                  if (l.isEmpty) return const Center(child: Text('No results. Try another spelling.'));
                  return ListView.builder(padding: const EdgeInsets.only(bottom: 120), itemCount: l.length, itemBuilder: (_, i) => TrackTile(l, i));
                }),
      ),
    ]);
  }
}

class MiniPlayer extends StatelessWidget {
  const MiniPlayer({super.key});
  @override
  Widget build(BuildContext context) {
    final s = context.watch<AppState>();
    final t = s.current;
    if (t == null) return const SizedBox.shrink();
    return StreamBuilder<bool>(
      stream: s.player.playingStream,
      builder: (_, snap) {
        final playing = snap.data ?? false;
        return GestureDetector(
          onTap: () => openPlayer(context),
          child: Container(
            margin: const EdgeInsets.fromLTRB(10, 0, 10, 6),
            decoration: BoxDecoration(color: const Color(0xFF241B45), borderRadius: BorderRadius.circular(16), boxShadow: const [BoxShadow(color: Colors.black54, blurRadius: 16)]),
            clipBehavior: Clip.antiAlias,
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              Padding(
                padding: const EdgeInsets.all(8),
                child: Row(children: [
                  art(t.image, 44, r: 10),
                  const SizedBox(width: 10),
                  Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(t.title, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700)), Text(t.artist, maxLines: 1, style: const TextStyle(fontSize: 12, color: Colors.white60))])),
                  IconButton(icon: Icon(playing ? Icons.pause_rounded : Icons.play_arrow_rounded, size: 30), onPressed: () => playing ? s.player.pause() : s.player.play()),
                  IconButton(icon: const Icon(Icons.skip_next_rounded, size: 28), onPressed: () => s.next()),
                ]),
              ),
              StreamBuilder<Duration>(
                stream: s.player.positionStream,
                builder: (_, p) {
                  final d = s.player.duration?.inMilliseconds ?? 0;
                  return LinearProgressIndicator(minHeight: 2, value: d == 0 ? 0 : (p.data ?? Duration.zero).inMilliseconds / d, backgroundColor: Colors.white12);
                },
              ),
            ]),
          ),
        );
      },
    );
  }
}

class PlayerPage extends StatelessWidget {
  const PlayerPage({super.key});
  String fmt(Duration d) => '${d.inMinutes}:${(d.inSeconds % 60).toString().padLeft(2, '0')}';
  @override
  Widget build(BuildContext context) {
    final s = context.watch<AppState>();
    final t = s.current;
    if (t == null) return const Scaffold();
    final liked = s.likes.containsKey(t.id);
    return Scaffold(
      backgroundColor: const Color(0xFF0B0B12),
      body: Stack(fit: StackFit.expand, children: [
        ImageFiltered(imageFilter: ImageFilter.blur(sigmaX: 50, sigmaY: 50), child: CachedNetworkImage(imageUrl: t.image, fit: BoxFit.cover, errorWidget: (_, __, ___) => const SizedBox())),
        Container(color: Colors.black.withValues(alpha: 0.62)),
        SafeArea(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 26),
            child: Column(children: [
              Row(children: [
                IconButton(icon: const Icon(Icons.keyboard_arrow_down_rounded, size: 32), onPressed: () => Navigator.pop(context)),
                Expanded(child: Text('Playing from ${t.src}', textAlign: TextAlign.center, style: const TextStyle(fontSize: 12, letterSpacing: 0.5))),
                IconButton(icon: const Icon(Icons.more_vert), onPressed: () => trackMenu(context, t)),
              ]),
              Expanded(
                child: Center(
                  child: AnimatedSwitcher(
                    duration: const Duration(milliseconds: 300),
                    child: Container(key: ValueKey(t.id), decoration: BoxDecoration(borderRadius: BorderRadius.circular(22), boxShadow: const [BoxShadow(color: Colors.black54, blurRadius: 40, offset: Offset(0, 16))]), child: art(t.image, MediaQuery.of(context).size.width - 70, r: 22)),
                  ),
                ),
              ),
              Row(children: [
                Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(t.title, style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w800, letterSpacing: -0.4), maxLines: 1, overflow: TextOverflow.ellipsis),
                  Text(t.artist, style: const TextStyle(fontSize: 16, color: Colors.white70), maxLines: 1),
                ])),
                IconButton(iconSize: 28, icon: Icon(liked ? Icons.favorite : Icons.favorite_border, color: liked ? Colors.pinkAccent : null), onPressed: () => s.toggleLike(t)),
              ]),
              if (t.preview) const Align(alignment: Alignment.centerLeft, child: Padding(padding: EdgeInsets.only(top: 6), child: Chip(label: Text('30-second preview'), visualDensity: VisualDensity.compact))),
              StreamBuilder<Duration>(
                stream: s.player.positionStream,
                builder: (_, snap) {
                  final pos = snap.data ?? Duration.zero;
                  final dur = s.player.duration ?? Duration.zero;
                  final max = dur.inMilliseconds.toDouble().clamp(1.0, double.infinity);
                  return Column(children: [
                    SliderTheme(
                      data: SliderTheme.of(context).copyWith(trackHeight: 3, thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 6), overlayShape: SliderComponentShape.noOverlay, activeTrackColor: Colors.white, inactiveTrackColor: Colors.white24, thumbColor: Colors.white),
                      child: Slider(value: pos.inMilliseconds.toDouble().clamp(0.0, max), max: max, onChanged: (v) => s.player.seek(Duration(milliseconds: v.toInt()))),
                    ),
                    Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [Text(fmt(pos), style: const TextStyle(fontSize: 12)), Text(fmt(dur), style: const TextStyle(fontSize: 12))]),
                  ]);
                },
              ),
              StreamBuilder<bool>(
                stream: s.player.playingStream,
                builder: (_, snap) {
                  final playing = snap.data ?? false;
                  return Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                    IconButton(icon: Icon(Icons.shuffle_rounded, color: s.shuffle ? accent : null), onPressed: s.toggleShuffle),
                    IconButton(iconSize: 42, icon: const Icon(Icons.skip_previous_rounded), onPressed: s.prev),
                    Container(decoration: const BoxDecoration(color: Colors.white, shape: BoxShape.circle), child: IconButton(iconSize: 44, color: Colors.black, icon: Icon(playing ? Icons.pause_rounded : Icons.play_arrow_rounded), onPressed: () => playing ? s.player.pause() : s.player.play())),
                    IconButton(iconSize: 42, icon: const Icon(Icons.skip_next_rounded), onPressed: () => s.next()),
                    IconButton(icon: Icon(s.repeat == 2 ? Icons.repeat_one_rounded : Icons.repeat_rounded, color: s.repeat > 0 ? accent : null), onPressed: s.cycleRepeat),
                  ]);
                },
              ),
              Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                TextButton(onPressed: () => s.setSpeed(s.speed >= 2 ? 0.5 : s.speed + 0.25), child: Text('${s.speed}x')),
                IconButton(icon: Icon(Icons.bedtime_outlined, color: s.sleepAt != null ? accent : null), onPressed: () => sleepSheet(context)),
                IconButton(icon: const Icon(Icons.lyrics_outlined), onPressed: () => lyricsSheet(context, t)),
                IconButton(icon: const Icon(Icons.queue_music_rounded), onPressed: () => queueSheet(context)),
              ]),
              const SizedBox(height: 8),
            ]),
          ),
        ),
      ]),
    );
  }
}
