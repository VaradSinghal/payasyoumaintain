import 'package:flutter/material.dart';

class AppTheme {
  // Brand colors from system design docs
  static const Color navyPrimary = Color(0xFF1B3A5C);
  static const Color tealAccent = Color(0xFF2E6F8E);
  
  static ThemeData get lightTheme {
    return ThemeData(
      colorScheme: ColorScheme.fromSeed(
        seedColor: navyPrimary,
        primary: navyPrimary,
        secondary: tealAccent,
      ),
      useMaterial3: true,
      appBarTheme: const AppBarTheme(
        backgroundColor: navyPrimary,
        foregroundColor: Colors.white,
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: tealAccent,
          foregroundColor: Colors.white,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(8),
          ),
        ),
      ),
    );
  }
}
