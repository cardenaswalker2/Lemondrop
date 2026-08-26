import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/models/models.dart';
import '../../../../core/network/api_client.dart';
import '../../../../core/theme/app_theme.dart';
import '../../../../core/utils/app_formatters.dart';
import '../../auth/providers/auth_provider.dart';
import 'widgets/interactive_order_card.dart';
import 'widgets/order_status_badge.dart';
import 'widgets/order_tracking_timeline.dart';

class OrderTrackingDetailScreen extends ConsumerStatefulWidget {
  final Order initialOrder;

  const OrderTrackingDetailScreen({
    super.key,
    required this.initialOrder,
  });

  @override
  ConsumerState<OrderTrackingDetailScreen> createState() => _OrderTrackingDetailScreenState();
}

class _OrderTrackingDetailScreenState extends ConsumerState<OrderTrackingDetailScreen> {
  late Order _currentOrder;
  bool _isRefreshing = false;

  @override
  void initState() {
    super.initState();
    _currentOrder = widget.initialOrder;
  }

  Future<void> _refreshOrderStatus() async {
    if (_isRefreshing) return;
    setState(() => _isRefreshing = true);

    try {
      final client = ref.read(apiClientProvider);
      final res = await client.dio.get('/api/public/pedidos/track/${_currentOrder.orderCode}');
      if (res.statusCode == 200 && res.data is Map<String, dynamic>) {
        final data = res.data as Map<String, dynamic>;
        if (mounted) {
          setState(() {
            _currentOrder = Order.fromJson(data);
          });
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(
              content: Text('Estado del pedido actualizado ✓'),
              duration: Duration(seconds: 1),
              backgroundColor: AppTheme.darkGreen,
            ),
          );
        }
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('No se pudo actualizar el estado.'),
            duration: Duration(seconds: 2),
            backgroundColor: AppTheme.strawberryRed,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isRefreshing = false);
      }
    }
  }

  void _copyOrderCode() {
    Clipboard.setData(ClipboardData(text: _currentOrder.orderCode));
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('Código ${_currentOrder.orderCode} copiado ✓'),
        duration: const Duration(seconds: 2),
        backgroundColor: AppTheme.darkGreen,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.creamBg,
      appBar: AppBar(
        backgroundColor: AppTheme.creamBg,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new_rounded, color: AppTheme.darkBg),
          onPressed: () => Navigator.pop(context),
          tooltip: 'Volver',
        ),
        title: const Text(
          'Detalle del pedido',
          style: TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.w900,
            color: AppTheme.darkBg,
          ),
        ),
        centerTitle: true,
        actions: [
          IconButton(
            icon: _isRefreshing
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(
                      strokeWidth: 2.5,
                      color: AppTheme.darkGreen,
                    ),
                  )
                : const Icon(Icons.refresh_rounded, color: AppTheme.darkGreen),
            onPressed: _refreshOrderStatus,
            tooltip: 'Actualizar estado',
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _refreshOrderStatus,
        color: AppTheme.darkGreen,
        child: SingleChildScrollView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 32),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // 1. Order Code & Customer Header
              _buildHeaderCard(),
              const SizedBox(height: 16),

              // 2. Current Status Banner & Friendly Message
              _buildStatusMessageCard(),
              const SizedBox(height: 16),

              // 3. Tracking Timeline
              OrderTrackingTimeline(
                status: _currentOrder.status,
                cancellationReason: _currentOrder.cancellationReason,
              ),
              const SizedBox(height: 20),

              // 4. Products List (Detalle de tu pedido)
              _buildOrderItemsCard(),
              const SizedBox(height: 16),

              // 5. Total Card
              _buildTotalCard(),
              const SizedBox(height: 24),

              // 6. WhatsApp Contact Button
              _buildWhatsAppButton(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildHeaderCard() {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(22),
        border: Border.all(color: AppTheme.darkGreen.withOpacity(0.12)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.04),
            blurRadius: 10,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                      decoration: BoxDecoration(
                        color: AppTheme.primaryLemon.withOpacity(0.3),
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: const Text(
                        'CÓDIGO DE ORDEN',
                        style: TextStyle(
                          fontSize: 10,
                          fontWeight: FontWeight.w900,
                          color: AppTheme.darkGreen,
                          letterSpacing: 0.5,
                        ),
                      ),
                    ),
                    const SizedBox(height: 4),
                    GestureDetector(
                      onTap: _copyOrderCode,
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            _currentOrder.orderCode,
                            style: const TextStyle(
                              fontSize: 20,
                              fontWeight: FontWeight.w900,
                              fontFamily: 'monospace',
                              color: AppTheme.darkBg,
                              letterSpacing: 0.8,
                            ),
                          ),
                          const SizedBox(width: 6),
                          const Icon(Icons.copy_rounded, size: 16, color: AppTheme.textGray),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              if (_currentOrder.customerName.isNotEmpty)
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    const Text(
                      'Cliente:',
                      style: TextStyle(fontSize: 11, color: AppTheme.textGray, fontWeight: FontWeight.w600),
                    ),
                    Text(
                      _currentOrder.customerName,
                      style: const TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.bold,
                        color: AppTheme.darkBg,
                      ),
                    ),
                  ],
                ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildStatusMessageCard() {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: _currentOrder.status == OrderStatus.cancelled
            ? const Color(0xFFFFEBEE)
            : AppTheme.softGreen.withOpacity(0.6),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(
          color: _currentOrder.status == OrderStatus.cancelled
              ? AppTheme.strawberryRed
              : AppTheme.darkGreen.withOpacity(0.2),
          width: 1.2,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              OrderStatusBadge(status: _currentOrder.status),
            ],
          ),
          const SizedBox(height: 12),
          Text(
            _currentOrder.status.trackingMessage,
            style: TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w600,
              height: 1.4,
              color: _currentOrder.status == OrderStatus.cancelled
                  ? const Color(0xFFB71C1C)
                  : AppTheme.darkBg,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildOrderItemsCard() {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(22),
        border: Border.all(color: AppTheme.darkGreen.withOpacity(0.12)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.04),
            blurRadius: 10,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Row(
            children: [
              Text('🍧', style: TextStyle(fontSize: 18)),
              SizedBox(width: 8),
              Text(
                'Detalle de tu pedido',
                style: TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w900,
                  color: AppTheme.darkBg,
                ),
              ),
            ],
          ),
          const SizedBox(height: 14),
          const Divider(height: 1),
          const SizedBox(height: 10),

          ...List.generate(_currentOrder.items.length, (index) {
            final item = _currentOrder.items[index];
            final isLast = index == _currentOrder.items.length - 1;

            return Padding(
              padding: EdgeInsets.only(bottom: isLast ? 0 : 14.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              '${item.productName} (${item.flavorName})',
                              style: const TextStyle(
                                fontSize: 14,
                                fontWeight: FontWeight.bold,
                                color: AppTheme.darkBg,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Wrap(
                              spacing: 8,
                              runSpacing: 4,
                              children: [
                                Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                  decoration: BoxDecoration(
                                    color: AppTheme.softGreen,
                                    borderRadius: BorderRadius.circular(6),
                                  ),
                                  child: Text(
                                    'Tamaño: ${item.size.displayName}',
                                    style: const TextStyle(
                                      fontSize: 11,
                                      fontWeight: FontWeight.bold,
                                      color: AppTheme.darkGreen,
                                    ),
                                  ),
                                ),
                                Container(
                                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                  decoration: BoxDecoration(
                                    color: const Color(0xFFF1F5F9),
                                    borderRadius: BorderRadius.circular(6),
                                  ),
                                  child: Text(
                                    'Cantidad: ${item.quantity}',
                                    style: const TextStyle(
                                      fontSize: 11,
                                      fontWeight: FontWeight.bold,
                                      color: AppTheme.darkBg,
                                    ),
                                  ),
                                ),
                              ],
                            ),
                            if (item.addons.isNotEmpty) ...[
                              const SizedBox(height: 6),
                              Row(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  const Text('Toppings: ',
                                      style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: AppTheme.textGray)),
                                  Expanded(
                                    child: Text(
                                      item.addons.map((a) => a.addonName).join(', '),
                                      style: const TextStyle(
                                        fontSize: 11,
                                        fontWeight: FontWeight.w600,
                                        color: AppTheme.darkGreen,
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ],
                            if (item.observations.isNotEmpty) ...[
                              const SizedBox(height: 4),
                              Text(
                                'Nota: ${item.observations}',
                                style: const TextStyle(
                                  fontSize: 11,
                                  fontStyle: FontStyle.italic,
                                  color: AppTheme.textGray,
                                ),
                              ),
                            ],
                          ],
                        ),
                      ),
                      const SizedBox(width: 8),
                      Text(
                        AppFormatters.formatCurrency(item.subtotal),
                        style: const TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w900,
                          color: AppTheme.darkGreen,
                        ),
                      ),
                    ],
                  ),
                  if (!isLast) ...[
                    const SizedBox(height: 12),
                    const Divider(height: 1, color: Color(0xFFF1F5F9)),
                  ],
                ],
              ),
            );
          }),
        ],
      ),
    );
  }

  Widget _buildTotalCard() {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: AppTheme.primaryLemon.withOpacity(0.18),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: AppTheme.primaryLemon, width: 1.5),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          const Text(
            'Total del pedido',
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.bold,
              color: AppTheme.darkBg,
            ),
          ),
          Text(
            AppFormatters.formatCurrency(_currentOrder.total),
            style: const TextStyle(
              fontSize: 22,
              fontWeight: FontWeight.w900,
              color: AppTheme.darkGreen,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildWhatsAppButton() {
    return ElevatedButton(
      style: ElevatedButton.styleFrom(
        backgroundColor: AppTheme.darkGreen,
        foregroundColor: Colors.white,
        padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 16),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
        elevation: 2,
      ),
      onPressed: () {
        AppFormatters.openOrderWhatsAppSupport(_currentOrder.orderCode);
      },
      child: const Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Text('💬', style: TextStyle(fontSize: 18)),
          SizedBox(width: 8),
          Flexible(
            child: Text(
              '¿Tienes dudas sobre tu pedido? Escríbenos por WhatsApp',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w900,
                color: Colors.white,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
