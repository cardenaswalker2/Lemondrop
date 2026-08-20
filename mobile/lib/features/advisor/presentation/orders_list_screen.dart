import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/models/models.dart';
import '../providers/orders_provider.dart';
import 'order_detail_screen.dart';

class OrdersListScreen extends ConsumerStatefulWidget {
  const OrdersListScreen({super.key});

  @override
  ConsumerState<OrdersListScreen> createState() => _OrdersListScreenState();
}

class _OrdersListScreenState extends ConsumerState<OrdersListScreen> with SingleTickerProviderStateMixin {
  late TabController _tabController;
  final _searchController = TextEditingController();
  String _searchQuery = '';

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

    return Scaffold(
      appBar: AppBar(
        title: const Text('Cola de Pedidos'),
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
            padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 10.0),
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
          Expanded(
            child: ordersAsync.when(
              data: (orders) {
                // Filter orders by search query
                var filtered = orders;
                if (_searchQuery.isNotEmpty) {
                  filtered = orders.where((o) {
                    return o.orderCode.toLowerCase().contains(_searchQuery) ||
                        o.customerName.toLowerCase().contains(_searchQuery) ||
                        o.customerPhone.toLowerCase().contains(_searchQuery);
                  }).toList();
                }

                return TabBarView(
                  controller: _tabController,
                  children: [
                    _buildOrdersListView(filtered), // Todos
                    _buildOrdersListView(filtered.where((o) => o.status == OrderStatus.received).toList()), // Nuevos
                    _buildOrdersListView(filtered.where((o) => o.status == OrderStatus.preparing).toList()), // Preparando
                    _buildOrdersListView(filtered.where((o) => o.status == OrderStatus.almostReady).toList()), // Casi Listos
                    _buildOrdersListView(filtered.where((o) => o.status == OrderStatus.ready).toList()), // Listos
                  ],
                );
              },
              loading: () => const Center(child: CircularProgressIndicator(color: AppTheme.darkGreen)),
              error: (_, __) => const Center(child: Text('Error al cargar pedidos.')),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildOrdersListView(List<Order> ordersList) {
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
                'No hay pedidos aquí.',
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
        return _buildOrderCard(order);
      },
    );
  }

  Widget _buildOrderCard(Order order) {
    final itemsStr = order.items.map((i) => '${i.quantity}x ${i.productName}').join(', ');
    final statusColor = _getStatusColor(order.status);

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: InkWell(
        borderRadius: BorderRadius.circular(20),
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
                style: const TextStyle(
                  fontSize: 13,
                  color: AppTheme.textGray,
                ),
              ),
              const SizedBox(height: 12),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    '\$${order.total.toStringAsFixed(0)}',
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w900,
                      color: AppTheme.darkGreen,
                    ),
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
