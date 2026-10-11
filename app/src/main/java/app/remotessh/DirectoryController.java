package app.remotessh;

import java.util.concurrent.Callable;

/** Runs SFTP directory operations off the UI thread, rejecting results after reconnect. */
final class DirectoryController {
  private final RemoteApp app;

  DirectoryController(RemoteApp app) { this.app = app; }

  public void browseDirectory(
      String path,
      java.util.function.Consumer<RemoteDirectory> success,
      java.util.function.Consumer<String> failure) {
    SshTransport current = app.currentTransport();
    directoryOperation("sftp/list", path, () -> current.listDirectories(path), success, failure);
  }

  public void createProject(
      String parent,
      String name,
      java.util.function.Consumer<RemoteDirectory> success,
      java.util.function.Consumer<String> failure) {
    SshTransport current = app.currentTransport();
    directoryOperation(
        "sftp/mkdir", parent, () -> current.createDirectory(parent, name), success, failure);
  }

  private void directoryOperation(
      String method,
      String path,
      Callable<RemoteDirectory> task,
      java.util.function.Consumer<RemoteDirectory> success,
      java.util.function.Consumer<String> failure) {
    if (!app.connected || app.currentTransport() == null) {
      failure.accept("请先连接 SSH 主机");
      return;
    }
    int g = app.connectionGeneration();
    app.executeBackground(
        () -> {
          try {
            RemoteDirectory result = task.call();
            app.main.post(
                () -> {
                  if (g != app.connectionGeneration()) {
                    failure.accept("SSH 连接已改变，请重新打开目录");
                    return;
                  }
                  success.accept(result);
                });
          } catch (Exception e) {
            app.main.post(
                () -> {
                  if (g != app.connectionGeneration()) {
                    failure.accept("SSH 连接已改变，请重新打开目录");
                    return;
                  }
                  app.reportFailure(method, Json.obj("path", path), e);
                  failure.accept(
                      ConnectionFailure.operationError(method, Json.obj("path", path), e));
                });
          }
        });
  }

}
