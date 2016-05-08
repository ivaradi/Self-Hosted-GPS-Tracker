package fr.herverenault.selfhostedgpstracker;

import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

import javax.net.ssl.SSLHandshakeException;

public class SelfHostedGPSTrackerService extends IntentService implements LocationListener {

    public static final String NOTIFICATION = "fr.herverenault.selfhostedgpstracker";

    public static boolean isRunning;
    public static Calendar runningSince;
    public static String lastServerResponse;

    public Calendar stoppedOn;

    private final static String MY_TAG = "SelfHostedGPSTrackerSrv";

    private SharedPreferences preferences;
    private String urlText;
    private LocationManager locationManager;
    private int pref_gps_updates;
    private long latestUpdate;
    private int pref_max_run_time;
    private boolean pref_timestamp;

    public SelfHostedGPSTrackerService() {
        super("SelfHostedGPSTrackerService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(MY_TAG, "in onCreate, init GPS stuff");

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            onProviderEnabled(LocationManager.GPS_PROVIDER);
        } else {
            onProviderDisabled(LocationManager.GPS_PROVIDER);
        }

        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putLong("stoppedOn", 0);
        editor.commit();
        pref_gps_updates = Integer.parseInt(preferences.getString("pref_gps_updates", "30")); // seconds
        pref_max_run_time = Integer.parseInt(preferences.getString("pref_max_run_time", "24")); // hours
        pref_timestamp = preferences.getBoolean("pref_timestamp", false);
        urlText = preferences.getString("URL", "");
        if (urlText.contains("?")) {
            urlText = urlText + "&";
        } else {
            urlText = urlText + "?";
        }

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, pref_gps_updates * 1000, 1, this);

        lastServerResponse = getResources().getString(R.string.waiting_for_gps_data);
        Intent notifIntent = new Intent(NOTIFICATION);
        notifIntent.putExtra(NOTIFICATION, "START");
        sendBroadcast(notifIntent);

        long currentTime = System.currentTimeMillis();
        new SelfHostedGPSTrackerRequest(getApplicationContext()).start("tracker=start&t=" + currentTime);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(MY_TAG, "in onHandleIntent, run for maximum time set in preferences");

        isRunning = true;
        runningSince = Calendar.getInstance();
        Intent notifIntent = new Intent(NOTIFICATION);
        sendBroadcast(notifIntent);

        Notification notification = new Notification(R.drawable.ic_notif, getText(R.string.toast_service_running), System.currentTimeMillis());
        Intent notificationIntent = new Intent(this, SelfHostedGPSTrackerActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        notification.setLatestEventInfo(this, getText(R.string.app_name), getText(R.string.toast_service_running), pendingIntent);
        startForeground(R.id.logo, notification);

        long endTime = System.currentTimeMillis() + pref_max_run_time*60*60*1000;
        while (System.currentTimeMillis() < endTime) {
            try {
                Thread.sleep(60*1000); // note: when device is sleeping, it may last up to 5 minutes or more
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void onDestroy() {
        // (user clicked the stop button, or max run time has been reached)
        Log.d(MY_TAG, "in onDestroy, stop listening to the GPS");
        long currentTime = System.currentTimeMillis();
        new SelfHostedGPSTrackerRequest(getApplicationContext()).start("tracker=stop&t=" + currentTime);

        locationManager.removeUpdates(this);

        isRunning = false;
        stoppedOn = Calendar.getInstance();

        SharedPreferences.Editor editor = preferences.edit();
        editor.putLong("stoppedOn", stoppedOn.getTimeInMillis());
        editor.commit();

        Intent notifIntent = new Intent(NOTIFICATION);
        sendBroadcast(notifIntent);
    }

    /* -------------- GPS stuff -------------- */

    @Override
    public void onLocationChanged(Location location) {
        Log.d(MY_TAG, "in onLocationChanged, latestUpdate == " + latestUpdate);
        long currentTime = System.currentTimeMillis();

        // Tolerate devices which sometimes send GPS updates 1 second too early,
        // such as HTC One Mini...
        if ((currentTime - latestUpdate) < (pref_gps_updates - 1) * 1000) {
            return;
        }

        latestUpdate = currentTime;

        new SelfHostedGPSTrackerRequest(getApplicationContext()).start(
                "lat=" + location.getLatitude()
                + "&lon=" + location.getLongitude()
                + "&alt=" + location.getAltitude ()
                + "&spd=" + location.getSpeed ()
                + "&brg=" + location.getBearing ()
                + "&t=" + currentTime );
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    private class PendingOpenHelper extends SQLiteOpenHelper {
        private static final int VERSION = 1;

        public PendingOpenHelper(Context context) {
            super(context, "gpstracker", null, VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE pending (" +
                       "status INT, " +
                       "params TEXT);");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }

    };

    private static long dbAddPending(SQLiteDatabase db, String params) {
        ContentValues values = new ContentValues();
        values.put("status", 2);
        values.put("params", params);
        try {
            return db.insertOrThrow("pending", null, values);
        } catch(SQLException e) {
            Log.d(MY_TAG, "Failed to add pending row: " + e);
            return -1;
        }
    }

    private static int dbDelete(SQLiteDatabase db, long rowID) {
        try {
            return db.delete("pending", "rowid=" + rowID, null);
        } catch(SQLException e) {
            Log.d(MY_TAG, "Failed to delete pending row: " + e);
            return -1;
        }
    }

    private static int dbMarkFailed(SQLiteDatabase db, long rowID) {
        ContentValues values = new ContentValues();
        values.put("status", 1);
        try {
            return db.update("pending", values, "rowid=" + rowID, null);
        } catch(SQLException e) {
            Log.d(MY_TAG, "Failed to mark row failed: " + e);
            return -1;
        }
    }

    private class SelfHostedGPSTrackerRequest extends Thread {
        private final static String MY_TAG = "SelfHostedGPSTrackerReq";
        private String params;
        private Context context;
        private long rowID = -1;
        private SQLiteDatabase database = null;

        public SelfHostedGPSTrackerRequest(Context context) {
            this.context = context;
        }

        public void run() {
            String message;
            int code = 0;

            openDatabase();
            if (rowID==-1 && database!=null) {
                rowID = dbAddPending(database, params);
            }

            boolean isOK = false;
            try {
                URL url = new URL(urlText + params);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout(10000 /* milliseconds */);
                conn.setConnectTimeout(15000 /* milliseconds */);
                conn.setRequestMethod("GET");
                conn.setDoInput(true);
                conn.connect();
                code = conn.getResponseCode();
                Log.d(MY_TAG, "HTTP request done: " + code);
                message = "HTTP " + code;
                isOK = code == 200;
            }
            catch (MalformedURLException e) {
                message = getResources().getString(R.string.error_malformed_url);
            }
            catch (UnknownHostException e) {
                message = getResources().getString(R.string.error_unknown_host);
            }
            catch (SSLHandshakeException e) {
                message = getResources().getString(R.string.error_ssl);
            }
            catch (SocketTimeoutException e) {
                message = getResources().getString(R.string.error_timeout);
            }
            catch (Exception e) {
                Log.d(MY_TAG, "HTTP request failed: " + e);
                message = e.getLocalizedMessage();
                if (message == null) {
                    message = e.toString();
                }
            }

            if (rowID!=-1) {
                if (isOK) {
                    dbDelete(database, rowID);
                } else {
                    dbMarkFailed(database, rowID);
                }
            }

            if ( ! params.startsWith("tracker=")) {
                lastServerResponse = getResources().getString(R.string.last_location_sent_at)
                        + " "
                        + DateFormat.getTimeInstance().format(new Date())
                        + " ";

                if (code == 200) {
                    lastServerResponse += "<font color='#00aa00'><b>"
                            + getResources().getString(R.string.http_request_ok)
                            + "</b></font>";
                } else {
                    lastServerResponse += "<font color='#ff0000'><b>"
                            + getResources().getString(R.string.http_request_failed)
                            + "</b></font>"
                            + "<br>"
                            + "(" + message + ")";
                }

                Intent notifIntent = new Intent(NOTIFICATION);
                notifIntent.putExtra(NOTIFICATION, "HTTP");
                sendBroadcast(notifIntent);
            }
        }

        public void start(String params) {
            this.params = params;
            super.start();
        }

        private void openDatabase() {
            if (database==null) {
                SQLiteOpenHelper sqliteHelper = new PendingOpenHelper(context);
                try {
                    database = sqliteHelper.getWritableDatabase();
                } catch(SQLException e) {
                    Log.d(MY_TAG, "Opening of SQLite database failed: " + e);
                }
            }
        }
    }
}
