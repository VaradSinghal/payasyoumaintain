import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../models/models.dart';
import '../providers/auth_provider.dart';

class ShellScreen extends ConsumerWidget {
  final StatefulNavigationShell navigationShell;

  const ShellScreen({super.key, required this.navigationShell});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final authState = ref.watch(authProvider);
    final demoScenario = authState.vehicleId != null
        ? DemoScenario.findByVehicleId(authState.vehicleId!)
        : null;

    final isDashboard = navigationShell.currentIndex == 0;

    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(isDashboard ? 'Dashboard' : 'Claims'),
            if (authState.isDemo && demoScenario != null)
              Text(
                'Demo: ${demoScenario.displayName}',
                style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.normal,
                  color: Colors.white70,
                ),
              ),
          ],
        ),
        actions: [
          PopupMenuButton<String>(
            onSelected: (value) {
              if (value == 'reset' || value == 'switch_vehicle') {
                ref.read(authProvider.notifier).logout();
              } else if (value == 'privacy') {
                context.push('/privacy');
              }
            },
            itemBuilder: (context) => [
              const PopupMenuItem(
                value: 'privacy',
                child: Text('Privacy & tracking'),
              ),
              const PopupMenuItem(
                value: 'switch_vehicle',
                child: Text('Switch vehicle'),
              ),
              const PopupMenuItem(
                value: 'reset',
                child: Text('Reset session'),
              ),
            ],
          ),
        ],
      ),
      body: navigationShell,
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: navigationShell.currentIndex,
        onTap: (index) {
          navigationShell.goBranch(
            index,
            initialLocation: index == navigationShell.currentIndex,
          );
        },
        items: const [
          BottomNavigationBarItem(
            icon: Icon(Icons.dashboard),
            label: 'Dashboard',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.assignment),
            label: 'Claims',
          ),
        ],
      ),
    );
  }
}
