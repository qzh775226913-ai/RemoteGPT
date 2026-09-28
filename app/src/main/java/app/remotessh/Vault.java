package app.remotessh;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.*;
import java.security.KeyStore;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;

/** Profile secrets and host pins are encrypted with a non-exportable Android Keystore key. */
public final class Vault implements HostPins {
  private final AtomicFile file;
  private final String alias = "remote.ssh.v1";
  public final List<Profile> profiles = new ArrayList<>();
  public final Map<String, String> pins = new HashMap<>();

  public Vault(Context c) {
    file = new AtomicFile(new File(c.getFilesDir(), "connections.enc"));
  }

  public synchronized String getPin(String target) {
    return pins.get(target);
  }

  public synchronized void rememberPin(String target, String fingerprint) throws Exception {
    pins.put(target, fingerprint);
    try {
      save();
    } catch (Exception e) {
      pins.remove(target);
      throw e;
    }
  }

  private java.security.Key key() throws Exception {
    KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
    ks.load(null);
    if (!ks.containsAlias(alias)) {
      KeyGenerator g = KeyGenerator.getInstance("AES", "AndroidKeyStore");
      g.init(
          new KeyGenParameterSpec.Builder(
                  alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .build());
      g.generateKey();
    }
    return ks.getKey(alias, null);
  }

  public synchronized void read() throws Exception {
    if (!file.getBaseFile().exists()) return;
    byte[] data = file.readFully();
    if (data.length < 29) throw new IOException("连接存储文件损坏");
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Arrays.copyOfRange(data, 0, 12)));
    JSONObject root =
        new JSONObject(
            new String(
                c.doFinal(Arrays.copyOfRange(data, 12, data.length)),
                java.nio.charset.StandardCharsets.UTF_8));
    JSONArray a = root.optJSONArray("profiles");
    if (a != null)
      for (int i = 0; i < a.length(); i++) profiles.add(Profile.from(a.getJSONObject(i)));
    JSONObject p = root.optJSONObject("pins");
    if (p != null) {
      Iterator<String> it = p.keys();
      while (it.hasNext()) {
        String k = it.next();
        pins.put(k, p.getString(k));
      }
    }
  }

  public synchronized void save() throws Exception {
    JSONArray a = new JSONArray();
    for (Profile p : profiles) a.put(p.json());
    Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
    c.init(Cipher.ENCRYPT_MODE, key());
    byte[] enc =
        c.doFinal(
            Json.obj("profiles", a, "pins", new JSONObject(pins))
                .toString()
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    FileOutputStream out = null;
    try {
      out = file.startWrite();
      out.write(c.getIV());
      out.write(enc);
      file.finishWrite(out);
    } catch (Exception e) {
      if (out != null) file.failWrite(out);
      throw e;
    }
  }
}
