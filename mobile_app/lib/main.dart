import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'router.dart';
import 'theme/app_theme.dart';
import 'config/api_config.dart';

void main() {
  runApp(
    const ProviderScope(
      child: PayMuApp(),
    ),
  );
}

class PayMuApp extends ConsumerWidget {
  const PayMuApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(routerProvider);
    
    Widget app = MaterialApp.router(
      title: 'Pay As You Maintain',
      theme: AppTheme.lightTheme,
      routerConfig: router,
      builder: (context, child) {
        if (ApiConfig.isDemoMode) {
          return Directionality(
            textDirection: TextDirection.ltr,
            child: Banner(
              location: BannerLocation.topEnd,
              message: 'Demo data',
              color: Colors.orange,
              child: child,
            ),
          );
        }
        return child!;
      },
    );

    return app;
  }
}
