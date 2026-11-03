package com.cgzone.play;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Fonte unica da verdade do modo imersivo do player.
 *
 * Regra: as barras do sistema (status + navegacao) so ficam ocultas enquanto
 * "active" for true, e "active" so e true entre CGPlayer.enter() e CGPlayer.exit(),
 * ou seja, somente com o player de video aberto em tela cheia.
 *
 * Tudo roda na thread de UI. MainActivity chama reapply() a cada ganho de foco
 * da janela, porque o Android reexibe as barras quando a janela perde o foco
 * (barra de notificacoes, teclado, multitarefa, dialogos, volta do background).
 */
final class ImmersiveController {
  private static boolean active = false;
  private static boolean saved = false;
  private static int prevStatusColor;
  private static int prevNavColor;

  private static final Handler H = new Handler(Looper.getMainLooper());
  private static boolean listening = false;

  private ImmersiveController() {}

  /** true se alguma barra do sistema esta visivel agora (para diagnostico). */
  static boolean[] barsVisible(Activity a) {
    try {
      androidx.core.view.WindowInsetsCompat r = ViewCompat.getRootWindowInsets(a.getWindow().getDecorView());
      if (r == null) return new boolean[] { false, false };
      return new boolean[] {
        r.isVisible(WindowInsetsCompat.Type.statusBars()),
        r.isVisible(WindowInsetsCompat.Type.navigationBars())
      };
    } catch (Exception e) { return new boolean[] { false, false }; }
  }

  /**
   * A troca de orientacao refaz o layout e muitos aparelhos (Xiaomi/HyperOS, Samsung)
   * reexibem as barras DEPOIS do hide(). Por isso reaplicamos com atraso.
   */
  static void reapplyLater(final Activity a) {
    H.removeCallbacksAndMessages(null);
    for (int ms : new int[] { 120, 450, 1000, 2000 }) {
      H.postDelayed(() -> reapply(a), ms);
    }
  }

  static boolean isActive() { return active; }

  /** Entra (true) ou sai (false) do modo imersivo. Idempotente. */
  static void set(Activity a, boolean enter) {
    if (a == null) return;
    active = enter;
    apply(a);
    if (enter) reapplyLater(a);
  }

  /** Reaplica o estado atual. Chamar em onWindowFocusChanged(true) e onResume. */
  static void reapply(Activity a) {
    if (a == null) return;
    if (active) apply(a);
  }

  /** Chamar quando a activity for destruida: nunca deixar estado preso. */
  static void reset(Activity a) {
    if (a != null && active) { active = false; apply(a); }
    active = false;
    saved = false;
  }

  @SuppressWarnings("deprecation")
  private static void apply(Activity a) {
    final Window w = a.getWindow();
    final View decor = w.getDecorView();

    // Guarda as cores originais das barras uma unica vez, antes de mexer.
    if (active && !saved) {
      prevStatusColor = w.getStatusBarColor();
      prevNavColor = w.getNavigationBarColor();
      saved = true;
    }

    // Area do notch/recorte: so invade quando imersivo.
    WindowManager.LayoutParams lp = w.getAttributes();
    if (Build.VERSION.SDK_INT >= 28) {
      lp.layoutInDisplayCutoutMode = active
          ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
          : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
    }
    if (!active) lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
    w.setAttributes(lp);

    // Conteudo passa por baixo das barras apenas no player.
    WindowCompat.setDecorFitsSystemWindows(w, !active);

    WindowInsetsControllerCompat ic = WindowCompat.getInsetsController(w, decor);

    if (active) {
      w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      if (Build.VERSION.SDK_INT < 30) w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

      // Se o usuario puxar da borda, as barras aparecem transparentes e somem sozinhas.
      w.setStatusBarColor(Color.TRANSPARENT);
      w.setNavigationBarColor(Color.TRANSPARENT);
      if (Build.VERSION.SDK_INT >= 29) {
        w.setNavigationBarContrastEnforced(false);
        w.setStatusBarContrastEnforced(false);
      }

      // Orientacao primeiro; as barras so depois (e de novo apos a rotacao, ver reapplyLater).
      a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

      ic.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
      ic.hide(WindowInsetsCompat.Type.systemBars());

      // Cinto e suspensorio: flags legadas, que alguns fabricantes ainda respeitam melhor.
      decor.setSystemUiVisibility(
          View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
          | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
          | View.SYSTEM_UI_FLAG_FULLSCREEN
          | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
          | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
          | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

      if (!listening) {
        listening = true;
        // Se o sistema reexibir as barras sozinho (nao por gesto do usuario), esconde de novo.
        decor.setOnSystemUiVisibilityChangeListener(v -> {
          if (active && (v & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) reapplyLater(a);
        });
      }
    } else {
      w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      if (Build.VERSION.SDK_INT < 30) w.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

      if (saved) {
        w.setStatusBarColor(prevStatusColor);
        w.setNavigationBarColor(prevNavColor);
      }

      H.removeCallbacksAndMessages(null);
      decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
      ic.show(WindowInsetsCompat.Type.systemBars());

      a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
  }
}
