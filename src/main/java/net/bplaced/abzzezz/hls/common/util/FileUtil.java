package net.bplaced.abzzezz.hls.common.util;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class FileUtil {

	/**
	 * Writes a string to a file, without appending
	 * @param file file to write to
	 * @param content content to write
	 * @param charset charset to use
	 */
	public static void writeToFile(File file, String content, Charset charset)  {
		try(final FileOutputStream fileOutputStream = new FileOutputStream(file)) {
			fileOutputStream.write(content.getBytes(charset));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
