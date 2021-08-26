package net.bplaced.abzzezz.hls.m3u8.main;


import net.bplaced.abzzezz.hls.common.util.FileUtil;
import net.bplaced.abzzezz.hls.m3u8.util.HttpsUtils;
import net.bplaced.abzzezz.hls.m3u8.util.LineType;
import net.bplaced.abzzezz.hls.m3u8.util.URLUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * "Original" Created by @author Yan ( https://blog.csdn.net/blueboyhi/article/details/40107683)
 * Remains of this code can be found in the
 *
 * @see HttpsUtils
 * all the other code has been changed for the better, I searched long for a program like this but unfortunately something like this is nowhere to be found
 * this m3u8 downloader collects all the m3u8 nodes and gets all ts files
 */
public class M3U8Downloader {

    private final File workRootDir;
    private final File tsWorkDir;
    private final File m3u8WorkDir;
    private final File outDir;

    private final String fileName, masterURL;

    private final String[][] headers; //Request headers when grabbing files

    private final List<String> tsFiles = new ArrayList<>(); //List of all ts-files to download & merge
    private final List<String> visitedM3U8 = new ArrayList<>(); //List of visited m3u8 nodes, so they can be ignored and the algorithm does not loop infinitely


    public M3U8Downloader(final String fileName, final String masterURL, final File workRootDir0, final String[]... headers) {
        this.fileName = fileName;
        this.masterURL = masterURL;
        this.headers = headers;

        this.workRootDir = new File(workRootDir0, fileName);
        if (!workRootDir.exists()) workRootDir.mkdirs();

        this.m3u8WorkDir = new File(workRootDir, "m3u8");
        if (!m3u8WorkDir.exists()) m3u8WorkDir.mkdirs();

        this.tsWorkDir = new File(workRootDir, "ts");
        if (!tsWorkDir.exists()) tsWorkDir.mkdirs();

        this.outDir = new File(workRootDir, "mp4");
        if (!outDir.exists()) outDir.mkdirs();
    }

    /**
     * Downloads the initial master-file
     *
     * @throws Exception new file creation / file download
     */
    public void downloadM3U8() throws Exception {
        final int lastSlash = masterURL.lastIndexOf("/");
        final int m3u8Extension = masterURL.lastIndexOf(".m3u8");
        final String urlPre = masterURL.substring(0, lastSlash + 1);
        final String fileName = masterURL.substring(lastSlash + 1, m3u8Extension);

        final String master = HttpsUtils.get(masterURL, headers);

        final File m3u8File = new File(m3u8WorkDir, fileName + ".m3u8");

        if (!m3u8File.exists()) {
            m3u8File.createNewFile();
        }

        processM3U8(masterURL, null); //Start looking for nodes
    }

    /**
     * Loads all the ts-files and downloads them
     * @throws Exception url / file
     */
    private void loadTS() throws Exception {
        //Load ts files...
        System.out.println("Downloading ts-files");

        for (int i = 0; i < tsFiles.size(); i++) {
            System.out.printf("Downloading %d out of %d%n", i + 1, tsFiles.size());
            final File tsFile = new File(tsWorkDir, i + ".ts");
            if (tsFile.exists()) continue;

            FileUtil.writeToFile(tsFile, URLUtil.collectLines(URLUtil.createHTTPURLConnection(tsFiles.get(i), 0, 0, headers), "\n"), StandardCharsets.UTF_8);
        }

        mergeTsFiles();
    }

    private void processM3U8(final String url, final String previousNode) throws Exception {
        final String m3u8 = HttpsUtils.get(url, headers); //Get m3u8

        final int lastSlash = url.lastIndexOf("/");
        final String fileName = url.substring(lastSlash + 1);

        FileUtil.writeToFile(new File(m3u8WorkDir, fileName), m3u8, StandardCharsets.UTF_8);

        System.out.println("M3U8 Saved:" + url);

        final String[] lines = m3u8.split("[\\r\\n]"); //Split into individual lines
        //Loop through all lines, skip m3u8 instructions
        for (String line : lines) {
            if (line.startsWith("#")) continue;

            final LineType lineType = checkLine(line.replaceAll("[\r\n]+", "")); //Determine the line type
            switch (lineType) {
                case TS -> { //If the line has a "ts" file extension add the ts file to the arraylist
                    if (!tsFiles.contains(line)) {
                        final String baseURL = url.substring(0, url.lastIndexOf("/") + 1);
                        tsFiles.add(baseURL + line);
                        System.out.println("Adding ts file: " + line);
                    }
                }
                case M3U8 -> {
                    if (!visitedM3U8.contains(line))
                        processM3U8(line, url); //Process the next coming m3u8 file
                }
            }
        }
        //If the previous is not null visit the previous node, this is inefficient but will work for now
        //TODO: Work out more efficient method
        visitedM3U8.add(url);
        if (previousNode != null)
            processM3U8(previousNode, null);
        else loadTS();
    }

    private LineType checkLine(String line) {
        final int lineEnd = line.lastIndexOf(".");
        final String extension = line.substring(lineEnd + 1);

        return switch (extension) {
            case "m3u8" -> LineType.M3U8;
            case "ts" -> LineType.TS;
            default -> LineType.UNKNOWN;
        };
    }

    /**
     * Merges all .ts files in the ts work directory
     */
    public void mergeTsFiles() {
        System.out.println("Merging ts-files");
        File mp4File = new File(outDir, fileName + ".mp4");

        if (!mp4File.exists()) {
            try {
                mp4File.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try (FileOutputStream fileOutputStream = new FileOutputStream(mp4File)) {
            Arrays.stream(Objects.requireNonNull(tsWorkDir.listFiles()))
                    .sorted()
                    .forEach(file -> {
                        System.out.println("Merging " + file.getName());

                        try (FileInputStream fileInputStream = new FileInputStream(file)) {
                            fileOutputStream.getChannel().transferFrom(Channels.newChannel(fileInputStream), 0, Long.MAX_VALUE);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("Done merging...");
    }

}
