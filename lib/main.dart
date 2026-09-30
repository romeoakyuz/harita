import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:geolocator/geolocator.dart';

void main() {
  runApp(const AvHaritasiApp());
}

class AvHaritasiApp extends StatelessWidget {
  const AvHaritasiApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      theme: ThemeData(primarySwatch: Colors.green),
      home: const HaritaEkrani(),
    );
  }
}

class HaritaEkrani extends StatefulWidget {
  const HaritaEkrani({Key? key}) : super(key: key);

  @override
  State<HaritaEkrani> createState() => _HaritaEkraniState();
}

class _HaritaEkraniState extends State<HaritaEkrani> {
  bool isSatellite = false;
  bool isRecording = false;
  List<LatLng> routePoints = [];
  StreamSubscription<Position>? positionStream;
  final MapController mapController = MapController();
  LatLng currentLocation = const LatLng(39.92077, 32.85411);

  @override
  void initState() {
    super.initState();
    _checkPermissions();
  }

  Future<void> _checkPermissions() async {
    bool serviceEnabled = await Geolocator.isLocationServiceEnabled();
    if (!serviceEnabled) return;

    LocationPermission permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
    }

    if (permission == LocationPermission.whileInUse || permission == LocationPermission.always) {
      Position pos = await Geolocator.getCurrentPosition();
      setState(() {
        currentLocation = LatLng(pos.latitude, pos.longitude);
      });
      mapController.move(currentLocation, 15.0);
    }
  }

  void toggleRecording() {
    if (isRecording) {
      positionStream?.cancel();
      setState(() {
        isRecording = false;
      });
    } else {
      setState(() {
        isRecording = true;
        routePoints.clear();
      });
      
      positionStream = Geolocator.getPositionStream(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.high,
          distanceFilter: 5,
        ),
      ).listen((Position position) {
        setState(() {
          currentLocation = LatLng(position.latitude, position.longitude);
          routePoints.add(currentLocation);
        });
        mapController.move(currentLocation, mapController.camera.zoom);
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Av Rotasi Kaydedici'),
        actions: [
          IconButton(
            icon: Icon(isSatellite ? Icons.map : Icons.satellite),
            onPressed: () {
              setState(() {
                isSatellite = !isSatellite;
              });
            },
          )
        ],
      ),
      body: FlutterMap(
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
                strokeWidth: 4.0,
                color: Colors.red,
              ),
            ],
          ),
          MarkerLayer(
            markers: [
              Marker(
                point: currentLocation,
                width: 20,
                height: 20,
                child: const Icon(Icons.my_location, color: Colors.blue, size: 20),
              ),
            ],
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: toggleRecording,
        backgroundColor: isRecording ? Colors.red : Colors.green,
        icon: Icon(isRecording ? Icons.stop : Icons.play_arrow),
        label: Text(isRecording ? 'Kaydi Bitir' : 'Rotayi Kaydet'),
      ),
      floatingActionButtonLocation: FloatingActionButtonLocation.centerFloat,
    );
  }
}
