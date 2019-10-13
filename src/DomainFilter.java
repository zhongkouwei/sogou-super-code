import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author gaoshuo
 * @date 2019-09-29
 */
public class DomainFilter {

    /**
     * rules map
     */
    private Map<String, String> yesMap = new ConcurrentHashMap<>();
    private Map<String, String> noMap = new ConcurrentHashMap<>();

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

                var hash = Md5Util.getMd5Bytes(url);

                if (perm == '-') {
                    noMap.put(hash, url);
                } else {
                    yesMap.put(hash, url);
                }
            }
        }
    }

    public int match(UrlFilter.Url url) {
        var domain = url.domain;
        boolean contain = false;
        var perm = -1;

        // first with port
        if (url.port != -1) {
            var domainPort = domain + ":" + url.port;
            while (!contain && domainPort.contains(".")) {
                var hash = Md5Util.getMd5Bytes(domainPort);
                if (noMap.containsKey(hash)) {
                    contain = true;
                    perm = 1;
                } else if (yesMap.containsKey(hash)) {
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
            var hash = Md5Util.getMd5Bytes(domain);
            if (noMap.containsKey(hash)) {
                contain = true;
                perm = 1;
            } else if (yesMap.containsKey(hash)) {
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
