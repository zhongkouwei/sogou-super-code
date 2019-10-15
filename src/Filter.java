import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * @author gaoshuo
 * @date 2019-10-14
 */
interface Filter {

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
