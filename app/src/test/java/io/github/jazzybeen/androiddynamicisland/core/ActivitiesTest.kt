package io.github.jazzybeen.androiddynamicisland.core

import io.github.jazzybeen.androiddynamicisland.activities.MediaPosition
import io.github.jazzybeen.androiddynamicisland.navigation.TransportLabels
import org.junit.Assert.*
import org.junit.Test

class ActivitiesTest {
    @Test fun playingPositionFollowsThePlayersMonotonicClock(){assertEquals(12500L,MediaPosition.at(10000,60000,true,1.25f,1000,3000))}
    @Test fun pausedPositionDoesNotCreep(){assertEquals(10000L,MediaPosition.at(10000,60000,false,1f,1000,30000))}
    @Test fun unknownTimestampDoesNotJumpToEnd(){assertEquals(10000L,MediaPosition.at(10000,60000,true,1f,0,500000))}
    @Test fun seekAndEndAreClamped(){assertEquals(60000L,MediaPosition.at(59000,60000,true,1f,1000,5000));assertEquals(0L,MediaPosition.at(-1,60000,false,1f,1000,5000))}
    @Test fun invalidSpeedDoesNotCorruptTheTimeline(){assertEquals(5000L,MediaPosition.at(5000,60000,true,Float.NaN,1000,3000))}
    @Test fun liveStreamDoesNotInventDuration(){assertEquals(12000L,MediaPosition.at(10000,0,true,1f,1000,3000))}
    @Test fun transitLabelsKeepNumbersAndRecognizeMinibuses(){assertEquals("Маршрутка К-12",TransportLabels.name(listOf("minibus"),"К-12"));assertEquals("Автобус 24",TransportLabels.name(listOf("bus"),"24"));assertEquals("Метро 2",TransportLabels.name(listOf("underground"),"2"));assertEquals("Транспорт А",TransportLabels.name(emptyList(),"А"))}
}
