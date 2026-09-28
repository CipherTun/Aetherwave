import 'package:supabase_flutter/supabase_flutter.dart';

const _url = String.fromEnvironment('SUPABASE_URL');
const _key = String.fromEnvironment('SUPABASE_ANON_KEY');
bool get cloudOn => _url.isNotEmpty && _key.isNotEmpty;
Future<void> initCloud() async {
  if (cloudOn) await Supabase.initialize(url: _url, publishableKey: _key);
}

SupabaseClient? get sb => cloudOn ? Supabase.instance.client : null;
