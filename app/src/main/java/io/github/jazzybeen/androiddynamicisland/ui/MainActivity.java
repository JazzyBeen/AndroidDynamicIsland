package io.github.jazzybeen.androiddynamicisland.ui;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import io.github.jazzybeen.androiddynamicisland.R;
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings;
import io.github.jazzybeen.androiddynamicisland.glass.*;
import io.github.jazzybeen.androiddynamicisland.system.*;
import io.github.jazzybeen.androiddynamicisland.vpn.*;

public final class MainActivity extends ComponentActivity {
    private IslandSettings settings;
    private boolean wizard,awaitingReturn;
    private int externalStep;
    private View screen;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);settings=new IslandSettings(this);
        getWindow().setDecorFitsSystemWindows(false);getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);
        render();
        if(!settings.introduced())new Handler(Looper.getMainLooper()).postDelayed(this::introduction,400);
    }
    @Override public void onResume(){
        super.onResume();if(screen!=null)LiquidUi.refresh(screen);
        if(settings.enabled()&&Permissions.ready(this))IslandService.ensureStarted(this);
        if(awaitingReturn){
            awaitingReturn=false;
            boolean granted=externalStep==1?Permissions.listener(this):externalStep==2?Permissions.accessibility(this):Permissions.battery(this);
            if(wizard&&granted)new Handler(Looper.getMainLooper()).postDelayed(this::nextPermission,250);else wizard=false;
        }
    }
    private void introduction(){
        if(isFinishing()||settings.introduced())return;settings.introduced(true);
        LiquidDialogs.show(this,"Dynamic Island","Выбери своё VPN-приложение и разреши необходимые доступы. Остров распознаёт появление меню питания после долгого нажатия и показывает состояние подключения.\n\nКлючи и серверы остаются в VPN-клиенте. Интернет-доступ самому острову не нужен.","Настроить",()->{wizard=true;nextPermission();},"Позже",null);
    }
    private void nextPermission(){
        if(!VpnClientBridge.installed(this)){ClientPicker.show(this,()->{render();nextPermission();});return;}
        if(!Permissions.notifications(this)&&Build.VERSION.SDK_INT>=33){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},41);return;}
        if(!Permissions.listener(this)){explain("Уведомления VPN","Доступ нужен для подтверждения состояния и действий выбранного VPN-клиента. Уведомления других приложений игнорируются и не сохраняются.",1,new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));return;}
        if(!Permissions.accessibility(this)){explain("Остров и меню питания","Служба распознаёт меню питания и рисует остров. Для клиентов без доступной команды она может нажать только однозначно подписанную кнопку подключения или отключения внутри выбранного приложения. Пароли и ключи не вводятся.\n\nТакже Android предложит возможность снимка экрана: она используется только при отдельно включённом живом преломлении, локально и без сохранения.\n\nВключи Dynamic Island в списке скачанных служб. Если Android блокирует этот доступ: информация о приложении → ⋮ → «Разрешить ограниченные настройки».",2,new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));return;}
        if(!Permissions.battery(this)){explain("Работа в фоне","Разреши фоновую работу без оптимизации батареи для острова. Такое же исключение может понадобиться для VPN-клиента. Некоторые прошивки дополнительно требуют автозапуск.",3,new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName())));return;}
        wizard=false;settings.enabled(true);IslandService.ensureStarted(this);render();
        LiquidDialogs.show(this,"Готово","Сначала подключись в VPN-клиенте вручную и подтверди системный диалог. Затем удержи питание до меню выключения. Если вызывается ассистент, измени назначение кнопки.\n\nВключить живое преломление? Для него служба получает изображение под островом. Кадр остаётся только в памяти и освобождается после исчезновения острова. Защищённые окна не захватываются.\n\nПодключение без окна зависит от выбранного VPN-клиента.","Включить преломление",()->{settings.liveBackdrop(true);render();VpnCoordinator.get(this).preview();},"Пока без него",()->VpnCoordinator.get(this).preview());
    }
    private void explain(String title,String text,int step,Intent intent){LiquidDialogs.show(this,title,text,"Открыть настройки",()->{externalStep=step;awaitingReturn=true;open(intent);},"Позже",()->wizard=false);}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==41){if(Permissions.notifications(this)&&wizard)nextPermission();else{wizard=false;render();}}}
    private void open(Intent intent){try{startActivity(intent);}catch(ActivityNotFoundException e){awaitingReturn=false;wizard=false;Toast.makeText(this,"Открой этот раздел в настройках телефона",Toast.LENGTH_LONG).show();}}
    private void render(){
        if(screen!=null){LiquidUi.refresh(screen);return;}
        screen=LiquidUi.create(this,settings,action->{
            switch(action){
                case "setup","accessibility","battery"->{wizard=true;nextPermission();}
                case "pickClient"->ClientPicker.show(this,this::render);
                case "liveBackdrop"->{
                    if(settings.liveBackdrop()){settings.liveBackdrop(false);IslandAccessibilityService service=IslandAccessibilityService.current();if(service!=null)service.hideIsland();render();}
                    else LiquidDialogs.show(this,"Живое преломление","Остров будет получать изображение приложения под ним и искажать его как выпуклое стекло.\n\nКадры используются только в оперативной памяти, только пока остров виден; не сохраняются и не отправляются. Защищённые Android-окна недоступны. На Android 12–13 используется один кадр при появлении, на Android 14+ фон обновляется.","Включить",()->{settings.liveBackdrop(true);render();},"Отмена",null);
                }
                case "listener"->open(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                case "enabled"->{
                    if(settings.enabled()){settings.enabled(false);stopService(new Intent(this,IslandService.class));IslandAccessibilityService service=IslandAccessibilityService.current();if(service!=null)service.hideIsland();render();}
                    else{wizard=true;nextPermission();}
                }
                case "toggleVpn"->{if(!Permissions.ready(this)||!settings.enabled()){wizard=true;nextPermission();}else VpnCoordinator.get(this).toggle();}
                case "preview"->{if(IslandAccessibilityService.current()!=null)VpnCoordinator.get(this).preview();else Toast.makeText(this,"Включи службу специальных возможностей",Toast.LENGTH_LONG).show();}
                case "client"->{try{VpnClientBridge.open(this,false);}catch(RuntimeException e){ClientPicker.show(this,this::render);}}
                case "tile"->addTile();
                case "details"->open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));
                case "autostart"->{try{startActivity(new Intent().setComponent(new ComponentName("com.miui.securitycenter","com.miui.permcenter.autostart.AutoStartManagementActivity")));}catch(RuntimeException e){open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}}
            }
        });setContentView(screen);
    }
    private void addTile(){if(Build.VERSION.SDK_INT>=33)getSystemService(android.app.StatusBarManager.class).requestAddTileService(new ComponentName(this,IslandTileService.class),"VPN · Island",android.graphics.drawable.Icon.createWithResource(this,R.drawable.ic_island),getMainExecutor(),result->{});else Toast.makeText(this,"Добавь плитку VPN · Island в редакторе шторки",Toast.LENGTH_LONG).show();}
}
