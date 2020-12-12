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
 * @mail gitsilence@lacknb.cn
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
