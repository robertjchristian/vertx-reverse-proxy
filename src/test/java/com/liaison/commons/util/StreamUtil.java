/*
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Implementation of commonly used Stream based utilities
 */
public class StreamUtil {

	/**
	 * Default constructor 
	 */
	protected StreamUtil() {
	}


	/**
	 * Reads up to length bytes into an array starting at the specified start position.  If a CRLF (i.e. OxOd + 0x0a) is
	 * encountered the reading is stopped.  <b>Note this method returns the CRLF!</b>
	 * @param in InputStream
	 * @param bytes byte[]
	 * @param start int
	 * @param length int
	 * @throws IOException
	 * @return int
	 */
	public static int readLine(InputStream in, byte[] bytes, int start, int length) throws IOException {
		int count = 0;
		int c = 0;
		int last = 0;

		if (in == null) {
			throw new IOException("InputStream is null.");
		}

		if (bytes == null) {
			throw new IOException("Byte array is null.");
		}

		if (bytes.length < length) {
			throw new IOException("Invalid length parameter.  Trying to read " + length + " bytes into array of size " + bytes.length);
		}

		if (length > 0) {
			do {
				last = c;
				c = in.read();
				// if reached EOF, we need to exit right away otherwise the count will
				// still be incremented by 1
				// Fixed as per bug Kenya #569
				if (c == -1) {
					break;
				}
				bytes[start + (count++)] = (byte) c;
			} while ((!(last == 0x0d && c == 0x0a)) && (c != -1) && (count < length));
		}

		return count;
	}

	/**
	 * Reads up to length bytes into an array.  If a CRLF (i.e. OxOd + 0x0a) is
	 * encountered the reading is stopped.  <b>Note this method returns the CRLF!</b>
	 * @param in InputStream
	 * @param bytes byte[]
	 * @throws IOException
	 * @return int
	 */
	public static int readLine(InputStream in, byte[] bytes) throws IOException {

		// check for bytes == null, otherwise, bytes.length will blow up
		if (bytes == null) {
			throw new IOException("Byte array is null.");
		}

		return readLine(in, bytes, 0, bytes.length);
	}


	/**
	 * Reads an input stream using the provided buffer size and writes content to a output stream, and closes the output stream upon method exit
	 * @param is InputStream The input stream containing the data to be read
	 * @param os OutputStream The output stream containing the data copied from the input stream
	 * @param bufferSize int size in byte of the buffer
	 * @throws IOException thrown when either streams are null, when there are exception accessing the streams or when the buffer size is less than 1
	 * @return The number of bytes in the output.
	 */
	public static long streamToStream(InputStream is, OutputStream os, int bufferSize) throws IOException {
		int iLen = 0;

		long bytesStreamed = 0;

		if (is == null) {
			throw new IOException("Input Stream is null");
		}
		if (os == null) {
			throw new IOException("Output Stream is null");
		}

		if (bufferSize < 1) {
			throw new IOException("Invalid buffer size " + bufferSize + ".  Buffer size must be at least 1 or greater");
		}

		try {

			byte[] baBuf = new byte[bufferSize];

			while ((iLen = is.read(baBuf)) >= 0) {
				os.write(baBuf, 0, iLen);
				bytesStreamed += iLen;
			}
		}
		finally {
			try {
				if (os != null) os.flush();
			}
			catch (Exception e) { /* Ignore */
			}
			try {
				if (os != null) os.close();
			}
			catch (Exception e) { /* Ignore */
			}

			try {
				if (is != null) is.close();
			}
			catch (Exception e) { /* Ignore */
			}
		}
		return bytesStreamed;
	}

	/**
	 * Reads the input stream and writes the content to the output stream, leaving the OutputStream in tact WITHOUT CLOSING IT
	 * @param is InputStream The input stream
	 * @param os OutputStream The output stream
	 * @throws IOException
	 * @return The number of bytes in the output.
	 */
	public static long copyStream(InputStream is, OutputStream os) throws IOException {
		int iLen = 0;
		long bytesStreamed = 0L;
		byte[] baBuf = new byte[DEFAULT_BUFFER_SIZE];


		// DO NOT CLOSE THE STREAM HERE - THIS IS BY DESIGN
		while ((iLen = is.read(baBuf)) >= 0) {
			bytesStreamed += iLen;
			os.write(baBuf, 0, iLen);
		}
		os.flush();

		return bytesStreamed;
	}

	/**
	 * Reads the input stream and writes the content to the output stream as well as closing the output stream upon method exit.
	 * This method calls {@link #streamToStream( InputStream is, OutputStream os, int bufferSize )} using the 
	 * default buffer size.
	 * @param is InputStream The input stream
	 * @param os OutputStream The output stream
	 * @throws IOException
	 * @see #streamToStream( InputStream is, OutputStream os, int bufferSize )
	 * @see #copyStream( InputStream is, OutputStream os);
	 * @return The number of bytes in the output.
	 */
	public static long streamToStream(InputStream is, OutputStream os) throws IOException {
		return streamToStream(is, os, DEFAULT_BUFFER_SIZE);
	}


	/**
	 * This method takes an underlying InputStream and returns a new byte array
	 * formed from the contents of the stream
	 * @param in InputStream to read from
	 * @return the byte array
	 * @throws IOException
	 * @see #streamToBytes(InputStream in, int bufSize, int byteArraySizeEst)
	 */
	public static byte[] streamToBytes(InputStream in) throws IOException {
		return streamToBytes(in, DEFAULT_BUFFER_SIZE, DEFAULT_BUFFER_SIZE);
	}

	/**
	 * This method takes an underlying InputStream and returns a new byte array
	 * @param in InputStream to read from
	 * @param bufSize size of the read buf
	 * @param byteArraySizeEst inital size of the byte array. This array will grow if need
	 * @return the byte array
	 * @throws IOException 
	 */
	public static byte[] streamToBytes(InputStream in, int bufSize, int byteArraySizeEst) throws IOException {

		ByteArrayOutputStream baos = null;
		byte[] baRet = null;

		if (in == null) {
			throw new IOException("Source InputStream is null");
		}

		// build the buffer and the BAOS
		baos = new ByteArrayOutputStream(byteArraySizeEst);

		streamToStream(in, baos, bufSize);
		baRet = baos.toByteArray();

		return baRet;
	}


	/**
	 * Converts a byte array to an Input Stream
	 * @param bytes source bytes
	 * @return InputStream an InputStream backed by ByteArrayInputStream
	 * @throws IOException
	 */
	public static InputStream bytesToStream(byte[] bytes) throws IOException {
		if (bytes == null) {
			throw new IOException("Source byte array is null");
		}

		ByteArrayInputStream in = new ByteArrayInputStream(bytes);
		return in;
	}

	/**
	 * Default size of the buffer when copying data from one stream to another stream
	 */
	public static final int DEFAULT_BUFFER_SIZE = 8192;


}