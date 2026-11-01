package app.remotessh;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

/** Subtle inset edge shared by the compact and expanded composer. */
final class ComposerSurface {
  private ComposerSurface() {}

  static Drawable create(Context context,int panel){
    float density=context.getResources().getDisplayMetrics().density;
    float radius=26*density;
    boolean dark=Color.red(panel)+Color.green(panel)+Color.blue(panel)<384;
    GradientDrawable edge=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
        new int[]{mix(panel,Color.BLACK,dark?.10f:.07f),mix(panel,Color.WHITE,dark?.045f:.05f)});
    edge.setCornerRadius(radius);
    GradientDrawable inner=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
        new int[]{mix(panel,Color.BLACK,.025f),panel,panel});
    int inset=Math.max(1,Math.round(.75f*density));
    inner.setCornerRadius(Math.max(0,radius-inset));
    LayerDrawable surface=new LayerDrawable(new Drawable[]{edge,inner});
    surface.setLayerInset(1,inset,inset,inset,inset);
    surface.setPadding(0,0,0,0);
    return surface;
  }

  private static int mix(int from,int to,float weight){
    return Color.rgb(Math.round(Color.red(from)*(1-weight)+Color.red(to)*weight),
        Math.round(Color.green(from)*(1-weight)+Color.green(to)*weight),
        Math.round(Color.blue(from)*(1-weight)+Color.blue(to)*weight));
  }
}
