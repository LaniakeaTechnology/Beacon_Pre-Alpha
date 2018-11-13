package com.example.android.beacon_pre_alpha;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessageListener;
import com.google.android.gms.nearby.messages.Messages;
import com.google.android.gms.nearby.messages.MessagesOptions;
import com.google.android.gms.nearby.messages.NearbyMessagesStatusCodes;
import com.google.android.gms.nearby.messages.NearbyPermissions;
import com.google.android.gms.nearby.messages.Strategy;
import com.google.android.gms.nearby.messages.SubscribeOptions;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        SharedPreferences.OnSharedPreferenceChangeListener{


    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int PERMISSIONS_REQUEST_CODE = 1111;
    private static final String KEY_SUBSCRIBED = "subscribed";
    /**
     * The entry point to Google Play Services.
     */
    private GoogleApiClient mGoogleApiClient;
    /**
     * The container {@link android.view.ViewGroup} for the minimal UI associated with this sample.
     */
    private ConstraintLayout mContainer;
    /**
     * Tracks subscription state. Set to true when a call to
     * {@link Messages#subscribe(GoogleApiClient, MessageListener)} succeeds.
     */
    private boolean mSubscribed = false;
    /**
     * Adapter for working with messages from nearby beacons.
     */
    private ArrayAdapter<String> mNearbyMessagesArrayAdapter;

    /**
     * Backing data structure for {@code mNearbyMessagesArrayAdapter}.
     */
    private List<String> mNearbyMessagesList = new ArrayList<>();

    private ListView nearbyMessages;

    private MessageListener mMessageListener;
    private Message mMessage;
    ArrayList<String> a;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /**
         * Adjusting list
         */
        nearbyMessages = (ListView)findViewById(R.id._messagesList);
        a = new ArrayList<>();
        a.add("hello");
        ArrayAdapter arrayAdapter = new ArrayAdapter(this,android.R.layout.simple_list_item_1,a);
        nearbyMessages.setAdapter(arrayAdapter);

        if (savedInstanceState != null) {
            mSubscribed = savedInstanceState.getBoolean(KEY_SUBSCRIBED, false);
        }

        mContainer = (ConstraintLayout) findViewById(R.id.constraint_main);

        if (!havePermissions()) {
            Log.i(TAG, "Requesting permissions needed for this app.");
            requestPermissions();
        }

        final List<String> cachedMessages = Utils.getCachedMessages(this);
        if (cachedMessages != null) {
            mNearbyMessagesList.addAll(cachedMessages);
        }

        final ListView nearbyMessagesListView = (ListView) findViewById(R.id._messagesList);
        mNearbyMessagesArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                mNearbyMessagesList);
        if (nearbyMessagesListView != null) {
            nearbyMessagesListView.setAdapter(mNearbyMessagesArrayAdapter);
        }



    }

    @Override
    protected void onResume() {
        super.onResume();

        getSharedPreferences(getApplicationContext().getPackageName(), Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(this);

        if (havePermissions()) {
            buildGoogleApiClient();
        }
    }

    @TargetApi(23)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode != PERMISSIONS_REQUEST_CODE) {
            return;
        }
        for (int i = 0; i < permissions.length; i++) {
            String permission = permissions[i];
            if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                if (shouldShowRequestPermissionRationale(permission)) {
                    Log.i(TAG, "Permission denied without 'NEVER ASK AGAIN': " + permission);
                    showRequestPermissionsSnackbar();
                } else {
                    Log.i(TAG, "Permission denied with 'NEVER ASK AGAIN': " + permission);
                    showLinkToSettingsSnackbar();
                }
            } else {
                Log.i(TAG, "Permission granted, building GoogleApiClient");
                buildGoogleApiClient();
            }
        }
    }

    private synchronized void buildGoogleApiClient() {
        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Nearby.MESSAGES_API, new MessagesOptions.Builder()
                            .setPermissions(NearbyPermissions.BLE).build())
                    .addConnectionCallbacks(this)
                    .enableAutoManage(this, this)
                    .build();
        }
    }

    @Override
    protected void onPause() {
        getSharedPreferences(getApplicationContext().getPackageName(), Context.MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        if (mContainer != null) {

            Toast.makeText(getApplicationContext(),"Exception while connecting to Google Play services: " +
                    connectionResult.getErrorMessage(),Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.w(TAG, "Connection suspended. Error code: " + i);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.i(TAG, "GoogleApiClient connected");
        subscribe();
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_SUBSCRIBED, mSubscribed);
    }

    private boolean havePermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSIONS_REQUEST_CODE);
    }


    private void subscribe() {
        // In this sample, we subscribe when the activity is launched, but not on device orientation
        // change.
        if (mSubscribed) {
            Log.i(TAG, "Already subscribed.");
            return;
        }

        SubscribeOptions options = new SubscribeOptions.Builder()
                .setStrategy(Strategy.BLE_ONLY)
                .build();

        Nearby.Messages.subscribe(mGoogleApiClient, getPendingIntent(), options)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        if (status.isSuccess()) {
                            Log.i(TAG, "Subscribed successfully.");
                            startService(getBackgroundSubscribeServiceIntent());
                        } else {
                            Log.e(TAG, "Operation failed. Error: " +
                                    NearbyMessagesStatusCodes.getStatusCodeString(
                                            status.getStatusCode()));
                        }
                    }
                });
    }


    private PendingIntent getPendingIntent() {
        return PendingIntent.getService(this, 0,
                getBackgroundSubscribeServiceIntent(), PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Intent getBackgroundSubscribeServiceIntent() {
        return new Intent(this, BackgroundSubscribeIntentService.class);
    }

    private void showLinkToSettingsSnackbar() {
        if (mContainer == null) {
            return;
        }
//        Snackbar.make(mContainer,
//                R.string.permission_denied_explanation,
//                Snackbar.LENGTH_INDEFINITE)
//                .setAction(R.string.settings, new View.OnClickListener() {
//                    @Override
//                    public void onClick(View view) {
//                        // Build intent that displays the App settings screen.
//                        Intent intent = new Intent();
//                        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
//                        Uri uri = Uri.fromParts("package",
//                                BuildConfig.APPLICATION_ID, null);
//                        intent.setData(uri);
//                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                        startActivity(intent);
//                    }
//                }).show();
    }

    private void showRequestPermissionsSnackbar() {
        if (mContainer == null) {
            return;
        }
//        Snackbar.make(mContainer, R.string.permission_rationale,
//                Snackbar.LENGTH_INDEFINITE)
//                .setAction(R.string.ok, new View.OnClickListener() {
//                    @Override
//                    public void onClick(View view) {
//                        // Request permission.
//                        ActivityCompat.requestPermissions(MainActivity.this,
//                                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
//                                PERMISSIONS_REQUEST_CODE);
//                    }
//                }).show();
    }
    @Override
    public void onStop() {
        Nearby.getMessagesClient(this).unpublish(mMessage);
        Nearby.getMessagesClient(this).unsubscribe(mMessageListener);
        super.onStop();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

    }


    public void found(String message){

        a.add(message);
        ArrayAdapter arrayAdapter = new ArrayAdapter(this,android.R.layout.simple_list_item_1,a);
        nearbyMessages.setAdapter(arrayAdapter);

    }

    public void buttonPressed(View v){
        a.add("hello");
        ArrayAdapter arrayAdapter = new ArrayAdapter(this,android.R.layout.simple_list_item_1,a);
        nearbyMessages.setAdapter(arrayAdapter);

    }
}
