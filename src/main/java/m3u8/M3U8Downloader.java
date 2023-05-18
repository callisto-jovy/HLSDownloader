package m3u8;


import m3u8.util.*;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

public class M3U8Downloader {

    public static final String KEY = "#EXT-X-KEY";

    private final File m3u8WorkDir;
    private final File outDir;

    private final File masterFile;
    private final File outputFile;

    private final String masterURL;

    private final String[][] headers; //Request headers when grabbing files

    private final List<String> tsURLs = new ArrayList<>(); //List of all ts-files to download & merge
    private final List<String> visitedNodes = new ArrayList<>(); //List of visited m3u8 nodes, so they can be ignored and the algorithm does not loop infinitely

    private Crypto crypto;

    public M3U8Downloader(final String fileName, final String masterURL, final File workRootDir0, final String[]... headers) {
        this.masterURL = masterURL;
        this.headers = headers;

        final File workRootDir = new File(workRootDir0, fileName);
        if (!workRootDir.exists()) workRootDir.mkdirs();

        this.m3u8WorkDir = new File(workRootDir, "m3u8");
        if (!m3u8WorkDir.exists()) m3u8WorkDir.mkdirs();

        this.outDir = new File(workRootDir, "out");
        if (!outDir.exists()) outDir.mkdirs();

        this.masterFile = new File(m3u8WorkDir, "index.m3u8");
        this.outputFile = new File(outDir, fileName + ".mp4");
    }

    /**
     * Chains the process together
     *
     * @throws Exception ioexceptions
     */
    public void downloadM3U8() throws Exception {
        this.grabMaster(); // Grav
        this.processMaster(); //Start looking for nodes
        this.loadTS(); //Load ts
        this.mergeFiles();
        this.deleteFiles();
    }

    /**
     * Downloads the master (playlist) from the supplied url using nio
     *
     * @throws IOException IO Error
     */
    private void grabMaster() throws IOException {
        FileUtil.writeToFile(URLUtil.createHTTPSURLConnection(masterURL, headers), masterFile);
    }

    /**
     * Processes the master
     *
     * @throws Exception
     */
    private void processMaster() throws Exception {
        final String baseURL = getBaseURL(masterURL); //Base master url
        this.crypto = new Crypto(baseURL); //Initialize crypto

        final String[] content = URLUtil.collectArray(masterFile.toURI().toURL()); //Content of master

        final Node root = new Node(masterURL, null); //N0 node (root)
        this.visitedNodes.add(root.getContent()); //add to visited node

        //update the key
        for (final String line : content) {
            if (line.startsWith(KEY)) { //Update key
                crypto.updateKeyString(line);
            }
        }
        //Start node processing
        this.processNode(root);
    }

    private void processNode(final Node node) throws Exception {
        final String url = node.getContent();

        final String[] content = URLUtil.collectArray(URLUtil.createHTTPSURLConnection(url, headers));
        final String baseURL = getBaseURL(url);

        for (final String line : content) {
            if (line.startsWith("#")) continue; //skip instructions

            final LineType lineType = checkLine(line.replaceAll("[\r\n]+", "")); //Determine the line type
            switch (lineType) {
                //New possible file. TODO: Identify non-files
                case TS, UNKNOWN -> {
                    final String segmentUrl = baseURL + line;
                    //TODO: optimize
                    if (!tsURLs.contains(segmentUrl)) {
                        tsURLs.add(segmentUrl);
                        System.out.println("Adding segment: " + segmentUrl);
                    }
                }
                case M3U8 -> {
                    final Node n = new Node(line, node);
                    if (!visitedNodes.contains(n.getContent())) {
                        processNode(n);
                    }
                }
            }
        }

        visitedNodes.add(node.getContent()); //Add to visited notes
        System.out.printf("Node %s was visited falling back to previous%n", node.getContent());

        //Return to parent
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

    private String getFileName(final String url) {
        return url.substring(url.lastIndexOf("/") + 1);
    }

    /**
     * Loads all the ts-files and downloads them
     */
    private void loadTS() throws ExecutionException {
        //Load ts files...
        System.out.println("Downloading & merging ts-files");
        //Create outputfile
        if (!outputFile.exists()) {
            try {
                outputFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        final ExecutorService pool = Executors.newFixedThreadPool(20);

        final List<Future<?>> futures = new ArrayList<>();

        for (final String tsURL : tsURLs) {
            final File file = new File(m3u8WorkDir, getFileName(tsURL));
            futures.add(pool.submit(new DownloadTask(crypto, tsURL, file, headers)));
        }

        for (final Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        System.out.println("Done downloading.");
    }

    public void mergeFiles() throws IOException {
        System.out.println("Starting to merge the files.");
        //Read master file and get segments
        final File[] files = URLUtil.collectList(masterFile.toURI().toURL())
                .stream()
                .filter(s -> !s.startsWith("#") && !s.isEmpty())
                .map(s -> new File(m3u8WorkDir, s))
                .toArray(File[]::new);

        //TODO: Image proportions
        //Grab the first element
        final FFmpegFrameGrabber sampleGrabber = new FFmpegFrameGrabber(files[0]);
        sampleGrabber.setOption("allowed_extensions", "ALL");
        sampleGrabber.start();


        final FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputFile, sampleGrabber.getImageWidth(), sampleGrabber.getImageHeight(), sampleGrabber.getAudioChannels());
        recorder.setFormat("mp4");
        recorder.setAudioCodec(sampleGrabber.getAudioCodec());
        recorder.setVideoCodec(sampleGrabber.getVideoCodec());
        recorder.setFrameRate(sampleGrabber.getFrameRate());
        recorder.setVideoBitrate(40 * 1024 * 1024);
        recorder.start();

        sampleGrabber.stop();
        sampleGrabber.release();

        for (final File segmentFile : files) {
            try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(segmentFile)) {
                grabber.setOption("allowed_extensions", "ALL");
                grabber.start();

                Frame frame;
                while ((frame = grabber.grabFrame()) != null) {
                    recorder.record(frame);
                }
                grabber.stop();
                grabber.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        recorder.stop();
        recorder.release();

        System.out.println("Done merging!");
    }

    public void deleteFiles() {
        try (Stream<Path> stream = Files.walk(m3u8WorkDir.toPath())) {
            stream.filter(Files::isRegularFile)
                    .map(Path::toFile).
                    forEach(File::delete);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
