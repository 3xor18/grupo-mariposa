import 'package:flutter/semantics.dart';
import 'package:flutter/widgets.dart';
import 'package:http/http.dart' as http;
import 'package:order_tracker/app/bootstrap.dart';
import 'package:order_tracker/core/platform/web_browser.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  SemanticsBinding.instance.ensureSemantics();
  final bootstrap = AppBootstrap(
    location: const WebBrowserLocation(),
    store: const WebSessionStore(),
    httpClient: http.Client(),
  );
  runApp(await bootstrap.createApp());
}
