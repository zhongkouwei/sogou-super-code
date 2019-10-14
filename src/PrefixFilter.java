import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author gaoshuo
 * @date 2019-09-29
 */
class PrefixFilter {
    private static final Object LOCK = new Object();

    private static final String RANGE_ = "range";
    private static final String PERM_ = "perm";
    private static final Map<String, Integer> POSITION_HTTP_RANGE_MAP = new HashMap<>(){{
       put("range*", 1);
       put("perm*", 1 << 1);
       put("range+", 1 << 2);
       put("perm+", 1 << 3);
       put("range=", 1 << 4);
       put("perm=", 1 << 5);
    }};

    private static final Map<String, Integer> POSITION_HTTPS_RANGE_MAP = new HashMap<>(){{
        put("range*", 1 << 6);
        put("perm*", 1 << 7);
        put("range+", 1 << 8);
        put("perm+", 1 << 9);
        put("range=", 1 << 10);
        put("perm=", 1 << 11);
    }};

    private static final int POSITION_HTTP = 1 << 12;
    private static final int POSITION_HTTPS = 1 << 13;

    /**
     * rules map
     */
//    private Map<String, BitSet> rulesMap = new ConcurrentHashMap<>();

    private static final int MAP_NUM = 100;
    private Map<String, Integer>[] maps = new Map[100];

    private Bloom bloom = new Bloom();

    private int minLength = Integer.MAX_VALUE;
    private int maxLength = Integer.MIN_VALUE;


    /**
     * load domainRuleFile
     * @param filename filename
     */
    void loadFile(String filename) throws IOException {
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
                var permValue = perm == '-';

                if (prefix.startsWith("//")) {
                    addRule(prefix.substring(2), range, permValue, true, true);
                } else if (prefix.startsWith("https")) {
                    addRule(prefix.substring(8),range, permValue, false, true);
                } else if (prefix.startsWith("http")) {
                    addRule(prefix.substring(7),range, permValue, true, false);
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

    private void addRule(String prefix, char range, boolean perm, boolean http, boolean https) {

        if (prefix.length() > maxLength) {
            maxLength = prefix.length();
        }
        if (prefix.length() < minLength) {
            minLength = prefix.length();
        }
        Map<String, Integer> rulesMap = getRulesMap(prefix);
        var bitInt = rulesMap.get(prefix);
        if (bitInt == null) {
            bitInt = 0;
        }

        if (http) {
            bitInt |= POSITION_HTTP;
            bitInt = setBitSet(bitInt, POSITION_HTTP_RANGE_MAP, range, perm);
        }
        if (https) {
            bitInt |= POSITION_HTTPS;
            bitInt = setBitSet(bitInt, POSITION_HTTPS_RANGE_MAP, range, perm);
        }

        rulesMap.put(prefix, bitInt);
    }

    private int setBitSet(int bitInt, Map<String, Integer> rangeMap, char range, boolean perm) {
        if (check(bitInt, rangeMap.get(RANGE_ + range))) {
            // - > +
            if (perm) {
                bitInt |= rangeMap.get(PERM_ + range);
            }
        } else {
            bitInt |= rangeMap.get(RANGE_ + range);
            if (perm) {
                bitInt |= rangeMap.get(PERM_ + range);
            }
        }
        return bitInt;
    }

    int match(UrlFilter.Url u) {
        var url = u.url;
        boolean contain = false;
        var perm = -1;

        var isHttp = false;
        var isHttps = false;

        if (url.startsWith("https")) {
            url = url.substring(8);
            isHttps = true;
        } else if (url.startsWith("http")) {
            url = url.substring(7);
            isHttp = true;
        }

        var prefix = url;

        while (!contain && prefix.length() > 1) {
            if (prefix.length() > maxLength) {
                prefix = prefix.substring(0, prefix.length() -1);
                continue;
            }
            if (prefix.length() < minLength) {
                break;
            }
            Map<String, Integer> rulesMap = getRulesMap(prefix);
            var bitInt = rulesMap.get(prefix);
            if (bitInt != null) {
                Map<String, Integer> rangeMap = POSITION_HTTP_RANGE_MAP ;

                if (isHttp) {
                    if (!check(bitInt, POSITION_HTTP)) {
                        prefix = prefix.substring(0, prefix.length() -1);
                        continue;
                    }
                    rangeMap = POSITION_HTTP_RANGE_MAP;
                }
                if (isHttps) {
                    if (!check(bitInt, POSITION_HTTPS)) {
                        prefix = prefix.substring(0, prefix.length() -1);
                        continue;
                    }
                    rangeMap = POSITION_HTTPS_RANGE_MAP;
                }

                // *
                if (check(bitInt, rangeMap.get(RANGE_ + "*"))) {
                    contain = true;
                    perm = check(bitInt, rangeMap.get(PERM_ + "*")) ? 1 : 0;
                }

                // +
                if (check(bitInt, rangeMap.get(RANGE_ + "+"))) {
                    if (url.length() > prefix.length()) {
                        contain = true;
                        perm = check(bitInt, rangeMap.get(PERM_ + "+")) ? 1 : 0;
                    }
                }

                // =
                if (check(bitInt, rangeMap.get(RANGE_ + "="))) {
                    if (prefix.equals(url)) {
                        contain = true;
                        perm = check(bitInt, rangeMap.get(PERM_ + "=")) ? 1 : 0;
                    }
                }
            }
            prefix = prefix.substring(0, prefix.length() -1);
        }

        return perm;
    }

    private boolean check(int value, int position){
        return (value & position) == position;
    }
}
