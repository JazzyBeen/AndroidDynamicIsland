package io.github.jazzybeen.androiddynamicisland.navigation

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import com.yandex.mapkit.*
import com.yandex.mapkit.geometry.*
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.directions.DirectionsFactory
import com.yandex.mapkit.directions.driving.*
import com.yandex.mapkit.transport.TransportFactory
import com.yandex.mapkit.transport.masstransit.*
import io.github.jazzybeen.androiddynamicisland.R
import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.yandex.mapkit.map.InputListener
import com.yandex.runtime.image.ImageProvider

/** Free MapKit viewer: explicit route requests only. No NaviKit, polling GPS or background guidance. */
private class MapConfigurationException(message:String):IllegalStateException(message)
private object MapRuntime{
    private var apiKey="";private var initialized=false;private var users=0
    fun acquire(context:Context){
        val key=MapKeyStore(context).read();if(key.isBlank())throw MapConfigurationException("Добавь бесплатный ключ MapKit в разделе «Навигация»")
        if(initialized&&key!=apiKey)throw MapConfigurationException("Ключ изменён. Полностью перезапусти приложение, когда VPN можно будет отключить")
        if(!initialized){MapKitFactory.setApiKey(key);MapKitFactory.setLocale("ru_RU");MapKitFactory.initialize(context.applicationContext);apiKey=key;initialized=true}
        if(users++==0)MapKitFactory.getInstance().onStart()
    }
    fun release(){if(users>0&&--users==0)MapKitFactory.getInstance().onStop()}
}

class MapRouteView(context:Context,private val route:SharedRoute?,private val describe:(List<RouteLeg>)->Unit):FrameLayout(context){
    private var mapView:MapView?=null
    private var transitSession:Session?=null;private var drivingSession:DrivingSession?=null
    private var router:Any?=null;private var listener:Any?=null;private var acquired=false;private var disposed=false
    private var from:Point?=null
    private var locationRequest:CancellationSignal?=null
    private var choices:List<Route> = emptyList()
    private val hint=TextView(context).apply{setTextColor(Color.WHITE);textSize=11f;setBackgroundColor(0xE61C1C1E.toInt());setPadding(18,8,18,8);gravity=Gravity.CENTER}
    private val mapInput=object:InputListener{
        override fun onMapTap(map:com.yandex.mapkit.map.Map,point:Point){
            if(route!=null)return
            if(from==null){from=point;pin(point,"А");hint.text="Выбери назначение · удержание меняет начало"}
            else{val start=from!!;NavigationHub.setRoute(SharedRoute(listOf(RoutePoint(start.latitude,start.longitude),RoutePoint(point.latitude,point.longitude)),"mt","picker"))}
        }
        override fun onMapLongTap(map:com.yandex.mapkit.map.Map,point:Point){if(route==null){from=point;map.mapObjects.clear();pin(point,"А");hint.text="Начало выбрано · нажми на назначение"}}
    }
    private val mapInputRef=java.lang.ref.WeakReference<InputListener>(mapInput)
    private val status=TextView(context).apply{setTextColor(Color.LTGRAY);textSize=12f;gravity=Gravity.CENTER;setBackgroundColor(Color.rgb(18,18,20));text="Загрузка карты…";setPadding(24,12,24,12)}
    init{setBackgroundColor(Color.BLACK);addView(status,LayoutParams(-1,-1))}
    override fun onAttachedToWindow(){super.onAttachedToWindow();if(mapView!=null||disposed)return
        try{
            MapRuntime.acquire(context);acquired=true
            val map=LayoutInflater.from(context).inflate(R.layout.island_map,this,false) as MapView;mapView=map
            addView(map,0,LayoutParams(-1,-1));map.map.isNightModeEnabled=true;map.map.isTiltGesturesEnabled=false;map.onStart()
            val origin=route?.points?.firstOrNull()
            map.map.move(com.yandex.mapkit.map.CameraPosition(origin?.let{Point(it.latitude,it.longitude)}?:Point(55.75,37.62),if(origin!=null)13f else 4f,0f,0f))
            addView(hint,LayoutParams(-1,-2,Gravity.TOP))
            if(route==null){removeView(status);hint.text="Нажми начало маршрута на карте";map.map.addInputListener(mapInputRef);locate()}
            else{hint.text="Общественный транспорт · поиск вариантов";status.text="Построение маршрута…";requestRoute()}
        }catch(error:MapConfigurationException){status.text=error.message}
        catch(_:LinkageError){status.text="MapKit не запустился на этом устройстве. Подсказки продолжают работать без карты."}
        catch(_:Exception){status.text="Карта не запустилась. Проверь ключ MapKit в разделе «Навигация»"}
    }
    private fun requestRoute(){
        val route=route?:return
        val points=route.points.map{RequestPoint(Point(it.latitude,it.longitude),RequestPointType.WAYPOINT,null,null,null)}
        if(route.mode=="auto"){
            val r=DirectionsFactory.getInstance().createDrivingRouter(DrivingRouterType.COMBINED);router=r
            val l=object:DrivingSession.DrivingRouteListener{
                override fun onDrivingRoutes(routes:MutableList<DrivingRoute>){if(disposed)return;val result=routes.firstOrNull()?:return failed();paint(result.geometry);describe(listOf(RouteLeg("На автомобиле",result.metadata.weight.timeWithTraffic.text,Color.rgb(10,132,255))))}
                override fun onDrivingRoutesError(error:com.yandex.runtime.Error)=failed()
            };listener=l;drivingSession=r.requestRoutes(points,DrivingOptions().setRoutesCount(1),VehicleOptions(),l)
        }else{
            val l=object:Session.RouteListener{
                override fun onMasstransitRoutes(routes:MutableList<Route>){if(disposed)return;if(routes.isEmpty())return failed();choices=routes.toList();selectChoice(NavigationHub.routeOption.coerceIn(0,choices.lastIndex));hint.setOnClickListener{if(choices.size>1)selectChoice((NavigationHub.routeOption+1)%choices.size)}}
                override fun onMasstransitRoutesError(error:com.yandex.runtime.Error)=failed()
            };listener=l
            if(route.mode=="mt"){val r=TransportFactory.getInstance().createMasstransitRouter();router=r;transitSession=r.requestRoutes(points,TransitOptions(),RouteOptions(),l)}
            else{val r=TransportFactory.getInstance().createPedestrianRouter();router=r;transitSession=r.requestRoutes(points,TimeOptions(),RouteOptions(),l)}
        }
    }
    private fun selectChoice(index:Int){
        if(disposed)return;NavigationHub.routeOption=index;val result=choices[index];paint(result.geometry)
        hint.text="Вариант ${index+1}/${choices.size} · ${result.metadata.weight.time.text}"+if(choices.size>1)"  ›"else""
        describe(result.sections.take(20).map{s->
            val transports=s.metadata.data.transports.orEmpty();val line=transports.firstOrNull()?.line
            val stops=s.stops.mapNotNull{it.metadata.stop?.name}
            val title=when{transports.isNotEmpty()->transports.map{t->TransportLabels.name(t.line.vehicleTypes,t.line.name)}.distinct().joinToString(" / ");s.metadata.data.transfer!=null->"Пересадка";s.metadata.data.wait!=null->"Ожидание";else->"Пешком"}
            val stopText=listOfNotNull(stops.firstOrNull(),stops.lastOrNull()?.takeIf{it!=stops.firstOrNull()}).joinToString(" → ")
            RouteLeg(title,listOf(stopText,s.metadata.weight.time.text).filter{it.isNotBlank()}.joinToString(" · "),line?.style?.color?:Color.GRAY)
        })
    }
    private fun locate(){
        if(context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return
        val manager=context.getSystemService(LocationManager::class.java)
        val provider=LocationManager.NETWORK_PROVIDER
        try{
            if(!manager.isProviderEnabled(provider))return
            locationRequest=CancellationSignal()
            manager.getCurrentLocation(provider,locationRequest,context.mainExecutor){location->
                if(disposed||from!=null||location==null)return@getCurrentLocation
                val age=SystemClock.elapsedRealtimeNanos()-location.elapsedRealtimeNanos
                if(age>120_000_000_000L)return@getCurrentLocation
                from=Point(location.latitude,location.longitude);pin(from!!,"А");mapView?.map?.move(com.yandex.mapkit.map.CameraPosition(from!!,13f,0f,0f));hint.text="От текущего места · выбери назначение"
            }
        }catch(_:SecurityException){}catch(_:IllegalArgumentException){}
    }
    private fun pin(point:Point,label:String){
        val bitmap=Bitmap.createBitmap(52,52,Bitmap.Config.ARGB_8888);val c=Canvas(bitmap);val p=Paint(Paint.ANTI_ALIAS_FLAG);p.color=Color.WHITE;c.drawCircle(26f,26f,25f,p);p.color=Color.rgb(10,132,255);c.drawCircle(26f,26f,21f,p);p.color=Color.WHITE;p.textSize=27f;p.textAlign=Paint.Align.CENTER;c.drawText(label,26f,36f,p)
        mapView?.map?.mapObjects?.addPlacemark()?.apply{geometry=point;setIcon(ImageProvider.fromBitmap(bitmap))}
    }
    private fun paint(line:Polyline){val map=mapView?.map?:return;map.mapObjects.clear();map.mapObjects.addPolyline(line).apply{setStrokeColor(Color.rgb(64,156,255));strokeWidth=5f};map.move(map.cameraPosition(Geometry.fromPolyline(line)));removeView(status)}
    private fun failed(){if(!disposed){status.text="Маршрут не получен. Проверь интернет, ключ и доступ к маршрутизации. Закрой карту и открой снова для повтора."}}
    fun dispose(){if(disposed)return;disposed=true;locationRequest?.cancel();runCatching{mapView?.map?.removeInputListener(mapInputRef)};choices=emptyList();runCatching{transitSession?.cancel()};runCatching{drivingSession?.cancel()};listener=null;router=null;runCatching{mapView?.onStop()};runCatching{mapView?.destroy()};mapView=null;if(acquired){runCatching{MapRuntime.release()};acquired=false}}
    override fun onDetachedFromWindow(){dispose();super.onDetachedFromWindow()}
}
data class RouteLeg(val title:String,val detail:String,val color:Int)
