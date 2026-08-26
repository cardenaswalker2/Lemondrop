import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../core/models/models.dart';
import '../../../core/theme/app_theme.dart';
import '../../auth/providers/auth_provider.dart';
import '../providers/orders_provider.dart';
import 'order_detail_screen.dart';
import 'widgets/advisor_badges.dart';
import 'widgets/order_timer_badge.dart';

class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final authState = ref.watch(authProvider);
    final statsAsync = ref.watch(statsProvider);
    final ordersAsync = ref.watch(activeOrdersProvider);
    final currentUser = authState.user;

    final todayStr = DateFormat('EEEE, d MMMM', 'es_CO').format(DateTime.now());
    final capitalizeToday = todayStr[0].toUpperCase() + todayStr.substring(1);

    return Scaffold(
      body: SafeArea(
        child: RefreshIndicator(
          color: AppTheme.darkGreen,
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
                            '¡Hola, ${currentUser?.name ?? "Asesor"}! 👋',
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
                const SizedBox(height: 20),

                // Operational KPIs Header
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
                  data: (stats) => Column(
                    children: [
                      GridView.count(
                        crossAxisCount: 2,
                        shrinkWrap: true,
                        physics: const NeverScrollableScrollPhysics(),
                        crossAxisSpacing: 10,
                        mainAxisSpacing: 10,
                        childAspectRatio: 1.5,
                        children: [
                          _buildKpiCard('🔔 PENDIENTES', stats.pendingCount.toString(), AppTheme.primaryLemon, AppTheme.darkBg),
                          _buildKpiCard('🧊 PREPARANDO', stats.preparingCount.toString(), AppTheme.mintGreen, AppTheme.darkGreen),
                          _buildKpiCard('🎉 LISTOS', stats.readyCount.toString(), AppTheme.softGreen, AppTheme.darkGreen),
                          _buildKpiCard('✅ ENTREGADOS', stats.deliveredCountToday.toString(), Colors.white, AppTheme.textDark),
                        ],
                      ),
                      const SizedBox(height: 10),
                      Row(
                        children: [
                          if (stats.urgentCount > 0) ...[
                            Expanded(
                              child: _buildMiniKpiCard('🔥 URGENTES', stats.urgentCount.toString(), const Color(0xFFFFEBEE), AppTheme.strawberryRed),
                            ),
                            const SizedBox(width: 10),
                          ],
                          Expanded(
                            child: _buildMiniKpiCard('👤 SIN ASIGNAR', stats.unassignedCount.toString(), const Color(0xFFF1F5F9), const Color(0xFF334155)),
                          ),
                          const SizedBox(width: 10),
                          Expanded(
                            child: _buildMiniKpiCard('✨ MIS DESPACHOS', stats.myDeliveredCountToday.toString(), AppTheme.softGreen, AppTheme.darkGreen),
                          ),
                        ],
                      ),
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
                const SizedBox(height: 24),

                // Highlighted Urgent or Motivation Banner
                _buildOperationalInsightBanner(ordersAsync, statsAsync),
                const SizedBox(height: 24),

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
                        return _buildNewOrderCard(context, ref, order, currentUser?.username);
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
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(14.0),
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
                letterSpacing: 0.5,
              ),
            ),
            Text(
              value,
              style: TextStyle(
                fontSize: 26,
                fontWeight: FontWeight.w900,
                color: textColor,
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildMiniKpiCard(String title, String value, Color bgColor, Color textColor) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: textColor.withOpacity(0.85)),
          ),
          const SizedBox(height: 2),
          Text(
            value,
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w900, color: textColor),
          ),
        ],
      ),
    );
  }

  Widget _buildOperationalInsightBanner(AsyncValue<List<Order>> ordersAsync, AsyncValue<Stats> statsAsync) {
    final urgentCount = statsAsync.asData?.value.urgentCount ?? 0;
    if (urgentCount > 0) {
      return Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: const Color(0xFFFFEBEE),
          border: Border.all(color: AppTheme.strawberryRed, width: 1.5),
          borderRadius: BorderRadius.circular(18),
        ),
        child: Row(
          children: [
            const Text('🔥', style: TextStyle(fontSize: 28)),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    '¡Atención prioritaria!',
                    style: TextStyle(fontWeight: FontWeight.w900, fontSize: 14, color: AppTheme.strawberryRed),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    'Hay $urgentCount pedido(s) con PRIORIDAD ALTA en la cola de producción.',
                    style: const TextStyle(fontSize: 12, color: Color(0xFFB71C1C)),
                  ),
                ],
              ),
            ),
          ],
        ),
      );
    }

    return statsAsync.maybeWhen(
      data: (stats) {
        if (stats.deliveredCountToday == 0) return const SizedBox.shrink();
        return Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            gradient: const LinearGradient(
              colors: [AppTheme.mintGreen, AppTheme.softGreen],
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
            ),
            borderRadius: BorderRadius.circular(18),
          ),
          child: Row(
            children: [
              const Text('✨', style: TextStyle(fontSize: 28)),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      '¡Excelente ritmo hoy!',
                      style: TextStyle(
                        fontWeight: FontWeight.w900,
                        fontSize: 14,
                        color: AppTheme.darkGreen,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      'Se han despachado ${stats.deliveredCountToday} pedidos con éxito.',
                      style: TextStyle(
                        fontSize: 12,
                        color: AppTheme.darkGreen.withOpacity(0.9),
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

  Widget _buildNewOrderCard(BuildContext context, WidgetRef ref, Order order, String? currentUsername) {
    final itemsStr = order.items.map((i) => '${i.quantity}x ${i.productName} (${i.flavorName})').join(', ');

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(18),
        side: order.isUrgent
            ? const BorderSide(color: AppTheme.strawberryRed, width: 2)
            : BorderSide.none,
      ),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                      decoration: BoxDecoration(
                        color: AppTheme.primaryLemon,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: const Text(
                        'NUEVO 🔔',
                        style: TextStyle(
                          fontSize: 10,
                          fontWeight: FontWeight.bold,
                          color: AppTheme.darkBg,
                        ),
                      ),
                    ),
                    if (order.isUrgent) ...[
                      const SizedBox(width: 6),
                      const PriorityBadge(isUrgent: true),
                    ],
                  ],
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
            const SizedBox(height: 10),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: Text(
                    order.customerName,
                    style: const TextStyle(
                      fontSize: 17,
                      fontWeight: FontWeight.bold,
                      color: AppTheme.darkBg,
                    ),
                  ),
                ),
                OrderTimerBadge(order: order, isCompact: true),
              ],
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
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Text(
                      '\$${order.total.toStringAsFixed(0)}',
                      style: const TextStyle(
                        fontSize: 17,
                        fontWeight: FontWeight.w900,
                        color: AppTheme.darkGreen,
                      ),
                    ),
                    const SizedBox(width: 8),
                    AdvisorBadge(assignedAdvisor: order.assignedAdvisor, currentUsername: currentUsername, isCompact: true),
                  ],
                ),
                ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppTheme.primaryLemon,
                    foregroundColor: AppTheme.darkBg,
                    elevation: 0,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
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
                    'VER FICHA',
                    style: TextStyle(fontWeight: FontWeight.w900, fontSize: 12),
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
