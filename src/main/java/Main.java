
import m3u8.M3U8Downloader;

import java.io.File;
import java.io.IOException;

public class Main {

    public static void main(String[] args) {

        final String url = "";
        final File outDir = new File("D:\\merger");
        if (!outDir.exists()) outDir.mkdir();

        final M3U8Downloader downloader = new M3U8Downloader("test",
                url,
                outDir,
                new String[]{"user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.159 Safari/537.36"}
        );
        try {
            downloader.downloadM3U8();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
