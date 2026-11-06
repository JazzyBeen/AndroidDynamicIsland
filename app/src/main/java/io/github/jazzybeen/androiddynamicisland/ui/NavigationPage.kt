package io.github.jazzybeen.androiddynamicisland.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.jazzybeen.androiddynamicisland.navigation.*
import java.util.function.Consumer

@Composable internal fun NavigationPage(action:Consumer<String>,revision:Int){
    val context=LocalContext.current
    val state by NavigationHub.state.collectAsState()
    val sourceIssue by NavigationHub.sourceIssue.collectAsState()
    val routeRevision by NavigationHub.routeChanges.collectAsState()
    val routePresent=remember(routeRevision){NavigationHub.route!=null}
    var enabled by remember(revision){mutableStateOf(NavigationHub.enabled(context))}
    var dialog by remember{mutableStateOf("")}
    var value by remember{mutableStateOf("")};var error by remember{mutableStateOf("")};var saved by remember{mutableStateOf(MapKeyStore(context).read().isNotBlank())}
    PageHeader("Навигация","Подсказки рядом с камерой")
    Group{
        SettingRow("Подсказки Яндекс Карт","Только навигационные уведомления"){
            Switch(enabled,{enabled=it;NavigationHub.enabled(context,it);action.accept("navEnabled")},colors=SwitchDefaults.colors(checkedTrackColor=Color(0xFF30D158),checkedThumbColor=Color.White))
        }
        ActionRow("Доступ к уведомлениям",if(Permissions.listener(context))"Разрешён"else"Настроить"){action.accept("listener")}
    }
    Caption("В Яндекс Картах включи «Навигация → Подсказки в фоновом режиме». Поворот появляется короткой карточкой, затем остаётся мини-капсула. Нажатие раскрывает детали; нажатие вне острова сворачивает их.")
    if(sourceIssue.isNotBlank())Caption(sourceIssue)
    Group{
        Column(Modifier.padding(18.dp)){
            Label(if(state==null)"Ждём начала маршрута"else if(state!!.demo)"Демонстрация интерфейса"else if(state!!.stale)"Ждём обновления от Карт"else "Навигация активна",17,bold=true)
            Spacer(Modifier.height(8.dp));Label(state?.instruction?:"Маршрут и личные адреса не сохраняются. Подсказки появятся после начала ведения в Картах.",13,Color(0xFF98989F))
        }
    }
    Section("КАРТА ВНУТРИ ОСТРОВА"){
        ActionRow("Построить маршрут на карте","Без ссылки"){action.accept("navPlanner")}
        ActionRow("Бесплатный ключ MapKit",if(saved)"Сохранён"else"Не добавлен"){value="";error="";dialog="key"}
        if(saved)ActionRow("Удалить ключ MapKit",null){MapKeyStore(context).clear();saved=false}
        ActionRow("Импорт по ссылке",if(routePresent)"Маршрут выбран"else"Необязательно"){value="";error="";dialog="route"}
        if(routePresent)ActionRow("Очистить переданный маршрут",null){NavigationHub.clearRoute()}
        ActionRow("Как получить бесплатный ключ",null){action.accept("mapKeyHelp")}
    }
    Caption("MapKit бесплатен до 1 000 пользователей карты в сутки при соблюдении условий Яндекса. Платные тарифы и NaviKit не подключаются. Карта загружается только при раскрытии. Нужен свой ключ MapKit; VPN-ключ не подходит.")
    Caption("Ссылки не обязательны: выбери начало и назначение на карте. MapKit предложит подходящий транспорт и пересадки. Это отдельный маршрут: API не передаёт текущую геометрию из приложения Карт. Подсказки Карт синхронизируются через уведомления.")
    Section("ПРОВЕРИТЬ ОФОРМЛЕНИЕ"){
        ActionRow("Поворот",null){NavigationHub.preview()}
        ActionRow("Пересадка",null){NavigationHub.preview(true)}
        ActionRow("Навигация и VPN рядом",null){action.accept("navBothPreview")}
        if(state?.demo==true)ActionRow("Закончить демонстрацию",null){action.accept("navClearDemo")}
    }
    Caption("Все примеры помечены «Демонстрация». VPN при проверке оформления не переключается. Рядом с маршрутом события VPN появляются отдельным кружком.")
    if(dialog.isNotBlank())AlertDialog(
        onDismissRequest={dialog=""},containerColor=Color(0xFF1C1C1E),title={Label(if(dialog=="key")"Бесплатный ключ MapKit"else"Маршрут из Карт",21,bold=true)},
        text={Column{
            Label(if(dialog=="key")"Создай ключ MapKit с бесплатным тарифом в кабинете Яндекса. Ключ шифруется на этом устройстве. Если заменить уже использованный ключ, потребуется перезапуск приложения."else"Вставь полную ссылку Яндекс Карт, содержащую начало и конец маршрута. Короткую ссылку сначала открой в браузере и скопируй полный адрес.",12,Color(0xFF98989F))
            Spacer(Modifier.height(14.dp))
            if(dialog=="key")OutlinedTextField(value,{value=it;error=""},label={Text("API-ключ MapKit")},singleLine=true,visualTransformation=PasswordVisualTransformation())
            else OutlinedTextField(value,{value=it;error=""},label={Text("Ссылка на маршрут")},minLines=3,maxLines=5)
            if(error.isNotBlank()){Spacer(Modifier.height(8.dp));Label(error,12,Color(0xFFFF6961))}
        }},
        confirmButton={TextButton(onClick={try{if(dialog=="key"){MapKeyStore(context).write(value);saved=true}else NavigationHub.setRoute(RouteLink.parse(value));dialog=""}catch(e:Exception){error=if(dialog=="key")"Проверь формат ключа MapKit"else e.message?:"Не удалось прочитать ссылку"}}){Text("Сохранить")}},
        dismissButton={TextButton(onClick={dialog=""}){Text("Отмена")}}
    )
}
