import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';

import 'package:aetherwave/main.dart';
import 'package:aetherwave/state.dart';

void main() {
  testWidgets('Aetherwave app renders', (tester) async {
    final state = AppState();

    await tester.pumpWidget(
      ChangeNotifierProvider<AppState>.value(
        value: state,
        child: const AetherwaveApp(),
      ),
    );

    expect(find.text('Home'), findsOneWidget);
    expect(find.text('Search'), findsOneWidget);
    expect(find.text('Library'), findsOneWidget);

    state.player.dispose();
  });
}
