package io.github.jazzybeen.androiddynamicisland.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class IslandCoreTest {
    @Test public void powerMenuIsScopedToTheSystem() {
        assertTrue(PowerMenuGate.isPowerMenu("com.android.systemui", "GlobalActionsDialogLite", ""));
        assertTrue(PowerMenuGate.isPowerMenu("com.miui.systemui", "View", "[system-power-actions] Выключить Перезагрузка"));
        assertFalse(PowerMenuGate.isPowerMenu("com.android.systemui", "NotificationShade", "Power off Restart"));
        assertFalse(PowerMenuGate.isPowerMenu("some.messenger", "GlobalActionsDialog", "Power off Restart"));
        assertFalse(PowerMenuGate.isPowerMenu("com.android.systemui", "View", "Restart notification"));
        assertFalse(PowerMenuGate.isPowerMenu("org.interhive.sota.connect", "View", "Power off Restart"));
    }
    @Test public void oneTogglePerMenuOpening() {
        PowerMenuGate gate = new PowerMenuGate();
        assertTrue(gate.update("com.android.systemui", "GlobalActionsDialog", "", 2000));
        assertFalse(gate.update("com.android.systemui", "GlobalActionsDialog", "", 2600));
        gate.update("com.android.systemui", "OtherView", "", 2800);
        assertFalse(gate.update("com.android.systemui", "GlobalActionsDialog", "", 2900));
        gate.update("com.android.systemui", "OtherView", "", 3900);
        assertTrue(gate.update("com.android.systemui", "GlobalActionsDialog", "", 4200));
    }
    @Test public void connectNeedsActualVpnAndSelectedClientEvidence() {
        VpnOperation op = new VpnOperation(true, 1000);
        assertEquals(VpnOperation.Result.WAITING, op.observe(false, true, 1500));
        assertEquals(VpnOperation.Result.WAITING, op.observe(true, false, 1500));
        assertEquals(VpnOperation.Result.CONNECTED, op.observe(true, true, 2000));
        assertEquals(VpnOperation.Result.TIMEOUT, op.observe(false, false, 31000));
    }
    @Test public void disconnectWaitsForNetworkRemoval() {
        VpnOperation op = new VpnOperation(false, 1000);
        assertEquals(VpnOperation.Result.WAITING, op.observe(true, false, 2000));
        assertEquals(VpnOperation.Result.DISCONNECTED, op.observe(false, false, 2200));
    }
    @Test public void motionExpandsOnlyAfterSeventyPercentAndReversesExactly() {
        IslandMotion.Frame before = IslandMotion.frame(IslandMotion.EXPAND_START, 34, 24, 320, 96, 8, false);
        assertEquals(34, before.width(), .01);
        assertEquals(24, before.height(), .01);
        assertEquals(.7, (before.top() + before.height()) / before.height(), .001);
        IslandMotion.Frame mid = IslandMotion.frame(210,34,24,320,96,8,false);
        IslandMotion.Frame reverse = IslandMotion.frame(210,34,24,320,96,8,true);
        assertTrue(mid.width() > 34 && mid.width() < 320);
        assertEquals(mid.width(), reverse.width(), .001);
        assertEquals(mid.top(), reverse.top(), .001);
    }
    @Test public void softSqueezeSettlesToOriginalSize() {
        IslandMotion.Frame peak = IslandMotion.frame(IslandMotion.OPEN + 70,34,24,320,96,8,false);
        assertEquals(320 * .98, peak.width(), .01);
        assertEquals(96 * 1.05, peak.height(), .01);
        IslandMotion.Frame end = IslandMotion.frame(IslandMotion.OPEN + 200,34,24,320,96,8,false);
        assertEquals(320,end.width(), .01); assertEquals(96,end.height(), .01);
    }
    @Test public void screenRadiusAcceptsEitherReportedCorner() {
        assertEquals(42,IslandShape.radius(42,0,true,32),.001);
        assertEquals(42,IslandShape.radius(0,42,true,32),.001);
        assertEquals(40,IslandShape.radius(42,40,true,32),.001);
    }
    @Test public void missingScreenRadiusUsesManualSetting() {
        assertEquals(32,IslandShape.radius(0,0,true,32),.001);
        assertEquals(28,IslandShape.radius(42,42,false,28),.001);
    }
    @Test public void hyperOsSliderWithNoResourceIdsIsRecognized(){
        assertTrue(PowerMenuGate.isPowerMenu("miui.systemui.plugin","android.widget.FrameLayout","[system-power-slider] Слайдер выключения питания Выключение Перезагрузка"));
        assertFalse(PowerMenuGate.isPowerMenu("miui.systemui.plugin","android.widget.FrameLayout","Выключение Перезагрузка"));
        assertFalse(PowerMenuGate.isPowerMenu("some.app","android.widget.FrameLayout","[system-power-slider] Выключение Перезагрузка"));
    }
    @Test public void knownPowerControlMatchesTheObservedPhoneLayout(){
        assertTrue(VpnButtonShape.matches(320,850,760,1290,1080,2400));
        assertFalse(VpnButtonShape.matches(904,160,1036,292,1080,2400));
        assertFalse(VpnButtonShape.matches(452,2012,628,2188,1080,2400));
        assertFalse(VpnButtonShape.matches(44,424,1036,628,1080,2400));
    }
    @Test public void explicitDismissalAllowsNextMenuWhenFirmwareOmitsHiddenEvent(){
        PowerMenuGate gate=new PowerMenuGate();
        assertTrue(gate.update("miui.systemui.plugin","FrameLayout","[system-power-slider] Выключение Перезагрузка",2000));
        gate.dismissed();
        assertFalse(gate.update("miui.systemui.plugin","FrameLayout","[system-power-slider] Выключение Перезагрузка",2500));
        gate.dismissed();
        assertTrue(gate.update("miui.systemui.plugin","FrameLayout","[system-power-slider] Выключение Перезагрузка",4500));
    }
}
