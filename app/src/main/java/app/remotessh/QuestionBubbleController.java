package app.remotessh;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;

/** Owns the pending question window, its input-relative bubble and temporary UI state. */
final class QuestionBubbleController {
  private final Activity activity;
  private final RemoteApp app;
  private final DialogUi ui;
  private final FrameLayout viewport;
  private final View composer;
  private final TextView bubble;
  private final QuestionBubbleMotion motion;
  private final Runnable reposition=this::position;
  private final View.OnLayoutChangeListener layoutListener=(v,l,t,r,b,ol,ot,or,ob)->position();
  private JSONObject request;
  private String token="", thread="", savedToken="";
  private int generation;
  private Bundle savedAnswers;
  private boolean savedMinimized, minimized, transitioning;
  private AlertDialog dialog;
  private QuestionForm form;

  QuestionBubbleController(Activity activity,RemoteApp app,DialogUi ui,FrameLayout viewport,View composer){
    this.activity=activity;this.app=app;this.ui=ui;this.viewport=viewport;this.composer=composer;
    motion=new QuestionBubbleMotion(activity);
    bubble=ui.text("?",21,ui.fg);bubble.setTypeface(null,Typeface.BOLD);bubble.setGravity(Gravity.CENTER);
    bubble.setContentDescription(UiText.get(R.string.ui_restore_question));
    GradientDrawable surface=ui.rounded(ui.panel,24);surface.setStroke(ui.dp(1),ui.line);bubble.setBackground(surface);
    bubble.setElevation(ui.dp(5));bubble.setOnClickListener(v->restore());
    viewport.addView(bubble,new FrameLayout.LayoutParams(ui.dp(44),ui.dp(44)));
    bubble.setVisibility(View.INVISIBLE);
    viewport.addOnLayoutChangeListener(layoutListener);composer.addOnLayoutChangeListener(layoutListener);
    viewport.post(reposition);
  }

  void restoreInstance(Bundle saved){
    if(saved==null)return;
    savedToken=saved.getString("questionToken","");savedAnswers=saved.getBundle("questionAnswers");
    savedMinimized=saved.getBoolean("questionMinimized",false);
  }

  void saveInstance(Bundle out){
    if(request==null || form==null)return;
    remember(minimized);out.putString("questionToken",token);out.putBundle("questionAnswers",form.saveAnswers());
    out.putBoolean("questionMinimized",minimized);
  }

  /** Returns true when the current request is a question, including while its answer is being sent. */
  boolean present(JSONObject next,String id){
    boolean question=next!=null && ConversationJournal.question(next);
    if(request!=null && (!question || request!=next || !token.equals(id) || generation!=app.connectionGeneration()))detach();
    if(!question)return false;
    if(app.isAnswering(next))return true;
    if(request==next){syncBubble();return true;}
    request=next;token=id;generation=app.connectionGeneration();
    thread=Json.object(next,"params").optString("threadId",app.threadId);
    if(thread.isEmpty())thread=app.threadId;
    QuestionUiState state=readState();
    form=new QuestionForm(activity,Json.object(next,"params"),ui.fg,ui.muted,ui.panel);
    if(token.equals(state.token)){form.restoreAnswers(state.answers);minimized=state.minimized;}
    else if(token.equals(savedToken)){form.restoreAnswers(savedAnswers);minimized=savedMinimized;}
    else minimized=false;
    savedToken="";savedAnswers=null;savedMinimized=false;
    ScrollView scroller=new ScrollView(activity);scroller.addView(form);
    LinearLayout title=ui.row();title.setPadding(ui.dp(20),ui.dp(6),ui.dp(8),0);
    TextView heading=ui.text(UiText.get(R.string.ui_additional_information),20,ui.fg);heading.setTypeface(null,Typeface.BOLD);
    title.addView(heading,new LinearLayout.LayoutParams(0,ui.dp(48),1));heading.setGravity(Gravity.CENTER_VERTICAL);
    title.addView(ui.icon("collapse",UiText.get(R.string.ui_minimize_question),this::minimize),
        new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
    dialog=new AlertDialog.Builder(activity).setCustomTitle(title).setView(scroller).setCancelable(false)
        .setNegativeButton(ConversationJournal.savedQuestion(next)?UiText.get(R.string.ui_skip_answer):UiText.get(R.string.ui_stop_task),
            (d,w)->{app.cancelQuestion(next);forget();close();})
        .setPositiveButton(UiText.get(R.string.ui_send),null).create();
    dialog.setTitle(UiText.get(R.string.ui_additional_information));
    dialog.show();
    android.widget.Button send=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
    form.onChanged(()->send.setEnabled(form.ready()));
    send.setOnClickListener(v->{
      if(!form.ready() || app.isAnswering(next))return;
      app.answer(next,form.result());
      if(app.isAnswering(next)){remember(false);close();}
    });
    if(minimized){dialog.hide();syncBubble();}else bubble.setVisibility(View.INVISIBLE);
    return true;
  }

  private QuestionUiState readState(){
    final QuestionUiState[] state={new QuestionUiState()};
    app.inConversation(thread,()->state[0]=app.questionUi);
    return state[0];
  }

  private void remember(boolean isMinimized){
    if(form==null || request==null || generation!=app.connectionGeneration())return;
    Bundle answers=form.saveAnswers();String id=token;
    app.inConversation(thread,()->{app.questionUi.token=id;app.questionUi.answers=answers;app.questionUi.minimized=isMinimized;});
  }

  private void forget(){
    if(request==null || generation!=app.connectionGeneration())return;
    String id=token;
    app.inConversation(thread,()->{if(id.equals(app.questionUi.token))app.questionUi=new QuestionUiState();});
  }

  private void minimize(){
    if(dialog==null || transitioning || minimized)return;
    minimized=true;remember(true);position();bubble.setVisibility(View.VISIBLE);transitioning=true;
    View focused=activity.getCurrentFocus();
    if(focused!=null){InputMethodManager ime=(InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);if(ime!=null)ime.hideSoftInputFromWindow(focused.getWindowToken(),0);}
    motion.run(dialog,bubble,true,()->{transitioning=false;syncBubble();});
  }

  private void restore(){
    if(dialog==null || transitioning || !minimized || !thread.equals(app.threadId))return;
    minimized=false;remember(false);transitioning=true;
    AlertDialog current=dialog;
    motion.run(current,bubble,false,()->{
      transitioning=false;
      if(dialog==current && request!=null && generation==app.connectionGeneration())current.show();
      syncBubble();
    });
  }

  private void syncBubble(){
    if(transitioning && !thread.equals(app.threadId)){
      motion.cancel();transitioning=false;minimized=true;remember(true);if(dialog!=null)dialog.hide();
    }
    if(transitioning)return;
    bubble.setAlpha(1f);bubble.setVisibility(request!=null && minimized && thread.equals(app.threadId)?View.VISIBLE:View.INVISIBLE);
    position();
  }

  private void position(){
    if(composer.getWidth()==0 || viewport.getWidth()==0)return;
    int[] origin=new int[2],input=new int[2];viewport.getLocationOnScreen(origin);composer.getLocationOnScreen(input);
    int x=input[0]-origin[0]+composer.getWidth()-ui.dp(44);
    int y=input[1]-origin[1]-ui.dp(50);
    bubble.setX(Math.max(0,Math.min(x,viewport.getWidth()-ui.dp(44))));bubble.setY(Math.max(0,y));
  }

  private void detach(){
    if(request!=null && generation==app.connectionGeneration()){
      if(app.allApprovals().contains(request))remember(minimized);else forget();
    }
    close();
  }

  private void close(){
    motion.cancel();transitioning=false;
    if(dialog!=null)dialog.dismiss();dialog=null;form=null;request=null;token="";thread="";
    bubble.setAlpha(1f);bubble.setVisibility(View.INVISIBLE);
  }

  void dispose(){
    detach();viewport.removeOnLayoutChangeListener(layoutListener);composer.removeOnLayoutChangeListener(layoutListener);
    viewport.removeCallbacks(reposition);viewport.removeView(bubble);
  }
}
