package app.remotessh;

import java.util.*;

/** GFM pipe tables in a prose block. Canonical source is retained separately from cell display. */
final class TableMarkup {
  static final class Table {
    final String raw,key;
    final List<List<String>> rows;
    final List<Integer> align;
    Table(String raw,String key,List<List<String>> rows,List<Integer> align) {
      this.raw=raw;this.key=key;this.rows=rows;this.align=align;
    }
    int columns(){return align.size();}
  }
  static final class Part {
    final String raw;final Table table;
    Part(String raw,Table table){this.raw=raw;this.table=table;}
  }
  static List<Part> parts(String source) {
    List<Part> out=new ArrayList<>();
    if(source.length()>131072){out.add(new Part(source,null));return out;}
    String[] lines=source.split("\n",-1);int[] offsets=new int[lines.length+1];
    for(int i=0;i<lines.length;i++)offsets[i+1]=Math.min(source.length(),offsets[i]+lines[i].length()+1);
    int last=0,tables=0,cells=0;
    for(int i=0;i+1<lines.length && tables<32;i++) {
      List<String> head=row(lines[i]),separator=row(lines[i+1]);
      if(head==null || separator==null || head.size()!=separator.size() || head.size()>32)continue;
      List<Integer> align=new ArrayList<>();
      for(String cell:separator) {
        if(!cell.matches(":?-+:?"))break;
        align.add(cell.startsWith(":")?(cell.endsWith(":")?1:0):(cell.endsWith(":")?2:0));
      }
      if(align.size()!=head.size())continue;
      List<List<String>> rows=new ArrayList<>();rows.add(head);int end=i+2;
      while(end<lines.length) {
        List<String> next=row(lines[end]);if(next==null)break;
        if(rows.size()<=128) {
          List<String> normalized=new ArrayList<>(next.subList(0,Math.min(next.size(),head.size())));
          while(normalized.size()<head.size())normalized.add("");
          rows.add(normalized);
        }
        end++;
      }
      if(rows.size()>128 || cells+rows.size()*head.size()>1024){i=end-1;continue;}
      int from=offsets[i],to=offsets[end];
      if(from>last)out.add(new Part(source.substring(last,from),null));
      String raw=source.substring(from,to),key=lines[i]+"\n"+lines[i+1];
      out.add(new Part(raw,new Table(raw,key,rows,align)));
      cells+=rows.size()*head.size();tables++;last=to;i=end-1;
    }
    if(last<source.length())out.add(new Part(source.substring(last),null));
    return out;
  }
  // A pipe must be present: this deliberately does not interpret space-aligned prose as a table.
  static List<String> row(String line) {
    if(line.startsWith("    ") || line.startsWith("\t"))return null;
    line=line.trim();if(line.isEmpty())return null;
    List<String> cells=new ArrayList<>();StringBuilder cell=new StringBuilder();boolean pipe=false;
    for(int i=0;i<line.length();i++) {
      char c=line.charAt(i);
      if(c=='|' && !escaped(line,i)){cells.add(cell.toString().trim());cell.setLength(0);pipe=true;}
      else if(c=='\\' && i+1<line.length() && line.charAt(i+1)=='|' && !escaped(line,i)){cell.append('|');i++;}
      else cell.append(c);
    }
    cells.add(cell.toString().trim());if(!pipe)return null;
    if(line.startsWith("|"))cells.remove(0);
    if(line.endsWith("|") && !escaped(line,line.length()-1))cells.remove(cells.size()-1);
    return cells.isEmpty()?null:cells;
  }
  private static boolean escaped(String source,int at) {
    int count=0;while(at>0 && source.charAt(--at)=='\\')count++;return (count&1)!=0;
  }
}
