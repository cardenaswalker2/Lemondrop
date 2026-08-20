import 'package:flutter/material.dart';

class AppTheme {
  // Paleta de colores Lemon Drop
  static const Color primaryLemon = Color(0xFFFCE22A);
  static const Color darkBg = Color(0xFF172018);
  static const Color creamBg = Color(0xFFFFFDF6);
  static const Color softGreen = Color(0xFFE8F5E9);
  static const Color mintGreen = Color(0xFFD8EBB5);
  static const Color darkGreen = Color(0xFF2E6F40);
  static const Color strawberryRed = Color(0xFFFF6B6B);
  static const Color textDark = Color(0xFF1E2721);
  static const Color textLight = Color(0xFFFFFFFF);
  static const Color textGray = Color(0xFF707A72);

  static ThemeData get lightTheme {
    return ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.light(
        primary: primaryLemon,
        secondary: mintGreen,
        background: creamBg,
        surface: Colors.white,
        onPrimary: darkBg,
        onSecondary: darkBg,
        onBackground: textDark,
        onSurface: textDark,
        error: strawberryRed,
      ),
      scaffoldBackgroundColor: creamBg,
      cardTheme: CardThemeData(
        color: Colors.white,
        elevation: 2,
        shadowColor: Colors.black.withOpacity(0.05),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: primaryLemon,
          foregroundColor: darkBg,
          elevation: 1,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
          textStyle: const TextStyle(
            fontWeight: FontWeight.bold,
            fontSize: 16,
          ),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: softGreen.withOpacity(0.4),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide.none,
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide.none,
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: darkGreen, width: 1.5),
        ),
        labelStyle: const TextStyle(color: textGray, fontSize: 14),
        floatingLabelStyle: const TextStyle(color: darkGreen, fontWeight: FontWeight.bold),
        prefixIconColor: textGray,
        suffixIconColor: textGray,
      ),
      chipTheme: ChipThemeData(
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
        backgroundColor: softGreen,
        side: BorderSide.none,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      ),
      tabBarTheme: TabBarThemeData(
        labelColor: darkGreen,
        unselectedLabelColor: textGray,
        indicatorColor: darkGreen,
        indicatorSize: TabBarIndicatorSize.label,
        labelStyle: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: creamBg,
        elevation: 0,
        centerTitle: false,
        iconTheme: IconThemeData(color: textDark),
        titleTextStyle: TextStyle(
          color: textDark,
          fontWeight: FontWeight.bold,
          fontSize: 20,
        ),
      ),
    );
  }
}
