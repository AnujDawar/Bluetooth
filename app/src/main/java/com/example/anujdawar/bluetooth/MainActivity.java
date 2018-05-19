package com.example.anujdawar.bluetooth;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity implements AdapterView.OnItemClickListener
{
    protected static final int SUCCESS_CONNECT = 0;
    protected static final int MESSAGE_READ = 1;
    ListView listView;
    ArrayAdapter<String> listAdapter;
    BluetoothAdapter btAdapter;
    Set<BluetoothDevice> devicesArray;
    BroadcastReceiver receiver;
    IntentFilter filter;
    ArrayList<BluetoothDevice> devices;
    public static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @SuppressLint("HandlerLeak")
    Handler mHandler1 =new Handler()
    {
        public void handleMessage(Message msgDisplay)
        {
            Toast.makeText(MainActivity.this, msgDisplay.obj.toString(), Toast.LENGTH_SHORT).show();
        }
    };

    Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what)
            {
                case SUCCESS_CONNECT:
                    ConnectedThread connectedThread = new ConnectedThread((BluetoothSocket)msg.obj);
                    Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_SHORT).show();

                    String s = "SUCCESSFULLY CONNECTED";
                    connectedThread.write(s.getBytes());
                    break;

                case MESSAGE_READ:
                    byte[] readBuf = (byte[])msg.obj;
                    String string = new String(readBuf);
                    Toast.makeText(getApplicationContext(), string, Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        init();

        if(btAdapter == null)
        {
            Toast.makeText(this, "No Bluetooth Detected", Toast.LENGTH_SHORT).show();
            finish();
        }

        else
            if(!btAdapter.isEnabled())
                turnOnBT();

        getPairedDevices();
        startDiscovery();
    }

    private void init()
    {
        listView = (ListView) findViewById(R.id.listview);
        listView.setOnItemClickListener(this);
        listAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, 0);
        listView.setAdapter(listAdapter);
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        devices = new ArrayList<BluetoothDevice>();

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                String action = intent.getAction();

                if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action))
                    if(btAdapter.getState() == btAdapter.STATE_OFF)
                        turnOnBT();
            }
        };

        registerReceiver(receiver, filter);
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        registerReceiver(receiver, filter);
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(receiver, filter);
        filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(receiver, filter);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(resultCode == RESULT_CANCELED)
        {
            Toast.makeText(this, "Bluetooth must be enabled to continue", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void getPairedDevices()
    {
        devicesArray = btAdapter.getBondedDevices();

        if(devicesArray.size() > 0)
            for(BluetoothDevice device : devicesArray)
            {
                listAdapter.add(device.getName() + "\n" + device.getAddress());
                devices.add(device);
            }
    }

    private void turnOnBT()
    {
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(intent, 1);
    }

    private void startDiscovery()
    {
        btAdapter.cancelDiscovery();
        btAdapter.startDiscovery();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

        if(btAdapter.isDiscovering())
            btAdapter.cancelDiscovery();

        if(listAdapter.getItem(i).contains("raspberry"))
        {
            BluetoothDevice selectedDevice = devices.get(i);
            ConnectThread connect = new ConnectThread(selectedDevice);
            connect.run();
        }

        else
            Toast.makeText(this, "Device is Not Paired", Toast.LENGTH_SHORT).show();
    }

    private class ConnectThread extends Thread
    {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device)
        {
            BluetoothSocket temp = null;
            mmDevice = device;

            try{
                temp = device.createRfcommSocketToServiceRecord(MY_UUID);
            }catch (Exception e){
                Toast.makeText(MainActivity.this, e.toString(), Toast.LENGTH_SHORT).show();
            }

            mmSocket = temp;
        }

        public void run()
        {
            btAdapter.cancelDiscovery();

            try{
                mmSocket.connect();
            }catch (IOException connectException){
                try{
                    mmSocket.close();
                }catch (IOException closeException) { }
                return;
            }

            mHandler.obtainMessage(SUCCESS_CONNECT, mmSocket).sendToTarget();
        }

        public void cancel()
        {
            try{
                mmSocket.close();
            }catch (IOException e) { }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer;  // buffer store for the stream
            int bytes; // bytes returned from read()

            while (true) {
                try {

                    Message rxdMsg=Message.obtain();
                    rxdMsg.obj = mmInStream.read();
                    mHandler1.sendMessage(rxdMsg);


//                    buffer = new byte[1024];
//                    bytes = mmInStream.read(buffer);
//                    mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
//                            .sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }
}