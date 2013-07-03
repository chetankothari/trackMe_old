package com.uprootlabs.trackme;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;

public final class LocationService extends Service implements LocationListener, GooglePlayServicesClient.ConnectionCallbacks,
    GooglePlayServicesClient.OnConnectionFailedListener {

  public static final String ACTION_CAPTURE_LOCATIONS = "LocationService/captureLocations";
  public static final String ACTION_STOP_CAPTURIGN_LOCATIONS = "LocationService/stopCapturing";
  public static final String ACTION_QUERY_STATUS_MAIN_ACTIVITY = "LocationService/queryStatusMainActivity";
  public static final String ACTION_QUERY_STATUS_UPLOAD_SERVICE = "LocationService/queryStatusUploadService";
  public static final String ERROR_CAPTURING_LOCATIONS = "errorCapturingLocations";
  public static final String ERROR_STARTING_SERVICE = "errorStartingService";
  public static final String LOCATION_SERVICE_TAG = "locationService";
  public static final String PARAM_LOCATION_SERVICE_STATUS = "serviceStatus";
  public static final String STATUS_CAPTURING_LOCATIONS = "capturingLocations";
  public static final String STATUS_WARMED_UP = "warmedUp";
  private static final int MILLISECONDS_PER_SECOND = 1000;
  private int captureFrequency;
  private Notification notification;
  private LocationClient myLocationClient;
  private LocationRequest myLocationRequest;
  private int errorCode;
  private boolean capturingLocations = false;
//  TrackMeDBHelper myLocationDB = new TrackMeDBHelper(this);
  SharedPreferences myPreferences;
//  private TrackMeDB db = new TrackMeDB(myLocationDB.getWritableDatabase());

  private final BroadcastReceiver broadCastReceiverLocationService = new BroadcastReceiver() {

    @Override
    public void onReceive(final Context context, final Intent intent) {
      final String action = intent.getAction();
      if (action.equals(ACTION_CAPTURE_LOCATIONS)) {
        Log.d(LOCATION_SERVICE_TAG, "capture Request");
        startCapture(intent);
      } else if (action.equals(ACTION_STOP_CAPTURIGN_LOCATIONS)) {
        Log.d(LOCATION_SERVICE_TAG, "Stop Request");
        stopCapturing(intent);
      } else if (action.equals(ACTION_QUERY_STATUS_MAIN_ACTIVITY) | action.equals(ACTION_QUERY_STATUS_UPLOAD_SERVICE)) {
        Log.d(LOCATION_SERVICE_TAG, "Status Request");
        if (capturingLocations) {
          Log.d(LOCATION_SERVICE_TAG, "Capturing Reply");
          intent.putExtra(PARAM_LOCATION_SERVICE_STATUS, STATUS_CAPTURING_LOCATIONS);
        } else {
          Log.d(LOCATION_SERVICE_TAG, "WarmedUp Reply");
          intent.putExtra(PARAM_LOCATION_SERVICE_STATUS, STATUS_WARMED_UP);
        }

      }
    }

  };

  @Override
  public void onCreate() {
    final IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(ACTION_CAPTURE_LOCATIONS);
    intentFilter.addAction(ACTION_QUERY_STATUS_MAIN_ACTIVITY);
    intentFilter.addAction(ACTION_QUERY_STATUS_UPLOAD_SERVICE);
    intentFilter.addAction(ACTION_STOP_CAPTURIGN_LOCATIONS);

    LocalBroadcastManager.getInstance(this).registerReceiver(broadCastReceiverLocationService, intentFilter);


    super.onCreate();
    Log.d(LOCATION_SERVICE_TAG, "Service Created");
  }

  @Override
  public IBinder onBind(final Intent intent) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public int onStartCommand(final Intent intent, final int flags, final int startId) {
    myPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    Log.d(LOCATION_SERVICE_TAG, "Service Started");

    Log.d(LOCATION_SERVICE_TAG, "warmingUP");

    warmUpService();

    Log.d(LOCATION_SERVICE_TAG, STATUS_WARMED_UP);
    return Service.START_STICKY;
  }

  private void warmUpService() {
    myLocationClient = new LocationClient(this, this, this);
    myLocationClient.connect();
  }

  private void startCapture(final Intent intent) {
    Log.d(LOCATION_SERVICE_TAG, "From startCapture");
    captureLocations(intent);
    setForegroundService();
  }

  private void captureLocations(final Intent intent) {
    Log.d(LOCATION_SERVICE_TAG, "From captureLocations");
    captureFrequency = (Integer.parseInt(myPreferences.getString("captureFrequency", "10"))) * MILLISECONDS_PER_SECOND;
    myLocationRequest = LocationRequest.create();
    myLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    myLocationRequest.setInterval(captureFrequency);
    myLocationRequest.setFastestInterval(captureFrequency);

    if (servicesConnected()) {
      myLocationClient.requestLocationUpdates(myLocationRequest, this);

      capturingLocations = true;
      Log.d(LOCATION_SERVICE_TAG, "capturingLocaions" + capturingLocations);

      intent.putExtra(PARAM_LOCATION_SERVICE_STATUS, STATUS_CAPTURING_LOCATIONS);
    } else {

      capturingLocations = false;
      Log.d(LOCATION_SERVICE_TAG, "capturingLocations" + capturingLocations);

      intent.putExtra(PARAM_LOCATION_SERVICE_STATUS, ERROR_CAPTURING_LOCATIONS);
    }

  }

  private void setForegroundService() {
    Log.d(LOCATION_SERVICE_TAG, "From setForegroundService");
    final Intent intentNotification = new Intent(this, MainActivity.class);
    final PendingIntent pi = PendingIntent.getActivity(this, 1, intentNotification, 0);
    notification = new Notification(R.drawable.ic_launcher, "Capturing", System.currentTimeMillis());
    notification.setLatestEventInfo(this, "TrackMe", "Capturing Locatoins", pi);
    notification.flags |= Notification.FLAG_ONGOING_EVENT;

    startForeground(1, notification);
  }

  private void stopCapturing(final Intent intent) {
    if (myLocationClient.isConnected())
      myLocationClient.removeLocationUpdates(this);

    capturingLocations = false;

    Log.d(LOCATION_SERVICE_TAG, "stop Capturing");
    intent.putExtra(PARAM_LOCATION_SERVICE_STATUS, STATUS_WARMED_UP);
    stopForeground(true);
  }

  @Override
  public void onDestroy() {
    if (myLocationClient.isConnected())
      myLocationClient.disconnect();
    capturingLocations = false;
    LocalBroadcastManager.getInstance(this).unregisterReceiver(broadCastReceiverLocationService);
    Log.d(LOCATION_SERVICE_TAG, "Destroyed");
  }

  @Override
  public void onLocationChanged(final Location location) {
    final long timeStamp = System.currentTimeMillis();
    Log.d(LOCATION_SERVICE_TAG, "Locations Changed");
//    db.insertLocations(location, timeStamp);
  }

  private boolean servicesConnected() {
    final int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);

    if (ConnectionResult.SUCCESS == resultCode) {
      return true;
    } else {
      Log.d(LOCATION_SERVICE_TAG, "Google Play Service Not Available");

      final Intent dialogIntent = new Intent(getBaseContext(), DialogActivity.class);
      dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      dialogIntent.putExtra("errorCode", errorCode);
      getApplication().startActivity(dialogIntent);

      return false;
    }
  }

  @Override
  public void onConnectionFailed(final ConnectionResult connectionResult) {
    errorCode = connectionResult.getErrorCode();
    if (connectionResult.hasResolution()) {
      Log.d(LOCATION_SERVICE_TAG, "Start Resolution Error");

      final Intent mainActivityBroadCastintent = new Intent(MainActivity.MAIN_ACTIVITY_LOCATION_SERVICE_STATUS);
      mainActivityBroadCastintent.putExtra(PARAM_LOCATION_SERVICE_STATUS, ERROR_STARTING_SERVICE);
      LocalBroadcastManager.getInstance(this).sendBroadcast(mainActivityBroadCastintent);

      stopSelf();
      // try {
      // connectionResult.startResolutionForResult(this,
      // CONNECTION_FAILURE_RESOLUTION_REQEUST);
      // } catch (IntentSender.SendIntentException e) {
      // e.printStackTrace();
      // }
    } else {
      Log.d(LOCATION_SERVICE_TAG, "Connection Failure");

      final Intent mainActivityBroadCastintent = new Intent(MainActivity.MAIN_ACTIVITY_LOCATION_SERVICE_STATUS);
      mainActivityBroadCastintent.putExtra(PARAM_LOCATION_SERVICE_STATUS, ERROR_STARTING_SERVICE);
      LocalBroadcastManager.getInstance(this).sendBroadcast(mainActivityBroadCastintent);

      final Intent dialogIntent = new Intent(getBaseContext(), DialogActivity.class);
      dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      dialogIntent.putExtra("errorCode", errorCode);
      getApplication().startActivity(dialogIntent);

      stopSelf();
    }
  }

  @Override
  public void onConnected(final Bundle bundle) {
    final Intent mainActivityBroadCastintent = new Intent(MainActivity.MAIN_ACTIVITY_LOCATION_SERVICE_STATUS);
    mainActivityBroadCastintent.putExtra(PARAM_LOCATION_SERVICE_STATUS, STATUS_WARMED_UP);
    LocalBroadcastManager.getInstance(this).sendBroadcast(mainActivityBroadCastintent);
  }

  @Override
  public void onDisconnected() {
    // TODO Auto-generated method stub

  }

}