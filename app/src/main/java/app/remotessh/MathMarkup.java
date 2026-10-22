package app.remotessh;

import java.util.ArrayList;
import java.util.List;

/** Recognizes explicit math delimiters only; leaves currency, code and incomplete streams alone. */
final class MathMarkup {
  enum Kind { TEXT, INLINE, DISPLAY }
  static final class Part {
    final Kind kind;
    final String raw, tex;
    Part(Kind kind, String raw, String tex) { this.kind=kind;this.raw=raw;this.tex=tex; }
  }

  static List<Part> parts(String source) {
    List<Part> out=new ArrayList<>();
    int last=0, count=0;
    if(source.length()<=131072) for(int i=0;i<source.length() && count<512;) {
      char c=source.charAt(i);
      if(c=='`' && !escaped(source,i)) {
        int n=1;while(i+n<source.length() && source.charAt(i+n)=='`')n++;
        String ticks=source.substring(i,i+n);int end=source.indexOf(ticks,i+n);
        i=end<0?source.length():end+n;continue;
      }
      if(source.startsWith("](",i)) {
        int end=source.indexOf(')',i+2);if(end>=0){i=end+1;continue;}
      }
      String open=null, close=null;Kind kind=Kind.INLINE;
      if(!escaped(source,i)) {
        if(source.startsWith("\\[",i)){open="\\[";close="\\]";kind=Kind.DISPLAY;}
        else if(source.startsWith("\\(",i)){open="\\(";close="\\)";}
        else if(source.startsWith("$$",i)){open=close="$$";kind=Kind.DISPLAY;}
        else if(c=='$' && i+1<source.length() && !Character.isWhitespace(source.charAt(i+1)))open=close="$";
      }
      if(open==null){i++;continue;}
      int end=i+open.length();
      while((end=source.indexOf(close,end))>=0) {
        boolean valid=!escaped(source,end);
        if(open.equals("$"))valid &= end>i+1 && !Character.isWhitespace(source.charAt(end-1))
            && (end+1==source.length() || (!Character.isDigit(source.charAt(end+1)) && source.charAt(end+1)!='$'))
            && source.substring(i+1,end).indexOf('\n')<0;
        if(valid)break;
        if(open.equals("$")){end=-1;break;}
        end+=close.length();
      }
      if(end<0){if(!open.equals("$"))break;i++;continue;}
      String tex=source.substring(i+open.length(),end);
      if(tex.trim().isEmpty()){i=end+close.length();continue;}
      if(i>last)out.add(new Part(Kind.TEXT,source.substring(last,i),""));
      int next=end+close.length();
      out.add(new Part(kind,source.substring(i,next),tex));count++;
      last=i=next;
    }
    if(last<source.length())out.add(new Part(Kind.TEXT,source.substring(last),""));
    return out;
  }

  static boolean containsMath(String source) {
    for(Part part:parts(source))if(part.kind!=Kind.TEXT)return true;
    return false;
  }
  private static boolean escaped(String source,int at) {
    int n=0;while(at>0 && source.charAt(--at)=='\\')n++;
    return (n&1)!=0;
  }
}
