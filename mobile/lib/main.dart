import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'core/theme/app_theme.dart';
import 'core/storage/preferences.dart';
import 'features/auth/providers/auth_provider.dart';
import 'features/advisor/presentation/advisor_main_layout.dart';
import 'features/admin/presentation/admin_screen.dart';
import 'features/welcome/presentation/welcome_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await AppPreferences.init();
  await initializeDateFormatting('es_CO', null);
  runApp(
    const ProviderScope(
      child: LemonDropApp(),
    ),
  );
}

class LemonDropApp extends ConsumerWidget {
  const LemonDropApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final authState = ref.watch(authProvider);

    return MaterialApp(
      title: 'Lemon Drop Mobile',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      home: _getHomeWidget(authState),
    );
  }

  Widget _getHomeWidget(AuthState state) {
    if (state.isLoading) {
      return const Scaffold(
        backgroundColor: AppTheme.creamBg,
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text('🍋', style: TextStyle(fontSize: 48)),
              SizedBox(height: 16),
              CircularProgressIndicator(color: AppTheme.darkGreen),
            ],
          ),
        ),
      );
    }

    final user = state.user;
    if (user == null) {
      return const WelcomeScreen();
    }

    if (user.isAdmin) {
      return const AdminScreen();
    }

    return const AdvisorMainLayout();
  }
}
