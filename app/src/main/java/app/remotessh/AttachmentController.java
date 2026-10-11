package app.remotessh;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Uploads draft attachments and reads bounded media without owning connection lifetime.
 * Each asynchronous result checks the originating connection before touching UI state.
 */
final class AttachmentController {
  private final RemoteApp app;

  AttachmentController(RemoteApp app) { this.app = app; }

  public void attachImage(android.net.Uri uri) {
    attachImages(Collections.singletonList(uri));
  }

  public void attachImages(List<android.net.Uri> selected) {
    List<android.net.Uri> uris = new ArrayList<>(new LinkedHashSet<>(selected));
    uris.removeAll(Collections.singleton(null));
    if (uris.isEmpty()) return;
    if (!canAttach()) {
      app.error = attachmentUnavailable(); app.changed(); return;
    }
    int remaining = app.remainingImages();
    if (uris.size() > remaining) {
      app.error = "每条消息最多 " + MediaFiles.MAX_ATTACHMENTS + " 张图片，目前还能添加 " + Math.max(0, remaining) + " 张，请重新选择";
      app.changed(); return;
    }
    if (app.currentTransport() == null) { app.error = "请先连接 SSH 主机"; app.changed(); return; }
    app.attaching = true; app.attachingFiles=false;
    app.pendingImagePreview = null;
    app.imageUploadIndex = 1; app.imageUploadCount = uris.size();
    final int token = app.connectionGeneration();
    final SshTransport current = app.currentTransport();
    app.changed();
    app.executeBackground(() -> {
      List<String> failures = new ArrayList<>();
      for (int i = 0; i < uris.size(); i++) {
        final int index = i + 1;
        // Synchronize cancellation through the main looper before each file.
        CompletableFuture<Boolean> active = new CompletableFuture<>();
        app.main.post(() -> active.complete(token == app.connectionGeneration() && app.connected && app.attaching && app.currentTransport() == current));
        try { if (!active.get()) break; }
        catch (Exception canceled) { break; }
        java.io.File file = null;
        try {
          app.main.post(() -> { if (token == app.connectionGeneration()) { app.imageUploadIndex = index; app.pendingImagePreview = null; app.changed(); } });
          java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
          try (java.io.InputStream in = app.getContentResolver().openInputStream(uris.get(i))) {
            if (in == null) throw new java.io.IOException("无法读取图片");
            MediaFiles.copy(in, bytes, MediaFiles.IMAGE_LIMIT);
          }
          MediaFiles.Image image = MediaFiles.prepareImage(bytes.toByteArray());
          app.main.post(() -> { if (token == app.connectionGeneration()) { app.pendingImagePreview = image.preview; app.changed(); } });
          file = java.io.File.createTempFile("attachment-", image.extension, app.getCacheDir());
          try (java.io.OutputStream out = new java.io.FileOutputStream(file)) { out.write(image.bytes); }
          String path = current.uploadImage(file);
          app.main.post(() -> {
            if (token == app.connectionGeneration()) {
              app.draftImages.add(path); app.draftImagePreviews.put(path, image.preview);
              app.pendingImagePreview = null; app.changed();
            }
          });
        } catch (Exception e) {
          failures.add("第 " + index + " 张：" + ConnectionFailure.reason(e));
        } finally { if (file != null) file.delete(); }
      }
      app.main.post(() -> {
        if (token != app.connectionGeneration()) return;
        app.pendingImagePreview = null; app.attaching = false; app.imageUploadIndex = app.imageUploadCount = 0;
        if (!failures.isEmpty()) app.error = (failures.size() == uris.size() ? "图片上传失败" : "部分图片上传失败，成功的图片已保留")
            + "\n" + android.text.TextUtils.join("\n", failures);
        app.changed();app.drainQueuedMessage();
      });
    });
  }

  boolean canAttach() {
    return app.connected && !app.attaching && !app.steering && !app.stopping && !app.isRestartingRemote()
        && !app.recoveryInProgress() && (!app.busy || !app.turnId.isEmpty());
  }
  String attachmentUnavailable() {
    if(!app.connected)return "请先连接服务器";
    if(app.attaching)return "附件正在上传，请稍候";
    return "正在接续或提交消息，请稍候再添加附件";
  }
  public void attachFiles(List<android.net.Uri> selected) {
    List<android.net.Uri> uris=new ArrayList<>(new LinkedHashSet<>(selected));uris.removeAll(Collections.singleton(null));
    if(uris.isEmpty())return;
    if(!canAttach()){app.error=attachmentUnavailable();app.changed();return;}
    if(uris.size()>app.remainingFiles()) {
      app.error="每条消息最多 "+FileAttachment.MAX_FILES+" 个文件，目前还能添加 "+app.remainingFiles()+" 个，请重新选择";
      app.changed();return;
    }
    if(app.currentTransport()==null){app.error="请先连接 SSH 主机";app.changed();return;}
    app.attaching=true;app.attachingFiles=true;app.pendingImagePreview=null;app.imageUploadIndex=1;app.imageUploadCount=uris.size();
    final int token=app.connectionGeneration();final SshTransport current=app.currentTransport();app.changed();
    app.executeBackground(()->{
      List<String> failures=new ArrayList<>();
      for(int i=0;i<uris.size();i++) {
        final int index=i+1;
        CompletableFuture<Boolean> active=new CompletableFuture<>();
        app.main.post(()->active.complete(token==app.connectionGeneration() && app.connected && app.attaching && app.currentTransport()==current));
        try {if(!active.get())break;}catch(Exception canceled){break;}
        java.io.File file=null;
        try {
          app.main.post(()->{if(token==app.connectionGeneration()){app.imageUploadIndex=index;app.changed();}});
          String name=FileAttachment.displayName(app.getContentResolver(),uris.get(i));
          file=java.io.File.createTempFile("file-attachment-",".tmp",app.getCacheDir());
          // Stream directly to disk; documents are never decoded as images or buffered entirely in memory.
          try(java.io.InputStream in=app.getContentResolver().openInputStream(uris.get(i));
              java.io.OutputStream out=new java.io.FileOutputStream(file)) {
            if(in==null)throw new java.io.IOException("无法读取文件");
            MediaFiles.copy(in,out,FileAttachment.LIMIT,()->token==app.connectionGeneration() && app.currentTransport()==current);
          }
          long size=file.length();String path=current.uploadFile(file,name);
          FileAttachment attachment=new FileAttachment(name,path,size);
          app.main.post(()->{if(token==app.connectionGeneration()){app.draftFiles.add(attachment);app.changed();}});
        }catch(Exception failure){failures.add("第 "+index+" 个文件："+ConnectionFailure.reason(failure));}
        finally{if(file!=null)file.delete();}
      }
      app.main.post(()->{
        if(token!=app.connectionGeneration())return;
        app.attaching=false;app.attachingFiles=false;app.imageUploadIndex=app.imageUploadCount=0;
        if(!failures.isEmpty())app.error=(failures.size()==uris.size()?"文件上传失败":"部分文件上传失败，成功的文件已保留")
            +"\n"+android.text.TextUtils.join("\n",failures);
        app.changed();app.drainQueuedMessage();
      });
    });
  }

  public void readMedia(String source, java.io.OutputStream out, long limit) throws Exception {
    readMedia(source,out,limit,()->true);
  }

  public void readMedia(String source, java.io.OutputStream out, long limit, java.util.function.BooleanSupplier active) throws Exception {
    MediaFiles.check(active);
    SshTransport current = app.currentTransport();
    String cwd = app.profile == null ? "" : app.profile.cwd;
    if (source.startsWith("data:image/")) {
      int comma = source.indexOf(',');
      if (comma < 0 || !source.substring(0, comma).endsWith(";base64") || source.length() > limit * 2) throw new java.io.IOException("无效或过大的图片");
      byte[] bytes = android.util.Base64.decode(source.substring(comma + 1), android.util.Base64.DEFAULT);
      if (bytes.length > limit) throw new java.io.IOException("图片过大");
      MediaFiles.check(active);
      out.write(bytes);
    } else if (source.startsWith("https://")) {
      java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(source).openConnection();
      c.setConnectTimeout(15000); c.setReadTimeout(20000); c.setInstanceFollowRedirects(false);
      try {
        if (c.getResponseCode() != 200) throw new java.io.IOException("图片服务器返回 HTTP " + c.getResponseCode());
        try (java.io.InputStream in = c.getInputStream()) { MediaFiles.copy(in, out, limit,active); }
      } finally { c.disconnect(); }
    } else {
      if (!app.connected || current == null) throw new java.io.IOException("请先连接图片所在 SSH 主机");
      current.download(MediaFiles.remotePath(source, cwd), out, limit,active);
    }
  }

}
