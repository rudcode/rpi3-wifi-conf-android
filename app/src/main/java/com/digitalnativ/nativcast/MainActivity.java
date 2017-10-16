package com.digitalnativ.nativcast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.location.LocationManager;

public class MainActivity extends Activity {
    BluetoothSocket mmSocket;

    Spinner btDevicesSpinner;
    Spinner wifiSpinner;
    TextView pskTextView;
    Button startButton;
    TextView messageTextView;
    private Toast toast;

    private WifiManager wifi;
    private WifiScanReceiver wifiReceiver = new WifiScanReceiver();
    private LocationManager location;

    private List<String> ssidList = new ArrayList<String>();
    private ArrayList<BluetoothDevice> btDevicesList = new ArrayList<BluetoothDevice>();
    private ArrayAdapter<String> wifiSpinnerArrayAdapter;
    private DeviceAdapter btDevicesSpinnerArrayAdapter;

    final UUID uuid = UUID.fromString("815425a5-bfac-47bf-9321-c5ff980b5e11");
    final byte delimiter = 33;
    int readBufferPosition = 0;
    final int PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION = 12345;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toast = Toast.makeText(getApplicationContext(), "", Toast.LENGTH_SHORT);

        pskTextView = (TextView) findViewById(R.id.psk_text);
        messageTextView = (TextView) findViewById(R.id.messages_text);

        btDevicesSpinner = (Spinner) findViewById(R.id.devices_spinner);
        btDevicesSpinner.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    refreshDevices();
                }
                return false;
            }
        });

        wifiSpinner = (Spinner) findViewById(R.id.wifi_spinner);
        wifiSpinner.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    refreshWifi();
                }
                return false;
            }
        });

        startButton = (Button) findViewById(R.id.start_button);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String ssid = (String) wifiSpinner.getSelectedItem();
                String psk = pskTextView.getText().toString();

                BluetoothDevice device = (BluetoothDevice) btDevicesSpinner.getSelectedItem();
                (new Thread(new workerThread(ssid, psk, device))).start();
            }
        });

        //wifi dropdown list
        wifiSpinnerArrayAdapter = new ArrayAdapter<String>(MainActivity.this, android.R.layout.simple_spinner_item, ssidList);
        wifiSpinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); // The drop down view
        wifiSpinner.setAdapter(wifiSpinnerArrayAdapter);

        btDevicesSpinnerArrayAdapter = new DeviceAdapter(this, R.layout.spinner_devices, btDevicesList);
        btDevicesSpinner.setAdapter(btDevicesSpinnerArrayAdapter);

        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        location = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);

        //registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        refreshWifi();
        refreshDevices();
    }

    @Override
    protected void onResume() {
        registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        super.onResume();
    }

    @Override
    protected void onPause() {
        try {
            unregisterReceiver(wifiReceiver);
        } catch (IllegalArgumentException ex) {
            // If Receiver not registered
        }
        super.onPause();
    }

    public class WifiScanReceiver extends BroadcastReceiver{
        public void onReceive(Context c, Intent intent) {
            List<ScanResult> wifiScanList = wifi.getScanResults();
            final int N = wifiScanList.size();

            ssidList.clear();
            for(int i = 0; i < N; i++){
                ssidList.add(wifiScanList.get(i).SSID);
                writeOutput("Add: " + (wifiScanList.get(i).SSID));
            }

            wifiSpinnerArrayAdapter.notifyDataSetChanged();
            writeOutput("Scanning completed.");
        }
    }

    protected void refreshWifi()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION);
        } else {
            wifiScan();
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Do something with granted permission
           wifiScan();
        }
    }

    public void wifiScan() {
        writeOutput("Scanning for wifi networks...");
        if(!wifi.isWifiEnabled()) {
            wifi.setWifiEnabled(true);
            writeOutput("Enable wifi...");
            while(!wifi.isWifiEnabled());
        }
        if(!location.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            writeOutput("Location disabled.");
        }
        wifi.startScan();
    }

    private void refreshDevices() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBluetooth, 0);
        }

        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        btDevicesList.clear();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                btDevicesList.add(device);
            }
        }
        btDevicesSpinnerArrayAdapter.notifyDataSetChanged();
    }

    final class workerThread implements Runnable {
        private String ssid;
        private String psk;
        private BluetoothDevice device;

        public workerThread(String ssid, String psk, BluetoothDevice device) {
            this.ssid = ssid;
            this.psk = psk;
            this.device = device;
        }

        public void run() {
            clearOutput();
            writeOutput("Starting config update.");

            writeOutput("Network: " + ssid);
            writeOutput("Device: " + device.getName() + " - " + device.getAddress());

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    toast.setText("Connecting...");
                    toast.setDuration(Toast.LENGTH_LONG);
                    toast.show();
                }
            });
            try {
                mmSocket = device.createRfcommSocketToServiceRecord(uuid);
                if (!mmSocket.isConnected()) {
                    mmSocket.connect();
                    Thread.sleep(1000);
                }

                writeOutput("Connected.");

                OutputStream mmOutputStream = mmSocket.getOutputStream();
                final InputStream mmInputStream = mmSocket.getInputStream();

                waitForResponse(mmInputStream, -1);

                writeOutput("Sending SSID.");

                mmOutputStream.write(ssid.getBytes());
                mmOutputStream.flush();
                waitForResponse(mmInputStream, -1);

                writeOutput("Sending PSK.");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        toast.setText("Sending Network & Password.");
                        toast.setDuration(Toast.LENGTH_LONG * 2);
                        toast.show();
                    }
                });

                mmOutputStream.write(psk.getBytes());
                mmOutputStream.flush();
                waitForResponse(mmInputStream, -1);

                mmSocket.close();

                writeOutput("Success.");
                /*runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        toast.setText("Success");
                        toast.setDuration(Toast.LENGTH_SHORT);
                        toast.show();
                    }
                });*/

            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();

                writeOutput("Failed.");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        toast.setText("Failed to connect to " + device.getName() + ".");
                        toast.setDuration(Toast.LENGTH_SHORT);
                        toast.show();
                    }
                });
            }

            writeOutput("Done.");
        }
    }

    private void writeOutput(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String currentText = messageTextView.getText().toString();
                messageTextView.setText(currentText + "\n" + text);
            }
        });
    }

    private void clearOutput() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                messageTextView.setText("");
            }
        });
    }

    /*
     * TODO actually use the timeout
     */
    private void waitForResponse(InputStream mmInputStream, long timeout) throws IOException {
        int bytesAvailable;

        while (true) {
            bytesAvailable = mmInputStream.available();
            if (bytesAvailable > 0) {
                byte[] packetBytes = new byte[bytesAvailable];
                byte[] readBuffer = new byte[1024];
                mmInputStream.read(packetBytes);

                for (int i = 0; i < bytesAvailable; i++) {
                    byte b = packetBytes[i];

                    if (b == delimiter) {
                        byte[] encodedBytes = new byte[readBufferPosition];
                        System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                        final String data = new String(encodedBytes, "US-ASCII");

                        writeOutput("Received:" + data);
                        if(data.contains("ip")) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    toast.setText("Success " + data);
                                    toast.setDuration(Toast.LENGTH_LONG);
                                    toast.show();
                                }
                            });
                        }
                        return;
                    } else {
                        readBuffer[readBufferPosition++] = b;
                    }
                }
            }
        }
    }
}