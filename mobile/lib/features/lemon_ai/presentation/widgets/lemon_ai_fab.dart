import 'package:flutter/material.dart';
import '../../../../core/theme/app_theme.dart';
import 'lemon_ai_sheet.dart';

class LemonAiFab extends StatelessWidget {
  final VoidCallback? onNavigateToTracking;

  const LemonAiFab({
    super.key,
    this.onNavigateToTracking,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(28),
        boxShadow: [
          BoxShadow(
            color: AppTheme.primaryLemon.withOpacity(0.4),
            blurRadius: 16,
            spreadRadius: 2,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: FloatingActionButton.extended(
        onPressed: () {
          LemonAiSheet.show(context, onNavigateToTracking: onNavigateToTracking);
        },
        backgroundColor: AppTheme.darkBg,
        foregroundColor: Colors.white,
        elevation: 0,
        highlightElevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(28),
          side: const BorderSide(color: AppTheme.primaryLemon, width: 2),
        ),
        icon: Stack(
          clipBehavior: Clip.none,
          children: [
            Container(
              width: 32,
              height: 32,
              decoration: const BoxDecoration(
                color: AppTheme.primaryLemon,
                shape: BoxShape.circle,
              ),
              child: const Center(
                child: Text('🍋', style: TextStyle(fontSize: 18)),
              ),
            ),
            Positioned(
              top: -2,
              right: -2,
              child: Container(
                width: 10,
                height: 10,
                decoration: BoxDecoration(
                  color: const Color(0xFF10B981),
                  shape: BoxShape.circle,
                  border: Border.all(color: AppTheme.darkBg, width: 2),
                ),
              ),
            ),
          ],
        ),
        label: const Row(
          children: [
            Text(
              'Lemon AI',
              style: TextStyle(
                fontWeight: FontWeight.w900,
                fontSize: 14,
                letterSpacing: 0.5,
              ),
            ),
            SizedBox(width: 4),
            Text('✨', style: TextStyle(fontSize: 12)),
          ],
        ),
      ),
    );
  }
}
