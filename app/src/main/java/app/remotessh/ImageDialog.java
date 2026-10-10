package app.remotessh;

import android.app.*;
import android.graphics.Bitmap;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Zoom support within the existing single-image preview dialog. */
final class ImageDialog {
  interface Reader {void read(String source,OutputStream out,BooleanSupplier active) throws Exception;}
  final AlertDialog dialog;
  final ZoomImageView image;
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private ImageDocument document;
  private volatile boolean closed;
  private final TextView status;

  ImageDialog(Activity activity,boolean dark,String source,Bitmap preview,Reader reader,BooleanSupplier valid,Consumer<String> save) {
    RemoteScreens ui=new RemoteScreens(activity,dark);LinearLayout root=ui.column();
    image=new ZoomImageView(activity,worker,delta->{});
    int height=Math.min(ui.dp(420),Math.max(ui.dp(100),activity.getResources().getDisplayMetrics().heightPixels-ui.dp(210)));
    root.addView(image,new LinearLayout.LayoutParams(-1,height));
    status=ui.text(source==null?"双指缩放 · 双击放大／复位":"正在加载大图…",13);root.addView(status);
    AlertDialog.Builder builder=new AlertDialog.Builder(activity).setView(root).setNegativeButton("关闭",null);
    if(source!=null)builder.setPositiveButton("保存图片",(d,w)->{
      if(valid.getAsBoolean())save.accept(source);else Toast.makeText(activity,"连接已变化，请重新打开图片后保存",Toast.LENGTH_SHORT).show();
    });
    dialog=builder.create();dialog.setOnDismissListener(d->dispose());dialog.show();
    if(preview!=null && !preview.isRecycled())image.setPlaceholder(preview);
    if(source==null)return;
    BooleanSupplier active=()->!closed && valid.getAsBoolean() && !activity.isDestroyed();
    status.setOnClickListener(v->{if(status.getText().toString().contains("失败"))load(activity,source,reader,active);});
    load(activity,source,reader,active);
  }
  private void load(Activity activity,String source,Reader reader,BooleanSupplier active) {
    if(!active.getAsBoolean())return;status.setText("正在加载大图…");
    worker.execute(()->{
      File temporary=null;ImageDocument ready=null;
      try {
        MediaFiles.check(active);temporary=File.createTempFile("viewer-",".image",activity.getCacheDir());
        try(OutputStream out=new FileOutputStream(temporary)){reader.read(source,out,active);}
        MediaFiles.check(active);ready=new ImageDocument(temporary);MediaFiles.check(active);
        ImageDocument value=ready;ready=null;temporary=null;
        activity.runOnUiThread(()->{
          if(!active.getAsBoolean()){release(value);return;}
          document=value;image.setDocument(value);status.setText("双指缩放 · 双击放大／复位");
        });
      }catch(Exception | OutOfMemoryError error){activity.runOnUiThread(()->{if(active.getAsBoolean())status.setText("图片加载失败 · 点按重试");});}
      finally {if(ready!=null)ready.close();if(temporary!=null)temporary.delete();}
    });
  }
  private void release(ImageDocument value){if(value==null)return;try{worker.execute(value::close);}catch(RejectedExecutionException ignored){value.close();}}
  void dismiss(){dialog.dismiss();}
  private void dispose(){if(closed)return;closed=true;image.dispose();release(document);document=null;worker.shutdown();}
}
