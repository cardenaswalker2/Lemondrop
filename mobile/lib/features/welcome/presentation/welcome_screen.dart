import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/app_theme.dart';
import '../../auth/presentation/login_screen.dart';
import '../../auth/providers/auth_provider.dart';
import 'customer_wizard_screen.dart';

class WelcomeScreen extends StatefulWidget {
  const WelcomeScreen({super.key});

  @override
  State<WelcomeScreen> createState() => _WelcomeScreenState();
}

class _WelcomeScreenState extends State<WelcomeScreen> {
  final _codeController = TextEditingController();
  bool _isLoading = false;
  Map<String, dynamic>? _trackedOrder;
  String? _errorMessage;

  @override
  void dispose() {
    _codeController.dispose();
    super.dispose();
  }

  void _trackOrder(WidgetRef ref) async {
    final code = _codeController.text.trim();
    if (code.isEmpty) {
      setState(() => _errorMessage = 'Ingresa el código de tu pedido.');
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = null;
      _trackedOrder = null;
    });

    try {
      final client = ref.read(apiClientProvider);
      final res = await client.dio.get('/api/public/pedidos/track/$code');
      if (res.statusCode == 200) {
        setState(() {
          _trackedOrder = res.data;
        });
      }
    } catch (e) {
      setState(() {
        _errorMessage = 'No se encontró ningún pedido con ese código.';
      });
    } finally {
      setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.creamBg,
      body: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Hero section with gradient background
            Container(
              padding: const EdgeInsets.fromLTRB(24, 60, 24, 40),
              decoration: const BoxDecoration(
                gradient: LinearGradient(
                  colors: [AppTheme.primaryLemon, AppTheme.mintGreen],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.only(
                  bottomLeft: Radius.circular(36),
                  bottomRight: Radius.circular(36),
                ),
              ),
              child: Column(
                children: [
                  Container(
                    width: 90,
                    height: 90,
                    decoration: const BoxDecoration(
                      color: Colors.white,
                      shape: BoxShape.circle,
                    ),
                    child: const Center(
                      child: Text('🍋', style: TextStyle(fontSize: 48)),
                    ),
                  ),
                  const SizedBox(height: 16),
                  const Text(
                    'LEMON DROP',
                    style: TextStyle(
                      fontSize: 32,
                      fontWeight: FontWeight.w900,
                      color: AppTheme.darkBg,
                      letterSpacing: 1.5,
                    ),
                  ),
                  const SizedBox(height: 6),
                  const Text(
                    'Sabor, Frescura y Diversión en cada sorbo',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: FontWeight.bold,
                      color: AppTheme.darkGreen,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 28),

            // Prominent Customer Order Button
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24.0),
              child: Card(
                elevation: 4,
                shadowColor: AppTheme.primaryLemon.withOpacity(0.3),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(28)),
                color: AppTheme.primaryLemon,
                child: InkWell(
                  onTap: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(builder: (context) => const CustomerWizardScreen()),
                    );
                  },
                  borderRadius: BorderRadius.circular(28),
                  child: const Padding(
                    padding: EdgeInsets.symmetric(vertical: 24, horizontal: 20),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text('🛒', style: TextStyle(fontSize: 36)),
                        SizedBox(width: 16),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'HACER MI PEDIDO',
                                style: TextStyle(
                                  fontSize: 20,
                                  fontWeight: FontWeight.w900,
                                  color: AppTheme.darkBg,
                                  letterSpacing: 0.5,
                                ),
                              ),
                              SizedBox(height: 4),
                              Text(
                                'Realiza tu pedido fácil y rápido aquí',
                                style: TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.bold,
                                  color: AppTheme.darkGreen,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
            const SizedBox(height: 24),

            // About us / Lemon Drop info section
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Nuestra Propuesta 🍧',
                    style: TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.bold,
                      color: AppTheme.darkBg,
                    ),
                  ),
                  const SizedBox(height: 6),
                  const Text(
                    'Creamos los granizados más refrescantes de la feria utilizando frutas e ingredientes de primera calidad.',
                    style: TextStyle(fontSize: 13, color: AppTheme.textGray),
                  ),
                  const SizedBox(height: 16),
                  
                  // Feature cards grid
                  Row(
                    children: [
                      Expanded(
                        child: _buildFeatureCard('❄️', 'Granizado', 'Hielo crujiente'),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: _buildFeatureCard('🍓', 'Frutas', 'Sabor natural'),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: _buildFeatureCard('🍯', 'Tops', 'Leche cond. y más'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 32),

            // Order Tracking Section
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24.0),
              child: Card(
                elevation: 2,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
                child: Padding(
                  padding: const EdgeInsets.all(20.0),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const Text(
                        'Rastrea tu Granizado 🥤',
                        style: TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                          color: AppTheme.darkBg,
                        ),
                      ),
                      const SizedBox(height: 6),
                      const Text(
                        '¿Ya hiciste tu pedido? Ingresa el código o tu número de celular para verificar el estado de preparación en tiempo real.',
                        style: TextStyle(fontSize: 12, color: AppTheme.textGray),
                      ),
                      const SizedBox(height: 16),
                      TextField(
                        controller: _codeController,
                        textCapitalization: TextCapitalization.characters,
                        decoration: const InputDecoration(
                          hintText: 'Ej. LD-2026-0012 o 3001234567',
                          prefixIcon: Icon(Icons.search_rounded),
                        ),
                      ),
                      const SizedBox(height: 16),
                      Consumer(
                        builder: (context, ref, child) => _isLoading
                            ? const Center(child: CircularProgressIndicator(color: AppTheme.darkGreen))
                            : ElevatedButton(
                                onPressed: () => _trackOrder(ref),
                                child: const Text('VER ESTADO DEL PEDIDO'),
                              ),
                      ),
                      if (_errorMessage != null) ...[
                        const SizedBox(height: 12),
                        Text(
                          _errorMessage!,
                          textAlign: TextAlign.center,
                          style: const TextStyle(
                            color: AppTheme.strawberryRed,
                            fontSize: 12,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ]
                    ],
                  ),
                ),
              ),
            ),
            const SizedBox(height: 20),

            // Track results output
            if (_trackedOrder != null)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 24.0),
                child: _buildTrackResults(),
              ),

            const SizedBox(height: 48),

            // Operational Access Section
            Container(
              padding: const EdgeInsets.all(24.0),
              color: AppTheme.softGreen.withOpacity(0.3),
              child: Column(
                children: [
                  const Text(
                    '¿Eres del equipo operativo?',
                    style: TextStyle(
                      fontSize: 13,
                      fontWeight: FontWeight.bold,
                      color: AppTheme.darkGreen,
                    ),
                  ),
                  const SizedBox(height: 8),
                  OutlinedButton.icon(
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                      side: const BorderSide(color: AppTheme.darkGreen, width: 2),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                    ),
                    icon: const Icon(Icons.lock_person_outlined, color: AppTheme.darkGreen),
                    label: const Text(
                      'ACCESO CENTRAL OPERATIVO',
                      style: TextStyle(
                        color: AppTheme.darkGreen,
                        fontWeight: FontWeight.bold,
                        fontSize: 12,
                      ),
                    ),
                    onPressed: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(builder: (context) => const LoginScreen()),
                      );
                    },
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildFeatureCard(String emoji, String title, String subtitle) {
    return Card(
      elevation: 0,
      color: AppTheme.softGreen.withOpacity(0.5),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
      ),
      child: Padding(
        padding: const EdgeInsets.all(12.0),
        child: Column(
          children: [
            Text(emoji, style: const TextStyle(fontSize: 24)),
            const SizedBox(height: 8),
            Text(
              title,
              style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13, color: AppTheme.darkBg),
            ),
            const SizedBox(height: 2),
            Text(
              subtitle,
              style: const TextStyle(fontSize: 10, color: AppTheme.textGray),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildTrackResults() {
    final isMultiple = _trackedOrder!['multiple'] as bool? ?? false;

    if (isMultiple) {
      final ordersList = _trackedOrder!['orders'] as List? ?? [];
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 8.0),
            child: Text(
              'Pedidos Encontrados:',
              style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: AppTheme.darkBg),
            ),
          ),
          ...ordersList.map((o) => _buildSingleOrderResultCard(o as Map<String, dynamic>)),
        ],
      );
    }

    return _buildSingleOrderResultCard(_trackedOrder!);
  }

  Widget _buildSingleOrderResultCard(Map<String, dynamic> orderData) {
    final statusDisplay = orderData['statusDisplay'] as String? ?? '';
    final status = orderData['status'] as String? ?? 'RECEIVED';
    final customerName = orderData['customerName'] as String? ?? '';
    final code = orderData['orderCode'] as String? ?? '';
    final total = orderData['total'] as num? ?? 0;
    final itemsList = orderData['items'] as List? ?? [];

    return Card(
      elevation: 2,
      margin: const EdgeInsets.only(bottom: 12),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(24)),
      child: Padding(
        padding: const EdgeInsets.all(20.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  code,
                  style: const TextStyle(fontWeight: FontWeight.bold, color: AppTheme.textGray),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: _getStatusColor(status).withOpacity(0.15),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    statusDisplay.toUpperCase(),
                    style: TextStyle(
                      fontSize: 10,
                      fontWeight: FontWeight.w900,
                      color: _getStatusColor(status),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Text(
              '¡Hola, $customerName! 👋',
              style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: AppTheme.darkBg),
            ),
            const SizedBox(height: 4),
            const Text(
              'Tu pedido está siendo preparado con toda la frescura de Lemon Drop.',
              style: TextStyle(fontSize: 12, color: AppTheme.textGray),
            ),
            const SizedBox(height: 12),
            const Divider(height: 1),
            const SizedBox(height: 12),
            ...itemsList.map((item) {
              return Padding(
                padding: const EdgeInsets.symmetric(vertical: 4.0),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      '${item['quantity']}x ${item['productName']} (${item['flavorName']})',
                      style: const TextStyle(fontSize: 13, fontWeight: FontWeight.bold),
                    ),
                    Text(
                      '\$${item['subtotal']}',
                      style: const TextStyle(fontSize: 13, color: AppTheme.textGray),
                    ),
                  ],
                ),
              );
            }),
            const SizedBox(height: 12),
            const Divider(height: 1),
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text('Total:', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                Text(
                  '\$${total.toStringAsFixed(0)}',
                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15, color: AppTheme.darkGreen),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Color _getStatusColor(String status) {
    switch (status.toUpperCase()) {
      case 'RECEIVED':
        return AppTheme.primaryLemon;
      case 'ACCEPTED':
        return Colors.blue;
      case 'PREPARING':
        return Colors.orange;
      case 'ALMOST_READY':
        return Colors.lightGreen;
      case 'READY':
        return AppTheme.darkGreen;
      case 'DELIVERED':
        return AppTheme.textGray;
      default:
        return AppTheme.textGray;
    }
  }
}
