package app.remotessh;

import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Keeps the resume control stable across connection and recovery states. */
final class ConversationOverlayLayout {
  private ConversationOverlayLayout() {}

  static void addResume(LinearLayout holder,TextView button){
    float density=holder.getResources().getDisplayMetrics().density;
    int padding=Math.round(12*density),spinner=Math.round(20*density);
    int minWidth=Math.round(112*density);
    int maxWidth=Math.max(minWidth,Math.min(Math.round(300*density),
        holder.getResources().getDisplayMetrics().widthPixels-Math.round(36*density)));
    holder.setGravity(Gravity.CENTER_HORIZONTAL);
    holder.setPadding(0,Math.round(3*density),0,Math.round(5*density));
    button.setSingleLine(true);button.setEllipsize(android.text.TextUtils.TruncateAt.END);
    button.setPadding(padding,Math.round(8*density),padding,Math.round(8*density));
    float longest=0;
    for(int id:new int[]{R.string.ui_resume,R.string.ui_reconnect_and_resume,R.string.ui_connecting,
        R.string.ui_synchronizing,R.string.ui_resuming})
      longest=Math.max(longest,button.getPaint().measureText(UiText.get(id)));
    int available=maxWidth-padding*2-spinner;
    if(longest>available && available>0){
      button.setTextSize(TypedValue.COMPLEX_UNIT_PX,button.getTextSize()*available/longest);
      longest=available;
    }
    int width=Math.min(maxWidth,Math.max(minWidth,(int)Math.ceil(longest)+padding*2+spinner));
    holder.addView(button,new LinearLayout.LayoutParams(width,Math.round(44*density)));
  }
}
