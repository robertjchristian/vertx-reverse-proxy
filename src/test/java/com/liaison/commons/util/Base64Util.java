package com.liaison.commons.util;

import java.io.ByteArrayOutputStream;

import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.Base64Encoder;
import org.bouncycastle.util.encoders.Encoder;

/**
 * bcprov-jdk15-1.50 implementation of Base64
 * version conflict on bc-prov-jdk15
 *
 * @author HPark
 */
public class Base64Util extends Base64 {

    private static final Encoder encoder = new Base64Encoder();

    public static String toBase64String(byte[] data) {
        return toBase64String(data, 0, data.length);
    }

    public static String toBase64String(byte[] data, int off, int length) {
        byte[] encoded = encode(data, off, length);
        return StringsUtil.fromByteArray(encoded);
    }

    /**
     * encode the input data producing a base 64 encoded byte array.
     *
     * @return a byte array containing the base 64 encoded data.
     */
    public static byte[] encode(byte[] data) {
        return encode(data, 0, data.length);
    }

    /**
     * encode the input data producing a base 64 encoded byte array.
     *
     * @return a byte array containing the base 64 encoded data.
     */
    public static byte[] encode(byte[] data, int off, int length) {
        int len = (length + 2) / 3 * 4;
        ByteArrayOutputStream bOut = new ByteArrayOutputStream(len);

        try {
            encoder.encode(data, off, length, bOut);
        } catch (Exception e) {
            throw new IllegalStateException("exception encoding base64 string: " + e.getMessage(), e);
        }

        return bOut.toByteArray();
    }
}
