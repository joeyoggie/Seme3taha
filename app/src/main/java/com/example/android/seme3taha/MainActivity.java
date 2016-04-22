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
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Locale;


//Todo send the location to a webserver which will send the GCM notifications to other nearby/to-be-nearby phones
//Todo fetch nearby locations from a webserver and display them when opening the app
//Todo add GCM functionality to receive notifications about nearby places

public class MainActivity extends ActionBarActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener, OnMapReadyCallback {

    //Default value set to Cairo, Egypt (will be overriden on app launch anyway)
    static double latitude = 30.048865;
    static double longitude = 31.235290;

    //Boolean to indicate whether a bomb was heard or not and update the map accordingly
    static boolean bombHeard = false;
    //String that will hold the address of the current/bomb location
    String addressString = null;

    TextView latTextView;
    TextView longTextView;
    TextView addressTextView;
    TextView timestamp;

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

        //Initialize the mGoogleApiClient object if it's null, and make sure to pass LocationServices API parameter
        if(mGoogleApiClient ==  null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }
        //Initialize the locationRequest object and set the preferred interval to 5 seconds, and same for fastest interval
        //Also, use the HIGH_ACCURACY parameter to get a more precise location
        locationRequest = new LocationRequest();
        locationRequest.setInterval(5000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        //Initialize the textviews
        latTextView = (TextView) findViewById(R.id.lat_text_view);
        longTextView = (TextView) findViewById(R.id.long_text_view);
        addressTextView = (TextView) findViewById(R.id.address_text_view);
        timestamp = (TextView) findViewById(R.id.timestamp);

        mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        //Build the LocationSettingsRequest and check if there are any settings that need to be changed for Location Services
        buildLocationSettingsRequest();

        //Initialize the Facebook ShareDialog
        shareDialog = new ShareDialog(this);

        //Fetch nearby events from the server
        fetchNearbyEvents();

        //Hide the progress dialog (should be dismissed when all above functions have finished, even background ones)
        progressDialog.dismiss();
    }

    private void buildLocationSettingsRequest() {
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
                        latTextView.setText(String.valueOf(latitude));
                        longTextView.setText(String.valueOf(longitude));

                        AddressDecoder decoder = new AddressDecoder();
                        decoder.execute(mLastLocation);

                        mapFragment.getMapAsync(MainActivity.this);
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
    }

    private void displayNearbyEvents(){
        //Display the nearby events on the map here...
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
        latTextView.setText(String.valueOf(latitude));
        longTextView.setText(String.valueOf(longitude));
        timestamp.setText(java.text.DateFormat.getTimeInstance().format(new Date()));
        //Khally el mapFragment.getMapAsync deh dependant 3la checkbox aw button to make the map "follow" the user location
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

        //Display fetched markers from server on map here...
        displayNearbyEvents();

        //Create the marker
        MarkerOptions marker1 = new MarkerOptions()
                .position(new LatLng(29.958303, 30.936197))
                .snippet("6th of October City")
                .title("Bomb heard from this location")
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.bomb3icon))
                .flat(true);
        map.addMarker(marker1).setAlpha((float)0.1);

        //Create the marker
        MarkerOptions marker2 = new MarkerOptions()
                .position(new LatLng(30.017174, 31.412134))
                .snippet("Downtown Mall, 5th District")
                .title("Bomb heard from this location")
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.bomb3icon))
                .flat(true);
        map.addMarker(marker2).setAlpha((float)0.1);

        //Create the marker
        MarkerOptions marker3 = new MarkerOptions()
                .position(new LatLng(29.851272, 31.342195))
                .snippet("Faculty of Engineering, Helwan")
                .title("Bomb heard from this location")
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.bomb3icon))
                .flat(true);
        map.addMarker(marker3).setAlpha((float)0.1);

        //If bombHeard is true, add a marker to the map and zoom in to the location of the marker (Zoom level 15)
        //Else, don't zoom in, just use City view (Zoom level 10)
        if(bombHeard) {
            //Get the current time strting that will be used insidethe marker
            String currentTime;
            Time time = new Time();
            time.setToNow();
            if(time.minute < 10) {
                currentTime = time.hour + ":0" + time.minute;
            }
            else {
                currentTime = time.hour + ":" + time.minute;
            }
            if(time.hour > 12) {
                currentTime += "PM";
            }
            else {
                currentTime += "AM";
            }
            //Create the marker
            MarkerOptions marker = new MarkerOptions()
                    .position(new LatLng(latitude, longitude))
                    .snippet(addressString)
                    .title("Bomb heard from this location @ " + currentTime)
                    .icon(BitmapDescriptorFactory.fromResource(R.drawable.bomb3icon))
                    .flat(true);
            map.addMarker(marker)
                    .showInfoWindow();

            map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitude, longitude), 15));

            mMap = map;
        }
        else {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(latitude, longitude), 10));
            mMap = map;
        }
    }

    public void seme3t(View view) {
        Toast.makeText(getApplicationContext(), "Getting location...", Toast.LENGTH_SHORT).show();

        bombHeard = true;

        //Build the LocationSettingsRequest and check if there are any settings that need to be changed for Location Services
        buildLocationSettingsRequest();
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
        }

        @Override
        protected void onProgressUpdate (Void... values) {
            addressTextView.setText("Still getting address...");
        }
    }
}
