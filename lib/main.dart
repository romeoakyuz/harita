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

String formatDuration(int seconds) {
  final duration = Duration(seconds: seconds);
  final hours = duration.inHours;
  final minutes = duration.inMinutes.remainder(60);
  final secs = duration.inSeconds.remainder(60);
  if (hours > 0) {
    return '$hours sa $minutes dk $secs sn';
  } else if (minutes > 0) {
    return '$minutes dk $secs sn';
  } else {
    return '$secs sn';
  }
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

class SavedMarker {
  final String id;
  final String name;
  final String dateStr;
  final double latitude;
  final double longitude;

  SavedMarker({
    required this.id,
    required this.name,
    required this.dateStr,
    required this.latitude,
    required this.longitude,
  });

  LatLng get position => LatLng(latitude, longitude);

  Map<String, dynamic> toJson() => {
        'id': id,
        'name': name,
        'dateStr': dateStr,
        'latitude': latitude,
        'longitude': longitude,
      };

  factory SavedMarker.fromJson(Map<String, dynamic> json) => SavedMarker(
        id: json['id'],
        name: json['name'],
        dateStr: json['dateStr'],
        latitude: (json['latitude'] as num).toDouble(),
        longitude: (json['longitude'] as num).toDouble(),
      );
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
  LatLng? focusTargetLocation;

  void _onRouteSelectedForView(SavedRoute route) {
    setState(() {
      viewingRoute = route;
      focusTargetLocation = route.points.isNotEmpty ? route.points.first : null;
      _selectedIndex = 0;
    });
  }

  void _onMarkerSelectedForView(SavedMarker marker) {
    setState(() {
      focusTargetLocation = marker.position;
      _selectedIndex = 0;
    });
  }

  @override
  Widget build(BuildContext context) {
    final pages = [
      HaritaEkrani(
        viewingRoute: viewingRoute,
        focusTargetLocation: focusTargetLocation,
        onClearViewingRoute: () {
          setState(() {
            viewingRoute = null;
          });
        },
      ),
      AyarlarEkrani(
        onSelectRoute: _onRouteSelectedForView,
        onSelectMarker: _onMarkerSelectedForView,
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
  final LatLng? focusTargetLocation;
  final VoidCallback onClearViewingRoute;

  const HaritaEkrani({
    super.key,
    this.viewingRoute,
    this.focusTargetLocation,
    required this.onClearViewingRoute,
  });

  @override
  State<HaritaEkrani> createState() => _HaritaEkraniState();
}

class _HaritaEkraniState extends State<HaritaEkrani> {
  bool isSatellite = true; // Varsayılan UYDU Görünümü
  bool isRecording = false;
  List<LatLng> routePoints = [];
  List<SavedMarker> savedMarkers = [];
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
    _loadSavedMarkers();
  }

  @override
  void didUpdateWidget(covariant HaritaEkrani oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.focusTargetLocation != null &&
        widget.focusTargetLocation != oldWidget.focusTargetLocation) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        mapController.move(widget.focusTargetLocation!, 16.0);
      });
    }
  }

  Future<void> _loadSavedMarkers() async {
    final prefs = await SharedPreferences.getInstance();
    final List<String> list = prefs.getStringList('saved_markers') ?? [];
    if (mounted) {
      setState(() {
        savedMarkers = list.map((item) => SavedMarker.fromJson(jsonDecode(item))).toList();
      });
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
        if (widget.focusTargetLocation == null) {
          mapController.move(currentLocation, 16.0);
        }
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

  Future<void> _showAddMarkerDialog() async {
    final now = DateTime.now();
    final defaultName = 'İşaret - ${DateFormat('dd.MM.yyyy HH:mm').format(now)}';
    final nameController = TextEditingController(text: defaultName);

    return showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text('Bulunduğun Yeri İşaretle'),
          content: TextField(
            controller: nameController,
            decoration: const InputDecoration(
              labelText: 'İşaret Adı (Örn: Av İzi, Barınak)',
              border: OutlineInputBorder(),
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('İptal'),
            ),
            ElevatedButton(
              onPressed: () async {
                final marker = SavedMarker(
                  id: now.millisecondsSinceEpoch.toString(),
                  name: nameController.text.trim().isEmpty ? defaultName : nameController.text,
                  dateStr: DateFormat('dd.MM.yyyy HH:mm').format(now),
                  latitude: currentLocation.latitude,
                  longitude: currentLocation.longitude,
                );
                final prefs = await SharedPreferences.getInstance();
                final list = prefs.getStringList('saved_markers') ?? [];
                list.add(jsonEncode(marker.toJson()));
                await prefs.setStringList('saved_markers', list);
                
                await _loadSavedMarkers();
                if (mounted) {
                  Navigator.pop(context);
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('Konum başarıyla işaretlendi!')),
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
              Text('Süre: ${formatDuration(duration)}'),
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
                final prefs = await SharedPreferences.getInstance();
                final List<String> list = prefs.getStringList('saved_routes') ?? [];
                list.add(jsonEncode(route.toJson()));
                await prefs.setStringList('saved_routes', list);

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

  Marker _buildFlagMarker(LatLng point, String label, Color color, IconData icon) {
    return Marker(
      point: point,
      width: 90,
      height: 65,
      alignment: Alignment.topCenter,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
            decoration: BoxDecoration(
              color: Colors.black.withOpacity(0.85),
              borderRadius: BorderRadius.circular(4),
              border: Border.all(color: color, width: 1.5),
            ),
            child: Text(
              label,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 10,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
          Icon(icon, color: color, size: 30),
        ],
      ),
    );
  }

  Marker _buildSavedPinMarker(SavedMarker pin) {
    return Marker(
      point: pin.position,
      width: 100,
      height: 60,
      alignment: Alignment.topCenter,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
            decoration: BoxDecoration(
              color: Colors.orange.shade900.withOpacity(0.9),
              borderRadius: BorderRadius.circular(4),
              border: Border.all(color: Colors.white, width: 1),
            ),
            child: Text(
              pin.name,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 9,
                fontWeight: FontWeight.bold,
              ),
              overflow: TextOverflow.ellipsis,
            ),
          ),
          const Icon(Icons.location_on, color: Colors.orangeAccent, size: 28),
        ],
      ),
    );
  }

  @override
  void dispose() {
    positionStream?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final activeRoutePoints = widget.viewingRoute != null
        ? widget.viewingRoute!.points
        : routePoints;

    final List<Marker> allMarkers = [];

    // Mevcut Konum Noktası
    allMarkers.add(
      Marker(
        point: currentLocation,
        width: 24,
        height: 24,
        child: Container(
          decoration: BoxDecoration(
            color: Colors.blueAccent,
            shape: BoxShape.circle,
            border: Border.all(color: Colors.white, width: 3),
            boxShadow: const [BoxShadow(color: Colors.black26, blurRadius: 6)],
          ),
        ),
      ),
    );

    // Kayıtlı İşaretler (Pinler)
    for (var pin in savedMarkers) {
      allMarkers.add(_buildSavedPinMarker(pin));
    }

    // Başlangıç ve Bitiş Bayrakları
    if (activeRoutePoints.isNotEmpty) {
      allMarkers.add(
        _buildFlagMarker(
          activeRoutePoints.first,
          'Başlangıç',
          Colors.greenAccent,
          Icons.flag,
        ),
      );
      if (activeRoutePoints.length > 1) {
        allMarkers.add(
          _buildFlagMarker(
            activeRoutePoints.last,
            'Bitiş',
            Colors.redAccent,
            Icons.sports_score,
          ),
        );
      }
    }

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
                    points: activeRoutePoints,
                    strokeWidth: 5.0,
                    color: Colors.redAccent, // Her zaman KIRMIZI ÇİZGİ
                  ),
                ],
              ),
              MarkerLayer(markers: allMarkers),
            ],
          ),

          // Kayıtlı Rota İnceleme Bilgi Kutusu
          if (widget.viewingRoute != null)
            Positioned(
              top: 16,
              left: 16,
              right: 16,
              child: Card(
                color: Colors.black.withOpacity(0.85),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                  side: const BorderSide(color: Colors.redAccent, width: 1.5),
                ),
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Expanded(
                            child: Text(
                              widget.viewingRoute!.name,
                              style: const TextStyle(
                                color: Colors.white,
                                fontSize: 16,
                                fontWeight: FontWeight.bold,
                              ),
                              overflow: TextOverflow.ellipsis,
                            ),
                          ),
                          IconButton(
                            icon: const Icon(Icons.close, color: Colors.white70),
                            onPressed: widget.onClearViewingRoute,
                            constraints: const BoxConstraints(),
                            padding: EdgeInsets.zero,
                          ),
                        ],
                      ),
                      const Divider(color: Colors.grey, height: 12),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text(
                            '📅 ${widget.viewingRoute!.dateStr}',
                            style: const TextStyle(color: Colors.white70, fontSize: 12),
                          ),
                          Text(
                            '⏱️ ${formatDuration(widget.viewingRoute!.durationSeconds)}',
                            style: const TextStyle(color: Colors.white70, fontSize: 12),
                          ),
                          Text(
                            '📏 ${(widget.viewingRoute!.distanceMeters / 1000).toStringAsFixed(2)} km',
                            style: const TextStyle(
                              color: Colors.greenAccent,
                              fontSize: 13,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ],
                      ),
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
      floatingActionButton: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          FloatingActionButton.extended(
            heroTag: 'btnMarker',
            onPressed: _showAddMarkerDialog,
            backgroundColor: Colors.orange.shade800,
            foregroundColor: Colors.white,
            icon: const Icon(Icons.add_location_alt),
            label: const Text('Konumu İşaretle'),
          ),
          const SizedBox(height: 12),
          FloatingActionButton.extended(
            heroTag: 'btnRoute',
            onPressed: toggleRecording,
            backgroundColor: isRecording ? Colors.red.shade700 : Colors.green.shade700,
            foregroundColor: Colors.white,
            icon: Icon(isRecording ? Icons.stop : Icons.play_arrow),
            label: Text(
              isRecording ? 'Kaydı Durdur & Kaydet' : 'Rotayı Başlat',
              style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
            ),
          ),
        ],
      ),
      floatingActionButtonLocation: FloatingActionButtonLocation.centerFloat,
    );
  }
}

class AyarlarEkrani extends StatelessWidget {
  final Function(SavedRoute) onSelectRoute;
  final Function(SavedMarker) onSelectMarker;

  const AyarlarEkrani({
    super.key,
    required this.onSelectRoute,
    required this.onSelectMarker,
  });

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
            leading: const Icon(Icons.push_pin, color: Colors.orange),
            title: const Text('İşaretlerim', style: TextStyle(fontWeight: FontWeight.bold)),
            subtitle: const Text('Kaydedilmiş özel konum ve pin işaretleri'),
            trailing: const Icon(Icons.arrow_forward_ios, size: 16),
            onTap: () {
              Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (context) => IsaretlerimEkrani(onSelectMarker: onSelectMarker),
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
                      'Tarih: ${route.dateStr}\nSüre: ${formatDuration(route.durationSeconds)} | Mesafe: ${(route.distanceMeters / 1000).toStringAsFixed(2)} km',
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

class IsaretlerimEkrani extends StatefulWidget {
  final Function(SavedMarker) onSelectMarker;

  const IsaretlerimEkrani({super.key, required this.onSelectMarker});

  @override
  State<IsaretlerimEkrani> createState() => _IsaretlerimEkraniState();
}

class _IsaretlerimEkraniState extends State<IsaretlerimEkrani> {
  List<SavedMarker> savedMarkers = [];

  @override
  void initState() {
    super.initState();
    _loadMarkers();
  }

  Future<void> _loadMarkers() async {
    final prefs = await SharedPreferences.getInstance();
    final List<String> list = prefs.getStringList('saved_markers') ?? [];
    setState(() {
      savedMarkers = list
          .map((item) => SavedMarker.fromJson(jsonDecode(item)))
          .toList()
          .reversed
          .toList();
    });
  }

  Future<void> _deleteMarker(int index) async {
    final prefs = await SharedPreferences.getInstance();
    final List<String> list = prefs.getStringList('saved_markers') ?? [];
    int realIndex = list.length - 1 - index;
    if (realIndex >= 0 && realIndex < list.length) {
      list.removeAt(realIndex);
      await prefs.setStringList('saved_markers', list);
      _loadMarkers();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('İşaretlerim'),
        backgroundColor: Colors.green.shade800,
        foregroundColor: Colors.white,
      ),
      body: savedMarkers.isEmpty
          ? const Center(
              child: Text(
                'Henüz kaydedilmiş bir işaret bulunmuyor.',
                style: TextStyle(color: Colors.grey, fontSize: 16),
              ),
            )
          : ListView.builder(
              itemCount: savedMarkers.length,
              itemBuilder: (context, index) {
                final marker = savedMarkers[index];
                return Card(
                  margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                  child: ListTile(
                    leading: const CircleAvatar(
                      backgroundColor: Colors.orange,
                      child: Icon(Icons.location_on, color: Colors.white),
                    ),
                    title: Text(marker.name, style: const TextStyle(fontWeight: FontWeight.bold)),
                    subtitle: Text('Tarih: ${marker.dateStr}\nKonum: ${marker.latitude.toStringAsFixed(5)}, ${marker.longitude.toStringAsFixed(5)}'),
                    isThreeLine: true,
                    trailing: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        IconButton(
                          icon: const Icon(Icons.center_focus_strong, color: Colors.blue),
                          tooltip: 'Konuma Odaklan',
                          onPressed: () {
                            widget.onSelectMarker(marker);
                            Navigator.pop(context);
                          },
                        ),
                        IconButton(
                          icon: const Icon(Icons.delete, color: Colors.red),
                          tooltip: 'Sil',
                          onPressed: () => _deleteMarker(index),
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
