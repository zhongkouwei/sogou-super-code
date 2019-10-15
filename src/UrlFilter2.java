// JavaOpt: -Xms13000m -Xmx13000m
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * @author gaoshuo
 * @date 2019-09-29
 */
public class UrlFilter2 {

    private static final Object lock = new Object();

    public static void main(String[] args) throws InterruptedException, IOException {
        long startTime = System.currentTimeMillis();

        ThreadPoolExecutor executor = new ThreadPoolExecutor(100,100, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        String curPath = System.getProperty("user.dir");

        var domainFilter = new DomainFilter();
        File domainFile = new File(args[0]);
        List<File> domainFileList = FileUtil.splitDataToSaveFile(100000, domainFile, curPath);
        for (File file : domainFileList) {
            Callable callable = new Callable() {
                @Override
                public Object call() throws Exception {
                    domainFilter.loadFile(file.getPath());
                    return "";
                }
            };
            executor.submit(callable);
        }
        long domainTime = System.currentTimeMillis();

        var prefixFilter = new PrefixFilter();
        File prefixFile = new File(args[1]);
        List<File> prefixFileList = FileUtil.splitDataToSaveFile(100000, prefixFile, curPath);
        for (File file : prefixFileList) {
            Callable callable = new Callable() {
                @Override
                public Object call() throws Exception {
                    prefixFilter.loadFile(file.getPath());
                    return "";
                }
            };
            executor.submit(callable);
        }
        prefixFilter.changeMap();

        long prefixTime = System.currentTimeMillis();

        executor.shutdown();
        while (true) {
            if (executor.isTerminated()) {
                break;
            }
        }
        executor = new ThreadPoolExecutor(9,9, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

        AtomicInteger allowCount = new AtomicInteger(0);
        AtomicInteger disAllowCount = new AtomicInteger(0);
        AtomicInteger noHitCount = new AtomicInteger(0);
        AtomicLong xOrAllowValue = new AtomicLong(0);
        AtomicLong xorDisAllowValue = new AtomicLong(0);

        LinkedBlockingQueue<String> set = new LinkedBlockingQueue<String>();

        File caseFile = new File(args[2]);
        List<File> fileList = FileUtil.splitDataToSaveFile(100000, caseFile, curPath);

        for (File file : fileList) {
            Callable callable = new Callable() {
                @Override
                public Object call() throws Exception {
                    var inputStream = new FileInputStream(file);
                    var sc = new Scanner(inputStream);
                    while (sc.hasNextLine()) {
                        var l = sc.nextLine();

                        if (l.isEmpty() || l.startsWith("#"))
                            return null;

                        int i = l.indexOf('\t');
                        var url = l.substring(0, i);
                        var strValue = l.substring(i+1, i+9);
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
                            disAllowCount.incrementAndGet();
                            synchronized (lock) {
                                long oldValue = xorDisAllowValue.get();
                                long newValue = oldValue ^ intValue;
                                xorDisAllowValue.set(newValue);
                            }
                        } else if (perm == 0) {
                            // +
                            allowCount.incrementAndGet();
                            synchronized (lock) {
                                long oldValue = xOrAllowValue.get();
                                long newValue = oldValue ^ intValue;
                                xOrAllowValue.set(newValue);
                            }
                            set.put(strValue);
                        } else {
                            allowCount.incrementAndGet();
                            noHitCount.incrementAndGet();
                            synchronized (lock) {
                                long oldValue = xOrAllowValue.get();
                                long newValue = oldValue ^ intValue;
                                xOrAllowValue.set(newValue);
                            }
                            set.put(strValue);
                        }
                    }
                    return "";
                }
            };
            executor.submit(callable);
        }

        executor.shutdown();

        StringBuffer sb = new StringBuffer();

        Thread printThread = new Thread(new Print(executor, set, sb));
        printThread.start();
        printThread.join();
        long dealTime = System.currentTimeMillis();

        System.out.println(sb);

        System.out.println(allowCount);
        System.out.println(disAllowCount);
        System.out.println(noHitCount);
        System.out.format("%08x\n", xOrAllowValue.get());
        System.out.format("%08x\n", xorDisAllowValue.get());

        System.err.println("domain: " + (domainTime-startTime) / 1000);
        System.err.println("prefix: " + (prefixTime-domainTime) / 1000);
        System.err.println("deal: " + (dealTime-prefixTime) / 1000);
        System.err.println("print: " + (System.currentTimeMillis()-dealTime) / 1000);
        System.err.println("total: " + (System.currentTimeMillis() - startTime) / 1000);
    }

    /**
     * print thread
     */
    static class Print implements Runnable {
        private ThreadPoolExecutor executor;
        private LinkedBlockingQueue<String> set;
        private StringBuffer stringBuffer;
        public Print(ThreadPoolExecutor executor, LinkedBlockingQueue<String> set, StringBuffer stringBuffer) {
            this.executor = executor;
            this.set = set;
            this.stringBuffer = stringBuffer;
        }

        @Override
        public void run() {
            while (true) {
                while (set.size() != 0) {
                    if (stringBuffer.length() != 0) {
                        stringBuffer.append("\n");
                    }
                    stringBuffer.append(set.poll());
                }
                if (executor.isTerminated() && set.size() == 0) {
                    break;
                }
            }
        }
    }

    /**
     * @author gaoshuo
     * @date 2019-09-29
     */
    public static class DomainFilter {

        /**
         * rules map
         */
        private Map<String, Integer> yesMap = new ConcurrentHashMap<>();
        private Map<String, Integer> noMap = new ConcurrentHashMap<>();

        public void changeMap() {
            yesMap = new HashMap<>(yesMap);
            noMap = new HashMap<>(noMap);
        }

        /**
         * load domainRuleFile
         * @param filename filename
         */
        public void loadFile(String filename) throws IOException {
            try (var inputStream = new FileInputStream(filename)) {
                var sc = new Scanner(inputStream);
                while (sc.hasNextLine()) {
                    String l = sc.nextLine();
                    var i = l.indexOf('\t');
                    var url = l.substring(0, i);
                    var perm = l.charAt(i+1);

                    if (perm == '-') {
                        noMap.put(url, 0);
                    } else {
                        yesMap.put(url, 0);
                    }
                }
            }
        }

        public int match(Url url) {
            var domain = url.domain;
            boolean contain = false;
            var perm = -1;

            // first with port
            if (url.port != -1) {
                var domainPort = domain + ":" + url.port;
                while (!contain && domainPort.contains(".")) {
                    if (noMap.containsKey(domainPort)) {
                        contain = true;
                        perm = 1;
                    } else if (yesMap.containsKey(domainPort)) {
                        contain = true;
                        perm = 0;
                    }

                    if (domainPort.startsWith(".")) {
                        domainPort = domainPort.substring(1);
                    } else {
                        domainPort = domainPort.substring(domainPort.indexOf("."));
                    }
                }
            }

            // then without port
            while (!contain && domain.contains(".")) {
                if (noMap.containsKey(domain)) {
                    contain = true;
                    perm = 1;
                } else if (yesMap.containsKey(domain)) {
                    contain = true;
                    perm = 0;
                }

                if (domain.startsWith(".")) {
                    domain = domain.substring(1);
                } else {
                    domain = domain.substring(domain.indexOf("."));
                }
            }

            return perm;
        }
    }

    /**
     * @author gaoshuo
     * @date 2019-09-29
     */
    public static class PrefixFilter {
        private static final Object lock = new Object();

        /**
         * rules map
         */
        private Map<String, String> httpMap = new ConcurrentHashMap<>();
        private Map<String, String> httpsMap = new ConcurrentHashMap<>();

        public void changeMap() {
            httpMap = new HashMap<>(httpMap);
            httpsMap = new HashMap<>(httpsMap);
        }

        /**
         * load domainRuleFile
         * @param filename filename
         */
        public void loadFile(String filename) throws IOException {
            try (var inputStream = new FileInputStream(filename)) {
                var sc = new Scanner(inputStream);
                while (sc.hasNextLine()) {
                    String l = sc.nextLine();
                    if (l.isEmpty() || l.startsWith("#"))
                        return;
                    var i = l.indexOf('\t');
                    var j = l.indexOf('\t', i+1);
                    var prefix = l.substring(0, i);
                    var range = l.charAt(i+1);
                    var perm = l.charAt(j+1);
                    String value = String.valueOf(range) + (perm == '+' ? 0 : 1);
                    if (prefix.startsWith("//")) {
                        addRule(httpMap, "http:" + prefix, value);
                        addRule(httpsMap, "https:" + prefix, value);
                    } else {
                        if (prefix.startsWith("https")) {
                            addRule(httpsMap, prefix, value);
                        } else {
                            addRule(httpMap, prefix, value);
                        }
                    }
                }
            }
        }

        private void addRule(Map<String, String> map, String prefix, String value) {
            if (map.containsKey(prefix)) {
                String oldValue = map.get(prefix);
                String newValue = getBestValue(Arrays.asList((oldValue + "," + value).split(","))).stream().collect(Collectors.joining(","));
                map.put(prefix, newValue);
            } else {
                map.put(prefix, value);
            }
        }

        public int match(Url u) {
            var url = u.url;
            boolean contain = false;
            var perm = -1;

            while (!contain && url.length() > 1) {
                Map<String, String> curMap;
                if (url.startsWith("https")) {
                    curMap = httpsMap;
                } else {
                    curMap = httpMap;
                }
                if (curMap.containsKey(url)) {
                    var values = curMap.get(url);
                    var valueList = values.split(",");
                    for (String value : valueList) {
                        if (contain) {
                            break;
                        }
                        var range = value.substring(0, 1);
                        if ("=".equals(range)) {
                            if (url.equals(u.url)) {
                                contain = true;
                                perm = Integer.valueOf(value.substring(1));
                            }
                        } else if ("+".equals(range)) {
                            if (u.url.length() > url.length()) {
                                contain = true;
                                perm = Integer.valueOf(value.substring(1));
                            }
                        } else if ("*".equals(range)) {
                            contain = true;
                            perm = Integer.valueOf(value.substring(1));
                        }
                    }
                }
                url = url.substring(0, url.length() -1);
            }

            return perm;
        }

        private List<String> getBestValue(List<String> values) {
            return values.stream().sorted((o1, o2) -> {
                String range1 = o1.substring(0, 1);
                String perm1 = o1.substring(1);
                String range2 = o2.substring(0, 1);
                String perm2 = o2.substring(1);

                if (!range1.equals(range2)) {
                    if ("*".equals(range1)) {
                        return 1;
                    } else if ("*".equals(range2)) {
                        return -1;
                    }
                }
                if (perm1.equals(perm2)) {
                    return 1;
                } else {
                    return "-".equals(perm1) ? -1 : 1;
                }

            }).collect(Collectors.toList());
        }
    }

    /**
     * @author gaoshuo
     * @date 2019-09-29
     */
    public static class Url {
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
     * @date 2019-09-30
     */
    public static class FileUtil {

        public static List<File> splitDataToSaveFile(int rows, File sourceFile, String targetDirectoryPath) {
            List<File> fileList = new ArrayList<>();
            File targetFile = new File(targetDirectoryPath);
            if (!sourceFile.exists() || rows <= 0 || sourceFile.isDirectory()) {
                return null;
            }
            if (targetFile.exists()) {
                if (!targetFile.isDirectory()) {
                    return null;
                }
            } else {
                targetFile.mkdirs();
            }

            try (FileInputStream fileInputStream = new FileInputStream(sourceFile);
                 InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8);
                 BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
                StringBuilder stringBuilder = new StringBuilder();
                String lineStr;
                int lineNo = 1, fileNum = 1;
                while ((lineStr = bufferedReader.readLine()) != null) {
                    stringBuilder.append(lineStr).append("\r\n");
                    if (lineNo % rows == 0) {
                        File file = new File(targetDirectoryPath + File.separator + fileNum + sourceFile.getName());
                        writeFile(stringBuilder.toString(), file);
                        stringBuilder.delete(0, stringBuilder.length());
                        fileNum++;
                        fileList.add(file);
                    }
                    lineNo++;
                }
                if ((lineNo - 1) % rows != 0) {
                    File file = new File(targetDirectoryPath + File.separator + fileNum + sourceFile.getName());
                    writeFile(stringBuilder.toString(), file);
                    fileList.add(file);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return fileList;
        }

        private static void writeFile(String text, File file) {
            try (
                    FileOutputStream fileOutputStream = new FileOutputStream(file);
                    OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8);
                    BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter, 1024)
            ) {
                bufferedWriter.write(text);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public static int getTotalLines(File file) throws IOException {
            FileReader in = new FileReader(file);
            LineNumberReader reader = new LineNumberReader(in);
            reader.skip(Long.MAX_VALUE);
            int lines = reader.getLineNumber();
            reader.close();
            return lines;
        }

    }
}
