package sec;

import org.jpos.iso.ISOUtil;
import org.jpos.security.CipherMode;
import org.jpos.security.Util;
import org.jpos.security.jceadapter.JCEHandlerException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JCEHandler {
    private static final String ALG_DES = "DES";
    private static final String ALG_TRIPLE_DES = "DESede";
    private static final String DES_NO_PADDING = "NoPadding";
    private static final Map<JCEHandler.MacEngineKey, Mac> macEngines = new ConcurrentHashMap();
    Provider provider;

    public JCEHandler(String jceProviderClassName) throws JCEHandlerException {
        try {
            this.provider = (Provider)Class.forName(jceProviderClassName).newInstance();
            Security.addProvider(this.provider);
        } catch (Exception var3) {
            throw new JCEHandlerException(var3);
        }
    }

    public JCEHandler(Provider provider) {
        this.provider = provider;
    }

    public Key generateDESKey(short keyLength) throws JCEHandlerException {
        SecretKey generatedClearKey = null;

        try {
            KeyGenerator k1;
            if (keyLength > 64) {
                k1 = KeyGenerator.getInstance("DESede", this.provider.getName());
            } else {
                k1 = KeyGenerator.getInstance("DES", this.provider.getName());
            }

            generatedClearKey = k1.generateKey();
            byte[] clearKeyBytes = this.extractDESKeyMaterial(keyLength, generatedClearKey);
            Util.adjustDESParity(clearKeyBytes);
            return this.formDESKey(keyLength, clearKeyBytes);
        } catch (Exception var5) {
            if (var5 instanceof JCEHandlerException) {
                throw (JCEHandlerException)var5;
            } else {
                throw new JCEHandlerException(var5);
            }
        }
    }

    public byte[] encryptDESKey(short keyLength, Key clearDESKey, Key encryptingKey) throws JCEHandlerException {
        byte[] clearKeyBytes = this.extractDESKeyMaterial(keyLength, clearDESKey);
        Util.adjustDESParity(clearKeyBytes);
        return this.doCryptStuff(clearKeyBytes, encryptingKey, 1);
    }

    protected byte[] extractDESKeyMaterial(short keyLength, Key clearDESKey) throws JCEHandlerException {
        String keyAlg = clearDESKey.getAlgorithm();
        String keyFormat = clearDESKey.getFormat();
        if (keyFormat.compareTo("RAW") != 0) {
            throw new JCEHandlerException("Unsupported DES key encoding format: " + keyFormat);
        } else if (!keyAlg.startsWith("DES")) {
            throw new JCEHandlerException("Unsupported key algorithm: " + keyAlg);
        } else {
            byte[] clearKeyBytes = clearDESKey.getEncoded();
            clearKeyBytes = ISOUtil.trim(clearKeyBytes, this.getBytesLength(keyLength));
            return clearKeyBytes;
        }
    }

    public Key decryptDESKey(short keyLength, byte[] encryptedDESKey, Key encryptingKey, boolean checkParity) throws JCEHandlerException {
        byte[] clearKeyBytes = this.doCryptStuff(encryptedDESKey, encryptingKey, 2);
        Util.adjustDESParity(clearKeyBytes);
        return this.formDESKey(keyLength, clearKeyBytes);
    }

    protected Key formDESKey(short keyLength, byte[] clearKeyBytes) throws JCEHandlerException {
        Key key = null;
        switch(keyLength) {
            case 64:
                key = new SecretKeySpec(clearKeyBytes, "DES");
                break;
            case 128:
                clearKeyBytes = ISOUtil.concat(clearKeyBytes, 0, this.getBytesLength((short)128), clearKeyBytes, 0, this.getBytesLength((short)64));
            case 192:
                key = new SecretKeySpec(clearKeyBytes, "DESede");
        }

        if (key == null) {
            throw new JCEHandlerException("Unsupported DES key length: " + keyLength + " bits");
        } else {
            return key;
        }
    }

    public byte[] encryptData(byte[] data, Key key) throws JCEHandlerException {
        return this.doCryptStuff(data, key, 1);
    }

    public byte[] decryptData(byte[] encryptedData, Key key) throws JCEHandlerException {
        return this.doCryptStuff(encryptedData, key, 2);
    }

    public byte[] encryptDataCBC(byte[] data, Key key, byte[] iv) throws JCEHandlerException {
        return this.doCryptStuff(data, key, 1, CipherMode.CBC, iv);
    }

    public byte[] decryptDataCBC(byte[] encryptedData, Key key, byte[] iv) throws JCEHandlerException {
        return this.doCryptStuff(encryptedData, key, 2, CipherMode.CBC, iv);
    }

    public byte[] doCryptStuff(byte[] data, Key key, int direction) throws JCEHandlerException {
        return this.doCryptStuff(data, key, direction, CipherMode.ECB, (byte[])null);
    }

    byte[] doCryptStuff(byte[] data, Key key, int direction, CipherMode cipherMode, byte[] iv) throws JCEHandlerException {
        String transformation = key.getAlgorithm();
        if (key.getAlgorithm().startsWith("DES")) {
            transformation = transformation + "/" + cipherMode.name() + "/" + "NoPadding";
        }

        IvParameterSpec aps = null;

        try {
            Cipher c1 = Cipher.getInstance(transformation, this.provider.getName());
            if (cipherMode != CipherMode.ECB) {
                aps = new IvParameterSpec(iv);
            }

            c1.init(direction, key, aps);
            byte[] result = c1.doFinal(data);
            if (cipherMode != CipherMode.ECB) {
                System.arraycopy(result, result.length - 8, iv, 0, iv.length);
            }

            return result;
        } catch (Exception var10) {
            throw new JCEHandlerException(var10);
        }
    }

    int getBytesLength(short keyLength) throws JCEHandlerException {
        byte bytesLength;
        switch(keyLength) {
            case 64:
                bytesLength = 8;
                break;
            case 128:
                bytesLength = 16;
                break;
            case 192:
                bytesLength = 24;
                break;
            default:
                throw new JCEHandlerException("Unsupported key length: " + keyLength + " bits");
        }

        return bytesLength;
    }

    Mac assignMACEngine(JCEHandler.MacEngineKey engine) throws JCEHandlerException {
        if (macEngines.containsKey(engine)) {
            return (Mac)macEngines.get(engine);
        } else {
            Mac mac = null;

            try {
                mac = Mac.getInstance(engine.getMacAlgorithm(), this.provider);
                mac.init(engine.getMacKey());
            } catch (NoSuchAlgorithmException | InvalidKeyException var4) {
                throw new JCEHandlerException(var4);
            }

            macEngines.put(engine, mac);
            return mac;
        }
    }

    public byte[] generateMAC(byte[] data, Key kd, String macAlgorithm) throws JCEHandlerException {
        Mac mac = this.assignMACEngine(new JCEHandler.MacEngineKey(macAlgorithm, kd));
        synchronized(mac) {
            mac.reset();
            return mac.doFinal(data);
        }
    }

    protected static class MacEngineKey {
        private final String macAlgorithm;
        private final Key macKey;

        protected MacEngineKey(String macAlgorithm, Key macKey) {
            this.macAlgorithm = macAlgorithm;
            this.macKey = macKey;
        }

        public String getMacAlgorithm() {
            return this.macAlgorithm;
        }

        public Key getMacKey() {
            return this.macKey;
        }

        public int hashCode() {
            int result = 1;
             result = 31 * result + (this.macAlgorithm == null ? 0 : this.macAlgorithm.hashCode());
            return result;
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj == null) {
                return false;
            } else if (this.getClass() != obj.getClass()) {
                return false;
            } else {
                JCEHandler.MacEngineKey other = (JCEHandler.MacEngineKey)obj;
                if (this.macAlgorithm == null) {
                    if (other.macAlgorithm != null) {
                        return false;
                    }
                } else {
                    if (!this.macAlgorithm.equals(other.macAlgorithm)) {
                        return false;
                    }

                    if (this.macKey != other.macKey) {
                        return false;
                    }
                }

                return true;
            }
        }
    }
}
