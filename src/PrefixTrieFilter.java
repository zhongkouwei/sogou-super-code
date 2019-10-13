/**
 * @author gaoshuo
 * @date 2019-10-08
 */

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author gaoshuo
 * @date 2019-09-29
 */
public class PrefixTrieFilter {
    private static final Object lock = new Object();

    /**
     * rules map
     */
    private ConcurrentHashMap<String, String> rulesMap = new ConcurrentHashMap<>();

    private Trie trie = new Trie();

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
                    addRule("http:" + prefix, value);
                    addRule("https:" + prefix, value);
                } else {
                    addRule(prefix, value);
                }
            }
        }
    }

    private void addRule(String prefix, String value) {
        Trie.TrieNode node = trie.search(prefix);
        if (node != null) {
            synchronized (lock) {
                String oldValue = node.value;
                String newValue = getBestValue(Arrays.asList(new String[]{oldValue, value})).stream().collect(Collectors.joining(","));
                trie.insert(prefix, newValue);
            }
        } else {
            trie.insert(prefix, value);
        }
    }

    public int match(UrlFilter.Url u) {
        var url = u.url;
        boolean contain = false;
        var perm = -1;

        while (!contain && url.length() > 1) {
            Trie.TrieNode node = trie.search(url);
            if (node != null) {
                var values = node.value;
                var valueList = Arrays.asList(values.split(","));
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
