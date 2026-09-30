import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:geolocator/geolocator.dart';

void main() {
  runApp(const AvHaritasiApp());
}

class AvHaritasiApp extends StatelessWidget {
  const AvHaritasiApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Av Rotası Kaydedici',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.green),
        useMaterial3: true,
      ),
      home: const HaritaEkrani(),
    );
  }
}

class HaritaEkrani extends StatefulWidget {
  const HaritaEkrani({super.key});

  @override
  State<HaritaEkrani> createState() => _HaritaEkraniState();
}

class _HaritaEkraniState extends State<HaritaEkrani> {
  bool isSatellite = false;
  bool isRecording = false;
  List<LatLng> routePoints = [];
  double totalDistanceMeters = 0;
  StreamSubscription<Position>? positionStream;
  final MapController mapController = MapController();
  LatLng currentLocation = const LatLng(39.92077, 32.85411);
  final Distance distanceCalculator = const Distance();

  @override
  void initState() {
    super.initState();
    _checkPermissions();
  }

  Future<void> _checkPermissions() async {
    bool serviceEnabled = await Geolocator.isLocationServiceEnabled();
    if (!serviceEnabled) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Lütfen GPS / Konum servisini açın.')),
        );
      }
      return;
    }

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
      setState(() {
        isRecording = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Rota tamamlandı. Toplam Mesafe: ${(totalDistanceMeters / 1000).toStringAsFixed(2)} km',
          ),
        ),
      );
    } else {
      setState(() {
        isRecording = true;
        routePoints.clear();
        totalDistanceMeters = 0;
      });

      positionStream = Geolocator.getPositionStream(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.high,
          distanceFilter: 5,
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

  void _recenterMap() {
    mapController.move(currentLocation, 16.0);
  }

  @override
  void dispose() {
    positionStream?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Av Rotası Kaydedici'),
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
                    points: routePoints,
                    strokeWidth: 5.0,
                    color: Colors.redAccent,
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
                          BoxShadow(
                            color: Colors.black26,
                            blurRadius: 6,
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
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
                      onPressed: _recenterMap,
                      tooltip: 'Mevcut Konuma Git',
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
          isRecording ? 'Kaydı Durdur' : 'Rotayı Başlat',
          style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
        ),
      ),
      floatingActionButtonLocation: FloatingActionButtonLocation.centerFloat,
    );
  }
}
