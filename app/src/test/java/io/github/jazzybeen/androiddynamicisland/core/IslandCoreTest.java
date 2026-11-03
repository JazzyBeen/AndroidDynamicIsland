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
    private static IslandMotion.Geometry geometry(){return new IslandMotion.Geometry(180,18,22,22,180,340,82,0,210,36,180);}
    @Test public void singleCapsuleStartsExactlyAtThePhysicalCutout() {
        IslandMotion.Frame seed=IslandMotion.opening(0,geometry());
        assertEquals(180,seed.centerX(),.001);assertEquals(7,seed.top(),.001);
        assertEquals(22,seed.width(),.001);assertEquals(0,seed.alpha(),.001);
        IslandMotion.Frame expanded=IslandMotion.opening(IslandMotion.OPEN+IslandMotion.SETTLE,geometry());
        assertEquals(340,expanded.width(),.01);assertEquals(82,expanded.height(),.01);
        assertEquals(1,expanded.contentAlpha(),.001);assertEquals(0,expanded.compact(),.001);
    }
    @Test public void softSqueezeSettlesToOriginalSize() {
        IslandMotion.Frame peak=IslandMotion.opening(IslandMotion.OPEN+IslandMotion.SETTLE*.35,geometry());
        assertEquals(340*.982,peak.width(),.01);assertEquals(82*1.045,peak.height(),.01);
        IslandMotion.Frame end=IslandMotion.opening(IslandMotion.OPEN+IslandMotion.SETTLE,geometry());
        assertEquals(340,end.width(),.01);assertEquals(82,end.height(),.01);
    }
    @Test public void expandedAndCompactLayoutsShareAContinuousSilhouette(){
        IslandMotion.Frame shown=IslandMotion.opening(1000,geometry());
        assertEquals(shown,IslandMotion.compacting(0,geometry()));
        IslandMotion.Frame midway=IslandMotion.compacting(IslandMotion.MORPH/2,geometry());
        assertTrue(midway.width()<340&&midway.width()>210);assertTrue(midway.compact()>0&&midway.compact()<1);
        IslandMotion.Frame small=IslandMotion.compacting(IslandMotion.MORPH+IslandMotion.SETTLE,geometry());
        assertEquals(210,small.width(),.01);assertEquals(36,small.height(),.01);assertEquals(1,small.compact(),.001);
        IslandMotion.Frame hidden=IslandMotion.closing(IslandMotion.CLOSE,geometry(),small);
        assertEquals(22,hidden.width(),.01);assertEquals(7,hidden.top(),.01);assertEquals(0,hidden.alpha(),.001);
    }
    @Test public void cameraDockDoesNotDropBetweenTheExpandedAndCompactStates(){
        IslandMotion.Frame expanded=IslandMotion.opening(1000,geometry());
        IslandMotion.Frame compact=IslandMotion.compacting(1000,geometry());
        assertEquals(expanded.top(),compact.top(),.001);
        assertEquals(geometry().cameraY(),compact.top()+compact.height()/2,.001);
        for(int time=0;time<=1000;time+=20)assertEquals(expanded.top(),IslandMotion.compacting(time,geometry()).top(),.001);
    }
    @Test public void compactCapsuleBrieflyStretchesAndThenReturnsToItsRestingSize(){
        IslandMotion.Frame peak=IslandMotion.compacting(IslandMotion.MORPH+IslandMotion.SETTLE*.35,geometry());
        assertEquals(210*.982,peak.width(),.01);assertEquals(36*1.065,peak.height(),.01);
        IslandMotion.Frame rest=IslandMotion.compacting(IslandMotion.MORPH+IslandMotion.SETTLE,geometry());
        assertEquals(210,rest.width(),.01);assertEquals(36,rest.height(),.01);assertEquals(peak.top(),rest.top(),.001);
    }
    @Test public void compactDismissalStartsRetractionAfterSeventyPercentOfWidthContraction(){
        IslandMotion.Frame small=IslandMotion.compacting(1000,geometry());
        IslandMotion.Frame narrow=IslandMotion.closing(IslandMotion.RETRACT_START,geometry(),small);
        double contracted=(small.width()-narrow.width())/(small.width()-geometry().seedWidth()*1.12);
        assertEquals(.7,contracted,.002);
        assertEquals(small.height(),narrow.height(),.001);assertEquals(1,narrow.alpha(),.001);
        IslandMotion.Frame retract=IslandMotion.closing(IslandMotion.RETRACT_START+45,geometry(),small);
        assertTrue(retract.height()<narrow.height());
        assertTrue(retract.width()<narrow.width()&&retract.width()>geometry().seedWidth()*1.12);
        assertEquals(0,IslandMotion.closing(IslandMotion.CLOSE,geometry(),small).alpha(),.001);
    }
    @Test public void expandedInsetMatchesBothSidesAndKeepsTheCameraInside(){
        float top=IslandShape.dockTop(24,23,36,8),width=IslandShape.expandedWidth(392,top);
        assertEquals(8,top,.001);assertEquals(top,(392-width)/2,.001);
        assertTrue(24-23f/2>=top);assertTrue(24+23f/2<=top+36);
    }
    @Test public void reopenedIslandGetsASeparateShortStretch(){
        IslandMotion.Frame opened=IslandMotion.opening(1000,geometry());
        IslandMotion.Frame peak=IslandMotion.rebound(opened,IslandMotion.SETTLE*.35);
        assertTrue(peak.height()>opened.height());assertTrue(peak.width()<opened.width());
        assertEquals(opened,IslandMotion.rebound(opened,IslandMotion.SETTLE));
    }
    @Test public void interruptedEntranceDoesNotJumpToTheFullSizeWhenDismissed(){
        IslandMotion.Frame partial=IslandMotion.opening(200,geometry());
        IslandMotion.Frame firstClose=IslandMotion.closing(0,geometry(),partial);
        assertEquals(partial,firstClose);
    }
    @Test public void rapidReopeningStartsAtTheCurrentPresentation(){
        IslandMotion.Frame shown=IslandMotion.opening(1000,geometry());
        IslandMotion.Frame partial=IslandMotion.closing(150,geometry(),shown);
        assertEquals(partial,IslandMotion.blend(partial,shown,0));
        assertEquals(shown,IslandMotion.blend(partial,shown,1));
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
