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
        if("import-profile".equals(scenario)){
            java.io.File input=new java.io.File(getFilesDir(),"vpn-smoke.json");
            try{
                org.json.JSONObject profile=io.github.jazzybeen.androiddynamicisland.vpn.embedded.TunnelConfig.INSTANCE.importProfile(new String(java.nio.file.Files.readAllBytes(input.toPath()),java.nio.charset.StandardCharsets.UTF_8));
                io.nekohasekai.libbox.Libbox.checkConfig(io.github.jazzybeen.androiddynamicisland.vpn.embedded.TunnelConfig.INSTANCE.runtime(profile));
                new io.github.jazzybeen.androiddynamicisland.data.VpnProfileStore(this).write(profile);
                settings.embeddedVpn(true);settings.vpnName("VPN · test");
                android.util.Log.i("IslandSmoke","ProfileValid=true");
            }catch(Exception error){android.util.Log.i("IslandSmoke","ProfileValid=false; category="+error.getClass().getSimpleName());}
            finally{input.delete();}
            startActivity(new Intent(this,MainActivity.class));finish();return;
        }
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
