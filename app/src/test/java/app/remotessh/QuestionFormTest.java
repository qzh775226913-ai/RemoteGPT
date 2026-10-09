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
}
