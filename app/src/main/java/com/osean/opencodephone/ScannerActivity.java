package com.osean.opencodephone;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import java.util.*;

@SuppressWarnings("deprecation")
public class ScannerActivity extends Activity implements SurfaceHolder.Callback {
    private Camera camera; private SurfaceView surface; private volatile boolean finished; private boolean surfaceReady; private int generation; private final java.util.concurrent.ExecutorService decoder=java.util.concurrent.Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);surface=new SurfaceView(this);root.addView(surface,new FrameLayout.LayoutParams(-1,-1));surface.getHolder().addCallback(this);
        TextView instruction=new TextView(this);instruction.setText("Point at the OpenCode QR code on your laptop");instruction.setTextColor(Color.WHITE);instruction.setTextSize(17);instruction.setGravity(Gravity.CENTER);instruction.setBackgroundColor(0xaa000000);instruction.setPadding(24,30,24,30);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);root.addView(instruction,p);root.setOnApplyWindowInsetsListener((v,insets)->{int bottom;if(android.os.Build.VERSION.SDK_INT>=30)bottom=insets.getInsets(android.view.WindowInsets.Type.systemBars()|android.view.WindowInsets.Type.displayCutout()).bottom;else bottom=insets.getSystemWindowInsetBottom();p.bottomMargin=bottom;instruction.setLayoutParams(p);return insets;});setContentView(root);
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},1);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==1){if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)openCamera();else{Toast.makeText(this,"Camera permission is needed to scan",Toast.LENGTH_LONG).show();finish();}}}
    @Override public void surfaceCreated(SurfaceHolder holder){surfaceReady=true;openCamera();}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){}
    @Override public void surfaceDestroyed(SurfaceHolder holder){surfaceReady=false;release();}
    private void openCamera(){if(!surfaceReady||camera!=null||checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return;
        try{camera=Camera.open();Camera.Parameters p=camera.getParameters();List<String> modes=p.getSupportedFocusModes();if(modes!=null&&modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);camera.setParameters(p);camera.setDisplayOrientation(90);camera.setPreviewDisplay(surface.getHolder());camera.startPreview();next();}
        catch(Exception e){release();Toast.makeText(this,"Could not open the camera",Toast.LENGTH_LONG).show();finish();}}
    private void next(){if(camera==null||finished)return;final int current=generation;try{camera.setOneShotPreviewCallback((data,cam)->{if(data==null||camera!=cam||finished)return;try{Camera.Size size=cam.getParameters().getPreviewSize();decoder.execute(()->decode(data,size.width,size.height,current));}catch(RuntimeException ignored){}});}catch(RuntimeException ignored){}}
    private void decode(byte[] data,int width,int height,int current){String result=read(data,width,height);if(result==null){byte[] rotated=new byte[width*height];for(int y=0;y<height;y++)for(int x=0;x<width;x++)rotated[x*height+height-y-1]=data[y*width+x];result=read(rotated,height,width);}final String value=result;runOnUiThread(()->{if(finished || current!=generation || camera==null)return;if(value!=null){finished=true;setResult(RESULT_OK,new Intent().putExtra("qr",value));finish();}else next();});}
    private String read(byte[] data,int width,int height){try{PlanarYUVLuminanceSource source=new PlanarYUVLuminanceSource(data,width,height,0,0,width,height,false);Map<DecodeHintType,Object> hints=new EnumMap<>(DecodeHintType.class);hints.put(DecodeHintType.POSSIBLE_FORMATS,Collections.singletonList(BarcodeFormat.QR_CODE));hints.put(DecodeHintType.TRY_HARDER,Boolean.TRUE);return new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(source)),hints).getText();}catch(Exception ignored){return null;}}
    private void release(){generation++;Camera old=camera;camera=null;if(old!=null){try{old.setPreviewCallback(null);old.stopPreview();}catch(RuntimeException ignored){}finally{old.release();}}}
    @Override protected void onPause(){super.onPause();release();}
    @Override protected void onResume(){super.onResume();openCamera();}
    @Override protected void onDestroy(){finished=true;decoder.shutdownNow();super.onDestroy();}
}
