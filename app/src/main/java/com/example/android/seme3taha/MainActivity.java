package com.example.android.seme3taha;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.facebook.FacebookSdk;
import com.facebook.share.model.ShareLinkContent;
import com.facebook.share.model.SharePhoto;
import com.facebook.share.model.SharePhotoContent;
import com.facebook.share.widget.ShareButton;
import com.facebook.share.widget.ShareDialog;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;


//TODO send the location to a webserver (DONE) which will send the GCM notifications to other nearby/to-be-nearby phones
//TODO add GCM functionality to receive notifications about nearby places
//TODO enable users to register and have the option to check up on their friends/family to know if they're safe or not

public class MainActivity extends ActionBarActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener, OnMapReadyCallback {

    //Default value set to Cairo, Egypt (will be overriden on app launch anyway)
    static double latitude = 30.048865;
    static double longitude = 31.235290;

    //Boolean to indicate whether a bomb was heard or not and update the map accordingly
    static boolean bombHeard = false;
    //Boolean that will just indicate whether the map is loaded for the first time or not
    static boolean firstStart = true;
    //String that will hold the address of the current/bomb location
    String addressString = "somewhere on earth";
    //String that will hold the timestamp of the current/bomb time
    String timestamp = "once upon a time, sometime long ago in the past";

    TextView latTextView;
    TextView longTextView;
    TextView addressTextView;
    TextView timestampTextView;

    //mGoogleApiClient is responsible for handling connections related to Google Play Services APIs
    private GoogleApiClient mGoogleApiClient;

    //locationRequest object is responsible for handling the location request settings (refresh period and accuracy)
    LocationRequest locationRequest;

    //REQUEST_CHECK_SETTINGS is used for the dialog that will be shown if the Location Settings need to be adjusted
    static final int REQUEST_CHECK_SETTINGS = 1;

    //mLastLocation is used to store the last known location
    Location mLastLocation;

    //integer to indicate the view of the map (0 = street, 1 = satellite)
    int mapView = 0;
    private GoogleMap mMap;
    MapFragment mapFragment;

    //Bitmap that will hold the Map view
    static Bitmap image;

    //Facebook ShareDialog
    ShareDialog shareDialog;

    //Progress dialog to be shown whenever there's loading being done
    ProgressDialog progressDialog;

    //List of nearby danger zones
    List<LocationEvent> nearbyEvents;

    //SimpleDateFormat that will format all the timestamps to a specific format (h:mm a - dd MMM, yyyy)
    SimpleDateFormat simpleDateFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Initialize the Facebook SDK before executing any other operations,
        //especially, if you're using Facebook UI elements.
        FacebookSdk.sdkInitialize(getApplicationContext());

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Initialize the progress dialog and show it
        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("");
        progressDialog.setMessage("Loading, please wait...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        //Initialize the SimpleDateFormat that will format the timestamps
        simpleDateFormat = new SimpleDateFormat("h:mm a - dd MMM, yyyy");

        //Initialize the mGoogleApiClient object if it's null, and make sure to pass LocationServices API parameter
        if(mGoogleApiClient ==  null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }

        //Build and initialize the LocationSettingsRequest/locationRequest objects and verify the device meets the required settings to get a GPS location
        buildLocationSettingsRequest();

        //Initialize the textviews
        latTextView = (TextView) findViewById(R.id.lat_text_view);
        longTextView = (TextView) findViewById(R.id.long_text_view);
        addressTextView = (TextView) findViewById(R.id.address_text_view);
        timestampTextView = (TextView) findViewById(R.id.timestamp);

        mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        //Initialize the Facebook ShareDialog
        shareDialog = new ShareDialog(this);

        //Fetch nearby events from the server
        fetchNearbyEvents();

        //Hide the progress dialog (TODO should be dismissed when all above functions have finished, even background ones)
        progressDialog.dismiss();
    }

    private void buildLocationSettingsRequest() {

        //Initialize the locationRequest object and set the preferred interval to 1 seconds, and same for fastest interval
        //Also, use the HIGH_ACCURACY parameter to get a more precise location
        locationRequest = new LocationRequest();
        locationRequest.setInterval(1000);
        locationRequest.setFastestInterval(1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        //Check whether the device's settings are enough to get a GPS position
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);

        final PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, builder.build());

        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(LocationSettingsResult result) {
                final Status status = result.getStatus();
                final LocationSettingsStates locationStates = result.getLocationSettingsStates();
                final int statusCode = status.getStatusCode();
                if(statusCode == LocationSettingsStatusCodes.SUCCESS)
                {
                    //Get the last known GPS location
                    mLastLocation = LocationServices.FusedLocationApi.getLastLocation(
                            mGoogleApiClient);
                    if (mLastLocation != null) {
                        latitude = mLastLocation.getLatitude();
                        longitude = mLastLocation.getLongitude();
                    }
                    else {
                        Toast.makeText(getApplicationContext(),"Please make sure your GPS is turned on",Toast.LENGTH_SHORT).show();
                    }
                }
                else if(statusCode == LocationSettingsStatusCodes.RESOLUTION_REQUIRED) {
                    //Show a dialog to change the Location Settings
                    try {
                        status.startResolutionForResult(MainActivity.this, REQUEST_CHECK_SETTINGS);
                    } catch (IntentSender.SendIntentException e) {
                        e.printStackTrace();
                    }
                }
                else if(statusCode == LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE) {
                    Toast.makeText(MainActivity.this, "Unable to use location settings!", Toast.LENGTH_SHORT);
                }
            }
        });
    }

    private void fetchNearbyEvents() {
        //Get the nearby events from the server here...
        nearbyEvents = new ArrayList<>();
        String url = "http://197.45.183.87:8080/Seme3taha/GetNearbyEvents?&userLongitude="+longitude+"&userLatitude="+latitude;
        //Request a string response from the provided URL.
        StringRequest request = new StringRequest(Request.Method.GET, url, new Response.Listener<String>(){
            Gson gson = new Gson();
            @Override
            public void onResponse(String response) {
                if(response.length() > 0) {
                    Type type = new TypeToken<List<LocationEvent>>(){}.getType();
                    nearbyEvents = gson.fromJson(response.toString(), type);
                    if(nearbyEvents.isEmpty() == false){
                        Log.d("Volley", "Received location events: "+String.valueOf(nearbyEvents.size()));
                        //Display fetched markers from server on map
                        displayNearbyEvents();
                    }
                    else{
                        Log.d("Volley", "Received no location events");
                        Toast.makeText(MainActivity.this, "Received no location events. This most likely means you're safe. :)", Toast.LENGTH_SHORT).show();
                    }
                }
                else {
                    Log.d("Volley", "Received an empty response from server. Probably means you're safe. :)");
                    Toast.makeText(MainActivity.this, "Received an empty response from server. This probably means you're safe. :)", Toast.LENGTH_SHORT).show();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d("Volley","Unable to fetch nearby events, server unreachable.");
                Toast.makeText(MainActivity.this, "Unable to fetch nearby events, server unreachable.", Toast.LENGTH_SHORT).show();
            }
        });
        HttpConnector.getInstance(this).addToRequestQueue(request);
    }

    //Display fetched markers from server on map
    private void displayNearbyEvents(){
        //Display the nearby events on the map here...
        if(nearbyEvents.isEmpty() == false) {
            int i;
            int numberOfEvents = nearbyEvents.size();
            for(i = 0; i<= numberOfEvents - 1 ;i++) {
                LocationEvent locEvent = nearbyEvents.get(i);
                //Create the marker
                MarkerOptions marker = new MarkerOptions()
                        .position(new LatLng(locEvent.getLatitude(), locEvent.getLongitude()))
                        .snippet(locEvent.getAddress())
                        .title("Bomb heard from this location @ " + locEvent.getTimestamp())
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.bomb3icon))
                        .flat(true);
                mMap.addMarker(marker).setAlpha((float)0.1);
            }
        }
    }

    private void sendEventToServer(LocationEvent locEvent) {
        //Send the event info to the server in a background thread
        //Instantiate the RequestQueue.
        String url = "http://197.45.183.87:8080/Seme3taha/InsertNewLocationEvent?latitude=" + locEvent.getLatitude() + "&longitude=" + locEvent.getLongitude() + "&address=" + URLEncoder.encode(locEvent.getAddress())+"&timestamp="+URLEncoder.encode(locEvent.getTimestamp());
        //Request a string response from the provided URL.
        StringRequest request = new StringRequest(Request.Method.GET, url, new Response.Listener<String>(){
            @Override
            public void onResponse(String response) {
                Log.d("VOLLEY","Event submitted to server successfully.");

            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d("VOLLEY", "Unable to submit event to server, server unreachable");
                Toast.makeText(MainActivity.this, "Unable to submit event to server, server unreachable", Toast.LENGTH_SHORT).show();
            }
        });
        //Add the request to the RequestQueue.
        HttpConnector.getInstance(this).addToRequestQueue(request);
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        // Connected to Google Play services!
        // The good stuff goes here.
        //Start listening for location updates, which will be resolved in the LocationListener.onLocationChanged callback
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, locationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int cause) {
        // The connection has been interrupted.
        // Disable any UI components that depend on Google APIs
        // until onConnected() is called.
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // This callback is important for handling errors that
        // may occur while attempting to connect with Google.
        //
        // More about this in the 'Handle Connection Failures' section.
        Toast.makeText(getApplicationContext(), "Connection to Google Play Services failed.", Toast.LENGTH_SHORT);
    }

    @Override
    public void onLocationChanged(Location location) {
        //This is the callback that will handle the current location of the user which is updated every 5 seconds
        mLastLocation = location;
        latitude = mLastLocation.getLatitude();
        longitude = mLastLocation.getLongitude();
        Date date = new Date(); //gets the current time
        timestamp = simpleDateFormat.format(date); //formats the date object to the specified date format above
        latTextView.setText(String.valueOf(latitude));
        longTextView.setText(String.valueOf(longitude));
        timestampTextView.setText(timestamp);
        //TODO Khally el mapFragment.getMapAsync deh dependant 3la checkbox aw button to make the map "follow" the user location
        //mapFragment.getMapAsync(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        //Connect to the Google Play Services
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        //Disconnect from the Google Play Services
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        //Stop listening for locations to save battery
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
    }

    @Override
    public void onMapReady(GoogleMap map){
        //Change the map view depending which menu option was clicked last
        if(mapView == 0) {
            map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        }
        if(mapView == 1){
            map.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        }
        //Show the user location on the map
        map.setMyLocationEnabled(true);

        //If bombHeard is true, add a marker to the map and zoom in to the location of the marker (Zoom level 15)
        //Else, don't zoom in, just use City view (Zoom level 10)
        if(bombHeard) {
            //Create the marker
            MarkerOptions marker = new MarkerOptions()
                    .position(new LatLng(latitude, longitude))
                    .snippet(addressString)
                    .title("Bomb heard from this location @ " + timestamp)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.bomb3icon))
                    .flat(true);
            map.addMarker(marker)
                    .showInfoWindow();

            map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitude, longitude), 15));

            mMap = map;
            bombHeard = false;
        }
        else if(firstStart) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitude, longitude), 10));
            mMap = map;
            firstStart = false;
        }
    }

    public void seme3t(View view) {
        Toast.makeText(getApplicationContext(), "Getting location...", Toast.LENGTH_SHORT).show();

        bombHeard = true;

        AddressDecoder decoder = new AddressDecoder();
        decoder.execute(mLastLocation);
    }

    public void shareFB(View view) {

        GoogleMap.SnapshotReadyCallback callback = new GoogleMap.SnapshotReadyCallback()
        {
            @Override
            public void onSnapshotReady(Bitmap snapshot) {
                image = snapshot;
                SharePhoto photo = new SharePhoto.Builder()
                        .setBitmap(image)
                        .build();
                SharePhotoContent content = new SharePhotoContent.Builder()
                        .addPhoto(photo)
                        .build();

                ShareButton shareButton = (ShareButton)findViewById(R.id.share_button_fb);
                shareButton.setShareContent(content);

                if (ShareDialog.canShow(ShareLinkContent.class)) {
                    shareDialog.show(content);
                }
            }
        };
        mMap.snapshot(callback);
    }

    public void share(View view) {

        Intent share = new Intent();
        share.setAction(Intent.ACTION_SEND);
        share.setType("image/jpeg");

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, 0, os);
        byte[] array = os.toByteArray();

        Bitmap imageJPEG = BitmapFactory.decodeByteArray(array, 0, array.length);

        share.putExtra(Intent.EXTRA_STREAM, imageJPEG);
        startActivity(Intent.createChooser(share, "Share Image"));

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement

        if(id == R.id.map_view_street) {
            mapView = 0;
            mapFragment.getMapAsync(this);
        }
        else if(id == R.id.map_view_satellite) {
            mapView = 1;
            mapFragment.getMapAsync(this);
        }

        return super.onOptionsItemSelected(item);
    }


    public class AddressDecoder extends AsyncTask<Location,Void,String> {

        Geocoder geocoder = new Geocoder(MainActivity.this, Locale.getDefault());

        TextView addressTextView = (TextView) findViewById(R.id.address_text_view);

        @Override
        protected String doInBackground(Location... location) {
            try{
                List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                Address address = addresses.get(0);
                addressString = "";
                for(int i=0;i<address.getMaxAddressLineIndex();i++) {
                    addressString += address.getAddressLine(i) + ", ";
                    publishProgress();
                }
                addressString = addressString + address.getCountryName();
            }
            catch (IOException io){
                Log.v("Main Activity", "Error in reverse geocoding the address!");
            }
            return addressString;
        }

        @Override
        protected void onPreExecute() {
            addressString = "Getting address...";
            addressTextView.setText(addressString);
        }

        @Override
        protected void onPostExecute(String result) {
            addressTextView.setText(addressString);
            mapFragment.getMapAsync(MainActivity.this);
            LocationEvent locationEvent = new LocationEvent();
            locationEvent.setLatitude(latitude);
            locationEvent.setLongitude(longitude);
            locationEvent.setTimestamp(timestamp);
            locationEvent.setAddress(addressString);
            Log.d("EVENT:",String.valueOf(latitude));
            Log.d("EVENT:",String.valueOf(longitude));
            Log.d("EVENT:",timestamp);
            Log.d("EVENT:",addressString);
            sendEventToServer(locationEvent);
        }

        @Override
        protected void onProgressUpdate (Void... values) {
            addressTextView.setText("Still getting address...");
        }
    }
}
