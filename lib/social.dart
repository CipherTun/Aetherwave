import 'dart:io';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'cloud.dart';

class SocialApi {
  static Future<List<Map<String, dynamic>>> comments(String trackId) async {
    if (sb == null) return [];
    try {
      final r = await sb!.from('track_comments').select('id,body,created_at,user_id').eq('track_id', trackId).order('created_at', ascending: false);
      return [for (final x in r) Map<String, dynamic>.from(x)];
    } catch (_) { return []; }
  }
  static Future<bool> addComment(String trackId, String body) async {
    final u = sb?.auth.currentUser;
    if (u == null || body.trim().isEmpty) return false;
    try { await sb!.from('track_comments').insert({'track_id': trackId, 'user_id': u.id, 'body': body.trim()}); return true; } catch (_) { return false; }
  }
  static Future<List<Map<String, dynamic>>> notifications() async {
    final u = sb?.auth.currentUser;
    if (u == null) return [];
    try {
      final r = await sb!.from('notifications').select().eq('user_id', u.id).order('created_at', ascending: false).limit(50);
      return [for (final x in r) Map<String, dynamic>.from(x)];
    } catch (_) { return []; }
  }
  static Future<String?> uploadRelease({required String title, required String description}) async {
    final u = sb?.auth.currentUser;
    if (u == null) return null;
    final pick = await FilePicker.platform.pickFiles(type: FileType.audio);
    if (pick == null || pick.files.single.path == null) return null;
    try {
      final file = File(pick.files.single.path!);
      final path = '${u.id}/${DateTime.now().millisecondsSinceEpoch}_${pick.files.single.name}';
      await sb!.storage.from('creator-audio').upload(path, file, fileOptions: const FileOptions(upsert: false));
      await sb!.from('creator_releases').insert({'owner_id': u.id, 'title': title, 'description': description, 'audio_path': path});
      return path;
    } catch (_) { return null; }
  }
}

class CommentsPage extends StatefulWidget {
  final String trackId, title;
  const CommentsPage(this.trackId, this.title, {super.key});
  @override State<CommentsPage> createState() => _CommentsPageState();
}
class _CommentsPageState extends State<CommentsPage> {
  final controller = TextEditingController();
  List<Map<String, dynamic>> items = [];
  bool busy = true;
  @override void initState() { super.initState(); load(); }
  Future<void> load() async { items = await SocialApi.comments(widget.trackId); if (mounted) setState(() => busy = false); }
  Future<void> send() async {
    if (controller.text.trim().isEmpty) return;
    final ok = await SocialApi.addComment(widget.trackId, controller.text);
    if (ok) { controller.clear(); await load(); }
    else if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Sign in to comment.')));
  }
  @override Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: Text('Comments · ${widget.title}')),
    body: Column(children: [
      Expanded(child: busy
        ? const Center(child: CircularProgressIndicator())
        : items.isEmpty
          ? const Center(child: Text('No comments yet. Start the conversation.'))
          : ListView.builder(itemCount: items.length, itemBuilder: (_, i) {
              final id = '${items[i]['user_id']}';
              return ListTile(leading: CircleAvatar(child: Text(id.substring(0, 1).toUpperCase())), title: Text(id), subtitle: Text('${items[i]['body']}'));
            })),
      SafeArea(child: Padding(padding: const EdgeInsets.all(10), child: Row(children: [
        Expanded(child: TextField(controller: controller, decoration: const InputDecoration(hintText: 'Write a comment'))),
        IconButton(onPressed: send, icon: const Icon(Icons.send)),
      ]))),
    ]),
  );
}

class CreatorStudioPage extends StatefulWidget { const CreatorStudioPage({super.key}); @override State<CreatorStudioPage> createState() => _CreatorStudioPageState(); }
class _CreatorStudioPageState extends State<CreatorStudioPage> {
  final title = TextEditingController(), desc = TextEditingController();
  bool busy = false; String status = '';
  Future<void> upload() async {
    if (title.text.trim().isEmpty) { setState(() => status = 'Enter a release title.'); return; }
    setState(() { busy = true; status = 'Select an audio file…'; });
    final r = await SocialApi.uploadRelease(title: title.text, description: desc.text);
    if (mounted) setState(() { busy = false; status = r == null ? 'Upload failed. Check account/storage setup.' : 'Uploaded successfully.'; });
  }
  @override Widget build(BuildContext context) {
    final u = sb?.auth.currentUser;
    return Scaffold(appBar: AppBar(title: const Text('Creator Studio')), body: Padding(padding: const EdgeInsets.all(20), child: u == null
      ? const Center(child: Text('Sign in to publish audio.'))
      : ListView(children: [
          const Text('Publish your own audio', style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800)),
          const SizedBox(height: 8),
          const Text('Uploads are stored in your Supabase creator-audio bucket and recorded as releases.'),
          const SizedBox(height: 22),
          TextField(controller: title, decoration: const InputDecoration(labelText: 'Release title')),
          const SizedBox(height: 12),
          TextField(controller: desc, maxLines: 4, decoration: const InputDecoration(labelText: 'Description')),
          const SizedBox(height: 18),
          FilledButton.icon(onPressed: busy ? null : upload, icon: const Icon(Icons.upload), label: Text(busy ? 'Uploading…' : 'Choose audio & publish')),
          if (status.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 18), child: Text(status)),
        ])));
  }
}

class NotificationsPage extends StatelessWidget {
  const NotificationsPage({super.key});
  @override Widget build(BuildContext context) => Scaffold(appBar: AppBar(title: const Text('Notifications')), body: FutureBuilder<List<Map<String, dynamic>>>(
    future: SocialApi.notifications(),
    builder: (context, snap) {
      final l = snap.data ?? [];
      if (l.isEmpty) return const Center(child: Text('No notifications yet.'));
      return ListView(children: [for (final n in l) ListTile(leading: const Icon(Icons.notifications_none), title: Text('${n['type'] ?? 'Activity'}'), subtitle: Text('${n['payload'] ?? ''}'))]);
    },
  ));
}
