package m3u8.util;

import m3u8.util.Crypto;
import m3u8.util.URLUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;

public class DownloadTask implements Runnable {
    private final String url;
    private final Crypto crypto;
    private final File file;
    private final String[][] headers;

    public DownloadTask(final Crypto crypto, final String url, final File file, final String[][] headers1) {
        this.url = url;
        this.crypto = crypto;
        this.file = file;
        this.headers = headers1;
    }


    @Override
    public void run() {
        System.out.println("Staring download on new thread.");
        final byte[] buffer = new byte[512]; //Buffer

        try (final FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            final URLConnection connection = URLUtil.createHTTPURLConnection(url, 0, 0, headers);
            final InputStream inputStream = crypto.hasKey() ?
                    crypto.wrapInputStream(connection.getInputStream()) :
                    connection.getInputStream();
            //Read the input stream & write to file output stream
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                fileOutputStream.write(buffer, 0, read);
            }
            inputStream.close();
            fileOutputStream.flush();
            System.out.println("Downloaded " + url);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
