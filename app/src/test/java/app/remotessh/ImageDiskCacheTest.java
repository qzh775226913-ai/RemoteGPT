package app.remotessh;

import static org.junit.Assert.*;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.Test;

public class ImageDiskCacheTest {
  private static final String PATH="/work/screenshot.png";
  private static final byte[] ORIGINAL={1,2,3,4,5,6};
  private static ImageDialog.Reader missing(){return (source,out,active)->{throw new SftpException(ChannelSftp.SSH_FX_NO_SUCH_FILE,"missing");};}
  private static byte[] read(ImageDiskCache cache,String scope,ImageDialog.Reader remote) throws Exception {
    ByteArrayOutputStream out=new ByteArrayOutputStream();
    ImageSourceReader.read(remote,cache,scope,PATH,out,1024,()->true);return out.toByteArray();
  }

  @Test public void missingRemoteFileUsesOnlyTheExactThreadAndHostCopy() throws Exception {
    File folder=Files.createTempDirectory("image-cache-test").toFile();ImageDiskCache cache=new ImageDiskCache(folder,100);
    Profile profile=new Profile();profile.host="host-a";profile.user="tester";
    String scope=ImageDiskCache.scope(profile,"thread-a","item-a");cache.put(scope,PATH,ORIGINAL,()->true);
    assertArrayEquals(ORIGINAL,read(cache,scope,missing()));
    for(String other:new String[]{ImageDiskCache.scope(profile,"thread-b","item-a"),ImageDiskCache.scope(profile,"thread-a","item-b"),""}){
      try{read(cache,other,missing());fail("another conversation must not see this copy");}
      catch(SftpException expected){assertEquals(ChannelSftp.SSH_FX_NO_SUCH_FILE,expected.id);}
    }
    profile.host="host-b";
    try{read(cache,ImageDiskCache.scope(profile,"thread-a","item-a"),missing());fail("another host must not see this copy");}
    catch(SftpException expected){assertEquals(ChannelSftp.SSH_FX_NO_SUCH_FILE,expected.id);}
  }

  @Test public void currentRemoteBytesWinAndUnrelatedErrorsNeverUseCache() throws Exception {
    ImageDiskCache cache=new ImageDiskCache(Files.createTempDirectory("image-cache-test").toFile(),100);
    cache.put("scope",PATH,ORIGINAL,()->true);byte[] updated={9,8,7};
    assertArrayEquals(updated,read(cache,"scope",(source,out,active)->out.write(updated)));
    try{read(cache,"scope",(source,out,active)->{throw new IOException("permission denied");});fail();}
    catch(IOException expected){assertEquals("permission denied",expected.getMessage());}
    ByteArrayOutputStream partial=new ByteArrayOutputStream();
    try{ImageSourceReader.read((source,out,active)->{out.write(12);missing().read(source,out,active);},cache,"scope",PATH,partial,100,()->true);fail();}
    catch(SftpException expected){assertArrayEquals(new byte[]{12},partial.toByteArray());}
  }

  @Test public void embeddedImageIsUsedForAMissingPathWithoutChangingItsIdentity() throws Exception {
    ImageDiskCache cache=new ImageDiskCache(Files.createTempDirectory("image-cache-test").toFile(),100);
    cache.put("scope",PATH,new byte[]{9,9},()->true);ByteArrayOutputStream out=new ByteArrayOutputStream();
    ImageDialog.Reader remote=(source,dest,active)->{
      if(source.equals(PATH))throw new SftpException(ChannelSftp.SSH_FX_NO_SUCH_FILE,"missing");
      assertEquals("data:image/png;base64,source",source);dest.write(ORIGINAL);
    };
    ImageSourceReader.read(remote,cache,"scope",PATH,"data:image/png;base64,source",out,100,()->true);
    assertArrayEquals(ORIGINAL,out.toByteArray());
  }

  @Test public void cacheIsBoundedAndCancelledWritesDoNotAppear() throws Exception {
    ImageDiskCache cache=new ImageDiskCache(Files.createTempDirectory("image-cache-test").toFile(),10);
    cache.put("scope","/old",ORIGINAL,()->true);Thread.sleep(5);
    cache.put("scope","/new",ORIGINAL,()->true);
    ByteArrayOutputStream out=new ByteArrayOutputStream();
    assertFalse(cache.read("scope","/old",out,100,()->true));assertTrue(cache.read("scope","/new",out,100,()->true));
    cache.put("scope","/cancelled",ORIGINAL,()->false);
    assertFalse(cache.read("scope","/cancelled",new ByteArrayOutputStream(),100,()->true));
    assertFalse(cache.read("scope","/new",new ByteArrayOutputStream(),2,()->true));
  }
}
