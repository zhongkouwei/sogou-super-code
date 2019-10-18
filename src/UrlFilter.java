// JavaOpt: -Xms13000m -Xmx13000m
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author gaoshuo
 * @date 2019-09-29
 */
public class UrlFilter {

    private static final Object lock = new Object();

    private static final BlockingQueue<String> URL_QUEUE = new LinkedBlockingQueue<>(1000);
    private static final BlockingQueue<String> OUT_QUEUE = new LinkedBlockingQueue<>();

    private static volatile boolean URL_END = false;
    private static volatile AtomicInteger DEAL_THREAD_NUM = new AtomicInteger(6);

    private static final AtomicInteger ALLOW_COUNT = new AtomicInteger(0);
    private static final AtomicInteger DISALLOW_COUNT = new AtomicInteger(0);
    private static final AtomicInteger NOHIT_COUNT = new AtomicInteger(0);
    private static final AtomicLong XOR_ALLOW_VALUE = new AtomicLong(0);
    private static final AtomicLong XOR_DISALLOW_VALUE = new AtomicLong(0);

    private static long startTime;

    public static void main(String[] args) throws InterruptedException{
        startTime = System.currentTimeMillis();

        // ************************ Load Rule **************************************//

        var domainFilter = new DomainFilter();
        Runnable domainRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    domainFilter.loadFromFile(args[0]);
                } catch (IOException e) {

                }
            }
        };
        Thread domainThread = new Thread(domainRunnable);

        var prefixFilter = new PrefixFilter();
        Runnable prefixRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    prefixFilter.loadFromFile(args[1]);
                } catch (IOException e) {

                }
            }
        };
        Thread prefixThread = new Thread(prefixRunnable);
        domainThread.start();
        prefixThread.start();
        domainThread.join();
        prefixThread.join();

        long loadTime = System.currentTimeMillis();

        // ************************ Load Url ****************************************//

        Thread printThread = new Thread(new PrintRunnable());
        int num = DEAL_THREAD_NUM.get();
        Thread[] dealThreads = new Thread[num];
        for (int i = 0; i < num ; i++) {
            Thread dealThread = new Thread(new DealRunnable(domainFilter, prefixFilter, printThread));
            dealThread.start();
            dealThreads[i] = dealThread;
        }
        Thread loadThread = new Thread(new LoadRunnable(args[2], dealThreads));
        loadThread.start();

        printThread.start();
        printThread.join();
        long dealTime = System.currentTimeMillis();

        System.out.println(ALLOW_COUNT);
        System.out.println(DISALLOW_COUNT);
        System.out.println(NOHIT_COUNT);
        System.out.format("%08x\n", XOR_ALLOW_VALUE.get());
        System.out.format("%08x\n", XOR_DISALLOW_VALUE.get());

        System.err.println("load: " + (loadTime - startTime) / 1000);
        System.err.println("deal: " + (dealTime - loadTime) / 1000);
        System.err.println("total: " + (System.currentTimeMillis() - startTime) / 1000);
    }

    /**
     * load url
     */
    static class LoadRunnable implements Runnable{

        private Thread[] dealThreads;
        private String fileName;

        public LoadRunnable(String fileName, Thread[] dealThreads) {
            this.fileName = fileName;
            this.dealThreads = dealThreads;
        }

        @Override
        public void run() {
            RandomAccessFile randomAccessFile = null;
            FileChannel channel = null;
            try {
                randomAccessFile = new RandomAccessFile(this.fileName, "r");
                channel = randomAccessFile.getChannel();
                ByteBuffer buffer = ByteBuffer.allocate(1 * 1024 * 1024);
                byte[] temp = new byte[0];
                int LF = "\n".getBytes()[0];
                while (channel.read(buffer) != -1) {
                    int position = buffer.position();
                    byte[] rbyte = new byte[position];
                    buffer.flip();
                    buffer.get(rbyte);
                    int startnum = 0;
                    for (int i = 0; i < rbyte.length; i++) {
                        if (rbyte[i] == LF) {
                            byte[] line = new byte[temp.length + i - startnum + 1];
                            System.arraycopy(temp, 0, line, 0, temp.length);
                            System.arraycopy(rbyte, startnum, line, temp.length, i - startnum + 1);
                            startnum = i + 1;
                            temp = new byte[0];
                            URL_QUEUE.put(new String(line));
                        }
                    }
                    if (startnum < rbyte.length) {
                        byte[] temp2 = new byte[temp.length + rbyte.length - startnum];
                        System.arraycopy(temp, 0, temp2, 0, temp.length);
                        System.arraycopy(rbyte, startnum, temp2, temp.length, rbyte.length - startnum);
                        temp = temp2;
                    }
                    buffer.clear();
                }
                if (temp.length > 0) {
                    URL_QUEUE.put(new String(temp));
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }finally {
                URL_END = true;
                try {
                    for (int i = 0; i < dealThreads.length ; i++) {
                        dealThreads[i].interrupt();
                    }
                    channel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Deal
     */
    static class DealRunnable implements Runnable{

        private DomainFilter domainFilter;

        private PrefixFilter prefixFilter;

        private Thread printThread;

        DealRunnable(DomainFilter domainFilter, PrefixFilter prefixFilter, Thread printThread) {
            this.domainFilter = domainFilter;
            this.prefixFilter = prefixFilter;
            this.printThread = printThread;
        }

        @Override
        public void run() {
            while (true) {
                if (URL_END && URL_QUEUE.size() == 0) {
                    int curThreadsNum = DEAL_THREAD_NUM.decrementAndGet();
                    if (curThreadsNum < 1) {
                        printThread.interrupt();
                    }
                    return;
                }
                String l = "";
                try {
                    l = URL_QUEUE.poll(100, TimeUnit.MILLISECONDS);
                    if (l == null) {
                        continue;
                    }
                } catch (InterruptedException e) {
                    if (URL_END && URL_QUEUE.size() == 0) {
                        int curThreadsNum = DEAL_THREAD_NUM.decrementAndGet();
                        if (curThreadsNum < 1) {
                            printThread.interrupt();
                        }
                        return;
                    } else {
                        continue;
                    }
                }
                if (l.isEmpty() || l.startsWith("#")) {
                    continue;
                }

                int i = l.indexOf('\t');
                var url = l.substring(0, i);
                var strValue = l.substring(i + 1, i + 9);
                var intValue = Long.parseLong(strValue, 16);

                var perm = -1;
                Url u;
                u = new Url(url);

                perm = domainFilter.match(u);
                if (perm != 1) {
                    var newPerm = prefixFilter.match(u);
                    if (newPerm > perm) {
                        perm = newPerm;
                    }
                }

                if (perm == 1) {
                    // -
                    DISALLOW_COUNT.incrementAndGet();
                    synchronized (lock) {
                        long oldValue = XOR_DISALLOW_VALUE.get();
                        long newValue = oldValue ^ intValue;
                        XOR_DISALLOW_VALUE.set(newValue);
                    }
                } else if (perm == 0) {
                    // +
                    ALLOW_COUNT.incrementAndGet();
                    synchronized (lock) {
                        long oldValue = XOR_ALLOW_VALUE.get();
                        long newValue = oldValue ^ intValue;
                        XOR_ALLOW_VALUE.set(newValue);
                    }
                    OUT_QUEUE.add(strValue);
                } else {
                    ALLOW_COUNT.incrementAndGet();
                    NOHIT_COUNT.incrementAndGet();
                    synchronized (lock) {
                        long oldValue = XOR_ALLOW_VALUE.get();
                        long newValue = oldValue ^ intValue;
                        XOR_ALLOW_VALUE.set(newValue);
                    }
                    OUT_QUEUE.add(strValue);
                }

            }
        }
    }

    /**
     * print thread
     */
    static class PrintRunnable implements Runnable {

        @Override
        public void run() {
            StringBuffer stringBuffer = new StringBuffer();
            int i = 0;
            while (true) {
                if (DEAL_THREAD_NUM.get() < 1 && OUT_QUEUE.size() == 0) {
                    if (stringBuffer.length() != 0) {
                        System.out.println(stringBuffer);
                    }
                    return;
                }
                String line = null;
                try {
                    line = OUT_QUEUE.poll(100, TimeUnit.MILLISECONDS);
                    if (line == null) {
                        continue;
                    }
                } catch (InterruptedException e) {
                    if (DEAL_THREAD_NUM.get() < 1 && OUT_QUEUE.size() == 0) {
                        if (stringBuffer.length() != 0) {
                            System.out.println(stringBuffer);
                        }
                        return;
                    } else {
                        continue;
                    }
                }
                if (stringBuffer.length() != 0) {
                    stringBuffer.append("\n");
                }
                stringBuffer.append(line);
                i ++;
                if (i == 100000) {
                    System.out.println(stringBuffer);
                    stringBuffer.setLength(0);
                    i = 0;
                }
            }
        }
    }

    /**
     * @author gaoshuo
     * @date 2019-09-29
     */
    static class Url {
        final String scheme;
        final String domain;
        final int port;
        final String path;
        final String url;

        public Url(String urlstr) {
            int p1, p2, p3;
            url = urlstr;
            p1 = urlstr.indexOf("://");
            p2 = urlstr.indexOf(":", p1+3);
            p3 = urlstr.indexOf("/", p1+3);
            this.scheme = urlstr.substring(0, p1);
            this.path = urlstr.substring(p3);
            if (p2 !=-1 && p2 < p3) {
                this.domain = urlstr.substring(p1+3, p2);
                this.port = Integer.parseInt(urlstr.substring(p2+1, p3));
            } else {
                this.domain = urlstr.substring(p1+3, p3);
                if (scheme.equals("http")) {
                    this.port = 80;
                } else if (scheme.equals("https")) {
                    this.port = 443;
                } else {
                    this.port = -1;
                }
            }
        }
    }

    /**
     * @author gaoshuo
     * @date 2019-10-14
     */
    static interface Filter {

        void load(String line);

        default void loadFromFile(String filename) throws IOException {
            RandomAccessFile randomAccessFile = null;
            FileChannel channel = null;
            try {
                randomAccessFile = new RandomAccessFile(filename, "r");
                channel = randomAccessFile.getChannel();
                ByteBuffer buffer = ByteBuffer.allocate(1 * 1024 * 1024);
                byte[] temp = new byte[0];
                int LF = "\n".getBytes()[0];
                while (channel.read(buffer) != -1) {
                    int position = buffer.position();
                    byte[] rbyte = new byte[position];
                    buffer.flip();
                    buffer.get(rbyte);
                    int startnum = 0;
                    for (int i = 0; i < rbyte.length; i++) {
                        if (rbyte[i] == LF) {
                            byte[] line = new byte[temp.length + i - startnum + 1];
                            System.arraycopy(temp, 0, line, 0, temp.length);
                            System.arraycopy(rbyte, startnum, line, temp.length, i - startnum + 1);
                            startnum = i + 1;
                            temp = new byte[0];
                            this.load(new String(line));
    //                        System.out.println(new String(line));
                        }
                    }
                    if (startnum < rbyte.length) {
                        byte[] temp2 = new byte[temp.length + rbyte.length - startnum];
                        System.arraycopy(temp, 0, temp2, 0, temp.length);
                        System.arraycopy(rbyte, startnum, temp2, temp.length, rbyte.length - startnum);
                        temp = temp2;
                    }
                    buffer.clear();
                }
                if (temp.length > 0) {
                    this.load(new String(temp));
    //                System.out.println(new String(temp));
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    channel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

}
