import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:geolocator/geolocator.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:intl/intl.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const AvHaritasiApp());
}

class SavedRoute {
  final String id;
  final String name;
  final String dateStr;
  final double distanceMeters;
  final int durationSeconds;
  final List<LatLng> points;

  SavedRoute({
    required this.id,
    required this.name,
    required this.dateStr,
    required this.distanceMeters,
    required this.durationSeconds,
    required this.points,
  });

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'dateStr': dateStr,
        'distanceMeters': distanceMeters,
        'durationSeconds': durationSeconds,
        'points': points.map((p) => {'lat': p.latitude, 'lng': p.longitude}).toList(),
      };

  factory SavedRoute.fromJson(Map<String, dynamic> json) {
    var ptsRaw = json['points'] as List;
    List<LatLng> pts = ptsRaw.map((item) => LatLng(item['lat'], item['lng'])).toList();
    return SavedRoute(
      id: json['id'],
      name: json['name'],
      dateStr: json['dateStr'],
      distanceMeters: (json['distanceMeters'] as num).toDouble(),
      durationSeconds: json['durationSeconds'] ?? 0,
      points: pts,
    );
  }
}

class AvHaritasiApp extends StatelessWidget {
  const AvHaritasiApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Av & Doğa Haritası',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.green),
        useMaterial3: true,
      ),
      home: const AnaSayfa(),
    );
  }
}

class AnaSayfa extends StatefulWidget {
  const AnaSayfa({super.key});

  @override
  State<AnaSayfa> createState() => _AnaSayfaState();
}

class _AnaSayfaState extends State<AnaSayfa> {
  int _selectedIndex = 0;
  SavedRoute? viewingRoute;

  void _onRouteSelectedForView(SavedRoute route) {
    setState(() {
      viewingRoute = route;
      _selectedIndex = 0; // Harita sekmesine geç
    });
  }

  @override
  Widget build(BuildContext context) {
    final pages = [
      HaritaEkrani(
        viewingRoute: viewingRoute,
        onClearViewingRoute: () {
          setState(() {
            viewingRoute = null;
          });
        },
      ),
      AyarlarEkrani(
        onSelectRoute: _onRouteSelectedForView,
      ),
    ];

    return Scaffold(
      body: pages[_selectedIndex],
      bottomNavigationBar: NavigationBar(
        selectedIndex: _selectedIndex,
        onDestinationSelected: (index) {
          setState(() {
            _selectedIndex = index;
          });
        },
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.map_outlined),
            selectedIcon: Icon(Icons.map),
            label: 'Harita',
          ),
          NavigationDestination(
            icon: Icon(Icons.settings_outlined),
            selectedIcon: Icon(Icons.settings),
            label: 'Ayarlar',
          ),
        ],
      ),
    );
  }
}

class HaritaEkrani extends StatefulWidget {
  final SavedRoute? viewingRoute;
  final VoidCallback onClearViewingRoute;

  const HaritaEkrani({
    super.key,
    this.viewingRoute,
    required this.onClearViewingRoute,
  });

  @override
  State<HaritaEkrani> createState() => _HaritaEkraniState();
}

class _HaritaEkraniState extends State<HaritaEkrani> {
  bool isSatellite = true; // Varsayılan UYDU GÖRÜNÜMÜ
  bool isRecording = false;
  List<LatLng> routePoints = [];
  double totalDistanceMeters = 0;
  DateTime? startTime;
  StreamSubscription<Position>? positionStream;
  final MapController mapController = MapController();
  LatLng currentLocation = const LatLng(39.92077, 32.85411);
  final Distance distanceCalculator = const Distance();

  @override
  void initState() {
    super.initState();
    _checkPermissions();
  }

  @override
  void didUpdateWidget(covariant HaritaEkrani oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.viewingRoute != null && widget.viewingRoute != oldWidget.viewingRoute) {
      if (widget.viewingRoute!.points.isNotEmpty) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          mapController.move(widget.viewingRoute!.points.first, 15.0);
        });
      }
    }
  }

  Future<void> _checkPermissions() async {
    bool serviceEnabled = await Geolocator.isLocationServiceEnabled();
    if (!serviceEnabled) return;

    LocationPermission permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
    }

    if (permission == LocationPermission.whileInUse || permission == LocationPermission.always) {
      Position pos = await Geolocator.getCurrentPosition(
        desiredAccuracy: LocationAccuracy.high,
      );
      if (mounted) {
        setState(() {
          currentLocation = LatLng(pos.latitude, pos.longitude);
        });
        mapController.move(currentLocation, 16.0);
      }
    }
  }

  void toggleRecording() {
    if (isRecording) {
      positionStream?.cancel();
      final duration = startTime != null ? DateTime.now().difference(startTime!).inSeconds : 0;
      final pointsToSave = List<LatLng>.from(routePoints);
      final distanceToSave = totalDistanceMeters;

      setState(() {
        isRecording = false;
      });

      if (pointsToSave.isNotEmpty) {
        _showSaveDialog(pointsToSave, distanceToSave, duration);
      }
    } else {
      if (widget.viewingRoute != null) {
        widget.onClearViewingRoute();
      }

      setState(() {
        isRecording = true;
        routePoints.clear();
        totalDistanceMeters = 0;
        startTime = DateTime.now();
      });

      positionStream = Geolocator.getPositionStream(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.high,
          distanceFilter: 3,
        ),
      ).listen((Position position) {
        LatLng newPoint = LatLng(position.latitude, position.longitude);

        if (mounted) {
          setState(() {
            if (routePoints.isNotEmpty) {
              totalDistanceMeters += distanceCalculator.as(
                LengthUnit.Meter,
                routePoints.last,
                newPoint,
              );
            }
            currentLocation = newPoint;
            routePoints.add(newPoint);
          });
          mapController.move(currentLocation, mapController.camera.zoom);
        }
      });
    }
  }

  Future<void> _showSaveDialog(List<LatLng> points, double distance, int duration) async {
    final now = DateTime.now();
    final defaultName = 'Av Rotası - ${DateFormat('dd.MM.yyyy HH:mm').format(now)}';
    final nameController = TextEditingController(text: defaultName);

    return showDialog(
      context: context,
      barrierDismissible: false,
      builder: (context) {
        return AlertDialog(
          title: const Text('Rotayı Kaydet'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Mesafe: ${(distance / 1000).toStringAsFixed(2)} km'),
              const SizedBox(height: 12),
              TextField(
                controller: nameController,
                decoration: const InputDecoration(
                  labelText: 'Rota Adı',
                  border: OutlineInputBorder(),
                ),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('İptal'),
            ),
            ElevatedButton(
              onPressed: () async {
                final route = SavedRoute(
                  id: now.millisecondsSinceEpoch.toString(),
                  name: nameController.text.trim().isEmpty ? defaultName : nameController.text,
                  dateStr: DateFormat('dd.MM.yyyy HH:mm').format(now),
                  distanceMeters: distance,
                  durationSeconds: duration,
                  points: points,
                );
                await _saveRouteToStorage(route);
                if (mounted) {
                  Navigator.pop(context);
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Rota başarıyla kaydedildi!')),
                  );
                }
              },
              child: const Text('Kaydet'),
            ),
          ],
        );
      },
    );
  }

  Future<void> _saveRouteToStorage(SavedRoute route) async {
    final prefs = await SharedPreferences.getInstance();
    final List<String> list = prefs.getStringList('saved_routes') ?? [];
    list.add(jsonEncode(route.toJson()));
    await prefs.setStringList('saved_routes', list);
  }

  @override
  void dispose() {
    positionStream?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final activeRouteToShow = widget.viewingRoute != null
        ? widget.viewingRoute!.points
        : routePoints;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Av & Doğa Haritası'),
        backgroundColor: Colors.green.shade800,
        foregroundColor: Colors.white,
        actions: [
          IconButton(
            tooltip: isSatellite ? 'Normal Harita' : 'Uydu Haritası',
            icon: Icon(isSatellite ? Icons.map : Icons.satellite_alt),
            onPressed: () {
              setState(() {
                isSatellite = !isSatellite;
              });
            },
          ),
        ],
      ),
      body: Stack(
        children: [
          FlutterMap(
            mapController: mapController,
            options: MapOptions(
              initialCenter: currentLocation,
              initialZoom: 15.0,
            ),
            children: [
              TileLayer(
                urlTemplate: isSatellite
                    ? 'https://mt1.google.com/vt/lyrs=s&x={x}&y={y}&z={z}'
                    : 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                userAgentPackageName: 'com.example.harita',
              ),
              PolylineLayer(
                polylines: [
                  Polyline(
                    points: activeRouteToShow,
                    strokeWidth: 5.0,
                    color: widget.viewingRoute != null ? Colors.blue : Colors.redAccent,
                  ),
                ],
              ),
              MarkerLayer(
                markers: [
                  Marker(
                    point: currentLocation,
                    width: 24,
                    height: 24,
                    child: Container(
                      decoration: BoxDecoration(
                        color: Colors.blueAccent,
                        shape: BoxShape.circle,
                        border: Border.all(color: Colors.white, width: 3),
                        boxShadow: const [
                          BoxShadow(color: Colors.black26, blurRadius: 6),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
          if (widget.viewingRoute != null)
            Positioned(
              top: 16,
              left: 16,
              right: 16,
              child: Card(
                color: Colors.blue.shade900,
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                  child: Row(
                    children: [
                      const Icon(Icons.route, color: Colors.white),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          'Görüntülenen: ${widget.viewingRoute!.name}',
                          style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.close, color: Colors.white),
                        onPressed: widget.onClearViewingRoute,
                      )
                    ],
                  ),
                ),
              ),
            )
          else
            Positioned(
              top: 16,
              left: 16,
              right: 16,
              child: Card(
                color: Colors.white.withOpacity(0.9),
                elevation: 4,
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            isRecording ? '● Rota Kaydediliyor...' : '○ Kayıt Bekliyor',
                            style: TextStyle(
                              fontWeight: FontWeight.bold,
                              color: isRecording ? Colors.red : Colors.grey.shade700,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            'Mesafe: ${(totalDistanceMeters / 1000).toStringAsFixed(2)} km (${totalDistanceMeters.toStringAsFixed(0)} m)',
                            style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500),
                          ),
                        ],
                      ),
                      IconButton(
                        icon: const Icon(Icons.my_location, color: Colors.green),
                        onPressed: () => mapController.move(currentLocation, 16.0),
                        tooltip: 'Konuma Git',
                      ),
                    ],
                  ),
                ),
              ),
            ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: toggleRecording,
        backgroundColor: isRecording ? Colors.red.shade700 : Colors.green.shade700,
        foregroundColor: Colors.white,
        icon: Icon(isRecording ? Icons.stop : Icons.play_arrow),
        label: Text(
          isRecording ? 'Kaydı Durdur & Kaydet' : 'Rotayı Başlat',
          style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
        ),
      ),
      floatingActionButtonLocation: FloatingActionButtonLocation.centerFloat,
    );
  }
}

class AyarlarEkrani extends StatelessWidget {
  final Function(SavedRoute) onSelectRoute;

  const AyarlarEkrani({super.key, required this.onSelectRoute});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Ayarlar'),
        backgroundColor: Colors.green.shade800,
        foregroundColor: Colors.white,
      ),
      body: ListView(
        children: [
          const SizedBox(height: 8),
          ListTile(
            leading: const Icon(Icons.history, color: Colors.green),
            title: const Text('Rotalarım', style: TextStyle(fontWeight: FontWeight.bold)),
            subtitle: const Text('Kaydedilmiş tüm av ve doğa rotaları'),
            trailing: const Icon(Icons.arrow_forward_ios, size: 16),
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (context) => RotalarimEkrani(onSelectRoute: onSelectRoute),
                ),
              );
            },
          ),
          const Divider(),
          ListTile(
            leading: const Icon(Icons.tune, color: Colors.green),
            title: const Text('Kayıt Ayarları', style: TextStyle(fontWeight: FontWeight.bold)),
            subtitle: const Text('GPS hassasiyeti ve kayıt seçenekleri'),
            trailing: const Icon(Icons.arrow_forward_ios, size: 16),
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (context) => const KayitAyarlariEkrani(),
                ),
              );
            },
          ),
          const Divider(),
        ],
      ),
    );
  }
}

class RotalarimEkrani extends StatefulWidget {
  final Function(SavedRoute) onSelectRoute;

  const RotalarimEkrani({super.key, required this.onSelectRoute});

  @override
  State<RotalarimEkrani> createState() => _RotalarimEkraniState();
}

class _RotalarimEkraniState extends State<RotalarimEkrani> {
  List<SavedRoute> savedRoutes = [];

  @override
  void initState() {
    super.initState();
    _loadRoutes();
  }

  Future<void> _loadRoutes() async {
    final prefs = await SharedPreferences.getInstance();
    final List<String> list = prefs.getStringList('saved_routes') ?? [];
    setState(() {
      savedRoutes = list
          .map((item) => SavedRoute.fromJson(jsonDecode(item)))
          .toList()
          .reversed
          .toList();
    });
  }

  Future<void> _deleteRoute(int index) async {
    final prefs = await SharedPreferences.getInstance();
    final List<String> list = prefs.getStringList('saved_routes') ?? [];
    int realIndex = list.length - 1 - index;
    if (realIndex >= 0 && realIndex < list.length) {
      list.removeAt(realIndex);
      await prefs.setStringList('saved_routes', list);
      _loadRoutes();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Rotalarım'),
        backgroundColor: Colors.green.shade800,
        foregroundColor: Colors.white,
      ),
      body: savedRoutes.isEmpty
          ? const Center(
              child: Text(
                'Henüz kaydedilmiş bir rota bulunmuyor.',
                style: TextStyle(color: Colors.grey, fontSize: 16),
              ),
            )
          : ListView.builder(
              itemCount: savedRoutes.length,
              itemBuilder: (context, index) {
                final route = savedRoutes[index];
                return Card(
                  margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                  child: ListTile(
                    leading: const CircleAvatar(
                      backgroundColor: Colors.green,
                      child: Icon(Icons.alt_route, color: Colors.white),
                    ),
                    title: Text(route.name, style: const TextStyle(fontWeight: FontWeight.bold)),
                    subtitle: Text(
                      'Tarih: ${route.dateStr}\nMesafe: ${(route.distanceMeters / 1000).toStringAsFixed(2)} km',
                    ),
                    isThreeLine: true,
                    trailing: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        IconButton(
                          icon: const Icon(Icons.map, color: Colors.blue),
                          tooltip: 'Haritada Göster',
                          onPressed: () {
                            widget.onSelectRoute(route);
                            Navigator.pop(context);
                          },
                        ),
                        IconButton(
                          icon: const Icon(Icons.delete, color: Colors.red),
                          tooltip: 'Sil',
                          onPressed: () => _deleteRoute(index),
                        ),
                      ],
                    ),
                  ),
                );
              },
            ),
    );
  }
}

class KayitAyarlariEkrani extends StatelessWidget {
  const KayitAyarlariEkrani({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Kayıt Ayarları'),
        backgroundColor: Colors.green.shade800,
        foregroundColor: Colors.white,
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Card(
          elevation: 2,
          child: Padding(
            padding: const EdgeInsets.all(16.0),
            child: Row(
              children: const [
                Icon(Icons.info_outline, color: Colors.orange, size: 32),
                SizedBox(width: 16),
                Expanded(
                  child: Text(
                    'Kayıt hassasiyeti, GPS güncelleme sıklığı ve harita seçenekleri yakında buraya eklenecektir.',
                    style: TextStyle(fontSize: 15),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
