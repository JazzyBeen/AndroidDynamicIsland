package io.github.jazzybeen.androiddynamicisland.system

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/** Required assistant metadata endpoint. This application deliberately offers no audio recognition. */
class NoAudioRecognitionService:RecognitionService(){
    override fun onStartListening(intent:Intent?,listener:Callback){listener.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)}
    override fun onStopListening(listener:Callback){listener.error(SpeechRecognizer.ERROR_NO_MATCH)}
    override fun onCancel(listener:Callback){}
}
