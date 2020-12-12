# 使用java线程池批量下载m3u8合并mp4.

看了线程池的demo，然后就想下载文件试试。

代码未必规范，多多建议。

大家可以自行修改，满足自己的需求。

还需要深入学习一下线程池。

## m3u8 链接下载

> 下载m3u8文件，找到内部的链接
> 下载内部的m3u8，提取所有的ts链接
> 线程池批量下载。

当前针对于 ok、zuida 资源 进行下载测试。

运行步骤：
1. 将ffmpeg 和 当前代码放在同一目录
2. 更换M3U8_URL的地址（下载啥就放啥）
3. 文件路径

然后直接运行就ok 了。

M3u8Download.java
```java
package cn.lacknb.test.threaddownload;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * @description: 下载 ok 资源网、最大资源网的  m3u8连接
 * @author gitsilence
 * @version 1.0
 * @date 2020/12/11 14:59
 */
public class M3u8Download {

    /*
    * m3u8地址
    * */

    private static final String M3U8_URL = "https://yuledy.helanzuida.com/20201204/15904_c1fcf83a/index.m3u8";

    /*
    * 视频的下载路径
    * */

    public static final String DOWNLOAD_PATH = "G:\\work\\video\\";

    /*
     * m3u8文件的路径
     * */

    private static final String FILE_PATH = DOWNLOAD_PATH + "tmp.m3u8";

    /*
    * 临时文件的文件名
    * */

    private static final String TMP_NAME = "tmp.txt";


    public static void main(String[] args) {

        try {
            // 创建父级文件夹
            createParentDirs();
            // 下载外层m3u8 url
            downloadFile(M3U8_URL);
            String content = readFile(FILE_PATH);
            System.out.println(content);
            String realUrl = M3U8_URL.split("index")[0] + content;
            System.out.println("realUrl: " + realUrl);
            // 下载内层m3u8 url
            downloadFile(realUrl);
            content = readFile(FILE_PATH);
            // 获取所有的ts， 并合并 url
            List<String> urls = mergeUrl(content, realUrl);
            // 生成tmp.txt, 用于合并视频
            createMergeVideoTmp(content);
//            urls.forEach(System.out::println);
            ExecutorService executorService = Executors.newFixedThreadPool(12);
            urls.forEach( url -> {
                executorService.execute(new TsDownload(url));
            });
//            // 关闭线程池，等待任务结束。
            executorService.shutdown();
            while (!executorService.isTerminated()) {
                System.out.println("任务下载中");
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            // 任务下载完毕后
            mergeVideo(content, UUID.randomUUID().toString());
            // 删除ts文件
            deleteTsFile(content);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    /**
     * corePoolSize：核心池的大小；当线程池中的线程数目达到corePoolSize后，就会把到达的任务放到缓存队列当中 =10
     * maximumPoolSize：线程池最大线程数 =16
     * keepAliveTime：表示线程没有任务执行时最多保持多久时间会终止 =1
     * unit：参数keepAliveTime的时间单位 =秒
     * workQueue：一个阻塞队列，用来存储等待执行的任务
     */


    public static void downloadFile (String m3u8Url) throws IOException {
        System.out.println("下载地址：" + m3u8Url);
        URL url = new URL(m3u8Url);
        URLConnection conn = url.openConnection();
        File f = new File(FILE_PATH);
        if (f.exists()) {
            f.delete();
        }

        RandomAccessFile file = new RandomAccessFile(FILE_PATH, "rw");
        InputStream inputStream = conn.getInputStream();

        byte[] buffer = new byte[1024];
        int hasRead = 0;
        while ((hasRead = inputStream.read(buffer)) != -1) {
            file.write(buffer, 0, hasRead);
        }
        file.close();
        inputStream.close();
    }

    public static String readFile (String path) {
        System.out.println("读取文件地址：" + path);
        StringBuilder builder = new StringBuilder();
        try {
            RandomAccessFile randomAccessFile = new RandomAccessFile(path, "r");
            String content = null;
            while ((content = randomAccessFile.readLine()) != null) {
                if (!content.startsWith("#")) {
                    builder.append(content + "\n");
                }
            }
            randomAccessFile.close();
            return builder.toString();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return builder.toString();
    }

    /**
     *
     * @param content 很多的xxxxx.ts，以\n分割
     * @param realUrl 真实的m3u8地址 只是为了 取它的base url。为了拼接完整的url
     * @return
     */
    public static List<String> mergeUrl(String content, String realUrl) {
        String baseUrl = realUrl.split("index")[0];
        String[] split = content.split("\n");
        List<String> urls = new ArrayList<>();
        for (String str : split) {
            urls.add(baseUrl + str);
        }
        return urls;
    }

    /**
     *
     * @param content 很多的xxxxx.ts，以\n分割
     */
    public static void createMergeVideoTmp (String content) {
        String[] tsNames = content.split("\n");
        File file = new File(M3u8Download.DOWNLOAD_PATH + TMP_NAME);
        if (file.exists()) {
            file.delete();
        }
        try {
            RandomAccessFile randomAccessFile = new RandomAccessFile(M3u8Download.DOWNLOAD_PATH + TMP_NAME, "rw");
            for (String tsName : tsNames) {
                randomAccessFile.write(("file '" + tsName + "'\n").getBytes());
            }
            randomAccessFile.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 执行cmd命令时，容易卡死，下面这种方式 正好解决办法
     * https://blog.csdn.net/myloveheqiaozhi/article/details/51451285
     * @param content 很多的xxxxx.ts，以\n分割
     * @param filename 合并之后，视频的名字
     */
    public static void mergeVideo (String content, String filename) {
        System.out.println("开始合并视频....");
        File file = new File(M3u8Download.DOWNLOAD_PATH + TMP_NAME);
        if (!file.exists()) {
            createMergeVideoTmp(content);
        }
        String path = M3u8Download.DOWNLOAD_PATH.replaceAll("\\\\", "/");
        // 合并视频的命令
        try {
            Process process = Runtime.getRuntime().exec(String.format("ffmpeg.exe -f concat -safe 0 -i %s -c copy %s" +
                            ".mp4",
                    path + TMP_NAME, path + filename));

            final InputStream is1 = process.getInputStream();
            new Thread(new Runnable(){
                @Override
                public void run() {
                    BufferedReader br = new BufferedReader(new InputStreamReader(is1));
                    try {
                        String outputLine = null;
                        while((outputLine=br.readLine())!= null) {
                            System.out.println(outputLine);
                        }

                    }catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
            InputStream is2 = process.getErrorStream();
            BufferedReader br2 = new BufferedReader(new InputStreamReader(is2));
            StringBuilder buf = new StringBuilder();
            String line = null;
            while((line = br2.readLine()) != null) {
                System.out.println(line);
                buf.append(line);
            };
            System.out.println("result:" + buf);
            while (br2.readLine() != null) {
                System.out.println(br2);
            }
            try {
                process.waitFor();
            }catch (InterruptedException e){
                e.printStackTrace();
            }
            int i = process.exitValue();
            System.out.println( process.exitValue());

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            file.delete();
        }
    }

    /**
     * 删除ts文件
     * @param content
     */
    public static void deleteTsFile (String content) {
        String[] tsNames = content.split("\n");
        for (int i = 0; i < tsNames.length; i++) {
            System.out.println(DOWNLOAD_PATH + tsNames[i]);
            File file = new File(DOWNLOAD_PATH, tsNames[i]);
            if (file.exists()) {
                System.out.println("删除成功  文件 =====> " + tsNames[i]);
                file.delete();
            }
        }
    }


    public static void createParentDirs () {
        File file = new File(DOWNLOAD_PATH);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

}

/**
 * Ts 多线程下载类。
 */
class TsDownload implements Runnable {
    private String downloadUrl;
    private String filename;

    public TsDownload () {

    }

    public TsDownload(String downloadUrl) {
        this.downloadUrl = downloadUrl;
        String[] split = downloadUrl.split("/");
        this.filename = split[split.length - 1];
    }

    @Override
    public void run() {
        URL url = null;
        try {
            url = new URL(this.downloadUrl);
            System.out.println(Thread.currentThread().getName() + " 正在下载 " + this.filename);
            URLConnection conn = url.openConnection();
            RandomAccessFile file = new RandomAccessFile(M3u8Download.DOWNLOAD_PATH + this.filename, "rw");
            InputStream inputStream = conn.getInputStream();

            byte[] buffer = new byte[1024];
            int hasRead = 0;
            while ((hasRead = inputStream.read(buffer)) != -1) {
                file.write(buffer, 0, hasRead);
            }
            file.close();
            inputStream.close();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

```


## teambition网盘的直链下载

这个demo，在网上找的下载，照着改了改。

链接有是效性。
MainDownload.java

```java
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

```


![rVAWE4.png](https://s3.ax1x.com/2020/12/12/rVAWE4.png)