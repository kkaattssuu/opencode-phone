package com.osean.opencodephone;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.*;

public class MainActivity extends Activity {
    private static final int BG=0xff0d0303, CARD=0xff201216, TEXT=0xffeff8ff, MUTED=0xffc0aeb6, ACCENT=0xffbbef1f;
    private static final int SCAN_REQUEST=41;
    private static final String DESKTOP_AGENT="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    private LinearLayout root, body; private WebView web, codexWeb, laptopWeb, phoneWeb, simpleWeb; private boolean simpleMode; private LocalShellBridge localShell; private TextView subtitle; private ProgressBar progress; private LinearLayout sessionBar; private boolean shownWeb; private String terminalKind; private boolean toolbarHidden; private boolean fullscreen; private boolean pageFailed; private boolean desktop; private String mobileAgent;
    private String base() { return getPreferences(MODE_PRIVATE).getString("url", ""); }
    int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+0.5f);}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    TextView label(String text,int size,int color,boolean bold){ TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v; }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        fullscreen=getPreferences(MODE_PRIVATE).getBoolean("fullscreen",true);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);setContentView(root);applyFullscreen();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int left,top,right,bottom;
            if (Build.VERSION.SDK_INT >= 30) { android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime()); left=bars.left;top=bars.top;right=bars.right;bottom=bars.bottom; }
            else { left=insets.getSystemWindowInsetLeft();top=insets.getSystemWindowInsetTop();right=insets.getSystemWindowInsetRight();bottom=insets.getSystemWindowInsetBottom(); }
            // In fullscreen sessions let the workspace extend into the landscape cutout area.
            if(fullscreen && shownWeb){left=0;right=0;}
            v.setPadding(left,top,right,bottom);return insets;
        });root.requestApplyInsets();
        desktop=getPreferences(MODE_PRIVATE).getBoolean("desktop",false);
        web=new WebView(this);web.setBackgroundColor(BG);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);mobileAgent=web.getSettings().getUserAgentString();configureWeb();
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){Uri u=req.getUrl();if(!"https".equalsIgnoreCase(u.getScheme()))return true;Uri orig=Uri.parse(base());if(orig.getHost()!=null&&orig.getHost().equalsIgnoreCase(u.getHost())&&orig.getPort()==u.getPort())return false;try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}return true;}
            @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){pageFailed=false;if(progress!=null){progress.setProgress(0);progress.setVisibility(View.VISIBLE);}}
            @Override public void onReceivedError(WebView v,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame()){pageFailed=true;if(progress!=null)progress.setVisibility(View.GONE);Toast.makeText(MainActivity.this,"Could not reach your laptop. Check that it is awake and online.",Toast.LENGTH_LONG).show();}}
            @Override public void onReceivedHttpError(WebView v,WebResourceRequest request,WebResourceResponse response){if(request.isForMainFrame() && response.getStatusCode()==401){pageFailed=true;Toast.makeText(MainActivity.this,"Your session expired. Pair your laptop again.",Toast.LENGTH_LONG).show();}}
            @Override public void onPageFinished(WebView v,String url){super.onPageFinished(v,url);if(!pageFailed)applyViewport();if(progress!=null)progress.setVisibility(View.GONE);CookieManager.getInstance().flush(); if(shownWeb && !pageFailed && !base().isEmpty() && url.startsWith(base()+"/") && !url.contains("/auth/connect/")) startMonitoring();}
        });web.setWebChromeClient(new WebChromeClient(){@Override public void onProgressChanged(WebView view,int value){if(progress!=null && value<100){progress.setVisibility(View.VISIBLE);progress.setProgress(value);}}});
        home();handleNotification(getIntent());
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleNotification(intent);}
    private void handleNotification(Intent intent){if(intent==null||intent.getAction()==null)return;String action=intent.getAction();if(action.equals("com.osean.opencodephone.OPEN_CODEX")){String id=intent.getData()==null?null:intent.getData().getLastPathSegment();if(id==null||id.isEmpty())return;simpleMode=true;boolean existing=simpleWeb!=null;pendingCodexSession=id;showCodex();if(existing){simpleWeb.evaluateJavascript("window.openCodexSession && window.openCodexSession("+org.json.JSONObject.quote(id)+")",null);pendingCodexSession=null;}}else if(action.equals("com.osean.opencodephone.OPEN_CODEX_TUI")){simpleMode=false;showCodex();}else if(action.equals("com.osean.opencodephone.OPEN_OPENCODE")){if(!base().isEmpty())showWeb();}}
    private String pendingCodexSession;
    @SuppressWarnings("deprecation")
    void applyFullscreen(){
        if(Build.VERSION.SDK_INT>=28){WindowManager.LayoutParams params=getWindow().getAttributes();params.layoutInDisplayCutoutMode=fullscreen?(Build.VERSION.SDK_INT>=30?WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS:WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES):WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;getWindow().setAttributes(params);}
        if(Build.VERSION.SDK_INT>=30){WindowInsetsController controller=getWindow().getInsetsController();if(controller!=null){controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);if(fullscreen)controller.hide(WindowInsets.Type.systemBars());else controller.show(WindowInsets.Type.systemBars());}}
        else {getWindow().getDecorView().setSystemUiVisibility(fullscreen?(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION):0);}
    }
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused&&fullscreen)applyFullscreen();}
    void clear(){ progress=null; sessionBar=null; if(web!=null && web.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)web.getParent()).removeView(web);for(WebView terminal:new WebView[]{codexWeb,laptopWeb,phoneWeb,simpleWeb})if(terminal!=null && terminal.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)terminal.getParent()).removeView(terminal);root.removeAllViews(); }
    // The touch listener returns false, so the view still handles the click and TalkBack.
    @SuppressLint("ClickableViewAccessibility")
    TextView action(String text,boolean primary,Runnable run){ TextView b=label(text,15,primary?BG:TEXT,true); b.setGravity(Gravity.CENTER);b.setPadding(dp(16),dp(15),dp(16),dp(15));android.graphics.drawable.Drawable fill=shape(primary?ACCENT:0xff38202a,14);b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(primary?0x33000000:0x55ffffff),fill,shape(Color.WHITE,14)));b.setOnTouchListener((v,event)->{switch(event.getActionMasked()){case android.view.MotionEvent.ACTION_DOWN:v.animate().scaleX(.97f).scaleY(.97f).setDuration(100).start();break;case android.view.MotionEvent.ACTION_UP:case android.view.MotionEvent.ACTION_CANCEL:v.animate().scaleX(1f).scaleY(1f).setDuration(140).start();break;}return false;});if(primary){b.setElevation(dp(8));if(Build.VERSION.SDK_INT>=28)b.setOutlineSpotShadowColor(0xffff0055);}b.setOnClickListener(v->run.run());return b; }
    void addSpace(LinearLayout l,int h){Space s=new Space(this);l.addView(s,new LinearLayout.LayoutParams(1,dp(h)));}
    void home(){ shownWeb=false;terminalKind=null;toolbarHidden=fullscreen;clear();root.requestApplyInsets();ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);root.addView(scroll);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(24),dp(32),dp(24),dp(28));FrameLayout content=new FrameLayout(this);scroll.addView(content);FrameLayout.LayoutParams bodyParams=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);bodyParams.width=Math.min(getResources().getDisplayMetrics().widthPixels,dp(680));content.addView(body,bodyParams);
        TextView brand=label("●  OPENCODE + CODEX",12,ACCENT,true);brand.setLetterSpacing(.1f);body.addView(brand);addSpace(body,32);
        body.addView(label("Your workspace,\nwithin reach.",34,TEXT,true));addSpace(body,12);TextView intro=label("Your coding agents, wherever you are.",16,MUTED,false);intro.setLineSpacing(dp(4),1);body.addView(intro);addSpace(body,32);
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(22),dp(22),dp(22),dp(22));card.setBackground(shape(CARD,22));body.addView(card);
        card.addView(label("YOUR CONNECTION",11,MUTED,true));addSpace(card,12);card.addView(label(base().isEmpty()?"Connect your laptop":"Ready to connect",21,TEXT,true));addSpace(card,7);subtitle=label(base().isEmpty()?"Pair once to access OpenCode remotely.":"Choose an agent to open on your laptop.",14,MUTED,false);subtitle.setLineSpacing(dp(3),1);card.addView(subtitle);addSpace(card,22);
        card.addView(action(base().isEmpty()?"Connect laptop  →":"Open OpenCode  →",true,()->{if(base().isEmpty())scan();else showWeb();}));addSpace(card,10);TextView codexButton=action("Open Codex  →",false,()->{if(base().isEmpty())scan();else showCodex();});codexButton.setBackground(shape(0xffff0055,14));card.addView(codexButton);addSpace(card,10);card.addView(action(base().isEmpty()?"Enter link instead":"Change connection",false,()->chooseConnection()));
        addSpace(body,24);body.addView(label("WORK FROM ANYWHERE",11,MUTED,true));addSpace(body,12);
        LinearLayout note=new LinearLayout(this);note.setOrientation(LinearLayout.VERTICAL);note.setPadding(dp(20),dp(18),dp(20),dp(18));note.setBackground(shape(CARD,18));body.addView(note);
        note.addView(label("✦   Task notifications",16,TEXT,true));addSpace(note,7);note.addView(label("We’ll monitor tasks when you’re connected.",14,MUTED,false));addSpace(note,20);note.addView(label("⌘   Terminals",16,TEXT,true));addSpace(note,7);note.addView(label("Run commands on your phone or laptop.",14,MUTED,false));addSpace(note,14);note.addView(action("Phone terminal  →",false,()->showTerminal("phone")));addSpace(note,10);note.addView(action("Laptop terminal  →",false,()->showTerminal("laptop")));
        addSpace(body,24);TextView info=label("Keep your laptop awake and connected to the internet.",13,MUTED,false);info.setGravity(Gravity.CENTER);body.addView(info);
        body.setAlpha(0f);body.setTranslationY(dp(14));body.animate().alpha(1f).translationY(0).setDuration(300).setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f)).start();
        card.setAlpha(0f);card.setTranslationY(dp(12));card.animate().alpha(1f).translationY(0).setStartDelay(45).setDuration(300).setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f)).start();
        note.setAlpha(0f);note.setTranslationY(dp(12));note.animate().alpha(1f).translationY(0).setStartDelay(90).setDuration(300).setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f)).start();
    }
    boolean landscape(){return getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE;}
    @SuppressLint("ClickableViewAccessibility")
    TextView toolbarButton(String text,String description,Runnable run){
        TextView button=label(text,text.equals("OFF")?12:22,TEXT,true);button.setContentDescription(description);button.setGravity(Gravity.CENTER);button.setIncludeFontPadding(false);if("OFF".equals(text))button.setTextSize(12);button.setMinWidth(dp(44));button.setMinHeight(dp(44));button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x44ffffff),shape(0xff38202a,12),shape(Color.WHITE,12)));button.setOnTouchListener((v,event)->{switch(event.getActionMasked()){case MotionEvent.ACTION_DOWN:v.animate().scaleX(.94f).scaleY(.94f).setDuration(90).start();break;case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:v.animate().scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();break;}return false;});button.setOnClickListener(v->run.run());return button;
    }
    void updateSessionBar(){if(sessionBar==null)return;boolean wide=landscape();sessionBar.setPadding(dp(wide?8:14),dp(wide?2:6),dp(wide?8:14),dp(wide?2:6));}
    void showWeb(){
        shownWeb=true;terminalKind=null;clear();root.requestApplyInsets();
        if(!toolbarHidden){
            sessionBar=new LinearLayout(this);sessionBar.setGravity(Gravity.CENTER_VERTICAL);sessionBar.setBackgroundColor(BG);updateSessionBar();root.addView(sessionBar);
            TextView back=toolbarButton("‹","Back to home",()->home());sessionBar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
            LinearLayout titleBlock=new LinearLayout(this);titleBlock.setOrientation(LinearLayout.VERTICAL);titleBlock.setPadding(dp(12),0,0,0);titleBlock.addView(label("OpenCode",16,TEXT,true));TextView mode=label(desktop?"DESKTOP VIEW":"PHONE VIEW",10,MUTED,true);mode.setLetterSpacing(.06f);titleBlock.addView(mode);sessionBar.addView(titleBlock,new LinearLayout.LayoutParams(0,-2,1));
            TextView size=toolbarButton("Aa","Desktop size",()->chooseDesktopSize());size.setTextSize(16);LinearLayout.LayoutParams sizeParams=new LinearLayout.LayoutParams(dp(44),dp(44));sizeParams.rightMargin=dp(6);sessionBar.addView(size,sizeParams);
            TextView disconnect=toolbarButton("OFF","End connection",()->confirmDisconnect());sessionBar.addView(disconnect,new LinearLayout.LayoutParams(dp(44),dp(44)));TextView refresh=toolbarButton("↻","Reload OpenCode",()->web.reload());LinearLayout.LayoutParams refreshParams=new LinearLayout.LayoutParams(dp(44),dp(44));refreshParams.rightMargin=dp(6);sessionBar.addView(refresh,refreshParams);
            TextView menu=toolbarButton("⋮","View options",()->{});menu.setOnClickListener(v->viewOptions(menu));sessionBar.addView(menu,new LinearLayout.LayoutParams(dp(44),dp(44)));
            progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));progress.setVisibility(web.getProgress()<100?View.VISIBLE:View.GONE);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(2)));
            sessionBar.setAlpha(0f);sessionBar.setTranslationY(-dp(5));sessionBar.animate().alpha(1f).translationY(0).setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        }
        FrameLayout workspace=new FrameLayout(this);root.addView(workspace,new LinearLayout.LayoutParams(-1,0,1));workspace.addView(web,new FrameLayout.LayoutParams(-1,-1));
        if(toolbarHidden){
            TextView restore=label("×",20,TEXT,true);restore.setContentDescription("Show toolbar");restore.setGravity(Gravity.CENTER);restore.setBackground(shape(0x994e5055,3));restore.setElevation(dp(5));
            FrameLayout.LayoutParams button=new FrameLayout.LayoutParams(dp(34),dp(34),Gravity.END|Gravity.CENTER_VERTICAL);button.rightMargin=dp(6);workspace.addView(restore,button);
            restore.setOnClickListener(v->{toolbarHidden=false;showWeb();});restore.setAlpha(0f);restore.animate().alpha(.88f).setStartDelay(80).setDuration(200).start();
        }
        if((web.getUrl()==null || "about:blank".equals(web.getUrl())) && !base().isEmpty())web.loadUrl(base());else if(!pageFailed && !base().isEmpty() && web.getProgress()==100)startMonitoring();
        web.post(this::applyViewport);
    }
    String terminalAsset(String name) throws IOException {try(InputStream in=getAssets().open("terminal/"+name);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);return out.toString("UTF-8");}}
    WebView createRemoteTerminal(boolean isCodex){
        WebView terminal=new WebView(this);terminal.setBackgroundColor(BG);terminal.getSettings().setJavaScriptEnabled(true);terminal.getSettings().setDomStorageEnabled(true);terminal.getSettings().setAllowFileAccess(false);terminal.getSettings().setAllowContentAccess(false);
        if(isCodex)terminal.addJavascriptInterface(new Object(){@JavascriptInterface public void codexAttention(){
            NotificationManager manager=getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel("codex_alerts","Codex updates",NotificationManager.IMPORTANCE_DEFAULT));
            Intent open=new Intent(MainActivity.this,MainActivity.class).setAction("com.osean.opencodephone.OPEN_CODEX_TUI").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);PendingIntent action=PendingIntent.getActivity(MainActivity.this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            try{manager.notify(493,new Notification.Builder(MainActivity.this,"codex_alerts").setSmallIcon(R.drawable.ic_notification).setContentTitle("Codex update").setContentText("Open Codex to see the result or respond.").setContentIntent(action).setAutoCancel(true).build());}catch(RuntimeException ignored){}
        }},"NativeNotice");
        terminal.addJavascriptInterface(new Object(){@JavascriptInterface public void finished(boolean ok){runOnUiThread(()->remoteCloseResult(ok));}},"NativeClose");
        terminal.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){return true;}});
        try{
            org.json.JSONObject spec=new org.json.JSONObject();spec.put("command","/bin/bash");org.json.JSONArray args=new org.json.JSONArray();
            if(isCodex){args.put("-lc");args.put("cd \"$HOME\" && exec codex -c 'tui.notification_method=\"osc9\"' -c 'tui.notification_condition=\"always\"'");}
            spec.put("args",args);spec.put("title",isCodex?"Codex Phone":"Phone Laptop Terminal");spec.put("env",new org.json.JSONObject().put("TERM","xterm-256color"));
            String html=terminalAsset("codex.html").replace("/*XTERM_CSS*/",terminalAsset("xterm.css")).replace("/*XTERM_JS*/",terminalAsset("xterm.js")).replace("/*FIT_JS*/",terminalAsset("fit.js")).replace("/*BASE_URL*/",org.json.JSONObject.quote(base())).replace("/*FONT_SIZE*/",Integer.toString(codexFontSize())).replace("/*TERMINAL_NAME*/",org.json.JSONObject.quote(isCodex?"Codex":"Laptop terminal")).replace("/*SESSION_KEY*/",org.json.JSONObject.quote(isCodex?"phone.codex.pty":"phone.laptop.pty")).replace("/*PTY_SPEC*/",spec.toString());terminal.loadDataWithBaseURL(base()+"/",html,"text/html","UTF-8",null);
        }catch(Exception e){Toast.makeText(this,"Could not open the terminal",Toast.LENGTH_LONG).show();}
        return terminal;
    }
    WebView createPhoneTerminal(){
        WebView terminal=new WebView(this);terminal.setBackgroundColor(BG);terminal.getSettings().setJavaScriptEnabled(true);terminal.getSettings().setAllowFileAccess(false);terminal.getSettings().setAllowContentAccess(false);terminal.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){return true;}@Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request){if(request.isForMainFrame() && "data".equalsIgnoreCase(request.getUrl().getScheme()))return null;return new WebResourceResponse("text/plain","UTF-8",403,"Forbidden",java.util.Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}});
        localShell=new LocalShellBridge(terminal,getFilesDir().getAbsolutePath());terminal.addJavascriptInterface(localShell,"LocalShell");
        try{String html=terminalAsset("phone.html").replace("/*XTERM_CSS*/",terminalAsset("xterm.css")).replace("/*XTERM_JS*/",terminalAsset("xterm.js")).replace("/*FIT_JS*/",terminalAsset("fit.js")).replace("/*FONT_SIZE*/",Integer.toString(codexFontSize()));terminal.loadDataWithBaseURL("https://terminal.invalid/",html,"text/html","UTF-8",null);}
        catch(IOException e){Toast.makeText(this,"Could not open the phone terminal",Toast.LENGTH_LONG).show();}return terminal;
    }
    WebView activeTerminal(){if("codex".equals(terminalKind))return simpleMode?simpleWeb:codexWeb;if("laptop".equals(terminalKind))return laptopWeb;if("phone".equals(terminalKind))return phoneWeb;return null;}
    void showCodex(){showTerminal("codex");}
    WebView createSimpleCodex(){WebView v=new WebView(this);v.setBackgroundColor(BG);v.getSettings().setJavaScriptEnabled(true);v.getSettings().setDomStorageEnabled(true);v.getSettings().setAllowFileAccess(false);v.addJavascriptInterface(new Object(){@JavascriptInterface public void finished(boolean ok){runOnUiThread(()->remoteCloseResult(ok));}},"NativeClose");v.addJavascriptInterface(new Object(){@JavascriptInterface public void taskFinished(String id,String name,String answer){
        if(id==null||id.isEmpty()||id.length()>256)return;
        if(name==null||name.trim().isEmpty())name="Codex session";if(name.length()>200)name=name.substring(0,200);
        if(answer==null||answer.trim().isEmpty())answer="Task finished";if(answer.length()>4000)answer=answer.substring(0,4000)+"…";
        NotificationManager manager=getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel("codex_results","Codex task results",NotificationManager.IMPORTANCE_DEFAULT));
        Intent open=new Intent(MainActivity.this,MainActivity.class).setAction("com.osean.opencodephone.OPEN_CODEX").setData(Uri.parse("opencodephone://codex/"+Uri.encode(id))).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent action=PendingIntent.getActivity(MainActivity.this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        try{manager.notify("codex:"+id,494,new Notification.Builder(MainActivity.this,"codex_results").setSmallIcon(R.drawable.ic_notification).setContentTitle(name).setContentText(answer).setStyle(new Notification.BigTextStyle().bigText(answer)).setContentIntent(action).setAutoCancel(true).build());}catch(RuntimeException ignored){}
    }},"NativeSimple");v.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){return true;}});try{String html=terminalAsset("simple.html").replace("/*BASE_URL*/",org.json.JSONObject.quote(base())).replace("/*INITIAL_TOOLBAR_CLASS*/",toolbarHidden?"toolbar-hidden":"").replace("/*INITIAL_THREAD*/",org.json.JSONObject.quote(pendingCodexSession==null?"":pendingCodexSession));pendingCodexSession=null;v.loadDataWithBaseURL(base()+"/",html,"text/html","UTF-8",null);}catch(IOException e){Toast.makeText(this,"Could not open simple view",Toast.LENGTH_LONG).show();}return v;}
    void showTerminal(String kind){
        boolean local=kind.equals("phone");if(!local && base().isEmpty()){scan();return;}shownWeb=true;terminalKind=kind;clear();root.requestApplyInsets();if(!local)startMonitoring();
        final String titleText=kind.equals("codex")?"Codex":local?"Phone terminal":"Laptop terminal";
        if(!toolbarHidden){sessionBar=new LinearLayout(this);sessionBar.setGravity(Gravity.CENTER_VERTICAL);sessionBar.setBackgroundColor(BG);updateSessionBar();root.addView(sessionBar);
            TextView back=toolbarButton("‹","Back to home",()->home());sessionBar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));TextView title=label(titleText,16,TEXT,true);title.setPadding(dp(12),0,0,0);sessionBar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            TextView size=toolbarButton("Aa","Terminal text size",()->chooseCodexSize());size.setTextSize(16);LinearLayout.LayoutParams sizeParams=new LinearLayout.LayoutParams(dp(44),dp(44));sizeParams.rightMargin=dp(6);sessionBar.addView(size,sizeParams);
            if(!local){TextView disconnect=toolbarButton("OFF","End connection",()->confirmDisconnect());sessionBar.addView(disconnect,new LinearLayout.LayoutParams(dp(44),dp(44)));}if(kind.equals("codex")){TextView switcher=toolbarButton(simpleMode?"TUI":"Simple","Switch Codex view",()->{simpleMode=!simpleMode;showTerminal("codex");});switcher.setTextSize(11);LinearLayout.LayoutParams switchParams=new LinearLayout.LayoutParams(dp(58),dp(44));switchParams.rightMargin=dp(6);sessionBar.addView(switcher,switchParams);}TextView options=toolbarButton("⋮","Terminal view options",()->{});options.setOnClickListener(v->{PopupMenu menu=new PopupMenu(this,options);menu.getMenu().add("Text size").setOnMenuItemClickListener(item->{chooseCodexSize();return true;});menu.getMenu().add("Hide toolbar").setOnMenuItemClickListener(item->{toolbarHidden=true;showTerminal(kind);return true;});menu.getMenu().add("Rotate to landscape").setOnMenuItemClickListener(item->{setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);return true;});menu.getMenu().add("Follow phone rotation").setOnMenuItemClickListener(item->{setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);return true;});menu.show();});sessionBar.addView(options,new LinearLayout.LayoutParams(dp(44),dp(44)));
        }
        if(kind.equals("codex") && simpleMode && simpleWeb==null)simpleWeb=createSimpleCodex();else if(kind.equals("codex") && !simpleMode && codexWeb==null)codexWeb=createRemoteTerminal(true);else if(kind.equals("laptop") && laptopWeb==null)laptopWeb=createRemoteTerminal(false);else if(local && phoneWeb==null)phoneWeb=createPhoneTerminal();
        final WebView terminal=activeTerminal();FrameLayout workspace=new FrameLayout(this);root.addView(workspace,new LinearLayout.LayoutParams(-1,0,1));workspace.addView(terminal,new FrameLayout.LayoutParams(-1,-1));
        if(toolbarHidden){TextView restore=label("×",20,TEXT,true);restore.setContentDescription("Show toolbar");restore.setGravity(Gravity.CENTER);restore.setBackground(shape(0x994e5055,3));restore.setElevation(dp(5));FrameLayout.LayoutParams button=new FrameLayout.LayoutParams(dp(34),dp(34),Gravity.END|Gravity.CENTER_VERTICAL);button.rightMargin=dp(6);workspace.addView(restore,button);restore.setOnClickListener(v->{toolbarHidden=false;showTerminal(kind);});}
        terminal.post(()->{if(kind.equals("codex") && simpleMode)terminal.evaluateJavascript("window.setToolbarVisible && window.setToolbarVisible("+(!toolbarHidden)+")",null);else terminal.evaluateJavascript("window.fitTerminal && window.fitTerminal()",null);});
    }
    private int pendingRemoteCloses; private boolean remoteCloseFailed;
    void confirmDisconnect(){
        new AlertDialog.Builder(this).setTitle("Disconnect from the session?")
            .setMessage("This closes the phone's remote terminals and may interrupt work running in them.")
            .setNegativeButton("No",null).setPositiveButton("Yes",(dialog,which)->disconnect()).show();
    }
    void disconnect(){
        if(pendingRemoteCloses>0)return;
        remoteCloseFailed=false;pendingRemoteCloses=(codexWeb!=null?1:0)+(laptopWeb!=null?1:0)+(simpleWeb!=null?1:0);
        if(pendingRemoteCloses==0){finishDisconnect();return;}
        Toast.makeText(this,"Closing remote terminal…",Toast.LENGTH_SHORT).show();
        if(codexWeb!=null)codexWeb.evaluateJavascript("window.releaseTerminal && window.releaseTerminal()",null);
        if(laptopWeb!=null)laptopWeb.evaluateJavascript("window.releaseTerminal && window.releaseTerminal()",null);
        if(simpleWeb!=null)simpleWeb.evaluateJavascript("window.releaseTerminal && window.releaseTerminal()",null);
        new Handler(getMainLooper()).postDelayed(()->{if(pendingRemoteCloses>0){pendingRemoteCloses=0;new AlertDialog.Builder(this).setMessage("Could not confirm that the remote terminal closed. Check your connection and try again.").setPositiveButton("OK",null).show();}},30000);
    }
    void remoteCloseResult(boolean ok){
        if(pendingRemoteCloses<=0)return;remoteCloseFailed|=!ok;
        if(--pendingRemoteCloses==0){if(remoteCloseFailed)new AlertDialog.Builder(this).setMessage("Could not close the remote terminal. Check your connection and try again.").setPositiveButton("OK",null).show();else finishDisconnect();}
    }
    void finishDisconnect(){
        stopService(new Intent(this,MonitorService.class));
        for(WebView t:new WebView[]{codexWeb,laptopWeb,simpleWeb})if(t!=null){if(t.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)t.getParent()).removeView(t);t.destroy();}
        codexWeb=null;laptopWeb=null;simpleWeb=null;web.stopLoading();web.loadUrl("about:blank");home();Toast.makeText(this,"Connection ended",Toast.LENGTH_SHORT).show();
    }
    void configureWeb(){
        WebSettings settings=web.getSettings();settings.setUserAgentString(desktop?DESKTOP_AGENT:mobileAgent);settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(desktop);settings.setSupportZoom(true);settings.setBuiltInZoomControls(true);settings.setDisplayZoomControls(false);
    }
    void applyViewport(){
        if(web==null)return;
        if(!desktop){web.evaluateJavascript("(function(){var m=document.querySelector('meta[name=viewport]');if(m)m.content='width=device-width, initial-scale=1, maximum-scale=5';})()",null);return;}
        int width=getPreferences(MODE_PRIVATE).getInt("desktop_width",1024);
        // Fit the selected desktop CSS width to the actual usable WebView width, accounting for Android density.
        float available=web.getWidth()/getResources().getDisplayMetrics().density;
        if(available<=0)return;
        float scale=Math.max(.25f,Math.min(1f,available/width));
        String meta=String.format(java.util.Locale.US,"width=%d, initial-scale=%.4f, minimum-scale=0.25, maximum-scale=5",width,scale);
        web.evaluateJavascript("(function(){var m=document.querySelector('meta[name=viewport]');if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}m.content='"+meta+"';})()",null);
    }
    void viewOptions(View anchor){
        PopupMenu popup=new PopupMenu(this,anchor);
        popup.getMenu().add(0,1,0,desktop?"Switch to phone view":"Switch to desktop view");
        popup.getMenu().add(0,5,1,"Desktop size");
        popup.getMenu().add(0,2,2,"Rotate to landscape");
        popup.getMenu().add(0,3,3,"Follow phone rotation");
        popup.getMenu().add(0,4,4,"Reload session");
        popup.getMenu().add(0,6,5,fullscreen?"Show system bars":"Fullscreen");
        popup.getMenu().add(0,7,6,"Hide toolbar");
        popup.setOnMenuItemClickListener(item->{
            switch(item.getItemId()){
                case 1: desktop=!desktop;getPreferences(MODE_PRIVATE).edit().putBoolean("desktop",desktop).apply();configureWeb();web.reload();showWeb();return true;
                case 2: setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);return true;
                case 3: setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);return true;
                case 4: web.reload();return true;
                case 5: chooseDesktopSize();return true;
                case 6: fullscreen=!fullscreen;toolbarHidden=fullscreen;getPreferences(MODE_PRIVATE).edit().putBoolean("fullscreen",fullscreen).apply();applyFullscreen();showWeb();return true;
                case 7: toolbarHidden=true;showWeb();return true;
                default:return false;
            }
        });popup.show();
    }
    int codexFontSize(){return Math.max(8,Math.min(24,getPreferences(MODE_PRIVATE).getInt("codex_font_size",12)));}
    void chooseCodexSize(){
        final int current=codexFontSize();LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(24),dp(8),dp(24),0);
        TextView value=label("",16,TEXT,true);content.addView(value);TextView help=label("Smaller text fits more of the terminal on screen.",14,MUTED,false);help.setPadding(0,dp(6),0,dp(8));content.addView(help);
        SeekBar slider=new SeekBar(this);slider.setMax(16);slider.setProgress(current-8);slider.setContentDescription("Terminal text size");content.addView(slider);
        TextView ends=label("Smaller                                      Larger",12,MUTED,false);content.addView(ends);value.setText("Text size  ·  "+current);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int progress,boolean fromUser){int size=8+progress;value.setText("Text size  ·  "+size);if(fromUser){getPreferences(MODE_PRIVATE).edit().putInt("codex_font_size",size).apply();for(WebView t:new WebView[]{codexWeb,laptopWeb,phoneWeb,simpleWeb})if(t!=null)t.evaluateJavascript("window.setTerminalFont && window.setTerminalFont("+size+")",null);}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        new AlertDialog.Builder(this).setTitle("Terminal text size").setView(content).setNeutralButton("Reset",(d,w)->{getPreferences(MODE_PRIVATE).edit().putInt("codex_font_size",12).apply();for(WebView t:new WebView[]{codexWeb,laptopWeb,phoneWeb,simpleWeb})if(t!=null)t.evaluateJavascript("window.setTerminalFont && window.setTerminalFont(12)",null);}).setPositiveButton("Done",null).show();
    }
    void chooseDesktopSize(){
        int saved=Math.max(750,Math.min(1800,getPreferences(MODE_PRIVATE).getInt("desktop_width",1024)));LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(24),dp(8),dp(24),0);
        TextView value=label("",16,TEXT,true);content.addView(value);TextView help=label("Move right to fit more of OpenCode on screen.",14,MUTED,false);help.setPadding(0,dp(6),0,dp(8));content.addView(help);
        SeekBar slider=new SeekBar(this);slider.setMax(105);slider.setProgress((saved-750)/10);slider.setContentDescription("OpenCode desktop size");content.addView(slider);TextView ends=label("Larger text                             More space",12,MUTED,false);content.addView(ends);value.setText("Desktop width  ·  "+saved);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int progress,boolean fromUser){int width=750+10*progress;value.setText("Desktop width  ·  "+width);if(fromUser){getPreferences(MODE_PRIVATE).edit().putInt("desktop_width",width).apply();if(!desktop){desktop=true;getPreferences(MODE_PRIVATE).edit().putBoolean("desktop",true).apply();configureWeb();web.reload();showWeb();}else applyViewport();}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        new AlertDialog.Builder(this).setTitle("OpenCode desktop size").setView(content).setNeutralButton("Reset",(d,w)->{getPreferences(MODE_PRIVATE).edit().putInt("desktop_width",1024).apply();if(!desktop){desktop=true;getPreferences(MODE_PRIVATE).edit().putBoolean("desktop",true).apply();configureWeb();web.reload();showWeb();}else applyViewport();}).setPositiveButton("Done",null).show();
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration config){super.onConfigurationChanged(config);updateSessionBar();if(terminalKind!=null && activeTerminal()!=null){WebView t=activeTerminal();t.post(()->t.evaluateJavascript("window.fitTerminal && window.fitTerminal()",null));}else if(web!=null && shownWeb)web.post(()->{if(web!=null)applyViewport();});else if(!shownWeb)home();}
    void chooseConnection(){
        new AlertDialog.Builder(this).setTitle("Connect your laptop").setItems(new String[]{"Scan QR code", "Enter link manually"},(d,which)->{if(which==0)scan();else connect();}).setNegativeButton("Cancel",null).show();
    }
    void scan(){
        new AlertDialog.Builder(this).setTitle("Scan the laptop’s QR code").setMessage("On your laptop, open OpenCode Phone Pairing from the app launcher. Then point your phone camera at the QR code.").setNegativeButton("Cancel",null).setPositiveButton("Open camera",(d,w)->startActivityForResult(new Intent(this,ScannerActivity.class),SCAN_REQUEST)).show();
    }
    boolean validPairLink(String link){
        Uri uri=Uri.parse(link);
        return "https".equalsIgnoreCase(uri.getScheme())&&uri.getHost()!=null&&uri.getUserInfo()==null&&uri.getFragment()==null&&uri.getPath()!=null&&uri.getPath().matches("/auth/connect/[A-Za-z0-9_-]+");
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=SCAN_REQUEST || result!=RESULT_OK || data==null)return;
        String raw=data.getStringExtra("qr");
        java.util.List<String> links=new java.util.ArrayList<>();
        try {
            if(raw!=null && raw.trim().startsWith("{")){
                org.json.JSONObject json=new org.json.JSONObject(raw);String code=json.getString("code");org.json.JSONArray urls=json.getJSONArray("urls");
                for(int i=0;i<urls.length();i++){Uri candidate=Uri.parse(urls.getString(i));if("https".equalsIgnoreCase(candidate.getScheme()) && candidate.getHost()!=null && candidate.getUserInfo()==null){String link="https://"+candidate.getAuthority()+"/auth/connect/"+code;if(validPairLink(link))links.add(link);}}
            } else if(raw!=null && validPairLink(raw.trim())) links.add(raw.trim());
        } catch(Exception ignored){}
        if(links.isEmpty()){Toast.makeText(this,"This is not an OpenCode remote pairing code",Toast.LENGTH_LONG).show();return;}
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Connecting laptop").setMessage("Connecting securely…").setNegativeButton("Cancel",null).setPositiveButton("Retry",null).create();
        dialog.setOnShowListener(ignored->{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{dialog.dismiss();scan();});new Thread(()->pair(links,dialog)).start();});dialog.show();
    }
    void connect(){
        EditText input=new EditText(this);input.setSingleLine();input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);input.setHint("https://…/auth/connect/…");
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Connect your laptop").setMessage("On your laptop, run opencode pair --remote. Paste the entire fresh link below.").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Connect",null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String link=input.getText().toString().trim();
            if(!validPairLink(link)) {input.setError("Paste the full HTTPS pairing link");return;}
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);dialog.setMessage("Connecting securely…");
            new Thread(()->pair(java.util.Collections.singletonList(link),dialog)).start();
        }));dialog.show();
    }
    /** A pairing code works once, so only unreachable candidates fall through to the next URL. */
    static final class PairingFailed extends Exception{final boolean fatal;PairingFailed(String message,boolean fatal){super(message);this.fatal=fatal;}}
    void pair(java.util.List<String> links,AlertDialog dialog){
        String message="Could not reach your laptop. Check that it is awake and online.";
        for(String link:links){
            try{pairOne(link,dialog);return;}
            catch(PairingFailed e){if(e.getMessage()!=null)message=e.getMessage();if(e.fatal)break;}
        }
        final String error=message;
        runOnUiThread(()->{if(isFinishing() || isDestroyed() || !dialog.isShowing())return;dialog.setMessage(error);dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);});
    }
    void pairOne(String link,AlertDialog dialog) throws PairingFailed{
        HttpURLConnection c=null,verify=null;
        try{
            Uri uri=Uri.parse(link);
            c=(HttpURLConnection)new URL(link).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(20000);c.setReadTimeout(20000);c.setRequestProperty("Accept","text/html");
            int status=c.getResponseCode();
            if(status==401)throw new PairingFailed("This link expired or was already used. Generate a new link on your laptop.",true);
            if(status!=302 && status!=303)throw new PairingFailed("The laptop returned an unexpected response ("+status+"). Generate a fresh link and try again.",false);
            String cookieHeader=null;
            for(java.util.Map.Entry<String,java.util.List<String>> e:c.getHeaderFields().entrySet())if(e.getKey()!=null && e.getKey().equalsIgnoreCase("Set-Cookie")) for(String value:e.getValue()){String first=value.split(";",2)[0];if(!first.isEmpty())cookieHeader=cookieHeader==null?first:cookieHeader+"; "+first;}
            if(cookieHeader==null)throw new PairingFailed("OpenCode did not return a session. Generate a new link and try again.",true);
            String origin="https://"+uri.getAuthority();
            verify=(HttpURLConnection)new URL(origin+"/api/info").openConnection();verify.setInstanceFollowRedirects(false);verify.setConnectTimeout(20000);verify.setReadTimeout(20000);verify.setRequestProperty("Cookie",cookieHeader);
            if(verify.getResponseCode()!=200)throw new PairingFailed("Sign-in could not be verified. Generate a new link and try again.",true);
            final java.util.List<String> cookieValues=new java.util.ArrayList<>();
            for(java.util.Map.Entry<String,java.util.List<String>> e:c.getHeaderFields().entrySet())if(e.getKey()!=null && e.getKey().equalsIgnoreCase("Set-Cookie"))cookieValues.addAll(e.getValue());
            runOnUiThread(()->{if(isFinishing() || isDestroyed() || !dialog.isShowing())return;if(!base().equals(origin)){for(WebView t:new WebView[]{codexWeb,laptopWeb,simpleWeb})if(t!=null){if(t.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)t.getParent()).removeView(t);t.destroy();}codexWeb=null;laptopWeb=null;}CookieManager cm=CookieManager.getInstance();for(String value:cookieValues)cm.setCookie(origin,value);cm.flush();getPreferences(MODE_PRIVATE).edit().putString("url",origin).apply();dialog.dismiss();web.loadUrl(origin);showWeb();startMonitoring();});
        }catch(IOException|RuntimeException e){throw new PairingFailed("Could not reach your laptop. Check that it is awake and online.",false);}
        finally{if(c!=null)c.disconnect();if(verify!=null)verify.disconnect();}
    }
    void startMonitoring(){String cookies=CookieManager.getInstance().getCookie(base());if(cookies==null||cookies.isEmpty())return;try{Intent i=new Intent(this,MonitorService.class).putExtra("url",base()).putExtra("cookie",cookies);startForegroundService(i);}catch(Exception ignored){}}
    @Override protected void onDestroy(){if(localShell!=null)localShell.shutdown();for(WebView t:new WebView[]{laptopWeb,phoneWeb,simpleWeb})if(t!=null){if(t.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)t.getParent()).removeView(t);t.destroy();}if(codexWeb!=null){if(codexWeb.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)codexWeb.getParent()).removeView(codexWeb);codexWeb.destroy();codexWeb=null;}if(web!=null){if(web.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)web.getParent()).removeView(web);web.destroy();web=null;}super.onDestroy();}
    @Override public void onBackPressed(){if(terminalKind!=null)home();else if(shownWeb&&web.canGoBack())web.goBack();else if(shownWeb)home();else super.onBackPressed();}
}
