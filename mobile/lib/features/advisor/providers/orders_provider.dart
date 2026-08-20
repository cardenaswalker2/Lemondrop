import 'dart:async';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:audioplayers/audioplayers.dart';
import '../../../core/models/models.dart';
import '../../../core/storage/preferences.dart';
import '../../auth/providers/auth_provider.dart';

// Catalog Provider
final catalogProvider = FutureProvider<Map<String, List<dynamic>>>((ref) async {
  final client = ref.read(apiClientProvider);
  final res = await client.dio.get('/api/mobile/catalog');
  if (res.statusCode == 200) {
    final data = res.data as Map<String, dynamic>;
    final products = (data['products'] as List).map((e) => Product.fromJson(e)).toList();
    final flavors = (data['flavors'] as List).map((e) => Flavor.fromJson(e)).toList();
    final addons = (data['addons'] as List).map((e) => Addon.fromJson(e)).toList();
    return {
      'products': products,
      'flavors': flavors,
      'addons': addons,
    };
  }
  throw Exception('No se pudo cargar el catálogo.');
});

// Stats Provider
final statsProvider = StateNotifierProvider<StatsNotifier, AsyncValue<Stats>>((ref) {
  return StatsNotifier(ref);
});

class StatsNotifier extends StateNotifier<AsyncValue<Stats>> {
  final Ref _ref;
  StatsNotifier(this._ref) : super(const AsyncValue.loading()) {
    fetchStats();
  }

  Future<void> fetchStats() async {
    try {
      final client = _ref.read(apiClientProvider);
      final res = await client.dio.get('/api/mobile/stats');
      if (res.statusCode == 200) {
        state = AsyncValue.data(Stats.fromJson(res.data));
      }
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }
}

// Active Orders State (List of Orders)
final activeOrdersProvider = StateNotifierProvider<ActiveOrdersNotifier, AsyncValue<List<Order>>>((ref) {
  return ActiveOrdersNotifier(ref);
});

class ActiveOrdersNotifier extends StateNotifier<AsyncValue<List<Order>>> {
  final Ref _ref;
  Timer? _pollingTimer;
  DateTime _lastUpdateTime = DateTime.now().subtract(const Duration(days: 1));
  final AudioPlayer _audioPlayer = AudioPlayer();

  ActiveOrdersNotifier(this._ref) : super(const Duration(seconds: 0) == const Duration(seconds: 1) ? const AsyncValue.data([]) : const AsyncValue.loading()) {
    fetchActiveOrders();
    _startPolling();
  }

  Future<void> fetchActiveOrders() async {
    try {
      final client = _ref.read(apiClientProvider);
      final res = await client.dio.get('/api/mobile/orders');
      if (res.statusCode == 200) {
        final List raw = res.data['content'] as List? ?? [];
        final orders = raw.map((e) => Order.fromJson(e)).toList();
        state = AsyncValue.data(orders);
        _lastUpdateTime = DateTime.now();
      }
    } catch (e, stack) {
      state = AsyncValue.error(e, stack);
    }
  }

  void _startPolling() {
    _pollingTimer?.cancel();
    _pollingTimer = Timer.periodic(const Duration(seconds: 5), (timer) async {
      // Avoid fetching if not logged in or in error state
      if (_ref.read(authProvider).user == null) return;

      try {
        final client = _ref.read(apiClientProvider);
        final timestamp = _lastUpdateTime.toIso8601String().split('.').first;
        final res = await client.dio.get('/api/mobile/orders/updates', queryParameters: {
          'since': timestamp,
        });

        if (res.statusCode == 200) {
          final List updatesRaw = res.data as List? ?? [];
          if (updatesRaw.isNotEmpty) {
            final updates = updatesRaw.map((e) => Order.fromJson(e)).toList();
            _lastUpdateTime = DateTime.now();

            // Merge updates with current active list
            final currentList = state.value ?? [];
            final updatedMap = {for (var o in currentList) o.id: o};

            bool hasNewReceived = false;

            for (var u in updates) {
              // If order is completed or cancelled, remove from active list
              if (u.status == OrderStatus.delivered || u.status == OrderStatus.cancelled) {
                updatedMap.remove(u.id);
              } else {
                // If it's a new RECEIVED order that wasn't in list before
                if (u.status == OrderStatus.received && !updatedMap.containsKey(u.id)) {
                  hasNewReceived = true;
                }
                updatedMap[u.id] = u;
              }
            }

            state = AsyncValue.data(updatedMap.values.toList()
              ..sort((a, b) => b.createdAt.compareTo(a.createdAt)));

            // Trigger alarms if new order arrived
            if (hasNewReceived) {
              _triggerAlert();
            }

            // Refresh stats
            _ref.read(statsProvider.notifier).fetchStats();
          }
        }
      } catch (_) {
        // Suppress background errors to keep UI responsive
      }
    });
  }

  static const _channel = MethodChannel('com.lemondrop.lemondrop_mobile/notification');

  Future<void> _triggerAlert() async {
    try {
      if (AppPreferences.soundEnabled || AppPreferences.vibrationEnabled) {
        await _channel.invokeMethod('playNotificationSoundAndVibrate', {
          'sound': AppPreferences.soundEnabled,
          'vibrate': AppPreferences.vibrationEnabled,
        });
        return;
      }
    } catch (_) {
      // Fallback to standard player/haptics if native call fails (e.g. non-Android platforms)
    }

    if (AppPreferences.vibrationEnabled) {
      HapticFeedback.vibrate();
    }
    if (AppPreferences.soundEnabled) {
      try {
        await _audioPlayer.play(UrlSource('https://assets.mixkit.co/active_storage/sfx/2869/2869-600.wav'));
      } catch (_) {
        // Fallback silently
      }
    }
  }

  Future<bool> updateStatus(String orderId, OrderStatus newStatus, {String notes = ''}) async {
    try {
      final client = _ref.read(apiClientProvider);
      final res = await client.dio.post('/api/mobile/orders/$orderId/status', data: {
        'status': newStatus.toJson(),
        'notes': notes,
      });

      if (res.statusCode == 200) {
        final updatedOrder = Order.fromJson(res.data);
        final currentList = state.value ?? [];

        if (newStatus == OrderStatus.delivered || newStatus == OrderStatus.cancelled) {
          state = AsyncValue.data(currentList.where((o) => o.id != orderId).toList());
        } else {
          state = AsyncValue.data(currentList.map((o) => o.id == orderId ? updatedOrder : o).toList());
        }

        // Refresh stats
        _ref.read(statsProvider.notifier).fetchStats();
        return true;
      }
    } catch (_) {}
    return false;
  }

  Future<bool> editOrder(String orderId, List<Map<String, dynamic>> items, String reason) async {
    try {
      final client = _ref.read(apiClientProvider);
      final res = await client.dio.post('/api/mobile/orders/$orderId/edit', data: {
        'items': items,
        'reason': reason,
      });

      if (res.statusCode == 200) {
        final updatedOrder = Order.fromJson(res.data);
        final currentList = state.value ?? [];
        state = AsyncValue.data(currentList.map((o) => o.id == orderId ? updatedOrder : o).toList());
        _ref.read(statsProvider.notifier).fetchStats();
        return true;
      }
    } catch (_) {}
    return false;
  }

  @override
  void dispose() {
    _pollingTimer?.cancel();
    _audioPlayer.dispose();
    super.dispose();
  }
}
