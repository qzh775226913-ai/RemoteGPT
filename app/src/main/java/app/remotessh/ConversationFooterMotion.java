package app.remotessh;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** Avoid remeasuring the entire timeline on every composer frame while reading history. */
final class ConversationFooterMotion implements View.OnLayoutChangeListener {
  private final LinearLayout footer;
  private final ConversationScrollView scroll;
  private final View jump;
  private final Runnable reposition;
  private boolean animating;
  private int reservedHeight;
  private final Runnable settle;

  ConversationFooterMotion(LinearLayout footer,ConversationScrollView scroll,View jump,Runnable reposition){
    this.footer=footer;this.scroll=scroll;this.jump=jump;this.reposition=reposition;
    settle=()->{if(!animating)apply(footer.getHeight());};
    footer.addOnLayoutChangeListener(this);
  }

  void begin(int heightChange){
    footer.removeCallbacks(settle);animating=true;
    reservedHeight=Math.max(scroll.getPaddingBottom()-dp(8),footer.getHeight()+Math.max(0,heightChange));
    reserve(reservedHeight);updateTranslation(footer.getHeight());
  }
  void end(){animating=false;footer.removeCallbacks(settle);footer.post(settle);}
  void dispose(){footer.removeCallbacks(settle);footer.removeOnLayoutChangeListener(this);clearTranslation();}

  @Override public void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int oright,int ob){
    int height=b-t;if(height==ob-ot)return;
    if(animating){
      FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)jump.getLayoutParams();
      jump.setTranslationY(params.bottomMargin-height-dp(12));
      updateTranslation(height);
    } else apply(height);
  }

  void scrollChanged(){if(animating)updateTranslation(footer.getHeight());}

  private void reserve(int height){
    int padding=height+dp(8);
    if(scroll.getPaddingBottom()!=padding){
      scroll.setPadding(scroll.getPaddingLeft(),scroll.getPaddingTop(),scroll.getPaddingRight(),padding);
      scroll.pin();
    }
  }

  private void updateTranslation(int height){
    if(scroll.getChildCount()==0)return;
    View timeline=scroll.getChildAt(0);
    float previous=timeline.getTranslationY();
    float next=scroll.isFollowing()?Math.max(0,reservedHeight-height):0;
    if(previous!=next)timeline.setTranslationY(next);
    if(previous!=0 && next==0 && !scroll.isFollowing())scroll.preserveVisualOffset(-Math.round(previous));
  }

  private void clearTranslation(){if(scroll.getChildCount()>0)scroll.getChildAt(0).setTranslationY(0);}

  private void apply(int height){
    reserve(height);clearTranslation();
    FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)jump.getLayoutParams();
    int margin=height+dp(12);
    if(params.bottomMargin!=margin){params.bottomMargin=margin;jump.setLayoutParams(params);}
    jump.setTranslationY(0);reposition.run();
  }

  private int dp(float value){return Math.round(value*footer.getResources().getDisplayMetrics().density);}
}
