import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/storage/preferences.dart';
import '../../auth/providers/auth_provider.dart';
import '../../advisor/providers/orders_provider.dart';

class AdminScreen extends ConsumerWidget {
  const AdminScreen({super.key});

  void _openWebConsole(BuildContext context) async {
    // Generate web dashboard URL based on current configured API host
    final apiUri = Uri.parse(AppPreferences.baseUrl);
    final hasExplicitPort = apiUri.hasPort && apiUri.port != 80 && apiUri.port != 443;
    final portStr = hasExplicitPort ? ':${apiUri.port}' : (apiUri.host.contains('192.168.') || apiUri.host == 'localhost' ? ':8080' : '');
    final scheme = apiUri.scheme == 'https' ? 'https' : 'http';
    final webUrl = Uri.parse('$scheme://${apiUri.host}$portStr/admin/dashboard');

    if (await launchUrl(webUrl, mode: LaunchMode.externalApplication)) {
      // success
    } else {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('No se pudo abrir el navegador.')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final authState = ref.watch(authProvider);
    final statsAsync = ref.watch(statsProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Consola Administrativa'),
        actions: [
          IconButton(
            icon: const Icon(Icons.logout_rounded, color: AppTheme.strawberryRed),
            onPressed: () {
              ref.read(authProvider.notifier).logout();
            },
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Admin Info Card
            Card(
              color: AppTheme.primaryLemon,
              child: Padding(
                padding: const EdgeInsets.all(20.0),
                child: Row(
                  children: [
                    const CircleAvatar(
                      radius: 28,
                      backgroundColor: Colors.white,
                      child: Text('👑', style: TextStyle(fontSize: 24)),
                    ),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Administrador: ${authState.user?.name ?? "Admin"}',
                            style: const TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.bold,
                              color: AppTheme.darkBg,
                            ),
                          ),
                          const SizedBox(height: 4),
                          const Text(
                            'Vista móvil reducida de LEMON DROP',
                            style: TextStyle(
                              fontSize: 12,
                              color: AppTheme.textDark,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 24),

            const Text(
              'Estadísticas de Venta y Operación Hoy',
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.bold,
                color: AppTheme.darkBg,
              ),
            ),
            const SizedBox(height: 12),

            statsAsync.when(
              data: (stats) => Column(
                children: [
                  // Highlight KPI Sales today
                  Card(
                    color: AppTheme.mintGreen,
                    child: Padding(
                      padding: const EdgeInsets.all(20.0),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                '💰 VENTAS TOTALES HOY',
                                style: TextStyle(
                                  fontSize: 11,
                                  fontWeight: FontWeight.w900,
                                  color: AppTheme.darkGreen,
                                  letterSpacing: 1,
                                ),
                              ),
                              SizedBox(height: 4),
                              Text(
                                'Pedidos del Asesor',
                                style: TextStyle(fontSize: 11, color: AppTheme.darkGreen),
                              ),
                            ],
                          ),
                          Text(
                            '\$${stats.totalSalesToday.toStringAsFixed(0).replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (Match m) => '${m[1]}.')}',
                            style: const TextStyle(
                              fontSize: 26,
                              fontWeight: FontWeight.w900,
                              color: AppTheme.darkGreen,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  // Detailed operation KPI grid
                  GridView.count(
                    crossAxisCount: 2,
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    crossAxisSpacing: 12,
                    mainAxisSpacing: 12,
                    childAspectRatio: 1.4,
                    children: [
                      _buildMiniKpiCard('CREADOS', stats.ordersCreatedToday.toString()),
                      _buildMiniKpiCard('ENTREGADOS', stats.deliveredCountToday.toString()),
                      _buildMiniKpiCard('TOP PRODUCTO', stats.topProductToday),
                      _buildMiniKpiCard('TOP SABOR', stats.topFlavorToday),
                    ],
                  ),
                ],
              ),
              loading: () => const Center(child: CircularProgressIndicator(color: AppTheme.darkGreen)),
              error: (_, __) => const Center(child: Text('Error al cargar métricas.')),
            ),
            const SizedBox(height: 32),

            // Open Web Portal console Call to Action
            Card(
              child: Padding(
                padding: const EdgeInsets.all(20.0),
                child: Column(
                  children: [
                    const Icon(Icons.laptop_chromebook_rounded, size: 48, color: AppTheme.darkGreen),
                    const SizedBox(height: 12),
                    const Text(
                      'Administración Completa',
                      style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppTheme.darkBg),
                    ),
                    const SizedBox(height: 6),
                    const Text(
                      'El CRUD completo de productos, sabores, complementos, inventarios profundos y gestión de usuarios está disponible únicamente en la versión de escritorio.',
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 12, color: AppTheme.textGray),
                    ),
                    const SizedBox(height: 20),
                    ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppTheme.darkGreen,
                        foregroundColor: Colors.white,
                      ),
                      onPressed: () => _openWebConsole(context),
                      child: const Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.open_in_new_rounded, size: 18),
                          SizedBox(width: 8),
                          Text('ABRIR CONSOLA WEB'),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMiniKpiCard(String title, String value) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              title,
              style: const TextStyle(
                fontSize: 9,
                fontWeight: FontWeight.w900,
                color: AppTheme.textGray,
                letterSpacing: 0.8,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              value,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.w900,
                color: AppTheme.darkBg,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
