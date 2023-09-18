
package com.samples.thermalapp;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.flir.thermalsdk.androidsdk.image.BitmapAndroid;
import com.flir.thermalsdk.image.ColorDistribution;
import com.flir.thermalsdk.image.Rectangle;
import com.flir.thermalsdk.image.Scale;
import com.flir.thermalsdk.image.TemperatureUnit;
import com.flir.thermalsdk.image.ThermalImage;
import com.flir.thermalsdk.image.fusion.FusionMode;
import com.flir.thermalsdk.image.palettes.PaletteManager;
import com.flir.thermalsdk.live.Camera;
import com.flir.thermalsdk.live.CommunicationInterface;
import com.flir.thermalsdk.live.ConnectParameters;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener;
import com.flir.thermalsdk.live.discovery.DiscoveryFactory;
import com.flir.thermalsdk.live.streaming.ThermalImageStreamListener;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

class CameraHandler {

    private static final String TAG = "CameraHandler";
    private StreamDataListener streamDataListener;

    public interface StreamDataListener {
        void images(FrameDataHolder dataHolder);
        void images(Bitmap msxBitmap, Bitmap dcBitmap);
        void images(Bitmap msxBitmap, Bitmap dcBitmap,Double MinTemp,Double MaxTemp);


    }

    //Discovered FLIR cameras
    LinkedList<Identity> foundCameraIdentities = new LinkedList<>();

    //A FLIR Camera
    private Camera camera;


    public interface DiscoveryStatus {
        void started();

        void stopped();
    }

    public CameraHandler() {
    }

    /**
     * Start discovery of USB and Emulators
     */
    public void startDiscovery(DiscoveryEventListener cameraDiscoveryListener, DiscoveryStatus discoveryStatus) {
        DiscoveryFactory.getInstance().scan(cameraDiscoveryListener, CommunicationInterface.EMULATOR, CommunicationInterface.USB);
        discoveryStatus.started();
    }

    /**
     * Stop discovery of USB and Emulators
     */
    public void stopDiscovery(DiscoveryStatus discoveryStatus) {
        DiscoveryFactory.getInstance().stop(CommunicationInterface.EMULATOR, CommunicationInterface.USB);
        discoveryStatus.stopped();
    }

    public void connect(Identity identity, ConnectionStatusListener connectionStatusListener) throws IOException {
        camera = new Camera();
        camera.connect(identity, connectionStatusListener, new ConnectParameters());
    }

    public void disconnect() {
        if (camera == null) {
            return;
        }
        if (camera.isGrabbing()) {
            camera.unsubscribeAllStreams();
        }
        camera.disconnect();
    }

    /**
     * Start a stream of {@link ThermalImage}s images from a FLIR ONE or emulator
     */
    public void startStream(StreamDataListener listener) {
        this.streamDataListener = listener;
        camera.subscribeStream(thermalImageStreamListener);
    }

    /**
     * Stop a stream of {@link ThermalImage}s images from a FLIR ONE or emulator
     */
    public void stopStream(ThermalImageStreamListener listener) {
        camera.unsubscribeStream(listener);
    }

    /**
     * Add a found camera to the list of known cameras
     */
    public void add(Identity identity) {
        foundCameraIdentities.add(identity);
    }

    @Nullable
    public Identity get(int i) {
        return foundCameraIdentities.get(i);
    }

    /**
     * Get a read only list of all found cameras
     */
    @Nullable
    public List<Identity> getCameraList() {
        return Collections.unmodifiableList(foundCameraIdentities);
    }

    /**
     * Clear all known network cameras
     */
    public void clear() {
        foundCameraIdentities.clear();
    }

    @Nullable
    public Identity getCppEmulator() {
        for (Identity foundCameraIdentity : foundCameraIdentities) {
            if (foundCameraIdentity.deviceId.contains("C++ Emulator")) {
                return foundCameraIdentity;
            }
        }
        return null;
    }

    @Nullable
    public Identity getFlirOneEmulator() {
        for (Identity foundCameraIdentity : foundCameraIdentities) {
            if (foundCameraIdentity.deviceId.contains("EMULATED FLIR ONE")) {
                return foundCameraIdentity;
            }
        }
        return null;
    }

    @Nullable
    public Identity getFlirOne() {
        for (Identity foundCameraIdentity : foundCameraIdentities) {
            boolean isFlirOneEmulator = foundCameraIdentity.deviceId.contains("EMULATED FLIR ONE");
            boolean isCppEmulator = foundCameraIdentity.deviceId.contains("C++ Emulator");
            if (!isFlirOneEmulator && !isCppEmulator) {
                return foundCameraIdentity;
            }
        }

        return null;
    }

    private void withImage(ThermalImageStreamListener listener, Camera.Consumer<ThermalImage> functionToRun) {
        camera.withImage(listener, functionToRun);
    }


    /**
     * Called whenever there is a new Thermal Image available, should be used in conjunction with {@link Camera.Consumer}
     */
    private final ThermalImageStreamListener thermalImageStreamListener = new ThermalImageStreamListener() {
        @Override
        public void onImageReceived() {
            //Will be called on a non-ui thread
            Log.d(TAG, "onImageReceived(), we got another ThermalImage");
            withImage(this, handleIncomingImage);
        }
    };

    /**
     * Function to process a Thermal Image and update UI
     */
    private final Camera.Consumer<ThermalImage> handleIncomingImage = new Camera.Consumer<ThermalImage>() {
        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void accept(ThermalImage thermalImage) {
            Log.d(TAG, "accept() called with: thermalImage = [" + thermalImage.getDescription() + "]");
            //Will be called on a non-ui thread,
            // extract information on the background thread and send the specific information to the UI thread

            //Get a bitmap with only IR data
            Bitmap msxBitmap;
            {
                //settings
                thermalImage.setTemperatureUnit(TemperatureUnit.CELSIUS);
                thermalImage.getFusion().setFusionMode(FusionMode.THERMAL_ONLY);
                thermalImage.setPalette(PaletteManager.getDefaultPalettes().get(0));
                thermalImage.setColorDistribution(ColorDistribution.HISTOGRAM_EQUALIZATION);
                int red = Color.parseColor("#ae0700");
                int neonGreen = Color.parseColor("#39ff14");

                //sets scale
                Scale scale = thermalImage.getScale();
                double minC = Double.parseDouble(String.valueOf(scale.getRangeMin()).substring(0,4));
                double maxC = Double.parseDouble(String.valueOf(scale.getRangeMax()).substring(0,4));

                //creates the bitmap of temperature data
                msxBitmap = BitmapAndroid.createBitmap(thermalImage.getImage()).getBitMap();

                //gets actual raw temp values
                Rectangle rectangle = new Rectangle(0, 0, thermalImage.getWidth(), thermalImage.getHeight());
                double[] all_temp = thermalImage.getValues(rectangle);
                double[][] temps = new double[thermalImage.getWidth()][thermalImage.getHeight()];
                int dotsize = 3;
                //loop every 9 pixels
                for (int i = 0; i < thermalImage.getWidth()-dotsize; i+=9) {
                    for(int j=0;j<thermalImage.getHeight()-dotsize;j+=9)
                    {
                        if(all_temp[j*thermalImage.getWidth() + i] < MainActivity.GetCutoffDewPoint())
                        {
                            //makes a three by three green dot
                            for(int x=0;x<dotsize;x++)
                            {
                                for(int y=0;y<dotsize;y++) {
                                    msxBitmap.setPixel(i+x,j+y, neonGreen);
                                }
                            }
                        }
                    }
                }


                //Get a bitmap with the visual image, it might have different dimensions then the bitmap from THERMAL_ONLY
                Bitmap dcBitmap = BitmapAndroid.createBitmap(thermalImage.getFusion().getPhoto()).getBitMap();
                //System.out.println(dcBitmap.getWidth()+" "+dcBitmap.getHeight());
                Log.d(TAG, "adding images to cache");
                streamDataListener.images(msxBitmap, dcBitmap,minC,maxC);
                Log.d(TAG, "dc1234: " + dcBitmap);
            }

        }


    };
}

