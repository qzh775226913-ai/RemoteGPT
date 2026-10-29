package app.remotessh;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.io.ByteArrayOutputStream;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Visible thumbnail lifetime, reconnect invalidation and bounded read retries. */
final class MessageImageTile {
  private static final int MAX_ATTEMPTS = 3;
  final String itemId, source;
  final LinearLayout row, tile;
  final ImageView preview;
  final TextView label;
  private final Activity activity;
  private final RemoteApp app;
  private final ThumbnailLoader worker;
  private final ImageDialog.Reader reader;
  private final BooleanSupplier owner;
  private final String host, thread, cacheHost;
  private volatile int generation;
  volatile boolean visible;
  boolean loading, loaded;
  private volatile long loadVersion;
  private long retryAt;
  private int attempts;
  private ThumbnailLoader.Job job;
  private final Runnable retry = this::load;

  MessageImageTile(Activity activity, RemoteApp app, DialogUi ui, ThumbnailLoader worker,
      String itemId, String source, LinearLayout row, boolean multiple,
      BooleanSupplier owner, Consumer<String> openImage) {
    this(activity,app,ui,worker,itemId,source,row,multiple,owner,openImage,
        (path,out,active)->app.readMedia(path,out,MediaFiles.IMAGE_LIMIT,active));
  }

  MessageImageTile(Activity activity, RemoteApp app, DialogUi ui, ThumbnailLoader worker,
      String itemId, String source, LinearLayout row, boolean multiple,
      BooleanSupplier owner, Consumer<String> openImage, ImageDialog.Reader reader) {
    this.activity=activity;this.app=app;this.worker=worker;this.owner=owner;
    this.reader=reader;
    this.itemId=itemId;this.source=source;this.row=row;
    host=app.profile==null?"":app.profile.id;thread=app.threadId;generation=app.connectionGeneration();
    cacheHost=contextKey();
    tile=ui.column();
    LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(multiple?0:-1,-2,multiple?1:0);
    tp.setMargins(0,ui.dp(6),ui.dp(multiple?6:0),ui.dp(6));tile.setLayoutParams(tp);
    FrameLayout area=new FrameLayout(activity);tile.addView(area,new LinearLayout.LayoutParams(-1,ui.dp(multiple?160:240)));
    preview=new ImageView(activity);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
    preview.setContentDescription(UiText.get(R.string.ui_message_image_tap_to_view_full_size));area.addView(preview,new FrameLayout.LayoutParams(-1,-1));
    label=ui.text(UiText.get(R.string.ui_image_scroll_here_to_load),12,ui.muted);label.setMinHeight(ui.dp(48));label.setSingleLine(true);
    label.setEllipsize(TextUtils.TruncateAt.END);label.setGravity(Gravity.CENTER);
    area.addView(label,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
    preview.setOnClickListener(v->openImage.accept(source));
    label.setOnClickListener(v->{app.main.removeCallbacks(retry);retryAt=0;attempts=0;load();});
    PressFeedback.apply(preview,ui.bg==android.graphics.Color.BLACK);PressFeedback.apply(label,ui.bg==android.graphics.Color.BLACK);
  }

  private String contextKey() {
    return app.profile==null?"":app.profile.id+":"+app.profile.host+":"+app.profile.port+":"+app.profile.user+":"+app.threadId+":"+itemId;
  }
  boolean sameContext() {
    return !activity.isDestroyed() && app.profile!=null && host.equals(app.profile.id)
        && thread.equals(app.threadId) && cacheHost.equals(contextKey()) && owner.getAsBoolean() && tile.getParent()!=null;
  }
  boolean valid() {return sameContext() && generation==app.connectionGeneration();}
  Bitmap cachedPreview() {
    if(!sameContext())return null;
    try{return app.thumbnails.images.get(ThumbnailCache.key(cacheHost,source));}
    catch(Exception ignored){return null;}
  }

  void refreshConnection() {
    if(generation!=app.connectionGeneration() && sameContext()) {
      release();generation=app.connectionGeneration();
    }
  }

  void load() {
    if(loading || loaded || !visible || !valid() || attempts>=MAX_ATTEMPTS || SystemClock.uptimeMillis()<retryAt)return;
    if(!app.connected && !source.startsWith("data:") && !source.startsWith("https://"))return;
    app.main.removeCallbacks(retry);retryAt=0;loading=true;attempts++;label.setText(UiText.get(R.string.ui_loading_image));label.setTooltipText(null);
    final long version=++loadVersion;final int connection=generation;
    job=worker.submit(running->{
      BooleanSupplier active=()->running.getAsBoolean() && visible && version==loadVersion && connection==app.connectionGeneration();
      try {
        MediaFiles.check(active);
        String key=ThumbnailCache.key(cacheHost,source);Bitmap bitmap=app.thumbnails.images.get(key);
        if(bitmap==null) {
          ByteArrayOutputStream bytes=new ByteArrayOutputStream();reader.read(source,bytes,active);
          MediaFiles.check(active);bitmap=MediaFiles.decode(bytes.toByteArray(),640);
          if(!active.getAsBoolean()){bitmap.recycle();MediaFiles.check(active);}
          app.thumbnails.images.put(key,bitmap);
        }
        final Bitmap image=bitmap;
        app.main.post(()->{
          if(version!=loadVersion)return;loading=false;job=null;
          if(!valid() || !visible)return;
          loaded=true;preview.setImageBitmap(image);label.setVisibility(View.GONE);
        });
      } catch(Exception error) {
        app.main.post(()->{
          if(version!=loadVersion)return;loading=false;job=null;
          if(!valid() || !visible)return;
          label.setText(UiText.get(R.string.ui_image_failed_to_load_tap_to_retry));label.setTooltipText(ConnectionFailure.reason(error));
          // Viewport refreshes must not bypass backoff or create an unbounded retry loop.
          if(attempts<MAX_ATTEMPTS) {
            long delay=attempts==1?500:1500;retryAt=SystemClock.uptimeMillis()+delay;
            app.main.postDelayed(retry,delay);
          }
        });
      }
    });
  }

  void release() {
    visible=false;loadVersion++;app.main.removeCallbacks(retry);retryAt=0;attempts=0;
    if(job!=null){job.discard();job=null;}loading=false;
    if(loaded){preview.setImageDrawable(null);loaded=false;}
    label.setText(UiText.get(R.string.ui_image_scroll_here_to_load));label.setTooltipText(null);label.setVisibility(View.VISIBLE);
  }
}
