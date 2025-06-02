# Summary of "WhereIsTheSun" Android App Development

This document summarizes the development process of the "WhereIsTheSun" Android application and outlines potential future work.

## 1. Project Objective

The primary goal was to create an Android application capable of:
- Using the phone's camera to capture images.
- Utilizing GPS and orientation sensors (accelerometer, magnetometer) to determine the device's geolocation and orientation.
- Processing the captured image to detect the sun or moon.
- Calculating the azimuth and elevation of the detected celestial body relative to the Earth's surface at the device's location.
- Reporting these findings (location, orientation, calculated angles, etc.) to a central server.

## 2. Development Steps & Key Features Implemented

The development followed a structured plan, resulting in the following features:

-   **Basic App Structure:** A standard Android project ("WhereIsTheSun") was set up with necessary manifest configurations, permissions (Camera, Location, Internet), and build dependencies.
-   **Camera Functionality:** Integrated `CameraX` for:
    -   Displaying a live camera preview.
    -   Capturing still images.
-   **Image Processing (OpenCV):**
    -   OpenCV library was added to the project.
    -   Captured images are processed to:
        -   Convert to grayscale.
        -   Identify the brightest spot.
        -   Apply binary thresholding based on brightness.
        -   Detect contours (shapes).
        -   Filter contours based on area and circularity to find potential sun/moon candidates.
        -   The center coordinates of the most promising candidate are determined.
-   **Sensor Data Acquisition:**
    -   **Location:** `LocationManager` used to obtain GPS coordinates (latitude, longitude, altitude).
    -   **Orientation:** `SensorManager` used for:
        -   Accelerometer and magnetometer data.
        -   Sensor fusion (`SensorManager.getRotationMatrix` and `SensorManager.getOrientation`) to calculate the device's true Azimuth, Pitch, and Roll.
        -   Low-pass filters applied to sensor readings for improved stability.
-   **Angle Calculation:**
    -   **Object's Angular Offset:** Calculated the horizontal and vertical angular displacement of the detected object from the center of the camera's view. This uses default Camera Field of View (FOV) values (60° H, 45° V) and `atan2` for accuracy.
    -   **World Azimuth/Elevation (Placeholder):** A *very basic placeholder estimation* for the object's final world Azimuth and Elevation was implemented. This currently involves a simplified combination of the device's orientation and the object's angular offset in the image. It was explicitly noted that this calculation requires significant improvement with proper 3D transformation mathematics for accuracy.
-   **Data Reporting:**
    -   **Networking:** `OkHttp` library integrated for HTTP communication.
    -   **Payload:** A JSON object is constructed containing:
        -   Timestamp
        -   Device ID (`Secure.ANDROID_ID`)
        -   Latitude, Longitude, Altitude
        -   Device Azimuth, Pitch, Roll
        -   Calculated (estimated) Object World Azimuth and Elevation
        -   Camera FOV (Horizontal and Vertical) used for calculations.
    -   **Transmission:** The JSON data is POSTed to a placeholder server URL (`https://your-placeholder-server.com/api/locationdata`).
    -   Includes basic error handling and logging for network responses.
-   **Code Submission:** All implemented features were committed to the `feature/initial-app-functionality` branch in the "WhereIsTheSun" repository context.

## 3. Server-Side Component

The plan included a conceptual step for developing a server-side application to receive, store, and visualize the data. This component was acknowledged as a separate development effort and not implemented as part of this phase. The Android app is, however, ready to send data to such a server.

# TODO List for Future Work

To complete and enhance the "WhereIsTheSun" project, the following areas need attention:

## A. Android Application Enhancements

1.  **[CRITICAL] Accurate Azimuth/Elevation Calculation:**
    -   [ ] Replace the current placeholder estimation with a robust 3D transformation algorithm. This involves:
        -   Representing the detected object's direction as a vector in the camera's coordinate system.
        -   Transforming this vector to the device's coordinate system (accounting for camera mounting relative to device screen orientation).
        -   Transforming the device vector to a world coordinate system (e.g., East-North-Up or North-East-Down) using the device's rotation matrix (derived from Azimuth, Pitch, Roll).
        -   Converting the final world vector into Azimuth and Elevation angles.
        -   Consider using rotation matrices or quaternions for these transformations.
2.  **Dynamic Camera Field of View (FOV):**
    -   [ ] Implement logic to dynamically query and use the actual FOV of the device's camera (`CameraCharacteristics`) instead of relying on default values. This will improve the accuracy of the object's angular offset calculation.
3.  **Improved Sun/Moon Detection Logic (Image Processing):**
    -   [ ] **Differentiate Sun vs. Moon:** Implement logic to distinguish between the sun and the moon (e.g., based on time of day, object brightness characteristics, expected size).
    -   [ ] **Robustness:** Enhance detection to be more resilient to partial cloud cover, haze, and other atmospheric conditions.
    -   [ ] **False Positive Reduction:** Improve filtering to reduce chances of identifying other bright objects (reflections, streetlights) as the sun/moon.
    -   [ ] **Consider ML Model:** For advanced detection, explore training a machine learning model (e.g., using TensorFlow Lite) to recognize the sun and moon in images.
4.  **User Interface (UI) and User Experience (UX):**
    -   [ ] **Display Results:** Show the calculated Azimuth and Elevation, and other relevant data, on the app screen.
    -   [ ] **Camera View Enhancements:** Overlay information on the camera preview (e.g., a marker on the detected object, current device orientation).
    -   [ ] **Settings:** Allow users to configure settings (e.g., server URL, reporting frequency).
    -   [ ] **Feedback:** Provide user feedback on detection status, sensor status, and data sending success/failure.
5.  **Calibration:**
    -   [ ] **Magnetometer Calibration:** Guide the user through a magnetometer calibration process (e.g., figure-8 motion) if high magnetic interference is detected or to improve orientation accuracy.
    -   [ ] **Camera Calibration (Advanced):** For very high accuracy, consider if camera intrinsic and extrinsic parameter calibration is needed, though likely overkill for this application's core goal.
6.  **Error Handling and Resilience:**
    -   [ ] **Sensor Availability:** Gracefully handle cases where GPS or other sensors are unavailable or denied permission.
    -   [ ] **Network Handling:** Improve network state checking (e.g., only attempt to send data when connected). Implement retry mechanisms for failed uploads.
    -   [ ] **Offline Storage:** If data cannot be sent immediately, store it locally and attempt to send later.
7.  **Device ID:**
    -   [ ] Replace `Secure.ANDROID_ID` with a more robust unique identifier (e.g., a GUID generated and stored by the app in SharedPreferences).
8.  **True North vs. Magnetic North:**
    -   [ ] Account for magnetic declination (difference between magnetic north and true north) by fetching it based on current location and time, or allowing manual input. Apply this to the Azimuth reading from `SensorManager.getOrientation()`.
9.  **Background Processing/Service:**
    -   [ ] Consider if continuous monitoring and reporting (even when app is not in foreground) is a requirement. If so, implement using a Foreground Service.

## B. Server-Side Development

1.  **Backend Application:**
    -   [ ] Choose a technology stack (e.g., Python/Flask, Node.js/Express).
    -   [ ] Implement an API endpoint to receive the JSON data from the Android app.
    -   [ ] Validate and sanitize incoming data.
2.  **Database:**
    -   [ ] Set up a database (e.g., PostgreSQL, MongoDB) to store the time-series data from multiple devices.
    -   [ ] Design an appropriate schema.
3.  **Data Visualization:**
    -   [ ] Develop a web interface to display the collected data.
    -   [ ] Use a mapping library (e.g., Leaflet, Mapbox GL JS, CesiumJS) to plot data points on a 2D map or 3D globe.
    -   [ ] Visualize the calculated sun/moon vectors from each data point.
    -   [ ] Implement filters and tools to explore the data (e.g., by time, location).
4.  **Security and Scalability:**
    -   [ ] Secure the API endpoint (e.g., using API keys or other authentication).
    -   [ ] Design the server infrastructure to handle potential load.

## C. Testing

-   [ ] **Unit Tests:** Write unit tests for individual components (e.g., sensor data processing, angle calculations once refined).
-   [ ] **Integration Tests:** Test interactions between components (e.g., image capture to data reporting).
-   [ ] **Field Testing:** Test the app in various real-world conditions (different times of day, weather, locations) to assess accuracy and robustness.
