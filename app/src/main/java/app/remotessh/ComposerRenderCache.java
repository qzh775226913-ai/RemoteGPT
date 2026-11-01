package app.remotessh;

import android.text.TextUtils;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Activity-local snapshots prevent unchanged streaming state from rebuilding the composer. */
final class ComposerRenderCache {
  private List<Object> attachments,queue;

  void attachments(RemoteApp app,Runnable render){
    List<Object> next=identity(app);
    next.add(app.steering || app.attaching || (app.busy && app.turnId.isEmpty()));
    next.add(app.attaching);
    if(app.attaching){
      next.add(app.attachingFiles);next.add(app.pendingImagePreview);
      next.add(app.imageUploadCount);next.add(app.imageUploadIndex);
    }
    next.add(new ArrayList<>(app.draftImages));
    for(String path:app.draftImages)next.add(app.draftImagePreviews.get(path));
    // FileAttachment is immutable and identity-based; callbacks must own the current draft object.
    next.add(new ArrayList<>(app.draftFiles));
    if(!next.equals(attachments)){render.run();attachments=next;}
  }

  void queue(RemoteApp app,Runnable render){
    List<Object> next=identity(app);String text=app.queuedMessage();next.add(text);
    if(!text.isEmpty())next.add(app.connected && !app.busy && !app.readOnly);
    if(!next.equals(queue)){render.run();queue=next;}
  }

  private static List<Object> identity(RemoteApp app){
    List<Object> next=new ArrayList<>();
    next.add(app.profile==null?null:app.profile.id);next.add(app.threadId);
    next.add(UiText.locale().toLanguageTag());return next;
  }

  static void text(TextView view,CharSequence value){
    if(!TextUtils.equals(view.getText(),value))view.setText(value);
  }
}
