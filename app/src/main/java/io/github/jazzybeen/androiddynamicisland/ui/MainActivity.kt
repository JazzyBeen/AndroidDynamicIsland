package io.github.jazzybeen.androiddynamicisland.ui

import android.Manifest
import android.app.StatusBarManager
import android.content.*
import android.graphics.Color
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import io.github.jazzybeen.androiddynamicisland.R
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings
import io.github.jazzybeen.androiddynamicisland.system.*
import io.github.jazzybeen.androiddynamicisland.vpn.*
import io.github.jazzybeen.androiddynamicisland.vpn.embedded.IslandVpnService
import io.github.jazzybeen.androiddynamicisland.data.VpnProfileStore

class MainActivity:ComponentActivity(){
    private lateinit var settings:IslandSettings
    private var screen:View?=null
    private var configuring=false
    private var waitingForAccessibility=false
    private val main=Handler(Looper.getMainLooper())
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState);settings=IslandSettings(this)
        window.setDecorFitsSystemWindows(false);window.statusBarColor=Color.BLACK;window.navigationBarColor=Color.BLACK
        screen=AppUi.create(this,settings){handle(it)};setContentView(screen)
        if(!settings.introduced())main.postDelayed({if(!isFinishing){settings.introduced(true);AppDialogs.show(this,"Твой остров","Добавь встроенный VPN-профиль и разреши показ острова через специальные возможности.\n\nПрофиль хранится зашифрованным. Приложение не делает снимки экрана и не использует микрофон.","Настроить",{VpnSetupDialog.show(this){configuring=true;nextSetup()}},"Позже")}},350)
    }
    override fun onResume(){
        super.onResume();if(!::settings.isInitialized)return
        refresh();VpnCoordinator.get(this).refresh()
        if(settings.enabled()&&Permissions.ready(this))IslandService.ensureStarted(this)
        if(waitingForAccessibility){waitingForAccessibility=false;if(configuring&&Permissions.accessibility(this))main.postDelayed({nextSetup()},300)}
        main.postDelayed({if(!isFinishing)refresh()},450)
    }
    private fun refresh(){screen?.let{AppUi.refresh(it)}}
    private fun nextSetup(){
        if(!VpnClientBridge.installed(this)){VpnSetupDialog.show(this){refresh();nextSetup()};return}
        if(!Permissions.notifications(this)&&Build.VERSION.SDK_INT>=33){requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),41);return}
        if(!Permissions.accessibility(this)){
            AppDialogs.show(this,"Показ острова","Включи Dynamic Island в разделе специальных возможностей. Служба показывает остров и распознаёт меню питания. При необходимости она нажимает кнопку VPN только в выбранном клиенте.\n\nЕсли Android блокирует доступ: информация о приложении → ⋮ → «Разрешить ограниченные настройки».","Открыть настройки",{waitingForAccessibility=true;open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))})
            return
        }
        configuring=false;settings.enabled(true);IslandService.ensureStarted(this);refresh()
        AppDialogs.show(this,"Остров включён","Кнопка «Показать остров» проверяет отображение.\n\nВ разделе VPN проверь подключение и отключение. Для вызова без меню выключения можно назначить остров системным ассистентом в настройках приложения.","Показать остров",{VpnCoordinator.get(this).preview()},"Готово")
    }
    private fun handle(action:String){when(action){
        "setup"->{configuring=true;nextSetup()}
        "enabled"->{if(settings.enabled()){settings.enabled(false);stopService(Intent(this,IslandService::class.java));IslandAccessibilityService.current()?.hideIsland();refresh()}else{configuring=true;nextSetup()}}
        "pickClient"->{if(IslandVpnService.status.connected||IslandVpnService.status.busy)profileBusy()else ClientPicker.show(this){refresh()}}
        "embeddedSetup"->{if(IslandVpnService.status.connected||IslandVpnService.status.busy)profileBusy()else VpnSetupDialog.show(this){refresh();authorizeVpn(false)}}
        "forgetProfile"->{if(IslandVpnService.status.connected||IslandVpnService.status.busy)profileBusy()else AppDialogs.show(this,"Удалить профиль?","Ключ и конфигурация будут удалены с этого устройства.","Удалить",{VpnProfileStore(this).clear();settings.embeddedVpn(false);settings.vpnPackage("");VpnCoordinator.get(this).clientChanged();refresh()},"Отмена")}
        "toggleVpn"->{
            if(!VpnClientBridge.installed(this)||!Permissions.ready(this)){configuring=true;nextSetup()}
            else if(IslandAccessibilityService.current()==null)showAccessibilityHelp()
            else{settings.enabled(true);IslandService.ensureStarted(this);refresh();if(settings.embeddedVpn())authorizeVpn(true)else VpnCoordinator.get(this).toggle()}
        }
        "preview"->{if(IslandAccessibilityService.current()!=null)VpnCoordinator.get(this).preview()else showAccessibilityHelp()}
        "originGuide"->{IslandAccessibilityService.current()?.showOriginGuide()}
        "eventPreview"->{VpnCoordinator.get(this).notifyCompact("Тестовое оповещение",IslandVpnService.status.connected)}
        "originPreview"->{if(IslandAccessibilityService.current()!=null){IslandAccessibilityService.current()?.hideIsland();VpnCoordinator.get(this).preview()}else showAccessibilityHelp()}
        "client"->{try{VpnClientBridge.open(this,false)}catch(_:RuntimeException){ClientPicker.show(this){refresh()}}}
        "accessibility"->showAccessibilityHelp()
        "listener"->AppDialogs.show(this,"Уведомления VPN","Этот доступ позволяет получать действие отключения из уведомления выбранного VPN. Уведомления других приложений не сохраняются.","Открыть настройки",{open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))})
        "notifications"->{if(Build.VERSION.SDK_INT>=33)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),42)}
        "battery"->AppDialogs.show(this,"Работа в фоне","Разреши работу без оптимизации батареи для острова и своего VPN-клиента.","Разрешить",{open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:$packageName")))})
        "autostart"->{try{startActivity(Intent().setComponent(ComponentName("com.miui.securitycenter","com.miui.permcenter.autostart.AutoStartManagementActivity")))}catch(_:RuntimeException){open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))}}
        "details"->open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))
        "assistant"->{
            if(AssistantController.isSelected(this))assistantHint()
            else AppDialogs.show(this,"Вместо меню выключения","Назначь Dynamic Island системным цифровым ассистентом. Тогда удержание питания, настроенное на вызов ассистента, будет переключать VPN через остров.\n\nПриложение заменит текущего ассистента. Голос, микрофон и содержимое экрана не используются.","Выбрать ассистента",{try{AssistantController.request(this)}catch(_:RuntimeException){open(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))}})
        }
        "tile"->{if(Build.VERSION.SDK_INT>=33)getSystemService(StatusBarManager::class.java).requestAddTileService(ComponentName(this,IslandTileService::class.java),"VPN · Island",Icon.createWithResource(this,R.drawable.ic_island),mainExecutor){}else AppDialogs.show(this,"Плитка VPN","Открой редактор шторки и добавь VPN · Island.","Понятно",{})}
    }}
    private fun profileBusy(){AppDialogs.show(this,"VPN работает","Сначала отключи VPN, затем измени профиль.","Понятно",{})}
    private fun authorizeVpn(connect:Boolean){
        val permission=android.net.VpnService.prepare(this)
        if(permission!=null){connectAfterPermission=connect;startActivityForResult(permission,73)}else if(connect)VpnCoordinator.get(this).toggle()
    }
    private var connectAfterPermission=false
    private fun showAccessibilityHelp(){AppDialogs.show(this,"Специальные возможности","Открой Dynamic Island и включи службу. Если она уже включена, но остров не появляется, выключи и снова включи её.\n\nДругие настройки специальных возможностей менять не нужно.","Открыть настройки",{open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))})}
    private fun assistantHint(){AppDialogs.show(this,"Удержание питания","В настройках телефона открой «Запуск цифрового ассистента» или настройки жестов кнопки питания. Включи вызов ассистента удержанием питания.\n\nЕсли есть переключатель «Удерживать кнопку питания 0,5 с», он должен быть включён.","Открыть настройки",{open(Intent(Settings.ACTION_SETTINGS))},"Понятно")}
    private fun open(intent:Intent){try{startActivity(intent)}catch(_:ActivityNotFoundException){AppDialogs.show(this,"Открой настройки телефона","Этот раздел недоступен по прямой ссылке.","Понятно",{})}}
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<String>,grantResults:IntArray){super.onRequestPermissionsResult(requestCode,permissions,grantResults);refresh();if(requestCode==41&&configuring&&Permissions.notifications(this))nextSetup()}
    @Deprecated("Activity result bridge") override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode==72&&AssistantController.isSelected(this)){refresh();main.postDelayed({assistantHint()},250)};if(requestCode==73){if(resultCode==RESULT_OK&&connectAfterPermission)VpnCoordinator.get(this).toggle();connectAfterPermission=false;refresh()}}
    override fun onDestroy(){main.removeCallbacksAndMessages(null);super.onDestroy()}
}
