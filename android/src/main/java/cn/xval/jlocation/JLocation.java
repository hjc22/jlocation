package cn.xval.jlocation;

import android.Manifest;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.xval.plugin.PluginPermissionHelper;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;


public final class JLocation implements EventChannel.StreamHandler {
    private static final String STREAM_CHANNEL_NAME = "xval.cn/jlocationstream";

    private static final long MIN_TIME      = 10000;
    private static final float MIN_DISTANCE  = 10;

    private Long minTime = null;
    private Float minDistance = null;

    private static String PROVIDER = LocationManager.GPS_PROVIDER;
    private final EventChannel mEventChannel;
    private LocationManager mLocationManager;
    private Context mContext;
//    private Location mLocation;
    private JlocationPlugin mPlugin;
    private final PluginPermissionHelper mPermission;
    private EventChannel.EventSink mEvents;
    private LocationListener mLocationListener;

//    private MyLocationListener mListener;

    public JLocation(JlocationPlugin plugin) {
        mPlugin = plugin;
        PluginRegistry.Registrar registrar = plugin.getRegistrar();

        mContext = registrar.activity();

        this.mPermission = plugin.getPermission();

        mEventChannel = new EventChannel(registrar.messenger(), STREAM_CHANNEL_NAME);
        mEventChannel.setStreamHandler(this);
        registrar.addRequestPermissionsResultListener(mPermission);


    }

    public void setMinTime(Long minTime) {
        this.minTime = minTime;
    }

    public void setMinDistance(Float minDistance) {
        this.minDistance = minDistance;
    }

    public void startLocationListen() {
        mLocationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
//        mListener = myLocationListener;
        if (mLocationListener == null) {
            mLocationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    onLocate(location, false);
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                }

                @Override
                public void onProviderEnabled(String provider) {
                }

                @Override
                public void onProviderDisabled(String provider) {
                }
            };

            Location loc = getLastKnownLocation();

            Log.i("xloc", loc.toString());

            onLocate(loc, true);



            mLocationManager.requestLocationUpdates(PROVIDER,
                    minTime == null ? MIN_TIME : minTime,
                    minDistance == null ? MIN_DISTANCE : minDistance,
                    mLocationListener,
                    mContext.getMainLooper());
        }
    }



    private Location getLastKnownLocation() {
        List<String> providers = mLocationManager.getProviders(true);
        Location bestLocation = null;
        for (String provider : providers) {
            Location l = mLocationManager.getLastKnownLocation(provider);
            if (l == null) {
                continue;
            }
            if (bestLocation == null || l.getAccuracy() < bestLocation.getAccuracy()) {
                // Found best last known location: %s", l);
                bestLocation = l;

                this.PROVIDER = provider;
            }
        }
        return bestLocation;
    }
    private Map<String, Double> generateResult(Location location, boolean isLast) {
        HashMap<String, Double> loc = new HashMap<>();
        loc.put("last", isLast ? 1.0 : 0.0);
        loc.put("latitude", location.getLatitude());
        loc.put("longitude", location.getLongitude());
        loc.put("accuracy", (double) location.getAccuracy());
        loc.put("altitude", location.getAltitude());
        loc.put("speed", (double) location.getSpeed());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            loc.put("speed_accuracy", (double) location.getSpeedAccuracyMetersPerSecond());
        }
        loc.put("heading", (double) location.getBearing());
        return loc;
    }

    public void onLocate(Location location, boolean useLast) {
        if (location != null) {
            Map<String, Double> loc = generateResult(location, useLast);
            if (mEvents != null) {
                mEvents.success(loc);
            }
        }
    }

    @Override
    public void onListen(Object o, EventChannel.EventSink eventSink) {
        mEvents = eventSink;
        if (! mPermission.checkPermissions(Manifest.permission.ACCESS_FINE_LOCATION)) {
            mPermission.requestPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }

        startLocationListen();
    }

    public void reStartListen() {
        if (mEvents != null) {
            stopListen();
            startLocationListen();
        }
    }

    public void stopListen() {
        if (mLocationListener != null) {
            mLocationManager.removeUpdates(mLocationListener);
            mLocationManager = null;
            mLocationListener = null;
        }
    }

    public void getLocation(final MethodChannel.Result result) {
        if (! mPermission.checkPermissions(Manifest.permission.ACCESS_FINE_LOCATION)) {
            mPermission.requestPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }
//        Log.i("xloc", "getLocation");
        final LocationManager locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);

        LocationListener listener = new LocationListener() {
            private boolean returned = false;
            @Override
            public void onLocationChanged(Location location) {
//                Log.i("xloc", "onLocationChanged");
                if (location != null && ! returned) {
//                    Log.i("xloc", "location != null");
                    returned = true;
                    Map<String, Double> loc = generateResult(location, false);
//                    Log.i("xloc", String.format("%f, %f", location.getLatitude(), location.getLongitude()));
                    result.success(loc);
                    locationManager.removeUpdates(this);
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };

        locationManager.requestLocationUpdates(PROVIDER,
                1000, 5, listener, mContext.getMainLooper());
    }

    public void responseError(String errTtile, String errMessage, Object o) {
        if (mEvents != null) {
            mEvents.error(errTtile, errMessage, o);
        }
    }

    @Override
    public void onCancel(Object o) {
        mEvents = null;
        stopListen();
    }
}
