package app.remotessh;

import android.os.Bundle;

/** Unsaved, non-secret question answers and presentation state for one conversation. */
final class QuestionUiState {
  String token = "";
  Bundle answers;
  boolean minimized;
}
