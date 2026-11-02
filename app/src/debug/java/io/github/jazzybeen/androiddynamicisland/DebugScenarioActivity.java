package io.github.jazzybeen.androiddynamicisland;

import android.app.Activity;
import android.content.Intent;
import android.os.*;
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings;
import io.github.jazzybeen.androiddynamicisland.system.*;
import io.github.jazzybeen.androiddynamicisland.ui.MainActivity;

/** Emulator-only test entry point; absent from the release manifest and release APK. */
public final class DebugScenarioActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        IslandSettings settings=new IslandSettings(this);settings.introduced(true);settings.enabled(true);settings.vpnPackage("org.interhive.sota.connect");
        IslandService.ensureStarted(this);
        String scenario=getIntent().getStringExtra("scenario");
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            IslandAccessibilityService service=IslandAccessibilityService.current();
            android.util.Log.i("IslandSmoke", "AccessibilityReady="+(service!=null)+"; scenario="+scenario);
            if(service!=null){
                if("power-menu".equals(scenario))service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG);
                else {service.showIsland("VPN","Предпросмотр · без подключения",false,true);new Handler(Looper.getMainLooper()).postDelayed(service::hideIsland,30000);}
            }
            finish();
        },300);
    }
}
