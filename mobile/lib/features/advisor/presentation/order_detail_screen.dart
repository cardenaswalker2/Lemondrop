import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/models/models.dart';
import '../providers/orders_provider.dart';
import 'edit_order_screen.dart';

class OrderDetailScreen extends ConsumerStatefulWidget {
  final String orderId;
  const OrderDetailScreen({super.key, required this.orderId});

  @override
  ConsumerState<OrderDetailScreen> createState() => _OrderDetailScreenState();
}

class _OrderDetailScreenState extends ConsumerState<OrderDetailScreen> {
  // Visual checklist helpers for preparation
  final Map<String, bool> _prepChecklist = {
    'Sabor seleccionado': false,
    'Tamaño correcto': false,
    'Hielo molido / granizado': false,
    'Complementos agregados': false,
    'Presentación y pitillo': false,
  };

  void _triggerWhatsApp(Order order) async {
    final message = 'Hola ${order.customerName} 👋🍋\n'
        'Tu pedido de *LEMON DROP* ya está listo para recoger.\n'
        'Pedido: *${order.orderCode}*\n'
        '¡Gracias por elegir LEMON DROP! 💛❄️';

    final cleanPhone = order.customerPhone.replaceAll(RegExp(r'\D'), '');
    // Ensure international code prefix for Colombia is added if 10-digit number
    final formattedPhone = cleanPhone.length == 10 ? '57$cleanPhone' : cleanPhone;

    final url = Uri.parse('https://wa.me/$formattedPhone?text=${Uri.encodeComponent(message)}');
    if (await launchUrl(url, mode: LaunchMode.externalApplication)) {
      // success
    } else {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('No se pudo abrir WhatsApp.')),
        );
      }
    }
  }

  void _showRejectModal(BuildContext context, Order order) {
    String selectedReason = 'Producto agotado';
    final customReasonController = TextEditingController();

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setStateDialog) => AlertDialog(
          title: const Text('Rechazar Pedido'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Text('¿Por qué se rechaza este pedido?'),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                value: selectedReason,
                decoration: const InputDecoration(contentPadding: EdgeInsets.all(12)),
                items: ['Producto agotado', 'Ingrediente no disponible', 'Problema operativo', 'Otro']
                    .map((r) => DropdownMenuItem(value: r, child: Text(r)))
                    .toList(),
                onChanged: (val) {
                  setStateDialog(() => selectedReason = val!);
                },
              ),
              if (selectedReason == 'Otro') ...[
                const SizedBox(height: 12),
                TextField(
                  controller: customReasonController,
                  decoration: const InputDecoration(
                    labelText: 'Escribe el motivo...',
                    contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  ),
                ),
              ]
            ],
          ),
          actions: [
            TextButton(
              child: const Text('Cancelar', style: TextStyle(color: AppTheme.textGray)),
              onPressed: () => Navigator.pop(ctx),
            ),
            TextButton(
              child: const Text('Rechazar', style: TextStyle(color: AppTheme.strawberryRed)),
              onPressed: () async {
                final reason = selectedReason == 'Otro'
                    ? customReasonController.text.trim()
                    : selectedReason;

                if (reason.isEmpty) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Debes especificar un motivo.')),
                  );
                  return;
                }

                Navigator.pop(ctx);
                final success = await ref.read(activeOrdersProvider.notifier).updateStatus(
                      order.id,
                      OrderStatus.cancelled,
                      notes: reason,
                    );
                if (success && mounted) {
                  Navigator.pop(context);
                }
              },
            ),
          ],
        ),
      ),
    );
  }

  void _showStatusConfirm(BuildContext context, OrderStatus targetStatus, String label, Order order) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('¿Cambiar estado a: $label?'),
        content: Text('Se actualizará el estado del pedido ${order.orderCode} en la base de datos.'),
        actions: [
          TextButton(
            child: const Text('Cancelar', style: TextStyle(color: AppTheme.textGray)),
            onPressed: () => Navigator.pop(ctx),
          ),
          TextButton(
            child: Text('Confirmar', style: const TextStyle(color: AppTheme.darkGreen)),
            onPressed: () async {
              Navigator.pop(ctx);
              final success = await ref.read(activeOrdersProvider.notifier).updateStatus(order.id, targetStatus);
              if (success && targetStatus == OrderStatus.delivered && mounted) {
                Navigator.pop(context);
              }
            },
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final ordersAsync = ref.watch(activeOrdersProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Ficha de Producción'),
      ),
      body: ordersAsync.when(
        data: (orders) {
          final order = orders.cast<Order?>().firstWhere((o) => o?.id == widget.orderId, orElse: () => null);
          if (order == null) {
            return const Center(child: Text('El pedido ya no está en la cola activa.'));
          }

          return Column(
            children: [
              Expanded(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.all(20.0),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      // Header card with ID and status
                      Card(
                        color: AppTheme.softGreen,
                        child: Padding(
                          padding: const EdgeInsets.all(16.0),
                          child: Column(
                            children: [
                              Text(
                                order.orderCode,
                                style: const TextStyle(
                                  fontSize: 24,
                                  fontWeight: FontWeight.w900,
                                  color: AppTheme.darkGreen,
                                ),
                              ),
                              const SizedBox(height: 6),
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                                decoration: BoxDecoration(
                                  color: Colors.white,
                                  borderRadius: BorderRadius.circular(12),
                                ),
                                child: Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    Container(
                                      width: 8,
                                      height: 8,
                                      decoration: BoxDecoration(
                                        color: _getStatusColor(order.status),
                                        shape: BoxShape.circle,
                                      ),
                                    ),
                                    const SizedBox(width: 8),
                                    Text(
                                      order.status.nameInSpanish.toUpperCase(),
                                      style: const TextStyle(
                                        fontWeight: FontWeight.w900,
                                        fontSize: 12,
                                        color: AppTheme.darkBg,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                      const SizedBox(height: 20),

                      // Customer information
                      const Text('Cliente', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
                      const SizedBox(height: 8),
                      Card(
                        child: ListTile(
                          leading: const Icon(Icons.person, color: AppTheme.darkGreen),
                          title: Text(order.customerName, style: const TextStyle(fontWeight: FontWeight.bold)),
                          subtitle: Text('Celular: ${order.customerPhone}'),
                        ),
                      ),
                      const SizedBox(height: 20),

                      // Items list
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Text('Productos', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
                          if (order.status == OrderStatus.received || order.status == OrderStatus.accepted)
                            TextButton.icon(
                              icon: const Icon(Icons.edit, size: 16, color: AppTheme.darkGreen),
                              label: const Text('Editar', style: TextStyle(color: AppTheme.darkGreen)),
                              onPressed: () {
                                Navigator.push(
                                  context,
                                  MaterialPageRoute(
                                    builder: (context) => EditOrderScreen(order: order),
                                  ),
                                );
                              },
                            ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      ListView.builder(
                        shrinkWrap: true,
                        physics: const NeverScrollableScrollPhysics(),
                        itemCount: order.items.length,
                        itemBuilder: (context, idx) {
                          final item = order.items[idx];
                          final addonsStr = item.addons.map((a) => '• ${a.addonName}').join('\n');

                          return Card(
                            child: Padding(
                              padding: const EdgeInsets.all(16.0),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.stretch,
                                children: [
                                  Row(
                                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                    children: [
                                      Text(
                                        '${item.quantity}x ${item.productName}',
                                        style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                                      ),
                                      Text(
                                        '\$${item.subtotal.toStringAsFixed(0)}',
                                        style: const TextStyle(fontWeight: FontWeight.bold),
                                      ),
                                    ],
                                  ),
                                  const SizedBox(height: 4),
                                  Text(
                                    'Sabor: ${item.flavorName} | Tamaño: ${item.size.displayName}',
                                    style: const TextStyle(fontSize: 13, color: AppTheme.textGray),
                                  ),
                                  if (item.addons.isNotEmpty) ...[
                                    const SizedBox(height: 8),
                                    const Text('Complementos:', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                                    const SizedBox(height: 2),
                                    Text(addonsStr, style: const TextStyle(fontSize: 12, color: AppTheme.darkGreen)),
                                  ],
                                  if (item.observations.isNotEmpty) ...[
                                    const SizedBox(height: 8),
                                    const Text('Observaciones:', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                                    const SizedBox(height: 2),
                                    Text('"${item.observations}"', style: const TextStyle(fontSize: 12, fontStyle: FontStyle.italic, color: AppTheme.strawberryRed)),
                                  ],
                                ],
                              ),
                            ),
                          );
                        },
                      ),
                      const SizedBox(height: 20),

                      // Order total display
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Text('Total:', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w900)),
                          Text(
                            '\$${order.total.toStringAsFixed(0)}',
                            style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w900, color: AppTheme.darkGreen),
                          ),
                        ],
                      ),
                      const SizedBox(height: 24),

                      // Preparing visual checklist
                      if (order.status == OrderStatus.preparing) ...[
                        const Text(
                          'Guía de Preparación',
                          style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppTheme.darkGreen),
                        ),
                        const SizedBox(height: 10),
                        Card(
                          child: Padding(
                            padding: const EdgeInsets.all(8.0),
                            child: Column(
                              children: _prepChecklist.keys.map((key) {
                                return CheckboxListTile(
                                  activeColor: AppTheme.darkGreen,
                                  title: Text(key, style: const TextStyle(fontSize: 14)),
                                  value: _prepChecklist[key],
                                  onChanged: (val) {
                                    setState(() {
                                      _prepChecklist[key] = val!;
                                    });
                                  },
                                );
                              }).toList(),
                            ),
                          ),
                        ),
                        const SizedBox(height: 20),
                      ]
                    ],
                  ),
                ),
              ),

              // Action buttons footer
              Container(
                padding: const EdgeInsets.all(20.0),
                decoration: BoxDecoration(
                  color: Colors.white,
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withOpacity(0.05),
                      blurRadius: 10,
                      offset: const Offset(0, -4),
                    ),
                  ],
                ),
                child: _buildActionButtons(context, order),
              ),
            ],
          );
        },
        loading: () => const Center(child: CircularProgressIndicator(color: AppTheme.darkGreen)),
        error: (_, __) => const Center(child: Text('Error al cargar detalle.')),
      ),
    );
  }

  Color _getStatusColor(OrderStatus status) {
    switch (status) {
      case OrderStatus.received:
        return AppTheme.primaryLemon;
      case OrderStatus.accepted:
        return AppTheme.mintGreen;
      case OrderStatus.preparing:
        return Colors.orange;
      case OrderStatus.almostReady:
        return AppTheme.softGreen;
      case OrderStatus.ready:
        return AppTheme.darkGreen;
      case OrderStatus.delivered:
        return AppTheme.textGray;
      case OrderStatus.cancelled:
        return AppTheme.strawberryRed;
    }
  }

  Widget _buildActionButtons(BuildContext context, Order order) {
    switch (order.status) {
      case OrderStatus.received:
        return Row(
          children: [
            Expanded(
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppTheme.strawberryRed,
                  foregroundColor: Colors.white,
                ),
                onPressed: () => _showRejectModal(context, order),
                child: const Text('RECHAZAR'),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppTheme.primaryLemon,
                  foregroundColor: AppTheme.darkBg,
                ),
                onPressed: () => _showStatusConfirm(context, OrderStatus.accepted, 'Aceptar Pedido', order),
                child: const Text('ACEPTAR'),
              ),
            ),
          ],
        );
      case OrderStatus.accepted:
        return ElevatedButton(
          style: ElevatedButton.styleFrom(
            backgroundColor: AppTheme.primaryLemon,
            foregroundColor: AppTheme.darkBg,
          ),
          onPressed: () => _showStatusConfirm(context, OrderStatus.preparing, 'Iniciar Preparación', order),
          child: const Text('INICIAR PREPARACIÓN'),
        );
      case OrderStatus.preparing:
        return ElevatedButton(
          style: ElevatedButton.styleFrom(
            backgroundColor: AppTheme.mintGreen,
            foregroundColor: AppTheme.darkGreen,
          ),
          onPressed: () => _showStatusConfirm(context, OrderStatus.almostReady, 'Marcar Casi Listo', order),
          child: const Text('MARCAR CASI LISTO'),
        );
      case OrderStatus.almostReady:
        return ElevatedButton(
          style: ElevatedButton.styleFrom(
            backgroundColor: AppTheme.darkGreen,
            foregroundColor: Colors.white,
          ),
          onPressed: () => _showStatusConfirm(context, OrderStatus.ready, 'Marcar Listo', order),
          child: const Text('MARCAR COMO LISTO'),
        );
      case OrderStatus.ready:
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            ElevatedButton(
              style: ElevatedButton.styleFrom(
                backgroundColor: AppTheme.darkGreen,
                foregroundColor: Colors.white,
              ),
              onPressed: () => _triggerWhatsApp(order),
              child: const Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.message_outlined, size: 20),
                  SizedBox(width: 8),
                  Text('AVISAR POR WHATSAPP'),
                ],
              ),
            ),
            const SizedBox(height: 8),
            OutlinedButton(
              style: OutlinedButton.styleFrom(
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                padding: const EdgeInsets.symmetric(vertical: 14),
                side: const BorderSide(color: AppTheme.darkBg, width: 2),
              ),
              onPressed: () => _showStatusConfirm(context, OrderStatus.delivered, 'Confirmar Entrega', order),
              child: const Text('MARCAR ENTREGADO', style: TextStyle(color: AppTheme.darkBg, fontWeight: FontWeight.bold)),
            ),
          ],
        );
      default:
        return const SizedBox.shrink();
    }
  }
}
