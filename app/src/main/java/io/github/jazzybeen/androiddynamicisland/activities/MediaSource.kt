package io.github.jazzybeen.androiddynamicisland.activities

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.*
import android.os.Handler
import android.os.Looper
import io.github.jazzybeen.androiddynamicisland.system.VpnNotificationListener

/** Controls the owning player's public MediaSession, without global key injection. */
class MediaSource(private val context:Context):AutoCloseable {
    private val manager=context.getSystemService(MediaSessionManager::class.java)
    private val component=ComponentName(context,VpnNotificationListener::class.java)
    private val main=Handler(Looper.getMainLooper())
    private var controllers=emptyList<MediaController>()
    private val callbacks=mutableMapOf<MediaController,MediaController.Callback>()
    private var current:MediaController?=null
    private val changed=MediaSessionManager.OnActiveSessionsChangedListener{bind(it.orEmpty())}
    fun start(){active=this;try{manager.addOnActiveSessionsChangedListener(changed,component,main);bind(manager.getActiveSessions(component))}catch(_:SecurityException){ActivityHub.setMedia(null)}}
    private fun bind(values:List<MediaController>){callbacks.forEach{(c,cb)->runCatching{c.unregisterCallback(cb)}};callbacks.clear();controllers=values
        values.forEach{c->val cb=object:MediaController.Callback(){override fun onPlaybackStateChanged(state:PlaybackState?){publish()};override fun onMetadataChanged(metadata:MediaMetadata?){publish()};override fun onSessionDestroyed(){controllers=controllers.filter{it!==c};publish()}};callbacks[c]=cb;c.registerCallback(cb,main)};publish()
    }
    fun publish(){
        if(!ActivityPreferences(context).media){current=null;ActivityHub.setMedia(null);return}
        val c=controllers.firstOrNull{it.playbackState?.state==PlaybackState.STATE_PLAYING}
            ?:controllers.firstOrNull{it==current&&it.playbackState?.state==PlaybackState.STATE_PAUSED}
            ?:controllers.firstOrNull{it.playbackState?.state==PlaybackState.STATE_PAUSED}
        current=c;if(c==null){ActivityHub.setMedia(null);return}
        val p=c.playbackState?:return;val m=c.metadata;val a=p.actions
        val label=runCatching{context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(c.packageName,0)).toString()}.getOrDefault(c.packageName)
        val art=m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?:m?.getBitmap(MediaMetadata.METADATA_KEY_ART)?:NoticeSource.appIcon(context,c.packageName)
        fun has(flag:Long)=a and flag!=0L
        ActivityHub.setMedia(ActivityCard(c.packageName,ActivityKind.MEDIA,label,m?.getString(MediaMetadata.METADATA_KEY_TITLE)?:label,m?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),NoticeSource.smallBitmap(art),c.sessionActivity,
            MediaState(p.state==PlaybackState.STATE_PLAYING,p.position.coerceAtLeast(0),m?.getLong(MediaMetadata.METADATA_KEY_DURATION)?:0,p.playbackSpeed,p.lastPositionUpdateTime,has(PlaybackState.ACTION_PLAY) || has(PlaybackState.ACTION_PLAY_PAUSE),has(PlaybackState.ACTION_PAUSE)||has(PlaybackState.ACTION_PLAY_PAUSE),has(PlaybackState.ACTION_SKIP_TO_PREVIOUS),has(PlaybackState.ACTION_SKIP_TO_NEXT),has(PlaybackState.ACTION_SEEK_TO))))
    }
    fun command(action:String,value:Long=0){val c=current?:return;val a=c.playbackState?.actions?:0;fun has(flag:Long)=a and flag!=0L
        runCatching{when(action){"play"->if(has(PlaybackState.ACTION_PLAY)||has(PlaybackState.ACTION_PLAY_PAUSE))c.transportControls.play();"pause"->if(has(PlaybackState.ACTION_PAUSE)||has(PlaybackState.ACTION_PLAY_PAUSE))c.transportControls.pause();"previous"->if(has(PlaybackState.ACTION_SKIP_TO_PREVIOUS))c.transportControls.skipToPrevious();"next"->if(has(PlaybackState.ACTION_SKIP_TO_NEXT))c.transportControls.skipToNext();"seek"->if(has(PlaybackState.ACTION_SEEK_TO)){val d=c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?:0;if(d>0)c.transportControls.seekTo(value.coerceIn(0,d))}}}
    }
    override fun close(){if(active===this)active=null;runCatching{manager.removeOnActiveSessionsChangedListener(changed)};callbacks.forEach{(c,cb)->runCatching{c.unregisterCallback(cb)}};callbacks.clear();controllers=emptyList();current=null;ActivityHub.setMedia(null)}
    companion object {var active:MediaSource?=null;private set}
}
