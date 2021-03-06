package com.reactlibrary;

import android.app.Application;
import android.app.Activity;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.launchdarkly.android.FeatureFlagChangeListener;
import com.launchdarkly.android.LDClient;
import com.launchdarkly.android.LDConfig;
import com.launchdarkly.android.LDUser;
import com.launchdarkly.android.LaunchDarklyException;

import java.util.concurrent.Future;

public class RNLaunchDarklyModule extends ReactContextBaseJavaModule {

  private final ReactApplicationContext reactContext;
  private LDClient ldClient;
  private LDUser user;

  public RNLaunchDarklyModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  @Override
  public String getName() {
    return "RNLaunchDarkly";
  }

  private void configure(String apiKey, ReadableMap options) throws Exception {
    LDConfig ldConfig = new LDConfig.Builder()
            .setMobileKey(apiKey)
            .build();

    LDUser.Builder userBuilder = new LDUser.Builder(options.getString("key"));

    if (options.hasKey("email")) {
      userBuilder = userBuilder.email(options.getString("email"));
    }

    if (options.hasKey("firstName")) {
      userBuilder = userBuilder.firstName(options.getString("firstName"));
    }

    if (options.hasKey("lastName")) {
      userBuilder = userBuilder.lastName(options.getString("lastName"));
    }

    if (options.hasKey("isAnonymous")) {
      userBuilder = userBuilder.anonymous(options.getBoolean("isAnonymous"));
    }

    if (options.hasKey("organization")) {
      userBuilder = userBuilder.custom("organization", options.getString("organization"));
    }

    user = userBuilder.build();
    if (user != null && ldClient != null) {
      ldClient.identify(user);
    } else {
      initLdClient(ldConfig);
    }
  }

  @ReactMethod
  public void configure(String apiKey, ReadableMap options, Promise promise) {
    try {
      configure(apiKey, options);
      promise.resolve(true);
    } catch (Exception e) {
      Log.d("RNLaunchDarklyModule", e.getMessage());
      promise.reject(e);
    }
  }

  private void initLdClient(LDConfig ldConfig) throws Exception {
    Activity activity = getCurrentActivity();
    if (activity == null) {
      throw new Exception("Couldn't init RNLaunchDarklyModule cause activity was null");
    }

    Application application = activity.getApplication();

    if (application == null) {
      throw new Exception("Couldn't init RNLaunchDarklyModule cause application was null");
    }

    Future<LDClient> future = LDClient.init(application, ldConfig, user);
    ldClient = future.get();
  }

  @ReactMethod
  public void addFeatureFlagChangeListener(String flagName) {
    FeatureFlagChangeListener listener = new FeatureFlagChangeListener() {
      @Override
      public void onFeatureFlagChange(String flagKey) {
        WritableMap result = Arguments.createMap();
        result.putString("flagName", flagKey);

        getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("FeatureFlagChanged", result);
      }
    };

    try {
      LDClient.get().registerFeatureFlagListener(flagName, listener);
    } catch (LaunchDarklyException e) {
      Log.d("RNLaunchDarklyModule", e.getMessage());
      e.printStackTrace();
    }
  }

  @ReactMethod
  public void boolVariation(String flagName, Boolean fallback, Callback callback) {
    Boolean variationResult = ldClient != null ? ldClient.boolVariation(flagName, fallback) : fallback;
    callback.invoke(variationResult);
  }

  @ReactMethod
  public void stringVariation(String flagName, String fallback, Callback callback) {
    String variationResult = ldClient != null ? ldClient.stringVariation(flagName, fallback) : fallback;
    callback.invoke(variationResult);
  }

  @ReactMethod
  public void track(String goalName, ReadableMap data) {
    if (ldClient != null) {
      Gson json = new GsonBuilder().create();
      try {
        JsonObject result = json.toJsonTree(data.toHashMap()).getAsJsonObject();
        ldClient.track(goalName, result);
      } catch (Exception e) {
        Log.d("RNLaunchDarklyModule", e.getMessage());
      }
    }
  }
}
