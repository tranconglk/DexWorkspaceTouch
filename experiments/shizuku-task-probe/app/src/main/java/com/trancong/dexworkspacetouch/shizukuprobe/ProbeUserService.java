package com.trancong.dexworkspacetouch.shizukuprobe;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProbeUserService extends IProbeService.Stub {
    private static final String TAG = "COMP016";
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList("com.android.chrome", "com.sec.android.app.sbrowser"));
    private final Context context;

    public ProbeUserService(Context context) { this.context = context; }

    @Override public String runtimeInfo() {
        int permission = context.checkPermission("android.permission.MANAGE_ACTIVITY_TASKS", Process.myPid(), Process.myUid());
        return "serviceUid=" + Process.myUid() + " servicePid=" + Process.myPid() + " MANAGE_ACTIVITY_TASKS=" +
                (permission == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED") + " DUMP=" +
                (context.checkPermission(Manifest.permission.DUMP, Process.myPid(), Process.myUid()) == PackageManager.PERMISSION_GRANTED ? "GRANTED" : "DENIED");
    }

    @Override public String findTask(String packageName) throws android.os.RemoteException {
        requirePackage(packageName);
        Log.i(TAG, "FIND_BEGIN executorUid="+Process.myUid()+" executorPid="+Process.myPid()+" package="+packageName);
        try { String result=parseSingleTask(run("/system/bin/dumpsys", "activity", "activities"), packageName).encode(); Log.i(TAG,"FIND_END "+result); return result; }
        catch (Exception e) { Log.e(TAG,"FIND_FAILED",e); throw new android.os.RemoteException("FIND_FAILED: "+String.valueOf(e)); }
    }

    @Override public String workArea(int displayId) throws android.os.RemoteException {
        if(displayId<=0)throw new SecurityException("external display required");
        try {
            String dump=run("/system/bin/dumpsys","window","displays");
            Matcher block=Pattern.compile("(?ms)^\\s*Display: mDisplayId="+displayId+"\\b(.*?)(?=^\\s*Display: mDisplayId=|\\z)").matcher(dump);
            if(!block.find())throw new IllegalStateException("DISPLAY_NOT_FOUND");
            String value=block.group(); Matcher size=Pattern.compile("init=(\\d+)x(\\d+)").matcher(value);
            Matcher nav=Pattern.compile("type=navigationBars frame=\\[0,(\\d+)\\]\\[(\\d+),(\\d+)\\] visible=true").matcher(value);
            if(!size.find()||!nav.find())throw new IllegalStateException("WORK_AREA_NOT_FOUND");
            int width=Integer.parseInt(size.group(1)), height=Integer.parseInt(size.group(2)), bottom=Integer.parseInt(nav.group(1));
            if(width!=Integer.parseInt(nav.group(2))||height!=Integer.parseInt(nav.group(3))||bottom<=0||bottom>height)throw new IllegalStateException("WORK_AREA_INVALID");
            String result="displayId="+displayId+" mode="+width+"x"+height+" workArea=0,0-"+width+","+bottom; Log.i(TAG,"WORK_AREA "+result); return result;
        } catch(Exception e){Log.e(TAG,"WORK_AREA_FAILED",e);throw new android.os.RemoteException("WORK_AREA_FAILED: "+String.valueOf(e));}
    }

    @Override public String resizeVerified(String packageName, int taskId, int displayId, int l, int t, int r, int b, String cid) throws android.os.RemoteException {
        requirePackage(packageName);
        if (taskId <= 0 || displayId <= 0 || l < 0 || t < 0 || r <= l || b <= t || cid == null || !cid.matches("[A-Za-z0-9_-]{1,64}")) throw new SecurityException("invalid request");
        try {
            Task before = parseSingleTask(run("/system/bin/dumpsys", "activity", "activities"), packageName);
            if (before.taskId != taskId || before.displayId != displayId || before.windowingMode != 5) throw new SecurityException("task identity mismatch: " + before.encode());
            Log.i(TAG, "RESIZE_BEGIN cid=" + cid + " executorUid=" + Process.myUid() + " executorPid=" + Process.myPid() + " request=["+l+","+t+"-"+r+","+b+"] before=" + before.encode());
            ExecResult exec = exec("/system/bin/am", "task", "resize", String.valueOf(taskId), String.valueOf(l), String.valueOf(t), String.valueOf(r), String.valueOf(b));
            Thread.sleep(250);
            Task after = parseSingleTask(run("/system/bin/dumpsys", "activity", "activities"), packageName);
            String result = "cid="+cid+" exit="+exec.code+" stdout="+oneLine(exec.out)+" stderr="+oneLine(exec.err)+" before={"+before.encode()+"} after={"+after.encode()+"}";
            Log.i(TAG, "RESIZE_END " + result);
            return result;
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new android.os.RemoteException("interrupted"); }
        catch (SecurityException e) { throw e; }
        catch (Exception e) { throw new android.os.RemoteException(e.toString()); }
    }

    @Override public void destroy() { System.exit(0); }
    private static void requirePackage(String p) { if (!ALLOWED.contains(p)) throw new SecurityException("package not allowed"); }
    private static String oneLine(String s) { return s.replace('\n',' ').replace('\r',' ').trim(); }
    private static String run(String... args) throws Exception { ExecResult r=exec(args); if(r.code!=0) throw new IllegalStateException(r.err); return r.out; }
    private static ExecResult exec(String... args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(args); java.lang.Process p=pb.start();
        String out=read(p.getInputStream()), err=read(p.getErrorStream()); int code=p.waitFor(); return new ExecResult(code,out,err);
    }
    private static String read(java.io.InputStream in) throws Exception { StringBuilder s=new StringBuilder(); try(BufferedReader b=new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))){String l; while((l=b.readLine())!=null)s.append(l).append('\n');} return s.toString(); }
    private static Task parseSingleTask(String dump, String pkg) {
        String[] lines=dump.split("\\r?\\n"); int currentDisplay=-1; Task found=null;
        Pattern displayPattern=Pattern.compile("^Display #(\\d+)");
        Pattern taskPattern=Pattern.compile("Task\\{[^#]*#(\\d+).*A=\\d+:"+Pattern.quote(pkg)+".*mode=freeform");
        Pattern boundsPattern=Pattern.compile("mBounds=Rect\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*-\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)");
        for(int i=0;i<lines.length;i++) {
            Matcher display=displayPattern.matcher(lines[i].trim()); if(display.find()){currentDisplay=Integer.parseInt(display.group(1));continue;}
            Matcher task=taskPattern.matcher(lines[i]); if(!task.find()||currentDisplay<=0)continue;
            String bounds=null; for(int j=i+1;j<Math.min(lines.length,i+40);j++){Matcher b=boundsPattern.matcher(lines[j]);if(b.find()){bounds=b.group(1)+","+b.group(2)+"-"+b.group(3)+","+b.group(4);break;}}
            if(bounds==null){Log.i(TAG,"FIND_SKIP_SUMMARY task="+task.group(1));continue;}
            Task x=new Task(Integer.parseInt(task.group(1)),currentDisplay,5,bounds); if(found!=null)throw new IllegalStateException("AMBIGUOUS"); found=x;
        }
        if(found==null)throw new IllegalStateException("NOT_FOUND"); return found;
    }
    private static final class Task { final int taskId,displayId,windowingMode; final String bounds; Task(int a,int b,int c,String d){taskId=a;displayId=b;windowingMode=c;bounds=d;} String encode(){return "taskId="+taskId+" displayId="+displayId+" windowingMode="+windowingMode+" bounds="+bounds;} }
    private static final class ExecResult { final int code; final String out,err; ExecResult(int c,String o,String e){code=c;out=o;err=e;} }
}
