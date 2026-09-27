package com.trancong.dexworkspacetouch.feature.embeddedwaze
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.view.Surface
import com.trancong.dexworkspacetouch.BuildConfig
import com.trancong.dexworkspacetouch.feature.embeddedwaze.remote.IEmbeddedWazeService
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

data class EmbeddedWazeState(val shellReady:Boolean=false,val busy:Boolean=false,val active:Boolean=false,val status:String="Connect Shizuku to begin",val displayId:Int=-1,val taskReady:Boolean=false)

class EmbeddedWazeSession(private val context:Context,private val changed:(EmbeddedWazeState)->Unit){
 private val main=Handler(Looper.getMainLooper());private val worker=Executors.newSingleThreadExecutor()
 private val args=Shizuku.UserServiceArgs(ComponentName(context.packageName,"com.trancong.dexworkspacetouch.feature.embeddedwaze.remote.EmbeddedWazeUserService")).daemon(false).processNameSuffix("embedded_waze").tag("dwt-vdm-001").version(BuildConfig.VERSION_CODE+1000).debuggable(BuildConfig.DEBUG)
 private var state=EmbeddedWazeState();private var remote:IEmbeddedWazeService?=null;private var connection:ServiceConnection?=null;private var closed=false
 private val received=Shizuku.OnBinderReceivedListener{refreshPermission()}
 private val permission=Shizuku.OnRequestPermissionResultListener{code,result->if(code==REQUEST){if(result==PackageManager.PERMISSION_GRANTED)bind() else update(state.copy(status="Shizuku permission denied"))}}
 fun start(){Shizuku.addBinderReceivedListenerSticky(received);Shizuku.addRequestPermissionResultListener(permission);changed(state)}
 fun connect(){if(!Shizuku.pingBinder()){update(state.copy(status="Shizuku is unavailable"));return};if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){Shizuku.requestPermission(REQUEST)}else bind()}
 private fun refreshPermission(){if(!closed&&Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED)bind()}
 private fun bind(){if(connection!=null||closed)return;update(state.copy(busy=true,status="Connecting shell UserService"))
  val c=object:ServiceConnection{
   override fun onServiceConnected(name:ComponentName,binder:IBinder){remote=IEmbeddedWazeService.Stub.asInterface(binder);val uid=runCatching{remote!!.uid}.getOrDefault(-1);update(state.copy(shellReady=uid==2000,busy=false,status=if(uid==2000)"Shell UID 2000 ready" else "Unexpected remote UID $uid"))}
   override fun onServiceDisconnected(name:ComponentName){remote=null;connection=null;update(EmbeddedWazeState(status="Shell UserService disconnected"))}
  };connection=c;runCatching{Shizuku.bindUserService(args,c)}.onFailure{connection=null;update(state.copy(busy=false,status="Bind failed: "+it.message))}
 }
 fun startSession(surface:Surface){val service=remote?:return update(state.copy(status="Connect Shizuku first"));if(!surface.isValid)return update(state.copy(status="Surface is not valid"));update(state.copy(busy=true,status="Creating trusted VDM session"))
  worker.execute{val result=runCatching{service.startSession(surface)};main.post{result.fold({b->if(b.getBoolean("success"))update(state.copy(busy=false,active=true,displayId=b.getInt("displayId"),status="Waze launched on trusted display "+b.getInt("displayId")))else update(state.copy(busy=false,status=b.getString("exception")?:"Session start failed"))},{update(state.copy(busy=false,status="Session start failed: "+it.message))})}}
 }
 fun touch(action:Int,x:Float,y:Float,pressure:Float,time:Long){val service=remote?:return;if(!state.active)return;worker.execute{val b=runCatching{service.sendTouch(action,x,y,pressure,time)}.getOrNull();if(b?.getBoolean("success")!=true)main.post{update(state.copy(status=b?.getString("exception")?:"Touch delivery failed"))}}}
 fun stop(){val service=remote;if(service==null){update(state.copy(active=false,displayId=-1));return};update(state.copy(busy=true,status="Stopping embedded session"));worker.execute{val ok=runCatching{service.stopSession()}.getOrNull();main.post{update(EmbeddedWazeState(shellReady=true,status=if(ok?.getBoolean("success")==true)"Embedded session stopped" else ok?.getString("exception")?:"Cleanup failed"))}}}
 fun close(){if(closed)return;closed=true;if(state.active)runCatching{remote?.stopSession()};connection?.let{runCatching{Shizuku.unbindUserService(args,it,true)}};Shizuku.removeBinderReceivedListener(received);Shizuku.removeRequestPermissionResultListener(permission);worker.shutdownNow()}
 private fun update(value:EmbeddedWazeState){state=value;if(!closed)changed(value)}
 companion object{private const val REQUEST=7101}
}
