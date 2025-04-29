customer side
Perfect! Let's update both files for:

- ✅ Showing **pickup/drop** on maps
- ✅ **Sending** ride requests from customer to Firebase
- ✅ **Listening** to ride requests on driver side
- ✅ Showing **driver email** to customer
- ✅ **Live updates** of driver's location to customer

---

## 🚕 Updated `CustomerMapActivity.java` (key changes)

### ✅ Show pickup/drop
### ✅ Send request to Firebase
### ✅ Listen to driver updates

Below is the **modified version only with relevant new logic** added to your existing `CustomerMapActivity`:

```java
// Inside onCreate(), after setupButtons();
listenToAssignedDriver();
```

```java
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
```

### 👂 Listen to driver assignment and updates:
```java
private void listenToAssignedDriver() {
    if (customerId == null) return;

    DatabaseReference responseRef = FirebaseDatabase.getInstance().getReference("rideResponses").child(customerId);
    responseRef.addValueEventListener(new ValueEventListener() {
        @Override
        public void onDataChange(@NonNull DataSnapshot snapshot) {
            if (!snapshot.exists()) return;

            String driverEmail = snapshot.child("driverEmail").getValue(String.class);
            double driverLat = snapshot.child("driver_lat").getValue(Double.class);
            double driverLng = snapshot.child("driver_lng").getValue(Double.class);

            if (driverEmail != null) {
                LatLng driverLocation = new LatLng(driverLat, driverLng);
                mMap.addMarker(new MarkerOptions().position(driverLocation).title("Driver: " + driverEmail));
                Toast.makeText(CustomerMapActivity.this, "Driver assigned: " + driverEmail, Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onCancelled(@NonNull DatabaseError error) {
        }
    });
}
```

---

## 🚗 Updated `DriverMapActivity.java`

### ✅ Listen to ride requests
### ✅ Update assigned driver info + location to customer

Add in `onCreate()`:

```java
listenForRideRequests();
```

Add this method:
```java
private void listenForRideRequests() {
    DatabaseReference rideRequestsRef = FirebaseDatabase.getInstance().getReference("rideRequests");
    rideRequestsRef.addChildEventListener(new ChildEventListener() {
        @Override
        public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
            String customerId = snapshot.getKey();
            double pickupLat = snapshot.child("pickup_lat").getValue(Double.class);
            double pickupLng = snapshot.child("pickup_lng").getValue(Double.class);
            double dropLat = snapshot.child("drop_lat").getValue(Double.class);
            double dropLng = snapshot.child("drop_lng").getValue(Double.class);

            Toast.makeText(DriverMapActivity.this, "New ride request!", Toast.LENGTH_SHORT).show();

            // For demo: auto-assign this driver
            assignDriverToCustomer(customerId);
        }

        @Override public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
        @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
        @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
        @Override public void onCancelled(@NonNull DatabaseError error) {}
    });
}
```

And this:
```java
private void assignDriverToCustomer(String customerId) {
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    if (user == null) return;

    String driverEmail = user.getEmail();

    fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
        if (location != null) {
            DatabaseReference responseRef = FirebaseDatabase.getInstance().getReference("rideResponses").child(customerId);

            Map<String, Object> response = new HashMap<>();
            response.put("driverEmail", driverEmail);
            response.put("driver_lat", location.getLatitude());
            response.put("driver_lng", location.getLongitude());

            responseRef.setValue(response);
        }
    });
}
```

---

### ✅ Firebase Database Structure
```
rideRequests/
  {customerId}/
    pickup_lat, pickup_lng, drop_lat, drop_lng, status

rideResponses/
  {customerId}/
    driverEmail, driver_lat, driver_lng
```
Driver side
Certainly! Let's focus on the **Driver Side** now.

In this case, we want to:

1. **Listen to incoming ride requests** in real-time.
2. **Allow drivers to accept the ride**.
3. **Update driver location live** in Firebase when the ride is accepted.
4. **Notify the customer** when the ride is accepted (by updating Firebase).

---

### 1. **Listen to Incoming Ride Requests**
We need to listen for any ride requests from customers. This will trigger a notification or update when a customer requests a ride.

### 2. **Update Driver Location Live**
Once the ride is accepted, the driver’s location should be updated in real-time for the customer.

---

### **Updated `DriverMapActivity.java`**

Here’s how we’ll modify the code:

```java
public class DriverMapActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 101;
    private GoogleMap mMap;
    private ActivityDriverMapBinding binding;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private DatabaseReference driverLocationRef;
    private GeoFire geoFire;
    private String driverId;
    private String assignedCustomerId;  // To track assigned customer

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDriverMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Firebase Setup
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            driverId = user.getUid();
            driverLocationRef = FirebaseDatabase.getInstance().getReference("locations/Drivers");
            geoFire = new GeoFire(driverLocationRef);
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Map Fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Listen for incoming ride requests
        listenForRideRequests();
        
        // Configure Location Updates
        setupLocationRequest();
        setupLocationCallback();
    }

    private void listenForRideRequests() {
        // Listen to rideRequests from Firebase
        DatabaseReference rideRequestsRef = FirebaseDatabase.getInstance().getReference("rideRequests");
        rideRequestsRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                String customerId = snapshot.getKey();
                double pickupLat = snapshot.child("pickup_lat").getValue(Double.class);
                double pickupLng = snapshot.child("pickup_lng").getValue(Double.class);
                double dropLat = snapshot.child("drop_lat").getValue(Double.class);
                double dropLng = snapshot.child("drop_lng").getValue(Double.class);
                
                // For simplicity, let's automatically accept the first ride request.
                // You could add logic to show a "Request" button and accept/reject the ride.
                acceptRideRequest(customerId, pickupLat, pickupLng, dropLat, dropLng);
            }

            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void acceptRideRequest(String customerId, double pickupLat, double pickupLng, double dropLat, double dropLng) {
        // Logic to accept a ride request

        assignedCustomerId = customerId;

        // Update driver's location in Firebase and mark as "accepted"
        FirebaseDatabase.getInstance().getReference("rideResponses")
                .child(customerId)
                .child("status")
                .setValue("accepted");

        // Add driver location to Firebase (this will be updated every time)
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference("rideResponses")
                        .child(customerId);
                driverRef.child("driver_lat").setValue(location.getLatitude());
                driverRef.child("driver_lng").setValue(location.getLongitude());
            }
        });

        // Show a toast indicating the ride has been accepted
        Toast.makeText(this, "Ride request accepted. Heading to pickup.", Toast.LENGTH_SHORT).show();
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

                    // Update driver's live location in Firebase for customer
                    if (assignedCustomerId != null) {
                        FirebaseDatabase.getInstance().getReference("rideResponses")
                                .child(assignedCustomerId)
                                .child("driver_lat")
                                .setValue(lat);

                        FirebaseDatabase.getInstance().getReference("rideResponses")
                                .child(assignedCustomerId)
                                .child("driver_lng")
                                .setValue(lng);
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
```

### Key Changes in `DriverMapActivity.java`:

1. **Listening for Ride Requests**: The app listens for ride requests using Firebase `ChildEventListener`. Whenever a new ride request is added, it triggers the `acceptRideRequest()` method.
   
2. **Updating Driver’s Location**: The driver’s location is continuously updated in Firebase under the `rideResponses` node. This helps the customer see the live location of the driver.

3. **Ride Acceptance**: When a ride is accepted, the status is updated to "accepted" in Firebase. The driver’s current position is updated in the `rideResponses` node for the assigned customer.

4. **GeoFire**: We use GeoFire to store the driver’s location in Firebase for real-time tracking.

---

### Firebase Structure (Updated)
Here’s how your Firebase structure should look:

```
rideRequests/
  {customerId}/
    pickup_lat, pickup_lng, drop_lat, drop_lng, status (pending/accepted)

rideResponses/
  {customerId}/
    driverEmail, driver_lat, driver_lng, status (accepted)
```

### Summary of Flow:
1. **Customer Side**: 
   - Sends a ride request to Firebase with pickup and drop locations.
Extra :
To show **both the pickup and drop locations on the map** and send this ride request to the **logged-in driver**, you need to update your `CustomerMapActivity` in two main ways:

---

### ✅ **1. Show Pickup & Drop on the Map**

Update your `btnRequestRide.setOnClickListener` to:
- Parse pickup/drop as coordinates.
- Add **two markers** on the map.
- Optionally draw a polyline between them.

```java
binding.btnRequestRide.setOnClickListener(v -> {
    String pickup = binding.etPickup.getText().toString();
    String drop = binding.etDrop.getText().toString();

    if (!pickup.isEmpty() && !drop.isEmpty()) {
        try {
            String[] pickupCoords = pickup.split(",");
            String[] dropCoords = drop.split(",");
            double pickupLat = Double.parseDouble(pickupCoords[0].trim());
            double pickupLng = Double.parseDouble(pickupCoords[1].trim());
            double dropLat = Double.parseDouble(dropCoords[0].trim());
            double dropLng = Double.parseDouble(dropCoords[1].trim());

            LatLng pickupLatLng = new LatLng(pickupLat, pickupLng);
            LatLng dropLatLng = new LatLng(dropLat, dropLng);

            mMap.clear();
            mMap.addMarker(new MarkerOptions().position(pickupLatLng).title("Pickup"));
            mMap.addMarker(new MarkerOptions().position(dropLatLng).title("Drop"));
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pickupLatLng, 13));

            // Optional: draw route line
            // mMap.addPolyline(new PolylineOptions().add(pickupLatLng, dropLatLng).color(Color.BLUE));

            Toast.makeText(this, "Ride Requested", Toast.LENGTH_SHORT).show();

            sendRideRequestToDriver(pickupLat, pickupLng, dropLat, dropLng);

        } catch (Exception e) {
            Toast.makeText(this, "Invalid coordinates", Toast.LENGTH_SHORT).show();
        }
    } else {
        Toast.makeText(this, "Please enter pickup and drop locations", Toast.LENGTH_SHORT).show();
    }
});
```

---

### ✅ **2. Send Ride Request to Firebase for Driver**

Add this method:

```java
private void sendRideRequestToDriver(double pickupLat, double pickupLng, double dropLat, double dropLng) {
    if (customerId == null) return;

    DatabaseReference rideRequestsRef = FirebaseDatabase.getInstance().getReference("rideRequests").child(customerId);

    Map<String, Object> request = new HashMap<>();
    request.put("pickup_lat", pickupLat);
    request.put("pickup_lng", pickupLng);
    request.put("drop_lat", dropLat);
    request.put("drop_lng", dropLng);
    request.put("status", "pending");

    rideRequestsRef.setValue(request);
}
```

Then, in your **DriverMapActivity**, listen to this path (`rideRequests/`) for changes and accept or reject requests.

---

Would you like me to help you implement the listener on the **driver's side** too?
   
2. **Driver Side**:
   - Listens for incoming ride requests.
   - Accepts the ride and updates the location in real-time for the customer to see.

