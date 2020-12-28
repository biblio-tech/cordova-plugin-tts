package com.wordsbaking.cordova.tts;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CordovaInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import java.util.HashMap;
import java.util.Locale;
import java.util.*;

import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;

import android.content.Intent;
import android.content.Context;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/*
    Cordova Text-to-Speech Plugin
    https://github.com/vilic/cordova-plugin-tts

    by VILIC VANE
    https://github.com/vilic

    MIT License
*/

public class TTS extends CordovaPlugin implements OnInitListener {

    public static final String ERR_INVALID_OPTIONS = "ERR_INVALID_OPTIONS";
    public static final String ERR_NOT_INITIALIZED = "ERR_NOT_INITIALIZED";
    public static final String ERR_ERROR_INITIALIZING = "ERR_ERROR_INITIALIZING";
    public static final String ERR_UNKNOWN = "ERR_UNKNOWN";

    boolean ttsInitialized = false;
    TextToSpeech tts = null;
    Context context = null;
    CallbackContext rangeStartCallbackContext = null;

    @Override
    public void initialize(CordovaInterface cordova, final CordovaWebView webView) {
        context = cordova.getActivity().getApplicationContext();
        tts = new TextToSpeech(cordova.getActivity().getApplicationContext(), this);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String s) {
                // do nothing
            }

            @Override
            public void onDone(String callbackId) {
                if (!callbackId.equals("")) {
                    CallbackContext context = new CallbackContext(callbackId, webView);
                    context.success();
                }
            }

            @Override
            public void onError(String callbackId) {
                if (!callbackId.equals("")) {
                    CallbackContext context = new CallbackContext(callbackId, webView);
                    context.error(ERR_UNKNOWN);
                }
            }

            @Override
            public void onRangeStart(String utteranceId = null,
                                     final int start = null,
                                     final int end = null,
                                     int frame = null) {
                //Log.i("XXX", "onRangeStart() ... utteranceId: " + utteranceId + ", start: " + start
                  //      + ", end: " + end + ", frame: " + frame);

                if (rangeStartCallbackContext != null) {
                    JSONObject params = new JSONObject();
                    if (utteranceId != null) {
                        params.put("utteranceId", utteranceId);
                    }
                    if (start != null) {
                        params.put("start", start);
                    }
                    if(end != null) {
                        params.put("end", end);
                    }
                    if(frame != null) {
                        params.put("frame", frame);
                    }
                    PluginResult result = new PluginResult(PluginResult.Status.OK, params);
                    result.setKeepCallback(true);
                    rangeStartCallbackContext.sendPluginResult(result);
                }
            }
        });
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext)
            throws JSONException {
        if (action.equals("speak")) {
            speak(args, callbackContext);
        } else if (action.equals("stop")) {
            stop(args, callbackContext);
        } else if (action.equals("checkLanguage")) {
            checkLanguage(args, callbackContext);
        } else if (action.equals("openInstallTts")) {
            callInstallTtsActivity(args, callbackContext);
        } else if (action.equals("getVoices")) {
            getVoices(args, callbackContext);
        } else if (action.equals("setRangeStartCallback")) {
             //Log.i("setting range start callback");
            setRangeStartCallback(args, callbackContext);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            tts = null;
        } else {
            // warm up the tts engine with an empty string
            HashMap<String, String> ttsParams = new HashMap<String, String>();
            ttsParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "");
            tts.setLanguage(new Locale("en", "US"));
            tts.speak("", TextToSpeech.QUEUE_FLUSH, ttsParams);

            ttsInitialized = true;
        }
    }

    private void stop(JSONArray args, CallbackContext callbackContext)
      throws JSONException, NullPointerException {
        tts.stop();
    }

    private void callInstallTtsActivity(JSONArray args, CallbackContext callbackContext)
      throws JSONException, NullPointerException {

        PackageManager pm = context.getPackageManager();
        Intent installIntent = new Intent();
        installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
        ResolveInfo resolveInfo = pm.resolveActivity( installIntent, PackageManager.MATCH_DEFAULT_ONLY );

        if( resolveInfo == null ) {
           // Not able to find the activity which should be started for this intent
        } else {
          installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          context.startActivity(installIntent);
        }
    }


    private void checkLanguage(JSONArray args, CallbackContext callbackContext)
      throws JSONException, NullPointerException {
        Set<Locale> supportedLanguages = tts.getAvailableLanguages();
        String languages = "";
        if(supportedLanguages!= null) {
            for (Locale lang : supportedLanguages) {
                languages = languages + "," + lang;
            }
        }
        if (languages != "") {
            languages = languages.substring(1);
        }

        final PluginResult result = new PluginResult(PluginResult.Status.OK, languages);
        callbackContext.sendPluginResult(result);
    }

    private void speak(JSONArray args, CallbackContext callbackContext)
            throws JSONException, NullPointerException {
        JSONObject params = args.getJSONObject(0);

        if (params == null) {
            callbackContext.error(ERR_INVALID_OPTIONS);
            return;
        }

        String text;
        String locale;
        double rate;
        Voice voice = null;

        if (params.isNull("text")) {
            callbackContext.error(ERR_INVALID_OPTIONS);
            return;
        } else {
            text = params.getString("text");
        }

        if (params.isNull("voice")) {
           voice = getVoiceByName(null);
        } else {
            voice = getVoiceByName(params.getString("voice"));
        }

        if (params.isNull("rate")) {
            rate = 1.0;
        } else {
            rate = params.getDouble("rate");
        }

        if (tts == null) {
            callbackContext.error(ERR_ERROR_INITIALIZING);
            return;
        }

        if (!ttsInitialized) {
            callbackContext.error(ERR_NOT_INITIALIZED);
            return;
        }

        HashMap<String, String> ttsParams = new HashMap<String, String>();
        ttsParams.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, callbackContext.getCallbackId());

        if (voice != null) {
            tts.setVoice(voice);
        }

        if (Build.VERSION.SDK_INT >= 27) {
            tts.setSpeechRate((float) rate * 0.7f);
        } else {
            tts.setSpeechRate((float) rate);
        }

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, ttsParams);
    }

    private Voice getVoiceByName(String voiceName) {
        Voice voice = null;

        if (tts != null && ttsInitialized) {
             for (Voice tmpVoice : tts.getVoices()) {
                if (tmpVoice.getName().contains(voiceName)) {
                    voice = tmpVoice;
                    break;
                } else if (voice == null && tmpVoice.getName().contains("#male") && tmpVoice.getName().contains("en-us")) {
                    voice = tmpVoice;
                }
            }
        }

        return voice;
    }

    private void getVoices(JSONArray args, CallbackContext callbackContext)
            throws JSONException, NullPointerException {

        if (tts == null) {
            callbackContext.error(ERR_ERROR_INITIALIZING);
            return;
        }

        if (!ttsInitialized) {
            callbackContext.error(ERR_NOT_INITIALIZED);
            return;
        }

        String voices = "";

        for (Voice tmpVoice : tts.getVoices()) {
            voices = voices + "," + tmpVoice.getName();
        }

        if (voices != "") {
            voices = voices.substring(1);
        }

        final PluginResult result = new PluginResult(PluginResult.Status.OK, voices);
        callbackContext.sendPluginResult(result);
    }

    private void setRangeStartCallback(JSONArray args, CallbackContext callbackContext) {
        rangeStartCallbackContext = callbackContext;
       // Log.i("rangeStartCallbackContext set");
    }
}
