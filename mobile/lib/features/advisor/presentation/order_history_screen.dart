import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/models/models.dart';
import '../../auth/providers/auth_provider.dart';
import '../providers/orders_provider.dart';
import 'order_detail_screen.dart';
import 'widgets/advisor_badges.dart';
import 'widgets/order_timer_badge.dart';

class OrderHistoryScreen extends ConsumerStatefulWidget {
  const OrderHistoryScreen({super.key});

  @override
  ConsumerState<OrderHistoryScreen> createState() => _OrderHistoryScreenState();
}

class _OrderHistoryScreenState extends ConsumerState<OrderHistoryScreen> {
  final _searchController = TextEditingController();
  final _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _searchController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_scrollController.position.pixels >= _scrollController.position.maxScrollExtent - 200) {
      ref.read(historyProvider.notifier).fetchNextPage();
    }
  }

  @override
  Widget build(BuildContext context) {
    final historyState = ref.watch(historyProvider);
    final authState = ref.watch(authProvider);
    final currentUsername = authState.user?.username;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Historial de Despachos'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_rounded),
            tooltip: 'Actualizar Historial',
            onPressed: () => ref.read(historyProvider.notifier).fetchHistory(isRefresh: true),
          ),
        ],
      ),
      body: Column(
        children: [
          // Search box
          Padding(
            padding: const EdgeInsets.fromLTRB(16.0, 10.0, 16.0, 6.0),
            child: TextField(
              controller: _searchController,
              decoration: InputDecoration(
                hintText: 'Buscar por código, cliente o teléfono...',
                prefixIcon: const Icon(Icons.search_rounded),
                suffixIcon: _searchController.text.isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.clear_rounded),
                        onPressed: () {
                          _searchController.clear();
                          ref.read(historyProvider.notifier).setSearchQuery('');
                        },
                      )
                    : null,
                contentPadding: const EdgeInsets.all(12),
              ),
              onSubmitted: (val) => ref.read(historyProvider.notifier).setSearchQuery(val),
              onChanged: (val) {
                if (val.isEmpty) {
                  ref.read(historyProvider.notifier).setSearchQuery('');
                }
              },
            ),
          ),

          // Time Filters
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 4.0),
            child: Row(
              children: [
                _buildTimeFilterChip('Hoy', 'HOY', historyState.timeFilter),
                const SizedBox(width: 8),
                _buildTimeFilterChip('Ayer', 'AYER', historyState.timeFilter),
                const SizedBox(width: 8),
                _buildTimeFilterChip('Últimos 7 días', '7_DIAS', historyState.timeFilter),
                const SizedBox(width: 8),
                _buildTimeFilterChip('Todos', 'TODOS', historyState.timeFilter),
                const VerticalDivider(width: 16),
                _buildStatusFilterChip('Todos', 'ALL', historyState.statusFilter),
                const SizedBox(width: 6),
                _buildStatusFilterChip('Entregados', 'DELIVERED', historyState.statusFilter),
                const SizedBox(width: 6),
                _buildStatusFilterChip('Cancelados', 'CANCELLED', historyState.statusFilter),
              ],
            ),
          ),
          const Divider(height: 12),

          Expanded(
            child: RefreshIndicator(
              color: AppTheme.darkGreen,
              onRefresh: () async {
                await ref.read(historyProvider.notifier).fetchHistory(isRefresh: true);
              },
              child: historyState.isLoading && historyState.orders.isEmpty
                  ? const Center(child: CircularProgressIndicator(color: AppTheme.darkGreen))
                  : historyState.orders.isEmpty
                      ? ListView(
                          physics: const AlwaysScrollableScrollPhysics(),
                          children: const [
                            SizedBox(height: 80),
                            Center(
                              child: Column(
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: [
                                  Text('📋', style: TextStyle(fontSize: 48)),
                                  SizedBox(height: 12),
                                  Text(
                                    'No se encontraron pedidos finalizados.',
                                    style: TextStyle(fontWeight: FontWeight.bold, color: AppTheme.textGray),
                                  ),
                                  SizedBox(height: 4),
                                  Text(
                                    'Desliza hacia abajo para refrescar.',
                                    style: TextStyle(fontSize: 12, color: AppTheme.textGray),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        )
                      : ListView.builder(
                          controller: _scrollController,
                          physics: const AlwaysScrollableScrollPhysics(),
                          padding: const EdgeInsets.all(16.0),
                          itemCount: historyState.orders.length + (historyState.isFetchingMore ? 1 : 0),
                          itemBuilder: (context, index) {
                            if (index == historyState.orders.length) {
                              return const Padding(
                                padding: EdgeInsets.all(16.0),
                                child: Center(child: CircularProgressIndicator(color: AppTheme.darkGreen)),
                              );
                            }
                            final order = historyState.orders[index];
                            return _buildHistoryCard(context, order, currentUsername);
                          },
                        ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTimeFilterChip(String label, String value, String currentValue) {
    final isSelected = currentValue == value;
    return FilterChip(
      label: Text(
        label,
        style: TextStyle(
          fontSize: 11,
          fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
          color: isSelected ? Colors.white : AppTheme.darkBg,
        ),
      ),
      selected: isSelected,
      selectedColor: AppTheme.darkGreen,
      backgroundColor: Colors.grey.shade100,
      showCheckmark: false,
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
      onSelected: (_) {
        ref.read(historyProvider.notifier).setTimeFilter(value);
      },
    );
  }

  Widget _buildStatusFilterChip(String label, String value, String currentValue) {
    final isSelected = currentValue == value;
    return FilterChip(
      label: Text(
        label,
        style: TextStyle(
          fontSize: 11,
          fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
          color: isSelected ? Colors.white : AppTheme.darkBg,
        ),
      ),
      selected: isSelected,
      selectedColor: value == 'CANCELLED' ? AppTheme.strawberryRed : AppTheme.darkGreen,
      backgroundColor: Colors.grey.shade100,
      showCheckmark: false,
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
      onSelected: (_) {
        ref.read(historyProvider.notifier).setStatusFilter(value);
      },
    );
  }

  Widget _buildHistoryCard(BuildContext context, Order order, String? currentUsername) {
    final itemsStr = order.items.map((i) => '${i.quantity}x ${i.productName} (${i.flavorName})').join(', ');
    final isDelivered = order.status == OrderStatus.delivered;
    final timeFormatter = DateFormat('dd/MM hh:mm a');

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(18),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: () {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (context) => OrderDetailScreen(
                orderId: order.id,
                preloadedOrder: order,
              ),
            ),
          );
        },
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
                          color: isDelivered
                              ? AppTheme.softGreen
                              : AppTheme.strawberryRed.withOpacity(0.15),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Text(
                          order.status.nameInSpanish.toUpperCase(),
                          style: TextStyle(
                            fontSize: 10,
                            fontWeight: FontWeight.bold,
                            color: isDelivered ? AppTheme.darkGreen : AppTheme.strawberryRed,
                          ),
                        ),
                      ),
                      if (order.isUrgent) ...[
                        const SizedBox(width: 6),
                        const PriorityBadge(isUrgent: true, isCompact: true),
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
                      style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
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
                style: const TextStyle(fontSize: 13, color: AppTheme.textGray),
              ),
              if (!isDelivered && order.cancellationReason != null && order.cancellationReason!.isNotEmpty) ...[
                const SizedBox(height: 6),
                Container(
                  padding: const EdgeInsets.all(6),
                  decoration: BoxDecoration(
                    color: const Color(0xFFFFEBEE),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(
                    'Motivo: ${order.cancellationReason}',
                    style: const TextStyle(fontSize: 11, color: AppTheme.strawberryRed, fontStyle: FontStyle.italic),
                  ),
                ),
              ],
              const Divider(height: 16),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      Text(
                        '\$${order.total.toStringAsFixed(0)}',
                        style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 16, color: AppTheme.darkGreen),
                      ),
                      const SizedBox(width: 8),
                      AdvisorBadge(assignedAdvisor: order.assignedAdvisor, currentUsername: currentUsername, isCompact: true),
                    ],
                  ),
                  Row(
                    children: [
                      Text(
                        timeFormatter.format(order.deliveredAt ?? order.cancelledAt ?? order.updatedAt),
                        style: const TextStyle(fontSize: 11, color: AppTheme.textGray),
                      ),
                      const SizedBox(width: 4),
                      const Icon(Icons.chevron_right_rounded, color: AppTheme.textGray, size: 18),
                    ],
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
