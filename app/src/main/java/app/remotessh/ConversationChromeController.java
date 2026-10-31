package app.remotessh;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
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
    composer.addView(prompt,0,new LinearLayout.LayoutParams(-1,dp(48)));
    LinearLayout toolbar=new LinearLayout(activity);
    toolbar.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
    enlarge=new IconView(activity,"expand",foreground);
    enlarge.setContentDescription(UiText.get(R.string.ui_expand_composer));
    enlarge.setOnClickListener(v->{large=!large;enlarge.icon=large?"collapse":"expand";
      enlarge.setContentDescription(UiText.get(large?R.string.ui_collapse_composer:R.string.ui_expand_composer));
      enlarge.invalidate();resizeInput.run();});
    toolbar.addView(enlarge,new LinearLayout.LayoutParams(dp(42),dp(34)));
    composer.addView(toolbar,1,new LinearLayout.LayoutParams(-1,dp(34)));
    composer.setBackground(rounded(panel,20));
    composer.setPadding(dp(4),dp(3),dp(4),dp(4));
  }

  boolean large() {return large;}
  void restore() {if(compact)setCompact(false);large=false;resizeInput.run();}

  void onScroll(int y,int oldY,boolean userScroll) {
    if(!userScroll || input.hasFocus() || headerAnimator!=null && headerAnimator.isRunning()
        || composerAnimator!=null && composerAnimator.isRunning())return;
    if(y<=0){if(compact)setCompact(false);return;}
    int delta=y-oldY;
    if(delta==0)return;
    int sign=delta>0?1:-1;
    if(sign!=direction){direction=sign;accumulated=0;}
    accumulated+=Math.min(Math.abs(delta),dp(35));
    if(accumulated>=dp(sign>0?40:50)) {setCompact(sign>0);accumulated=0;}
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
    int from=composer.getHeight();
    LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)composer.getLayoutParams();
    if(!hide){lp.width=regularWidth;composer.setLayoutParams(lp);
      attachments.setVisibility(View.VISIBLE);input.setVisibility(View.VISIBLE);controls.setVisibility(View.VISIBLE);
      composer.getLayoutParams().height=ViewGroup.LayoutParams.WRAP_CONTENT;
      composer.measure(View.MeasureSpec.makeMeasureSpec(composer.getWidth(),View.MeasureSpec.EXACTLY),
          View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));}
    int to=hide?dp(48):composer.getMeasuredHeight();
    if(hide)prompt.setText(UiText.get(R.string.ui_send_message));
    prompt.setVisibility(View.VISIBLE);
    composerAnimator=ValueAnimator.ofInt(from,to);composerAnimator.setDuration(230);
    composerAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
    composerAnimator.addUpdateListener(a->{composer.getLayoutParams().height=(int)a.getAnimatedValue();
      composer.requestLayout();float fraction=a.getAnimatedFraction();
      prompt.setAlpha(hide?fraction:1-fraction);
      float normal=hide?1-fraction:fraction;
      attachments.setAlpha(normal);input.setAlpha(normal);controls.setAlpha(normal);});
    composerAnimator.addListener(new android.animation.AnimatorListenerAdapter(){
      @Override public void onAnimationEnd(android.animation.Animator a){
        if(composerAnimator!=a)return;
        if(hide){attachments.setVisibility(View.GONE);input.setVisibility(View.GONE);
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
  private int dp(float value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
}
