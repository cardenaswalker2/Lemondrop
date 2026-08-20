import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../core/models/models.dart';
import '../../../core/theme/app_theme.dart';
import '../../auth/providers/auth_provider.dart';
import '../providers/orders_provider.dart';
import 'order_detail_screen.dart';

class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final authState = ref.watch(authProvider);
    final statsAsync = ref.watch(statsProvider);
    final ordersAsync = ref.watch(activeOrdersProvider);

    final todayStr = DateFormat('EEEE, d MMMM', 'es_CO').format(DateTime.now());
    final capitalizeToday = todayStr[0].toUpperCase() + todayStr.substring(1);

    return Scaffold(
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: () async {
            ref.read(statsProvider.notifier).fetchStats();
            ref.read(activeOrdersProvider.notifier).fetchActiveOrders();
          },
          child: SingleChildScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(20.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // Header section
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '¡Hola, ${authState.user?.name ?? "María"}! 👋',
                            style: const TextStyle(
                              fontSize: 24,
                              fontWeight: FontWeight.w900,
                              color: AppTheme.darkBg,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            capitalizeToday,
                            style: const TextStyle(
                              fontSize: 14,
                              color: AppTheme.textGray,
                            ),
                          ),
                        ],
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.all(10),
                      decoration: const BoxDecoration(
                        color: AppTheme.primaryLemon,
                        shape: BoxShape.circle,
                      ),
                      child: const Text('🍋', style: TextStyle(fontSize: 20)),
                    ),
                  ],
                ),
                const SizedBox(height: 24),

                // Operational KPIs Grid
                const Text(
                  'Operación Hoy',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                    color: AppTheme.darkBg,
                  ),
                ),
                const SizedBox(height: 12),
                statsAsync.when(
                  data: (stats) => GridView.count(
                    crossAxisCount: 2,
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    crossAxisSpacing: 12,
                    mainAxisSpacing: 12,
                    childAspectRatio: 1.4,
                    children: [
                      _buildKpiCard('🔔 PENDIENTES', stats.pendingCount.toString(), AppTheme.primaryLemon, AppTheme.darkBg),
                      _buildKpiCard('🧊 PREPARANDO', stats.preparingCount.toString(), AppTheme.mintGreen, AppTheme.darkGreen),
                      _buildKpiCard('🎉 LISTOS', stats.readyCount.toString(), AppTheme.softGreen, AppTheme.darkGreen),
                      _buildKpiCard('✅ ENTREGADOS', stats.deliveredCountToday.toString(), Colors.white, AppTheme.textDark),
                    ],
                  ),
                  loading: () => const Center(
                    child: Padding(
                      padding: EdgeInsets.symmetric(vertical: 24.0),
                      child: CircularProgressIndicator(color: AppTheme.darkGreen),
                    ),
                  ),
                  error: (_, __) => Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: AppTheme.strawberryRed.withOpacity(0.1),
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: const Text(
                      'No se pudieron cargar las estadísticas.',
                      style: TextStyle(color: AppTheme.strawberryRed, fontSize: 13),
                    ),
                  ),
                ),
                const SizedBox(height: 28),

                // Highlighted Notification Board or Motivation banner
                _buildMotivationBanner(statsAsync),
                const SizedBox(height: 28),

                // New/Pending Orders Board
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Text(
                      'Nuevos Pedidos Recibidos',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                        color: AppTheme.darkBg,
                      ),
                    ),
                    TextButton(
                      onPressed: () {
                        // Switch tab to orders list
                        final layoutState = ref.read(advisorLayoutTabProvider.notifier);
                        layoutState.state = 1; // index for Pedidos Tab
                      },
                      child: const Text(
                        'Ver Todos',
                        style: TextStyle(color: AppTheme.darkGreen, fontWeight: FontWeight.bold),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 8),

                ordersAsync.when(
                  data: (orders) {
                    final newOrders = orders.where((o) => o.status == OrderStatus.received).toList();
                    if (newOrders.isEmpty) {
                      return Card(
                        child: Padding(
                          padding: const EdgeInsets.all(28.0),
                          child: Column(
                            children: [
                              const Text('🎉', style: TextStyle(fontSize: 36)),
                              const SizedBox(height: 8),
                              const Text(
                                'No hay pedidos nuevos por ahora.',
                                style: TextStyle(fontWeight: FontWeight.bold, color: AppTheme.darkBg),
                              ),
                              const SizedBox(height: 4),
                              Text(
                                '¡Todo está bajo control!',
                                style: TextStyle(fontSize: 12, color: AppTheme.textGray),
                              ),
                            ],
                          ),
                        ),
                      );
                    }

                    return ListView.builder(
                      shrinkWrap: true,
                      physics: const NeverScrollableScrollPhysics(),
                      itemCount: newOrders.length,
                      itemBuilder: (context, index) {
                        final order = newOrders[index];
                        return _buildNewOrderCard(context, ref, order);
                      },
                    );
                  },
                  loading: () => const Center(child: CircularProgressIndicator(color: AppTheme.darkGreen)),
                  error: (err, _) => const Center(
                    child: Text('Error al cargar pedidos. Verifica tu conexión.'),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildKpiCard(String title, String value, Color bgColor, Color textColor) {
    return Card(
      color: bgColor,
      elevation: 1,
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              title,
              style: TextStyle(
                fontSize: 10,
                fontWeight: FontWeight.w900,
                color: textColor.withOpacity(0.8),
                letterSpacing: 1,
              ),
            ),
            Text(
              value,
              style: TextStyle(
                fontSize: 28,
                fontWeight: FontWeight.w900,
                color: textColor,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMotivationBanner(AsyncValue<Stats> statsAsync) {
    return statsAsync.maybeWhen(
      data: (stats) {
        if (stats.deliveredCountToday == 0) return const SizedBox.shrink();
        return Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(
            gradient: const LinearGradient(
              colors: [AppTheme.mintGreen, AppTheme.softGreen],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
            borderRadius: BorderRadius.circular(20),
          ),
          child: Row(
            children: [
              const Text('🔥', style: TextStyle(fontSize: 32)),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      '¡Excelente trabajo hoy!',
                      style: TextStyle(
                        fontWeight: FontWeight.w900,
                        fontSize: 15,
                        color: AppTheme.darkGreen,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      'Has gestionado ${stats.deliveredCountToday} pedidos con éxito.',
                      style: TextStyle(
                        fontSize: 13,
                        color: AppTheme.darkGreen.withOpacity(0.85),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        );
      },
      orElse: () => const SizedBox.shrink(),
    );
  }

  Widget _buildNewOrderCard(BuildContext context, WidgetRef ref, Order order) {
    // Collect item summaries
    final itemsStr = order.items.map((i) => '${i.quantity}x ${i.productName}').join(', ');

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: AppTheme.primaryLemon,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    'NUEVO 🔔',
                    style: const TextStyle(
                      fontSize: 10,
                      fontWeight: FontWeight.bold,
                      color: AppTheme.darkBg,
                    ),
                  ),
                ),
                Text(
                  order.orderCode,
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                    color: AppTheme.textGray,
                    fontSize: 13,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Text(
              order.customerName,
              style: const TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.bold,
                color: AppTheme.darkBg,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              itemsStr,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(
                fontSize: 13,
                color: AppTheme.textGray,
              ),
            ),
            const SizedBox(height: 14),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  '\$${order.total.toStringAsFixed(0).replaceAllMapped(RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'), (Match m) => '${m[1]}.')}',
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w900,
                    color: AppTheme.darkGreen,
                  ),
                ),
                OutlinedButton(
                  style: OutlinedButton.styleFrom(
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                    side: const BorderSide(color: AppTheme.primaryLemon, width: 2),
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  ),
                  onPressed: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (context) => OrderDetailScreen(orderId: order.id),
                      ),
                    );
                  },
                  child: const Text(
                    'VER PEDIDO',
                    style: TextStyle(color: AppTheme.darkBg, fontWeight: FontWeight.bold, fontSize: 12),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

// Global Provider for layout tab navigation inside Advisor Layout
final advisorLayoutTabProvider = StateProvider<int>((ref) => 0);
