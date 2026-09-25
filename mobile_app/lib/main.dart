import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'router.dart';
import 'theme/app_theme.dart';

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
    
    return MaterialApp.router(
      title: 'Pay As You Maintain',
      theme: AppTheme.lightTheme,
      routerConfig: router,
    );
  }
}
