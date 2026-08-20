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

class _EditOrderScreenState extends ConsumerState<EditOrderScreen> {
  final _reasonController = TextEditingController();
  final _observationsController = TextEditingController();

  // Selected values for the first order item
  late String _selectedProductId;
  late String _selectedFlavorId;
  late ProductSize _selectedSize;
  late int _selectedQuantity;
  final List<String> _selectedAddonIds = [];

  @override
  void initState() {
    super.initState();
    final firstItem = widget.order.items.first;
    _selectedProductId = firstItem.productId;
    _selectedFlavorId = firstItem.flavorId;
    _selectedSize = firstItem.size;
    _selectedQuantity = firstItem.quantity;
    _selectedAddonIds.addAll(firstItem.addons.map((a) => a.addonId));
    _observationsController.text = firstItem.observations;
  }

  @override
  void dispose() {
    _reasonController.dispose();
    _observationsController.dispose();
    super.dispose();
  }

  void _submitChange() async {
    final reason = _reasonController.text.trim();
    if (reason.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('El motivo del cambio es obligatorio.')),
      );
      return;
    }

    final itemsPayload = [
      {
        'productId': _selectedProductId,
        'flavorId': _selectedFlavorId,
        'size': _selectedSize.toJson(),
        'quantity': _selectedQuantity,
        'addonIds': _selectedAddonIds,
        'observations': _observationsController.text.trim(),
      }
    ];

    final success = await ref.read(activeOrdersProvider.notifier).editOrder(
          widget.order.id,
          itemsPayload,
          reason,
        );

    if (success && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: const Text('Pedido modificado y recalculado con éxito.'),
          backgroundColor: AppTheme.darkGreen,
          behavior: SnackBarBehavior.floating,
        ),
      );
      Navigator.pop(context);
    } else {
      if (mounted) {
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
        title: const Text('Modificar Pedido'),
      ),
      body: catalogAsync.when(
        data: (catalog) {
          final products = catalog['products'] as List<Product>;
          final flavors = catalog['flavors'] as List<Flavor>;
          final addons = catalog['addons'] as List<Addon>;

          return SingleChildScrollView(
            padding: const EdgeInsets.all(20.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // Product selector
                const Text('Producto:', style: TextStyle(fontWeight: FontWeight.bold)),
                const SizedBox(height: 8),
                DropdownButtonFormField<String>(
                  value: _selectedProductId,
                  decoration: const InputDecoration(contentPadding: EdgeInsets.all(12)),
                  items: products
                      .map((p) => DropdownMenuItem(value: p.id, child: Text(p.name)))
                      .toList(),
                  onChanged: (val) {
                    if (val != null) {
                      setState(() => _selectedProductId = val);
                    }
                  },
                ),
                const SizedBox(height: 16),

                // Size selector
                const Text('Tamaño:', style: TextStyle(fontWeight: FontWeight.bold)),
                const SizedBox(height: 8),
                DropdownButtonFormField<ProductSize>(
                  value: _selectedSize,
                  decoration: const InputDecoration(contentPadding: EdgeInsets.all(12)),
                  items: ProductSize.values
                      .map((s) => DropdownMenuItem(value: s, child: Text(s.displayName)))
                      .toList(),
                  onChanged: (val) {
                    if (val != null) {
                      setState(() => _selectedSize = val);
                    }
                  },
                ),
                const SizedBox(height: 16),

                // Flavor selector
                const Text('Sabor:', style: TextStyle(fontWeight: FontWeight.bold)),
                const SizedBox(height: 8),
                DropdownButtonFormField<String>(
                  value: _selectedFlavorId,
                  decoration: const InputDecoration(contentPadding: EdgeInsets.all(12)),
                  items: flavors
                      .map((f) => DropdownMenuItem(value: f.id, child: Text(f.name)))
                      .toList(),
                  onChanged: (val) {
                    if (val != null) {
                      setState(() => _selectedFlavorId = val);
                    }
                  },
                ),
                const SizedBox(height: 16),

                // Addons checkboxes
                const Text('Complementos:', style: TextStyle(fontWeight: FontWeight.bold)),
                const SizedBox(height: 8),
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(8.0),
                    child: Column(
                      children: addons.map((addon) {
                        final isChecked = _selectedAddonIds.contains(addon.id);
                        return CheckboxListTile(
                          activeColor: AppTheme.darkGreen,
                          title: Text('${addon.name} (+\$${addon.additionalPrice.toStringAsFixed(0)})'),
                          value: isChecked,
                          onChanged: (val) {
                            setState(() {
                              if (val == true) {
                                _selectedAddonIds.add(addon.id);
                              } else {
                                _selectedAddonIds.remove(addon.id);
                              }
                            });
                          },
                        );
                      }).toList(),
                    ),
                  ),
                ),
                const SizedBox(height: 16),

                // Quantity selector
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    const Text('Cantidad:', style: TextStyle(fontWeight: FontWeight.bold)),
                    Row(
                      children: [
                        IconButton(
                          icon: const Icon(Icons.remove_circle_outline, color: AppTheme.darkGreen),
                          onPressed: () {
                            if (_selectedQuantity > 1) {
                              setState(() => _selectedQuantity--);
                            }
                          },
                        ),
                        Text(
                          _selectedQuantity.toString(),
                          style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                        ),
                        IconButton(
                          icon: const Icon(Icons.add_circle_outline, color: AppTheme.darkGreen),
                          onPressed: () {
                            setState(() => _selectedQuantity++);
                          },
                        ),
                      ],
                    ),
                  ],
                ),
                const SizedBox(height: 16),

                // Observations Field
                const Text('Observaciones:', style: TextStyle(fontWeight: FontWeight.bold)),
                const SizedBox(height: 8),
                TextField(
                  controller: _observationsController,
                  decoration: const InputDecoration(hintText: 'Ej. Poco dulce, sin pitillo...'),
                ),
                const SizedBox(height: 24),

                // REQUIRED REASON FOR EDIT FIELD
                const Text('Motivo del Cambio (Obligatorio):', style: TextStyle(fontWeight: FontWeight.bold, color: AppTheme.strawberryRed)),
                const SizedBox(height: 8),
                TextField(
                  controller: _reasonController,
                  decoration: const InputDecoration(
                    hintText: 'Escribe por qué modificas el pedido...',
                    fillColor: Color(0xFFFFECEC),
                  ),
                ),
                const SizedBox(height: 32),

                ElevatedButton(
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppTheme.darkGreen,
                    foregroundColor: Colors.white,
                  ),
                  onPressed: _submitChange,
                  child: const Text('GUARDAR Y RECALCULAR'),
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
}
