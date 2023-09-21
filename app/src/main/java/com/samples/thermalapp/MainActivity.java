package com.samples.thermalapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.flir.thermalsdk.ErrorCode;
import com.flir.thermalsdk.androidsdk.BuildConfig;
import com.flir.thermalsdk.androidsdk.ThermalSdkAndroid;
import com.flir.thermalsdk.androidsdk.live.connectivity.UsbPermissionHandler;
import com.flir.thermalsdk.live.CommunicationInterface;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener;
import com.flir.thermalsdk.log.ThermalLog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import androidx.appcompat.app.AppCompatActivity;

// Added to solve a thermal camera toggle issue
import android.app.PendingIntent;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import java.util.HashMap;

/**
 * Sample application for scanning a FLIR ONE or a built in emulator
 * <p>
 * See the {@link CameraHandler} for how to preform discovery of a FLIR ONE camera, connecting to it and start streaming images
 * <p>
 * The MainActivity is primarily focused to "glue" different helper classes together and updating the UI components
 * <p/>
 * Please note, this is <b>NOT</b> production quality code, error handling has been kept to a minimum to keep the code as clear and concise as possible
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    //Handles Android permission for eg Network
    private PermissionHandler permissionHandler;

    //Handles network camera operations
    private CameraHandler cameraHandler;

    private Identity connectedIdentity = null;
    private TextView connectionStatus;
    private TextView minTemperature;
    private TextView maxTemperature;

    private ImageView msxImage;
    private Boolean CONNECT = true;
    private LinkedBlockingQueue<FrameDataHolder> framesBuffer = new LinkedBlockingQueue(21);

    private UsbPermissionHandler usbPermissionHandler = new UsbPermissionHandler();
    private MyUsbPermissionHandler myUsbPermissionHandler = new MyUsbPermissionHandler();

    private static double CutoffTemperature = 20;
    private static double CutoffHumidity = 100;
    private static double CutoffDewPoint = 293.15;

    private volatile boolean newData = false;
    private volatile boolean isUpdatingFromSensor = true;
    private int SensorID = 1;

    // Added to solve a thermal camera toggle issue
    private static final String ACTION_USB_PERMISSION = "com.samples.thermalapp.USB_PERMISSION";

    EditText cutoffTemperatureInput;
    EditText cutoffHumidityInput;
    Switch cameraSwitch;
    ToggleButton cameraToggleButton;

    /**
     * Show message on the screen
     */
    public interface ShowMessage {
        void show(String message);
    }

    public static double GetCutoffDewPoint() {
        return CutoffDewPoint;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);




        //links to input feild
        cutoffTemperatureInput = findViewById(R.id.CutoffTermperatureInput);
        cutoffHumidityInput = findViewById(R.id.CutoffHumidityInput);
        cameraToggleButton = findViewById(R.id.toggleButton);

        cameraToggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    SensorID = 2;
                } else {
                    SensorID = 1;
                }
            }
        });

        //when user presses done for temperature we save the input
        ((EditText) findViewById(R.id.CutoffTermperatureInput)).setOnEditorActionListener(
                (v, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                            actionId == EditorInfo.IME_ACTION_DONE ||
                            event != null &&
                                    event.getAction() == KeyEvent.ACTION_DOWN &&
                                    event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                        if (event == null || !event.isShiftPressed()) {
                            // the user is done typing.
                            //saves the input
                            CutoffTemperature = Double.valueOf(cutoffTemperatureInput.getText().toString()) + 273.15;
                            //closes keyboard
                            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                            //updates dew point
                            double CutoffDewPointCelcius = ((Math.pow((CutoffHumidity / 100), 1.0 / 8.0) * (112 + .9 * (CutoffTemperature - 273.15))) + ((.1 * (CutoffTemperature - 273.15)) - 112));
                            CutoffDewPoint =CutoffDewPointCelcius + 273.15;
                            ((TextView) findViewById(R.id.DewPointDisplay)).setText(String.format("%.3f %n", CutoffDewPointCelcius));
                            return true; // consume.
                        }
                    }
                    return false; // pass on to other listeners.
                }
        );

        //when user presses done for humidity we save the input
        ((EditText) findViewById(R.id.CutoffHumidityInput)).setOnEditorActionListener(
                (v, actionId, event) -> {
                    if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                            actionId == EditorInfo.IME_ACTION_DONE ||
                            event != null &&
                                    event.getAction() == KeyEvent.ACTION_DOWN &&
                                    event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                        if (event == null || !event.isShiftPressed()) {
                            // the user is done typing.
                            //saves the input
                            CutoffHumidity = Double.valueOf(cutoffHumidityInput.getText().toString());
                            //system.out.println("Humidity input" + CutoffHumidity);
                            //closes keyboard
                            //v.setCursorVisible(false);
                            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);

                            //calculate dew point
                            //CutoffDewPoint = ((Math.pow((CutoffHumidity / 100), (1.0 / 8.0)) * (112 + .9 * CutoffTemperature)) + ((.1 * CutoffTemperature) - 112));
                            double CutoffDewPointCelcius = ((Math.pow((CutoffHumidity / 100), 1.0 / 8.0) * (112 + .9 * (CutoffTemperature - 273.15))) + ((.1 * (CutoffTemperature - 273.15)) - 112));
                            //double CutoffDewPointCelcius = CutoffDewPoint -273.15;
                            CutoffDewPoint = CutoffDewPointCelcius +273.15;

                            //weird edge case where we get temp data in C instead of in K
                            //system.out.println("h_dpk" + CutoffDewPoint);
                            //system.out.println("h_dpC" + CutoffDewPointCelcius);
                            //system.out.println("h_temp" + CutoffTemperature);
                            //system.out.println("h_hum" + CutoffHumidity);

//                            if(CutoffDewPointCelcius < 0)
//                            {
//                                CutoffDewPointCelcius = CutoffDewPoint;
//                                CutoffDewPoint = 273.15 +CutoffDewPointCelcius;
//                                //system.out.println("dpk" + CutoffDewPoint);
//                                //system.out.println("dpC2" + CutoffDewPointCelcius);
//                                //system.out.println("dpk" + CutoffDewPoint);
//                                //system.out.println("dpK2" + CutoffDewPoint);
//
//                            }

                            ((TextView) findViewById(R.id.DewPointDisplay)).setText(String.format("%.3f %n", CutoffDewPointCelcius));



                            return true; // consume.
                        }
                    }
                    return false; // pass on to other listeners.
                }
        );


        ThermalLog.LogLevel enableLoggingInDebug = BuildConfig.DEBUG ? ThermalLog.LogLevel.DEBUG : ThermalLog.LogLevel.NONE;

        //ThermalSdkAndroid has to be initiated from a Activity with the Application Context to prevent leaking Context,
        // and before ANY using any ThermalSdkAndroid functions
        //ThermalLog will show log from the Thermal SDK in standards android log framework
        ThermalSdkAndroid.init(getApplicationContext(), enableLoggingInDebug);

        permissionHandler = new PermissionHandler(showMessage, MainActivity.this);

        cameraHandler = new CameraHandler();

        setupViews();
        startMultithreading();

        //connection toggle switch
        cameraSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if(isChecked)
            {
                startDiscovery();
                connect(cameraHandler.getFlirOne());
            }
            else {
                disconnect();
            }
        });
    }

    public void startDiscovery(View view) {
        startDiscovery();
    }

    public void stopDiscovery(View view) {
        stopDiscovery();
    }



    /**
     * Handle Android permission request response for Bluetooth permissions
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult() called with: requestCode = [" + requestCode + "], permissions = [" + permissions + "], grantResults = [" + grantResults + "]");
        permissionHandler.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /**
     * Connect to a Camera
     */

    private void connect(Identity identity) {
        //We don't have to stop a discovery but it's nice to do if we have found the camera that we are looking for
        //disconnect();
        cameraHandler.stopDiscovery(discoveryStatusListener);

        if (connectedIdentity != null) {
            Log.d(TAG, "connect(), in *this* code sample we only support one camera connection at the time");
            showMessage.show("connect(), in *this* code sample we only support one camera connection at the time");
            return;
        }

        if (identity == null) {
            Log.d(TAG, "connect(), can't connect, no camera available");
            showMessage.show("connect(), can't connect, no camera available");
            return;
        }

        connectedIdentity = identity;

        //updateConnectionText(identity, "CONNECTING");
        //IF your using "USB_DEVICE_ATTACHED" and "usb-device vendor-id" in the Android Manifest
        // you don't need to request permission, see documentation for more information
        // Check if the connected device is a Flir One and request USB permission if true
        if (UsbPermissionHandler.isFlirOne(identity)) {
            myUsbPermissionHandler.requestFlirOnePermisson(identity, this, permissionListener);
            //usbPermissionHandler.requestFlirOnePermisson(identity, this, permissionListener);
            doConnect(identity);
        }
        else
        {
            // If the device is not a Flir One, proceed to establish a connection (?)
            doConnect(identity);
        }
    }

    private UsbPermissionHandler.UsbPermissionListener permissionListener = new UsbPermissionHandler.UsbPermissionListener() {
        @Override
        public void permissionGranted(Identity identity) {
            doConnect(identity);
            Log.d(TAG, "Permission was granted for identity ");
        }

        @Override
        public void permissionDenied(Identity identity) {
            MainActivity.this.showMessage.show("Permission was denied for identity ");
            Log.d(TAG, "Permission was denied for identity ");
        }

        @Override
        public void error(UsbPermissionHandler.UsbPermissionListener.ErrorType errorType, final Identity identity) {
            MainActivity.this.showMessage.show("Error when asking for permission for FLIR ONE, error:" + errorType + " identity:" + identity);
            Log.d(TAG, "Error when asking for permission for FLIR ONE, error:" + errorType + " identity:" + identity);
        }
    };

    private void doConnect(Identity identity) {
        new Thread(() -> {
            try {
                Log.d(TAG, "Trying to connect...");
                cameraHandler.connect(identity, connectionStatusListener);
                runOnUiThread(() -> {
                    Log.d(TAG, "Starting stream...");
                    //updateConnectionText(identity, "CONNECTING");
                    cameraHandler.startStream(streamDataListener);
                    //updateConnectionText(identity, "CONNECTED");

                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    Log.d(TAG, "Could not connect: " + e);
                    //updateConnectionText(identity, "DISCONNECTED");
                });
            }
        }).start();
    }

    /**
     * Disconnect to a camera
     */
    private void disconnect() {
        //updateConnectionText(connectedIdentity, "DISCONNECTING");
        connectedIdentity = null;
        Log.d(TAG, "disconnect() called with: connectedIdentity = [" + connectedIdentity + "]");
        new Thread(() -> {
            cameraHandler.disconnect();
            runOnUiThread(() -> {
                //updateConnectionText(null, "DISCONNECTED");
            });
        }).start();
    }

    /**
     * Update the UI text for connection status
     */
//    private void updateConnectionText(Identity identity, String status) {
//        String deviceId = identity != null ? identity.deviceId : "";
//        //connectionStatus.setText(getString(R.string.connection_status_text, deviceId + " " + status));
//        connectionStatus.setText(status);
//    }

    /**
     * Start camera discovery
     */
    private void startDiscovery() {
        cameraHandler.startDiscovery(cameraDiscoveryListener, discoveryStatusListener);
    }

    /**
     * Stop camera discovery
     */
    private void stopDiscovery() {
        cameraHandler.stopDiscovery(discoveryStatusListener);
    }

    /**
     * Callback for discovery status, using it to update UI
     */
    private CameraHandler.DiscoveryStatus discoveryStatusListener = new CameraHandler.DiscoveryStatus() {
        @Override
        public void started() {
            //discoveryStatus.setText(getString(R.string.connection_status_text, "discovering"));
        }

        @Override
        public void stopped() {
            //discoveryStatus.setText(getString(R.string.connection_status_text, "not discovering"));
        }
    };

    /**
     * Camera connecting state thermalImageStreamListener, keeps track of if the camera is connected or not
     * <p>
     * Note that callbacks are received on a non-ui thread so have to eg use {@link #runOnUiThread(Runnable)} to interact view UI components
     */
    private ConnectionStatusListener connectionStatusListener = new ConnectionStatusListener() {
        @Override
        public void onDisconnected(@org.jetbrains.annotations.Nullable ErrorCode errorCode) {
            Log.d(TAG, "onDisconnected errorCode:" + errorCode);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //updateConnectionText(connectedIdentity, "DISCONNECTED");
                }
            });
        }
    };

    private final CameraHandler.StreamDataListener streamDataListener = new CameraHandler.StreamDataListener() {

        @Override
        public void images(FrameDataHolder dataHolder) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    msxImage.setImageBitmap(dataHolder.msxBitmap);
                }
            });
        }

        @Override
        public void images(Bitmap msxBitmap, Bitmap dcBitmap) {

            try {
                Log.d(TAG, "loading bitmap");
                framesBuffer.put(new FrameDataHolder(msxBitmap, dcBitmap));
            } catch (InterruptedException e) {
                //if interrupted while waiting for adding a new item in the queue
                Log.e(TAG, "images(), unable to add incoming images to frames buffer, exception:" + e);
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "framebuffer size:" + framesBuffer.size());
                    FrameDataHolder poll = framesBuffer.poll();
                    msxImage.setImageBitmap(poll.msxBitmap);
                    //photoImage.setImageBitmap(poll.dcBitmap);
                }
            });

        }

        @Override
        public void images(Bitmap msxBitmap, Bitmap dcBitmap, Double minC, Double maxC) {

            try {
                Log.d(TAG, "loading bitmap");
                framesBuffer.put(new FrameDataHolder(msxBitmap, dcBitmap));
            } catch (InterruptedException e) {
                //if interrupted while waiting for adding a new item in the queue
                Log.e(TAG, "images(), unable to add incoming images to frames buffer, exception:" + e);
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "framebuffer size:" + framesBuffer.size());
                    FrameDataHolder poll = framesBuffer.poll();
                    msxImage.setImageBitmap(poll.msxBitmap);
                    if (maxC > -8) {
                        minTemperature.setText(String.valueOf(minC));
                        maxTemperature.setText(String.valueOf(maxC));
                    }

                    //photoImage.setImageBitmap(poll.dcBitmap);
                }
            });

        }
    };

    /**
     * Camera Discovery thermalImageStreamListener, is notified if a new camera was found during a active discovery phase
     * <p>
     * Note that callbacks are received on a non-ui thread so have to eg use {@link #runOnUiThread(Runnable)} to interact view UI components
     */
    private DiscoveryEventListener cameraDiscoveryListener = new DiscoveryEventListener() {
        @Override
        public void onCameraFound(Identity identity) {
            Log.d(TAG, "onCameraFound identity:" + identity);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    cameraHandler.add(identity);
                }
            });
        }

        @Override
        public void onDiscoveryError(CommunicationInterface communicationInterface, ErrorCode errorCode) {
            Log.d(TAG, "onDiscoveryError communicationInterface:" + communicationInterface + " errorCode:" + errorCode);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    stopDiscovery();
                    MainActivity.this.showMessage.show("onDiscoveryError communicationInterface:" + communicationInterface + " errorCode:" + errorCode);
                }
            });
        }
    };

    private ShowMessage showMessage = new ShowMessage() {
        @Override
        public void show(String message) {
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
        }
    };


    private void setupViews() {

        cameraSwitch = (Switch) findViewById(R.id.switch3);
        minTemperature = findViewById(R.id.MinimumC);
        maxTemperature = findViewById(R.id.MaximumC);
        msxImage = findViewById(R.id.msx_image);
    }

    //multithreading to read from sensor
    public void startMultithreading() {
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    while (!isInterrupted()) {
                        Thread.sleep(1000);
                        Switch s = findViewById(R.id.switch1);
                        while (s.isChecked()) {
                            //calls methods to read from the sensors and collect output
                            String key = SensorHandler.Authenticate();
                            ArrayList<Double> output = SensorHandler.QuerySamples(key,SensorID);

                            //Log
                            Log.d("Debug", "Key: " + key);
                            Log.d("Debug", "SensorID: " + SensorID);

                            //thread saftey
                            newData = true;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (newData == true) {
                                        //update UI on UI thread
                                        updateUIFromSensor(output.get(0), output.get(1));
                                        newData = false;
                                    }
                                }
                            });
                        }


                    }
                } catch (InterruptedException | IOException e) {
                }
            }
        };

        t.start();
    }


    private void updateUIFromSensor(Double temperature, Double humidity) {
        //changes from F to C
        CutoffTemperature = (temperature - 32) * (5.0 / 9.0);

        //Updates UI text view components
        CutoffHumidity = humidity;
        ((TextView) findViewById(R.id.CutoffHumidityInput)).setText(String.format("%.1f %n", CutoffHumidity));
        ((TextView) findViewById(R.id.CutoffTermperatureInput)).setText(String.format("%.1f %n", CutoffTemperature));

        //calculates dew point
        double CutoffDewPointCelcius = ((Math.pow((CutoffHumidity / 100), 1.0 / 8.0) * (112 + .9 * CutoffTemperature)) + ((.1 * CutoffTemperature) - 112));
        //updates UI text view comonents
        ((TextView) findViewById(R.id.DewPointDisplay)).setText(String.format("%.3f %n", CutoffDewPointCelcius));

        //always save in K
        CutoffDewPoint = 273.15 + CutoffDewPointCelcius;
        CutoffTemperature = 273.15+CutoffTemperature;
    }

    public class MyUsbPermissionHandler extends UsbPermissionHandler {

        @Override
        public void requestFlirOnePermisson(Identity identity, Context context, UsbPermissionListener listener) {
            if (identity != null) {
                // Create an Intent for USB permission request
                PendingIntent permissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);

                // Get the USB Manager
                UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

                // Get a list of USB devices
                HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();

                UsbDevice device = null;
                // Find the required USB device (e.g., device with Vendor ID 0x09CB == FLIR CAMERA)
                for (UsbDevice currentDevice : deviceList.values()) {
                    if (currentDevice.getVendorId() == 0x09CB) {
                        device = currentDevice;
                        break;
                    }
                }

                if (device != null) {
                    // Request USB permission
                    usbManager.requestPermission(device, permissionIntent);
                } else {
                    // Handle the case where the required device is not found
                    // Add necessary handling here
                }
            }
        }
    }
}