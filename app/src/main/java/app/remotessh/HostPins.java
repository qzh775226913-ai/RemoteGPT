package app.remotessh;

public interface HostPins {
  String getPin(String target);

  void rememberPin(String target, String fingerprint) throws Exception;
}
