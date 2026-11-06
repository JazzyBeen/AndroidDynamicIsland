package io.github.jazzybeen.androiddynamicisland.core

import org.junit.Assert.*
import org.junit.Test

class AutoConnectPolicyTest {
    @Test fun firstAttemptIsImmediate(){assertTrue(AutoConnectPolicy().due(0))}
    @Test fun pausesGrowAndCapAtTenMinutes(){
        assertEquals(listOf(15_000L,30_000L,60_000L,120_000L,300_000L,600_000L,600_000L),(1..7).map{AutoConnectPolicy.pauseAfter(it)})
    }
    @Test fun waitsOutThePauseAfterAnAttempt(){
        val policy=AutoConnectPolicy();policy.attempted(1_000)
        assertFalse(policy.due(15_999));assertTrue(policy.due(16_000))
    }
    @Test fun successStartsOver(){
        val policy=AutoConnectPolicy();repeat(3){policy.attempted(0)};policy.succeeded()
        assertEquals(0,policy.misses());assertTrue(policy.due(0))
    }
    @Test fun givesUpAfterRepeatedMisses(){
        val policy=AutoConnectPolicy();var now=0L
        repeat(AutoConnectPolicy.GIVE_UP_AFTER){assertTrue(policy.due(now));policy.attempted(now);now+=AutoConnectPolicy.pauseAfter(policy.misses())}
        assertFalse(policy.due(now+3_600_000))
    }
    @Test fun returningNetworkAllowsOneMoreTryRightAway(){
        val policy=AutoConnectPolicy();repeat(AutoConnectPolicy.GIVE_UP_AFTER){policy.attempted(0)}
        policy.networkReturned(5_000)
        assertTrue(policy.due(5_000));policy.attempted(5_000);assertFalse(policy.due(5_001))
    }
}
