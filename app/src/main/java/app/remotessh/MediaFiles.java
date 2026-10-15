package app.remotessh;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import android.graphics.*;

/** Bounded binary handling; remote paths are never opened as Android local files. */
final class MediaFiles {
  static final long IMAGE_LIMIT = 20L * 1024 * 1024;
  static final int MAX_ATTACHMENTS = 4;
  static final long DOWNLOAD_LIMIT = 100L * 1024 * 1024;
  static final Pattern IMAGE = Pattern.compile("!\\[([^\\]]*)\\]\\(([^\\s)]+)\\)");

  /** Metadata for Android's document creator; the payload is still saved unchanged. */
  static android.content.Intent saveIntent(String source) {
    String name, mime = "";
    android.webkit.MimeTypeMap types = android.webkit.MimeTypeMap.getSingleton();
    if (source.startsWith("data:image/")) {
      int end = source.indexOf(';');
      if (end < 0) end = source.indexOf(',');
      if (end > 5) mime = source.substring(5, end).toLowerCase(Locale.ROOT);
      String extension = types.getExtensionFromMimeType(mime);
      name = "image" + (extension == null ? "" : "." + extension);
    } else {
      name = android.net.Uri.parse(source).getLastPathSegment();
      if (name == null || name.isEmpty()) name = "download";
      int dot = name.lastIndexOf('.');
      if (dot >= 0) {
        String known = types.getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase(Locale.ROOT));
        if (known != null) mime = known;
      }
    }
    return new android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT)
        .setType(mime.isEmpty() ? "application/octet-stream" : mime)
        .addCategory(android.content.Intent.CATEGORY_OPENABLE)
        .putExtra(android.content.Intent.EXTRA_TITLE, name);
  }

  static void copy(InputStream in, OutputStream out, long limit) throws IOException {
    copy(in,out,limit,()->true);
  }

  static void check(java.util.function.BooleanSupplier active) throws IOException {
    if(!active.getAsBoolean())throw new java.io.InterruptedIOException("图片加载已取消");
  }

  static void copy(InputStream in, OutputStream out, long limit, java.util.function.BooleanSupplier active) throws IOException {
    byte[] buffer = new byte[16384];
    long total = 0;
    for (;;) {
      check(active);
      int n=in.read(buffer);
      check(active);
      if(n==-1)return;
      total += n;
      if (total > limit) throw new IOException("文件超过大小限制");
      out.write(buffer, 0, n);
    }
  }

  static String remotePath(String source, String cwd) {
    String path = source;
    if (path.startsWith("sandbox:")) path = path.substring(8);
    else if (path.startsWith("file:")) {
      URI uri = URI.create(path);
      if (uri.getAuthority() != null && !uri.getAuthority().isEmpty()) throw new IllegalArgumentException("不支持带主机名的文件链接");
      path = uri.getPath();
    } else if (path.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) throw new IllegalArgumentException("不支持此链接类型");
    else path = android.net.Uri.decode(path);
    if (path == null || path.isEmpty() || path.indexOf('\0') >= 0) throw new IllegalArgumentException("无效的文件路径");
    if (!path.startsWith("/")) {
      if (cwd.isEmpty()) throw new IllegalArgumentException("相对链接缺少项目路径");
      path = cwd + "/" + path;
    }
    return path;
  }

  static Bitmap decode(byte[] bytes) throws IOException {
    return decode(bytes, 1600);
  }

  static Bitmap decode(byte[] bytes, int maxDimension) throws IOException {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("无法解码图片");
    options.inSampleSize = 1;
    while (options.outWidth / options.inSampleSize > maxDimension || options.outHeight / options.inSampleSize > maxDimension) options.inSampleSize *= 2;
    options.inJustDecodeBounds = false;
    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    if (bitmap == null) throw new IOException("无法解码图片");
    return bitmap;
  }

  static final class Image {
    final byte[] bytes;
    final String extension;
    final Bitmap preview;
    Image(byte[] bytes, String extension, Bitmap preview) {
      this.bytes = bytes; this.extension = extension; this.preview = preview;
    }
  }

  static Image prepareImage(byte[] bytes) throws IOException {
    BitmapFactory.Options info = new BitmapFactory.Options(); info.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, info);
    if (info.outWidth <= 0 || info.outHeight <= 0) throw new IOException("此手机无法读取这张图片，请换一张或导出为 JPEG / PNG");
    String mime = info.outMimeType == null ? "" : info.outMimeType;
    Bitmap preview = decode(bytes, 256);
    if (Arrays.asList("image/jpeg", "image/png", "image/webp", "image/gif").contains(mime))
      return new Image(bytes, mime.equals("image/jpeg") ? ".jpg" : "." + mime.substring(6), preview);
    // Native albums may return HEIF/AVIF. Decode locally and send a portable JPEG.
    Bitmap image = decode(bytes, 2560);
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      if (!image.compress(Bitmap.CompressFormat.JPEG, 92, out)) throw new IOException("无法转换图片");
      if (out.size() > IMAGE_LIMIT) throw new IOException("转换后的图片超过大小限制");
      return new Image(out.toByteArray(), ".jpg", preview);
    } finally { image.recycle(); }
  }

  static List<String> images(ChatItem item) {
    List<String> result = new ArrayList<>(item.images);
    Matcher matcher = IMAGE.matcher(item.text);
    while (matcher.find() && result.size() < 12) if (!result.contains(matcher.group(2))) result.add(matcher.group(2));
    return result;
  }
}
