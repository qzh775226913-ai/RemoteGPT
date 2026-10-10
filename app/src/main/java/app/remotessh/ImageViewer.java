package app.remotessh;

import android.app.*;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Full-screen viewer shared by conversation images and draft attachments. */
final class ImageViewer {
  interface Reader {void read(String source,OutputStream out,java.util.function.BooleanSupplier active) throws Exception;}
  final Dialog dialog;
  final ZoomImageView image;
  private final Activity activity;
  private final Reader reader;
  private final java.util.function.BooleanSupplier valid;
  private final java.util.function.Function<String,Bitmap> previews;
  private final List<String> sources;
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private final TextView counter,status,save;
  private final View previous,next;
  private ImageDocument document;
  private volatile long revision;
  private volatile boolean closed;
  private int index;

  ImageViewer(Activity activity,List<String> sources,int index,Reader reader,java.util.function.BooleanSupplier valid,
      java.util.function.Function<String,Bitmap> previews,java.util.function.Consumer<String> saveImage) {
    this.activity=activity;this.sources=new ArrayList<>(sources);this.index=index;this.reader=reader;this.valid=valid;this.previews=previews;
    RemoteScreens ui=new RemoteScreens(activity,true);LinearLayout root=ui.column();root.setContentDescription("图片查看器");
    dialog=ui.window(root);LinearLayout toolbar=ui.row();toolbar.setPadding(ui.dp(8),0,ui.dp(8),0);
    toolbar.addView(ui.icon("close","关闭图片",this::dismiss),new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
    counter=ui.text("",14);counter.setGravity(Gravity.CENTER);toolbar.addView(counter,new LinearLayout.LayoutParams(0,ui.dp(48),1));
    TextView reset=ui.button("全图",false,()->imageReset());reset.setContentDescription("恢复全图");toolbar.addView(reset);
    save=ui.button("保存",false,()->{
      if(valid.getAsBoolean())saveImage.accept(this.sources.get(this.index));
      else Toast.makeText(activity,"连接已变化，请重新打开图片后保存",Toast.LENGTH_SHORT).show();
    });save.setContentDescription("保存图片");LinearLayout.LayoutParams saveLayout=new LinearLayout.LayoutParams(-2,-2);saveLayout.setMarginStart(ui.dp(3));toolbar.addView(save,saveLayout);
    root.addView(toolbar);image=new ZoomImageView(activity,worker,this::step);root.addView(image,new LinearLayout.LayoutParams(-1,0,1));
    LinearLayout footer=ui.row();footer.setPadding(ui.dp(8),0,ui.dp(8),0);
    previous=ui.icon("back","上一张图片",()->step(-1));footer.addView(previous,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
    status=ui.text("",13);status.setGravity(Gravity.CENTER);status.setMinHeight(ui.dp(48));status.setContentDescription("图片加载状态");
    status.setOnClickListener(v->load());footer.addView(status,new LinearLayout.LayoutParams(0,-2,1));
    next=ui.icon("chevron","下一张图片",()->step(1));footer.addView(next,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));root.addView(footer);
    dialog.setOnDismissListener(d->dispose());ui.show(dialog);dialog.getWindow().setStatusBarColor(Color.BLACK);dialog.getWindow().setNavigationBarColor(Color.BLACK);
    dialog.getWindow().getDecorView().setSystemUiVisibility(0);load();
  }
  private void imageReset(){image.reset();}
  void dismiss(){dialog.dismiss();}
  private void release(ImageDocument value){if(value==null)return;try{worker.execute(value::close);}catch(RejectedExecutionException ignored){value.close();}}
  void step(int delta){int target=index+delta;if(target<0 || target>=sources.size() || closed)return;if(!valid.getAsBoolean()){status.setText("连接已变化，请重新打开图片");return;}index=target;load();}
  private void load() {
    if(closed || !valid.getAsBoolean()){if(!closed)status.setText("连接已变化，请重新打开图片");return;}
    long token=++revision;image.clear();release(document);document=null;
    String source=sources.get(index);Bitmap preview=previews.apply(source);if(preview!=null && !preview.isRecycled())image.setPlaceholder(preview);
    counter.setText((index+1)+" / "+sources.size());counter.setContentDescription("第 "+(index+1)+" 张，共 "+sources.size()+" 张图片");
    previous.setVisibility(sources.size()>1?View.VISIBLE:View.INVISIBLE);next.setVisibility(sources.size()>1?View.VISIBLE:View.INVISIBLE);
    previous.setEnabled(index>0);next.setEnabled(index<sources.size()-1);previous.setAlpha(index>0?1:.35f);next.setAlpha(index<sources.size()-1?1:.35f);
    save.setEnabled(true);status.setText("正在加载大图…");
    java.util.function.BooleanSupplier active=()->!closed && token==revision && valid.getAsBoolean() && !activity.isDestroyed();
    worker.execute(()->{
      File temporary=null;ImageDocument ready=null;
      try {
        MediaFiles.check(active);temporary=File.createTempFile("viewer-",".image",activity.getCacheDir());
        try(OutputStream out=new FileOutputStream(temporary)){reader.read(source,out,active);}MediaFiles.check(active);
        ready=new ImageDocument(temporary);MediaFiles.check(active);ImageDocument value=ready;ready=null;temporary=null;
        activity.runOnUiThread(()->{
          if(!active.getAsBoolean()){release(value);return;}
          document=value;image.setDocument(value);status.setText("双指缩放 · 双击放大／复位");
        });
      }catch(Exception | OutOfMemoryError error){activity.runOnUiThread(()->{if(active.getAsBoolean())status.setText("图片加载失败 · 点按重试");});}
      finally{if(ready!=null)ready.close();if(temporary!=null)temporary.delete();}
    });
  }
  private void dispose(){if(closed)return;closed=true;revision++;image.dispose();release(document);document=null;worker.shutdown();}
}
