package app.remotessh;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.BooleanSupplier;

/** Bounded private copies of successfully viewed remote images, scoped by host and thread. */
final class ImageDiskCache {
  private static final long BUDGET=96L*1024*1024;
  private final File directory;
  private final long budget;
  ImageDiskCache(File directory){this(directory,BUDGET);}
  ImageDiskCache(File directory,long budget){this.directory=directory;this.budget=budget;}

  static String scope(Profile profile,String thread,String item){
    if(profile==null || thread==null || thread.isEmpty() || item==null || item.isEmpty())return "";
    try{return ThumbnailCache.key(profile.id,profile.host+"\u0000"+profile.port+"\u0000"+profile.user+"\u0000"+thread+"\u0000"+item);}
    catch(Exception ignored){return "";}
  }
  private File file(String scope,String source){
    if(scope.isEmpty() || source==null || !source.startsWith("/"))return null;
    try{return new File(directory,ThumbnailCache.key(scope,source)+".image");}
    catch(Exception ignored){return null;}
  }
  synchronized boolean read(String scope,String source,OutputStream out,long limit,BooleanSupplier active) throws IOException {
    File file=file(scope,source);if(file==null || !file.isFile() || file.length()>limit)return false;
    MediaFiles.check(active);
    try(FileInputStream in=new FileInputStream(file)){MediaFiles.copy(in,out,limit,active);}
    file.setLastModified(System.currentTimeMillis());return true;
  }
  synchronized void put(String scope,String source,byte[] bytes,BooleanSupplier active){
    File target=file(scope,source);
    if(target==null || bytes.length==0 || bytes.length>budget || !active.getAsBoolean())return;
    if(target.isFile()){target.setLastModified(System.currentTimeMillis());return;}
    File temp=null;
    try{
      if(!directory.isDirectory() && !directory.mkdirs())return;
      temp=File.createTempFile("image-", ".tmp",directory);
      try(FileOutputStream out=new FileOutputStream(temp)){out.write(bytes);}
      if(!active.getAsBoolean() || !temp.renameTo(target))return;
      target.setLastModified(System.currentTimeMillis());prune();
    }catch(IOException ignored){}finally{if(temp!=null)temp.delete();}
  }
  private void prune(){
    File[] entries=directory.listFiles((dir,name)->name.endsWith(".image"));if(entries==null)return;
    Arrays.sort(entries,Comparator.comparingLong(File::lastModified).thenComparing(File::getName));
    long total=0;for(File file:entries)total+=file.length();
    for(File file:entries){if(total<=budget)break;long size=file.length();if(file.delete())total-=size;}
  }
}
