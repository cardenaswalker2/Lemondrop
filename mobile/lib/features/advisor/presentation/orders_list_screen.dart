import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/models/models.dart';
import '../../auth/providers/auth_provider.dart';
import '../providers/orders_provider.dart';
import 'order_detail_screen.dart';
import 'widgets/advisor_badges.dart';
import 'widgets/order_timer_badge.dart';

class OrdersListScreen extends ConsumerStatefulWidget {
  const OrdersListScreen({super.key});

  @override
  ConsumerState<OrdersListScreen> createState() => _OrdersListScreenState();
}

class _OrdersListScreenState extends ConsumerState<OrdersListScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;
  final _searchController = TextEditingController();
  String _searchQuery = '';
  String _operationalFilter = 'TODOS'; // 'TODOS', 'URGENTE', 'MIS_PEDIDOS', 'SIN_ASIGNAR'

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 5, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    _searchController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final ordersAsync = ref.watch(activeOrdersProvider);
    final authState = ref.watch(authProvider);
    final currentUsername = authState.user?.username;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Cola de Producción'),
        bottom: TabBar(
          controller: _tabController,
          isScrollable: true,
          tabs: const [
            Tab(text: 'Todos'),
            Tab(text: 'Nuevos 🔔'),
            Tab(text: 'Preparando 🧊'),
            Tab(text: 'Casi Listos 👌'),
            Tab(text: 'Listos 🎉'),
          ],
        ),
      ),
      body: Column(
        children: [
          // Search box
          Padding(
            padding: const EdgeInsets.fromLTRB(16.0, 10.0, 16.0, 6.0),
            child: TextField(
              controller: _searchController,
              decoration: InputDecoration(
                hintText: 'Buscar por código, nombre o teléfono...',
                prefixIcon: const Icon(Icons.search_rounded),
                suffixIcon: _searchQuery.isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.clear_rounded),
                        onPressed: () {
                          setState(() {
                            _searchController.clear();
                            _searchQuery = '';
                          });
                        },
                      )
                    : null,
                contentPadding: const EdgeInsets.all(12),
              ),
              onChanged: (val) {
                setState(() => _searchQuery = val.trim().toLowerCase());
              },
            ),
          ),

          // Operational Filter Chips
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 4.0),
            child: Row(
              children: [
                _buildFilterChip('Todos', 'TODOS'),
                const SizedBox(width: 8),
                _buildFilterChip('🔥 Prioridad Alta', 'URGENTE', activeColor: AppTheme.strawberryRed),
                const SizedBox(width: 8),
                _buildFilterChip('👤 Mis Asignados', 'MIS_PEDIDOS', activeColor: AppTheme.darkGreen),
                const SizedBox(width: 8),
                _buildFilterChip('👤 Sin Asignar', 'SIN_ASIGNAR'),
              ],
            ),
          ),
          const Divider(height: 12),

          Expanded(
            child: ordersAsync.when(
              data: (orders) {
                // Apply search and operational filters
                var filtered = orders;

                // 1. Operational filter
                if (_operationalFilter == 'URGENTE') {
                  filtered = filtered.where((o) => o.isUrgent).toList();
                } else if (_operationalFilter == 'MIS_PEDIDOS') {
                  filtered = filtered.where((o) => currentUsername != null && o.assignedAdvisor?.equalsIgnoreCase(currentUsername) == true).toList();
                } else if (_operationalFilter == 'SIN_ASIGNAR') {
                  filtered = filtered.where((o) => !o.isAssigned).toList();
                }

                // 2. Search query filter
                if (_searchQuery.isNotEmpty) {
                  filtered = filtered.where((o) {
                    return o.orderCode.toLowerCase().contains(_searchQuery) ||
                        o.customerName.toLowerCase().contains(_searchQuery) ||
                        o.customerPhone.toLowerCase().contains(_searchQuery);
                  }).toList();
                }

                return TabBarView(
                  controller: _tabController,
                  children: [
                    _buildOrdersListView(filtered, currentUsername), // Todos
                    _buildOrdersListView(filtered.where((o) => o.status == OrderStatus.received).toList(), currentUsername), // Nuevos
                    _buildOrdersListView(filtered.where((o) => o.status == OrderStatus.preparing).toList(), currentUsername), // Preparando
                    _buildOrdersListView(filtered.where((o) => o.status == OrderStatus.almostReady).toList(), currentUsername), // Casi Listos
                    _buildOrdersListView(filtered.where((o) => o.status == OrderStatus.ready).toList(), currentUsername), // Listos
                  ],
                );
              },
              loading: () => const Center(child: CircularProgressIndicator(color: AppTheme.darkGreen)),
              error: (_, __) => const Center(child: Text('Error al cargar la cola de pedidos.')),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildFilterChip(String label, String value, {Color? activeColor}) {
    final isSelected = _operationalFilter == value;
    final color = activeColor ?? AppTheme.darkGreen;

    return FilterChip(
      label: Text(
        label,
        style: TextStyle(
          fontSize: 12,
          fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
          color: isSelected ? Colors.white : AppTheme.darkBg,
        ),
      ),
      selected: isSelected,
      selectedColor: color,
      backgroundColor: Colors.grey.shade100,
      showCheckmark: false,
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
      onSelected: (selected) {
        setState(() {
          _operationalFilter = value;
        });
      },
    );
  }

  Widget _buildOrdersListView(List<Order> ordersList, String? currentUsername) {
    if (ordersList.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(28.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Text('🥤', style: TextStyle(fontSize: 48)),
              const SizedBox(height: 12),
              const Text(
                'No hay pedidos con estos filtros.',
                style: TextStyle(fontWeight: FontWeight.bold, color: AppTheme.textGray),
              ),
            ],
          ),
        ),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.all(16.0),
      itemCount: ordersList.length,
      itemBuilder: (context, index) {
        final order = ordersList[index];
        return _buildOrderCard(order, currentUsername);
      },
    );
  }

  Widget _buildOrderCard(Order order, String? currentUsername) {
    final itemsStr = order.items.map((i) => '${i.quantity}x ${i.productName} (${i.flavorName})').join(', ');
    final statusColor = _getStatusColor(order.status);

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(18),
        side: order.isUrgent
            ? const BorderSide(color: AppTheme.strawberryRed, width: 2)
            : BorderSide.none,
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(18),
        onTap: () {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (context) => OrderDetailScreen(orderId: order.id),
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
                          color: statusColor.withOpacity(0.15),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Text(
                          order.status.nameInSpanish.toUpperCase(),
                          style: TextStyle(
                            fontSize: 10,
                            fontWeight: FontWeight.w900,
                            color: statusColor == Colors.white ? AppTheme.darkBg : statusColor,
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
                          fontSize: 16,
                          fontWeight: FontWeight.w900,
                          color: AppTheme.darkGreen,
                        ),
                      ),
                      const SizedBox(width: 8),
                      AdvisorBadge(assignedAdvisor: order.assignedAdvisor, currentUsername: currentUsername, isCompact: true),
                    ],
                  ),
                  const Row(
                    children: [
                      Text(
                        'Ver Detalle',
                        style: TextStyle(
                          fontSize: 12,
                          color: AppTheme.darkGreen,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      Icon(Icons.chevron_right_rounded, color: AppTheme.darkGreen, size: 18),
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

  Color _getStatusColor(OrderStatus status) {
    switch (status) {
      case OrderStatus.received:
        return AppTheme.primaryLemon;
      case OrderStatus.accepted:
        return Colors.blue;
      case OrderStatus.preparing:
        return Colors.orange;
      case OrderStatus.almostReady:
        return Colors.lightGreen;
      case OrderStatus.ready:
        return AppTheme.darkGreen;
      default:
        return AppTheme.textGray;
    }
  }
}
