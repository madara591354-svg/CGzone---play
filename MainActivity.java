package com.cgzone.play;

import android.content.Context;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.webkit.WebView;
import androidx.activity.OnBackPressedCallback;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  private ConnectivityManager.NetworkCallback netCb;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(CGPlayerPlugin.class);
    super.onCreate(savedInstanceState);

    // V1.18: botao voltar do celular.
    // Sem isto o Android fechava o app em qualquer tela. Agora pergunta ao app
    // (window.cgBack no index.html): se ele voltou uma tela, fica no app;
    // so fecha quando ja esta na tela inicial (Todos).
    getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
      @Override
      public void handleOnBackPressed() {
        WebView wv = getBridge() != null ? getBridge().getWebView() : null;
        if (wv == null) { finish(); return; }
        wv.evaluateJavascript(
          "(function(){try{return !!(window.cgBack&&window.cgBack())}catch(e){return false}})()",
          value -> { if (!"true".equals(value)) finish(); }
        );
      }
    });

    // V1.18.0 (correcao): avisa o app quando o wi-fi/dados liga ou desliga,
    // para mostrar (ou tirar) o mascote "Liga o wi-fi cara" na hora.
    watchNetwork();
  }

  private void watchNetwork() {
    try {
      ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
      if (cm == null) return;
      netCb = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network n) { notifyNet(); }
        @Override public void onLost(Network n) { notifyNet(); }
      };
      cm.registerNetworkCallback(
        new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
        netCb
      );
    } catch (Exception e) {
      netCb = null;
    }
  }

  private void notifyNet() {
    runOnUiThread(() -> {
      WebView wv = getBridge() != null ? getBridge().getWebView() : null;
      if (wv != null) wv.evaluateJavascript("window.cgNet&&window.cgNet()", null);
    });
  }

  @Override
  public void onDestroy() {
    try {
      if (netCb != null) {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) cm.unregisterNetworkCallback(netCb);
      }
    } catch (Exception e) { }
    netCb = null;
    super.onDestroy();
  }

  /**
   * O Android reexibe as barras do sistema sempre que a janela perde o foco
   * (barra de notificacoes, multitarefa, dialogos, teclado). Ao recuperar o
   * foco, reaplicamos o modo imersivo, mas somente se o player ainda estiver aberto.
   * (onResume e onDestroy ficam a cargo do CGPlayerPlugin.)
   */
  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) ImmersiveController.reapply(this);
  }

  /** Fim da rotacao (configChanges evita recriar a Activity): reaplica o imersivo. */
  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    if (ImmersiveController.isActive()) ImmersiveController.reapplyLater(this);
  }
}
