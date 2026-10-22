package app.remotessh;

import android.content.res.AssetManager;
import android.graphics.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;
import ru.noties.jlatexmath.awt.Font;

/** Original font outlines for U+00AD, which Android otherwise treats as an invisible soft hyphen. */
final class MathGlyphs {
  private static AssetManager assets;
  private static Map<Typeface,Glyph> glyphs;
  static synchronized void init(AssetManager value) {
    if(assets!=value){assets=value;glyphs=null;}
  }
  private static final class Glyph {
    final Path path;final float units,advance;
    Glyph(JSONObject data) throws JSONException {
      units=(float)data.getDouble("units");advance=(float)data.getDouble("advance");path=new Path();
      JSONArray commands=data.getJSONArray("path");
      for(int i=0;i<commands.length();i++) {
        JSONArray c=commands.getJSONArray(i);
        switch(c.getString(0)) {
          case "M":path.moveTo(n(c,1),n(c,2));break;
          case "L":path.lineTo(n(c,1),n(c,2));break;
          case "Q":path.quadTo(n(c,1),n(c,2),n(c,3),n(c,4));break;
          case "C":path.cubicTo(n(c,1),n(c,2),n(c,3),n(c,4),n(c,5),n(c,6));break;
          case "Z":path.close();break;
          default:throw new JSONException("Unknown outline command");
        }
      }
    }
    private static float n(JSONArray data,int i) throws JSONException{return (float)data.getDouble(i);}
  }
  // Called on the formula worker, before any drawable is published to the main thread.
  static synchronized void prepare() {
    if(glyphs!=null)return;
    if(assets==null)throw new IllegalStateException("Math assets were not initialized");
    Map<Typeface,Glyph> result=new HashMap<>();
    try(InputStream in=assets.open("math_glyphs.json")) {
      ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
      while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
      JSONArray entries=new JSONObject(out.toString(StandardCharsets.UTF_8.name())).getJSONArray("glyphs");
      for(int i=0;i<entries.length();i++) {
        JSONObject entry=entries.getJSONObject(i);Glyph glyph=new Glyph(entry);
        Typeface face=Typeface.createFromAsset(assets,entry.getString("font"));
        for(int style=0;style<4;style++)result.put(Typeface.create(face,style),glyph);
      }
    } catch(IOException | JSONException error){throw new IllegalStateException("Cannot load original math outlines",error);}
    glyphs=Collections.unmodifiableMap(result);
  }
  static boolean draw(Canvas canvas,Paint paint,Font font,char[] chars,int offset,int length,int x,int y) {
    if(glyphs==null || font==null)return false;
    Glyph glyph=glyphs.get(font.typeface());if(glyph==null)return false;
    boolean found=false;for(int i=offset;i<offset+length;i++)found|=chars[i]=='\u00ad';
    if(!found)return false;
    float at=x;
    for(int i=offset;i<offset+length;i++) {
      if(chars[i]=='\u00ad') {
        int save=canvas.save();canvas.translate(at,y);float scale=font.size()/glyph.units;
        canvas.scale(scale,-scale);canvas.drawPath(glyph.path,paint);canvas.restoreToCount(save);
        at+=font.size()*glyph.advance/glyph.units;
      } else {canvas.drawText(chars,i,1,at,y,paint);at+=paint.measureText(chars,i,1);}
    }
    return true;
  }
}
