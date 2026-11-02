package app.remotessh;

import static org.junit.Assert.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35)
public class QuestionFormTest {
  private QuestionForm form(JSONArray questions){return new QuestionForm(RuntimeEnvironment.getApplication(),Json.obj("questions",questions),0xffeeeeee,0xffaaaaaa,0xff303030);}
  private <T extends View> T find(View v,Class<T> type){if(type.isInstance(v))return type.cast(v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){T found=find(((ViewGroup)v).getChildAt(i),type);if(found!=null)return found;}return null;}
  @Test public void explicitChoiceOrOtherTextRequiredAndPreserved() throws Exception {
    JSONArray questions=new JSONArray().put(Json.obj("id","q","question","使用哪个方案","isOther",true,"options",new JSONArray().put(Json.obj("label","方案 A","description","速度快"))));
    QuestionForm form=form(questions);assertFalse(form.ready());
    RadioGroup choices=find(form,RadioGroup.class);((RadioButton)choices.getChildAt(0)).performClick();
    assertTrue(form.ready());assertEquals("方案 A",form.result().getJSONObject("answers").getJSONObject("q").getJSONArray("answers").getString(0));
    ((RadioButton)choices.getChildAt(1)).performClick();assertFalse(form.ready());find(form,EditText.class).setText(" ");assertFalse(form.ready());
    find(form,EditText.class).setText("自己的方案");assertTrue(form.ready());
    QuestionForm restored=form(questions);restored.restoreAnswers(form.saveAnswers());assertTrue(restored.ready());assertEquals(form.result().toString(),restored.result().toString());
  }
  @Test public void allQuestionsRequiredAndSecretsNeverSaved() {
    JSONArray questions=new JSONArray().put(Json.obj("id","one","question","项目路径")).put(Json.obj("id","secret","question","口令","isSecret",true));
    QuestionForm form=form(questions);
    java.util.List<EditText> fields=new java.util.ArrayList<>();for(int i=0;i<form.getChildCount();i++)if(form.getChildAt(i) instanceof EditText)fields.add((EditText)form.getChildAt(i));
    fields.get(0).setText("/work");assertFalse(form.ready());fields.get(1).setText("test-secret");assertTrue(form.ready());
    assertFalse(form.saveAnswers().containsKey("secret"));
    QuestionForm restored=form(questions);restored.restoreAnswers(form.saveAnswers());assertFalse(restored.ready());assertEquals("/work",find(restored,EditText.class).getText().toString());
  }
  @Test public void questionAndOptionTextCanBeSelectedWithoutDisablingTheChoice() {
    JSONArray questions=new JSONArray().put(Json.obj("id","q","question","Choose a path",
        "options",new JSONArray().put(Json.obj("label","Path A","description","Full description"))));
    QuestionForm form=form(questions);TextView title=(TextView)form.getChildAt(0);
    RadioButton option=(RadioButton)find(form,RadioGroup.class).getChildAt(0);
    assertTrue(title.isTextSelectable());assertTrue(option.isTextSelectable());
    assertTrue(option.getText().toString().contains("Full description"));
    option.performClick();assertTrue(option.isChecked());assertTrue(form.ready());
  }

  @Test public void selectableOptionStillRespondsToTouch() {
    JSONArray questions=new JSONArray().put(Json.obj("id","q","question","Choose",
        "options",new JSONArray().put(Json.obj("label","Path A","description","Details"))));
    QuestionForm form=form(questions);RadioButton option=(RadioButton)find(form,RadioGroup.class).getChildAt(0);
    option.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(100,View.MeasureSpec.EXACTLY));
    option.layout(0,0,400,100);
    MotionEvent down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,20,40,0);
    MotionEvent up=MotionEvent.obtain(0,80,MotionEvent.ACTION_UP,20,40,0);
    option.dispatchTouchEvent(down);option.dispatchTouchEvent(up);down.recycle();up.recycle();
    assertTrue("tapping a selectable option should still choose it",option.isChecked());assertTrue(form.ready());
  }

  @Test public void longPressDoesNotSelectAnOptionAndSelectedTextCanBeCopied() {
    org.robolectric.android.controller.ActivityController<android.app.Activity> owner=Robolectric.buildActivity(android.app.Activity.class).setup();
    try {
      android.app.Activity activity=owner.get();
      QuestionForm form=new QuestionForm(activity,Json.obj("questions",new JSONArray().put(Json.obj("id","q","question","Choose",
          "options",new JSONArray().put(Json.obj("label","Path A","description","Details"))))),0xffeeeeee,0xffaaaaaa,0xff303030);
      activity.setContentView(form);RadioButton option=(RadioButton)find(form,RadioGroup.class).getChildAt(0);
      option.measure(View.MeasureSpec.makeMeasureSpec(400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(100,View.MeasureSpec.EXACTLY));option.layout(0,0,400,100);
      MotionEvent down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,20,40,0);
      MotionEvent up=MotionEvent.obtain(0,800,MotionEvent.ACTION_UP,20,40,0);
      option.dispatchTouchEvent(down);option.dispatchTouchEvent(up);down.recycle();up.recycle();assertFalse(option.isChecked());
      assertTrue(option.getText() instanceof android.text.Spannable);
      android.text.Selection.setSelection((android.text.Spannable)option.getText(),0,6);
      assertTrue(option.onTextContextMenuItem(android.R.id.copy));
      android.content.ClipboardManager clipboard=(android.content.ClipboardManager)activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
      assertEquals("Path A",clipboard.getPrimaryClip().getItemAt(0).getText().toString());
    } finally {owner.pause().stop().destroy();}
  }
}
