import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'providers/auth_provider.dart';
import 'screens/claims_screen.dart';
import 'screens/dashboard_screen.dart';
import 'screens/onboarding_screen.dart';
import 'screens/shell_screen.dart';
import 'widgets/shared_widgets.dart';

final routerProvider = Provider<GoRouter>((ref) {
  final authState = ref.watch(authProvider);

  return GoRouter(
    initialLocation: '/',
    redirect: (context, state) {
      if (authState.isLoading) return null; // wait for initialization

      final isGoingToOnboarding = state.matchedLocation == '/onboarding';
      final isLoggedIn = authState.vehicleId != null;

      if (!isLoggedIn && !isGoingToOnboarding) {
        return '/onboarding';
      }
      
      if (isLoggedIn && isGoingToOnboarding) {
        return '/';
      }

      return null;
    },
    routes: [
      GoRoute(
        path: '/onboarding',
        builder: (context, state) => const OnboardingScreen(),
      ),
      StatefulShellRoute.indexedStack(
        builder: (context, state, navigationShell) {
          if (authState.isLoading) {
            return const Scaffold(
              body: AppLoading(message: 'Loading secure storage...'),
            );
          }
          return ShellScreen(navigationShell: navigationShell);
        },
        branches: [
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/',
                builder: (context, state) => const DashboardScreen(),
              ),
            ],
          ),
          StatefulShellBranch(
            routes: [
              GoRoute(
                path: '/claims',
                builder: (context, state) => const ClaimsScreen(),
              ),
            ],
          ),
        ],
      ),
    ],
  );
});
