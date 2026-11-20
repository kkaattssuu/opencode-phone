package com.osean.opencodephone;

import android.app.*;
import android.content.*;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.*;
import java.io.*;
import java.net.*;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class MonitorService extends Service {
    private static final String MONITOR="task_monitor_v2", FINISHED="task_finished_v2";
    private static final int FOREGROUND_ID=1;
    private volatile boolean running; private Thread worker; private volatile HttpURLConnection connection; private String activeUrl, activeCookie;
    private final Set<String> active=new HashSet<>();
    private final Map<String,Long> completed=new HashMap<>();
    private boolean connected;
    @Override public void onCreate(){super.onCreate();NotificationManager n=getSystemService(NotificationManager.class);
        NotificationChannel monitor=new NotificationChannel(MONITOR,"OpenCode connection and tasks",NotificationManager.IMPORTANCE_LOW);monitor.setSound(null,null);n.createNotificationChannel(monitor);
        NotificationChannel done=new NotificationChannel(FINISHED,"Task results",NotificationManager.IMPORTANCE_DEFAULT);done.setDescription("Notifications when an OpenCode task finishes");done.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());n.createNotificationChannel(done);
    }
    private Notification.Builder builder(String channel,String title,String message){Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);return new Notification.Builder(this,channel).setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(message).setContentIntent(pi);}
    private synchronized Notification foreground(){String title,message;if(!connected){title="OpenCode";message="Connecting to your laptop";}else if(active.isEmpty()){title="OpenCode connected";message="Your laptop is available";}else{title=active.size()==1?"OpenCode task in progress":active.size()+" OpenCode tasks in progress";message="You'll be notified when work finishes";}return builder(MONITOR,title,message).setOngoing(true).setOnlyAlertOnce(true).setProgress(0,0,!active.isEmpty()).build();}
    private synchronized void refresh(){if(running)try{getSystemService(NotificationManager.class).notify(FOREGROUND_ID,foreground());}catch(RuntimeException ignored){}}
    private void result(String key,String type){
        final String url=activeUrl,cookie=activeCookie;
        new Thread(()->{
            String title="OpenCode session",text=type.equals("session.execution.failed")?"Task failed":type.equals("session.execution.interrupted")?"Task stopped":"Task finished";
            try{
                String path="/api/session/"+android.net.Uri.encode(key);
                JSONObject session=getJson(url+path,cookie).optJSONObject("data");
                if(session!=null && !session.optString("title", "").trim().isEmpty())title=session.optString("title");
                if(!type.equals("session.execution.failed")&&!type.equals("session.execution.interrupted")){
                    JSONArray messages=getJson(url+path+"/message?limit=20&order=desc",cookie).optJSONArray("data");
                    if(messages!=null)for(int i=0;i<messages.length();i++){
                        JSONObject message=messages.optJSONObject(i);if(message==null)continue;if("user".equals(message.optString("type")))break;if(!"assistant".equals(message.optString("type")))continue;
                        JSONArray content=message.optJSONArray("content");StringBuilder answer=new StringBuilder();
                        if(content!=null)for(int j=0;j<content.length();j++){JSONObject part=content.optJSONObject(j);if(part!=null&&"text".equals(part.optString("type"))){String t=part.optString("text","").trim();if(!t.isEmpty()){if(answer.length()>0)answer.append("\n");answer.append(t);}}}
                        if(answer.length()>0){text=answer.toString();break;}
                    }
                }
            }catch(Exception ignored){}
            if(text.length()>4000)text=text.substring(0,4000)+"…";
            Intent open=new Intent(this,MainActivity.class).setAction("com.osean.opencodephone.OPEN_OPENCODE").setData(android.net.Uri.parse("opencodephone://opencode/"+android.net.Uri.encode(key))).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            try{getSystemService(NotificationManager.class).notify("task:"+key,2,builder(FINISHED,title,text).setContentIntent(pi).setStyle(new Notification.BigTextStyle().bigText(text)).setAutoCancel(true).setCategory(Notification.CATEGORY_STATUS).build());}catch(RuntimeException ignored){}
        },"OpenCode notification").start();
    }
    private JSONObject getJson(String url,String cookie)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();try{c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(10000);c.setRequestProperty("Cookie",cookie);c.setRequestProperty("Accept","application/json");if(c.getResponseCode()!=200)throw new IOException("Request failed");return new JSONObject(readAll(c.getInputStream()));}finally{c.disconnect();}}
    @Override public int onStartCommand(Intent intent,int flags,int id){try{startForeground(FOREGROUND_ID,foreground());}catch(RuntimeException e){stopSelf();return START_NOT_STICKY;}if(intent==null){stopSelf();return START_NOT_STICKY;}
        String url=intent.getStringExtra("url"),cookie=intent.getStringExtra("cookie");if(url==null||cookie==null||!url.startsWith("https://")){stopSelf();return START_NOT_STICKY;}
        if(running&&worker!=null&&worker.isAlive()&&url.equals(activeUrl)&&cookie.equals(activeCookie))return START_NOT_STICKY;
        running=false;if(connection!=null)connection.disconnect();if(worker!=null)worker.interrupt();
        synchronized(this){active.clear();completed.clear();connected=false;}activeUrl=url;activeCookie=cookie;running=true;refresh();worker=new Thread(()->listen(url,cookie),"OpenCode events");worker.start();return START_NOT_STICKY;
    }
    private void listen(String url,String cookie){while(running&&!Thread.currentThread().isInterrupted()){
        HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(url+"/api/event").openConnection();connection=c;c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(90000);c.setRequestProperty("Accept","text/event-stream");c.setRequestProperty("Cookie",cookie);
            int status=c.getResponseCode();if(status==401){expired();return;}if(status!=200)throw new IOException("Event stream unavailable");synchronized(this){connected=true;}refresh();catchUp(url,cookie);
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"))){String line;StringBuilder data=new StringBuilder();while(running&&!Thread.currentThread().isInterrupted()&&(line=reader.readLine())!=null){if(line.startsWith("data:")){if(data.length()>0)data.append('\n');data.append(line.substring(5).trim());}else if(line.isEmpty()&&data.length()>0){event(data.toString());data.setLength(0);}}}
        }catch(Exception ignored){}finally{if(c!=null)c.disconnect();if(connection==c)connection=null;}
        if(running&&!Thread.currentThread().isInterrupted()){synchronized(this){connected=false;}refresh();try{Thread.sleep(10000);}catch(InterruptedException e){return;}}
    }}
    /** The pairing session is gone; retrying cannot succeed, so say so and stop. */
    private void expired(){
        running=false;
        try{getSystemService(NotificationManager.class).notify(3,builder(FINISHED,"OpenCode session expired","Pair your laptop again to keep getting task notifications").setAutoCancel(true).build());}catch(RuntimeException ignored){}
        stopSelf();
    }
    /** Reconcile sessions that started or ended while the event stream was down. */
    private void catchUp(String url,String cookie){
        Set<String> now=activeSessions(url,cookie);if(now==null)return;
        List<String> ended=new ArrayList<>();
        synchronized(this){
            for(String key:active)if(!now.contains(key))ended.add(key);
            active.retainAll(now);active.addAll(now);
            long nowMillis=System.currentTimeMillis();
            for(String key:ended){Long prior=completed.get(key);if(prior==null||nowMillis-prior>=30000){completed.put(key,nowMillis);result(key,"session.idle");}}
        }
        refresh();
    }
    private Set<String> activeSessions(String url,String cookie){
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)new URL(url+"/api/session/active").openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setRequestProperty("Cookie",cookie);
            if(c.getResponseCode()!=200)return null;
            JSONArray data=new JSONObject(readAll(c.getInputStream())).optJSONArray("data");if(data==null)return null;
            Set<String> ids=new HashSet<>();
            for(int i=0;i<data.length();i++){Object item=data.opt(i);String id="";if(item instanceof String)id=(String)item;else if(item instanceof JSONObject)id=((JSONObject)item).optString("id",((JSONObject)item).optString("sessionID",""));if(!id.isEmpty())ids.add(id);}
            return ids.isEmpty() && data.length()>0 ? null : ids; // unknown shape: skip reconciliation rather than report everything as finished
        }catch(Exception e){return null;}finally{if(c!=null)c.disconnect();}
    }
    private static String readAll(InputStream in) throws IOException{java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);return out.toString("UTF-8");}
    private synchronized void event(String raw){try{JSONObject event=new JSONObject(raw);JSONObject payload=event.optJSONObject("payload");if(payload!=null)event=payload;String type=event.optString("type");JSONObject data=event.optJSONObject("data");if(data==null)data=event.optJSONObject("properties");if(data==null)data=new JSONObject();String key=data.optString("sessionID",data.optString("sessionId",""));if(key.isEmpty())return;
        if(type.equals("session.execution.started")){active.add(key);completed.remove(key);refresh();return;}
        if(type.equals("session.status")){JSONObject status=data.optJSONObject("status");String state=status==null?"":status.optString("type");if(state.equals("busy")||state.equals("retry")){active.add(key);completed.remove(key);refresh();return;}if(state.equals("idle"))type="session.idle";}
        boolean success=type.equals("session.execution.succeeded"), failed=type.equals("session.execution.failed"), interrupted=type.equals("session.execution.interrupted"), idle=type.equals("session.idle");
        if(success||failed||interrupted||idle){boolean wasActive=active.remove(key);long now=System.currentTimeMillis();Long prior=completed.get(key);boolean recent=prior!=null&&now-prior<30000;if(!recent&&(wasActive||success||failed||interrupted)){completed.put(key,now);if(completed.size()>200)completed.entrySet().removeIf(e->now-e.getValue()>30000);result(key,type);}refresh();}
    }catch(Exception ignored){}}
    @Override public void onDestroy(){running=false;if(connection!=null)connection.disconnect();if(worker!=null)worker.interrupt();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
