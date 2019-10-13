import java.math.BigInteger;
import java.security.MessageDigest;

/**
 * @author gaoshuo
 * @date 2019-10-02
 */
public class Test {

    public static void main(String[] args) {
//        UrlFilter.Trie trie = new UrlFilter.Trie();

//        trie.insert("http://sogou.com");

//        System.out.println(getMD5String("afdfdfdsfasdfdsfadsfadsfadsfdsa"));

        String url = "http://baidu.com";
        byte[] bytes = url.getBytes();
        for (byte b : bytes) {
            System.out.println(b);
        }
    }

    public static String getMD5String(String str) {
        MessageDigest md5 = null;
        try{
            md5 = MessageDigest.getInstance("MD5");
        }catch (Exception e){
            System.out.println(e.toString());
            e.printStackTrace();
            return "";
        }
        char[] charArray = str.toCharArray();
        byte[] byteArray = new byte[charArray.length];
        for (int i = 0; i < charArray.length; i++)
            byteArray[i] = (byte) charArray[i];
        byte[] md5Bytes = md5.digest(byteArray);
        StringBuffer hexValue = new StringBuffer();
        for (int i = 0; i < md5Bytes.length; i++){
            int val = ((int) md5Bytes[i]) & 0xff;
            if (val < 16)
                hexValue.append("0");
            hexValue.append(Integer.toHexString(val));
        }
        return hexValue.toString().substring(0, 8);
    }

}
