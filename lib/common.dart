import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'api.dart';
import 'state.dart';
import 'social.dart';
import 'screens.dart';

const accent = Color(0xFFB39DFF);

Widget art(String url, double s, {double r=10}) => ClipRRect(
  borderRadius: BorderRadius.circular(r),
  child: CachedNetworkImage(imageUrl:url,width:s,height:s,fit:BoxFit.cover,fadeInDuration:const Duration(milliseconds:180),placeholder:(_,__)=>_ph(s,r),errorWidget:(_,__,___)=>_ph(s,r)),
);
Widget _ph(double s,double r)=>Container(width:s,height:s,decoration:BoxDecoration(borderRadius:BorderRadius.circular(r),gradient:const LinearGradient(colors:[Color(0xFF3B2A78),Color(0xFF17122E)])),child:const Icon(Icons.music_note,color:Colors.white38));
void toast(BuildContext c,String m)=>ScaffoldMessenger.of(c).showSnackBar(SnackBar(content:Text(m),duration:const Duration(seconds:2)));

void trackMenu(BuildContext context,Track t){
 final s=context.read<AppState>();
 showModalBottomSheet(context:context,showDragHandle:true,builder:(_)=>SafeArea(child:Column(mainAxisSize:MainAxisSize.min,children:[
  ListTile(leading:art(t.image,48),title:Text(t.title,maxLines:1),subtitle:Text('${t.artist}${t.album.isEmpty ? '' : ' · ${t.album}'}${t.preview ? ' · Preview' : ''}')),
  ListTile(leading:const Icon(Icons.queue_play_next),title:const Text('Play next'),onTap:(){s.playNext(t);Navigator.pop(context);}),
  ListTile(leading:const Icon(Icons.queue_music),title:const Text('Add to queue'),onTap:(){s.addToQueue(t);Navigator.pop(context);}),
  ListTile(leading:const Icon(Icons.comment_outlined),title:const Text('Comments'),onTap:(){Navigator.pop(context);Navigator.push(context,MaterialPageRoute(builder:(_)=>CommentsPage(t.id,t.title)));}),
  ListTile(leading:const Icon(Icons.playlist_add),title:const Text('Add to playlist'),onTap:(){Navigator.pop(context);pickPlaylist(context,t);}),
  ListTile(leading:const Icon(Icons.radio),title:const Text('Start artist radio'),onTap:(){Navigator.pop(context);s.startRadio(t);}),
  ListTile(leading:const Icon(Icons.person_outline),title:const Text('Artist'),onTap:(){Navigator.pop(context);Navigator.push(context,MaterialPageRoute(builder:(_)=>ArtistPage(t.artist,t.image)));}),
  ListTile(leading:Icon(s.likes.containsKey(t.id)?Icons.favorite:Icons.favorite_border),title:Text(s.likes.containsKey(t.id)?'Remove from liked':'Like'),onTap:(){s.toggleLike(t);Navigator.pop(context);}),
  if(s.canDownload(t))ListTile(leading:Icon(s.isDownloaded(t)?Icons.delete_outline:Icons.download),title:Text(s.isDownloaded(t)?'Remove download':'Download'),onTap:(){s.isDownloaded(t)?s.removeDownload(t):s.download(t);Navigator.pop(context);})
  else const ListTile(leading:Icon(Icons.info_outline),title:Text('Download unavailable for this track')),
 ])));
}

void pickPlaylist(BuildContext context,Track t){final s=context.read<AppState>();showModalBottomSheet(context:context,showDragHandle:true,builder:(_)=>SafeArea(child:Column(mainAxisSize:MainAxisSize.min,children:[ListTile(leading:const Icon(Icons.add),title:const Text('New playlist'),onTap:()async{Navigator.pop(context);final n=await askName(context);if(n!=null&&n.trim().isNotEmpty){s.createPlaylist(n);s.addToPlaylist(n.trim(),t);}}),for(final n in s.playlists.keys)ListTile(leading:const Icon(Icons.queue_music),title:Text(n),onTap:(){s.addToPlaylist(n,t);Navigator.pop(context);toast(context,'Added to $n');})])));}
Future<String?> askName(
  BuildContext c, {
  String title = 'Playlist name',
}) {
  final controller = TextEditingController();

  return showDialog<String>(
    context: c,
    builder: (dialogContext) => AlertDialog(
      title: Text(title),
      content: TextField(
        controller: controller,
        autofocus: true,
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(dialogContext),
          child: const Text('Cancel'),
        ),
        FilledButton(
          onPressed: () => Navigator.pop(dialogContext, controller.text),
          child: const Text('Save'),
        ),
      ],
    ),
  );
}
class TrackTile extends StatelessWidget {
  final List<Track> list;
  final int i;

  const TrackTile(
    this.list,
    this.i, {
    super.key,
  });

  @override
  Widget build(BuildContext context) {
    final state=context.watch<AppState>();
    final track=list[i];

    final playing=state.current?.id==track.id;
    final downloaded=state.isDownloaded(track);
    final downloading=state.progress.containsKey(track.id);
    final playable=
        track.url.trim().isNotEmpty||
        track.dl.trim().isNotEmpty;

    return ListTile(
      leading: Stack(
        children:[
          art(track.image,54),
          if(playing)
            Positioned(
              right:2,
              bottom:2,
              child:Container(
                width:20,
                height:20,
                decoration:BoxDecoration(
                  color:accent,
                  borderRadius:BorderRadius.circular(6),
                ),
                child:const Icon(
                  Icons.equalizer_rounded,
                  size:14,
                  color:Colors.white,
                ),
              ),
            ),
        ],
      ),
      title: Text(
        track.title,
        maxLines:1,
        overflow:TextOverflow.ellipsis,
        style:TextStyle(
          fontWeight:FontWeight.w700,
          color:playing?accent:null,
        ),
      ),
      subtitle: Text(
        '${track.artist}'
        '${track.album.isEmpty?'':' · ${track.album}'}'
        '${track.preview?' · Preview':''}'
        '${downloaded?' · Downloaded':''}',
        maxLines:1,
        overflow:TextOverflow.ellipsis,
      ),
      onTap: playable
          ? ()=>state.play(list,i)
          : null,
      trailing: Row(
        mainAxisSize:MainAxisSize.min,
        children:[
          if(downloading)
            SizedBox(
              width:24,
              height:24,
              child:CircularProgressIndicator(
                value:state.progress[track.id],
                strokeWidth:2,
              ),
            )
          else if(downloaded)
            IconButton(
              tooltip:'Downloaded',
              icon:const Icon(
                Icons.download_done_outlined,
              ),
              onPressed:()=>state.removeDownload(track),
            )
          else if(state.canDownload(track))
            IconButton(
              tooltip:'Download',
              icon:const Icon(
                Icons.download_outlined,
              ),
              onPressed:()=>state.download(track),
            )
          else
            IconButton(
              tooltip:state.likes.containsKey(track.id)
                  ?'Remove from liked'
                  :'Like',
              icon:Icon(
                state.likes.containsKey(track.id)
                    ?Icons.favorite
                    :Icons.favorite_border,
              ),
              onPressed:()=>state.toggleLike(track),
            ),
          IconButton(
            tooltip:'More',
            icon:const Icon(Icons.more_vert),
            onPressed:()=>trackMenu(context,track),
          ),
        ],
      ),
    );
  }
}

void lyricsSheet(BuildContext c,Track t){showModalBottomSheet(context:c,isScrollControlled:true,showDragHandle:true,builder:(_)=>DraggableScrollableSheet(expand:false,initialChildSize:.8,builder:(_,sc)=>FutureBuilder<String?>(future:fetchLyrics(t),builder:(c,s){if(s.connectionState!=ConnectionState.done)return const Center(child:CircularProgressIndicator());return SingleChildScrollView(controller:sc,padding:const EdgeInsets.all(24),child:Text(s.data?.isNotEmpty==true?s.data!:'No lyrics found.',style:const TextStyle(fontSize:18,height:1.6)));})));}
void sleepSheet(BuildContext c){final s=c.read<AppState>();showModalBottomSheet(context:c,showDragHandle:true,builder:(_)=>SafeArea(child:Column(mainAxisSize:MainAxisSize.min,children:[const ListTile(title:Text('Sleep timer',style:TextStyle(fontWeight:FontWeight.bold))),for(final m in [15,30,45,60])ListTile(title:Text('$m minutes'),onTap:(){s.setSleep(m);Navigator.pop(c);}),ListTile(title:const Text('Off'),onTap:(){s.setSleep(null);Navigator.pop(c);})])));}
void queueSheet(BuildContext c){
  final state=c.read<AppState>();

  showModalBottomSheet(
    context:c,
    isScrollControlled:true,
    showDragHandle:true,
    builder:(_)=>DraggableScrollableSheet(
      expand:false,
      initialChildSize:.75,
      maxChildSize:.95,
      builder:(_,scrollController)=>Column(
        children:[
          Padding(
            padding:const EdgeInsets.fromLTRB(
              20,
              4,
              12,
              10,
            ),
            child:Row(
              children:[
                const Expanded(
                  child:Text(
                    'Queue',
                    style:TextStyle(
                      fontSize:20,
                      fontWeight:FontWeight.w800,
                    ),
                  ),
                ),
                Text(
                  '${state.queue.length} tracks',
                ),
              ],
            ),
          ),

          Expanded(
            child:ListView.builder(
              controller:scrollController,
              itemCount:state.queue.length,
              itemBuilder:(context,i){
                final track=state.queue[i];
                final active=i==state.index;

                return ListTile(
                  leading:Stack(
                    children:[
                      art(track.image,48,r:8),
                      if(active)
                        Positioned(
                          right:0,
                          bottom:0,
                          child:Container(
                            width:18,
                            height:18,
                            decoration:BoxDecoration(
                              color:accent,
                              borderRadius:
                                  BorderRadius.circular(5),
                            ),
                            child:const Icon(
                              Icons.equalizer_rounded,
                              size:12,
                              color:Colors.white,
                            ),
                          ),
                        ),
                    ],
                  ),
                  title:Text(
                    track.title,
                    maxLines:1,
                    overflow:TextOverflow.ellipsis,
                    style:TextStyle(
                      fontWeight:active
                          ?FontWeight.w800
                          :FontWeight.w500,
                      color:active?accent:null,
                    ),
                  ),
                  subtitle:Text(
                    track.artist,
                    maxLines:1,
                    overflow:TextOverflow.ellipsis,
                  ),
                  onTap:(){
                    state.play(
                      state.queue,
                      i,
                    );
                    Navigator.pop(c);
                  },
                  trailing:PopupMenuButton<String>(
                    onSelected:(value){
                      if(value=='next'){
                        state.queue.removeAt(i);
                        state.queue.insert(
                          state.index+1,
                          track,
                        );
                        state.notifyListeners();
                      }

                      if(value=='remove'&&
                          state.queue.length>1){
                        state.queue.removeAt(i);

                        if(i<state.index){
                          state.index--;
                        }else if(
                            state.index>=state.queue.length){
                          state.index=
                              state.queue.length-1;
                        }

                        state.notifyListeners();
                      }
                    },
                    itemBuilder:(_)=>const[
                      PopupMenuItem(
                        value:'next',
                        child:Text('Play next'),
                      ),
                      PopupMenuItem(
                        value:'remove',
                        child:Text('Remove from queue'),
                      ),
                    ],
                  ),
                );
              },
            ),
          ),
        ],
      ),
    ),
  );
}
