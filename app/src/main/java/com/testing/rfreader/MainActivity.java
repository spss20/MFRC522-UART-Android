package com.testing.rfreader;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private UsbManager usbManager = null;
    private UsbDevice usbDevice = null;

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    private Handler mHandler;
    private UsbSerialPort port;

    private TextView cardUuid;
    private TextView cardSectorData;
    private TextView errorTv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mHandler = new Handler(Looper.getMainLooper());

        findUsbDevice(0x1A86, 0x7523);

        cardUuid = findViewById(R.id.card_uuid);
        cardSectorData = findViewById(R.id.sector_data);
        errorTv = findViewById(R.id.error_tv);
    }

    private class RfReading extends Thread {
        private Rc522 rc522;

        public RfReading(UsbSerialPort port) {
            try {
                rc522 = new Rc522(port);
                rc522.setDebugging(true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            super.run();
            try {
                startReading();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        private void startReading() throws InterruptedException {
            Log.v(TAG, "Starting RF Reader");
            while (true) {

                Thread.sleep(500);

                if (!rc522.request()) {
                    continue;
                }

                Log.v(TAG, "Card Detected");
                runOnUiThread(() -> {
                    cardUuid.setText("N/A");
                    cardSectorData.setText("N/A");
                    errorTv.setText("N/A");
                });

                if (!rc522.antiCollisionDetect()) {
                    continue;
                }

                byte[] uuid = rc522.getUid();
                String uuidText = MyUtils.bytesToHex(uuid);
                runOnUiThread(() -> cardUuid.setText(uuidText));
                Log.v(TAG, "Card UUID: " + uuidText);
                boolean success = rc522.selectTag(uuid);
                if (!success) {
                    Log.v(TAG, "Err: Cannot select the card");
                    runOnUiThread(() -> errorTv.setText("Err: Cannot select the card"));

                    continue;
                }

                byte address = Rc522.getBlockAddress(2, 1);

                byte[] key = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
                byte[] newData = {0x0F, 0x0E, 0x0D, 0x0C, 0x0B, 0x0A, 0x09, 0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01, 0x00};


                //We need to authenticate the card, each sector can have a different key
                boolean result = rc522.authenticateCard(Rc522.AUTH_A, address, key);
                if (!result) {
                    Log.v(TAG, "Err: Authentication Error");
                    runOnUiThread(() -> errorTv.setText("Err: Authentication Error"));
                    continue;
                }
                result = rc522.writeBlock(address, newData);
                if (!result) {
                    Log.v(TAG, "Err: Error writing block data");
                    runOnUiThread(() -> errorTv.setText("Err: Error writing block data"));
                    continue;
                }
                Log.v(TAG, "Sector Written Successfully");
//              resultsText += "Sector written successfully";
                byte[] buffer = new byte[16];
                //Since we're still using the same block, we don't need to authenticate again
                result = rc522.readBlock(address, buffer);
                if (!result) {
                    Log.v(TAG, "Err: Reading Block Error");
                    runOnUiThread(() -> errorTv.setText("Err: Reading Block Error"));
                    continue;
                }
                String blockData = MyUtils.bytesToHex(buffer);
                Log.v(TAG, "Block Data: " + blockData);
                runOnUiThread(() -> cardSectorData.setText(blockData));

                rc522.stopCrypto();

            }
        }
    }

    private void connectDevice() {
        // Find all available drivers from attached devices.
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);

        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            return;
        }

        // Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
        if (connection == null) {
            Log.v(TAG, "Connection null");
            return;
        }

        port = driver.getPorts().get(0); // Most devices have just one port (port 0)

        try {
            port.open(connection);
            port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    new RfReading(port).start();
                }
            }, 1000);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void sendTestPacket() {
        if (port == null) {
            Log.v(TAG, "Port is null");
            return;
        }

        Log.v(TAG, "Sending test packet");
        byte[] data = {(byte) 0x01, (byte) 0x0F};
        try {
            port.write(data, 500);
        } catch (IOException e) {
            e.printStackTrace();
        }
//
//        try {
//            Thread.sleep(200);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }

        byte[] res = new byte[1];
        try {
            port.read(res, 5000);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.v(TAG, "Res " + MyUtils.bytesToHex(res));
    }

    private void findUsbDevice(int vendorId, int productId) {
        System.out.println("Finding Usb Devices");
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        for (UsbDevice usbDevice : usbManager.getDeviceList().values()) {
            System.out.println("USB V: " + usbDevice.getVendorId() + " P: " + usbDevice.getProductId());
            if (usbDevice.getVendorId() == vendorId && usbDevice.getProductId() == productId) {
                this.usbDevice = usbDevice;
            }
        }

        if (usbDevice != null) {
            System.out.println("One Device Found, trying to open");
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                    this,
                    0,
                    new Intent(ACTION_USB_PERMISSION),
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ? PendingIntent.FLAG_MUTABLE : 0
            );
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            registerReceiver(usbReceiver, filter);
            usbManager.requestPermission(usbDevice, permissionIntent);
        } else {
            System.out.println("No Devices Found, Search Complete");
        }
    }

    private BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    usbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (usbManager != null && usbDevice != null) {
                            Toast.makeText(MainActivity.this, "Device Permission Allowed", Toast.LENGTH_SHORT).show();
                            connectDevice();
                        }
                    }
                }
            }
        }
    };
}