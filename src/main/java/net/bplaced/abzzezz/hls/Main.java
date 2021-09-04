package net.bplaced.abzzezz.hls;

import net.bplaced.abzzezz.hls.m3u8.main.M3U8Downloader;

import java.io.File;

public class Main {

    public static void main(String[] args) {
        final String url = "https://s75.upstreamcdn.co/hls/,w47rycopqjbnx2nroy4kcuv5bp7wqmqcmmb35tauo5p74jmyrhq376ubtz6a,.urlset/master.m3u8";
        final File outDir = new File(System.getProperty("user.home"), "merger");
        if (!outDir.exists()) outDir.mkdir();

        final M3U8Downloader downloader = new M3U8Downloader("out.mp4",
                url,
                outDir,
                new String[]{"user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.159 Safari/537.36"},
                new String[] {"referer", "https://upstream.to/embed-cv439qeqv3s4.html"});
        try {
            downloader.downloadM3U8();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
