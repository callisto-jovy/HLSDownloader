package m3u8.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.charset.Charset;

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

	public static void writeToFile(final URLConnection in, final File out) {
		try(final FileOutputStream fos = new FileOutputStream(out)) {
			fos.getChannel().transferFrom(Channels.newChannel(in.getInputStream()), 0, Long.MAX_VALUE);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
