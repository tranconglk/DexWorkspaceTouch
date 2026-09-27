package com.trancong.dexworkspacetouch.feature.embeddedwaze
import android.app.Activity
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun EmbeddedWazeScreen(activity:Activity,onBack:()->Unit){
 var state by remember{mutableStateOf(EmbeddedWazeState())};var surface by remember{mutableStateOf<SurfaceView?>(null)}
 val session=remember(activity){EmbeddedWazeSession(activity.applicationContext){state=it}}
 DisposableEffect(session){session.start();onDispose{session.close()}}
 Scaffold(topBar={TopAppBar(title={Text("Embedded Waze (Experimental)")},navigationIcon={TextButton(onClick=onBack){Text("Back")}})}){padding->
  Column(Modifier.padding(padding).fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
   Text(state.status)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
    Button(onClick=session::connect,enabled=!state.shellReady&&!state.busy){Text("Connect Shizuku")}
    Button(onClick={surface?.holder?.surface?.let(session::startSession)},enabled=state.shellReady&&!state.active&&!state.busy&&surface?.holder?.surface?.isValid==true){Text("Start Waze")}
    OutlinedButton(onClick=session::stop,enabled=state.active&&!state.busy){Text("Stop")}
   }
   AndroidView(modifier=Modifier.fillMaxWidth().aspectRatio(4f/3f),factory={ctx->
    SurfaceView(ctx).also{view->surface=view;view.holder.setFixedSize(900,675);view.holder.addCallback(object:SurfaceHolder.Callback{
     override fun surfaceCreated(h:SurfaceHolder)=Unit
     override fun surfaceChanged(h:SurfaceHolder,f:Int,w:Int,hg:Int)=Unit
     override fun surfaceDestroyed(h:SurfaceHolder){if(state.active)session.stop()}
    });view.setOnTouchListener{_,event->if(!state.active)return@setOnTouchListener false;val p=mapPoint(event.x,event.y,view.width,view.height);val action=runCatching{virtualAction(event.actionMasked)}.getOrElse{return@setOnTouchListener false};session.touch(action,p.x,p.y,pressureFor(event.actionMasked,event.pressure),event.eventTime*1_000_000L);true}}
   })
  }
 }
}
