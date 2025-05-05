package com.kobha.ourmap;

import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.Manifest;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.location.*;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import com.kobha.ourmap.databinding.ActivityDriverMapBinding;

import java.util.HashMap;
import java.util.Map;

public class DriverMapActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private ActivityDriverMapBinding binding;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;

    private String driverId;
    private GeoFire geoFire;

    private boolean rideAccepted = false;
    private String assignedCustomerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDriverMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        driverId = user.getUid();
        DatabaseReference driverLocationRef = FirebaseDatabase.getInstance().getReference("locations/Drivers");
        geoFire = new GeoFire(driverLocationRef);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        setupLocationRequest();
        setupLocationCallback();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.driver_map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        listenToRideRequests();
    }

    private void setupLocationRequest() {
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
                .setMinUpdateIntervalMillis(1500)
                .build();
    }

    private void setupLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
               // if (locationResult == null) return;

                for (Location location : locationResult.getLocations()) {
                    double lat = location.getLatitude();
                    double lng = location.getLongitude();

                    LatLng latLng = new LatLng(lat, lng);
                    mMap.clear();
                    mMap.addMarker(new MarkerOptions().position(latLng).title("Driver Location"));
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));

                    geoFire.setLocation(driverId, new GeoLocation(lat, lng));

                    if (rideAccepted && assignedCustomerId != null) {
                        updateDriverLocationForCustomer(lat, lng);
                    }
                }
            }
        };
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
            return;
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private void listenToRideRequests() {
        DatabaseReference rideRequestsRef = FirebaseDatabase.getInstance().getReference("rideRequests");
        Query pendingRidesQuery = rideRequestsRef.orderByChild("status").equalTo("pending");

        pendingRidesQuery.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                if (rideAccepted) return;

                String customerId = snapshot.getKey();
                Double pickupLat = snapshot.child("pickup_lat").getValue(Double.class);
                Double pickupLng = snapshot.child("pickup_lng").getValue(Double.class);
                Double dropLat = snapshot.child("drop_lat").getValue(Double.class);
                Double dropLng = snapshot.child("drop_lng").getValue(Double.class);

                if (pickupLat != null && pickupLng != null && dropLat != null && dropLng != null) {
                    assignedCustomerId = customerId;

                    LatLng pickup = new LatLng(pickupLat, pickupLng);
                    LatLng drop = new LatLng(dropLat, dropLng);

                    mMap.clear();
                    mMap.addMarker(new MarkerOptions().position(pickup).title("Pickup Location").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
                    mMap.addMarker(new MarkerOptions().position(drop).title("Drop Location").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

                    binding.btnAcceptRide.setVisibility(View.VISIBLE);
                    binding.btnAcceptRide.setOnClickListener(v -> acceptRideWithTransaction(customerId));
                }
            }

            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(DriverMapActivity.this, "Failed to load ride requests.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void acceptRideWithTransaction(String customerId) {
        DatabaseReference requestRef = FirebaseDatabase.getInstance().getReference("rideRequests").child(customerId);
        requestRef.child("status").runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                String currentStatus = currentData.getValue(String.class);
                if ("pending".equals(currentStatus)) {
                    currentData.setValue("accepted");
                    return Transaction.success(currentData);
                }
                return Transaction.abort();
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed, @Nullable DataSnapshot currentData) {
                if (committed) {
                    proceedWithRideAcceptance(customerId);
                } else {
                    Toast.makeText(DriverMapActivity.this, "Ride already accepted or failed", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void proceedWithRideAcceptance(String customerId) {
        rideAccepted = true;
        assignedCustomerId = customerId;

        DatabaseReference responseRef = FirebaseDatabase.getInstance().getReference("rideResponses").child(customerId);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user != null) {
            String email = user.getEmail();
            String name = user.getDisplayName() != null ? user.getDisplayName() : "Driver";

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    double driverLat = location.getLatitude();
                    double driverLng = location.getLongitude();

                    Map<String, Object> response = new HashMap<>();
                    response.put("driverEmail", email);
                    response.put("driverName", name);
                    response.put("driver_lat", driverLat);
                    response.put("driver_lng", driverLng);
                    response.put("status", "accepted");

                    responseRef.setValue(response);
                    Toast.makeText(this, "Ride accepted. Moving to pickup...", Toast.LENGTH_SHORT).show();
                }
            });
        }

        binding.btnAcceptRide.setVisibility(View.GONE);
    }

    private void updateDriverLocationForCustomer(double lat, double lng) {
        DatabaseReference responseRef = FirebaseDatabase.getInstance().getReference("rideResponses").child(assignedCustomerId);
        responseRef.child("driver_lat").setValue(lat);
        responseRef.child("driver_lng").setValue(lng);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 101);
            return;
        }

        mMap.setMyLocationEnabled(true);
        startLocationUpdates();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }
}

//public class DriverMapActivity extends FragmentActivity implements OnMapReadyCallback {
//    private static final int LOCATION_PERMISSION_REQUEST_CODE = 101;
//
//    private GoogleMap mMap;
//    private ActivityDriverMapBinding binding;
//    private FusedLocationProviderClient fusedLocationClient;
//    private LocationRequest locationRequest;
//    private LocationCallback locationCallback;
//    private DatabaseReference driverAvailableRef;
//    GeoFire geoFire;private String driverId;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        binding = ActivityDriverMapBinding.inflate(getLayoutInflater());
//        setContentView(binding.getRoot());
//
//        // Firebase Setup
//        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
//        if (user != null) {
//            driverId = user.getUid();
//            driverAvailableRef = FirebaseDatabase.getInstance().getReference("locations/Drivers");
//            geoFire = new GeoFire(driverAvailableRef);
//        }
//
//        // FusedLocationProvider Setup
//        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
//
//        // Map Fragment
//        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
//                .findFragmentById(R.id.map);
//        if (mapFragment != null) {
//            mapFragment.getMapAsync(this);
//        }
//        listenForRideRequests();
//
//        // Configure Location Updates
//        setupLocationRequest();
//        setupLocationCallback();
//    }
//    private void setupLocationRequest() {
//        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
//                .setMinUpdateIntervalMillis(2000)
//                .build();
//    }
//    private void assignDriverToCustomer(String customerId) {
//        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
//        if (user == null) return;
//
//        String driverEmail = user.getEmail();
//
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//            // TODO: Consider calling
//            //    ActivityCompat#requestPermissions
//            // here to request the missing permissions, and then overriding
//            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
//            //                                          int[] grantResults)
//            // to handle the case where the user grants the permission. See the documentation
//            // for ActivityCompat#requestPermissions for more details.
//            return;
//        }
//        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
//            if (location != null) {
//                DatabaseReference responseRef = FirebaseDatabase.getInstance().getReference("rideResponses").child(customerId);
//
//                Map<String, Object> response = new HashMap<>();
//                response.put("driverEmail", driverEmail);
//                response.put("driver_lat", location.getLatitude());
//                response.put("driver_lng", location.getLongitude());
//
//                responseRef.setValue(response);
//            }
//        });
//    }
//    private void listenForRideRequests() {
//        DatabaseReference rideRequestsRef = FirebaseDatabase.getInstance().getReference("rideRequests");
//        rideRequestsRef.addChildEventListener(new ChildEventListener() {
//            @Override
//            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
//                String customerId = snapshot.getKey();
//                double pickupLat = snapshot.child("pickup_lat").getValue(Double.class);
//                double pickupLng = snapshot.child("pickup_lng").getValue(Double.class);
//                double dropLat = snapshot.child("drop_lat").getValue(Double.class);
//                double dropLng = snapshot.child("drop_lng").getValue(Double.class);
//
//                Toast.makeText(DriverMapActivity.this, "New ride request!", Toast.LENGTH_SHORT).show();
//
//                // For demo: auto-assign this driver
//                assignDriverToCustomer(customerId);
//            }
//
//            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
//            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
//            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
//
//            /**
//             * This method will be triggered in the event that this listener either failed at the server, or
//             * is removed as a result of the security and Firebase rules. For more information on securing
//             * your data, see: <a href="https://firebase.google.com/docs/database/security/quickstart"
//             * target="_blank"> Security Quickstart</a>
//             *
//             * @param error A description of the error that occurred
//             */
//
//            @Override public void onCancelled(@NonNull DatabaseError error) {}
//        });
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
//                    // Update location on map
//                    LatLng driverLatLng = new LatLng(lat, lng);
//                    mMap.clear();
//                    mMap.addMarker(new MarkerOptions().position(driverLatLng).title("You're here"));
//                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(driverLatLng, 15));
//
//                    // Upload to Firebase GeoFire
//                    if (geoFire != null && driverId != null) {
//                        geoFire.setLocation(driverId, new GeoLocation(lat, lng));
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
//    @Override
//    public void onRequestPermissionsResult(int requestCode,
//                                           @NonNull String[] permissions,
//                                           @NonNull int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//
//        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
//                grantResults.length > 0 &&
//                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//            startLocationUpdates();
//        }
//    }
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        if (fusedLocationClient != null && locationCallback != null) {
//            fusedLocationClient.removeLocationUpdates(locationCallback);
//        }
//    }
//}
