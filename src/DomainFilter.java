import java.io.FileInputStream;
import java.io.IOException;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author gaoshuo
 * @date 2019-09-29
 */
public class DomainFilter {
    private static final Object LOCK = new Object();

    /**
     * rules map
     */
//    private Map<String, BitSet> rulesMap = new ConcurrentHashMap<>();

    private static final int POSITION_IS_URL = 1;
    private static final int POSITION_URL_PERM = 1 << 1;
    private static final int POSITION_IS_POINT = 1 << 2;
    private static final int POSITION_POINT_PERM = 1 << 3;

    private static final int MAP_NUM = 100;
    private Map<String, Integer>[] maps = new Map[100];

    private Bloom bloom = new Bloom();

    /**
     * load domainRuleFile
     * @param filename filename
     */
    void loadFile(String filename) throws IOException {
        try (var inputStream = new FileInputStream(filename)) {
            var sc = new Scanner(inputStream);
            while (sc.hasNextLine()) {
                String l = sc.nextLine();
                var i = l.indexOf('\t');
                var url = l.substring(0, i);
                var perm = l.charAt(i+1) == '-';

                if (url.startsWith(".")) {
                    addRule(url.substring(1), POSITION_IS_POINT, POSITION_POINT_PERM, perm);
                } else {
                    addRule(url, POSITION_IS_URL, POSITION_URL_PERM, perm);
                }

            }
        }
    }

    private Map<String, Integer> getRulesMap(String url) {
        int hash = url.length();
        int position = Math.abs(hash % MAP_NUM);
        if (maps[position] == null) {
            maps[position] = new ConcurrentHashMap<>();
        }
        return maps[position];
    }

    private void addRule(String url, int isPosition, int permPosition, boolean perm) {
        Map<String, Integer> rulesMap = getRulesMap(url);
        var bitInt = rulesMap.get(url);
        if (bitInt == null) {
            bitInt =  0;
        }
        bitInt |= isPosition;
        if (!(check(bitInt, permPosition))) {
            if (perm) {
                bitInt |= permPosition;
            }
        }
        rulesMap.put(url, bitInt);
    }

    int match(UrlFilter.Url url) {
        var domain = url.domain;
        var perm = -1;

        // first with port
        if (url.port != -1) {
            var domainPort = domain + ":" + url.port;
            perm = checkUrl(domainPort);
        }

        if (perm != -1) {
            return perm;
        }

        // then without port
        perm = checkUrl(domain);

        return perm;
    }

    private int checkUrl(String url) {
        var havaPoint = false;
        var perm = -1;
        while (true) {
            Map<String, Integer> rulesMap = getRulesMap(url);
            var bitInt = rulesMap.get(url);
            if (bitInt != null) {
                if (havaPoint && (check(bitInt, POSITION_IS_POINT))) {
                    perm = check(bitInt, POSITION_POINT_PERM) ? 1 : 0;
                    break;
                }
                if (check(bitInt, POSITION_IS_URL)) {
                    perm = check(bitInt, POSITION_URL_PERM) ? 1 : 0;
                    break;
                }
            }

            if (!url.contains(".")) {
                break;
            }
            url = url.substring(url.indexOf(".") + 1);
            havaPoint = true;
        }
        return perm;
    }

    private boolean check(int value, int position){
        return (value & position) == position;
    }
}
