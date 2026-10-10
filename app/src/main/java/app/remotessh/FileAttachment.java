package app.remotessh;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.util.List;

/** Ordinary files are uploaded unchanged and referenced by remote path in the message. */
final class FileAttachment {
  static final int MAX_FILES=8;
  static final long LIMIT=100L*1024*1024;
  final String name,path;
  final long size;
  FileAttachment(String name,String path,long size){this.name=name;this.path=path;this.size=size;}
  static String safeName(String original) {
    String name=original==null?"":original.replaceAll("[\\p{Cntrl}/\\\\]","_").trim();
    if(name.isEmpty() || name.equals(".") || name.equals(".."))name="file";
    // Leave room for the unique prefix even on filesystems with a 255-byte name limit.
    while(name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>180)
      name=name.substring(0,name.offsetByCodePoints(name.length(),-1));
    return name;
  }
  static String displayName(ContentResolver resolver,Uri uri) {
    try(Cursor cursor=resolver.query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
      if(cursor!=null && cursor.moveToFirst()) {
        int index=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        if(index>=0 && !cursor.isNull(index))return safeName(cursor.getString(index));
      }
    } catch(RuntimeException ignored) { }
    return safeName(uri.getLastPathSegment());
  }
  static String message(String text,List<FileAttachment> files) {
    if(files.isEmpty())return text;
    StringBuilder result=new StringBuilder(text);
    if(!text.isEmpty())result.append("\n\n");
    result.append("已上传文件（可从远端路径读取）：");
    for(FileAttachment file:files)result.append("\n").append(file.name).append("\n").append(file.path);
    return result.toString();
  }
}
