package com.osean.opencodephone;

import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Owns an app-sandboxed shell for the lifetime of the local terminal. */
final class LocalShellBridge {
    private final WebView view;
    private final String directory;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final StringBuilder pending=new StringBuilder();
    private boolean flushScheduled;
    private volatile int fd=-1, pid=-1;
    private volatile boolean closed;
    LocalShellBridge(WebView view,String directory){this.view=view;this.directory=directory;}
    private void emit(String fn,String value){view.post(()->{if(!closed)view.evaluateJavascript("window."+fn+" && window."+fn+"("+JSONObject.quote(value)+")",null);});}
    // One bridge call per frame instead of one per PTY read: a fast producer used to flood the UI thread.
    private void emitOutput(String text){
        synchronized(this){if(closed)return;pending.append(text);if(flushScheduled)return;flushScheduled=true;}
        ui.postDelayed(this::flushOutput,16);
    }
    private void flushOutput(){
        String chunk;
        synchronized(this){chunk=pending.toString();pending.setLength(0);flushScheduled=false;if(closed)return;}
        if(!chunk.isEmpty())view.evaluateJavascript("window.terminalOutput && window.terminalOutput("+JSONObject.quote(chunk)+")",null);
    }
    @JavascriptInterface public synchronized void start(){
        if(closed||fd>=0)return;
        try{int[] p=PtyNative.spawn(directory);if(p==null||p.length!=2){emit("localState","Could not start the phone shell");return;}fd=p[0];pid=p[1];final int currentFd=fd,currentPid=pid;emit("localState","Phone shell · app sandbox");
            new Thread(()->readLoop(currentFd,currentPid),"Phone terminal output").start();
        }catch(LinkageError|RuntimeException e){emit("localState","Phone shell is unavailable on this device");}
    }
    private void readLoop(int currentFd,int currentPid){
        try(InputStreamReader reader=new InputStreamReader(new InputStream(){
            @Override public int read(){byte[] one=new byte[1];int n=PtyNative.read(currentFd,one);return n>0?one[0]&255:-1;}
            @Override public int read(byte[] b,int off,int len){if(len==0)return 0;byte[] target=off==0&&len==b.length?b:new byte[len];int n=PtyNative.read(currentFd,target);if(n<=0)return -1;if(target!=b)System.arraycopy(target,0,b,off,n);return n;}
        },StandardCharsets.UTF_8)){char[] buffer=new char[4096];int n;while(!closed&&(n=reader.read(buffer))!=-1)emitOutput(new String(buffer,0,n));}
        catch(Exception ignored){}finally{synchronized(this){if(fd==currentFd){fd=-1;pid=-1;PtyNative.close(currentFd,currentPid);}}if(!closed)emit("localState","Shell ended · tap to start again");}
    }
    @JavascriptInterface public synchronized void send(String text){if(!closed&&fd>=0&&text!=null&&text.length()<=65536)PtyNative.write(fd,text.getBytes(StandardCharsets.UTF_8));}
    @JavascriptInterface public synchronized void resize(int cols,int rows){if(!closed&&fd>=0)PtyNative.resize(fd,cols,rows);}
    synchronized void shutdown(){closed=true;int oldFd=fd,oldPid=pid;fd=-1;pid=-1;if(oldFd>=0)PtyNative.close(oldFd,oldPid);}
}
