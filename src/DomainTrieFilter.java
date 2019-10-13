import java.io.FileInputStream;
import java.io.IOException;
import java.util.Scanner;

/**
 * @author gaoshuo
 * @date 2019-10-08
 */
public class DomainTrieFilter {

    /**
     * rules trie
     */
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
                var i = l.indexOf('\t');
                var url = l.substring(0, i);
                var perm = l.charAt(i+1);

                trie.insert(url, String.valueOf(perm));
            }
        }
    }

    public int match(UrlFilter2.Url url) {
        var domain = url.domain;
        boolean contain = false;
        var perm = -1;

        // first with port
        if (url.port != -1) {
            var domainPort = domain + ":" + url.port;
            while (!contain && domainPort.contains(".")) {
                Trie.TrieNode node = trie.search(domainPort);
                if (node != null) {
                    contain = true;
                    perm = "+".equals(node.value) ? 0 : 1;
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
                Trie.TrieNode node = trie.search(domain);
                if (node != null) {
                    contain = true;
                    perm = "+".equals(node.value) ? 0 : 1;
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
