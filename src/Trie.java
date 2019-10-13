/**
 * @author gaoshuo
 * @date 2019-10-08
 */

import java.util.HashMap;
import java.util.Map;

/**
 * @author gaoshuo
 * @date 2019-09-29
 */
public class Trie {
    private TrieNode root;

    public Trie() {
        root = new TrieNode();
        root.wordEnd = false;
    }

    public void insert(String word, String value) {
//        TrieNode node = root;
//        byte[] bytes = word.getBytes();
//        for (int i = 0; i < bytes.length ; i++) {
//            byte b = bytes[i];
//            if (!node.childdren.containsKey(b)) {
//                node.childdren.put(b, new TrieNode());
//            }
//            node = node.childdren.get(b);
//            if (i == word.length() -1) {
//                node.value = value;
//            }
//        }
//        node.wordEnd = true;
    }

    public TrieNode search(String word) {
//        TrieNode node = root;
//        byte[] bytes = word.getBytes();
//        for (int i = 0; i < bytes.length ; i++) {
//            byte b = bytes[i];
//            if (!node.childdren.containsKey(b)) {
//                return null;
//            }
//            node = node.childdren.get(b);
//        }
//        return node.wordEnd ?  node : null;
        return null;
    }

    public class TrieNode {
        TrieNode[] childdren;
        boolean wordEnd;
        String value;

        public TrieNode() {
            childdren = new TrieNode[]{};
            wordEnd = false;
        }
    }
}
