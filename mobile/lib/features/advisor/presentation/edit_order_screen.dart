import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/models/models.dart';
import '../providers/orders_provider.dart';

class EditOrderScreen extends ConsumerStatefulWidget {
  final Order order;
  const EditOrderScreen({super.key, required this.order});

  @override
  ConsumerState<EditOrderScreen> createState() => _EditOrderScreenState();
}

class _EditableItem {
  String productId;
  String flavorId;
  ProductSize size;
  int quantity;
  List<String> addonIds;
  TextEditingController observationsController;

  _EditableItem({
    required this.productId,
    required this.flavorId,
    required this.size,
    required this.quantity,
    required this.addonIds,
    required String observations,
  }) : observationsController = TextEditingController(text: observations);
}

class _EditOrderScreenState extends ConsumerState<EditOrderScreen> {
  final _reasonController = TextEditingController();
  final List<_EditableItem> _editableItems = [];
  bool _isSaving = false;

  @override
  void initState() {
    super.initState();
    // Initialize editable items from original order items
    for (var item in widget.order.items) {
      _editableItems.add(
        _EditableItem(
          productId: item.productId,
          flavorId: item.flavorId,
          size: item.size,
          quantity: item.quantity,
          addonIds: item.addons.map((a) => a.addonId).toList(),
          observations: item.observations,
        ),
      );
    }
  }

  @override
  void dispose() {
    _reasonController.dispose();
    for (var item in _editableItems) {
      item.observationsController.dispose();
    }
    super.dispose();
  }

  void _addNewItem(List<Product> products, List<Flavor> flavors) {
    if (products.isEmpty || flavors.isEmpty) return;
    setState(() {
      _editableItems.add(
        _EditableItem(
          productId: products.first.id,
          flavorId: flavors.first.id,
          size: ProductSize.medium,
          quantity: 1,
          addonIds: [],
          observations: '',
        ),
      );
    });
  }

  void _removeItem(int index) {
    if (_editableItems.length <= 1) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('El pedido debe tener al menos un producto.')),
      );
      return;
    }
    setState(() {
      _editableItems[index].observationsController.dispose();
      _editableItems.removeAt(index);
    });
  }

  double _calculateEstimatedTotal(Map<String, List<dynamic>> catalog) {
    final products = catalog['products'] as List<Product>;
    final flavors = catalog['flavors'] as List<Flavor>;
    final addons = catalog['addons'] as List<Addon>;

    final productMap = {for (var p in products) p.id: p};
    final flavorMap = {for (var f in flavors) f.id: f};
    final addonMap = {for (var a in addons) a.id: a};

    double total = 0.0;
    for (var item in _editableItems) {
      final p = productMap[item.productId];
      final f = flavorMap[item.flavorId];
      final basePrice = p?.sizePrices[item.size] ?? 0.0;
      final flavorExtra = f?.additionalPrice ?? 0.0;

      double addonsSum = 0.0;
      for (var aid in item.addonIds) {
        addonsSum += addonMap[aid]?.additionalPrice ?? 0.0;
      }

      total += (basePrice + flavorExtra + addonsSum) * item.quantity;
    }
    return total;
  }

  void _submitChange() async {
    final reason = _reasonController.text.trim();
    if (reason.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('El motivo del cambio es obligatorio.')),
      );
      return;
    }

    if (_editableItems.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('El pedido no puede quedar vacío.')),
      );
      return;
    }

    final itemsPayload = _editableItems.map((e) {
      return {
        'productId': e.productId,
        'flavorId': e.flavorId,
        'size': e.size.toJson(),
        'quantity': e.quantity,
        'addonIds': e.addonIds,
        'observations': e.observationsController.text.trim(),
      };
    }).toList();

    setState(() => _isSaving = true);

    final success = await ref.read(activeOrdersProvider.notifier).editOrder(
          widget.order.id,
          itemsPayload,
          reason,
        );

    if (mounted) {
      setState(() => _isSaving = false);
      if (success) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: const Text('Pedido modificado y recalculado con éxito.'),
            backgroundColor: AppTheme.darkGreen,
            behavior: SnackBarBehavior.floating,
          ),
        );
        Navigator.pop(context);
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Error al modificar el pedido.')),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final catalogAsync = ref.watch(catalogProvider);

    return Scaffold(
      appBar: AppBar(
        title: Text('Modificar ${widget.order.orderCode}'),
      ),
      body: catalogAsync.when(
        data: (catalog) {
          final products = catalog['products'] as List<Product>;
          final flavors = catalog['flavors'] as List<Flavor>;
          final addons = catalog['addons'] as List<Addon>;

          final estimatedTotal = _calculateEstimatedTotal(catalog);

          return SingleChildScrollView(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // Header info
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppTheme.softGreen,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'Cliente: ${widget.order.customerName}',
                        style: const TextStyle(fontWeight: FontWeight.bold, color: AppTheme.darkGreen),
                      ),
                      Text(
                        '${_editableItems.length} producto(s)',
                        style: const TextStyle(fontSize: 12, color: AppTheme.darkGreen, fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),

                // Editable items list
                ListView.builder(
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  itemCount: _editableItems.length,
                  itemBuilder: (context, index) {
                    final item = _editableItems[index];
                    return _buildItemCard(index, item, products, flavors, addons);
                  },
                ),
                const SizedBox(height: 8),

                // Add product button
                OutlinedButton.icon(
                  style: OutlinedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 12),
                    side: const BorderSide(color: AppTheme.darkGreen, width: 1.5),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                  ),
                  icon: const Icon(Icons.add_circle_outline, color: AppTheme.darkGreen),
                  label: const Text('+ Agregar Producto al Pedido', style: TextStyle(color: AppTheme.darkGreen, fontWeight: FontWeight.bold)),
                  onPressed: () => _addNewItem(products, flavors),
                ),
                const SizedBox(height: 24),

                // Price difference comparison box
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(color: Colors.grey.shade300),
                  ),
                  child: Column(
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Text('Total Anterior:', style: TextStyle(color: AppTheme.textGray, fontSize: 13)),
                          Text(
                            '\$${widget.order.total.toStringAsFixed(0)}',
                            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14, color: AppTheme.textGray),
                          ),
                        ],
                      ),
                      const Divider(height: 16),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Text('Nuevo Total Estimado:', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
                          Text(
                            '\$${estimatedTotal.toStringAsFixed(0)}',
                            style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 18, color: AppTheme.darkGreen),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 20),

                // Mandatory Reason Field
                const Text(
                  'Motivo del Cambio * (Obligatorio para auditoría):',
                  style: TextStyle(fontWeight: FontWeight.bold, color: AppTheme.strawberryRed),
                ),
                const SizedBox(height: 6),
                TextField(
                  controller: _reasonController,
                  decoration: const InputDecoration(
                    hintText: 'Ej. Cliente solicitó cambio de sabor o tamaño...',
                    fillColor: Color(0xFFFFECEC),
                  ),
                ),
                const SizedBox(height: 28),

                // Save button
                ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppTheme.darkGreen,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 14),
                  ),
                  onPressed: _isSaving ? null : _submitChange,
                  child: _isSaving
                      ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2))
                      : const Text('GUARDAR CAMBIOS Y RECALCULAR', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
                ),
              ],
            ),
          );
        },
        loading: () => const Center(child: CircularProgressIndicator(color: AppTheme.darkGreen)),
        error: (err, _) => const Center(child: Text('Error al cargar catálogo.')),
      ),
    );
  }

  Widget _buildItemCard(
    int index,
    _EditableItem item,
    List<Product> products,
    List<Flavor> flavors,
    List<Addon> addons,
  ) {
    return Card(
      margin: const EdgeInsets.only(bottom: 16),
      elevation: 2,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'Producto #${index + 1}',
                  style: const TextStyle(fontWeight: FontWeight.w900, fontSize: 15, color: AppTheme.darkBg),
                ),
                if (_editableItems.length > 1)
                  IconButton(
                    icon: const Icon(Icons.delete_outline, color: AppTheme.strawberryRed, size: 22),
                    tooltip: 'Eliminar este producto',
                    onPressed: () => _removeItem(index),
                  ),
              ],
            ),
            const Divider(height: 12),

            // Product selector
            const Text('Base:', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
            const SizedBox(height: 4),
            DropdownButtonFormField<String>(
              value: products.any((p) => p.id == item.productId) ? item.productId : products.first.id,
              decoration: const InputDecoration(contentPadding: EdgeInsets.all(10)),
              items: products.map((p) => DropdownMenuItem(value: p.id, child: Text(p.name))).toList(),
              onChanged: (val) {
                if (val != null) setState(() => item.productId = val);
              },
            ),
            const SizedBox(height: 12),

            // Size & Flavor Row
            Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('Tamaño:', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                      const SizedBox(height: 4),
                      DropdownButtonFormField<ProductSize>(
                        value: item.size,
                        decoration: const InputDecoration(contentPadding: EdgeInsets.all(10)),
                        items: ProductSize.values.map((s) => DropdownMenuItem(value: s, child: Text(s.displayName))).toList(),
                        onChanged: (val) {
                          if (val != null) setState(() => item.size = val);
                        },
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('Sabor:', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                      const SizedBox(height: 4),
                      DropdownButtonFormField<String>(
                        value: flavors.any((f) => f.id == item.flavorId) ? item.flavorId : flavors.first.id,
                        decoration: const InputDecoration(contentPadding: EdgeInsets.all(10)),
                        items: flavors.map((f) => DropdownMenuItem(value: f.id, child: Text(f.name))).toList(),
                        onChanged: (val) {
                          if (val != null) setState(() => item.flavorId = val);
                        },
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),

            // Quantity selector
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text('Cantidad:', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold)),
                Row(
                  children: [
                    IconButton(
                      icon: const Icon(Icons.remove_circle_outline, color: AppTheme.darkGreen),
                      onPressed: () {
                        if (item.quantity > 1) {
                          setState(() => item.quantity--);
                        }
                      },
                    ),
                    Text(
                      item.quantity.toString(),
                      style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                    ),
                    IconButton(
                      icon: const Icon(Icons.add_circle_outline, color: AppTheme.darkGreen),
                      onPressed: () {
                        setState(() => item.quantity++);
                      },
                    ),
                  ],
                ),
              ],
            ),
            const SizedBox(height: 8),

            // Addons
            const Text('Complementos:', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
            const SizedBox(height: 4),
            Wrap(
              spacing: 6,
              runSpacing: 4,
              children: addons.map((addon) {
                final isChecked = item.addonIds.contains(addon.id);
                return FilterChip(
                  label: Text('${addon.name} (+\$${addon.additionalPrice.toStringAsFixed(0)})', style: const TextStyle(fontSize: 11)),
                  selected: isChecked,
                  selectedColor: AppTheme.mintGreen,
                  checkmarkColor: AppTheme.darkGreen,
                  onSelected: (val) {
                    setState(() {
                      if (val) {
                        item.addonIds.add(addon.id);
                      } else {
                        item.addonIds.remove(addon.id);
                      }
                    });
                  },
                );
              }).toList(),
            ),
            const SizedBox(height: 10),

            // Observations
            const Text('Observaciones:', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
            const SizedBox(height: 4),
            TextField(
              controller: item.observationsController,
              decoration: const InputDecoration(
                hintText: 'Ej. Poco dulce, sin pitillo...',
                contentPadding: EdgeInsets.all(10),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
