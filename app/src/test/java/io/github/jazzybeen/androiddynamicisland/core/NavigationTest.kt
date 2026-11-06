package io.github.jazzybeen.androiddynamicisland.core

import io.github.jazzybeen.androiddynamicisland.navigation.*
import org.junit.Assert.*
import org.junit.Test

class NavigationTest{
    @Test fun cardsFitTheirContentAndNeverUseMoreThanHalfTheScreen(){
        assertEquals(128f,NavigationLayout.height(false,false,0,false,870f),.01f)
        assertTrue(NavigationLayout.height(true,true,6,false,870f)>NavigationLayout.height(true,true,2,false,870f))
        assertTrue(NavigationLayout.height(true,false,0,true,870f)>NavigationLayout.height(true,false,0,false,870f))
        assertTrue(NavigationLayout.height(true,false,0,true,500f)<=250f)
    }
    @Test fun splitActivitiesHaveSeparateBoundsAndCanUseEitherSide(){
        val width=ActivityLayout.compactWidth(382f,1f,true)
        assertEquals(134f,width,.01f);assertEquals(271f,ActivityLayout.satelliteLeft(392f,196f,width,8f,36f,5f),.01f)
        assertTrue(ActivityLayout.satelliteLeft(392f,310f,width,8f,36f,5f)+36<310-width/2)
        assertEquals(210f,ActivityLayout.compactWidth(382f,1f,false),.01f)
    }
    @Test fun turnTextWinsOverGenericRouteTitle(){val s=NavigationParser.parse("n","Ведение по маршруту","Через 20 м поверните налево на улицу Тестовую",emptyList(),true,"service",100)!!;assertEquals(Maneuver.LEFT,s.maneuver);assertEquals("20 м",s.distance);assertTrue(s.instruction.contains("налево"))}
    @Test fun ignorePromotionalAndEmptyNotifications(){assertNull(NavigationParser.parse("a","Новый маршрут выходного дня","Попробуйте",emptyList(),false,null,0));assertNull(NavigationParser.parse("a","","",emptyList(),true,"navigation",0))}
    @Test fun keepOnlyRealTransferInformation(){val s=NavigationParser.parse("n","Переход на линию 2","Станция А → Станция Б",listOf("Выход 3"),true,"navigation",0)!!;assertEquals(Maneuver.TRANSFER,s.maneuver);assertTrue(s.lines.contains("Выход 3"));assertFalse(s.lines.any{it.contains("цвет")})}
    @Test fun arrivalDoesNotConfuseEstimatedArrival(){assertEquals(Maneuver.ARRIVAL,NavigationParser.parse("a","Вы прибыли","Маршрут завершён",emptyList(),true,"navigation",0)!!.maneuver);assertEquals(Maneuver.ROUTE,NavigationParser.parse("a","Маршрут","Время прибытия 14:20",emptyList(),true,"navigation",0)!!.maneuver)}
    @Test fun distanceUpdatesDoNotOpenTheCardEverySecond(){
        val gate=NavigationAlertGate()
        fun s(m:Int)=NavigationSnapshot("n","Через $m м поверните налево",distance="$m м")
        assertTrue(gate.shouldAnnounce(s(180),10000));assertFalse(gate.shouldAnnounce(s(170),13000))
        assertTrue(gate.shouldAnnounce(s(90),16000));assertFalse(gate.shouldAnnounce(s(80),19000))
    }
    @Test fun aNewTurnDuringCooldownIsNotForgotten(){
        val gate=NavigationAlertGate();val left=NavigationSnapshot("n","Поверните налево");val right=NavigationSnapshot("n","Поверните направо")
        assertTrue(gate.shouldAnnounce(left,10000));assertFalse(gate.shouldAnnounce(right,11000));assertTrue(gate.shouldAnnounce(right,14000))
    }
    @Test fun parseFullTransitLinkWithoutLosingCoordinates(){val r=RouteLink.parse("https://yandex.ru/maps/?rtext=55.75%2C37.61~55.76%2C37.62&rtt=mt");assertEquals(2,r.points.size);assertEquals("mt",r.mode);assertEquals(55.75,r.points[0].latitude,.0001)}
    @Test fun rejectUnknownHostsShortLinksAndInvalidCoordinates(){listOf("https://example.com/?rtext=55,37~56,38","https://yandex.ru/maps/-/short","https://yandex.ru/maps/?rtext=999,37~56,38","https://yandex.ru@evil.example/maps/?rtext=55,37~56,38","https://yandex.ru/maps/?rtext=NaN,37~56,38").forEach{try{RouteLink.parse(it);fail("Invalid link accepted")}catch(_:IllegalArgumentException){}catch(_:IllegalStateException){}}}
}
