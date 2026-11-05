package com.cgzone.play;

import android.content.res.Configuration;
import android.os.Bundle;
import android.webkit.WebView;
import androidx.activity.OnBackPressedCallback;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
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
