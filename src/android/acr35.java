package me.stuartphillips.plugins;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.acs.audiojack.AudioJackReader;
import com.acs.audiojack.ReaderException;

import android.media.AudioManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;

import java.lang.Override;
import java.lang.Runnable;
import java.lang.System;
import java.lang.Thread;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.acs.audiojack.AesTrackData;
import com.acs.audiojack.AudioJackReader;
import com.acs.audiojack.DukptReceiver;
import com.acs.audiojack.DukptTrackData;
import com.acs.audiojack.Result;
import com.acs.audiojack.Status;
import com.acs.audiojack.Track1Data;
import com.acs.audiojack.Track2Data;
import com.acs.audiojack.TrackData;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;

/**
 * This class allows control of the ACR35 reader sleep state and PICC commands
 */
public class acr35 extends CordovaPlugin {

    private Transmitter transmitter;
    private AudioManager mAudioManager;
    private AudioJackReader mReader;
    private Context mContext;

    
    public static final String DEFAULT_MASTER_KEY_STRING = "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00";
    public static final String DEFAULT_AES_KEY_STRING = "4E 61 74 68 61 6E 2E 4C 69 20 54 65 64 64 79 20";
    public static final String DEFAULT_IKSN_STRING = "FF FF 98 76 54 32 10 E0 00 00";
    public static final String DEFAULT_IPEK_STRING = "6A C2 92 FA A1 31 5B 4D 85 8A B3 A3 D7 D5 93 3A";

     private static String TAG = "ACS_Reader";

    private byte[] mMasterKey = new byte[16];
    private byte[] mNewMasterKey = new byte[16];
    private byte[] mAesKey = new byte[16];
    private byte[] mIksn = new byte[10];
    private byte[] mIpek = new byte[16];

   
    private Track1Data mTrack1Data = null;
    private Track2Data mTrack2Data = null;
    private Track1Data mTrack1MaskedData = null;
    private Track2Data mTrack2MaskedData = null;

    
    private boolean firstRun = true;    /** Is this plugin being initialised? */
    private boolean firstReset = true;  /** Is this the first reset of the reader? */

    /** APDU command for reading a card's UID */
    private final byte[] apdu = { (byte) 0xFF, (byte) 0xCA, (byte) 0x00, (byte) 0x00, (byte) 0x00 };
    /** Timeout for APDU response (in <b>seconds</b>) */
    private final int timeout = 1;

    /**
     * Converts raw data into a hexidecimal string
     *
     * @param buffer: raw data in the form of a byte array
     * @return a string containing the data in hexidecimal form
     */
    private String bytesToHex(byte[] buffer) {
        String bufferString = "";
        if (buffer != null) {
            for(int i = 0; i < buffer.length; i++) {
                String hexChar = Integer.toHexString(buffer[i] & 0xFF);
                if (hexChar.length() == 1) {
                    hexChar = "0" + hexChar;
                }
                bufferString += hexChar.toUpperCase(Locale.US) + " ";
            }
        }
        return bufferString;
    }
    

         private void showAesTrackData(final CallbackContext callbackContext ,AesTrackData trackData) {

        byte[] decryptedTrackData = null;

            /* Decrypt the track data. */
        try {

            decryptedTrackData = aesDecrypt(mAesKey,
                    trackData.getTrackData());

           // Log.d(TAG, decryptedTrackData.toString());

        } catch (GeneralSecurityException e) {

                /* Show the track data. */
          //  Log.d(TAG, "Track 1 " + mTrack1Data.getTrack1String());
          //  Log.d(TAG, "Track 2 " + mTrack2Data.getTrack2String());
           PluginResult result = new PluginResult(PluginResult.Status.ERROR, e.getMessage());
                        result.setKeepCallback(true);
                        callbackContext.sendPluginResult(result);

            return;
        }

            /* Verify the track data. */
        if (!mReader.verifyData(decryptedTrackData)) {

                /* Show the track data. */
             PluginResult result = new PluginResult(PluginResult.Status.ERROR, "Invalid Data");
                        result.setKeepCallback(true);
                        callbackContext.sendPluginResult(result);
            return;
        }

            /* Decode the track data. */
        mTrack1Data.fromByteArray(decryptedTrackData, 0,
                trackData.getTrack1Length());
        mTrack2Data.fromByteArray(decryptedTrackData, 79,
                trackData.getTrack2Length());

            /* Show the track data. */
          JSONObject dataReader=new JSONObject();

        try {
            dataReader.put("Track1",mTrack1Data.getTrack1String());
            dataReader.put("Track2",mTrack2Data.getTrack2String()); 
        } catch (JSONException e) {
            e.printStackTrace();
        }
         PluginResult result = new PluginResult(PluginResult.Status.OK,
                                 dataReader);
                        result.setKeepCallback(true);
                        callbackContext.sendPluginResult(result);
    }

private byte[] aesDecrypt(byte key[], byte[] input)
            throws GeneralSecurityException {

        SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        IvParameterSpec ivParameterSpec = new IvParameterSpec(new byte[16]);

        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);

        return cipher.doFinal(input);
    }

    private int toByteArray(String hexString, byte[] byteArray) {

        char c = 0;
        boolean first = true;
        int length = 0;
        int value = 0;
        int i = 0;

        for (i = 0; i < hexString.length(); i++) {

            c = hexString.charAt(i);
            if ((c >= '0') && (c <= '9')) {
                value = c - '0';
            } else if ((c >= 'A') && (c <= 'F')) {
                value = c - 'A' + 10;
            } else if ((c >= 'a') && (c <= 'f')) {
                value = c - 'a' + 10;
            } else {
                value = -1;
            }

            if (value >= 0) {

                if (first) {

                    byteArray[length] = (byte) (value << 4);

                } else {

                    byteArray[length] |= value;
                    length++;
                }

                first = !first;
            }

            if (length >= byteArray.length) {
                break;
            }
        }

        return length;
    }
    /**
     * Checks if the device media volume is set to 100%
     *
     * @return true if media volume is at 100%
     */
    private boolean maxVolume() {
        int currentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
       // mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, AudioManager.FLAG_SHOW_UI);
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0);
       // if (currentVolume < maxVolume) {
        
           // return false;
       // }
       // else{
            return true;
     //   }
    }

    /**
     * Sets the ACR35 reader to continuously poll for the presence of a card. If a card is found,
     * the UID will be returned to the Apache Cordova application
     *
     * @param callbackContext: the callback context provided by Cordova
     * @param cardType: the integer representing card type
     */
    private void read(final CallbackContext callbackContext, final int cardType){
        System.out.println("setting up for reading...");
       
        firstReset = true;
           
        

        /* If no device is plugged into the audio socket or the media volume is < 100% */
        if(!mAudioManager.isWiredHeadsetOn()){
            /* Communicate to the Cordova application that the reader is unplugged */
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK,
                    "unplugged"));
            return;
        } else if(!maxVolume()) {
            /* Communicate to the Cordova application that the media volume is low */
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK,
                    "low_volume"));
            return;
        }
        
        /* Set the PICC response APDU callback */
       // mReader.setOnPiccResponseApduAvailableListener
         //       (new AudioJackReader.OnPiccResponseApduAvailableListener() {
           //         @Override
             //       public void onPiccResponseApduAvailable(AudioJackReader reader,
               //                                             byte[] responseApdu) {
                        /* Update the connection status of the transmitter */
                 //       transmitter.updateStatus(true);
                        /* Send the card UID to the Cordova application */
                   //    PluginResult result = new PluginResult(PluginResult.Status.OK,
                     //           bytesToHex(responseApdu));
                       // result.setKeepCallback(true);
                        //callbackContext.sendPluginResult(result);

                        /* Print out the UID */
//                        System.out.println(bytesToHex(responseApdu));
  //                  }
    //            });

        /* Set the reset complete callback */
        mReader.setOnResetCompleteListener(new AudioJackReader.OnResetCompleteListener() {
            @Override
            public void onResetComplete(AudioJackReader reader) {
                System.out.println("reset complete");

                     toByteArray(DEFAULT_MASTER_KEY_STRING, mMasterKey);
                     toByteArray(DEFAULT_MASTER_KEY_STRING, mNewMasterKey);
                     toByteArray(DEFAULT_AES_KEY_STRING, mAesKey);

                  mReader.authenticate(mMasterKey, new AudioJackReader.OnAuthCompleteListener() {
                    @Override
                    public void onAuthComplete(AudioJackReader audioJackReader, int i) {
                       // Log.d(TAG, "Authentication Result " + i);
                    }
                });

                /* If this is the first reset, the ACR35 reader must be turned off and back on again
                   to work reliably... */
                if(firstReset){
                    cordova.getThreadPool().execute(new Runnable() {
                        public void run() {
                            try{
                                /* Set the reader asleep */
                            //   mReader.sleep();
                                /* Wait one second */
                               Thread.sleep(10);
                                /* Reset the reader */
                             //  mReader.reset();

                            //    firstReset = false;
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                                // TODO: add exception handling
                            }
                        }
                    });
                } else {
                    /* Create a new transmitter for the UID read command */
                    transmitter = new Transmitter(mReader, mAudioManager, callbackContext, timeout,
                            apdu, cardType);
                    /* Cordova has its own thread management system */
                    cordova.getThreadPool().execute(transmitter);
                }
            }
        });
        mReader.setOnResultAvailableListener(new AudioJackReader.OnResultAvailableListener() {
            @Override
            public void onResultAvailable(AudioJackReader audioJackReader, Result result) {
              //  Log.d(TAG, "Result " + result.getErrorCode());
             System.out.println(" Result" + result.getErrorCode());
            }
        });

         mReader.setOnTrackDataAvailableListener(new AudioJackReader.OnTrackDataAvailableListener() {
            @Override
            public void onTrackDataAvailable(AudioJackReader audioJackReader, TrackData trackData) {
              //  Log.d(TAG, "Reader data " + trackData.toString());

                if (trackData instanceof AesTrackData) {
                    showAesTrackData(callbackContext,(AesTrackData) trackData);
                } else if (trackData instanceof DukptTrackData) {
                  //  showDukptTrackData((DukptTrackData) trackData);
                }
            }
        });

        mReader.start();
        mReader.reset();
        System.out.println("setup complete");
    }

    /**
     * This method acts as the bridge between Cordova and native Android code. The Cordova
     * application will invoke this method from JavaScript
     *
     * @param action: the command sent by the Cordova application
     * @param args: the command arguments sent by the Cordova application
     * @param callbackContext: the callback context provided by Cordova
     * @return a boolean that notifies whether the command execution was successful
     * @throws JSONException
     */
    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext)
            throws JSONException {

        /* Class variables require initialisation on first launch */
        if(firstRun){
            /* Context is acquired using cordova.getActivity() */
            mAudioManager = (AudioManager) this.cordova.getActivity().getApplicationContext()
                    .getSystemService(Context.AUDIO_SERVICE);
            mReader = new AudioJackReader(mAudioManager);

        mTrack1Data = new Track1Data();
        mTrack2Data = new Track2Data();
        mTrack1MaskedData = new Track1Data();
        mTrack2MaskedData = new Track2Data();
            firstRun = false;
        }

        if (action.equals("read")) {
            System.out.println("reading...");
            /* Use args.getString to retrieve arguments sent by the Cordova application */
            read(callbackContext, Integer.parseInt(args.getString(0)));
            /* Required so that a result can be returned asynchronously from another thread */
            PluginResult result = new PluginResult(PluginResult.Status.NO_RESULT);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);
            return true;
        } else if (action.equals("sleep")) {
            System.out.println("sleeping...");
            /* Kill the polling thread */
            if(transmitter != null){
                transmitter.kill();
            }
        //int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
       // mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume/2, AudioManager.FLAG_SHOW_UI);
            /* Send a success message back to Cordova */
           // mReader.stop();
            mReader.sleep();
            callbackContext.success();
            return true;
        }
        /* Else, an invalid command was sent */
        else {
            System.out.println("invalid command");
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.INVALID_ACTION));
            return false;
        }
    }

}
