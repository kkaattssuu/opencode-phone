package com.osean.opencodephone;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/** Regression check for activity launch and notification service startup. */
public class LaunchTest extends Instrumentation {
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        Context context=getTargetContext();
        Intent launch=new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity=startActivitySync(launch);waitForIdleSync();
        if(activity.isFinishing())throw new AssertionError("Main activity closed on launch");
        Intent monitor=new Intent().setClassName(context,"com.osean.opencodephone.MonitorService").putExtra("url","https://example.com").putExtra("cookie","test=synthetic");
        context.startForegroundService(monitor);Thread.sleep(1000);waitForIdleSync();
        if(activity.isFinishing())throw new AssertionError("Task monitor closed the activity");
        context.stopService(monitor);
        int[] shell=PtyNative.spawn(context.getFilesDir().getAbsolutePath());if(shell==null||shell.length!=2)throw new AssertionError("Phone PTY failed to start");
        try{PtyNative.resize(shell[0],80,24);byte[] command="printf 'PHONE_%s_OK\\n' TERMINAL\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);if(PtyNative.write(shell[0],command)!=command.length)throw new AssertionError("Phone terminal input failed");
            java.util.concurrent.ExecutorService reader=java.util.concurrent.Executors.newSingleThreadExecutor();try{java.util.concurrent.Future<String> output=reader.submit(()->{StringBuilder value=new StringBuilder();byte[] bytes=new byte[2048];while(value.length()<8192){int n=PtyNative.read(shell[0],bytes);if(n<=0)break;value.append(new String(bytes,0,n,java.nio.charset.StandardCharsets.UTF_8));if(value.indexOf("PHONE_TERMINAL_OK")>=0)return value.toString();}return value.toString();});if(!output.get(8,java.util.concurrent.TimeUnit.SECONDS).contains("PHONE_TERMINAL_OK"))throw new AssertionError("Phone terminal output failed");}finally{reader.shutdownNow();}
        }finally{PtyNative.close(shell[0],shell[1]);}
        activity.finish();result.putString("result","PASS: launch, notification service, and phone terminal input/output");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result);}}
}
