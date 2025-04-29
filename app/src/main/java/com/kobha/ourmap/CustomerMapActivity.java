package com.kobha.ourmap;

import android.location.Location;
import com.google.android.gms.location.LocationRequest;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import android.os.Bundle;
import android.os.Looper;
import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.kobha.ourmap.databinding.ActivityCustomerMapBinding;
import com.kobha.ourmap.databinding.ActivityDriverMapBinding;
import com.google.firebase.auth.FirebaseUser;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCustomerMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Firebase User
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

                    LatLng userLatLng = new LatLng(lat, lng);
                    mMap.clear();
                    mMap.addMarker(new MarkerOptions().position(userLatLng).title("You are here"));
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15));

                    // Save to Firebase via GeoFire
                    if (geoFire != null && customerId != null) {
                        geoFire.setLocation(customerId, new GeoLocation(lat, lng));
                    }
                }
            }
        };
    }
    private void setupButtons() {
        binding.btnRequestRide.setOnClickListener(v -> {
            String pickup = binding.etPickup.getText().toString();
            String drop = binding.etDrop.getText().toString();

            if (!pickup.isEmpty() && !drop.isEmpty()) {
                Toast.makeText(this, "Ride Requested\nPickup: " + pickup + "\nDrop: " + drop, Toast.LENGTH_SHORT).show();
                // Ride request logic here if needed
            } else {
                Toast.makeText(this, "Please enter pickup and drop locations", Toast.LENGTH_SHORT).show();
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

                            // Visual update on map
                            LatLng userLatLng = new LatLng(lat, lng);
                            mMap.clear();
                            mMap.addMarker(new MarkerOptions().position(userLatLng).title("You are here"));
                            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15));

                            // Save to Firebase manually too
                            if (geoFire != null && customerId != null) {
                                geoFire.setLocation(customerId, new GeoLocation(lat, lng));
                            }
                        } else {
                            Toast.makeText(this, "Couldn't fetch location", Toast.LENGTH_SHORT).show();
                        }
                    });
        });
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

