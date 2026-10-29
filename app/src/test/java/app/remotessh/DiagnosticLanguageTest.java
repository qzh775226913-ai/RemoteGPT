package app.remotessh;

import static org.junit.Assert.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=35,qualifiers="en")
public class DiagnosticLanguageTest {
  @Test public void localConnectionGuidanceFollowsLanguageWithoutTranslatingRawException() {
    RemoteApp app=(RemoteApp)RuntimeEnvironment.getApplication();
    String raw="Session.connect: 原始服务器报错 %1$s /192.0.2.10 (port 22)";
    DiagnosticText diagnostic=DiagnosticText.connection(new com.jcraft.jsch.JSchException(raw),"连接主机（TCP）",-1,"");
    String english=DiagnosticText.display(diagnostic.raw,diagnostic);
    assertTrue(english.startsWith("Connection failed · Connecting to host (TCP)"));
    assertTrue(english.contains("SSH authentication has not started"));
    assertTrue(english.contains("JSchException: "+raw));
    assertFalse(english.contains("尚未进入"));assertFalse(english.contains("原因："));
    app.language.select("zh-Hant");assertNotEquals(english,DiagnosticText.display(diagnostic.raw,diagnostic));
    app.language.select("en");assertEquals(english,DiagnosticText.display(diagnostic.raw,diagnostic));
    // Equal text from a remote response has no trusted client diagnostic provenance.
    String unrelated=new String(diagnostic.raw);assertSame(unrelated,DiagnosticText.display(unrelated,diagnostic));
  }
  @Test public void operationSnapshotPreservesRawServerMessagesAndDetailsAcrossLanguages() throws Exception {
    RemoteApp app=(RemoteApp)RuntimeEnvironment.getApplication();
    String raw="原始服务器错误：连接失败，设置 %1$s";
    org.json.JSONObject data=Json.obj("message",raw),params=Json.obj("threadId","original-thread","path","/原始/path");
    DiagnosticText diagnostic=DiagnosticText.operation("thread/read",params,
        new RpcException("thread/read",Json.obj("code",42,"message",raw,"data",data)));
    params.put("path","/changed");data.put("message","changed");
    for(int i=1;i<AppLanguage.TAGS.length;i++) {
      app.language.select(AppLanguage.TAGS[i]);String shown=DiagnosticText.display(diagnostic.raw,diagnostic);
      assertTrue(shown.contains(raw));assertTrue(shown.contains("original-thread"));assertTrue(shown.contains("/原始/path"));assertFalse(shown.contains("/changed"));
      if(AppLanguage.TAGS[i].equals("en"))assertFalse(shown.contains("服务器详情："));
    }
  }
  @Test @Config(sdk=26,qualifiers="en") public void actualConnectionFailureDialogAndCopyUseLocalizedWrapper() throws Exception {
    RemoteApp app=(RemoteApp)RuntimeEnvironment.getApplication();
    java.lang.reflect.Method failure=RemoteApp.class.getDeclaredMethod("failConnection",Throwable.class,SshTransport.class);failure.setAccessible(true);
    failure.invoke(app,new com.jcraft.jsch.JSchException("java.net.ConnectException: 原始异常"),null);
    var owner=Robolectric.buildActivity(MainActivity.class).setup();
    try {
      java.lang.reflect.Method show=MainActivity.class.getDeclaredMethod("showConnectionError");show.setAccessible(true);show.invoke(owner.get());
      android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
      dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick();
      org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
      android.content.ClipboardManager clipboard=owner.get().getSystemService(android.content.ClipboardManager.class);
      String copied=clipboard.getPrimaryClip().getItemAt(0).getText().toString();
      assertTrue(copied.startsWith("Connection failed · Initializing remote program"));
      assertTrue(copied.contains("原始异常"));assertTrue(copied.contains("SSH authentication has not started"));
      assertFalse(copied.contains("尚未进入"));assertFalse(copied.contains("连接失败"));
    } finally {owner.pause().stop().destroy();}
  }
  @Test public void backgroundFailureMetadataSurvivesCaptureApplyAndExceptionRecovery() {
    RemoteApp app=(RemoteApp)RuntimeEnvironment.getApplication();app.profile=new Profile();app.threadId="A";app.changed();
    app.reportFailure("model/list",Json.obj("threadId","A"),new java.io.IOException("A raw"));
    DiagnosticText a=app.errorDiagnostic;String rawA=app.error;
    try {
      app.inConversation("B",()->{
        app.replaying=true;
        app.reportFailure("thread/read",Json.obj("threadId","B"),new java.io.IOException("B raw"));
        throw new IllegalStateException("synthetic");
      });fail();
    } catch(IllegalStateException expected) {}
    assertEquals("A",app.threadId);assertFalse(app.replaying);assertSame(a,app.errorDiagnostic);assertSame(rawA,app.error);
    app.inConversation("B",()->{
      assertTrue(app.replaying);assertTrue(DiagnosticText.display(app.error,app.errorDiagnostic).contains("B raw"));
      assertFalse(DiagnosticText.display(app.error,app.errorDiagnostic).contains("A raw"));
      app.error="远端错误保持原文";assertEquals(app.error,DiagnosticText.display(app.error,app.errorDiagnostic));
    });
    assertSame(a,app.errorDiagnostic);assertSame(rawA,app.error);
    app.inConversation("B",()->assertNull(app.errorDiagnostic));
  }
}
