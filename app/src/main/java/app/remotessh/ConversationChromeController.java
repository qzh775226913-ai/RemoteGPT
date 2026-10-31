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
  private final View attachments, controls;
  private final TextView prompt;
  private final IconView enlarge;
  private final FrameLayout inputLayer;
  private final Runnable resizeInput;
  private boolean compact, large;
  private int accumulated, direction;
  private ValueAnimator headerAnimator, composerAnimator;
  private final int regularWidth;

  ConversationChromeController(Activity activity, LinearLayout header, LinearLayout composer,
      EditText input, View attachments, View controls, int foreground, int muted,
      int panel, Runnable resizeInput) {
    this.activity=activity;this.header=header;this.composer=composer;this.input=input;
    this.attachments=attachments;this.controls=controls;this.resizeInput=resizeInput;
    regularWidth=((LinearLayout.LayoutParams)composer.getLayoutParams()).width;
    prompt=new TextView(activity);
    prompt.setText("");prompt.setTextColor(muted);
    prompt.setTextSize(14);prompt.setGravity(Gravity.CENTER_VERTICAL);
    prompt.setPadding(dp(14),0,dp(14),0);
    prompt.setVisibility(View.GONE);
    prompt.setContentDescription(UiText.get(R.string.ui_expand_composer));
    prompt.setOnClickListener(v->{setCompact(false);input.requestFocus();});
    composer.addView(prompt,0,new LinearLayout.LayoutParams(-1,dp(40)));
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
    composer.setBackground(rounded(panel,26));
    composer.setClipToOutline(true);
    composer.setPadding(dp(4),dp(3),dp(4),dp(4));
  }

  boolean large() {return large;}
  void restore() {if(compact)setCompact(false);large=false;enlarge.icon="expand";
    enlarge.setContentDescription(UiText.get(R.string.ui_expand_composer));enlarge.invalidate();resizeInput.run();}

  void onScroll(int y,int oldY,boolean userScroll) {
    if(!userScroll || editingWithKeyboard())return;
    int delta=y-oldY;
    if(delta==0)return;
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

  private void animateHeader(boolean hide) {
    if(headerAnimator!=null)headerAnimator.cancel();
    int from=header.getHeight();
    if(!hide){header.setVisibility(View.VISIBLE);header.getLayoutParams().height=ViewGroup.LayoutParams.WRAP_CONTENT;
      header.measure(View.MeasureSpec.makeMeasureSpec(header.getWidth(),View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));}
    int to=hide?0:header.getMeasuredHeight();
    headerAnimator=ValueAnimator.ofInt(from,to);headerAnimator.setDuration(260);
    headerAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
    headerAnimator.addUpdateListener(a->{header.getLayoutParams().height=(int)a.getAnimatedValue();
      header.requestLayout();header.setAlpha(hide?1-a.getAnimatedFraction():a.getAnimatedFraction());});
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
    if(composerAnimator!=null)composerAnimator.cancel();
    int from=composer.getHeight(),fromWidth=composer.getWidth();
    LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)composer.getLayoutParams();
    if(!hide){attachments.setVisibility(View.VISIBLE);inputLayer.setVisibility(View.VISIBLE);controls.setVisibility(View.VISIBLE);
      enlarge.setVisibility(View.VISIBLE);
      composer.getLayoutParams().height=ViewGroup.LayoutParams.WRAP_CONTENT;
      int targetWidth=normalWidth();
      composer.measure(View.MeasureSpec.makeMeasureSpec(targetWidth,View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));}
    int to=hide?dp(48):composer.getMeasuredHeight();
    int targetWidth=hide?dp(206):normalWidth();
    if(hide)prompt.setText(UiText.get(R.string.ui_send_message));
    prompt.setVisibility(View.VISIBLE);
    composerAnimator=ValueAnimator.ofInt(from,to);composerAnimator.setDuration(230);
    composerAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
    composerAnimator.addUpdateListener(a->{composer.getLayoutParams().height=(int)a.getAnimatedValue();
      lp.width=fromWidth+Math.round((targetWidth-fromWidth)*a.getAnimatedFraction());
      composer.setLayoutParams(lp);
      composer.requestLayout();float fraction=a.getAnimatedFraction();
      prompt.setAlpha(hide?fraction:1-fraction);
      float normal=hide?1-fraction:fraction;
      attachments.setAlpha(normal);inputLayer.setAlpha(normal);controls.setAlpha(normal);});
    composerAnimator.addListener(new android.animation.AnimatorListenerAdapter(){
      @Override public void onAnimationEnd(android.animation.Animator a){
        if(composerAnimator!=a)return;
        if(hide){attachments.setVisibility(View.GONE);inputLayer.setVisibility(View.GONE);
          controls.setVisibility(View.GONE);lp.width=dp(206);lp.gravity=Gravity.CENTER_HORIZONTAL;
          composer.setLayoutParams(lp);}
        else {prompt.setVisibility(View.GONE);prompt.setText("");lp.width=regularWidth;composer.setLayoutParams(lp);}
        composer.getLayoutParams().height=ViewGroup.LayoutParams.WRAP_CONTENT;
        composer.requestLayout();
      }
    });composerAnimator.start();
  }

  private GradientDrawable rounded(int color,int radius) {GradientDrawable d=new GradientDrawable();
    d.setColor(color);d.setCornerRadius(dp(radius));return d;}
  private boolean editingWithKeyboard(){
    if(!input.hasFocus() || android.os.Build.VERSION.SDK_INT<30)return false;
    WindowInsets insets=activity.getWindow().getDecorView().getRootWindowInsets();
    return insets!=null && insets.isVisible(WindowInsets.Type.ime());
  }
  private int normalWidth(){return regularWidth>0?regularWidth:
      Math.max(dp(206),((View)composer.getParent()).getWidth()-dp(36));}
  private int dp(float value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
}
