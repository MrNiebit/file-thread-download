package cn.lacknb.test.threaddownload;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @Description: 下载teambition 的网盘直链。
 * @author gitsilence
 * @version 1.0
 * @date 2020/12/10 19:52
 * @mail gitsilence@lacknb.cn
 */
public class MainDownload {

    public static void main(String[] args) {
        try {
            String downloadUrl = "https://data-hz-pds.teambition.net/5fccd7c403ca7a0ca34e456189672403e98832a3%2F5fccd7c43bd9aea8576b478693fd7885b1af775a?response-content-disposition=attachment%3B%20filename%2A%3DUTF-8%27%27%25E9%25BE%2599%25E7%258F%25A0%2520%25EF%25BC%259A%25E7%25AC%25AC101%25E9%259B%2586%2520%25E6%25AD%25A6%25E9%2581%2593%25E4%25BC%259A%25E7%25BB%2593%25E6%259D%259F.mp4&x-oss-access-key-id=LTAIsE5mAn2F493Q&x-oss-expires=1607754425&x-oss-signature=pFgGtR1LcdhJkgaXuwb4WCzkoUdFT5lArWXuJNvcRmg%3D&x-oss-signature-version=OSS2";
            File file = new File(FileDownload.PATH);
            if (!file.exists()) {
                file.mkdirs();
            }
            URL url = new URL(downloadUrl);
            URLConnection conn = url.openConnection();
            // b -> kb > mb
            int contentLength = conn.getContentLength();
            Map<String, List<String>> headerFields = conn.getHeaderFields();
//            for (String key : headerFields.keySet()) {
//                System.out.println(key + "  =====  " + headerFields.get(key));
//            }
            List<String> strings = headerFields.get("Content-Disposition");
            String[] split = strings.get(0).split("''");
            String filename = split[split.length - 1];
            filename = URLDecoder.decode(filename);
            System.out.println(filename);
//            RandomAccessFile file = new RandomAccessFile("G:\\work\\test.mp4", "rw");
//            file.setLength(contentLength);
//            file.close();
            System.out.println("total mb size: " + StaticConstant.transMb(contentLength));
            int concurrentThreadSize = 6;  // 设置6个线程同时下载
            int threadFileSize = contentLength / concurrentThreadSize + 1;  // 每个线程块所负责的大小
            ExecutorService executorService = Executors.newFixedThreadPool(concurrentThreadSize);
            for (int i = 0; i < concurrentThreadSize; i++) {
//                FileDownload fileDownload = new FileDownload(i * threadFileSize, threadFileSize, downloadUrl, filename);
//                new Thread(fileDownload).start();
                executorService.execute(new FileDownload(i * threadFileSize, threadFileSize, downloadUrl, filename));
            }
            executorService.shutdown();


        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}

class StaticConstant {
    public static String transMb (long size) {
        long mb = size / 1024 / 1024;
        return String.valueOf(mb) + " MB";
    }
}

class FileDownload implements Runnable {

    public static final String PATH = "G:\\work\\";
    private int order;  // 所负责 文件块的第一个下标
    private RandomAccessFile randomAccessFile;  // 所负责的文件
    private int fileSize;  // 所负责的总大小
    String url;
    private int nowFileSize = 0;  // 当前所完成的大小。
    private String filename;

    public FileDownload() {
    }

    public FileDownload(int order, int fileSize, String url, String filename) {
        this.order = order;
        this.url = url;
        this.fileSize = fileSize;
        this.filename = filename;
    }

    @Override
    public void run() {
        try {
            URL url = new URL(this.url);
            System.out.println(Thread.currentThread().getName() + "正在运行....");
            URLConnection urlConnection = url.openConnection();
            urlConnection.setConnectTimeout(1000);
            InputStream inputStream = urlConnection.getInputStream();
            inputStream.skip(order);
            randomAccessFile = new RandomAccessFile(PATH + this.filename, "rw") ;
            randomAccessFile.seek(order);
            byte[] buffer = new byte[1024];
            int hasRead = 0;
            while (nowFileSize < fileSize && (hasRead = inputStream.read(buffer)) != -1) {
                randomAccessFile.write(buffer, 0, hasRead);
                nowFileSize += hasRead;
                System.out.println(String.format("%s ======= 还剩下 %s 没下载完", Thread.currentThread().getName(),
                        StaticConstant.transMb(fileSize - nowFileSize)));
            }
            randomAccessFile.close();
            inputStream.close();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            System.out.println(Thread.currentThread().getName() + " ===== 下载完毕。");
        }

    }
}
