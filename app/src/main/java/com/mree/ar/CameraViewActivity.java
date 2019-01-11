package com.mree.ar;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.location.Location;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CameraViewActivity extends Activity implements
        SurfaceHolder.Callback, OnLocationChangedListener, OnAzimuthChangedListener{

    private Camera camera;
    private SurfaceHolder surfaceHolder;
    private boolean isCameraViewOn = false;
    private AugmentedPOI augmentedPOI;

    private double azimuthReal = 0;
    private double azimuthTheoretical = 0;
    private static double AZIMUTH_ACCURACY = 5;
    private double myLatitude = 0;
    private double myLongitude = 0;
    private double range;

    private MyCurrentAzimuth myCurrentAzimuth;
    private MyCurrentLocation myCurrentLocation;

    private TextView descriptionTextView;
    private ImageView pointerIcon;
    private TextView pointerRange;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_view);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        setupListeners();
        setupLayout();
        setAugmentedRealityPoint();
    }

    private void setAugmentedRealityPoint() {
        augmentedPOI = new AugmentedPOI(
                "Ahmet Yesevi Üniversitesi Ankara Yerleşkesi",
                "AYÜ Ankara Yerleşkesi",
                39.924684,
                32.830855
        );
    }

    public double calculateTheoreticalAzimuth() {
        double dX = augmentedPOI.getLatitude() - myLatitude;
        double dY = augmentedPOI.getLongitude() - myLongitude;

        double phiAngle;
        double tanPhi;
        double azimuth = 0;

        tanPhi = Math.abs(dY / dX);
        phiAngle = Math.atan(tanPhi);
        phiAngle = Math.toDegrees(phiAngle);

        if (dX > 0 && dY > 0) { // I quater
            return azimuth = phiAngle;
        } else if (dX < 0 && dY > 0) { // II
            return azimuth = 180 - phiAngle;
        } else if (dX < 0 && dY < 0) { // III
            return azimuth = 180 + phiAngle;
        } else if (dX > 0 && dY < 0) { // IV
            return azimuth = 360 - phiAngle;
        }

        return phiAngle;
    }

    private List<Double> calculateAzimuthAccuracy(double azimuth) {
        double minAngle = azimuth - AZIMUTH_ACCURACY;
        double maxAngle = azimuth + AZIMUTH_ACCURACY;
        List<Double> minMax = new ArrayList<Double>();

        if (minAngle < 0)
            minAngle += 360;

        if (maxAngle >= 360)
            maxAngle -= 360;

        minMax.clear();
        minMax.add(minAngle);
        minMax.add(maxAngle);

        return minMax;
    }

    private boolean isBetween(double minAngle, double maxAngle, double azimuth) {
        if (minAngle > maxAngle) {
            if (isBetween(0, maxAngle, azimuth) && isBetween(minAngle, 360, azimuth))
                return true;
        } else {
            if (azimuth > minAngle && azimuth < maxAngle)
                return true;
        }
        return false;
    }

    private void updateDescription() {
        descriptionTextView.setText(augmentedPOI.getName() + "\n"
                + "Teorik Azimut: " + azimuthTheoretical + "\n"
                + "Gerçek Azimut: " + azimuthReal + "\n"
                + "Enlem: " + myLatitude + "\n"
                + "Boylam: " + myLongitude);
    }

    @Override
    public void onLocationChanged(Location location) {
        myLatitude = location.getLatitude();
        myLongitude = location.getLongitude();
        azimuthTheoretical = calculateTheoreticalAzimuth();
        Toast.makeText(this,"Yeni Enlem: " + location.getLatitude() + " Yeni Boylam: " + location.getLongitude(), Toast.LENGTH_SHORT).show();
        range = distance(augmentedPOI.getLatitude(), augmentedPOI.getLongitude(), location.getLatitude(), location.getLongitude(), 'K');
        updateDescription();
    }

    @Override
    public void onAzimuthChanged(float azimuthChangedFrom, float azimuthChangedTo) {
        azimuthReal = azimuthChangedTo;
        azimuthTheoretical = calculateTheoreticalAzimuth();

        pointerIcon = findViewById(R.id.icon);
        pointerRange = findViewById(R.id.range);

        double minAngle = calculateAzimuthAccuracy(azimuthTheoretical).get(0);
        double maxAngle = calculateAzimuthAccuracy(azimuthTheoretical).get(1);

        if (isBetween(minAngle, maxAngle, azimuthReal)) {
            pointerIcon.setVisibility(View.VISIBLE);
            pointerRange.setVisibility(View.VISIBLE);
            pointerRange.setText(String.format(Locale.GERMANY, "%.2f km", range));
        } else {
            pointerIcon.setVisibility(View.INVISIBLE);
            pointerRange.setVisibility(View.INVISIBLE);
        }

        updateDescription();
    }

    @Override
    protected void onStop() {
        myCurrentAzimuth.stop();
        myCurrentLocation.stop();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        myCurrentAzimuth.start();
        myCurrentLocation.start();
    }

    private void setupListeners() {
        myCurrentLocation = new MyCurrentLocation(this);
        myCurrentLocation.buildGoogleApiClient(this);
        myCurrentLocation.start();

        myCurrentAzimuth = new MyCurrentAzimuth(this, this);
        myCurrentAzimuth.start();
    }

    private void setupLayout() {
        descriptionTextView = findViewById(R.id.cameraTextView);

        getWindow().setFormat(PixelFormat.UNKNOWN);
        SurfaceView surfaceView = findViewById(R.id.cameraview);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
        if (isCameraViewOn) {
            camera.stopPreview();
            isCameraViewOn = false;
        }

        if (camera != null) {
            try {
                camera.setPreviewDisplay(surfaceHolder);
                camera.startPreview();
                isCameraViewOn = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        camera = Camera.open();
        camera.setDisplayOrientation(90);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        camera.stopPreview();
        camera.release();
        camera = null;
        isCameraViewOn = false;
    }

    private double distance(double lat1, double lon1, double lat2, double lon2, char unit) {
        double theta = lon1 - lon2;
        double dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2)) + Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) * Math.cos(deg2rad(theta));
        dist = Math.acos(dist);
        dist = rad2deg(dist);
        dist = dist * 60 * 1.1515;
        if (unit == 'K') {
            dist = dist * 1.609344;
        } else if (unit == 'N') {
            dist = dist * 0.8684;
        }
        return (dist);
    }

    private double deg2rad(double deg) {
        return (deg * Math.PI / 180.0);
    }

    private double rad2deg(double rad) {
        return (rad * 180.0 / Math.PI);
    }
}