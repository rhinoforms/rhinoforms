package com.rhinoforms.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamUtils {

	public void copyInputStreamToOutputStream(InputStream inputStream, OutputStream outputStream) throws IOException {
		byte[] buf = new byte[1024];
		int len;
		while ((len = inputStream.read(buf)) != -1) {
			outputStream.write(buf, 0, len);
		}
		outputStream.flush();
	}

	public void copyInputStreamToOutputStreamAndCloseBoth(InputStream inputStream, OutputStream outputStream) throws IOException {
		try {
			copyInputStreamToOutputStream(inputStream, outputStream);
		} finally {
			inputStream.close();
			outputStream.close();
		}
	}

	public byte[] readStream(InputStream inputStream) throws IOException {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		copyInputStreamToOutputStreamAndCloseBoth(inputStream, outputStream);
		return outputStream.toByteArray();
	}
	
}
