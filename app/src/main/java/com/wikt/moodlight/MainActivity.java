package com.wikt.moodlight;


import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/*
This is the main window, responsible for choosing and setting the LED color on the Arduino.
The Set button sends a command via Bluetooth to the Arduino.
The BTService class also automatically turns the LED on or off using the chosen color.
 */

public class MainActivity extends AppCompatActivity {

    String address = null;
    private boolean isServiceActive = false;
    private boolean mBound;
    final int[] rgb = new int[3];
    private SuccessReceiver succreceiver;

    private class SuccessReceiver extends BroadcastReceiver {
        //Just to notify the user that the connection has been established.
        //Otherwise, setting the color won't work if attempted.
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean succ = intent.getBooleanExtra("success", false);
            Log.d("Succ", "got intent: "+String.valueOf(succ));
            if (succ){
                Toast.makeText(getApplicationContext(), "Connected to Moodlight!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //This bit:
        if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT){
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        //is there to avoid bugs caused by the activity reloading itself on orientation change.

        SeekBar rseek = (SeekBar)findViewById(R.id.rBar);
        SeekBar gseek = (SeekBar)findViewById(R.id.gBar);
        SeekBar bseek = (SeekBar)findViewById(R.id.bBar);
        final TextView rval = (TextView)findViewById(R.id.rValue);
        final TextView gval = (TextView)findViewById(R.id.gValue);
        final TextView bval = (TextView)findViewById(R.id.bValue);
        final FrameLayout colorPv = (FrameLayout)findViewById(R.id.colorPv);

        rseek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override
            public void onProgressChanged(SeekBar rseek, int progress, boolean fromUser){
                rval.setText(String.valueOf(progress));
                rgb[0] = progress;
                colorPv.setBackgroundColor(Color.rgb(rgb[0], rgb[1], rgb[2]));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        gseek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override
            public void onProgressChanged(SeekBar gseek, int progress, boolean fromUser){
                gval.setText(String.valueOf(progress));
                rgb[1] = progress;
                colorPv.setBackgroundColor(Color.rgb(rgb[0], rgb[1], rgb[2]));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        bseek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override
            public void onProgressChanged(SeekBar bseek, int progress, boolean fromUser){
                bval.setText(String.valueOf(progress));
                rgb[2] = progress;
                colorPv.setBackgroundColor(Color.rgb(rgb[0], rgb[1], rgb[2]));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        succreceiver = new SuccessReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.wikt.moodlight.BT_RESPONSE");
        registerReceiver(succreceiver, filter);
    }

    @Override
    protected void onResume(){
        super.onResume();
        Intent newint = getIntent();
        address = newint.getStringExtra(DeviceList.EXTRA_ADDRESS);
        if (address != null && !isServiceActive){
            Intent btServiceLaunch = new Intent(MainActivity.this, BTService.class);
            btServiceLaunch.putExtra("address", address);
            startService(btServiceLaunch);
            isServiceActive = true;
        }
    }

    public void goToList(View view){
        if (!isServiceActive) {
            Intent intent = new Intent(this, DeviceList.class);
            startActivity(intent);
        } else Toast.makeText(getApplicationContext(), "Already connected.", Toast.LENGTH_SHORT).show();
    }

    public void sendColor(View view){
        if (isServiceActive) {
            Intent color = new Intent("com.wikt.moodlight.BT_COMMAND");
            color.putExtra("command", "setColor");
            color.putExtra("color", rgb);
            sendBroadcast(color);
        }
    }
    @Override
    protected void onStart(){
        super.onStart();
        if (!mBound){
            Intent btServiceBind = new Intent(MainActivity.this, BTService.class);
            bindService(btServiceBind, mConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onStop(){
        super.onStop();
        if (mBound){
            unbindService(mConnection);
            mBound = false;
        }
    }

    @Override
    protected void onDestroy(){
        unregisterReceiver(succreceiver);
        super.onDestroy();
    }

    ServiceConnection mConnection = new ServiceConnection(){
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };
}

