package app.remotessh;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Scroll-direction chrome motion; presentation state never changes the thread draft. */
final class ConversationChromeController {
  private final Activity activity;
  private final LinearLayout header, composer;
  private final EditText input;
  private final TextView prompt;
  private final IconView enlarge;
  private final FrameLayout inputLayer, composerHost;
  private final Runnable resizeInput,endMotion;
  private final java.util.function.IntConsumer beginMotion;
  private boolean compact, large;
  private int accumulated, direction;
  private ValueAnimator headerAnimator, composerAnimator;
  private final int regularWidth;
  private int composerTargetHeight;

  ConversationChromeController(Activity activity, LinearLayout header, LinearLayout composer,
      EditText input, int foreground, int muted,
      int panel, Runnable resizeInput,java.util.function.IntConsumer beginMotion,Runnable endMotion) {
    this.activity=activity;this.header=header;this.composer=composer;this.input=input;
    this.resizeInput=resizeInput;this.beginMotion=beginMotion;this.endMotion=endMotion;
    LinearLayout parent=(LinearLayout)composer.getParent();
    int index=parent.indexOfChild(composer);
    LinearLayout.LayoutParams hostParams=(LinearLayout.LayoutParams)composer.getLayoutParams();
    regularWidth=hostParams.width;
    parent.removeView(composer);
    composerHost=new FrameLayout(activity);
    composerHost.setBackground(ComposerSurface.create(activity,panel));
    composerHost.setClipToOutline(true);
    parent.addView(composerHost,index,hostParams);
    composer.setBackgroundColor(Color.TRANSPARENT);
    composerHost.addView(composer,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL));
    prompt=new TextView(activity);
    prompt.setText("");prompt.setTextColor(muted);
    prompt.setTextSize(14);prompt.setGravity(Gravity.CENTER_VERTICAL);
    prompt.setPadding(dp(18),0,dp(18),0);
    prompt.setVisibility(View.GONE);
    prompt.setContentDescription(UiText.get(R.string.ui_expand_composer));
    prompt.setOnClickListener(v->{setCompact(false);input.requestFocus();});
    composerHost.addView(prompt,new FrameLayout.LayoutParams(-1,-1));
    int inputIndex=composer.indexOfChild(input);
    composer.removeView(input);
    inputLayer=new FrameLayout(activity){
      @Override protected void onMeasure(int widthSpec,int heightSpec){
        super.onMeasure(widthSpec,heightSpec);
        setMeasuredDimension(getMeasuredWidth(),input.getMeasuredHeight());
      }
    };
    inputLayer.addView(input,new FrameLayout.LayoutParams(-1,-2));
    View glass=new View(activity);
    GradientDrawable haze=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
        new int[]{Color.TRANSPARENT,Color.argb(185,Color.red(panel),Color.green(panel),Color.blue(panel)),
            Color.argb(225,Color.red(panel),Color.green(panel),Color.blue(panel))});
    haze.setCornerRadius(dp(18));glass.setBackground(haze);
    FrameLayout.LayoutParams glassParams=new FrameLayout.LayoutParams(dp(43),dp(36),Gravity.TOP|Gravity.END);
    glassParams.topMargin=dp(2);glassParams.rightMargin=dp(2);
    inputLayer.addView(glass,glassParams);
    if(android.os.Build.VERSION.SDK_INT>=31)glass.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(
        dp(2),dp(2),android.graphics.Shader.TileMode.CLAMP));
    enlarge=new IconView(activity,"expand",foreground);
    enlarge.setContentDescription(UiText.get(R.string.ui_expand_composer));
    enlarge.setOnClickListener(v->{large=!large;enlarge.icon=large?"collapse":"expand";
      enlarge.setContentDescription(UiText.get(large?R.string.ui_collapse_composer:R.string.ui_expand_composer));
      enlarge.invalidate();resizeInput.run();});
    FrameLayout.LayoutParams expandParams=new FrameLayout.LayoutParams(dp(40),dp(36),Gravity.TOP|Gravity.END);
    expandParams.topMargin=dp(2);expandParams.rightMargin=dp(2);
    inputLayer.addView(enlarge,expandParams);
    composer.addView(inputLayer,inputIndex,new LinearLayout.LayoutParams(-1,-2));
    composer.setPadding(dp(4),dp(3),dp(4),dp(4));
  }

  boolean large() {return large;}
  void restore() {large=false;enlarge.icon="expand";
    enlarge.setContentDescription(UiText.get(R.string.ui_expand_composer));enlarge.invalidate();resizeInput.run();
    if(compact)setCompact(false);}

  void dispose(){
    if(headerAnimator!=null){ValueAnimator old=headerAnimator;headerAnimator=null;old.cancel();}
    if(composerAnimator!=null){ValueAnimator old=composerAnimator;composerAnimator=null;old.cancel();}
  }

  void onScroll(int y,int oldY,boolean userScroll,boolean atLatest) {
    if(!userScroll || editingWithKeyboard())return;
    int delta=y-oldY;
    if(delta==0)return;
    if(atLatest && delta>0){accumulated=0;direction=0;setCompact(false);return;}
    int sign=delta<0?1:-1;
    if(sign!=direction){direction=sign;accumulated=0;}
    accumulated+=Math.min(Math.abs(delta),dp(60));
    if(accumulated>=dp(sign>0?32:40)) {setCompact(sign>0);accumulated=0;}
  }

  void setCompact(boolean value) {
    if(compact==value)return;
    compact=value;
    animateHeader(value);
    animateComposer(value);
  }

  void contentChanged() {
    if(!compact && composerAnimator!=null && composerAnimator.isRunning() &&
        measureContent(normalWidth())!=composerTargetHeight)animateComposer(false);
  }

  private void animateHeader(boolean hide) {
    if(headerAnimator!=null){ValueAnimator old=headerAnimator;headerAnimator=null;old.cancel();}
    int from=header.getVisibility()==View.GONE?0:currentHeight(header);
    float fromAlpha=header.getVisibility()==View.GONE?0:header.getAlpha();
    if(!hide){header.setVisibility(View.VISIBLE);header.getLayoutParams().height=ViewGroup.LayoutParams.WRAP_CONTENT;
      header.measure(View.MeasureSpec.makeMeasureSpec(header.getWidth(),View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));}
    int to=hide?0:header.getMeasuredHeight();
    header.getLayoutParams().height=from;header.setAlpha(fromAlpha);header.requestLayout();
    headerAnimator=ValueAnimator.ofInt(from,to);headerAnimator.setDuration(260);
    headerAnimator.setInterpolator(new android.view.animation.PathInterpolator(.22f,0f,.2f,1f));
    headerAnimator.addUpdateListener(a->{header.getLayoutParams().height=(int)a.getAnimatedValue();
      header.requestLayout();header.setAlpha(fromAlpha+((hide?0:1)-fromAlpha)*a.getAnimatedFraction());});
    headerAnimator.addListener(new android.animation.AnimatorListenerAdapter(){
      @Override public void onAnimationEnd(android.animation.Animator a){
        if(headerAnimator!=a)return;
        header.setVisibility(hide?View.GONE:View.VISIBLE);
        header.getLayoutParams().height=ViewGroup.LayoutParams.WRAP_CONTENT;
        header.setAlpha(1);header.requestLayout();
      }
    });headerAnimator.start();
  }

  private void animateComposer(boolean hide) {
    if(composerAnimator!=null){ValueAnimator old=composerAnimator;composerAnimator=null;old.cancel();}
    int from=currentHeight(composerHost),fromWidth=currentWidth(composerHost);
    float fromNormal=composer.getVisibility()==View.GONE?0:composer.getAlpha();
    float fromPrompt=prompt.getVisibility()==View.GONE?0:prompt.getAlpha();
    int fullWidth=normalWidth();
    LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)composerHost.getLayoutParams();
    FrameLayout.LayoutParams contentParams=(FrameLayout.LayoutParams)composer.getLayoutParams();
    contentParams.width=fullWidth;composer.setLayoutParams(contentParams);
    composer.setVisibility(View.VISIBLE);
    composerTargetHeight=measureContent(fullWidth);
    contentParams.height=composerTargetHeight;composer.setLayoutParams(contentParams);
    int to=hide?dp(48):composerTargetHeight;
    int targetWidth=hide?Math.min(dp(206),fullWidth):fullWidth;
    beginMotion.accept(to-from);
    prompt.setText(UiText.get(R.string.ui_send_message));prompt.setVisibility(View.VISIBLE);
    lp.height=from;lp.width=fromWidth;composerHost.setLayoutParams(lp);
    composer.setAlpha(fromNormal);prompt.setAlpha(fromPrompt);
    composerAnimator=ValueAnimator.ofFloat(0,1);composerAnimator.setDuration(260);
    composerAnimator.setInterpolator(new android.view.animation.PathInterpolator(.22f,0f,.2f,1f));
    composerAnimator.addUpdateListener(a->{float fraction=a.getAnimatedFraction();
      lp.height=from+Math.round((to-from)*fraction);
      lp.width=fromWidth+Math.round((targetWidth-fromWidth)*a.getAnimatedFraction());
      composerHost.setLayoutParams(lp);
      prompt.setAlpha(fromPrompt+((hide?1:0)-fromPrompt)*fraction);
      composer.setAlpha(fromNormal+((hide?0:1)-fromNormal)*fraction);});
    composerAnimator.addListener(new android.animation.AnimatorListenerAdapter(){
      @Override public void onAnimationEnd(android.animation.Animator a){
        if(composerAnimator!=a)return;
        if(hide){composer.setVisibility(View.GONE);composer.setAlpha(1);lp.width=targetWidth;lp.height=dp(48);}
        else {prompt.setVisibility(View.GONE);prompt.setText("");composer.setAlpha(1);
          contentParams.width=ViewGroup.LayoutParams.MATCH_PARENT;contentParams.height=ViewGroup.LayoutParams.WRAP_CONTENT;
          composer.setLayoutParams(contentParams);
          lp.width=regularWidth;lp.height=ViewGroup.LayoutParams.WRAP_CONTENT;}
        lp.gravity=Gravity.CENTER_HORIZONTAL;composerHost.setLayoutParams(lp);
        endMotion.run();
      }
    });composerAnimator.start();
  }

  private boolean editingWithKeyboard(){
    if(!input.hasFocus() || android.os.Build.VERSION.SDK_INT<30)return false;
    WindowInsets insets=activity.getWindow().getDecorView().getRootWindowInsets();
    return insets!=null && insets.isVisible(WindowInsets.Type.ime());
  }
  private int currentHeight(View view){int value=view.getLayoutParams().height;return value>0?value:view.getHeight();}
  private int currentWidth(View view){int value=view.getLayoutParams().width;return value>0?value:view.getWidth();}
  private int measureContent(int width){composer.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));return composer.getMeasuredHeight();}
  private int normalWidth(){View parent=(View)composerHost.getParent();
    int width=parent.getWidth()-parent.getPaddingLeft()-parent.getPaddingRight();
    if(width<=0)width=activity.getResources().getDisplayMetrics().widthPixels-dp(36);
    return Math.max(1,regularWidth>0?Math.min(regularWidth,width):width);}
  private int dp(float value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
}
