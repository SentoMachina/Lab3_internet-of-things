package com.galarzaa.androidthings.samples;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.galarzaa.androidthings.Rc522;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.SpiDevice;
import com.galarzaa.androidthings.samples.MVVM.VM.NPNHomeViewModel;
import com.galarzaa.androidthings.samples.MVVM.View.NPNHomeView;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.regex.Pattern;

import com.galarzaa.androidthings.samples.Network.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity implements NPNHomeView  {
    private static final String TAG = "API Sender";
    private Rc522 mRc522;
    RfidTask mRfidTask;
    private TextView mTagDetectedView;
    private TextView mTagUidView;
    private TextView mTagResultsView;
    private Button button;
    private Gpio mLedGpio_R, mLedGpio_G, mLedGpio_B;
    NPNHomeViewModel mHomeViewModel;

    private SpiDevice spiDevice;
    private Gpio gpioReset;

    private static final String SPI_PORT = "SPI0.0";
    private static final String PIN_RESET = "BCM25";

    String resultsText = "";

    String UIDresult = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTagDetectedView = (TextView)findViewById(R.id.tag_read);
        mTagUidView = (TextView)findViewById(R.id.tag_uid);
        mTagResultsView = (TextView) findViewById(R.id.tag_results);
        button = (Button)findViewById(R.id.button);
        mHomeViewModel = new NPNHomeViewModel();
        mHomeViewModel.attach(this, this);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRfidTask = new RfidTask(mRc522);
                mRfidTask.execute();
                ((Button)v).setText(R.string.reading);
                //UIDresult = mRfidTask.getUIDdata();
                ;// get UID Data from Card
               // UIDresult = "12345678";
               // senderDataToServer();


            }
        });
        PeripheralManager pioService = PeripheralManager.getInstance();
        try {
            spiDevice = pioService.openSpiDevice(SPI_PORT);
            gpioReset = pioService.openGpio(PIN_RESET);
            mRc522 = new Rc522(spiDevice, gpioReset);
            mRc522.setDebugging(true);
            ///
            String[] pinName = BoardDefaults.getGPIOForLED();
            mLedGpio_R = PeripheralManager.getInstance().openGpio(pinName[0]);
            mLedGpio_R.setDirection(Gpio.DIRECTION_OUT_INITIALLY_HIGH);
            mLedGpio_G = PeripheralManager.getInstance().openGpio(pinName[1]);
            mLedGpio_G.setDirection(Gpio.DIRECTION_OUT_INITIALLY_HIGH);
            mLedGpio_B = PeripheralManager.getInstance().openGpio(pinName[2]);
            mLedGpio_B.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            //

        } catch (IOException e) {
            Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }

        // Set Network API

        VolleyRemoteApiClient.createInstance(this);

//        Map<String, String> headerGA = new HashMap<>();
//
//        headerGA.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/59.0.3071.115 Safari/537.36");
//        headerGA.put("Content-Type", "application/x-www-form-urlencoded");

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try{
            if(spiDevice != null){
                spiDevice.close();
            }
            if(gpioReset != null){
                gpioReset.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void senderDataToServer() {

        String urlSalinity = "http://demo1.chipfc.com/SensorValue/update?sensorid=7&sensorvalue=";
        if(UIDresult != "") {
            urlSalinity += UIDresult;
        }else {
            Log.d(TAG,"UID is empty. Check RFcard");
        }
        //mHomeViewModel.updateToServer(urlSalinity);

        Map<String, String> headerGA = new HashMap<>();

        headerGA.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/59.0.3071.115 Safari/537.36");
        //headerGA.put("Content-Type", "application/x-www-form-urlencoded");

        Map<String, String> params = new HashMap<>();
        mHomeViewModel.updateToServer(urlSalinity);
        VolleyRemoteApiClient.getInstance().post(urlSalinity,"",params, new ApiResponseListener<String>() {
            @Override
            public void onSuccess(String response) {
                Log.d(TAG, "Sending data to server");
            }

            @Override
            public void onError(String error) {
                Log.d(TAG, "Error when sendiing data to server");
            }
        });

    }
    @Override
    public void onErrorUpdateServer(String message) {
        //txtConsole.setText("Request server is fail");
        Log.d(TAG, "Request server is fail");
    }

    @Override
    public void onSuccessUpdateServer(String message) {
        Log.d(TAG, "Request server is successful " + message);
    }

    private class RfidTask extends AsyncTask<Object, Object, Boolean> {
        private static final String TAG = "RfidTask";
        private Rc522 rc522;

        RfidTask(Rc522 rc522){
            this.rc522 = rc522;
        }

        @Override
        protected void onPreExecute() {
            button.setEnabled(false);
            mTagResultsView.setVisibility(View.GONE);
            mTagDetectedView.setVisibility(View.GONE);
            mTagUidView.setVisibility(View.GONE);
            resultsText = "";
        }

        // Get UID function
        public String getUIDdata() {
            byte[] UIDByte = rc522.getUid();
            String UIDString = new String(UIDByte);
            return UIDString;
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            rc522.stopCrypto();
            while(true){
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return null;
                }
                //Check if a RFID tag has been found
                if(!rc522.request()){
                    continue;
                }
                //Check for collision errors
                if(!rc522.antiCollisionDetect()){
                    continue;
                }
                byte[] uuid = rc522.getUid();
                UIDresult = rc522.getUidString();
                //Log.d("Hello",UIDresult);
                UIDresult = UIDresult.replace('-',' ');

                UIDresult = UIDresult.replace(" ","");
                Log.d("Hello",UIDresult);
                return rc522.selectTag(uuid);
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if(!success){
                mTagResultsView.setText(R.string.unknown_error);
                return;
            }
            // Try to avoid doing any non RC522 operations until you're done communicating with it.
            byte address = Rc522.getBlockAddress(2,1);
            // Mifare's card default key A and key B, the key may have been changed previously
            byte[] key = {(byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF};
            // Each sector holds 16 bytes
            // Data that will be written to sector 2, block 1
            //String name = "Tai 1996 1413365";

            //byte[] newData = name.getBytes();
            //byte[] newData = {0x0F,0x0E};
            // In this case, Rc522.AUTH_A or Rc522.AUTH_B can be used
            try {
                //We need to authenticate the card, each sector can have a different key
                boolean result = rc522.authenticateCard(Rc522.AUTH_A, address, key);
                if (!result) {
                    mTagResultsView.setText(R.string.authetication_error);
                    try {
                        mLedGpio_R.setValue(false);
                        mLedGpio_G.setValue(true);
                        mLedGpio_B.setValue(true);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    return;
                }
                //result = rc522.writeBlock(address, newData);
                if(!result){
                    mTagResultsView.setText(R.string.write_error);
                    return;
                }
                //resultsText += "Sector written successfully";
                byte[] buffer = new byte[16];
                //Since we're still using the same block, we don't need to authenticate again
                result = rc522.readBlock(address, buffer);
                if(!result){
                    mTagResultsView.setText(R.string.read_error);
                    return;
                }
                resultsText += "\nSector read successfully: "+ new String(buffer);
                try {
                    mLedGpio_R.setValue(true);
                    mLedGpio_G.setValue(false);
                    mLedGpio_B.setValue(true);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                rc522.stopCrypto();
                mTagResultsView.setText(resultsText);
            }finally{
                button.setEnabled(true);
                button.setText(R.string.start);
                mTagUidView.setText(getString(R.string.tag_uid,rc522.getUidString()));
                senderDataToServer();
                mTagResultsView.setVisibility(View.VISIBLE);
                mTagDetectedView.setVisibility(View.VISIBLE);
                mTagUidView.setVisibility(View.VISIBLE);
            }
        }
    }
}
