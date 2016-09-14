package com.wikt.moodlight;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/* This part of the code is responsible for a service that handles the Bluetooth connection to Arduino
and keeps track of notification count.
The user starts the service by choosing the Arduino module from a list of bonded devices.
The user must have already paired the Arduino with Android and enabled the app
to monitor incoming notifications in system settings.
*/

public class BTService extends NotificationListenerService {
    private BluetoothAdapter BT = null;
    private boolean stopThread;
    private String address;

    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    Handler bluetoothIn;
    final int handlerState = 0;
    private int[] setrgb; // Saves the chosen RGB color to persist across notification updates.
    private ConnectingThread mConnectingThread;
    private ConnectedThread mConnectedThread;
    private boolean NotifConnected = true;
    private Receiver commands;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn){
        Log.d("onNotificationPosted", sbn.getPackageName());
        checkNotifications();
    }
    @Override
    public void onNotificationRemoved(StatusBarNotification sbn){
        Log.d("onNotificationRemoved", sbn.getPackageName());
        checkNotifications();
    }

    // On each notification change event, verify if all notifications have been removed or if any
    // new notifications have appeared.

    private void checkNotifications(){
        if (NotifConnected && mConnectedThread != null){
            StatusBarNotification[] active = getActiveNotifications();
            int noOfNotifs = active.length;
            for (StatusBarNotification ntf : active){
                if (!ntf.isClearable() || ntf.isOngoing()){
                    noOfNotifs = noOfNotifs - 1;
                    // Ignore notifications that are difficult to remove.
                }
            }
            Log.d("noOfNotifs", String.valueOf(noOfNotifs));
            if (noOfNotifs > 0) {
                mConnectedThread.write(String.valueOf(setrgb[0]) + "," + String.valueOf(setrgb[1]) + "," + String.valueOf(setrgb[2]));
            } else {
                // No active connections. Turn the LED off.
                mConnectedThread.write("0,0,0");
            }
        }
    }

    @Override
    public void onListenerConnected() {
        Log.d("listener", "connected");
        NotifConnected = true;
    }

    public class Receiver extends BroadcastReceiver {
        //Receives colors from the MainActivity.
        @Override
        public void onReceive(Context context, Intent intent) {
            String cmd = intent.getStringExtra("command");
            Log.d("Receiver", "got request, intent: "+cmd);
            if (cmd.equals("setColor")) {
                setColor(intent.getIntArrayExtra("color"));
                Log.d("Receiver", "set color "+intent.getIntArrayExtra("color").toString());
            }
        }
    }

    @Override
    public void onCreate(){
        commands = new Receiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.wikt.moodlight.BT_COMMAND");
        registerReceiver(commands, filter);
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){

        BT = BluetoothAdapter.getDefaultAdapter();
        if (intent != null) {
            address = intent.getStringExtra("address");
            Log.d("intent was there", address);
        }
        checkBTState();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        stopThread = true;
        if (mConnectedThread != null){
            mConnectedThread.closeStreams();
        }
        if (mConnectingThread != null){
            mConnectingThread.closeSocket();
        }
        unregisterReceiver(commands);
    }

    private void checkBTState(){
        if (BT == null){
            stopSelf();
        } else {
            try {
                Log.d("checkBTstate", "starting");
                BluetoothDevice device = BT.getRemoteDevice(address);
                mConnectingThread = new ConnectingThread(device);
                mConnectingThread.start();
            } catch (IllegalArgumentException e) {
                stopSelf();
            }
        }
    }

    private class ConnectingThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        public ConnectingThread(BluetoothDevice device){
            mmDevice = device;
            BluetoothSocket temp = null;
            try {
                temp = mmDevice.createInsecureRfcommSocketToServiceRecord(BTMODULEUUID);
            } catch (IOException e) {
                stopSelf();
            }
            mmSocket = temp;
            Log.d("ConnectingThread", "socket opened");
        }

        @Override
        public void run(){
            super.run();
            BT.cancelDiscovery();
            try {
                mmSocket.connect();
                mConnectedThread = new ConnectedThread(mmSocket);
                mConnectedThread.start();
            } catch (IOException e){
                try {
                    mmSocket.close();
                    stopSelf();
                } catch (IOException e1) {
                    stopSelf();
                }
            } catch (IllegalStateException e) {
                stopSelf();
            }
        }

        public void closeSocket() {
            try {
                mmSocket.close();
            } catch (IOException e){
                stopSelf();
            }
        }

    }

    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket){
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try{
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.d("ConnectedThread", e.toString());
                stopSelf();
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            Log.d("ConnectedThread", "streams open");
            Intent success = new Intent("com.wikt.moodlight.BT_RESPONSE");
            success.putExtra("success", true);
            sendBroadcast(success);
        }

        public void run() {
            byte[] buffer = new byte[256];
            int bytes;

            while (true && !stopThread) {
                try {
                    bytes = mmInStream.read(buffer);
                    String readMessage = new String(buffer, 0, bytes);
                    //bluetoothIn.obtainMessage(handlerState, bytes, -1, readMessage).sendToTarget();
                } catch (IOException e) {
                    stopSelf();
                    break;
                }
            }
        }
        public void write(String input) {
            byte[] msgBuffer = input.getBytes();
            try {
                mmOutStream.write(msgBuffer);
            } catch (IOException e) {
                stopSelf();
            }
        }

        public void closeStreams() {
            try{
                mmInStream.close();
                mmOutStream.close();
            } catch (IOException e) {
                stopSelf();
            }
        }
    }
    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    public void setColor(int[] rgb){
        setrgb = rgb;
        if (mConnectedThread != null) {
            mConnectedThread.write(String.valueOf(rgb[0]) + "," + String.valueOf(rgb[1]) + "," + String.valueOf(rgb[2]));
            Toast.makeText(getApplicationContext(), "Color has been sent", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getApplicationContext(), "Cannot set color, please wait until connected.", Toast.LENGTH_SHORT).show();
        }
    }
}
