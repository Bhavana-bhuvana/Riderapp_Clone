package com.kobha.ourmap;

import android.location.Address;
import android.location.Geocoder;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Looper;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import com.kobha.ourmap.databinding.ActivityCustomerMapBinding;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

public class CustomerMapActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 101;

    private GoogleMap mMap;
    private ActivityCustomerMapBinding binding;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    private DatabaseReference customerLocationRef;
    private GeoFire geoFire;
    private String customerId;

    private Marker pickupMarker, dropMarker, driverMarker;
    private Polyline routePolyline;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCustomerMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            customerId = user.getUid();
            customerLocationRef = FirebaseDatabase.getInstance().getReference("locations/Customers");
            geoFire = new GeoFire(customerLocationRef);
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.customer_map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        setupLocationRequest();
        setupLocationCallback();
        setupButtons();
        listenToAssignedDriver();
    }

    private void setupButtons() {
        binding.btnRequestRide.setOnClickListener(v -> {
            String pickup = binding.etPickup.getText().toString();
            String drop = binding.etDrop.getText().toString();

            if (pickup.isEmpty() || drop.isEmpty()) {
                Toast.makeText(this, "Please enter pickup and drop locations", Toast.LENGTH_SHORT).show();
                return;
            }

            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            try {
                List<Address> pickupList = geocoder.getFromLocationName(pickup, 1);
                List<Address> dropList = geocoder.getFromLocationName(drop, 1);

                if (pickupList.isEmpty() || dropList.isEmpty()) {
                    Toast.makeText(this, "Invalid locations", Toast.LENGTH_SHORT).show();
                    return;
                }

                Address pickupAddress = pickupList.get(0);
                Address dropAddress = dropList.get(0);

                LatLng pickupLatLng = new LatLng(pickupAddress.getLatitude(), pickupAddress.getLongitude());
                LatLng dropLatLng = new LatLng(dropAddress.getLatitude(), dropAddress.getLongitude());

                // Remove previous markers
                if (pickupMarker != null) pickupMarker.remove();
                if (dropMarker != null) dropMarker.remove();
                if (routePolyline != null) routePolyline.remove();

                pickupMarker = mMap.addMarker(new MarkerOptions().position(pickupLatLng).title("Pickup"));
                dropMarker = mMap.addMarker(new MarkerOptions().position(dropLatLng).title("Drop"));
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pickupLatLng, 15));

                sendRideRequestToDriver(pickupLatLng.latitude, pickupLatLng.longitude, dropLatLng.latitude, dropLatLng.longitude);
                drawRouteBetween(pickupLatLng, dropLatLng);

                Toast.makeText(this, "Ride Requested", Toast.LENGTH_SHORT).show();

            } catch (IOException e) {
                Toast.makeText(this, "Geocoding error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        binding.btnUseCurrentLocation.setOnClickListener(v -> {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        LOCATION_PERMISSION_REQUEST_CODE);
                return;
            }

            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            double lat = location.getLatitude();
                            double lng = location.getLongitude();
                            String locationStr = lat + ", " + lng;
                            binding.etPickup.setText(locationStr);

                            LatLng userLatLng = new LatLng(lat, lng);
                            mMap.clear();
                            mMap.addMarker(new MarkerOptions().position(userLatLng).title("You are here"));
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15));

                            if (geoFire != null && customerId != null) {
                                geoFire.setLocation(customerId, new GeoLocation(lat, lng));
                            }
                        }
                    });
        });
    }

    private void sendRideRequestToDriver(double pickupLat, double pickupLng, double dropLat, double dropLng) {
        if (customerId == null) return;

        DatabaseReference requestRef = FirebaseDatabase.getInstance().getReference("rideRequests").child(customerId);

        Map<String, Object> request = new HashMap<>();
        request.put("pickup_lat", pickupLat);
        request.put("pickup_lng", pickupLng);
        request.put("drop_lat", dropLat);
        request.put("drop_lng", dropLng);
        request.put("status", "pending");

        requestRef.setValue(request);
    }

    private void listenToAssignedDriver() {
        if (customerId == null) return;

        DatabaseReference responseRef = FirebaseDatabase.getInstance().getReference("rideResponses").child(customerId);
        responseRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                String driverName = snapshot.child("driverName").getValue(String.class);
                String driverEmail = snapshot.child("driverEmail").getValue(String.class);
                Double driverLat = snapshot.child("driver_lat").getValue(Double.class);
                Double driverLng = snapshot.child("driver_lng").getValue(Double.class);

                if (driverEmail != null && driverLat != null && driverLng != null) {
                    LatLng driverLocation = new LatLng(driverLat, driverLng);

                    if (driverMarker != null) driverMarker.remove();
                    driverMarker = mMap.addMarker(new MarkerOptions().position(driverLocation).title("Driver: " + driverName));

                    Toast.makeText(CustomerMapActivity.this,
                            "Driver assigned: " + driverName + "\nEmail: " + driverEmail,
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void drawRouteBetween(LatLng origin, LatLng destination) {
        String apiKey = getString(R.string.google_maps_key);
        String url = "https://maps.googleapis.com/maps/api/directions/json?origin=" +
                origin.latitude + "," + origin.longitude +
                "&destination=" + destination.latitude + "," + destination.longitude +
                "&key=" + apiKey;

        RequestQueue queue = Volley.newRequestQueue(this);
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        JSONArray routes = response.getJSONArray("routes");
                        if (routes.length() > 0) {
                            JSONObject overviewPolyline = routes.getJSONObject(0).getJSONObject("overview_polyline");
                            String encodedPoints = overviewPolyline.getString("points");

                            List<LatLng> decodedPath = decodePoly(encodedPoints);

                            if (routePolyline != null) routePolyline.remove();
                            routePolyline = mMap.addPolyline(new PolylineOptions()
                                    .addAll(decodedPath)
                                    .width(10)
                                    .color(Color.BLUE)
                                    .geodesic(true));
                        }
                    } catch (JSONException e) {
                        Toast.makeText(this, "Route parsing failed", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> Toast.makeText(this, "Failed to fetch route", Toast.LENGTH_SHORT).show());

        queue.add(request);
    }

    private List<LatLng> decodePoly(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do{b=encoded.charAt(index++)-63;result|=(b & 0x1f) << shift; shift += 5; }
            while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0; result = 0;
            do { b = encoded.charAt(index++) - 63; result |= (b & 0x1f) << shift; shift += 5; }
            while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            poly.add(new LatLng(lat / 1E5, lng / 1E5));
        }

        return poly;
    }

    private void setupLocationRequest() {
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .build();
    }

    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    double lat = location.getLatitude();
                    double lng = location.getLongitude();

                    LatLng userLatLng = new LatLng(lat, lng);
                    mMap.addMarker(new MarkerOptions().position(userLatLng).title("You are here"));
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15));

                    if (geoFire != null && customerId != null) {
                        geoFire.setLocation(customerId, new GeoLocation(lat, lng));
                    }
                }
            }
        };
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        mMap.setMyLocationEnabled(true);
        startLocationUpdates();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }
}



//**********************************************************************
//package com.kobha.ourmap;
//
//import android.location.Address;
//import android.location.Geocoder;
//import android.location.Location;
//import com.google.android.gms.location.LocationRequest;
//
//import androidx.annotation.NonNull;
//import androidx.core.app.ActivityCompat;
//import androidx.fragment.app.FragmentActivity;
//import com.google.android.gms.location.FusedLocationProviderClient;
//import com.google.android.gms.location.LocationCallback;
//import com.google.android.gms.location.LocationResult;
//import com.google.android.gms.location.LocationServices;
//import com.google.android.gms.location.Priority;
//import com.google.android.gms.maps.CameraUpdateFactory;
//import com.google.android.gms.maps.GoogleMap;
//import com.google.android.gms.maps.OnMapReadyCallback;
//import com.google.android.gms.maps.SupportMapFragment;
//import com.google.android.gms.maps.model.LatLng;
//import com.google.android.gms.maps.model.LatLngBounds;
//import com.google.android.gms.maps.model.Marker;
//import com.google.android.gms.maps.model.MarkerOptions;
//import android.os.Bundle;
//import android.os.Looper;
//import android.Manifest;
//import android.content.pm.PackageManager;
//import android.widget.Toast;
//
//import com.firebase.geofire.GeoFire;
//import com.firebase.geofire.GeoLocation;
//import com.google.firebase.auth.FirebaseAuth;
//import com.google.firebase.database.DataSnapshot;
//import com.google.firebase.database.DatabaseError;
//import com.google.firebase.database.DatabaseReference;
//import com.google.firebase.database.FirebaseDatabase;
//import com.google.firebase.database.ValueEventListener;
//import com.kobha.ourmap.databinding.ActivityCustomerMapBinding;
//import com.google.firebase.auth.FirebaseUser;
//
//import java.io.IOException;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Locale;
//import java.util.Map;
//public class CustomerMapActivity extends FragmentActivity implements OnMapReadyCallback {
//
//    private static final int LOCATION_PERMISSION_REQUEST_CODE = 101;
//
//    private GoogleMap mMap;
//    private ActivityCustomerMapBinding binding;
//
//    private FusedLocationProviderClient fusedLocationClient;
//    private LocationRequest locationRequest;
//    private LocationCallback locationCallback;
//
//    private DatabaseReference customerLocationRef;
//    private GeoFire geoFire;
//    private String customerId;
//
//    private Marker pickupMarker;
//    private Marker dropMarker;
//    private Marker driverMarker;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        binding = ActivityCustomerMapBinding.inflate(getLayoutInflater());
//        setContentView(binding.getRoot());
//
//        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
//        if (user != null) {
//            customerId = user.getUid();
//            customerLocationRef = FirebaseDatabase.getInstance().getReference("locations/Customers");
//            geoFire = new GeoFire(customerLocationRef);
//        }
//
//        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
//
//        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
//                .findFragmentById(R.id.customer_map);
//        if (mapFragment != null) {
//            mapFragment.getMapAsync(this);
//        }
//
//        setupLocationRequest();
//        setupLocationCallback();
//        setupButtons();
//        listenToAssignedDriver();
//    }
//
//    private void setupButtons() {
//        binding.btnRequestRide.setOnClickListener(v -> {
//            String pickup = binding.etPickup.getText().toString();
//            String drop = binding.etDrop.getText().toString();
//
//            if (pickup.isEmpty() || drop.isEmpty()) {
//                Toast.makeText(this, "Please enter pickup and drop locations", Toast.LENGTH_SHORT).show();
//                return;
//            }
//
//            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
//            try {
//                List<Address> pickupList = geocoder.getFromLocationName(pickup, 1);
//                List<Address> dropList = geocoder.getFromLocationName(drop, 1);
//
//                if (pickupList.isEmpty() || dropList.isEmpty()) {
//                    Toast.makeText(this, "Invalid locations", Toast.LENGTH_SHORT).show();
//                    return;
//                }
//
//                Address pickupAddress = pickupList.get(0);
//                Address dropAddress = dropList.get(0);
//
//                double pickupLat = pickupAddress.getLatitude();
//                double pickupLng = pickupAddress.getLongitude();
//                double dropLat = dropAddress.getLatitude();
//                double dropLng = dropAddress.getLongitude();
//
//                // Remove previous markers
//                if (pickupMarker != null) pickupMarker.remove();
//                if (dropMarker != null) dropMarker.remove();
//
//                // Add new markers
//                LatLng pickupLatLng = new LatLng(pickupLat, pickupLng);
//                LatLng dropLatLng = new LatLng(dropLat, dropLng);
//                pickupMarker = mMap.addMarker(new MarkerOptions().position(pickupLatLng).title("Pickup Location"));
//                dropMarker = mMap.addMarker(new MarkerOptions().position(dropLatLng).title("Drop Location"));
//
//                // Adjust camera to show both markers
//                LatLngBounds.Builder builder = new LatLngBounds.Builder();
//                builder.include(pickupLatLng);
//                builder.include(dropLatLng);
//                LatLngBounds bounds = builder.build();
//                mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150));
//
//                // Send request to driver
//                sendRideRequestToDriver(pickupLat, pickupLng, dropLat, dropLng);
//                Toast.makeText(this, "Ride Requested", Toast.LENGTH_SHORT).show();
//
//            } catch (IOException e) {
//                Toast.makeText(this, "Geocoding error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
//            }
//        });
//
//    }
//
//    private void sendRideRequestToDriver(double pickupLat, double pickupLng, double dropLat, double dropLng) {
//        if (customerId == null) return;
//
//        DatabaseReference requestRef = FirebaseDatabase.getInstance().getReference("rideRequests").child(customerId);
//
//        Map<String, Object> request = new HashMap<>();
//        request.put("pickup_lat", pickupLat);
//        request.put("pickup_lng", pickupLng);
//        request.put("drop_lat", dropLat);
//        request.put("drop_lng", dropLng);
//        request.put("status", "pending");
//
//        requestRef.setValue(request);
//    }
//
//    private void listenToAssignedDriver() {
//        if (customerId == null) return;
//
//        DatabaseReference responseRef = FirebaseDatabase.getInstance().getReference("rideResponses").child(customerId);
//        responseRef.addValueEventListener(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot snapshot) {
//                if (!snapshot.exists()) return;
//
//                String driverName = snapshot.child("driverName").getValue(String.class);
//                String driverEmail = snapshot.child("driverEmail").getValue(String.class);
//                Double driverLat = snapshot.child("driver_lat").getValue(Double.class);
//                Double driverLng = snapshot.child("driver_lng").getValue(Double.class);
//
//                if (driverEmail != null && driverLat != null && driverLng != null) {
//                    LatLng driverLocation = new LatLng(driverLat, driverLng);
//
//                    if (driverMarker != null) driverMarker.remove();
//                    driverMarker = mMap.addMarker(new MarkerOptions().position(driverLocation).title("Driver: " + driverName));
//
//                    Toast.makeText(CustomerMapActivity.this,
//                            "Driver assigned: " + driverName + "\nEmail: " + driverEmail,
//                            Toast.LENGTH_LONG).show();
//                }
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {
//            }
//        });
//    }
//
//    private void setupLocationRequest() {
//        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
//                .setMinUpdateIntervalMillis(2000)
//                .build();
//    }
//
//    private void setupLocationCallback() {
//        locationCallback = new LocationCallback() {
//            @Override
//            public void onLocationResult(@NonNull LocationResult locationResult) {
//                if (locationResult == null) return;
//
//                for (Location location : locationResult.getLocations()) {
//                    double lat = location.getLatitude();
//                    double lng = location.getLongitude();
//
//                    LatLng userLatLng = new LatLng(lat, lng);
//                    mMap.addMarker(new MarkerOptions().position(userLatLng).title("You are here"));
//                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15));
//
//                    if (geoFire != null && customerId != null) {
//                        geoFire.setLocation(customerId, new GeoLocation(lat, lng));
//                    }
//                }
//            }
//        };
//    }
//
//    private void startLocationUpdates() {
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
//                != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this,
//                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
//                    LOCATION_PERMISSION_REQUEST_CODE);
//            return;
//        }
//        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
//    }
//
//    @Override
//    public void onMapReady(@NonNull GoogleMap googleMap) {
//        mMap = googleMap;
//
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
//                != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this,
//                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
//                    LOCATION_PERMISSION_REQUEST_CODE);
//            return;
//        }
//
//        mMap.setMyLocationEnabled(true);
//        startLocationUpdates();
//    }
//
//    @Override
//    public void onRequestPermissionsResult(int requestCode,
//                                           @NonNull String[] permissions,
//                                           @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
//                grantResults.length > 0 &&
//                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//            startLocationUpdates();
//        }
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        if (fusedLocationClient != null && locationCallback != null) {
//            fusedLocationClient.removeLocationUpdates(locationCallback);
//        }
//    }
//}
//
//*****************************************************************************************************************
/*public class CustomerMapActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 101;

    private GoogleMap mMap;
    private ActivityDriverMapBinding binding;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    private DatabaseReference RequestRide;
    private GeoFire geoFire;
    private String driverId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDriverMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Firebase Setup
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            driverId = user.getUid();
            RequestRide = FirebaseDatabase.getInstance().getReference("locations/Customers");
            geoFire = new GeoFire(RequestRide);
        }

        // FusedLocationProvider Setup
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Map Fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Configure Location Updates
        setupLocationRequest();
        setupLocationCallback();
    }

    private void setupLocationRequest() {
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .build();
    }

    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationResult == null) return;

                for (Location location : locationResult.getLocations()) {
                    double lat = location.getLatitude();
                    double lng = location.getLongitude();

                    // Update location on map
                    LatLng driverLatLng = new LatLng(lat, lng);
                    mMap.clear();
                    mMap.addMarker(new MarkerOptions().position(driverLatLng).title("You're here"));
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(driverLatLng, 15));

                    // Upload to Firebase GeoFire
                    if (geoFire != null && driverId != null) {
                        geoFire.setLocation(driverId, new GeoLocation(lat, lng));
                    }
                }
            }
        };
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        mMap.setMyLocationEnabled(true);
        startLocationUpdates();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }
}*/
/*public class CustomerMapActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ActivityCustomerMapBinding binding;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCustomerMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.customer_map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        setupLocationRequest();
        setupLocationCallback();
        setupButtons();
    }

    private void setupLocationRequest() {
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .build();
    }

    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationResult == null) return;

                for (Location location : locationResult.getLocations()) {
                    LatLng userLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                    mMap.clear();
                    mMap.addMarker(new MarkerOptions().position(userLatLng).title("Your Location"));
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15));

                    // Optional: autofill pickup EditText with current location
                    binding.etPickup.setText(location.getLatitude() + ", " + location.getLongitude());
                }
            }
        };
    }

    private void getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private void setupButtons() {
        binding.btnRequestRide.setOnClickListener(v -> {
            String pickup = binding.etPickup.getText().toString();
            String drop = binding.etDrop.getText().toString();

            if (!pickup.isEmpty() && !drop.isEmpty()) {
                Toast.makeText(this, "Ride Requested\nPickup: " + pickup + "\nDrop: " + drop, Toast.LENGTH_SHORT).show();
                // You can store in Firebase or trigger driver logic here
            } else {
                Toast.makeText(this, "Please enter pickup and drop locations", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        mMap.setMyLocationEnabled(true);
        getCurrentLocation();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }
}*/
//public class CustomerMapActivity extends FragmentActivity implements OnMapReadyCallback {
//
//    private static final int LOCATION_PERMISSION_REQUEST_CODE = 101;
//
//    private GoogleMap mMap;
//    private ActivityCustomerMapBinding binding;
//
//    private FusedLocationProviderClient fusedLocationClient;
//    private LocationRequest locationRequest;
//    private LocationCallback locationCallback;
//
//    private DatabaseReference customerLocationRef;
//    private GeoFire geoFire;
//    private String customerId;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        binding = ActivityCustomerMapBinding.inflate(getLayoutInflater());
//        setContentView(binding.getRoot());
//
//        // Firebase User
//        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
//        if (user != null) {
//            customerId = user.getUid();
//            customerLocationRef = FirebaseDatabase.getInstance().getReference("locations/Customers");
//            geoFire = new GeoFire(customerLocationRef);
//        }
//        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
//
//        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
//                .findFragmentById(R.id.customer_map);
//        if (mapFragment != null) {
//            mapFragment.getMapAsync(this);
//        }
//        setupLocationRequest();
//        setupLocationCallback();
//        setupButtons();
//        // Inside onCreate(), after setupButtons();
//        listenToAssignedDriver();
//    }
//    private void listenToAssignedDriver() {
//        if (customerId == null) return;
//
//        DatabaseReference responseRef = FirebaseDatabase.getInstance().getReference("rideResponses").child(customerId);
//        responseRef.addValueEventListener(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot snapshot) {
//                if (!snapshot.exists()) return;
//
//                String driverEmail = snapshot.child("driverEmail").getValue(String.class);
//                double driverLat = snapshot.child("driver_lat").getValue(Double.class);
//                double driverLng = snapshot.child("driver_lng").getValue(Double.class);
//
//                if (driverEmail != null) {
//                    LatLng driverLocation = new LatLng(driverLat, driverLng);
//                    mMap.addMarker(new MarkerOptions().position(driverLocation).title("Driver: " + driverEmail));
//                    Toast.makeText(CustomerMapActivity.this, "Driver assigned: " + driverEmail, Toast.LENGTH_SHORT).show();
//                }
//            }
//            private void sendRideRequestToDriver(double pickupLat, double pickupLng, double dropLat, double dropLng) {
//                if (customerId == null) return;
//
//                DatabaseReference requestRef = FirebaseDatabase.getInstance().getReference("rideRequests").child(customerId);
//
//                Map<String, Object> request = new HashMap<>();
//                request.put("pickup_lat", pickupLat);
//                request.put("pickup_lng", pickupLng);
//                request.put("drop_lat", dropLat);
//                request.put("drop_lng", dropLng);
//                request.put("status", "pending");
//
//                requestRef.setValue(request);
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {
//            }
//        });
//    }
//    private void setupLocationRequest() {
//        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
//                .setMinUpdateIntervalMillis(2000)
//                .build();
//    }
//
//    private void setupLocationCallback() {
//        locationCallback = new LocationCallback() {
//            @Override
//            public void onLocationResult(@NonNull LocationResult locationResult) {
//                if (locationResult == null) return;
//
//                for (Location location : locationResult.getLocations()) {
//                    double lat = location.getLatitude();
//                    double lng = location.getLongitude();
//
//                    LatLng userLatLng = new LatLng(lat, lng);
//                    mMap.clear();
//                    mMap.addMarker(new MarkerOptions().position(userLatLng).title("You are here"));
//                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15));
//
//                    // Save to Firebase via GeoFire
//                    if (geoFire != null && customerId != null) {
//                        geoFire.setLocation(customerId, new GeoLocation(lat, lng));
//                    }
//                }
//            }
//        };
//    }
//    private void setupButtons() {
//        binding.btnRequestRide.setOnClickListener(v -> {
//            String pickup = binding.etPickup.getText().toString();
//            String drop = binding.etDrop.getText().toString();
//
//            if (!pickup.isEmpty() && !drop.isEmpty()) {
//                Toast.makeText(this, "Ride Requested\nPickup: " + pickup + "\nDrop: " + drop, Toast.LENGTH_SHORT).show();
//                // Ride request logic here if needed
//            } else {
//                Toast.makeText(this, "Please enter pickup and drop locations", Toast.LENGTH_SHORT).show();
//            }
//        });
//
//        binding.btnUseCurrentLocation.setOnClickListener(v -> {
//            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
//                    != PackageManager.PERMISSION_GRANTED) {
//                ActivityCompat.requestPermissions(this,
//                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
//                        LOCATION_PERMISSION_REQUEST_CODE);
//                return;
//            }
//
//            fusedLocationClient.getLastLocation()
//                    .addOnSuccessListener(location -> {
//                        if (location != null) {
//                            double lat = location.getLatitude();
//                            double lng = location.getLongitude();
//                            String locationStr = lat + ", " + lng;
//                            binding.etPickup.setText(locationStr);
//
//                            // Visual update on map
//                            LatLng userLatLng = new LatLng(lat, lng);
//                            mMap.clear();
//                            mMap.addMarker(new MarkerOptions().position(userLatLng).title("You are here"));
//                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15));
//
//                            // Save to Firebase manually too
//                            if (geoFire != null && customerId != null) {
//                                geoFire.setLocation(customerId, new GeoLocation(lat, lng));
//                            }
//                        } else {
//                            Toast.makeText(this, "Couldn't fetch location", Toast.LENGTH_SHORT).show();
//                        }
//                    });
//        });
//    }
//
//    private void startLocationUpdates() {
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
//                != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this,
//                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
//                    LOCATION_PERMISSION_REQUEST_CODE);
//            return;
//        }
//
//        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
//    }
//
//    @Override
//    public void onMapReady(@NonNull GoogleMap googleMap) {
//        mMap = googleMap;
//
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
//                != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this,
//                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
//                    LOCATION_PERMISSION_REQUEST_CODE);
//            return;
//        }
//
//        mMap.setMyLocationEnabled(true);
//        startLocationUpdates();
//    }
//
//    @Override
//    public void onRequestPermissionsResult(int requestCode,
//                                           @NonNull String[] permissions,
//                                           @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
//                grantResults.length > 0 &&
//                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//            startLocationUpdates();
//        }
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        if (fusedLocationClient != null && locationCallback != null) {
//            fusedLocationClient.removeLocationUpdates(locationCallback);
//        }
//    }
//}
//
