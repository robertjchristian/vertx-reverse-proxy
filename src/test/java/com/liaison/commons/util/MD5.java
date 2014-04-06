/*
 * Copyright Liaison Technologies, Inc. All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Liaison Technologies, Inc. ("Confidential Information").  You shall 
 * not disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Liaison Technologies.
 */

package com.liaison.commons.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Implementation of MD5.java
 */
public class MD5 {

    /**
     * Default Constructor
     */
    protected MD5() {
    }


    /**
     * Returns true if the MD5 of a file matches the given byte Array
     *
     * @param file File
     * @param md5  byte[]
     * @return boolean
     */
    public static boolean compare(File file, String md5) {
        boolean bRet = false;
        if (file != null && md5 != null) {
            String hex = toHexString(calc(file));
            if (hex != null) {
                bRet = hex.equals(md5);
            }
        }
        return bRet;
    }

    /**
     * Returns true if the MD5 of a file matches the given byte Array
     *
     * @param file File
     * @param md5  byte[]
     * @return boolean
     */
    public static boolean compare(File file, byte[] md5) {
        boolean bRet = false;
        if (file != null && md5 != null) {
            byte[] test = calc(file);
            if (test != null) {
                bRet = MessageDigest.isEqual(test, md5);
            }
        }
        return bRet;
    }

    /**
     * Converts the digest byte array into a HEX String
     *
     * @param digest
     * @return
     */
    public static String toHexString(byte[] digest) {

        char[] hex = new char[digest.length * 2];
        for (int j = 0; j < digest.length; j++) {
            hex[j * 2] = ALPHABET[(digest[j] >> 4) & 0x0f];
            hex[j * 2 + 1] = ALPHABET[(digest[j] >> 0) & 0x0f];
        }

        return new String(hex);

        //        String ret = null;
        //        if (digest != null) {
        //            char[] chars = new char[digest.length * 2];
        //
        //            for (int i = 0; i < digest.length; i++) {
        //                int high = (digest[i] & 0xf0) >> 4;
        //                int low = digest[i] & 0x0f;
        //                chars[2 * i] = Integer.toHexString(high).charAt(0);
        //                chars[2 * i + 1] = Integer.toHexString(low).charAt(0);
        //            }
        //            ret = new String(chars);
        //        }
        //        return ret;
    }

    /**
     * Calculates the MD5 of a byte array.
     *
     * @param bytes byte[]
     * @return byte[]
     */
    public static byte[] calc(byte[] bytes) {
        MessageDigest md5 = null;
        byte[] ret = null;
        md5 = calcMessageDigest(bytes);
        if (md5 != null) {
            ret = md5.digest();
        }
        return ret;
    }


    /**
     * Calculates the MD5 of a byte array.
     *
     * @param bytes byte[]
     * @return MessageDigest md5 or null if something went wrong
     */
    public static MessageDigest calcMessageDigest(byte[] bytes) {
        MessageDigest md5 = null;
        InputStream in = null;

        if (bytes != null) {
            try {
                in = new ByteArrayInputStream(bytes);
                md5 = calc(in);
                in = null;
            } catch (Exception e) {
            }
        }
        return md5;
    }

    /**
     * Calculates the MD5 of a file.
     *
     * @param f File
     * @return byte[]
     */
    public static byte[] calc(File f) {
        MessageDigest md5 = null;
        byte[] ret = null;
        md5 = calcMessageDigest(f);
        if (md5 != null) {
            ret = md5.digest();
        }
        return ret;

    }

    public static byte[] calc(File f, long maxLength) {
        BufferedInputStream bis = null;
        byte[] digest = null;
        try {
            bis = new BufferedInputStream(new FileInputStream(f));
            digest = calc(bis, maxLength);
        } catch (Throwable t) {

        } finally {
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException e) {
                }
            }
        }
        return (digest);
    }

    /**
     * Calculates the MD5 of a file.
     *
     * @param f File
     * @return byte[] the md5 or null if something went wrong
     */
    public static MessageDigest calcMessageDigest(File f) {
        // The various input streams must be explicitly closed and set to null
        // to avoid contention while trying to access the file later.
        FileInputStream fin = null;
        MessageDigest md5 = null;
        InputStream in = null;

        if (f != null && f.exists()) {
            try {
                fin = new FileInputStream(f);
                in = new BufferedInputStream(fin);
                md5 = calc(in);
            } catch (Throwable e) {
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (Throwable e2) {
                    }
                    in = null;
                }
                if (fin != null) {
                    try {
                        fin.close();
                    } catch (Throwable e2) {
                    }
                    fin = null;
                }
            }
        }
        return md5;
    }

    /**
     * Calculates the MD5 of an input stream
     *
     * @param in InputStream
     * @return byte[]
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    protected static MessageDigest calc(InputStream in) throws NoSuchAlgorithmException, IOException {
        MessageDigest digest = null;
        digest = MessageDigest.getInstance("MD5");

        if (in != null && digest != null) {
            DigestInputStream dis = new DigestInputStream(in, digest);
            byte[] bytes = new byte[8192];
            try {
                while (dis.read(bytes) >= 0)
                    ;
            } catch (Exception e) {
            } finally {
                try {
                    dis.close();
                } catch (Exception e2) {
                }
                dis = null;
            }
        }
        return digest;
    }

    /**
     * Calculates the MD5 of the input stream up to the number of bytes specified by length.
     *
     * @param inStream
     * @param length
     * @return the digest byte array or null
     * @throws NoSuchAlgorithmException
     * @throws IOException
     * @see #calc(File)
     */
    public static byte[] calc(InputStream inStream, long length) throws NoSuchAlgorithmException, IOException {
        BufferedInputStream is = null;
        try {

            if (inStream instanceof BufferedInputStream) {
                is = (BufferedInputStream) inStream;
            } else {
                new BufferedInputStream(inStream);
            }

            byte[] buffer = new byte[BUFFER_SIZE];
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            while (length > 0) {
                int len;
                if (length > BUFFER_SIZE) {
                    len = BUFFER_SIZE;
                } else {
                    len = (int) length;
                }
                len = is.read(buffer, 0, len);
                if (len == 0) // Something fishy with the file
                    return null;
                md5.update(buffer, 0, len);
                length -= len;
            }
            is.close();
            byte[] digest = md5.digest();
            is = null;

            return digest;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
        }
    }

    protected static final int BUFFER_SIZE = 4096;
    protected static final char[] ALPHABET = "0123456789abcdef".toCharArray();
}