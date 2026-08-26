import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/models/models.dart';
import '../../../core/theme/app_theme.dart';
import '../../auth/presentation/login_screen.dart';
import '../../auth/providers/auth_provider.dart';
import '../../lemon_ai/presentation/widgets/lemon_ai_fab.dart';
import '../../lemon_ai/presentation/widgets/lemon_ai_sheet.dart';
import '../../tracking/presentation/order_tracking_detail_screen.dart';
import '../../tracking/presentation/widgets/interactive_order_card.dart';
import 'customer_wizard_screen.dart';

class WelcomeScreen extends ConsumerStatefulWidget {
  const WelcomeScreen({super.key});

  @override
  ConsumerState<WelcomeScreen> createState() => _WelcomeScreenState();
}

class _WelcomeScreenState extends ConsumerState<WelcomeScreen> {
  final _codeController = TextEditingController();
  bool _isLoading = false;
  Map<String, dynamic>? _trackedOrder;
  String? _errorMessage;

  @override
  void dispose() {
    _codeController.dispose();
    super.dispose();
  }

  void _trackOrder() async {
    final code = _codeController.text.trim();
    if (code.isEmpty) {
      setState(() => _errorMessage = 'Ingresa el código o celular de tu pedido.');
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
      if (res.statusCode == 200 && res.data is Map<String, dynamic>) {
        setState(() {
          _trackedOrder = res.data as Map<String, dynamic>;
        });
      }
    } catch (e) {
      setState(() {
        _errorMessage = 'No se encontró ningún pedido con los datos ingresados.';
      });
    } finally {
      setState(() => _isLoading = false);
    }
  }

  void _openOrderDetail(Order order) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => OrderTrackingDetailScreen(initialOrder: order),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppTheme.creamBg,
      floatingActionButton: const LemonAiFab(),
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
            const SizedBox(height: 14),

            // Lemon AI Assistant Prominent Card
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24.0),
              child: Card(
                elevation: 3,
                shadowColor: AppTheme.darkGreen.withOpacity(0.2),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(24),
                  side: const BorderSide(color: AppTheme.primaryLemon, width: 1.5),
                ),
                color: AppTheme.darkBg,
                child: InkWell(
                  onTap: () {
                    LemonAiSheet.show(context);
                  },
                  borderRadius: BorderRadius.circular(24),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 18, horizontal: 20),
                    child: Row(
                      children: [
                        Container(
                          width: 50,
                          height: 50,
                          decoration: BoxDecoration(
                            color: AppTheme.primaryLemon,
                            shape: BoxShape.circle,
                            boxShadow: [
                              BoxShadow(
                                color: AppTheme.primaryLemon.withOpacity(0.4),
                                blurRadius: 10,
                              ),
                            ],
                          ),
                          child: const Center(
                            child: Text('🍋', style: TextStyle(fontSize: 26)),
                          ),
                        ),
                        const SizedBox(width: 16),
                        const Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  Text(
                                    'PEDIR CON LEMON AI',
                                    style: TextStyle(
                                      fontSize: 16,
                                      fontWeight: FontWeight.w900,
                                      color: Colors.white,
                                      letterSpacing: 0.5,
                                    ),
                                  ),
                                  SizedBox(width: 4),
                                  Text('✨', style: TextStyle(fontSize: 14)),
                                ],
                              ),
                              SizedBox(height: 3),
                              Text(
                                'Pide hablando por voz 🎙️ o escribiendo por chat',
                                style: TextStyle(
                                  fontSize: 12,
                                  fontWeight: FontWeight.w500,
                                  color: AppTheme.mintGreen,
                                ),
                              ),
                            ],
                          ),
                        ),
                        const Icon(Icons.arrow_forward_ios_rounded, color: AppTheme.primaryLemon, size: 18),
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
                        onSubmitted: (_) => _trackOrder(),
                      ),
                      const SizedBox(height: 16),
                      _isLoading
                          ? const Center(child: CircularProgressIndicator(color: AppTheme.darkGreen))
                          : ElevatedButton(
                              onPressed: _trackOrder,
                              child: const Text('VER ESTADO DEL PEDIDO'),
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
      final ordersRaw = _trackedOrder!['orders'] as List? ?? [];
      final orders = ordersRaw
          .map((o) => Order.fromJson(o as Map<String, dynamic>))
          .toList();

      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 8.0),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text(
                  'Pedidos Encontrados 📋',
                  style: TextStyle(fontSize: 16, fontWeight: FontWeight.w900, color: AppTheme.darkBg),
                ),
                Text(
                  '${orders.length} pedido(s)',
                  style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: AppTheme.darkGreen),
                ),
              ],
            ),
          ),
          const SizedBox(height: 4),
          ...orders.map((order) => InteractiveOrderCard(
                order: order,
                onTap: () => _openOrderDetail(order),
              )),
        ],
      );
    }

    final singleOrder = Order.fromJson(_trackedOrder!);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        const Padding(
          padding: EdgeInsets.symmetric(vertical: 8.0),
          child: Text(
            'Pedido Encontrado 📋',
            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w900, color: AppTheme.darkBg),
          ),
        ),
        InteractiveOrderCard(
          order: singleOrder,
          onTap: () => _openOrderDetail(singleOrder),
        ),
      ],
    );
  }
}

