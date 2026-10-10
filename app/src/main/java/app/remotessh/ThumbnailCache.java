package app.remotessh;

import android.graphics.Bitmap;
import android.util.LruCache;
import java.nio.charset.StandardCharsets;

/** Bounded thumbnails shared across activity recreation; originals are loaded on demand. */
final class ThumbnailCache {
  final LruCache<String,Bitmap> images=new LruCache<String,Bitmap>(8*1024*1024) {
    @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getAllocationByteCount();}
    // A view may still display an evicted bitmap, so never recycle it here.
  };
  static String key(String host,String source) throws Exception {
    java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
    digest.update(host.getBytes(StandardCharsets.UTF_8));digest.update((byte)0);digest.update(source.getBytes(StandardCharsets.UTF_8));
    return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest());
  }
}
