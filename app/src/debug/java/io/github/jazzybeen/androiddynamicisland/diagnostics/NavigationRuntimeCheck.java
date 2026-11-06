package io.github.jazzybeen.androiddynamicisland.diagnostics;

import java.util.List;
import io.github.jazzybeen.androiddynamicisland.navigation.*;

/** Runs against Android's ICU regex implementation using app_process; absent from release builds. */
public final class NavigationRuntimeCheck {
    public static void main(String[] args){
        NavigationSnapshot turn=NavigationParser.INSTANCE.parse("test","Ведение по маршруту","Через 20 м поверните налево",List.of("Осталось 7 мин"),true,"service",100);
        require(turn!=null&&turn.getManeuver()==Maneuver.LEFT&&turn.getDistance().equals("20 м")&&turn.getRemaining().equals("7 мин"));
        NavigationSnapshot km=NavigationParser.INSTANCE.parse("test","Маршрут","Через 1,2 км направо",List.of(),true,"navigation",100);
        require(km!=null&&km.getDistance().equals("1,2 км")&&km.getManeuver()==Maneuver.RIGHT);
        require(NavigationParser.INSTANCE.parse("test","Маршрут выходного дня","Скидки",List.of(),false,null,100)==null);
        NavigationSnapshot transfer=NavigationParser.INSTANCE.parse("test","Переход на линию 2","Выход 3",List.of("Станция А"),true,"navigation",100);
        require(transfer!=null&&transfer.getManeuver()==Maneuver.TRANSFER);
        NavigationAlertGate gate=new NavigationAlertGate();
        require(gate.shouldAnnounce(turn,10000));require(!gate.shouldAnnounce(turn,13000));require(gate.shouldAnnounce(km,16000));
        require(RouteLink.INSTANCE.parse("https://yandex.ru/maps/?rtext=55.75%2C37.61~55.76%2C37.62&rtt=mt").getPoints().size()==2);
        System.out.println("Android navigation runtime checks: 8 passed");
    }
    private static void require(boolean value){if(!value)throw new AssertionError("Android navigation runtime check failed");}
}
