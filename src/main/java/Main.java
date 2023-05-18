
import m3u8.M3U8Downloader;

import java.io.File;
import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException {

        final String url = "https://eerht.artdesigncdn.net/_v10/751c33863806cf63f9b11d9facb4d8bf2030ad6cbc1ea88d2005b5fb673ad4ce618f4e6da57e0b5a65575b33a4a0c91da6888e352bbef005e79eaea4b9ab3546211e5baed400146663044f400c244c89009a151fc20d2396c0fc38b8106161d14672c405ce4309ed543eb9faccc9d3e162304566b7677732c0c9d985857d6b2d/720/index.m3u8";
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
