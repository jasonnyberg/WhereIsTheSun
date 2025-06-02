package com.example.whereisthesun;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.provider.Settings.Secure; // For Android ID
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.json.JSONObject;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private PreviewView previewView;
    private Button captureButton;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;

    // Sensor related member variables
    private LocationManager locationManager;
    private LocationListener locationListener;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer; // Added
    private SensorEventListener accelerometerListener;

    private Location lastKnownLocation;
    private float[] lastOrientation = new float[3]; // Azimuth, Pitch, Roll (will be in degrees)
    private float[] gravity;     // Stores accelerometer readings (for orientation)
    private float[] geomagnetic; // Stores magnetometer readings

    // Final calculated object angles
    private double objectAzimuth = -1.0;
    private double objectElevation = -1.0;

    // Camera Field of View (default values)
    private float cameraFovHorizontal = 60.0f;
    private float cameraFovVertical = 45.0f;

    // OkHttp client and server URL
    private OkHttpClient httpClient;
    private static final String SERVER_URL = "https://your-placeholder-server.com/api/locationdata"; // Replace with actual server URL
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.previewView);
        captureButton = findViewById(R.id.capture_button);

        cameraExecutor = Executors.newSingleThreadExecutor();
        httpClient = new OkHttpClient(); // Initialize OkHttpClient
        initializeSensors(); // Initialize sensors

        if (allPermissionsGranted()) {
            startCamera();
            // registerSensorListeners(); // Moved to onResume
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePhoto();
            }
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder().build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture);

                    /*
                    try {
                        // ... CameraCharacteristics FOV calculation code ...
                        // This would typically involve getting CameraCharacteristics from cameraProvider
                        // and then getting SENSOR_INFO_PHYSICAL_SIZE and LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                        // For now, we use default values.
                    } catch (Exception e) {
                        Log.e(TAG, "Could not get camera characteristics for FOV, using defaults.", e);
                    }
                    */
                    Log.i(TAG, "Using default FOV - H: " + cameraFovHorizontal + ", V: " + cameraFovVertical);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        if (imageCapture == null) {
            return;
        }

        File photoFile = new File(getExternalMediaDirs()[0], System.currentTimeMillis() + ".jpg");

        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(
                outputOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        String msg = "Photo capture succeeded: " + photoFile.getAbsolutePath();
                        Toast.makeText(getBaseContext(), msg, Toast.LENGTH_SHORT).show();
                        Log.d(TAG, msg);
                            // Add call to image processing
                            processImage(photoFile.getAbsolutePath());
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);
                    }
                });
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(
                    this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
                    registerSensorListeners(); // Register listeners if permissions granted now
            } else {
                Toast.makeText(this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (allPermissionsGranted()) {
            registerSensorListeners();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterSensorListeners();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        // unregisterSensorListeners(); // Already called in onPause
    }

    private void initializeSensors() {
        // Location Manager
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                lastKnownLocation = location;
                Log.d(TAG, "Location Updated: " + location.getLatitude() + ", " + location.getLongitude());
            }
            @Override
            public void onProviderDisabled(@NonNull String provider) {}
            @Override
            public void onProviderEnabled(@NonNull String provider) {}
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}
        };

        // Sensor Manager for Accelerometer
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (magnetometer == null) {
            Log.w(TAG, "Magnetometer not available, orientation accuracy will be lower.");
        }
        // Initialize gravity and geomagnetic arrays
        gravity = new float[3];
        geomagnetic = new float[3];

        accelerometerListener = new SensorEventListener() {
            // float[] gravity; // Now a class member

            @Override
            public void onSensorChanged(SensorEvent event) {
                final float alpha = 0.8f; // Low-pass filter alpha

                if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                    gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
                    gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
                    gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];
                } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                    geomagnetic[0] = alpha * geomagnetic[0] + (1 - alpha) * event.values[0];
                    geomagnetic[1] = alpha * geomagnetic[1] + (1 - alpha) * event.values[1];
                    geomagnetic[2] = alpha * geomagnetic[2] + (1 - alpha) * event.values[2];
                }

                // Check for non-zero to ensure actual readings, especially after initialization
                boolean gravityReady = gravity != null && (gravity[0] != 0.0f || gravity[1] != 0.0f || gravity[2] != 0.0f);
                boolean geomagneticReady = geomagnetic != null && (geomagnetic[0] != 0.0f || geomagnetic[1] != 0.0f || geomagnetic[2] != 0.0f);

                if (gravityReady && geomagneticReady) {
                    float[] R = new float[9];
                    float[] I = new float[9]; // Inclination matrix

                    boolean success = SensorManager.getRotationMatrix(R, I, gravity, geomagnetic);
                    if (success) {
                        float[] orientationAngles = new float[3]; // azimuth, pitch, roll in radians
                        SensorManager.getOrientation(R, orientationAngles);

                        lastOrientation[0] = (float) Math.toDegrees(orientationAngles[0]); // Azimuth
                        lastOrientation[1] = (float) Math.toDegrees(orientationAngles[1]); // Pitch
                        lastOrientation[2] = (float) Math.toDegrees(orientationAngles[2]); // Roll

                        if (lastOrientation[0] < 0) {
                            lastOrientation[0] += 360;
                        }
                    }
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };
    }

    private void registerSensorListeners() {
        // Location
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, locationListener);
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 10, locationListener);
                Log.d(TAG, "Location listeners registered.");
            } catch (SecurityException e) {
                Log.e(TAG, "Failed to register location listener", e);
            }
        }
        // Accelerometer
        if (accelerometer != null) {
            sensorManager.registerListener(accelerometerListener, accelerometer, SensorManager.SENSOR_DELAY_UI);
            Log.d(TAG, "Accelerometer listener registered.");
        }
        // Magnetometer
        if (magnetometer != null) {
            sensorManager.registerListener(accelerometerListener, magnetometer, SensorManager.SENSOR_DELAY_UI);
            Log.d(TAG, "Magnetometer listener registered.");
        }
    }

    private void unregisterSensorListeners() {
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
            Log.d(TAG, "Location listeners unregistered.");
        }
        if (sensorManager != null && accelerometerListener != null) {
            sensorManager.unregisterListener(accelerometerListener);
            Log.d(TAG, "Accelerometer listener unregistered.");
        }
    }

    private void processImage(String imagePath) {
        Mat image = Imgcodecs.imread(imagePath);
        if (image.empty()) {
            Log.e(TAG, "Failed to load image: " + imagePath);
            return;
        }

        Mat grayImage = new Mat();
        Imgproc.cvtColor(image, grayImage, Imgproc.COLOR_BGR2GRAY);

        // Find the brightest spot
        Core.MinMaxLocResult mmr = Core.minMaxLoc(grayImage);
        Point brightestPoint = mmr.maxLoc;
        double maxVal = mmr.maxVal;

        Log.d(TAG, "Brightest point at: " + brightestPoint + " with intensity: " + maxVal);

        // Simple thresholding based on the max brightness (e.g., 80% of max)
        Mat thresholdImage = new Mat();
        double thresholdValue = maxVal * 0.8;
        Imgproc.threshold(grayImage, thresholdImage, thresholdValue, 255, Imgproc.THRESH_BINARY);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(thresholdImage, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        double maxArea = 0;
        Point detectedCenter = null;
        double detectedRadius = 0;

        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area < 100) continue; // Filter out small noise

            MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
            Point center = new Point();
            float[] radius = new float[1];
            Imgproc.minEnclosingCircle(contour2f, center, radius);

            double circularity = 4 * Math.PI * area / (Math.pow(Imgproc.arcLength(contour2f, true), 2));

            Log.d(TAG, "Contour: area=" + area + ", circularity=" + circularity + ", center=" + center + ", radius=" + radius[0]);

            // Prioritize larger, more circular objects that are bright
            if (circularity > 0.6 && area > maxArea) { // Adjust circularity threshold as needed
                // Check if the center of this contour is close to the brightest point found earlier
                // This helps to ensure we are picking a bright, circular object
                double dist = Math.sqrt(Math.pow(center.x - brightestPoint.x, 2) + Math.pow(center.y - brightestPoint.y, 2));
                if (dist < radius[0] * 2) { // Allow some tolerance
                    maxArea = area;
                    detectedCenter = center;
                    detectedRadius = radius[0];
                }
            }
        }

        if (detectedCenter != null) {
            Log.i(TAG, "Sun/Moon detected at: " + detectedCenter + " with radius: " + detectedRadius);
            // For now, just log. Later, this data will be used.
            // You might want to draw on the original image for debugging:
            // Imgproc.circle(image, detectedCenter, (int) detectedRadius, new Scalar(0, 255, 0), 3);
            // Imgproc.putText(image, "Object", detectedCenter, Imgproc.FONT_HERSHEY_SIMPLEX, 1.0, new Scalar(0,0,255));
            // Imgcodecs.imwrite(imagePath.replace(".jpg", "_processed.jpg"), image);

            // Calculate angular offsets
            // Mat image = Imgcodecs.imread(imagePath); // image is already loaded
            int imageWidth = image.cols();
            int imageHeight = image.rows();

            double x_offset_deg = Math.toDegrees(Math.atan2(detectedCenter.x - imageWidth / 2.0,
                    (imageWidth / 2.0) / Math.tan(Math.toRadians(cameraFovHorizontal / 2.0))));
            double y_offset_deg = Math.toDegrees(Math.atan2(detectedCenter.y - imageHeight / 2.0,
                    (imageHeight / 2.0) / Math.tan(Math.toRadians(cameraFovVertical / 2.0))));

            Log.d(TAG, "Object offset from camera center (deg): H=" + x_offset_deg + ", V=" + y_offset_deg);

            float deviceAzimuth = lastOrientation[0];
            float devicePitch = lastOrientation[1];
            float deviceRoll = lastOrientation[2];

            objectAzimuth = (deviceAzimuth + x_offset_deg + 360) % 360;
            objectElevation = devicePitch - y_offset_deg; // y_offset is positive if object is below center, so subtract

            Log.i(TAG, "Device State: Azimuth=" + deviceAzimuth + ", Pitch=" + devicePitch + ", Roll=" + deviceRoll);
            Log.i(TAG, "Object Offsets (camera frame): H_offset=" + x_offset_deg + ", V_offset=" + y_offset_deg);
            Log.w(TAG, "ROUGH ESTIMATE Object World Azimuth (deg): " + objectAzimuth);
            Log.w(TAG, "ROUGH ESTIMATE Object World Elevation (deg): " + objectElevation);

        } else {
            Log.i(TAG, "Sun/Moon not detected with high confidence.");
            objectAzimuth = -1.0; // Reset if not detected
            objectElevation = -1.0;
        }

        // Log sensor data (now includes refined orientation)
        if (lastKnownLocation != null) {
            Log.i(TAG, "Current Location: Lat " + lastKnownLocation.getLatitude() + ", Lon " + lastKnownLocation.getLongitude() + ", Alt " + lastKnownLocation.getAltitude());
        } else {
            Log.i(TAG, "Current Location: Unknown");
        }
        Log.i(TAG, "Current Orientation (Az,El,Roll degrees): " + Arrays.toString(lastOrientation));
        sendDataToServer(); // Call data sending method
    }

    private void sendDataToServer() {
        if (lastKnownLocation == null && objectAzimuth == -1) {
            Log.d(TAG, "No valid data to send to server.");
            return;
        }

        String androidId = Secure.getString(getContentResolver(), Secure.ANDROID_ID);
        if (androidId == null) androidId = "unknown_device";

        try {
            JSONObject jsonData = new JSONObject();
            jsonData.put("timestamp", System.currentTimeMillis());
            jsonData.put("deviceId", androidId);

            if (lastKnownLocation != null) {
                jsonData.put("latitude", lastKnownLocation.getLatitude());
                jsonData.put("longitude", lastKnownLocation.getLongitude());
                jsonData.put("altitude", lastKnownLocation.getAltitude());
            }

            // Device Orientation
            jsonData.put("deviceAzimuth", lastOrientation[0]);
            jsonData.put("devicePitch", lastOrientation[1]);
            jsonData.put("deviceRoll", lastOrientation[2]);

            if (objectAzimuth != -1 && objectElevation != -1) {
                jsonData.put("objectWorldAzimuth", objectAzimuth);
                jsonData.put("objectWorldElevation", objectElevation);
            }

            jsonData.put("cameraFovHorizontal", cameraFovHorizontal);
            jsonData.put("cameraFovVertical", cameraFovVertical);

            RequestBody body = RequestBody.create(jsonData.toString(), JSON);
            Request request = new Request.Builder()
                    .url(SERVER_URL)
                    .post(body)
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Failed to send data to server: " + e.getMessage(), e);
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String responseBody = response.body().string(); // Read once
                    if (response.isSuccessful()) {
                        Log.i(TAG, "Data sent to server successfully. Response: " + responseBody);
                    } else {
                        Log.w(TAG, "Failed to send data. Server responded with: " + response.code() + " " + response.message());
                        Log.w(TAG, "Response body: " + responseBody);
                    }
                    // For OkHttp 4.x, response.body().string() closes the body.
                    // Explicitly closing the response itself is good practice if not using try-with-resources for the response.
                    response.close();
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error preparing data for server: " + e.getMessage(), e);
        }
    }
}
