package app.remotessh;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.*;
import android.view.View;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Explicit selection and validated answers; opening the form never submits an answer. */
final class QuestionForm extends LinearLayout {
  private static final class Answer {
    String id; boolean secret; EditText text; RadioGroup choices; int other=-1;
    final Map<Integer,String> labels=new LinkedHashMap<>();
    String value() {
      if (choices==null || (other!=-1 && choices.getCheckedRadioButtonId()==other)) return text.getText().toString();
      return labels.getOrDefault(choices.getCheckedRadioButtonId(), "");
    }
  }
  private final List<Answer> answers=new ArrayList<>();
  private Runnable changed=()->{};
  private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
  private TextView label(String value,int fg) { TextView v=new TextView(getContext());v.setText(value);v.setTextSize(14);v.setTextColor(fg);return v; }
  private GradientDrawable background(int panel) { GradientDrawable d=new GradientDrawable();d.setColor(panel);d.setCornerRadius(dp(12));return d; }

  QuestionForm(Context context, JSONObject params, int fg, int muted, int panel) {
    super(context);setOrientation(VERTICAL);setPadding(dp(20),dp(8),dp(20),dp(16));
    TextView hint=label(params.has("callId")
        ? "远端的异步问题已保留。点击发送后，答案会作为对话消息回传。"
        : params.optBoolean("isBlocking",true)
        ? "等待你的回答后继续；选择选项或输入内容，再点发送。"
        : "这是远端的非阻塞提问：等待回答期间，它可以继续独立工作。",muted);
    addView(hint);JSONArray questions=params.optJSONArray("questions");
    if (questions==null) return;
    for (int i=0;i<questions.length();i++) {
      JSONObject q=questions.optJSONObject(i);if(q==null)continue;
      Answer answer=new Answer();answer.id=q.optString("id");answer.secret=q.optBoolean("isSecret");answers.add(answer);
      TextView title=label(q.optString("question"),fg);title.setPadding(0,dp(20),0,dp(10));addView(title);
      answer.text=new EditText(context);answer.text.setTextSize(15);answer.text.setTextColor(fg);answer.text.setHintTextColor(muted);
      answer.text.setHint("输入回答");answer.text.setContentDescription(q.optString("question"));
      answer.text.setPadding(dp(14),dp(12),dp(14),dp(12));answer.text.setMinHeight(dp(48));answer.text.setBackground(background(panel));
      answer.text.setInputType(InputType.TYPE_CLASS_TEXT | (answer.secret ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_FLAG_MULTI_LINE));
      answer.text.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
      JSONArray options=q.optJSONArray("options");
      if (options!=null && options.length()>0 && !answer.secret) {
        answer.choices=new RadioGroup(context);addView(answer.choices,new LayoutParams(-1,-2));
        for (int k=0;k<options.length();k++) {
          JSONObject option=options.optJSONObject(k);if(option==null)continue;
          String name=option.optString("label"),description=option.optString("description");
          RadioButton button=new RadioButton(context);button.setId(View.generateViewId());button.setText(name+(description.isEmpty()?"":"\n"+description));
          button.setTextSize(14);button.setTextColor(fg);button.setContentDescription(name);button.setMinHeight(dp(56));
          button.setPadding(dp(8),dp(12),dp(12),dp(12));button.setBackground(background(panel));
          RadioGroup.LayoutParams bp=new RadioGroup.LayoutParams(-1,-2);bp.bottomMargin=dp(10);answer.choices.addView(button,bp);
          answer.labels.put(button.getId(),name);
        }
        if(q.optBoolean("isOther")) {
          RadioButton other=new RadioButton(context);other.setId(View.generateViewId());other.setText("自己填写");other.setTextColor(fg);other.setMinHeight(dp(48));
          answer.other=other.getId();answer.choices.addView(other);
        }
        answer.text.setVisibility(GONE);
        answer.choices.setOnCheckedChangeListener((group,id)->{
          answer.text.setVisibility(id==answer.other ? VISIBLE:GONE);changed.run();
        });
      }
      addView(answer.text,new LayoutParams(-1,-2));
      answer.text.addTextChangedListener(new TextWatcher(){
        public void beforeTextChanged(CharSequence s,int start,int count,int after){}
        public void onTextChanged(CharSequence s,int start,int before,int count){changed.run();}
        public void afterTextChanged(Editable e){}
      });
    }
  }
  void onChanged(Runnable listener) {changed=listener;changed.run();}
  boolean ready() {if(answers.isEmpty())return false;for(Answer answer:answers)if(answer.id.isEmpty()||answer.value().trim().isEmpty())return false;return true;}
  JSONObject result() {
    JSONObject values=new JSONObject();for(Answer answer:answers)try{values.put(answer.id,Json.obj("answers",new JSONArray().put(answer.value())));}catch(JSONException ignored){}
    return Json.obj("answers",values);
  }
  Bundle saveAnswers() {
    Bundle state=new Bundle();for(Answer answer:answers)if(!answer.secret){
      Bundle saved=new Bundle();saved.putString("text",answer.text.getText().toString());
      if(answer.choices!=null){int id=answer.choices.getCheckedRadioButtonId();saved.putBoolean("other",id==answer.other && id!=-1);saved.putString("label",answer.labels.getOrDefault(id,""));}
      state.putBundle(answer.id,saved);
    }return state;
  }
  void restoreAnswers(Bundle state) {
    if(state==null)return;for(Answer answer:answers){Bundle saved=state.getBundle(answer.id);if(saved==null||answer.secret)continue;
      answer.text.setText(saved.getString("text",""));if(answer.choices!=null){
        if(saved.getBoolean("other") && answer.other!=-1)answer.choices.check(answer.other);
        else for(Map.Entry<Integer,String> entry:answer.labels.entrySet())if(entry.getValue().equals(saved.getString("label","")))answer.choices.check(entry.getKey());
      }
    }
  }
}
