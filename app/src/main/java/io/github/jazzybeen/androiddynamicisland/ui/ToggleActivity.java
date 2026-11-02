package io.github.jazzybeen.androiddynamicisland.ui;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings;
import io.github.jazzybeen.androiddynamicisland.system.IslandService;
import io.github.jazzybeen.androiddynamicisland.vpn.VpnCoordinator;
public final class ToggleActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (new IslandSettings(this).enabled()) { IslandService.ensureStarted(this); VpnCoordinator.get(this).toggle(); }
        else startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
