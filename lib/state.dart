import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:just_audio/just_audio.dart';
import 'package:just_audio_background/just_audio_background.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:file_picker/file_picker.dart';
import 'package:supabase_flutter/supabase_flutter.dart' show User, AuthException;
import 'api.dart';
import 'cloud.dart';

class AppState extends ChangeNotifier {
  final player = AudioPlayer();
  late SharedPreferences p;
  List<Track> queue=[], recent=[], local=[];
  Set<String> follows={};
  int index=0, repeat=0;
  bool shuffle=false;
  double speed=1;
  Map<String,Track> likes={}, downloads={};
  Map<String,String> paths={};
  Map<String,double> progress={};
  Map<String,List<Track>> playlists={};
  String country='US', countryLabel='';
  ThemeMode theme=ThemeMode.dark;
  List<String> searches=[];
  Set<String> genres={};
  bool smartDownloads=false;
  String sortMode='recent';
  Timer? _sleep; DateTime? sleepAt;
  Track? get current=>queue.isEmpty?null:queue[index];
  List<Track> _rl(String k)=>[for(final e in jsonDecode(p.getString(k)??'[]')) Track.fromJson(e)];
  String _js(Iterable<Track> l)=>jsonEncode([for(final t in l)t.toJson()]);

  Future<void> load() async {
    p=await SharedPreferences.getInstance();
    likes={for(final t in _rl('likes'))t.id:t}; downloads={for(final t in _rl('downloads'))t.id:t}; recent=_rl('recent'); local=_rl('local');
    paths=Map<String,String>.from(jsonDecode(p.getString('paths')??'{}'));
    final pl=jsonDecode(p.getString('playlists')??'{}') as Map; playlists={for(final e in pl.entries)e.key:[for(final j in e.value)Track.fromJson(j)]};
    country=p.getString('country')??(PlatformDispatcher.instance.locale.countryCode??'US'); countryLabel=p.getString('countryLabel')??country;
    final ti=p.getInt('theme')??2; theme=ThemeMode.values[ti.clamp(0,2).toInt()]; speed=p.getDouble('speed')??1;
    searches=p.getStringList('searches')??[]; follows=(p.getStringList('follows')??[]).toSet(); genres=(p.getStringList('genres')??[]).toSet();
    smartDownloads=p.getBool('smartDownloads')??false; sortMode=p.getString('sortMode')??'recent';
    if(user!=null) pull();
    player.playerStateStream.listen((st){if(st.processingState==ProcessingState.completed){if(repeat==2){player.seek(Duration.zero);player.play();}else{next(auto:true);}}});
  }
  void _save({bool sync=true}){p.setStringList('follows',follows.toList());p.setStringList('genres',genres.toList());p.setString('local',_js(local));p.setString('likes',_js(likes.values));p.setString('downloads',_js(downloads.values));p.setString('recent',_js(recent));p.setString('paths',jsonEncode(paths));p.setString('playlists',jsonEncode({for(final e in playlists.entries)e.key:[for(final t in e.value)t.toJson()]}));notifyListeners();if(sync)push();}
  AudioSource _src(Track t){
    final tag=MediaItem(
      id:t.id,
      title:t.title,
      artist:t.artist,
      album:t.album.isEmpty?'Aetherwave':t.album,
      artUri:Uri.tryParse(t.image),
    );

    if(t.src=='Device'){
      return AudioSource.file(t.url,tag:tag);
    }

    final localPath=paths[t.id];

    if(localPath!=null&&File(localPath).existsSync()){
      return AudioSource.file(localPath,tag:tag);
    }

    final playbackUrl=t.url.trim().isNotEmpty
        ? t.url.trim()
        : t.dl.trim();

    if(playbackUrl.isEmpty){
      throw StateError('No playable audio URL');
    }

    return AudioSource.uri(
      Uri.parse(playbackUrl),
      tag:tag,
    );
  }
  Track _resolvePlayableTrack(List<Track> list,int requestedIndex){
    if(list.isEmpty){
      throw StateError('Cannot resolve an empty track list');
    }

    final safeIndex=requestedIndex.clamp(0,list.length-1);
    final requested=list[safeIndex];

    if(!requested.preview&&requested.url.trim().isNotEmpty){
      return requested;
    }

    final title=requested.title.trim().toLowerCase();
    final artist=requested.artist.trim().toLowerCase();

    for(final candidate in list){
      if(candidate.id==requested.id)continue;
      if(candidate.preview)continue;

      final playable=candidate.url.trim().isNotEmpty ||
          candidate.dl.trim().isNotEmpty;

      if(!playable)continue;

      final sameTitle=
          candidate.title.trim().toLowerCase()==title;

      final sameArtist=
          artist.isEmpty ||
          candidate.artist.trim().toLowerCase()==artist;

      if(sameTitle&&sameArtist){
        return candidate;
      }
    }

    return requested;
  }

  Future<void> play(List<Track> list,int i)async{
    if(list.isEmpty)return;

    queue=List.of(list);

    final requestedIndex=i.clamp(0,queue.length-1);
    final selected=_resolvePlayableTrack(queue,requestedIndex);

    final resolvedIndex=queue.indexWhere(
      (track)=>track.id==selected.id,
    );

    index=resolvedIndex<0?requestedIndex:resolvedIndex;

    recent=[
      queue[index],
      ...recent.where((track)=>track.id!=queue[index].id),
    ].take(50).toList();

    _save();

    try{
      await player.setAudioSource(_src(queue[index]));
      await player.setSpeed(speed);
      await player.play();
    }catch(_){
      notifyListeners();
    }
  }
  Future<void> next({bool auto=false})async{if(queue.isEmpty)return;var n=shuffle?Random().nextInt(queue.length):index+1;if(n>=queue.length){if(repeat==1||!auto)n=0;else return;}await play(queue,n);}
  Future<void> prev()async{if(player.position.inSeconds>3||index==0)return player.seek(Duration.zero);await play(queue,index-1);}
  void playNext(Track t){
    if(queue.isEmpty){
      queue.add(t);
    }else{
      queue.insert(index+1,t);
    }
    notifyListeners();
  }

  void addToQueue(Track t){
    queue.add(t);
    notifyListeners();
  }

  void moveQueueNext(int queueIndex){
    if(queueIndex<0||
        queueIndex>=queue.length||
        queueIndex==index||
        queue.length<2){
      return;
    }

    final track=queue.removeAt(queueIndex);

    if(queueIndex<index){
      index--;
    }

    final target=(index+1).clamp(0,queue.length);
    queue.insert(target,track);
    notifyListeners();
  }

  void removeQueueAt(int queueIndex){
    if(queue.length<=1||
        queueIndex<0||
        queueIndex>=queue.length||
        queueIndex==index){
      return;
    }

    queue.removeAt(queueIndex);

    if(queueIndex<index){
      index--;
    }

    if(index>=queue.length){
      index=queue.length-1;
    }

    notifyListeners();
  }
  void addSearch(String q){searches=[q,...searches.where((x)=>x!=q)].take(20).toList();p.setStringList('searches',searches);notifyListeners();}
  void clearSearches(){searches=[];p.remove('searches');notifyListeners();}
  void toggleShuffle(){shuffle=!shuffle;notifyListeners();}
  void cycleRepeat(){repeat=(repeat+1)%3;notifyListeners();}
  void setSpeed(double v){speed=v;player.setSpeed(v);p.setDouble('speed',v);notifyListeners();}
  void setSleep(int? min){_sleep?.cancel();sleepAt=min==null?null:DateTime.now().add(Duration(minutes:min));if(min!=null)_sleep=Timer(Duration(minutes:min),(){player.pause();sleepAt=null;notifyListeners();});notifyListeners();}
  void setCountry(Country c){country=c.code;countryLabel='${c.flag} ${c.name}';p.setString('country',country);p.setString('countryLabel',countryLabel);notifyListeners();}
  void setTheme(ThemeMode m){theme=m;p.setInt('theme',m.index);notifyListeners();}
  void toggleLike(Track t){likes.containsKey(t.id)?likes.remove(t.id):likes[t.id]=t;_save();}
  void createPlaylist(String n){if(n.trim().isNotEmpty)playlists.putIfAbsent(n.trim(),()=>[]);_save();}
  void deletePlaylist(String n){playlists.remove(n);_save();}
  void addToPlaylist(String n,Track t){final l=playlists[n]!;if(!l.any((x)=>x.id==t.id))l.add(t);_save();}
  void removeFromPlaylist(String n,Track t){playlists[n]?.removeWhere((x)=>x.id==t.id);_save();}
  void reorder(String n,int a,int b){final l=playlists[n]!;if(b>a)b--;l.insert(b,l.removeAt(a));_save();}
  bool canDownload(Track t)=>t.dl.isNotEmpty; bool isDownloaded(Track t)=>paths[t.id]!=null&&File(paths[t.id]!).existsSync();
  Future<void> download(Track t)async{
    final url=t.dl.trim();

    if(url.isEmpty||
        isDownloaded(t)||
        progress.containsKey(t.id)){
      return;
    }

    progress[t.id]=0;
    notifyListeners();

    final client=http.Client();
    File? target;

    try{
      final dir=await getApplicationDocumentsDirectory();

      final lower=url.toLowerCase();

      final extension=lower.contains('.flac')
          ?'.flac'
          :lower.contains('.wav')
              ?'.wav'
              :lower.contains('.ogg')
                  ?'.ogg'
                  :'.mp3';

      target=File(
        '${dir.path}/${t.id}$extension',
      );

      final request=http.Request(
        'GET',
        Uri.parse(url),
      );

      request.headers['Accept']='audio/*';

      final response=await client.send(request);

      if(response.statusCode<200||
          response.statusCode>=300){
        throw HttpException(
          'Download failed: HTTP ${response.statusCode}',
          uri:Uri.parse(url),
        );
      }

      final sink=target.openWrite();

      var received=0;
      final total=response.contentLength??0;

      await for(final chunk in response.stream){
        sink.add(chunk);
        received+=chunk.length;

        if(total>0){
          progress[t.id]=received/total;
          notifyListeners();
        }
      }

      await sink.flush();
      await sink.close();

      if(!await target.exists()||
          await target.length()==0){
        throw StateError('Downloaded file is empty');
      }

      paths[t.id]=target.path;
      downloads[t.id]=t;
    }catch(_){
      if(target!=null&&await target.exists()){
        try{
          await target.delete();
        }catch(_){}
      }
    }finally{
      client.close();
      progress.remove(t.id);
      _save();
    }
  }
  Future<void> removeDownload(Track t)async{final f=paths.remove(t.id);downloads.remove(t.id);if(f!=null&&File(f).existsSync())await File(f).delete();_save();}
  Future<void> smartDownload()async{if(!smartDownloads)return;final candidates=[...likes.values,...recent];final seen=<String>{};for(final t in candidates){if(seen.add(t.id)&&canDownload(t)&&!isDownloaded(t))await download(t);}}
  void setSmartDownloads(bool v){smartDownloads=v;p.setBool('smartDownloads',v);notifyListeners();if(v)smartDownload();}
  List<Track> sortedLibrary(Iterable<Track> source){
    final result = List<Track>.of(source);
    switch(sortMode){
      case 'alpha':
        result.sort((a,b){
          final aa='${a.artist} ${a.title}'.toLowerCase();
          final bb='${b.artist} ${b.title}'.toLowerCase();
          return aa.compareTo(bb);
        });
        break;
      case 'saved':
        result.sort((a,b){
          final ai=downloads.containsKey(a.id) ? 0 : 1;
          final bi=downloads.containsKey(b.id) ? 0 : 1;
          return ai.compareTo(bi);
        });
        break;
      case 'recent':
      default:
        final order=<String,int>{};
        for(var i=0;i<recent.length;i++){
          order[recent[i].id]=i;
        }
        result.sort((a,b){
          final ai=order[a.id] ?? 999999;
          final bi=order[b.id] ?? 999999;
          return ai.compareTo(bi);
        });
    }
    return result;
  }

  void toggleFollow(String artist){
    final name=artist.trim();
    if(name.isEmpty)return;
    follows.contains(name) ? follows.remove(name) : follows.add(name);
    p.setStringList('follows',follows.toList());
    _save();
  }

  void setSort(String v){sortMode=v;p.setString('sortMode',v);notifyListeners();}
  void toggleGenre(String g){genres.contains(g)?genres.remove(g):genres.add(g);p.setStringList('genres',genres.toList());notifyListeners();}
  List<Track> recommendations(){final seed=[...likes.values,...recent];final artists=seed.map((t)=>t.artist.toLowerCase()).toSet();final gs=seed.map((t)=>t.genre.toLowerCase()).where((x)=>x.isNotEmpty).toSet();final pool=[...likes.values,...recent,...downloads.values];pool.sort((a,b){int score(Track t){var n=0;if(artists.contains(t.artist.toLowerCase()))n+=5;if(gs.contains(t.genre.toLowerCase()))n+=3;if(genres.contains(t.genre))n+=6;if(t.preview)n--;if(isDownloaded(t))n++;return n;}return score(b).compareTo(score(a));});final seen=<String>{};return [for(final t in pool)if(seen.add(t.id))t];}
  Future<void> startRadio(Track t)async{final l=await searchAll(t.artist,country);final mix=[t,...l.where((x)=>x.id!=t.id)]..shuffle();await play(mix,0);}
  Future<void> addLocal()async{final r=await FilePicker.platform.pickFiles(type:FileType.audio,allowMultiple:true);if(r==null)return;final dir=await getApplicationDocumentsDirectory();for(final f in r.files){if(f.path==null)continue;final dest='${dir.path}/lc_${DateTime.now().microsecondsSinceEpoch}_${f.name}';await File(f.path!).copy(dest);local.add(Track('lc${dest.hashCode}','Device',f.name.replaceAll(RegExp(r'\.\w+$'),''),'On this device','',dest,'',false));} _save();}
  User? get user=>sb?.auth.currentUser;
  Map _snap()=>{'likes':[for(final t in likes.values)t.toJson()],'playlists':{for(final e in playlists.entries)e.key:[for(final t in e.value)t.toJson()]},'follows':follows.toList(),'searches':searches,'genres':genres.toList()};
  Future<void>push()async{final u=user;if(u==null)return;try{await sb!.from('user_data').upsert({'user_id':u.id,'data':_snap(),'updated_at':DateTime.now().toIso8601String()});}catch(_){} }
  Future<void>pull()async{final u=user;if(u==null)return;try{final r=await sb!.from('user_data').select('data').eq('user_id',u.id).maybeSingle();if(r==null){await push();return;}final d=r['data'] as Map;likes={for(final j in d['likes']??[])'${j['id']}':Track.fromJson(j)};playlists={for(final e in (d['playlists']??{}).entries)e.key:[for(final j in e.value)Track.fromJson(j)]};follows={...?(d['follows']as List?)?.cast<String>()};genres={...?(d['genres']as List?)?.cast<String>()};_save(sync:false);}catch(_){} }
  Future<String?>auth(String email,String pw,bool signUp)async{try{signUp?await sb!.auth.signUp(email:email,password:pw):await sb!.auth.signInWithPassword(email:email,password:pw);await pull();notifyListeners();return null;}on AuthException catch(e){return e.message;}catch(_){return 'Something went wrong. Check your connection.';}}
  Future<void>signOut()async{await sb?.auth.signOut();notifyListeners();}
  Future<String?>sharePlaylist(String n)async{try{final r=await sb!.from('shared_playlists').insert({'name':n,'tracks':[for(final t in playlists[n]!)t.toJson()]}).select('id').single();return '${r['id']}';}catch(_){return null;}}
  Future<bool>importPlaylist(String id)async{try{final r=await sb!.from('shared_playlists').select().eq('id',id).single();playlists[r['name']]=[for(final j in r['tracks'])Track.fromJson(j)];_save();return true;}catch(_){return false;}}
}
