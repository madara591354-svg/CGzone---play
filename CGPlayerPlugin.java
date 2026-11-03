package com.cgzone.play;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowManager;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Ponte JS -> Android do player.
 *   CGPlayer.enter()  -> tela cheia imersiva real + paisagem (barras ocultas)
 *   CGPlayer.exit()   -> restaura barras + retrato
 *   CGPlayer.isImmersive() -> estado nativo atual
 *   CGPlayer.getState() / setBrightness() / setVolume() -> brilho e volume reais
 */
@CapacitorPlugin(name = "CGPlayer")
public class CGPlayerPlugin extends Plugin {

  @PluginMethod
  public void enter(PluginCall call) {
    final Activity a = getActivity();
    if (a == null) { call.reject("sem activity"); return; }
    a.runOnUiThread(() -> { ImmersiveController.set(a, true); call.resolve(); });
  }

  @PluginMethod
  public void exit(PluginCall call) {
    final Activity a = getActivity();
    if (a == null) { call.reject("sem activity"); return; }
    a.runOnUiThread(() -> { ImmersiveController.set(a, false); call.resolve(); });
  }

  @PluginMethod
  public void isImmersive(PluginCall call) {
    JSObject r = new JSObject();
    r.put("value", ImmersiveController.isActive());
    call.resolve(r);
  }

  /** Diagnostico: versao do codigo nativo e se as barras estao mesmo ocultas. */
  @PluginMethod
  public void diag(PluginCall call) {
    final Activity a = getActivity();
    if (a == null) { call.reject("sem activity"); return; }
    a.runOnUiThread(() -> {
      boolean[] v = ImmersiveController.barsVisible(a);
      JSObject r = new JSObject();
      r.put("ver", "1.9.1");
      r.put("sdk", android.os.Build.VERSION.SDK_INT);
      r.put("immersive", ImmersiveController.isActive());
      r.put("statusVisible", v[0]);
      r.put("navVisible", v[1]);
      call.resolve(r);
    });
  }

  @Override
  protected void handleOnResume() {
    final Activity a = getActivity();
    if (a != null) a.runOnUiThread(() -> ImmersiveController.reapply(a));
  }

  @Override
  protected void handleOnDestroy() {
    ImmersiveController.reset(getActivity());
  }

  @PluginMethod
  public void getState(PluginCall call) {
    final Activity a = getActivity();
    if (a == null) { call.reject("sem activity"); return; }
    a.runOnUiThread(() -> {
      try {
        AudioManager am = (AudioManager) a.getSystemService(Context.AUDIO_SERVICE);
        int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        float b = a.getWindow().getAttributes().screenBrightness;
        if (b < 0) {
          try {
            b = Settings.System.getInt(a.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) / 255f;
          } catch (Exception e) { b = 0.5f; }
        }
        JSObject r = new JSObject();
        r.put("volume", max > 0 ? (double) am.getStreamVolume(AudioManager.STREAM_MUSIC) / max : 1.0);
        r.put("brightness", (double) Math.max(0f, Math.min(1f, b)));
        call.resolve(r);
      } catch (Exception e) {
        call.reject("getState: " + e.getMessage());
      }
    });
  }

  @PluginMethod
  public void setBrightness(PluginCall call) {
    final Activity a = getActivity();
    if (a == null) { call.reject("sem activity"); return; }
    final float v = Math.max(0.01f, Math.min(1f, call.getFloat("value", 0.5f)));
    a.runOnUiThread(() -> {
      Window w = a.getWindow();
      WindowManager.LayoutParams lp = w.getAttributes();
      lp.screenBrightness = v;
      w.setAttributes(lp);
      call.resolve();
    });
  }

  @PluginMethod
  public void setVolume(PluginCall call) {
    try {
      float v = Math.max(0f, Math.min(1f, call.getFloat("value", 1f)));
      AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
      int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
      am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(v * max), 0);
      call.resolve();
    } catch (Exception e) {
      // Ex.: modo Nao Perturbe pode negar o ajuste de volume.
      call.reject("setVolume: " + e.getMessage());
    }
  }
}
