import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/storage/preferences.dart';
import '../../auth/providers/auth_provider.dart';

class ProfileScreen extends ConsumerStatefulWidget {
  const ProfileScreen({super.key});

  @override
  ConsumerState<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends ConsumerState<ProfileScreen> {
  late bool _soundEnabled;
  late bool _vibrationEnabled;
  late bool _keepScreenOn;
  final _urlController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _soundEnabled = AppPreferences.soundEnabled;
    _vibrationEnabled = AppPreferences.vibrationEnabled;
    _keepScreenOn = AppPreferences.keepScreenOn;
    _urlController.text = AppPreferences.baseUrl;
  }

  @override
  void dispose() {
    _urlController.dispose();
    super.dispose();
  }

  void _saveSettings() {
    AppPreferences.soundEnabled = _soundEnabled;
    AppPreferences.vibrationEnabled = _vibrationEnabled;
    AppPreferences.keepScreenOn = _keepScreenOn;
    AppPreferences.baseUrl = _urlController.text.trim();

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: const Text('Configuraciones guardadas correctamente.'),
        backgroundColor: AppTheme.darkGreen,
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authProvider);
    final user = authState.user;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Mi Perfil'),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // User card profile
            if (user != null)
              Card(
                elevation: 1,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(20),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(20.0),
                  child: Row(
                    children: [
                      CircleAvatar(
                        radius: 36,
                        backgroundColor: AppTheme.mintGreen,
                        child: Text(
                          user.name.substring(0, 1).toUpperCase(),
                          style: const TextStyle(
                            fontSize: 28,
                            fontWeight: FontWeight.bold,
                            color: AppTheme.darkGreen,
                          ),
                        ),
                      ),
                      const SizedBox(width: 16),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              user.name,
                              style: const TextStyle(
                                fontSize: 20,
                                fontWeight: FontWeight.bold,
                                color: AppTheme.darkBg,
                              ),
                            ),
                            const SizedBox(height: 4),
                            Text(
                              '@${user.username}',
                              style: const TextStyle(
                                fontSize: 14,
                                color: AppTheme.textGray,
                              ),
                            ),
                            const SizedBox(height: 8),
                            Container(
                              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                              decoration: BoxDecoration(
                                color: user.role == 'ADMIN'
                                    ? AppTheme.primaryLemon
                                    : AppTheme.softGreen,
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: Text(
                                user.role == 'ADMIN' ? 'ADMINISTRADOR' : 'ASESOR',
                                style: TextStyle(
                                  fontSize: 11,
                                  fontWeight: FontWeight.bold,
                                  color: user.role == 'ADMIN'
                                      ? AppTheme.darkBg
                                      : AppTheme.darkGreen,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            const SizedBox(height: 24),

            // Operation Mode Configuration
            const Text(
              'Ajustes de Operación',
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.bold,
                color: AppTheme.darkBg,
              ),
            ),
            const SizedBox(height: 12),
            Card(
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 8.0),
                child: Column(
                  children: [
                    SwitchListTile(
                      activeColor: AppTheme.darkGreen,
                      title: const Text('🔔 Sonido de alerta'),
                      subtitle: const Text('Emitir sonido al recibir nuevos pedidos'),
                      value: _soundEnabled,
                      onChanged: (val) {
                        setState(() => _soundEnabled = val);
                        _saveSettings();
                      },
                    ),
                    const Divider(height: 1, indent: 16, endIndent: 16),
                    SwitchListTile(
                      activeColor: AppTheme.darkGreen,
                      title: const Text('📳 Vibración corta'),
                      subtitle: const Text('Vibrar al recibir notificaciones'),
                      value: _vibrationEnabled,
                      onChanged: (val) {
                        setState(() => _vibrationEnabled = val);
                        _saveSettings();
                      },
                    ),
                    const Divider(height: 1, indent: 16, endIndent: 16),
                    SwitchListTile(
                      activeColor: AppTheme.darkGreen,
                      title: const Text('🔥 Modo Operación'),
                      subtitle: const Text('Mantener la pantalla encendida permanentemente'),
                      value: _keepScreenOn,
                      onChanged: (val) {
                        setState(() => _keepScreenOn = val);
                        _saveSettings();
                      },
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 24),

            // Backend Config Configuration
            const Text(
              'Configuración de Servidor',
              style: TextStyle(
                fontSize: 16,
                fontWeight: FontWeight.bold,
                color: AppTheme.darkBg,
              ),
            ),
            const SizedBox(height: 12),
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    const Text(
                      'URL del Backend:',
                      style: TextStyle(fontSize: 13, color: AppTheme.textGray),
                    ),
                    const SizedBox(height: 8),
                    TextField(
                      controller: _urlController,
                      decoration: const InputDecoration(
                        hintText: 'http://192.168.x.x:8080',
                        prefixIcon: Icon(Icons.dns_outlined),
                      ),
                    ),
                    const SizedBox(height: 16),
                    ElevatedButton(
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppTheme.darkGreen,
                        foregroundColor: Colors.white,
                      ),
                      onPressed: _saveSettings,
                      child: const Text('Guardar Configuración'),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 32),

            // Logout button
            ElevatedButton(
              style: ElevatedButton.styleFrom(
                backgroundColor: AppTheme.strawberryRed,
                foregroundColor: Colors.white,
              ),
              onPressed: () {
                showDialog(
                  context: context,
                  builder: (ctx) => AlertDialog(
                    title: const Text('¿Cerrar Sesión?'),
                    content: const Text('¿Seguro que deseas salir de la Central de Operaciones?'),
                    actions: [
                      TextButton(
                        child: const Text('Cancelar', style: TextStyle(color: AppTheme.textGray)),
                        onPressed: () => Navigator.pop(ctx),
                      ),
                      TextButton(
                        child: const Text('Cerrar Sesión', style: TextStyle(color: AppTheme.strawberryRed)),
                        onPressed: () {
                          Navigator.pop(ctx);
                          ref.read(authProvider.notifier).logout();
                        },
                      ),
                    ],
                  ),
                );
              },
              child: const Text('CERRAR SESIÓN'),
            ),
          ],
        ),
      ),
    );
  }
}
