package app.remotessh;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import java.io.OutputStream;
import java.util.function.BooleanSupplier;

/** Prefer the current remote file; use an exact private copy only if that file is gone. */
final class ImageSourceReader {
  private ImageSourceReader(){}
  static void read(ImageDialog.Reader remote,ImageDiskCache cache,String scope,String source,
      OutputStream out,long limit,BooleanSupplier active) throws Exception {
    final long[] written={0};
    OutputStream tracked=new OutputStream(){
      @Override public void write(int value) throws java.io.IOException {out.write(value);written[0]++;}
      @Override public void write(byte[] data,int start,int count) throws java.io.IOException {
        out.write(data,start,count);written[0]+=count;
      }
    };
    try{remote.read(source,tracked,active);}
    catch(Exception failure){
      if(written[0]!=0 || !missing(failure) || !cache.read(scope,source,out,limit,active))throw failure;
    }
  }
  private static boolean missing(Throwable failure){
    for(Throwable cause=failure;cause!=null;cause=cause.getCause())
      if(cause instanceof SftpException && ((SftpException)cause).id==ChannelSftp.SSH_FX_NO_SUCH_FILE)return true;
    return false;
  }
}
