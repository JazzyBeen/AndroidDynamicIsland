package io.github.jazzybeen.androiddynamicisland.core;
import org.junit.Test;
import static org.junit.Assert.*;

public class TrafficIdleGateTest{
    @Test public void idleIsReportedAfterTwentySecondsAndOnlyOnce(){
        TrafficIdleGate gate=new TrafficIdleGate();assertFalse(gate.sample(100,true,0));
        assertFalse(gate.sample(100,true,19999));assertTrue(gate.sample(100,true,20000));
        assertFalse(gate.sample(100,true,40000));assertFalse(gate.sample(100,true,200000));
    }
    @Test public void repeatedIdleSpellsRespectTheCooldown(){
        TrafficIdleGate gate=new TrafficIdleGate();gate.sample(100,true,0);assertTrue(gate.sample(100,true,20000));
        gate.sample(200,true,21000);assertFalse(gate.sample(200,true,41000));assertTrue(gate.sample(200,true,140000));
    }
    @Test public void missingCountersAndDisconnectedVpnNeverProduceAnIdleAlert(){
        TrafficIdleGate gate=new TrafficIdleGate();assertFalse(gate.sample(-1,true,0));assertFalse(gate.sample(-1,true,50000));
        assertFalse(gate.sample(0,false,100000));assertFalse(gate.sample(0,true,200000));assertFalse(gate.sample(0,false,250000));
    }
}
