import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/models/models.dart';
import '../../auth/providers/auth_provider.dart';

class OrderHistoryScreen extends ConsumerStatefulWidget {
  const OrderHistoryScreen({super.key});

  @override
  ConsumerState<OrderHistoryScreen> createState() => _OrderHistoryScreenState();
}

class _OrderHistoryScreenState extends ConsumerState<OrderHistoryScreen> {
  int _currentPage = 0;
  bool _isLoading = false;
  List<Order> _historyOrders = [];
  bool _isLastPage = false;

  @override
  void initState() {
    super.initState();
    _fetchHistory();
  }

  Future<void> _fetchHistory() async {
    if (_isLoading) return;
    setState(() => _isLoading = true);

    try {
      final client = ref.read(apiClientProvider);
      final res = await client.dio.get('/api/mobile/orders/history', queryParameters: {
        'page': _currentPage,
        'size': 20,
      });

      if (res.statusCode == 200) {
        final List raw = res.data['content'] as List? ?? [];
        final fetched = raw.map((e) => Order.fromJson(e)).toList();
        final totalPages = res.data['totalPages'] as int? ?? 1;

        setState(() {
          _historyOrders = fetched;
          _isLastPage = _currentPage >= totalPages - 1;
        });
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Error al cargar historial.')),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isLoading = false);
      }
    }
  }

  void _nextPage() {
    if (!_isLastPage) {
      setState(() => _currentPage++);
      _fetchHistory();
    }
  }

  void _prevPage() {
    if (_currentPage > 0) {
      setState(() => _currentPage--);
      _fetchHistory();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Historial de Pedidos'),
      ),
      body: Column(
        children: [
          Expanded(
            child: _isLoading
                ? const Center(child: CircularProgressIndicator(color: AppTheme.darkGreen))
                : _historyOrders.isEmpty
                    ? const Center(
                        child: Text(
                          'No hay pedidos en el historial.',
                          style: TextStyle(color: AppTheme.textGray),
                        ),
                      )
                    : ListView.builder(
                        padding: const EdgeInsets.all(16.0),
                        itemCount: _historyOrders.length,
                        itemBuilder: (context, index) {
                          final order = _historyOrders[index];
                          return _buildHistoryCard(order);
                        },
                      ),
          ),
          // Pagination controls footer
          if (_historyOrders.isNotEmpty)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
              decoration: BoxDecoration(
                color: Colors.white,
                border: Border(top: BorderSide(color: Colors.grey.shade200)),
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppTheme.softGreen,
                      foregroundColor: AppTheme.darkGreen,
                      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                    ),
                    onPressed: _currentPage > 0 ? _prevPage : null,
                    child: const Text('Anterior'),
                  ),
                  Text(
                    'Página ${_currentPage + 1}',
                    style: const TextStyle(fontWeight: FontWeight.bold),
                  ),
                  ElevatedButton(
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppTheme.softGreen,
                      foregroundColor: AppTheme.darkGreen,
                      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                    ),
                    onPressed: !_isLastPage ? _nextPage : null,
                    child: const Text('Siguiente'),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildHistoryCard(Order order) {
    final itemsStr = order.items.map((i) => '${i.quantity}x ${i.productName}').join(', ');
    final isDelivered = order.status == OrderStatus.delivered;

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
                Text(
                  order.orderCode,
                  style: const TextStyle(
                    fontWeight: FontWeight.bold,
                    color: AppTheme.textGray,
                    fontSize: 12,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Text(
              order.customerName,
              style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 4),
            Text(
              itemsStr,
              style: const TextStyle(fontSize: 13, color: AppTheme.textGray),
            ),
            if (!isDelivered && order.cancellationReason != null) ...[
              const SizedBox(height: 8),
              Text(
                'Motivo: ${order.cancellationReason}',
                style: const TextStyle(fontSize: 12, color: AppTheme.strawberryRed, fontStyle: FontStyle.italic),
              ),
            ],
            const Divider(height: 20),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  '\$${order.total.toStringAsFixed(0)}',
                  style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 15, color: AppTheme.darkGreen),
                ),
                Text(
                  'Entregado: ${order.updatedAt.hour.toString().padLeft(2, '0')}:${order.updatedAt.minute.toString().padLeft(2, '0')}',
                  style: const TextStyle(fontSize: 12, color: AppTheme.textGray),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
