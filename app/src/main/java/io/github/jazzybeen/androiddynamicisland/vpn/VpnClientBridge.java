package io.github.jazzybeen.androiddynamicisland.vpn;

import android.app.PendingIntent;
import android.content.*;
import android.content.pm.*;
import java.util.*;
import io.github.jazzybeen.androiddynamicisland.data.IslandSettings;
import io.github.jazzybeen.androiddynamicisland.system.*;

/** Selected-client adapter. A native launch command is used only for the client that implements it. */
public final class VpnClientBridge {
    private static final String NATIVE_CONNECT_PACKAGE="org.interhive.sota.connect";
    private static final String CONNECT="org.interhive.kitten_vpn_engine.extra.CONNECT_ON_LAUNCH";
    private VpnClientBridge(){}
    public static String selectedPackage(Context context){IslandSettings settings=new IslandSettings(context);return settings.embeddedVpn()?context.getPackageName():settings.vpnPackage();}
    /** Branding stays independent from the process that owns the embedded tunnel. */
    public static String iconPackage(Context context){
        IslandSettings settings=new IslandSettings(context);String preferred=settings.vpnPackage();
        if(settings.embeddedVpn()&&!preferred.isEmpty())try{context.getPackageManager().getApplicationInfo(preferred,0);return preferred;}catch(PackageManager.NameNotFoundException ignored){}
        return selectedPackage(context);
    }
    public static boolean installed(Context context){
        if(new IslandSettings(context).embeddedVpn())return new io.github.jazzybeen.androiddynamicisland.data.VpnProfileStore(context).exists();
        String name=selectedPackage(context);if(name.isEmpty())return false;
        try{context.getPackageManager().getPackageInfo(name,0);return true;}catch(PackageManager.NameNotFoundException e){return false;}
    }
    public static String label(Context context){
        if(new IslandSettings(context).embeddedVpn())return new IslandSettings(context).vpnName();
        try{return context.getPackageManager().getApplicationLabel(context.getPackageManager().getApplicationInfo(selectedPackage(context),0)).toString();}
        catch(PackageManager.NameNotFoundException e){return "Выберите VPN-приложение";}
    }
    public static void open(Context context,boolean nativeConnect){
        String name=selectedPackage(context);
        Intent intent=context.getPackageManager().getLaunchIntentForPackage(name);
        if(intent==null)throw new IllegalStateException("Выберите установленное VPN-приложение");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if(nativeConnect&&NATIVE_CONNECT_PACKAGE.equals(name))intent.putExtra(CONNECT,true);
        context.startActivity(intent);
    }
    public static boolean sendNotificationAction(boolean connect){
        PendingIntent action=VpnNotificationListener.action(connect);if(action==null)return false;
        try{action.send();return true;}catch(PendingIntent.CanceledException e){return false;}
    }
    public static void connect(Context context){
        if(sendNotificationAction(true))return;
        if(NATIVE_CONNECT_PACKAGE.equals(selectedPackage(context))){open(context,true);IslandAccessibilityService available=IslandAccessibilityService.current();if(available!=null)available.requestClientAction(true,false);return;}
        IslandAccessibilityService service=IslandAccessibilityService.current();
        if(service==null){open(context,false);return;}
        service.requestClientAction(true);
    }
    public static void disconnect(Context context){
        if(sendNotificationAction(false))return;
        IslandAccessibilityService service=IslandAccessibilityService.current();
        if(service==null){open(context,false);return;}
        service.requestClientAction(false);
    }
    public record Client(String packageName,String label){}
    public static List<Client> discover(Context context){
        PackageManager pm=context.getPackageManager();List<Client> clients=new ArrayList<>();Set<String> seen=new HashSet<>();
        Intent launcher=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for(ResolveInfo item:pm.queryIntentActivities(launcher,0)){
            String name=item.activityInfo.packageName;if(name.equals(context.getPackageName())||!seen.add(name))continue;
            try{
                PackageInfo info=pm.getPackageInfo(name,PackageManager.GET_SERVICES);
                if(info.services==null)continue;
                for(ServiceInfo service:info.services)if("android.permission.BIND_VPN_SERVICE".equals(service.permission)){
                    clients.add(new Client(name,pm.getApplicationLabel(info.applicationInfo).toString()));break;
                }
            }catch(PackageManager.NameNotFoundException ignored){}
        }
        clients.sort(Comparator.comparing(Client::label,String.CASE_INSENSITIVE_ORDER));return clients;
    }
}
