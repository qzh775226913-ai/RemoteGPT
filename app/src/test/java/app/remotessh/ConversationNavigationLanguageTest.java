package app.remotessh;

import static org.junit.Assert.*;
import android.app.AlertDialog;
import android.view.*;
import android.widget.*;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="en-w320dp-h720dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ConversationNavigationLanguageTest {
  @Test public void contextAndHeaderMenusFitAtLargeFontInAllLanguages() throws Exception {
    RemoteApp app=(RemoteApp)RuntimeEnvironment.getApplication();
    app.getSharedPreferences("display",0).edit().putFloat("fontScale",1.15f).apply();
    for (int i=1;i<AppLanguage.TAGS.length;i++) {
      String tag=AppLanguage.TAGS[i];app.language.select(tag);
      var owner=Robolectric.buildActivity(MainActivity.class).setup();
      try {
        MainActivity activity=owner.get();
        ContextRingView ring=findRing(activity.findViewById(android.R.id.content));
        assertNotNull(tag,ring);
        assertTrue(tag,ring.getContentDescription().toString().contains(UiText.get(R.string.ui_unknown)));
        ring.performClick();
        AlertDialog menu=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        assertEquals(tag,4,menu.getListView().getAdapter().getCount());
        assertEquals(tag,UiText.get(R.string.ui_context_information),menu.getListView().getAdapter().getItem(0));
        assertEquals(tag,UiText.get(R.string.ui_compact_context),menu.getListView().getAdapter().getItem(1));
        assertFits(tag,menu);
        menu.dismiss();
        Field titleField=MainActivity.class.getDeclaredField("title");titleField.setAccessible(true);
        TextView title=(TextView)titleField.get(activity);
        ((View)title.getParent()).performClick();
        AlertDialog header=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        assertEquals(tag,3,header.getListView().getAdapter().getCount());
        assertEquals(tag,UiText.get(R.string.ui_current_project_and_conversation),header.getListView().getAdapter().getItem(0));
        assertEquals(tag,UiText.get(R.string.ui_available_skills),header.getListView().getAdapter().getItem(1));
        assertEquals(tag,UiText.get(R.string.ui_mcp_servers),header.getListView().getAdapter().getItem(2));
        assertFits(tag,header);
        header.dismiss();
      } finally {owner.pause().stop().destroy();}
    }
  }

  private ContextRingView findRing(View root) {
    if (root instanceof ContextRingView) return (ContextRingView)root;
    if (root instanceof ViewGroup) for (int i=0;i<((ViewGroup)root).getChildCount();i++) {
      ContextRingView result=findRing(((ViewGroup)root).getChildAt(i));if(result!=null)return result;
    }
    return null;
  }

  private void assertFits(String tag,AlertDialog dialog) {
    View root=dialog.getWindow().getDecorView();
    root.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY));
    root.layout(0,0,320,root.getMeasuredHeight());
    ListView list=dialog.getListView();
    assertTrue(tag,list.getWidth()>0 && list.getWidth()<=320);
    for(int i=0;i<list.getChildCount();i++) {
      View row=list.getChildAt(i);
      assertTrue(tag,row.getWidth()>0 && row.getRight()<=list.getWidth());
      if(row instanceof TextView) {
        TextView label=(TextView)row;
        assertNotNull(tag,label.getLayout());
        for(int line=0;line<label.getLayout().getLineCount();line++)
          assertEquals(tag,0,label.getLayout().getEllipsisCount(line));
      }
    }
  }
}
