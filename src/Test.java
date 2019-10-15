import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * @author gaoshuo
 * @date 2019-10-02
 */
public class Test {

    public static void main(String[] args) throws IOException {
//        UrlFilter.Trie trie = new UrlFilter.Trie();

//        trie.insert("http://sogou.com");

//        System.out.println(getMD5String("afdfdfdsfasdfdsfadsfadsfadsfdsa"));

//        String url = "http://baidu.com";
//        byte[] bytes = url.getBytes();
//        for (byte b : bytes) {
//            System.out.println(b);
//        }
//        readLine();


    }

    private static void readLine() throws IOException {
//        long startTime = System.currentTimeMillis();
//        Map<byte[], Integer> map = new HashMap<>();
//        RandomAccessFile randomAccessFile = new RandomAccessFile("/Users/gaoshuo/work/gitlab/chaojimali2/case2/urlprefix.txt", "rw");
//        FileChannel channel = randomAccessFile.getChannel();
//        ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
//        int bytesRead = channel.read(buffer);
//        ByteBuffer stringBuffer = ByteBuffer.allocate(20);
//        while (bytesRead != -1) {
//            //之前是写buffer，现在要读buffer
//            buffer.flip();// 切换模式，写->读
//            while (buffer.hasRemaining()) {
//                byte b = buffer.get();
//                if (b == 10 || b == 13) { // 换行或回车
//                    stringBuffer.flip();
//                    // 这里就是一个行
//                    final String line = Charset.forName("utf-8").decode(stringBuffer).toString();
//                    var hash = UrlFilter.Md5Util.getMd5Bytes(line);
//                    map.put(hash, 1);
//                    stringBuffer.clear();
//                } else {
//                    if (stringBuffer.hasRemaining())
//                        stringBuffer.put(b);
//                    else { // 空间不够扩容
//                        stringBuffer = reAllocate(stringBuffer);
//                        stringBuffer.put(b);
//                    }
//                }
//            }
//            buffer.clear();// 清空,position位置为0，limit=capacity
//            //  继续往buffer中写
//            bytesRead = channel.read(buffer);
//        }
//        randomAccessFile.close();
//        try (var inputStream = new FileInputStream("/Users/gaoshuo/work/gitlab/chaojimali2/case2/urlprefix.txt")) {
//            var sc = new Scanner(inputStream);
//            while (sc.hasNextLine()) {
//                String l = sc.nextLine();
//                map.put(l.getBytes(), 1);
//            }
//        }
//        System.out.println((System.currentTimeMillis() - startTime));
    }

    private static ByteBuffer reAllocate(ByteBuffer stringBuffer) {
        final int capacity = stringBuffer.capacity();
        byte[] newBuffer = new byte[capacity * 2];
        System.arraycopy(stringBuffer.array(), 0, newBuffer, 0, capacity);
        return (ByteBuffer) ByteBuffer.wrap(newBuffer).position(capacity);
    }

}
