import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/app_theme.dart';
import '../providers/orders_provider.dart';
import 'dashboard_screen.dart';
import 'orders_list_screen.dart';
import 'order_history_screen.dart';
import '../../profile/presentation/profile_screen.dart';

class AdvisorMainLayout extends ConsumerWidget {
  const AdvisorMainLayout({super.key});

  final List<Widget> _screens = const [
    DashboardScreen(),
    OrdersListScreen(),
    OrderHistoryScreen(),
    ProfileScreen(),
  ];

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final activeTab = ref.watch(advisorLayoutTabProvider);
    final ordersAsync = ref.watch(activeOrdersProvider);

    // Calculate count of active/pending orders for badge
    int pendingCount = 0;
    ordersAsync.whenData((orders) {
      pendingCount = orders.length;
    });

    return Scaffold(
      body: IndexedStack(
        index: activeTab,
        children: _screens,
      ),
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: activeTab,
        type: BottomNavigationBarType.fixed,
        selectedItemColor: AppTheme.darkGreen,
        unselectedItemColor: AppTheme.textGray,
        backgroundColor: Colors.white,
        elevation: 8,
        onTap: (index) {
          ref.read(advisorLayoutTabProvider.notifier).state = index;
        },
        items: [
          const BottomNavigationBarItem(
            icon: Icon(Icons.home_filled),
            label: 'Inicio',
          ),
          BottomNavigationBarItem(
            icon: Badge(
              backgroundColor: AppTheme.primaryLemon,
              textColor: AppTheme.darkBg,
              label: pendingCount > 0 ? Text(pendingCount.toString()) : null,
              isLabelVisible: pendingCount > 0,
              child: const Icon(Icons.receipt_long_rounded),
            ),
            label: 'Pedidos',
          ),
          const BottomNavigationBarItem(
            icon: Icon(Icons.history_rounded),
            label: 'Historial',
          ),
          const BottomNavigationBarItem(
            icon: Icon(Icons.person_rounded),
            label: 'Perfil',
          ),
        ],
      ),
    );
  }
}
