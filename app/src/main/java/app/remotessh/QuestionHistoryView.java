package app.remotessh;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.*;
import org.json.*;
import java.util.function.Consumer;

/** Read-only question/answer cards. Historical choices never submit another answer. */
final class QuestionHistoryView extends LinearLayout {
  private final int fg,muted,selected;
  private final Consumer<String> copy;
  private String rendered="";
  QuestionHistoryView(Context context,int fg,int muted,int background,int selected,Consumer<String> copy) {
    super(context);setOrientation(VERTICAL);this.fg=fg;this.muted=muted;this.selected=selected;this.copy=copy;
    GradientDrawable d=new GradientDrawable();d.setColor(background);d.setCornerRadius(dp(18));setBackground(d);setPadding(dp(16),dp(6),dp(16),dp(12));
  }
  private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
  private TextView text(String value,int color,int size){TextView v=new TextView(getContext());v.setText(MessageBodyView.paragraphText(MessageBodyView.proseDisplay(value),dp(6)));v.setTextColor(color);v.setTextSize(size);v.setIncludeFontPadding(false);v.setTextIsSelectable(true);v.setLineSpacing(dp(3),1);return v;}
  void bind(JSONObject entry) {
    String key=entry.toString();if(key.equals(rendered))return;rendered=key;removeAllViews();
    String status=entry.optString("status");String label=status.equals("answered")?"交互提问 · 已回答":status.equals("pending")?"交互提问 · 待回答":status.equals("skipped")?"交互提问 · 已跳过":"交互提问 · 已结束";
    LinearLayout header=new LinearLayout(getContext());header.setGravity(android.view.Gravity.CENTER_VERTICAL);addView(header,new LayoutParams(-1,-2));
    TextView heading=text(label,muted,12);heading.setTextIsSelectable(false);header.addView(heading,new LayoutParams(0,-2,1));
    JSONArray questions=entry.optJSONArray("questions");JSONObject answers=Json.object(entry,"answers");StringBuilder plain=new StringBuilder();
    TextView button=text("复制",fg,13);button.setTextIsSelectable(false);button.setGravity(android.view.Gravity.CENTER);button.setMinHeight(dp(48));button.setPadding(dp(10),0,dp(10),0);button.setContentDescription("复制交互问答");header.addView(button);PressFeedback.apply(button,(fg&0xffffff)>0x808080);
    if(questions!=null)for(int i=0;i<questions.length();i++) {
      JSONObject q=questions.optJSONObject(i);if(q==null)continue;String title=q.optString("question"),answer=answers.optString(q.optString("id"));
      TextView question=text(title.trim(),fg,15);question.setTypeface(null,Typeface.BOLD);question.setPadding(0,dp(i==0?0:12),0,0);addView(question);
      plain.append(title).append('\n');JSONArray options=q.optJSONArray("options");
      if(options!=null)for(int n=0;n<options.length();n++) {
        JSONObject option=options.optJSONObject(n);if(option==null)continue;String name=option.optString("label"),description=option.optString("description");
        boolean chosen=!q.optBoolean("isSecret") && answer.equals(name);
        TextView value=text((chosen?"✓  ":"○  ")+name+(description.isEmpty()?"":"\n    "+description),chosen?selected:muted,14);value.setPadding(dp(6),dp(4),0,dp(4));addView(value);
        plain.append(chosen?"✓ ":"○ ").append(name).append(description.isEmpty()?"":" — "+description).append('\n');
      }
      if(!answer.isEmpty()) {TextView value=text("回答："+answer.trim(),selected,15);value.setPadding(0,dp(8),0,0);addView(value);plain.append("回答：").append(answer).append('\n');}
      if(i+1<questions.length())plain.append('\n');
    }
    button.setOnClickListener(v->copy.accept(plain.toString().trim()));
  }
}
