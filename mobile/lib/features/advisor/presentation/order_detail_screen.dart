import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/models/models.dart';
import '../../auth/providers/auth_provider.dart';
import '../providers/orders_provider.dart';
import '../providers/checklist_service.dart';
import 'edit_order_screen.dart';
import 'widgets/advisor_badges.dart';
import 'widgets/order_timer_badge.dart';

class OrderDetailScreen extends ConsumerStatefulWidget {
  final String orderId;
  final Order? preloadedOrder;

  const OrderDetailScreen({
    super.key,
    required this.orderId,
    this.preloadedOrder,
  });

  @override
  ConsumerState<OrderDetailScreen> createState() => _OrderDetailScreenState();
}

class _OrderDetailScreenState extends ConsumerState<OrderDetailScreen> {
  Map<String, bool> _prepChecklist = {};
  bool _checklistLoaded = false;
  bool _isClaiming = false;

  @override
  void initState() {
    super.initState();
    _loadChecklist();
  }

  Future<void> _loadChecklist() async {
    final list = await ChecklistService.getChecklist(widget.orderId);
    if (mounted) {
      setState(() {
        _prepChecklist = list;
        _checklistLoaded = true;
      });
    }
  }

  void _onChecklistChanged(String key, bool val) {
    setState(() {
      _prepChecklist[key] = val;
    });
    ChecklistService.saveChecklist(widget.orderId, _prepChecklist);
  }

  void _triggerWhatsApp(Order order) async {
    final message = 'Hola ${order.customerName} 👋🍋\n'
        'Tu pedido de *LEMON DROP* ya está listo para recoger.\n'
        'Pedido: *${order.orderCode}*\n'
        '¡Gracias por elegir LEMON DROP! 💛❄️';

    final cleanPhone = order.customerPhone.replaceAll(RegExp(r'\D'), '');
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

  void _callCustomer(String phone) async {
    final cleanPhone = phone.replaceAll(RegExp(r'\D'), '');
    final url = Uri.parse('tel:$cleanPhone');
    if (await canLaunchUrl(url)) {
      await launchUrl(url);
    }
  }

  void _claimOrder(Order order) async {
    setState(() => _isClaiming = true);
    final error = await ref.read(activeOrdersProvider.notifier).claimOrder(order.id);
    if (mounted) {
      setState(() => _isClaiming = false);
      if (error == null) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('¡Has tomado la comanda ${order.orderCode}!'),
            backgroundColor: AppTheme.darkGreen,
            behavior: SnackBarBehavior.floating,
          ),
        );
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(error),
            backgroundColor: AppTheme.strawberryRed,
            behavior: SnackBarBehavior.floating,
          ),
        );
      }
    }
  }

  void _showCancelModal(BuildContext context, Order order) {
    String selectedReason = 'Producto agotado';
    final customReasonController = TextEditingController();

    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setStateDialog) => AlertDialog(
          title: const Text('Cancelar / Rechazar Pedido'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Text('Especifica el motivo de la cancelación:'),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                value: selectedReason,
                decoration: const InputDecoration(contentPadding: EdgeInsets.all(12)),
                items: ['Producto agotado', 'Ingrediente no disponible', 'Problema operativo', 'Cliente canceló en ventanilla', 'Otro']
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
                    labelText: 'Escribe el motivo detallado...',
                    contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  ),
                ),
              ]
            ],
          ),
          actions: [
            TextButton(
              child: const Text('Volver', style: TextStyle(color: AppTheme.textGray)),
              onPressed: () => Navigator.pop(ctx),
            ),
            TextButton(
              child: const Text('Confirmar Cancelación', style: TextStyle(color: AppTheme.strawberryRed, fontWeight: FontWeight.bold)),
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
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text('Pedido ${order.orderCode} cancelado.'),
                      backgroundColor: AppTheme.strawberryRed,
                    ),
                  );
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
        title: Text('¿Cambiar a: $label?'),
        content: Text('Se registrará la transición de la comanda ${order.orderCode}.'),
        actions: [
          TextButton(
            child: const Text('Cancelar', style: TextStyle(color: AppTheme.textGray)),
            onPressed: () => Navigator.pop(ctx),
          ),
          TextButton(
            child: const Text('Confirmar', style: TextStyle(color: AppTheme.darkGreen, fontWeight: FontWeight.bold)),
            onPressed: () async {
              Navigator.pop(ctx);
              final success = await ref.read(activeOrdersProvider.notifier).updateStatus(order.id, targetStatus);
              if (success && targetStatus == OrderStatus.delivered && mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text('¡Pedido ${order.orderCode} entregado con éxito! 🎉'),
                    backgroundColor: AppTheme.darkGreen,
                  ),
                );
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
    final historyState = ref.watch(historyProvider);
    final authState = ref.watch(authProvider);
    final currentUsername = authState.user?.username;

    // 1. Check in active orders
    Order? order = ordersAsync.asData?.value.cast<Order?>().firstWhere(
          (o) => o?.id == widget.orderId,
          orElse: () => null,
        );

    // 2. Fallback to history orders
    order ??= historyState.orders.cast<Order?>().firstWhere(
          (o) => o?.id == widget.orderId,
          orElse: () => null,
        );

    // 3. Fallback to preloaded order
    order ??= widget.preloadedOrder;

    final resolvedOrder = order;
    if (resolvedOrder == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('Ficha de Producción')),
        body: const Center(
          child: Padding(
            padding: EdgeInsets.all(24.0),
            child: Text('No se encontró el pedido o ya fue archivado.'),
          ),
        ),
      );
    }

    final isFinished = resolvedOrder.status == OrderStatus.delivered || resolvedOrder.status == OrderStatus.cancelled;
    final statusHistoryAsync = ref.watch(orderStatusHistoryProvider(resolvedOrder.id));
    final changeHistoryAsync = ref.watch(orderChangeHistoryProvider(resolvedOrder.id));
    final timeFormatter = DateFormat('hh:mm a');

    return Scaffold(
      appBar: AppBar(
        title: Text('Comanda ${resolvedOrder.orderCode}'),
        actions: [
          if (resolvedOrder.isUrgent)
            const Padding(
              padding: EdgeInsets.only(right: 16.0),
              child: Center(child: PriorityBadge(isUrgent: true)),
            ),
        ],
      ),
      body: Column(
        children: [
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(20.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  // Urgent Priority Banner
                  if (resolvedOrder.isUrgent) ...[
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: const Color(0xFFFFEBEE),
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(color: AppTheme.strawberryRed, width: 1.5),
                      ),
                      child: const Row(
                        children: [
                          Text('🔥', style: TextStyle(fontSize: 20)),
                          SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              'PEDIDO CON PRIORIDAD ALTA: Preparar de inmediato.',
                              style: TextStyle(color: AppTheme.strawberryRed, fontWeight: FontWeight.bold, fontSize: 12),
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 14),
                  ],

                  // Header card with ID, status and Live Timer
                  Card(
                    color: AppTheme.softGreen,
                    child: Padding(
                      padding: const EdgeInsets.all(16.0),
                      child: Column(
                        children: [
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Text(
                                resolvedOrder.orderCode,
                                style: const TextStyle(
                                  fontSize: 24,
                                  fontWeight: FontWeight.w900,
                                  color: AppTheme.darkGreen,
                                ),
                              ),
                              OrderTimerBadge(order: resolvedOrder),
                            ],
                          ),
                          const SizedBox(height: 10),
                          Row(
                            mainAxisAlignment: MainAxisAlignment.spaceBetween,
                            children: [
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                                decoration: BoxDecoration(
                                  color: Colors.white,
                                  borderRadius: BorderRadius.circular(10),
                                ),
                                child: Text(
                                  resolvedOrder.status.nameInSpanish.toUpperCase(),
                                  style: const TextStyle(
                                    fontWeight: FontWeight.w900,
                                    fontSize: 11,
                                    color: AppTheme.darkGreen,
                                  ),
                                ),
                              ),
                              AdvisorBadge(
                                assignedAdvisor: resolvedOrder.assignedAdvisor,
                                currentUsername: currentUsername,
                              ),
                            ],
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 14),

                  // Claim Order Action Card (if not assigned to current user and not finished)
                  if (!isFinished && (!resolvedOrder.isAssigned || (currentUsername != null && !resolvedOrder.assignedAdvisor!.equalsIgnoreCase(currentUsername)))) ...[
                    Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: const Color(0xFFF0FDF4),
                        borderRadius: BorderRadius.circular(14),
                        border: Border.all(color: AppTheme.darkGreen.withOpacity(0.3)),
                      ),
                      child: Row(
                        children: [
                          const Icon(Icons.touch_app_outlined, color: AppTheme.darkGreen),
                          const SizedBox(width: 10),
                          Expanded(
                            child: Text(
                              resolvedOrder.isAssigned
                                  ? 'Asignado a: ${resolvedOrder.assignedAdvisor}'
                                  : 'Comanda sin asignar a ningún asesor.',
                              style: const TextStyle(fontSize: 12, color: AppTheme.darkGreen, fontWeight: FontWeight.w600),
                            ),
                          ),
                          ElevatedButton(
                            style: ElevatedButton.styleFrom(
                              backgroundColor: AppTheme.darkGreen,
                              foregroundColor: Colors.white,
                              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                            ),
                            onPressed: _isClaiming ? null : () => _claimOrder(resolvedOrder),
                            child: _isClaiming
                                ? const SizedBox(width: 14, height: 14, child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2))
                                : const Text('Tomar Pedido', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold)),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 14),
                  ],

                  // Customer information
                  const Text('Cliente', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
                  const SizedBox(height: 6),
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 12.0),
                      child: Row(
                        children: [
                          const CircleAvatar(
                            backgroundColor: AppTheme.mintGreen,
                            child: Icon(Icons.person, color: AppTheme.darkGreen),
                          ),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  resolvedOrder.customerName,
                                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                                ),
                                const SizedBox(height: 2),
                                Text(
                                  'Cel: ${resolvedOrder.customerPhone.isNotEmpty ? resolvedOrder.customerPhone : "Sin teléfono"}',
                                  style: const TextStyle(fontSize: 13, color: AppTheme.textGray),
                                ),
                              ],
                            ),
                          ),
                          if (resolvedOrder.customerPhone.isNotEmpty) ...[
                            IconButton(
                              icon: const Icon(Icons.phone_outlined, color: AppTheme.darkGreen),
                              onPressed: () => _callCustomer(resolvedOrder.customerPhone),
                            ),
                            IconButton(
                              icon: const Icon(Icons.message_outlined, color: AppTheme.darkGreen),
                              onPressed: () => _triggerWhatsApp(resolvedOrder),
                            ),
                          ],
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 18),

                  // Products list
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'Productos (${resolvedOrder.items.length})',
                        style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                      ),
                      if (!isFinished && (resolvedOrder.status == OrderStatus.received || resolvedOrder.status == OrderStatus.accepted))
                        TextButton.icon(
                          icon: const Icon(Icons.edit_note_rounded, size: 18, color: AppTheme.darkGreen),
                          label: const Text('Modificar Pedido', style: TextStyle(color: AppTheme.darkGreen, fontWeight: FontWeight.bold)),
                          onPressed: () {
                            Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (context) => EditOrderScreen(order: resolvedOrder),
                              ),
                            );
                          },
                        ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  ListView.builder(
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    itemCount: resolvedOrder.items.length,
                    itemBuilder: (context, idx) {
                      final item = resolvedOrder.items[idx];
                      final addonsStr = item.addons.map((a) => '• ${a.addonName} (+\$${a.unitPrice.toStringAsFixed(0)})').join('\n');

                      return Card(
                        margin: const EdgeInsets.only(bottom: 8),
                        child: Padding(
                          padding: const EdgeInsets.all(14.0),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  Expanded(
                                    child: Text(
                                      '${item.quantity}x ${item.productName}',
                                      style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold),
                                    ),
                                  ),
                                  Text(
                                    '\$${item.subtotal.toStringAsFixed(0)}',
                                    style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15, color: AppTheme.darkGreen),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 4),
                              Text(
                                'Sabor: ${item.flavorName} | Tamaño: ${item.size.displayName}',
                                style: const TextStyle(fontSize: 13, color: AppTheme.textGray),
                              ),
                              if (item.addons.isNotEmpty) ...[
                                const SizedBox(height: 6),
                                const Text('Complementos:', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold)),
                                const SizedBox(height: 2),
                                Text(addonsStr, style: const TextStyle(fontSize: 11, color: AppTheme.darkGreen)),
                              ],
                              if (item.observations.isNotEmpty) ...[
                                const SizedBox(height: 6),
                                Container(
                                  padding: const EdgeInsets.all(8),
                                  decoration: BoxDecoration(
                                    color: const Color(0xFFFFF9C4),
                                    borderRadius: BorderRadius.circular(6),
                                  ),
                                  child: Text(
                                    'Nota: "${item.observations}"',
                                    style: const TextStyle(fontSize: 11, fontStyle: FontStyle.italic, color: Color(0xFF5D4037)),
                                  ),
                                ),
                              ],
                            ],
                          ),
                        ),
                      );
                    },
                  ),
                  const SizedBox(height: 12),

                  // Order total display
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text('Total a Cobrar:', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
                      Text(
                        '\$${resolvedOrder.total.toStringAsFixed(0)}',
                        style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w900, color: AppTheme.darkGreen),
                      ),
                    ],
                  ),
                  const SizedBox(height: 20),

                  // Persistent Checklist in PREPARING mode
                  if (!isFinished && resolvedOrder.status == OrderStatus.preparing && _checklistLoaded) ...[
                    const Text(
                      'Checklist de Calidad en Preparación',
                      style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: AppTheme.darkGreen),
                    ),
                    const SizedBox(height: 6),
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(6.0),
                        child: Column(
                          children: _prepChecklist.keys.map((key) {
                            return CheckboxListTile(
                              activeColor: AppTheme.darkGreen,
                              dense: true,
                              title: Text(key, style: const TextStyle(fontSize: 13)),
                              value: _prepChecklist[key] ?? false,
                              onChanged: (val) => _onChecklistChanged(key, val ?? false),
                            );
                          }).toList(),
                        ),
                      ),
                    ),
                    const SizedBox(height: 20),
                  ],

                  // Traceability / Timeline section
                  const Divider(height: 28),
                  const Text('Trazabilidad y Auditoría', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
                  const SizedBox(height: 8),
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16.0),
                      child: statusHistoryAsync.when(
                        data: (sHistory) => changeHistoryAsync.when(
                          data: (cHistory) => OrderTimelineWidget(
                            statusHistory: sHistory,
                            changeHistory: cHistory,
                          ),
                          loading: () => const Center(child: CircularProgressIndicator(color: AppTheme.darkGreen)),
                          error: (_, __) => OrderTimelineWidget(statusHistory: sHistory, changeHistory: const []),
                        ),
                        loading: () => const Center(child: CircularProgressIndicator(color: AppTheme.darkGreen)),
                        error: (_, __) => const Center(child: Text('Error al cargar historial.')),
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),

                  // Historical Summary if completed/cancelled
                  if (isFinished) ...[
                    Card(
                      color: resolvedOrder.status == OrderStatus.delivered ? AppTheme.softGreen : const Color(0xFFFFEBEE),
                      child: Padding(
                        padding: const EdgeInsets.all(16.0),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              resolvedOrder.status == OrderStatus.delivered
                                  ? '✅ Pedido Entregado con Éxito'
                                  : '❌ Pedido Cancelado',
                              style: TextStyle(
                                fontWeight: FontWeight.w900,
                                fontSize: 14,
                                color: resolvedOrder.status == OrderStatus.delivered ? AppTheme.darkGreen : AppTheme.strawberryRed,
                              ),
                            ),
                            const SizedBox(height: 4),
                            if (resolvedOrder.cancellationReason != null && resolvedOrder.cancellationReason!.isNotEmpty)
                              Text('Motivo: ${resolvedOrder.cancellationReason}', style: const TextStyle(fontSize: 12, color: AppTheme.strawberryRed)),
                            const SizedBox(height: 4),
                            Text(
                              'Hora: ${timeFormatter.format(resolvedOrder.updatedAt)} | Duración total: ${resolvedOrder.totalDuration.inMinutes} min',
                              style: const TextStyle(fontSize: 11, color: AppTheme.textGray),
                            ),
                          ],
                        ),
                      ),
                    ),
                    const SizedBox(height: 20),
                  ],
                ],
              ),
            ),
          ),

          // Action buttons footer (only for active orders)
          if (!isFinished)
            Container(
              padding: const EdgeInsets.all(16.0),
              decoration: BoxDecoration(
                color: Colors.white,
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.05),
                    blurRadius: 8,
                    offset: const Offset(0, -3),
                  ),
                ],
              ),
              child: _buildActionButtons(context, resolvedOrder),
            ),
        ],
      ),
    );
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
                onPressed: () => _showCancelModal(context, order),
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
        return Row(
          children: [
            IconButton(
              icon: const Icon(Icons.cancel_outlined, color: AppTheme.strawberryRed),
              tooltip: 'Cancelar',
              onPressed: () => _showCancelModal(context, order),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppTheme.primaryLemon,
                  foregroundColor: AppTheme.darkBg,
                ),
                onPressed: () => _showStatusConfirm(context, OrderStatus.preparing, 'Iniciar Preparación', order),
                child: const Text('INICIAR PREPARACIÓN'),
              ),
            ),
          ],
        );
      case OrderStatus.preparing:
        return Row(
          children: [
            IconButton(
              icon: const Icon(Icons.cancel_outlined, color: AppTheme.strawberryRed),
              tooltip: 'Cancelar',
              onPressed: () => _showCancelModal(context, order),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppTheme.mintGreen,
                  foregroundColor: AppTheme.darkGreen,
                ),
                onPressed: () => _showStatusConfirm(context, OrderStatus.almostReady, 'Marcar Casi Listo', order),
                child: const Text('MARCAR CASI LISTO'),
              ),
            ),
          ],
        );
      case OrderStatus.almostReady:
        return Row(
          children: [
            IconButton(
              icon: const Icon(Icons.cancel_outlined, color: AppTheme.strawberryRed),
              tooltip: 'Cancelar',
              onPressed: () => _showCancelModal(context, order),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppTheme.darkGreen,
                  foregroundColor: Colors.white,
                ),
                onPressed: () => _showStatusConfirm(context, OrderStatus.ready, 'Marcar Listo', order),
                child: const Text('MARCAR COMO LISTO'),
              ),
            ),
          ],
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
            Row(
              children: [
                IconButton(
                  icon: const Icon(Icons.cancel_outlined, color: AppTheme.strawberryRed),
                  tooltip: 'Cancelar',
                  onPressed: () => _showCancelModal(context, order),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: OutlinedButton(
                    style: OutlinedButton.styleFrom(
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      side: const BorderSide(color: AppTheme.darkBg, width: 2),
                    ),
                    onPressed: () => _showStatusConfirm(context, OrderStatus.delivered, 'Confirmar Entrega', order),
                    child: const Text('MARCAR ENTREGADO', style: TextStyle(color: AppTheme.darkBg, fontWeight: FontWeight.bold)),
                  ),
                ),
              ],
            ),
          ],
        );
      default:
        return const SizedBox.shrink();
    }
  }
}
