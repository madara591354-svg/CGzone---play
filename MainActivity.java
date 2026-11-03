package com.cgzone.play;

import android.content.res.Configuration;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(CGPlayerPlugin.class);
    super.onCreate(savedInstanceState);
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
