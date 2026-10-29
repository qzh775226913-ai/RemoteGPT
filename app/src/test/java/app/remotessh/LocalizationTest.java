package app.remotessh;

import static org.junit.Assert.*;
import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, qualifiers="en-w360dp-h720dp-xhdpi")
public class LocalizationTest {
  private RemoteApp app;
  @Before public void setup() { app = (RemoteApp) RuntimeEnvironment.getApplication(); }

  @Test public void defaultFollowsSystemAndUnsupportedLanguagesFallBackToEnglish() {
    assertEquals("", AppLanguage.preference(app));
    assertEquals("Settings", UiText.get(R.string.ui_settings));
    for (String[] pair : new String[][] {{"zh-CN","zh-Hans"},{"zh-SG","zh-Hans"},
        {"zh-TW","zh-Hant"},{"zh-HK","zh-Hant"},{"zh-Hans-TW","zh-Hans"},
        {"pt-PT","pt-BR"},{"es-MX","es"},{"ar","en"}})
      assertEquals(pair[0], pair[1], AppLanguage.supported(Locale.forLanguageTag(pair[0])).toLanguageTag());
    systemLanguage("ja-JP");
    assertEquals("ja", UiText.locale().getLanguage());
    systemLanguage("ar");
    assertEquals("Settings", UiText.get(R.string.ui_settings));
  }

  private void systemLanguage(String tag) {
    Configuration config = new Configuration(app.getResources().getConfiguration());
    config.setLocales(new LocaleList(Locale.forLanguageTag(tag)));
    app.getResources().updateConfiguration(config, app.getResources().getDisplayMetrics());
    app.onConfigurationChanged(config);
  }

  @Test public void explicitChoiceSurvivesSystemChangesAndCanReturnToFollowingSystem() {
    app.language.select("de");
    assertEquals("de", AppLanguage.preference(app));
    systemLanguage("ja");
    AppLanguage.refresh(app); // Same path as process initialization.
    assertEquals("de", UiText.locale().getLanguage());
    app.language.select("");
    assertEquals("ja", UiText.locale().getLanguage());
    assertEquals("", AppLanguage.preference(app));
  }

  @Test public void everyLanguageHasItsOwnSettingsAndPermissionLabels() {
    String[] settings = {"Settings","设置","設定","設定","설정","Ajustes","Configurações","Einstellungen","Paramètres","Настройки"};
    for (int i=1; i<AppLanguage.TAGS.length; i++) {
      app.language.select(AppLanguage.TAGS[i]);
      assertEquals(AppLanguage.TAGS[i], settings[i-1], UiText.get(R.string.ui_settings));
      assertFalse(UiPresentation.permission(PermissionMode.FULL).isEmpty());
      assertFalse(UiText.quantity(R.plurals.ui_minutes, 1).isEmpty());
      assertFalse(UiText.quantity(R.plurals.ui_minutes, 2).isEmpty());
      assertFalse(UiText.quantity(R.plurals.ui_minutes, 5).isEmpty());
    }
  }

  @Test public void languagePickerUsesAutonymsAndPersistsSelection() {
    var activity = Robolectric.buildActivity(MainActivity.class).setup();
    try {
      LanguagePicker.show(activity.get(), app);
      AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
      assertEquals("Language", Shadows.shadowOf(dialog).getTitle().toString());
      ListView list = dialog.getListView();
      for (int i=1; i<AppLanguage.NAMES.length; i++)
        assertEquals(AppLanguage.NAMES[i], list.getAdapter().getItem(i).toString());
      list.performItemClick(list.getAdapter().getView(4,null,list),4,4);
      assertEquals("ja", AppLanguage.preference(app));
      assertFalse(dialog.isShowing());
    } finally { activity.pause().stop().destroy(); }
  }

  @Test public void rawMessagesErrorsQuestionsAndServerLabelsAreNeverTranslated() throws Exception {
    String raw = "设置\n允许一次\nОшибка: /tmp/中文 --flag\n%1$s";
    ChatItem message = new ChatItem("a","agentMessage",raw);
    ChatItem command = new ChatItem("c","commandExecution",raw);
    JSONObject model = Json.obj("model","remote", "displayName",raw,"serviceTiers",
        new JSONArray().put(Json.obj("id","custom","name",raw,"description",raw)));
    app.items.add(message); app.error=raw;
    JSONObject question=Json.obj("id","q","question",raw,"options",
        new JSONArray().put(Json.obj("label",raw,"description",raw)));
    JSONObject request=Json.obj("id",7,"method","item/tool/requestUserInput","params",
        Json.obj("questions",new JSONArray().put(question)));
    String original=request.toString();
    for (String tag : AppLanguage.TAGS) {
      app.language.select(tag);
      assertEquals(raw, UiPresentation.itemTitle(message));
      assertEquals(raw, UiPresentation.itemTitle(command));
      assertEquals(raw, app.error);
      assertEquals(raw, ModelOptions.displayName(new JSONArray().put(model),"remote"));
      assertEquals(raw, ServiceTierOptions.label("custom",model));
      assertEquals(raw, ServiceTierOptions.description("custom",model));
      QuestionForm form = new QuestionForm(AppLanguage.wrap(app), Json.object(request,"params"), 0xff000000,0xff777777,0xffffffff);
      assertEquals(original, request.toString());
    }
    assertEquals(raw,message.text);
  }

  @Test public void switchingWhileApprovalPendingKeepsIdentityStateAndRemoteDetails() {
    app.profile=new Profile(); app.connected=true; app.threadId="A"; app.turnId="turn-A"; app.busy=true;
    app.state="正在停止…"; app.draftText="draft A";
    app.inConversation("B",()->{app.draftText="draft B"; app.state="已连接";});
    JSONObject request=Json.obj("id",7,"method","item/commandExecution/requestApproval","params",
        Json.obj("threadId","A","command","echo 设置","reason","原始原因"));
    app.approvals.add(request); app.changed();
    String token=app.notificationApprovalToken(request);
    var service=Robolectric.buildService(ConnectionService.class).create();
    try {
      app.language.select("de");
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
      NotificationManager manager=app.getSystemService(NotificationManager.class);
      Notification n=Shadows.shadowOf(manager).getNotification(app.liveStatus.idFor("A"));
      assertEquals("Einmal erlauben", n.actions[0].title.toString());
      assertTrue(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("echo 设置"));
      assertEquals(token,app.notificationApprovalToken(request));
      assertSame(request,app.approvals.get(0));
      assertEquals("A",app.threadId);assertEquals("draft A",app.draftText);assertEquals("正在停止…",app.state);
      app.inConversation("B",()->assertEquals("draft B",app.draftText));
      assertEquals("A",app.threadId);
      assertEquals("SSH-Verbindung",manager.getNotificationChannel("ssh").getName().toString());
    } finally {service.destroy();}
  }

  @Test public void localFormValidationIsTranslatedWithoutChangingDiagnosticExceptions() throws Exception {
    Profile profile=new Profile();
    try {profile.validate();fail("invalid host must fail");}
    catch(IllegalArgumentException error) {
      assertEquals("请输入有效的 SSH 主机地址，不包含 ssh://",error.getMessage());
      assertEquals("Enter a valid SSH host address without ssh://",UiPresentation.profileValidation(error));
    }
    var activity=Robolectric.buildActivity(MainActivity.class).setup();
    try {
      java.lang.reflect.Method edit=MainActivity.class.getDeclaredMethod("editProfile",Profile.class,boolean.class);
      edit.setAccessible(true);edit.invoke(activity.get(),profile,true);
      Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();
      View root=dialog.getWindow().getDecorView();
      findText(root,"Save").performClick();
      assertNotNull(findText(root,"Enter a valid SSH host address without ssh://"));
      assertTrue(dialog.isShowing());
      assertFalse(app.connected);
      dialog.dismiss();
    } finally {activity.pause().stop().destroy();}
  }
  private TextView findText(View view,String value) {
    if(view instanceof TextView && value.contentEquals(((TextView)view).getText()))return (TextView)view;
    if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
      TextView result=findText(((ViewGroup)view).getChildAt(i),value);if(result!=null)return result;
    }
    return null;
  }

  @Test public void recreationPreservesDraftAttachmentsAndQuestionAnswers() throws Exception {
    app.profile=new Profile();app.threadId="draft-thread";
    app.draftText="原始草稿\nkeep %1$s";app.draftImages.add("/tmp/original.png");
    var activity=Robolectric.buildActivity(MainActivity.class).setup();
    try {
      java.lang.reflect.Field input=MainActivity.class.getDeclaredField("input");input.setAccessible(true);
      ((EditText)input.get(activity.get())).setText("Edited draft 原文");
      app.language.select("fr");
      activity.recreate();
      assertEquals("Edited draft 原文",((EditText)input.get(activity.get())).getText().toString());
      assertEquals(Collections.singletonList("/tmp/original.png"),app.draftImages);
      assertEquals("draft-thread",app.threadId);
      assertFalse(app.connected);
      JSONObject params=Json.obj("questions",new JSONArray().put(Json.obj("id","q","question","原始提问")));
      QuestionForm form=new QuestionForm(AppLanguage.wrap(app),params,0xff000000,0xff777777,0xffffffff);
      EditText answer=findInput(form);answer.setText("原始回答 %1$s");Bundle saved=form.saveAnswers();
      app.language.select("ja");
      QuestionForm restored=new QuestionForm(AppLanguage.wrap(app),params,0xff000000,0xff777777,0xffffffff);
      restored.restoreAnswers(saved);
      assertEquals("原始回答 %1$s",findInput(restored).getText().toString());
    } finally {activity.pause().stop().destroy();}
  }
  private EditText findInput(View view) {
    if(view instanceof EditText)return (EditText)view;
    if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++) {
      EditText result=findInput(((ViewGroup)view).getChildAt(i));if(result!=null)return result;
    }
    return null;
  }

  @Test @Config(sdk=26, qualifiers="en-w320dp-h640dp-mdpi")
  public void android8UsesTheSamePreferenceAndKeepsFontScaling() {
    app.getSharedPreferences("display",Context.MODE_PRIVATE).edit().putFloat("fontScale",.9f).apply();
    app.language.select("ja");
    var activity=Robolectric.buildActivity(MainActivity.class).setup();
    try {
      assertEquals("ja",activity.get().getResources().getConfiguration().getLocales().get(0).getLanguage());
      assertEquals(.9f,activity.get().getResources().getConfiguration().fontScale,.001f);
    } finally {activity.pause().stop().destroy();}
  }
}
