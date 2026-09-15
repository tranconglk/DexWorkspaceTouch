package com.trancong.dexworkspacetouch.shizukuprobe;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private static final int REQUEST_CODE = 16;
    private final Shizuku.UserServiceArgs serviceArgs = new Shizuku.UserServiceArgs(new ComponentName("com.trancong.dexworkspacetouch.shizukuprobe", ProbeUserService.class.getName())).daemon(false).processNameSuffix("comp016").debuggable(true).version(1);
    private TextView status; private IProbeService service; private int taskId=-1, displayId=-1;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder binder) { service=IProbeService.Stub.asInterface(binder); show("CONNECTED\n"+callRuntime()); }
        @Override public void onServiceDisconnected(ComponentName n) { service=null; show("DISCONNECTED"); }
    };
    @Override protected void onCreate(Bundle state) { super.onCreate(state); buildUi(); check(); }
    private void buildUi(){ LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); int p=24; box.setPadding(p,p,p,p); status=new TextView(this); status.setTextSize(16); box.addView(status); add(box,"CHECK",this::check); add(box,"REQUEST SHIZUKU PERMISSION",this::request); add(box,"CONNECT SERVICE",this::connect); add(box,"FIND CHROME",this::find); add(box,"MOVE LEFT",()->move(false)); add(box,"MOVE RIGHT",()->move(true)); ScrollView s=new ScrollView(this); s.addView(box); setContentView(s); }
    private void add(LinearLayout box,String label,Runnable action){Button b=new Button(this); b.setText(label); b.setMinHeight(56); b.setOnClickListener(v->action.run()); box.addView(b);}
    private void check(){ try{show("appUid="+Process.myUid()+" appPid="+Process.myPid()+" binderAlive="+Shizuku.pingBinder()+" version="+Shizuku.getVersion()+" permission="+(Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED?"GRANTED":"DENIED"));}catch(Exception e){show("CHECK ERROR "+e);} }
    private void request(){if(Shizuku.isPreV11())show("UNSUPPORTED_SHIZUKU"); else Shizuku.requestPermission(REQUEST_CODE);}
    private void connect(){if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){show("AUTHORIZE FIRST");return;} Shizuku.bindUserService(serviceArgs,connection);}
    private void find(){if(service==null){show("CONNECT FIRST");return;} try{String r=service.findTask("com.android.chrome"); if(r==null){show("FIND ERROR null response");return;} Matcher t=Pattern.compile("taskId=(\\d+)").matcher(r),d=Pattern.compile("displayId=(\\d+)").matcher(r); if(t.find()&&d.find()){taskId=Integer.parseInt(t.group(1));displayId=Integer.parseInt(d.group(1));} show(r);}catch(Exception e){show("FIND ERROR "+e);} }
    private void move(boolean right){if(service==null){show("CONNECT FIRST");return;} if(taskId<1)find(); if(taskId<1)return; new Thread(()->{try{String area=service.workArea(displayId);Matcher a=Pattern.compile("workArea=0,0-(\\d+),(\\d+)").matcher(area);if(!a.find())throw new IllegalStateException("bad work area: "+area);int width=Integer.parseInt(a.group(1)),height=Integer.parseInt(a.group(2)),gap=8;int mid=width/2;int l=right?mid+gap:gap,r=right?width-gap:mid-gap;String cid=(right?"RIGHT":"LEFT")+System.currentTimeMillis();String out=area+" "+service.resizeVerified("com.android.chrome",taskId,displayId,l,gap,r,height-gap,cid);runOnUiThread(()->show(out));}catch(Exception e){runOnUiThread(()->show("RESIZE ERROR "+e));}}).start();}
    private String callRuntime(){try{return service.runtimeInfo();}catch(Exception e){return "RUNTIME ERROR "+e;}}
    private void show(String s){status.setText(s);android.util.Log.i("COMP016","APP "+s.replace('\n',' '));}
    @Override public void onRequestPermissionsResult(int req,String[] perms,int[] results){super.onRequestPermissionsResult(req,perms,results);if(req==REQUEST_CODE)check();}
}
