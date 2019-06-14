/***********************************
 * Class for GPS calibration wizard page
 * Comes after the tire wizard page
 ***********************************/

/**
 * Import needed libraries
 */
package com.android.ecs193.meterconverter.MeterWizard;
import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.android.ecs193.meterconverter.SettingsFragment;
import com.android.ecs193.meterconverter.R;

public class MeterWizardCalibrate extends AppCompatActivity {

    /**
     * Variables
     */

    TextView text_target;
    TextView text_current;
    TextView text_holdSpeed;
    TextView text_laws;
    Button but_cancel;
    Button but_finish;
    TextView text_timer;
    Integer targetSpeed;
    SettingsFragment mSettingsFragment = new SettingsFragment();
    MeterWizardRPM mMeterWizardRPM = new MeterWizardRPM();
    Boolean firstCountDown = true;
    Boolean finishCalibrate = false;
    LocationManager locationManager; // reference to GPS location manager
    LocationListener locationListener; // listener responding to location updates
    public static Location oldLocation = null; // store previous location

    /**
     * Constants for GPS calibration
     */
    public static final double EARTH_RADIUS = 6371071.0;
    private static final int MY_PERMISSION_ACCESS_FINE_LOCATION = 12; // constant for fine location

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wizard_meter_calibrate);

        // Check which unit is used for setting target speed
        text_target = findViewById(R.id.text_target_speed);
        if (mSettingsFragment.getDriveCheck()) {
            text_target.setText(String.valueOf(mMeterWizardRPM.getTargetSpeed()));
        } else {
            // Set prompt
            if (mSettingsFragment.getUnits() == "kph") {
                targetSpeed = Math.min(50, Integer.valueOf(mSettingsFragment.getMaxSpeed()) / 2);
            } else if (mSettingsFragment.getUnits() == "mph") {
                targetSpeed = Math.min(40,  Integer.valueOf(mSettingsFragment.getMaxSpeed()) / 2);
            }
            text_target.setText(String.valueOf(targetSpeed));
        }

        // Get current speed
        text_current = findViewById(R.id.text_curr_speed);
        locationManager = (LocationManager)this.getSystemService(Context.LOCATION_SERVICE);
        locationListener = new speedCalc();

        // Check if GPS is enabled in phone
        boolean statusOfGPS = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        // Check user's phone permission for GPS on this app
        if ((ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) && (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)){
            ActivityCompat.requestPermissions(this, new String[] {
                    android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION
            }, MY_PERMISSION_ACCESS_FINE_LOCATION);

        // Asked to enable GPS on phone in Alert Dialog box
        } else if (!statusOfGPS) {
            // show alert dialog if Internet is not connected
            AlertDialog.Builder builder = new AlertDialog.Builder(MeterWizardCalibrate.this, R.style.DialogTheme)
                    .setTitle("GPS Disabled")
                    .setMessage("Please turn on GPS in location settings to use this feature")
                    .setCancelable(false)
                    .setPositiveButton("Settings",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    Intent intent = new Intent(
                                            Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                    MeterWizardCalibrate.this.startActivity(intent);
                                    dialog.dismiss();
                                }
                            })
                    .setNegativeButton("Cancel",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    Intent wizIntent = new Intent(MeterWizardCalibrate.this, MeterWizardTireSize.class);
                                    finish();
                                    // Slide from right to left
                                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                                    startActivity(wizIntent);
                                }
                            });
            AlertDialog alert = builder.create();
            alert.show();

        // Start GPS if permitted
        } else if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // minTime: 0 and minDistance: 0 indicates that the provider should make updates as fast as possible. This seems to be about once per second.
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 0, locationListener);
        }

        // Finish button
        but_finish = findViewById(R.id.but_finish);
        but_finish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        // Cancel button
        but_cancel = findViewById(R.id.but_cancel);
        but_cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent wizIntent = new Intent(MeterWizardCalibrate.this, MeterWizardTireSize.class);
                finish();
                // Slide from right to left
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                startActivity(wizIntent);
            }
        });
    }

    /**
     * Function to calibrate GPS by holding the speed for 10 seconds
     */
    public void startCountDown() {

        text_timer = findViewById(R.id.countdownTimer);
        text_holdSpeed = findViewById(R.id.textHoldSpeed);
        text_laws = findViewById(R.id.textLaws);

        mSettingsFragment.startCalibration();

        // 10 seconds count down
        final CountDownTimer showCountDown;
        showCountDown = new CountDownTimer(10000, 1000) {

            int totalSpeed = 0;
            Double targetSpeed = Double.valueOf(text_target.getText().toString());
            Double currentSpeed = Double.valueOf(text_current.getText().toString());
            public void onTick(long millisUntilFinished) {
                text_timer.setVisibility(View.VISIBLE);
                // Continue timer if speed is within +-1 of the target speed
                if (currentSpeed >= (targetSpeed - 1.00) && currentSpeed <= (targetSpeed + 1.00)) {
                    text_timer.setText(String.valueOf(millisUntilFinished / 1000 + 1));
                    totalSpeed += currentSpeed;
                } else {
                    mSettingsFragment.endCalibration();
                    text_timer.setVisibility(View.INVISIBLE);
                    cancel();
                }
            }

            public void onFinish() {
                text_timer.setVisibility(View.GONE);
                but_finish.setVisibility(View.VISIBLE);
                totalSpeed *= 10;
                if (mSettingsFragment.setFinalDrive(String.valueOf(totalSpeed))) {
                    finishCalibrate = true;
                    finish();
                } else {
                    cancel();
                }
            }
        };

        if (firstCountDown) {
            text_laws.setVisibility(View.INVISIBLE);
            new CountDownTimer(2000,1000)  {
                public void onTick(long millisUntilFinished) {
                    text_holdSpeed.setVisibility(View.VISIBLE);
                }

                public void onFinish() {
                    text_holdSpeed.setVisibility(View.GONE);
                    showCountDown.start();
                }
            }.start();
            firstCountDown = false;
        } else {
            showCountDown.start();
        }

        return;
    }

    public void useNewLocation(Location location) {
        // If on first call, set oldLocation and return
        if (oldLocation == null) {
            oldLocation = location;
            return;
        }
        double newLat = location.getLatitude(); // In degrees
        double newLon = location.getLongitude(); // In degrees
        long currTime = location.getTime(); // Milliseconds since 1/1/70
        double distance = calcHaversineDist(oldLocation.getLatitude(), oldLocation.getLongitude(), newLat, newLon);
        int mph = (int) ((distance/1609.0)/(((double)(currTime - oldLocation.getTime())) / 3600000.0)); // Convert meters to miles and ms to hours
        text_current.setText(String.valueOf(mph));
        oldLocation = location;

        if ((String.valueOf(mph) == text_target.getText().toString()) && (finishCalibrate == false)) {
            startCountDown();
        }
    }

    // Haversine Formula for distance b/t pts on sphere given lat. & lon.; see https://en.wikipedia.org/wiki/Haversine_formula
    public static double calcHaversineDist(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2.0)
                * Math.sin(dLon / 2.0);
        double c = 2 * Math.asin(Math.sqrt(a));
        double distInMeters = EARTH_RADIUS * c;
        return distInMeters;
    }

    // Defines a listener that responds to location updates
    class speedCalc implements LocationListener {
        @Override
        public void onLocationChanged(Location location) {

            // Called when a new location is found by the GPS location provider.
            if (location != null) {
                useNewLocation(location);
            }
        }
        // Following functions not used but required in class which implements LocationListener
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}
        @Override
        public void onProviderEnabled(String provider) {}
        @Override
        public void onProviderDisabled(String provider) {}
    }

    private void msg(String s)
    {
        Toast.makeText(getApplicationContext(),s, Toast.LENGTH_LONG).show();
    }
}


