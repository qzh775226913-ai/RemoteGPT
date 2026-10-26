package app.remotessh;

import android.app.*;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Owns a conversation image snapshot, page loads and borrowed preview bitmaps. */
final class ImageDialog {
  interface Reader {void read(String source,OutputStream out,BooleanSupplier active) throws Exception;}
  final AlertDialog dialog;
  final ZoomImageView image;
  private final ThreadPoolExecutor worker=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<>());
  private final Map<String,ImageDocument> recent=new LinkedHashMap<>(4,.75f,true);
  private static final int CACHE_ENTRIES=3;
  private static final long CACHE_BITMAP_BYTES=16L*1024*1024,CACHE_FILE_BYTES=40L*1024*1024;
  private final Activity activity;
  private final Reader reader;
  private final BooleanSupplier valid;
  private final Function<String,Bitmap> previews;
  private final List<String> sources;
  private String initialSource;
  private Bitmap initialPreview;
  private final TextView status;
  private ImageDocument document;
  private Future<?> pending,preload;
  private final Handler main=new Handler(Looper.getMainLooper());
  private final ArrayDeque<String> nearby=new ArrayDeque<>();
  private String preloading,preferredSource;
  private volatile long preloadGeneration;
  private int direction=1;
  private final Runnable preloadNext=this::advancePrefetch;
  private volatile boolean closed;
  private volatile long generation;
  private int index;
  private boolean failed;

  ImageDialog(Activity activity,boolean dark,String source,Bitmap preview,Reader reader,BooleanSupplier valid,Consumer<String> save) {
    this(activity,dark,source,preview,Collections.emptyList(),path->null,reader,valid,save);
  }
  ImageDialog(Activity activity,boolean dark,String source,Bitmap preview,List<String> gallery,Function<String,Bitmap> previews,
      Reader reader,BooleanSupplier valid,Consumer<String> save) {
    this.activity=activity;this.reader=reader;this.valid=valid;this.previews=previews;
    initialSource=source;initialPreview=preview;
    List<String> snapshot=new ArrayList<>(new LinkedHashSet<>(gallery));snapshot.removeAll(Collections.singleton(null));
    if(source==null)snapshot.clear();
    else if(!snapshot.contains(source)){snapshot.clear();snapshot.add(source);}
    sources=snapshot;index=source==null?0:sources.indexOf(source);
    RemoteScreens ui=new RemoteScreens(activity,dark);LinearLayout root=ui.column();
    image=new ZoomImageView(activity,worker,this::page);
    int height=Math.min(ui.dp(420),Math.max(ui.dp(100),activity.getResources().getDisplayMetrics().heightPixels-ui.dp(210)));
    root.addView(image,new LinearLayout.LayoutParams(-1,height));
    status=ui.text("双指缩放 · 双击放大／复位",13);root.addView(status);
    AlertDialog.Builder builder=new AlertDialog.Builder(activity).setView(root).setNegativeButton("关闭",null);
    if(source!=null)builder.setPositiveButton("保存图片",(d,w)->{
      if(active())save.accept(currentSource());else Toast.makeText(activity,"连接或对话已变化，请重新打开图片后保存",Toast.LENGTH_SHORT).show();
    });
    dialog=builder.create();dialog.setOnDismissListener(d->dispose());dialog.show();
    status.setOnClickListener(v->{if(failed)load();});
    if(source==null){if(preview!=null && !preview.isRecycled())image.setPlaceholder(preview);return;}
    load();
  }
  String currentSource(){return sources.isEmpty()?null:sources.get(index);}
  boolean isCurrent(){return active();}
  private boolean active(){return !closed && !activity.isDestroyed() && valid.getAsBoolean();}
  private void message(String value){status.setText((sources.size()>1?(index+1)+" / "+sources.size()+" · ":"")+value);}
  void page(int delta) {
    if(!active() || delta==0 || sources.size()<2)return;
    int next=index+(delta>0?1:-1);if(next<0 || next>=sources.size())return;
    direction=delta>0?1:-1;index=next;load();
  }
  private void cancelLoad() {
    if(pending!=null){pending.cancel(true);if(pending instanceof Runnable)worker.remove((Runnable)pending);pending=null;}
  }
  private void cancelPrefetch() {
    preloadGeneration++;main.removeCallbacks(preloadNext);nearby.clear();
    if(preload!=null){preload.cancel(true);if(preload instanceof Runnable)worker.remove((Runnable)preload);}
    preload=null;preloading=null;
  }
  private void load() {
    if(!active() || currentSource()==null)return;
    String source=currentSource();boolean adopting=preload!=null && source.equals(preloading);
    long ticket=++generation;main.removeCallbacks(preloadNext);nearby.clear();
    if(adopting && pending==preload)pending=null;
    cancelLoad();if(!adopting)cancelPrefetch();failed=false;
    image.clear();document=null;
    ImageDocument cached=recent.get(source);
    if(cached!=null){show(cached);prepareAdjacent();return;}
    Bitmap preview=previews.apply(source);
    if(preview==null && source.equals(initialSource))preview=initialPreview;
    if(preview!=null && !preview.isRecycled())image.setPlaceholder(preview);
    message("正在加载大图…");
    if(adopting){pending=preload;return;}
    BooleanSupplier active=()->ticket==generation && active();
    pending=read(source,active,value->{recent.put(source,value);show(value);trimRecent();prepareAdjacent();},this::loadFailed);
  }
  private Future<?> read(String source,BooleanSupplier active,Consumer<ImageDocument> success,Runnable failure) {
    return worker.submit(()->{
      File temporary=null;ImageDocument ready=null;
      try {
        MediaFiles.check(active);temporary=File.createTempFile("viewer-",".image",activity.getCacheDir());
        try(OutputStream out=new FileOutputStream(temporary)){reader.read(source,out,active);}
        MediaFiles.check(active);ready=new ImageDocument(temporary);MediaFiles.check(active);
        ImageDocument value=ready;ready=null;temporary=null;
        activity.runOnUiThread(()->{
          if(!active.getAsBoolean()){release(value);return;}
          success.accept(value);
        });
      }catch(Exception | OutOfMemoryError error){activity.runOnUiThread(()->{if(active.getAsBoolean())failure.run();});}
      finally {if(ready!=null)ready.close();if(temporary!=null)temporary.delete();}
    });
  }
  private void loadFailed(){failed=true;message("图片加载失败 · 点按重试");}
  private void prepareAdjacent() {
    nearby.clear();preferredSource=null;
    for(int delta:new int[]{direction,-direction}) {
      int next=index+delta;if(next<0 || next>=sources.size())continue;
      String source=sources.get(next);if(preferredSource==null)preferredSource=source;
      if(!recent.containsKey(source))nearby.add(source);
    }
    main.removeCallbacks(preloadNext);
    if(!nearby.isEmpty())main.postDelayed(preloadNext,150);
  }
  private boolean fitsPrefetch(String source,ImageDocument value) {
    if(document==null)return false;
    int count=1;long bitmaps=document.overview.getAllocationByteCount(),files=document.fileBytes;
    ImageDocument preferred=source.equals(preferredSource)?null:recent.get(preferredSource);
    if(preferred!=null && preferred!=document){count++;bitmaps+=preferred.overview.getAllocationByteCount();files+=preferred.fileBytes;}
    if(value!=null){count++;bitmaps+=value.overview.getAllocationByteCount();files+=value.fileBytes;}
    return value==null?count<CACHE_ENTRIES && bitmaps<CACHE_BITMAP_BYTES && files<CACHE_FILE_BYTES
        :count<=CACHE_ENTRIES && bitmaps<=CACHE_BITMAP_BYTES && files<=CACHE_FILE_BYTES;
  }
  private void advancePrefetch() {
    if(!active() || document==null || preload!=null)return;
    while(!nearby.isEmpty()) {
      String source=nearby.peek();
      if(recent.containsKey(source) || !fitsPrefetch(source,null)){nearby.remove();continue;}
      // Keep one lane free for foreground reads and zoom details; no queued warm-up flood.
      if(worker.getActiveCount()!=0 || !worker.getQueue().isEmpty()) {main.postDelayed(preloadNext,80);return;}
      nearby.remove();preloading=source;long ticket=++preloadGeneration;
      BooleanSupplier active=()->ticket==preloadGeneration && active();
      preload=read(source,active,value->{
        clearPreload();
        if(source.equals(currentSource()) && document==null){recent.put(source,value);show(value);trimRecent();prepareAdjacent();}
        else {if(fitsPrefetch(source,value)){recent.put(source,value);trimRecent();}else release(value);advancePrefetch();}
      },()->{
        clearPreload();
        if(source.equals(currentSource()) && document==null)loadFailed();else advancePrefetch();
      });
      return;
    }
  }
  private void clearPreload(){if(pending==preload)pending=null;preload=null;preloading=null;}
  private void show(ImageDocument value) {
    document=value;image.setDocument(value);
    message("双指缩放 · 双击放大／复位"+(sources.size()>1?" · 左右滑动翻图":""));
  }
  private boolean overBudget() {
    long bitmaps=0,files=0;
    for(ImageDocument value:recent.values()){bitmaps+=value.overview.getAllocationByteCount();files+=value.fileBytes;}
    return recent.size()>CACHE_ENTRIES || bitmaps>CACHE_BITMAP_BYTES || files>CACHE_FILE_BYTES;
  }
  private void trimRecent() {
    Iterator<ImageDocument> iterator=recent.values().iterator();
    while(iterator.hasNext() && overBudget()) {
      ImageDocument value=iterator.next();if(value==document)continue;
      iterator.remove();release(value);
    }
  }
  private void release(ImageDocument value){if(value==null)return;try{worker.execute(value::close);}catch(RejectedExecutionException ignored){value.close();}}
  void dismiss(){dialog.dismiss();}
  private void dispose(){
    if(closed)return;closed=true;generation++;cancelLoad();cancelPrefetch();image.dispose();
    for(ImageDocument value:recent.values())release(value);recent.clear();document=null;
    sources.clear();initialSource=null;initialPreview=null;status.setOnClickListener(null);worker.shutdown();
  }
}
