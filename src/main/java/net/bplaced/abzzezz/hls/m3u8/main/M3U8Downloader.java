package net.bplaced.abzzezz.hls.m3u8.main;


import net.bplaced.abzzezz.hls.m3u8.util.Crypto;
import net.bplaced.abzzezz.hls.m3u8.util.LineType;
import net.bplaced.abzzezz.hls.m3u8.util.Node;
import net.bplaced.abzzezz.hls.m3u8.util.URLUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

/**
 * "Original" Created by @author Yan ( https://blog.csdn.net/blueboyhi/article/details/40107683)
 * Remains of this code can be found in the
 * <p>
 * all the other code has been changed for the better, I searched long for a program like this but unfortunately something like this is nowhere to be found
 * this m3u8 downloader collects all the m3u8 nodes and gets all ts files
 */
public class M3U8Downloader {

    public static final String KEY = "#EXT-X-KEY";

    private final File m3u8WorkDir;
    private final File outDir;

    private final String fileName, masterURL;

    private final String[][] headers; //Request headers when grabbing files

    private final List<String> tsURLs = new ArrayList<>(); //List of all ts-files to download & merge
    private final List<String> visitedNodes = new ArrayList<>(); //List of visited m3u8 nodes, so they can be ignored and the algorithm does not loop infinitely


    private Crypto crypto;

    public M3U8Downloader(final String fileName, final String masterURL, final File workRootDir0, final String[]... headers) {
        this.fileName = fileName;
        this.masterURL = masterURL;
        this.headers = headers;

        File workRootDir = new File(workRootDir0, fileName);
        if (!workRootDir.exists()) workRootDir.mkdirs();

        this.m3u8WorkDir = new File(workRootDir, "m3u8");
        if (!m3u8WorkDir.exists()) m3u8WorkDir.mkdirs();

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
        final String fileName = masterURL.substring(lastSlash + 1, m3u8Extension);

        final File m3u8File = new File(m3u8WorkDir, fileName + ".m3u8");

        if (!m3u8File.exists()) {
            m3u8File.createNewFile();
        }

        this.processMaster(masterURL); //Start looking for nodes
        this.loadTS(); //Load ts
    }

    private void processMaster(final String url) throws Exception {
        final String baseURL = getBaseURL(url); //Base master url
        final String[] content = URLUtil.collectArray(URLUtil.createHTTPSURLConnection(masterURL, headers)); //Content of master
        final Node n0 = new Node(url, null); //N0 node (root)

        this.crypto = new Crypto(baseURL); //Initialize crypto

        visitedNodes.add(n0.getContent()); //add to visited node

        for (final String line : content) {
            System.out.println("Checking line from master");

            if (line.startsWith(KEY)) { //Update key
                crypto.updateKeyString(line);
            } else if (!line.isEmpty() && !line.startsWith("#")) { //process line if not empty
                final Node nNext = new Node(line, n0);
                if (!visitedNodes.contains(nNext.getContent()))
                    processNode(nNext);
            }
        }
    }

    private void processNode(final Node node) throws Exception {
        final String url = node.getContent();
        final String[] content = URLUtil.collectArray(URLUtil.createHTTPSURLConnection(url, headers));
        final String baseURL = getBaseURL(url);

        for (final String line : content) {
            if (line.startsWith("#")) continue; //skip instructions

            final LineType lineType = checkLine(line.replaceAll("[\r\n]+", "")); //Determine the line type
            switch (lineType) {
                case TS -> {
                    final String tsURL = baseURL + line;
                    if (!tsURLs.contains(tsURL)) {
                        tsURLs.add(tsURL);
                        System.out.println("Adding segment: " + tsURL);
                    }
                }
                case M3U8 -> {
                    final Node n1 = new Node(line, node);
                    if (!visitedNodes.contains(n1.getContent())) {
                        processNode(n1);
                    }
                }
            }
        }

        visitedNodes.add(node.getContent()); //Add to visited notes
        System.out.printf("Node %s was visited falling back to previous%n", node.getContent());

        //Visit parents
        if (node.getPrevious() != null) //go to previous
            processNode(node.getPrevious());
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

    private String getBaseURL(final String url) {
        return url.substring(0, url.lastIndexOf("/") + 1);
    }

    /**
     * Loads all the ts-files and downloads them
     *
     * @throws Exception url / file
     */
    private void loadTS() throws Exception {
        //Load ts files...
        System.out.println("Downloading & merging ts-files");
        final byte[] buffer = new byte[512]; //Buffer

        final File outputFile = new File(outDir, fileName + ".mp4");

        if (!outputFile.exists()) {
            try {
                outputFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try (final FileOutputStream fileOutputStream = new FileOutputStream(outputFile)) {
            for (int i = 0; i < tsURLs.size(); i++) { //loop through all ts-files
                System.out.printf("Writing %d out of %d%n", i + 1, tsURLs.size());

                final URLConnection connection = URLUtil.createHTTPURLConnection(tsURLs.get(i), 0, 0, headers);
                InputStream inputStream = crypto.hasKey() ?
                        crypto.wrapInputStream(connection.getInputStream()) :
                        connection.getInputStream();
                //Read the input stream & write to file output stream
                int read;
                while ((read = inputStream.read(buffer)) >= 0) {
                    fileOutputStream.write(buffer, 0, read);
                }
                inputStream.close();
                fileOutputStream.flush();
                System.out.printf("Done writing %d out of %d%n", i + 1, tsURLs.size());
            }
        }
        System.out.println("Done merging");
    }
}
