package house.panel;

import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

final class Vault {
    private static final String ALIAS = "bravia_stream_key";
    private static KeyStore store() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        return ks;
    }
    static String seal(String value) throws Exception {
        KeyStore ks = store();
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance("AES","AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            kg.generateKey();
        }
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, ks.getKey(ALIAS,null));
        return Base64.encodeToString(c.getIV(),2)+"."+Base64.encodeToString(c.doFinal(value.getBytes("UTF-8")),2);
    }
    static String open(String blob) throws Exception {
        String[] parts = blob.split("\\.");
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE,store().getKey(ALIAS,null),new GCMParameterSpec(128,Base64.decode(parts[0],2)));
        return new String(c.doFinal(Base64.decode(parts[1],2)),"UTF-8");
    }
    static void clear() throws Exception { store().deleteEntry(ALIAS); }
}
