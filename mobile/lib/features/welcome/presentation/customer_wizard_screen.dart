import 'dart:async';
import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/models/models.dart';
import '../../auth/providers/auth_provider.dart';

class CustomerWizardScreen extends ConsumerStatefulWidget {
  const CustomerWizardScreen({super.key});

  @override
  ConsumerState<CustomerWizardScreen> createState() => _CustomerWizardScreenState();
}

class _CustomerWizardScreenState extends ConsumerState<CustomerWizardScreen> {
  int _currentStep = 1; // 1: Welcome/Empezar, 2: Select Product, 3: Customize, 4: Checkout Info, 5: Success
  
  // Catalog Data
  List<Product> _products = [];
  List<Flavor> _flavors = [];
  List<Addon> _addons = [];
  bool _isLoadingCatalog = true;
  String? _catalogError;

  // Selected Customization State
  Product? _selectedProduct;
  Flavor? _selectedFlavor;
  ProductSize _selectedSize = ProductSize.medium;
  final Set<String> _selectedAddonIds = {};
  int _quantity = 1;
  final _observationsController = TextEditingController();

  // Final Order Info
  final _nameController = TextEditingController();
  final _phoneController = TextEditingController();
  final _orderObsController = TextEditingController();
  
  // Submission & Network Polling State
  bool _isSubmitting = false;
  String _checkoutRequestId = '';
  String? _successOrderCode;
  Timer? _autoReturnTimer;
  int _returnSecondsRemaining = 15;

  @override
  void initState() {
    super.initState();
    _resetClientSession();
    _fetchPublicCatalog();
  }

  @override
  void dispose() {
    _observationsController.dispose();
    _nameController.dispose();
    _phoneController.dispose();
    _orderObsController.dispose();
    _autoReturnTimer?.cancel();
    super.dispose();
  }

  void _resetClientSession() {
    _selectedProduct = null;
    _selectedFlavor = null;
    _selectedSize = ProductSize.medium;
    _selectedAddonIds.clear();
    _quantity = 1;
    _observationsController.clear();
    _nameController.clear();
    _phoneController.clear();
    _orderObsController.clear();
    _isSubmitting = false;
    _successOrderCode = null;
    _checkoutRequestId = _generateUuidV4();
    _autoReturnTimer?.cancel();
    _returnSecondsRemaining = 15;
  }

  String _generateUuidV4() {
    final random = math.Random.secure();
    final values = List<int>.generate(16, (i) => random.nextInt(256));
    values[6] = (values[6] & 0x0f) | 0x40; // Set version 4
    values[8] = (values[8] & 0x3f) | 0x80; // Set variant RFC 4122
    
    final buffer = StringBuffer();
    for (var i = 0; i < 16; i++) {
      if (i == 4 || i == 6 || i == 8 || i == 10) {
        buffer.write('-');
      }
      buffer.write(values[i].toRadixString(16).padLeft(2, '0'));
    }
    return buffer.toString();
  }

  Future<void> _fetchPublicCatalog() async {
    setState(() {
      _isLoadingCatalog = true;
      _catalogError = null;
    });

    try {
      final client = ref.read(apiClientProvider);
      final res = await client.dio.get('/api/public/catalog');
      if (res.statusCode == 200) {
        final data = res.data as Map<String, dynamic>;
        final rawProducts = data['products'] as List? ?? [];
        final rawFlavors = data['flavors'] as List? ?? [];
        final rawAddons = data['addons'] as List? ?? [];

        setState(() {
          _products = rawProducts.map((e) => Product.fromJson(e)).toList();
          _flavors = rawFlavors.map((e) => Flavor.fromJson(e)).toList();
          _addons = rawAddons.map((e) => Addon.fromJson(e)).toList();
          _isLoadingCatalog = false;
        });
      }
    } catch (e) {
      setState(() {
        _catalogError = 'No pudimos conectarnos con Lemon Drop. Revisa tu conexión.';
        _isLoadingCatalog = false;
      });
    }
  }

  double _calculateItemPrice() {
    if (_selectedProduct == null) return 0.0;
    
    double base = _selectedProduct!.sizePrices[_selectedSize] ?? 0.0;
    double flavorExtra = _selectedFlavor?.additionalPrice ?? 0.0;
    
    double addonsExtra = 0.0;
    for (var addonId in _selectedAddonIds) {
      final addon = _addons.firstWhere((a) => a.id == addonId);
      addonsExtra += addon.additionalPrice;
    }
    
    return (base + flavorExtra + addonsExtra) * _quantity;
  }

  // Polling check logic if network fails during creation
  Future<void> _verifyOrderCreated() async {
    int attempts = 0;
    const maxAttempts = 3;

    Timer.periodic(const Duration(seconds: 3), (timer) async {
      attempts++;
      if (!mounted) {
        timer.cancel();
        return;
      }

      try {
        final client = ref.read(apiClientProvider);
        final res = await client.dio.get('/api/public/pedidos/check-request/$_checkoutRequestId');
        if (res.statusCode == 200 && res.data != null) {
          final found = res.data['found'] as bool? ?? false;
          if (found) {
            timer.cancel();
            final code = res.data['orderCode'] as String? ?? 'LD-2026-XXXX';
            _onOrderSuccess(code);
            return;
          }
        }
      } catch (_) {}

      if (attempts >= maxAttempts) {
        timer.cancel();
        setState(() => _isSubmitting = false);
        _showErrorDialog('No pudimos confirmar la respuesta del servidor. Revisa tu conexión e inténtalo de nuevo.');
      }
    });
  }

  void _onOrderSuccess(String code) {
    setState(() {
      _successOrderCode = code;
      _isSubmitting = false;
      _currentStep = 5; // Go to Success Screen
    });

    // Start auto return countdown
    _autoReturnTimer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (!mounted) {
        timer.cancel();
        return;
      }
      setState(() {
        if (_returnSecondsRemaining > 1) {
          _returnSecondsRemaining--;
        } else {
          timer.cancel();
          _resetClientSession();
          Navigator.pop(context); // Return to Welcome Screen
        }
      });
    });
  }

  Future<void> _submitOrder() async {
    if (_isSubmitting) return;

    final name = _nameController.text.trim();
    final phone = _phoneController.text.trim();

    if (name.isEmpty || phone.isEmpty) {
      _showErrorDialog('Por favor ingresa tu nombre y número de teléfono.');
      return;
    }

    if (!RegExp(r'^[0-9]{7,15}$').hasMatch(phone)) {
      _showErrorDialog('El número de teléfono debe contener entre 7 y 15 dígitos.');
      return;
    }

    setState(() {
      _isSubmitting = true;
    });

    // Build order items
    final payloadItems = [
      {
        'productId': _selectedProduct!.id,
        'flavorId': _selectedFlavor?.id ?? '',
        'size': _selectedSize.toJson(),
        'quantity': _quantity,
        'addonIds': _selectedAddonIds.toList(),
        'observations': _observationsController.text.trim(),
      }
    ];

    final payload = {
      'customerName': name,
      'customerPhone': phone,
      'observations': _orderObsController.text.trim(),
      'requestId': _checkoutRequestId,
      'items': payloadItems
    };

    try {
      final client = ref.read(apiClientProvider);
      final res = await client.dio.post('/api/public/pedidos', data: payload);
      
      if (res.statusCode == 200 && res.data != null) {
        final success = res.data['success'] as bool? ?? false;
        if (success) {
          final code = res.data['orderCode'] as String? ?? 'LD-2026-XXXX';
          _onOrderSuccess(code);
        } else {
          setState(() => _isSubmitting = false);
          _showErrorDialog('Error al crear pedido: ${res.data['message']}');
        }
      } else {
        throw Exception('Error de servidor.');
      }
    } catch (e) {
      // Ambiguous server or network error: start polling validation
      _verifyOrderCreated();
    }
  }

  void _showErrorDialog(String msg) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: const Text('¡Aviso! 🍋', style: TextStyle(fontWeight: FontWeight.bold)),
        content: Text(msg),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('ENTENDIDO', style: TextStyle(color: AppTheme.darkGreen, fontWeight: FontWeight.bold)),
          )
        ],
      ),
    );
  }

  void _confirmCancelOrder() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        title: const Text('¿Cancelar Pedido? 🍧', style: TextStyle(fontWeight: FontWeight.bold)),
        content: const Text('¿Seguro que quieres salir? Se borrarán todos los productos seleccionados.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('SEGUIR PIDIENDO', style: TextStyle(color: AppTheme.darkGreen, fontWeight: FontWeight.bold)),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: AppTheme.strawberryRed, foregroundColor: Colors.white),
            onPressed: () {
              Navigator.pop(context); // Close dialog
              _resetClientSession();
              Navigator.pop(context); // Exit wizard
            },
            child: const Text('CANCELAR Y SALIR'),
          )
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return WillPopScope(
      onWillPop: () async {
        if (_currentStep == 5) {
          // Success screen auto-resets, just pop
          _resetClientSession();
          return true;
        }
        _confirmCancelOrder();
        return false;
      },
      child: Scaffold(
        backgroundColor: AppTheme.creamBg,
        appBar: AppBar(
          title: const Text('🍋 Modo Autoservicio'),
          leading: IconButton(
            icon: const Icon(Icons.close),
            onPressed: _confirmCancelOrder,
          ),
          bottom: _currentStep > 1 && _currentStep < 5
              ? PreferredSize(
                  preferredSize: const Size.fromHeight(40),
                  child: Container(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        _buildStepIndicator(2, 'Producto'),
                        _buildStepLine(),
                        _buildStepIndicator(3, 'Personalizar'),
                        _buildStepLine(),
                        _buildStepIndicator(4, 'Confirmar'),
                      ],
                    ),
                  ),
                )
              : null,
        ),
        body: _isLoadingCatalog
            ? const Center(child: CircularProgressIndicator(color: AppTheme.darkGreen))
            : _catalogError != null
                ? _buildErrorScreen()
                : _isSubmitting
                    ? _buildSubmittingScreen()
                    : _buildCurrentStepScreen(),
      ),
    );
  }

  Widget _buildStepIndicator(int step, String label) {
    final isActive = _currentStep >= step;
    return Row(
      children: [
        Container(
          width: 24,
          height: 24,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: isActive ? AppTheme.darkGreen : Colors.grey.shade300,
          ),
          child: Center(
            child: Text(
              '${step - 1}',
              style: TextStyle(
                color: isActive ? Colors.white : Colors.grey.shade600,
                fontSize: 12,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
        ),
        const SizedBox(width: 4),
        Text(
          label,
          style: TextStyle(
            color: isActive ? AppTheme.darkGreen : Colors.grey.shade600,
            fontSize: 11,
            fontWeight: isActive ? FontWeight.bold : FontWeight.normal,
          ),
        )
      ],
    );
  }

  Widget _buildStepLine() {
    return Container(
      width: 20,
      height: 2,
      margin: const EdgeInsets.symmetric(horizontal: 6),
      color: Colors.grey.shade300,
    );
  }

  Widget _buildErrorScreen() {
    return Padding(
      padding: const EdgeInsets.all(32),
      child: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Text('⚠️', style: TextStyle(fontSize: 48)),
            const SizedBox(height: 16),
            Text(
              _catalogError!,
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 24),
          ElevatedButton(
            onPressed: _fetchPublicCatalog,
            child: const Text('REINTENTAR CONEXIÓN'),
          )
        ],
      ),
    ),
  );
}

  Widget _buildSubmittingScreen() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Text('🍋', style: TextStyle(fontSize: 80)),
          const SizedBox(height: 24),
          const Text(
            'Estamos creando tu pedido...',
            style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold, color: AppTheme.darkGreen),
          ),
          const SizedBox(height: 8),
          const Text(
            'Estamos enviando tu pedido a Lemon Drop.',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 14, color: AppTheme.textGray),
          ),
          const SizedBox(height: 24),
          const CircularProgressIndicator(color: AppTheme.darkGreen),
          const SizedBox(height: 24),
          const Text(
            'Por favor espera un momento y no cierres esta pantalla.',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 12, color: AppTheme.strawberryRed, fontWeight: FontWeight.bold),
          ),
        ],
      ),
    );
  }

  Widget _buildCurrentStepScreen() {
    switch (_currentStep) {
      case 1:
        return _buildWelcomeStep();
      case 2:
        return _buildSelectProductStep();
      case 3:
        return _buildCustomizeStep();
      case 4:
        return _buildCheckoutInfoStep();
      case 5:
        return _buildSuccessStep();
      default:
        return _buildWelcomeStep();
    }
  }

  Widget _buildWelcomeStep() {
    return Padding(
      padding: const EdgeInsets.all(24.0),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Text('🍋', textAlign: TextAlign.center, style: TextStyle(fontSize: 90)),
          const SizedBox(height: 16),
          const Text(
            '¡Hola! 👋',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 36, fontWeight: FontWeight.w900, color: AppTheme.darkBg),
          ),
          const SizedBox(height: 8),
          const Text(
            'Vamos a preparar tu Lemon Drop favorito en segundos.',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 16, color: AppTheme.textGray),
          ),
          const SizedBox(height: 48),
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              padding: const EdgeInsets.symmetric(vertical: 20),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
            ),
            onPressed: () => setState(() => _currentStep = 2),
            child: const Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text('EMPEZAR A ORDENAR 🍧', style: TextStyle(fontSize: 18)),
              ],
            ),
          )
        ],
      ),
    );
  }

  Widget _buildSelectProductStep() {
    final availableProds = _products.where((p) => p.available).toList();

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 16.0),
            child: Text(
              '¿Qué quieres pedir hoy? 🍧',
              style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold, color: AppTheme.darkBg),
            ),
          ),
          Expanded(
            child: availableProds.isEmpty
                ? const Center(child: Text('No hay productos disponibles en este momento.'))
                : ListView.builder(
                    itemCount: availableProds.length,
                    itemBuilder: (context, index) {
                      final prod = availableProds[index];
                      final startPrice = prod.sizePrices[ProductSize.small] ?? 0.0;

                      return Card(
                        margin: const EdgeInsets.only(bottom: 16),
                        child: InkWell(
                          onTap: () {
                            setState(() {
                              _selectedProduct = prod;
                              _selectedFlavor = _flavors.isNotEmpty ? _flavors.first : null;
                              _currentStep = 3;
                            });
                          },
                          borderRadius: BorderRadius.circular(20),
                          child: Padding(
                            padding: const EdgeInsets.all(16.0),
                            child: Row(
                              children: [
                                Container(
                                  width: 80,
                                  height: 80,
                                  decoration: BoxDecoration(
                                    color: AppTheme.softGreen,
                                    borderRadius: BorderRadius.circular(16),
                                  ),
                                  child: const Center(child: Text('🍧', style: TextStyle(fontSize: 40))),
                                ),
                                const SizedBox(width: 16),
                                Expanded(
                                  child: Column(
                                    crossAxisAlignment: CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        prod.name,
                                        style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                                      ),
                                      const SizedBox(height: 4),
                                      Text(
                                        prod.description,
                                        maxLines: 2,
                                        overflow: TextOverflow.ellipsis,
                                        style: const TextStyle(fontSize: 12, color: AppTheme.textGray),
                                      ),
                                      const SizedBox(height: 8),
                                      Text(
                                        'Desde \$${startPrice.toStringAsFixed(0)}',
                                        style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w800, color: AppTheme.darkGreen),
                                      )
                                    ],
                                  ),
                                ),
                                const Icon(Icons.arrow_forward_ios_rounded, color: AppTheme.textGray, size: 16)
                              ],
                            ),
                          ),
                        ),
                      );
                    },
                  ),
          )
        ],
      ),
    );
  }

  Widget _buildCustomizeStep() {
    if (_selectedProduct == null) return const SizedBox();

    return Column(
      children: [
        Expanded(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // Info Box
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: AppTheme.softGreen.withOpacity(0.3),
                    borderRadius: BorderRadius.circular(20),
                  ),
                  child: Row(
                    children: [
                      const Text('🍧', style: TextStyle(fontSize: 32)),
                      const SizedBox(width: 12),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(_selectedProduct!.name, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
                            Text(_selectedProduct!.description, style: const TextStyle(fontSize: 12, color: AppTheme.textGray)),
                          ],
                        ),
                      )
                    ],
                  ),
                ),
                const SizedBox(height: 20),

                // Sizes
                const Text('1. Escoge el Tamaño 📏', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
                const SizedBox(height: 8),
                Row(
                  children: ProductSize.values.map((size) {
                    final isSel = _selectedSize == size;
                    final price = _selectedProduct!.sizePrices[size] ?? 0.0;
                    return Expanded(
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 4.0),
                        child: ChoiceChip(
                          label: Column(
                            children: [
                              Text(size.displayName, style: const TextStyle(fontWeight: FontWeight.bold)),
                              Text('\$${price.toStringAsFixed(0)}', style: const TextStyle(fontSize: 11)),
                            ],
                          ),
                          selected: isSel,
                          onSelected: (_) => setState(() => _selectedSize = size),
                        ),
                      ),
                    );
                  }).toList(),
                ),
                const SizedBox(height: 20),

                // Flavors
                const Text('2. Selecciona el Sabor Base 🍇🍋', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
                const SizedBox(height: 8),
                if (_flavors.isEmpty)
                  const Text('No hay sabores disponibles.')
                else
                  Column(
                    children: _flavors.where((f) => f.available).map((flavor) {
                      final isSel = _selectedFlavor?.id == flavor.id;
                      final extra = flavor.additionalPrice == 0
                          ? 'Gratis'
                          : '+\$${flavor.additionalPrice.toStringAsFixed(0)}';
                      return RadioListTile<String>(
                        title: Text(flavor.name, style: const TextStyle(fontWeight: FontWeight.bold)),
                        subtitle: Text('${flavor.description} ($extra)'),
                        value: flavor.id,
                        groupValue: _selectedFlavor?.id,
                        onChanged: (val) {
                          setState(() {
                            _selectedFlavor = _flavors.firstWhere((f) => f.id == val);
                          });
                        },
                      );
                    }).toList(),
                  ),
                const SizedBox(height: 20),

                // Addons
                const Text('3. Agrega Complementos (Opcional) 🥛', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
                const SizedBox(height: 8),
                if (_addons.isEmpty)
                  const Text('No hay complementos disponibles.')
                else
                  Column(
                    children: _addons.where((a) => a.available).map((addon) {
                      final isChecked = _selectedAddonIds.contains(addon.id);
                      return CheckboxListTile(
                        title: Text(addon.name, style: const TextStyle(fontWeight: FontWeight.bold)),
                        subtitle: Text('+\$${addon.additionalPrice.toStringAsFixed(0)}'),
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
                const SizedBox(height: 20),

                // Quantity
                const Text('4. Cantidad 🔢', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
                const SizedBox(height: 8),
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    IconButton(
                      icon: const Icon(Icons.remove_circle_outline, size: 36, color: AppTheme.strawberryRed),
                      onPressed: _quantity > 1 ? () => setState(() => _quantity--) : null,
                    ),
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 24.0),
                      child: Text('$_quantity', style: const TextStyle(fontSize: 24, fontWeight: FontWeight.bold)),
                    ),
                    IconButton(
                      icon: const Icon(Icons.add_circle_outline, size: 36, color: AppTheme.darkGreen),
                      onPressed: () => setState(() => _quantity++),
                    ),
                  ],
                ),
                const SizedBox(height: 20),

                // Item Observations
                const Text('5. Notas especiales para la preparación ✏️', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
                const SizedBox(height: 8),
                TextField(
                  controller: _observationsController,
                  decoration: const InputDecoration(
                    hintText: 'Ej: Con mucha leche condensada, sin pitillo...',
                  ),
                ),
              ],
            ),
          ),
        ),
        
        // Sticky Price Bar
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: Colors.white,
            boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.05), blurRadius: 10, offset: const Offset(0, -3))],
          ),
          child: SafeArea(
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Text('Total Actual:', style: TextStyle(fontSize: 12, color: AppTheme.textGray)),
                    Text('\$${_calculateItemPrice().toStringAsFixed(0)}', style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w900, color: AppTheme.darkGreen)),
                  ],
                ),
                ElevatedButton(
                  onPressed: () => setState(() => _currentStep = 4),
                  child: const Text('CONTINUAR ➔'),
                )
              ],
            ),
          ),
        )
      ],
    );
  }

  Widget _buildCheckoutInfoStep() {
    final total = _calculateItemPrice();
    final addonNames = _selectedAddonIds.map((id) => _addons.firstWhere((a) => a.id == id).name).join(', ');

    return Column(
      children: [
        Expanded(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                // Cart Summary Display
                Card(
                  color: AppTheme.softGreen.withOpacity(0.2),
                  elevation: 0,
                  child: Padding(
                    padding: const EdgeInsets.all(16.0),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text('🛒 Tu Pedido', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                        const SizedBox(height: 12),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Expanded(
                              child: Text(
                                '${_selectedProduct!.name} (${_selectedFlavor?.name ?? "Clásico"}) x$_quantity',
                                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                              ),
                            ),
                            Text('\$$total', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
                          ],
                        ),
                        Text('Tamaño: ${_selectedSize.displayName}', style: const TextStyle(fontSize: 12, color: AppTheme.textGray)),
                        if (addonNames.isNotEmpty)
                          Text('+ Adicionales: $addonNames', style: const TextStyle(fontSize: 12, color: AppTheme.textGray)),
                        if (_observationsController.text.trim().isNotEmpty)
                          Text('Prep: "${_observationsController.text.trim()}"', style: const TextStyle(fontSize: 12, fontStyle: FontStyle.italic, color: AppTheme.darkGreen)),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 24),

                // Customer Data
                const Text('Completa tus Datos ✏️', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
                const SizedBox(height: 16),
                TextField(
                  controller: _nameController,
                  textCapitalization: TextCapitalization.words,
                  decoration: const InputDecoration(
                    labelText: 'Tu Nombre *',
                    prefixIcon: Icon(Icons.person_rounded),
                  ),
                ),
                const SizedBox(height: 16),
                TextField(
                  controller: _phoneController,
                  keyboardType: TextInputType.phone,
                  decoration: const InputDecoration(
                    labelText: 'Teléfono de contacto *',
                    prefixIcon: Icon(Icons.phone_android_rounded),
                    hintText: 'Ej: 3001234567',
                  ),
                ),
                const SizedBox(height: 16),
                TextField(
                  controller: _orderObsController,
                  decoration: const InputDecoration(
                    labelText: 'Observaciones generales del servicio',
                    prefixIcon: Icon(Icons.chat_bubble_outline_rounded),
                    hintText: 'Ej: Entregar en la mesa 4...',
                  ),
                ),
              ],
            ),
          ),
        ),
        
        // Sticky Checkout Action Bar
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: Colors.white,
            boxShadow: [BoxShadow(color: Colors.black.withOpacity(0.05), blurRadius: 10, offset: const Offset(0, -3))],
          ),
          child: SafeArea(
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                TextButton(
                  onPressed: () => setState(() => _currentStep = 3),
                  child: const Text('← MODIFICAR', style: TextStyle(fontWeight: FontWeight.bold, color: AppTheme.strawberryRed)),
                ),
                ElevatedButton(
                  onPressed: _submitOrder,
                  child: const Text('🛒 CONFIRMAR PEDIDO'),
                )
              ],
            ),
          ),
        )
      ],
    );
  }

  Widget _buildSuccessStep() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(24.0),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Text('🍧💛', textAlign: TextAlign.center, style: TextStyle(fontSize: 80)),
          const SizedBox(height: 20),
          const Text(
            '¡Pedido recibido!',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 28, fontWeight: FontWeight.w900, color: AppTheme.darkGreen),
          ),
          const SizedBox(height: 6),
          const Text(
            'Tu pedido fue enviado correctamente.',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 14, color: AppTheme.textGray),
          ),
          const SizedBox(height: 30),
          
          // Large Code Display
          Container(
            padding: const EdgeInsets.symmetric(vertical: 20, horizontal: 30),
            decoration: BoxDecoration(
              color: AppTheme.softGreen,
              borderRadius: BorderRadius.circular(20),
              border: Border.all(color: AppTheme.darkGreen, width: 2),
            ),
            child: Column(
              children: [
                const Text('CÓDIGO DE TU PEDIDO', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppTheme.darkGreen, letterSpacing: 1)),
                const SizedBox(height: 8),
                Text(
                  _successOrderCode ?? 'LD-2026-XXXX',
                  style: const TextStyle(fontSize: 32, fontWeight: FontWeight.w900, letterSpacing: 2, color: AppTheme.darkBg),
                ),
              ],
            ),
          ),
          const SizedBox(height: 24),
          
          const Text(
            'Estamos preparando tu Lemon Drop.\nTe avisaremos en el punto de venta cuando esté listo.',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 13, height: 1.5, color: AppTheme.textGray),
          ),
          const SizedBox(height: 48),
          
          ElevatedButton(
            style: ElevatedButton.styleFrom(
              padding: const EdgeInsets.symmetric(vertical: 16),
            ),
            onPressed: () {
              _resetClientSession();
              Navigator.pop(context);
            },
            child: const Text('ENTENDIDO / INICIO 🏠'),
          ),
          const SizedBox(height: 16),
          
          Text(
            'Esta pantalla volverá al inicio automáticamente en $_returnSecondsRemaining segundos.',
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 11, fontStyle: FontStyle.italic, color: AppTheme.textGray),
          ),
        ],
      ),
    );
  }
}
