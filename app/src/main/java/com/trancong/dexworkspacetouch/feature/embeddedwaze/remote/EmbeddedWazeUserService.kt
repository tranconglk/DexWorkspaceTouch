package com.trancong.dexworkspacetouch.feature.embeddedwaze.remote
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import kotlin.system.exitProcess
class EmbeddedWazeUserService:IEmbeddedWazeService.Stub(){
 private var association:SessionAssociation?=null
 @Synchronized override fun getUid()=Process.myUid()
 @Synchronized override fun startSession(surface:Surface):Bundle{
  return runCatching{
   check(Process.myUid()==2000)
   check(association==null)
   val created=AssociationShell.create();association=created
   val display=EmbeddedWazeVdm.create(surface,900,675,320,created.id)
   val input=EmbeddedWazeVdm.prepareTouchscreen()
   waitForStableFingerConfig(display.getInt("displayId"))
   val launch=EmbeddedWazeVdm.launchWaze()
   Bundle().apply{putBoolean("success",true);putInt("remoteUid",Process.myUid());putInt("associationId",created.id);putString("associationMac",created.mac);putInt("deviceId",display.getInt("deviceId"));putInt("displayId",display.getInt("displayId"));putInt("inputDeviceId",input.getInt("inputDeviceId"));putInt("launchResult",launch.getInt("result"))}
  }.getOrElse{error->
   Log.e(TAG,"start failed",error);runCatching{stopInternal()}
   Bundle().apply{putBoolean("success",false);putInt("remoteUid",Process.myUid());putString("exception",error.javaClass.name+": "+error.message)}
  }
 }
 @Synchronized override fun sendTouch(action:Int,x:Float,y:Float,pressure:Float,eventTimeNanos:Long)=EmbeddedWazeVdm.sendDirectTouch(action,x,y,pressure,eventTimeNanos)
 @Synchronized override fun stopSession():Bundle=runCatching{stopInternal();Bundle().apply{putBoolean("success",true);putInt("remoteUid",Process.myUid())}}.getOrElse{Bundle().apply{putBoolean("success",false);putString("exception",it.javaClass.name+": "+it.message)}}
 private fun stopInternal(){var first:Throwable?=null;try{EmbeddedWazeVdm.cleanup()}catch(t:Throwable){first=t};try{AssociationShell.remove(association)}catch(t:Throwable){if(first==null)first=t};association=null;first?.let{throw it}}
 private fun waitForStableFingerConfig(displayId:Int){var previous="";var equal=0;val end=SystemClock.uptimeMillis()+3000;while(SystemClock.uptimeMillis()<end){val dump=ProcessBuilder("dumpsys","window","displays").redirectErrorStream(true).start().inputStream.bufferedReader().use{it.readText()};val section=dump.substringAfter("Display: mDisplayId=$displayId","").substringBefore("Display: mDisplayId=");check(section.isNotEmpty());if(section.contains(" finger ")&&section==previous)equal++ else equal=0;if(equal>=1)return;previous=section;Thread.sleep(100)};error("VDM input configuration did not settle to touchscreen=finger")}
 override fun destroy(){runCatching{stopInternal()};exitProcess(0)}
 companion object{private const val TAG="DWT.EmbeddedWaze"}
}
