import 'package:flutter/material.dart';
import 'package:just_audio_background/just_audio_background.dart';
import 'package:provider/provider.dart';
import 'cloud.dart';
import 'screens.dart';
import 'state.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await JustAudioBackground.init(
    androidNotificationChannelId: 'com.aether.wave.audio',
    androidNotificationChannelName: 'Aetherwave playback',
    androidNotificationOngoing: true,
  );
  await initCloud();
  final s = AppState();
  await s.load();
  runApp(ChangeNotifierProvider.value(value: s, child: const AetherApp()));
}

class AetherApp extends StatelessWidget {
  const AetherApp({super.key});
  @override
  Widget build(BuildContext context) {
    final mode = context.select<AppState, ThemeMode>((s) => s.theme);
    ThemeData t(Brightness b) => ThemeData(
          useMaterial3: true,
          brightness: b,
          colorSchemeSeed: const Color(0xFF7C4DFF),
          scaffoldBackgroundColor: b == Brightness.dark ? const Color(0xFF0B0B12) : null,
        );
    return MaterialApp(
      title: 'Aetherwave',
      debugShowCheckedModeBanner: false,
      theme: t(Brightness.light),
      darkTheme: t(Brightness.dark),
      themeMode: mode,
      home: const Shell(),
    );
  }
}
