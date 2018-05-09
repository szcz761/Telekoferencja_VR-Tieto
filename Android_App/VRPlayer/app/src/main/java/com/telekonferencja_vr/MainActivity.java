package com.telekonferencja_vr;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
//import android.support.annotation.NonNull;
//import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class MainActivity extends Activity implements SensorEventListener {
    //==============================Audio Coonfiguration==============================
    AudioRecord recorder;
    private int sampleRate = 16000 ; // 44100 for music
    private int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    int minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
    final int AUDIO_ACCES =5;


    //==============================Orientation Configuration==============================
    private final float[] mAccelerometerReading = new float[3];
    private final float[] mMagnetometerReading = new float[3];
    private final float[] mRotationMatrix = new float[9];
    private final float[] mOrientationAngles = new float[3];

    private float CalibratedRollValue = 0;
    private float CalibratedPitchValue = 0;
    private float CalibratedYawValue = 0;
    private float RollValue = 0;
    private float PitchValue = 0;
    private float YawValue = 0;


    private SensorManager sensorManager;
    Sensor accelerometer;
    Sensor magnetometer;

    //==============================PORT Configuration==============================
    private final int SendAudioPORT = 5300;
    private final int SendOrientationPORT = 5100;

    //==============================GUI Configuration==============================
    private boolean statusSending = false;

    Button startThreadButton;
    Button calibrateSensorsButton;
    CheckBox checkBoxAudio;
    CheckBox checkBoxOrientation;
    EditText editTextIP;
    TextView textViewOrientation;
    EditText videoURI;
    Button videoButton;

    //Intent sensorActivity;
    //GenericAudioStream audioStream; jakby to wysylanie dziwku sie nei sprawdziło
    Intent videoViewer;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        videoViewer = new Intent(this, VRPlayer.class);

        videoURI = (EditText) findViewById(R.id.videoUrl);
        videoButton = (Button) findViewById(R.id.videoStartButton);
        videoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                videoViewer.putExtra("VideoURL", videoURI.getText().toString());
                startActivity(videoViewer);
            }
        });
        try {
            checkBoxAudio = (CheckBox) findViewById(R.id.checkBoxSendAudio);
            checkBoxOrientation = (CheckBox) findViewById(R.id.checkBoxSendOrientation);
            checkBoxOrientation.setText("Send Orientation: " + SendOrientationPORT);
            checkBoxAudio.setText("Send Audio: " + SendAudioPORT);

            startThreadButton = (Button) findViewById(R.id.buttonStart);
            startThreadButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(!statusSending) {
                        startStreamingAudio();
                        startStreamingOrientation();
                        startThreadButton.setText("Stop Threads");
                        statusSending = true;
                    }
                    else{
                        checkBoxAudio.setChecked(false);
                        checkBoxOrientation.setChecked(false);
                        startThreadButton.setText("Start Threads");
                        statusSending = false;
                    }
                }
            });
            editTextIP = (EditText) findViewById(R.id.EditTextAudioIP);
            textViewOrientation = (TextView) findViewById(R.id.textViewShowOrientation);

            calibrateSensorsButton = (Button) findViewById(R.id.buttonCalibrate);
            calibrateSensorsButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    performCalibration();
                }
            });

            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

            /*if(!ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.RECORD_AUDIO)) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.RECORD_AUDIO},AUDIO_ACCES);
            }*/
        }
        catch (Exception e){
            Log.e("OnCreate",e.getMessage());
        }
    }


    public void startStreamingAudio() {
        Thread streamThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    DatagramSocket socket = new DatagramSocket();
                    byte[] buffer = new byte[minBufSize];
                    DatagramPacket packet;
                    final InetAddress destination = InetAddress.getByName(editTextIP.getText().toString());
                    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, minBufSize * 10);
                    recorder.startRecording();
                    while (checkBoxAudio.isChecked()) {
                        //reading data from MIC into buffer
                        minBufSize = recorder.read(buffer, 0, buffer.length);
                        //putting buffer in the packet
                        packet = new DatagramPacket(buffer, buffer.length, destination, SendAudioPORT);
                        socket.send(packet);

                        String data = (" ");//separator
                        DatagramPacket dp = new DatagramPacket(data.getBytes(), data.length(), destination, SendAudioPORT);
                        socket.send(dp);


                        Log.d("AudioSending","MinBufferSize: " + minBufSize);
                    }
                    recorder.release();
                } catch(UnknownHostException e) {
                    Log.e("AudioSending", "UnknownHostException");
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e("AudioSending", "IOException");
                }
            }

        });
        streamThread.start();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, /*@NonNull*/ String[] permissions, /*@NonNull*/ int[] grantResults) {
        switch (requestCode)
        {
            case AUDIO_ACCES:
                if(!(grantResults.length>0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)){
                    Toast.makeText(getApplicationContext(),"daj pozwolenie",Toast.LENGTH_LONG).show();
                }
        }
    }


    //=============================================ORIENTATION PART=======================================
    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, magnetometer, sensorManager.SENSOR_DELAY_NORMAL);

    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        checkBoxAudio.setChecked(false);
        checkBoxOrientation.setChecked(false);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, mAccelerometerReading,
                    0, mAccelerometerReading.length);
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, mMagnetometerReading,
                    0, mMagnetometerReading.length);
        }
        updateOrientationAngles();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void performCalibration(){
        SensorManager.getRotationMatrix(mRotationMatrix, null, mAccelerometerReading, mMagnetometerReading);
        SensorManager.getOrientation(mRotationMatrix, mOrientationAngles);

        CalibratedRollValue = (int)Math.toDegrees(mOrientationAngles[1]);
        CalibratedPitchValue = (int)Math.toDegrees(mOrientationAngles[2]);
        CalibratedYawValue = (int)Math.toDegrees(mOrientationAngles[0]);
    }

    public void updateOrientationAngles() {
        SensorManager.getRotationMatrix(mRotationMatrix, null, mAccelerometerReading, mMagnetometerReading);
        SensorManager.getOrientation(mRotationMatrix, mOrientationAngles);

        RollValue = (int) (Math.toDegrees(mOrientationAngles[1])) - CalibratedRollValue;
        PitchValue = (int) (Math.toDegrees(mOrientationAngles[2])) - CalibratedPitchValue;
        YawValue = (int) (Math.toDegrees(mOrientationAngles[0])) - CalibratedYawValue;

        //textViewOrientation.setText("R: " +((int) (Math.toDegrees(mOrientationAngles[1] +  Math.PI))) + "\n" + "P: " +((int) (Math.toDegrees(mOrientationAngles[2] +  Math.PI))) + "\n" + "Y: " +((int) (Math.toDegrees(mOrientationAngles[0] +  Math.PI))));
        textViewOrientation.setText("R: " + RollValue + " P: " + PitchValue + " Y: " + YawValue);
    }

    private void startStreamingOrientation() {
        Thread startSending = new Thread(new Runnable() {
            @Override
            public void run() {
                DatagramSocket ds = null;
                try {
                    InetAddress serverAddr = InetAddress.getByName(editTextIP.getText().toString());
                    ds = new DatagramSocket();
                    while(checkBoxOrientation.isChecked()) {
                        //String data = ("R: " + (int) Math.toDegrees(mOrientationAngles[1] +  Math.PI) + " P: " + (int) Math.toDegrees(mOrientationAngles[2] +  Math.PI)+ " Y: " +  (int) Math.toDegrees(mOrientationAngles[0] +  Math.PI));
                        String data = ("R: " + RollValue + " " + " P: " + PitchValue + " Y: " + YawValue);
                        DatagramPacket dp = new DatagramPacket(data.getBytes(), data.length(), serverAddr, SendOrientationPORT);
                        ds.send(dp);
                        Thread.sleep(100);
                        Log.d("OrientationSending", data);
                    }
                   /* byte[] lMsg = new byte[1000];
                    dp = new DatagramPacket(lMsg, lMsg.length);
                    ds.receive(dp);
                    stringData = new String(lMsg, 0, dp.getLength());*/
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    if (ds != null) {
                        ds.close();
                    }
                }
            }
        });
        startSending.start();
    }
}
