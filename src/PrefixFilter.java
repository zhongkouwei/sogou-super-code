import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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
       put("range*", 0);
       put("perm*", 1);
       put("range+", 2);
       put("perm+", 3);
       put("range=", 4);
       put("perm=", 5);
    }};

    private static final Map<String, Integer> POSITION_HTTPS_RANGE_MAP = new HashMap<>(){{
        put("range*", 6);
        put("perm*", 7);
        put("range+", 8);
        put("perm+", 9);
        put("range=", 10);
        put("perm=", 11);
    }};

    private static final int POSITION_HTTP = 12;
    private static final int POSITION_HTTPS = 13;

    /**
     * rules map
     */
    private Map<String, BitSet> rulesMap = new ConcurrentHashMap<>();

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
                    addRule(rulesMap, prefix.substring(2), range, permValue, true, true);
                } else if (prefix.startsWith("https")) {
                    addRule(rulesMap, prefix.substring(8),range, permValue, false, true);
                } else if (prefix.startsWith("http")) {
                    addRule(rulesMap, prefix.substring(7),range, permValue, true, false);
                }
            }
        }
    }

    private void addRule(Map<String, BitSet> map, String prefix, char range, boolean perm, boolean http, boolean https) {
        BitSet bitSet = rulesMap.get(prefix);
        if (bitSet == null) {
            bitSet = new BitSet();
        }

        if (http) {
            bitSet.set(POSITION_HTTP, http);
            setBitSet(bitSet, POSITION_HTTP_RANGE_MAP, range, perm);
        }
        if (https) {
            bitSet.set(POSITION_HTTPS, https);
            setBitSet(bitSet, POSITION_HTTPS_RANGE_MAP, range, perm);
        }

        map.put(prefix, bitSet);
    }

    private void setBitSet(BitSet bitSet, Map<String, Integer> rangeMap, char range, boolean perm) {
        if (bitSet.get(rangeMap.get(RANGE_ + range))) {
            // - > +
            if (perm) {
                bitSet.set(rangeMap.get(PERM_ + range), perm);
            }
        } else {
            bitSet.set(rangeMap.get(RANGE_ + range), true);
            bitSet.set(rangeMap.get(PERM_ + range), perm);
        }
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
            if (rulesMap.containsKey(prefix)) {
                var bitSet = rulesMap.get(prefix);
                Map<String, Integer> rangeMap = POSITION_HTTP_RANGE_MAP ;

                if (isHttp) {
                    if (!bitSet.get(POSITION_HTTP)) {
                        prefix = prefix.substring(0, prefix.length() -1);
                        continue;
                    }
                    rangeMap = POSITION_HTTP_RANGE_MAP;
                }
                if (isHttps) {
                    if (!bitSet.get(POSITION_HTTPS)) {
                        prefix = prefix.substring(0, prefix.length() -1);
                        continue;
                    }
                    rangeMap = POSITION_HTTPS_RANGE_MAP;
                }

                // *
                if (bitSet.get(rangeMap.get(RANGE_ + "*"))) {
                    contain = true;
                    perm = bitSet.get(rangeMap.get(PERM_ + "*")) ? 1 : 0;
                }

                // +
                if (bitSet.get(rangeMap.get(RANGE_ + "+"))) {
                    if (url.length() > prefix.length()) {
                        contain = true;
                        perm = bitSet.get(rangeMap.get(PERM_ + "+")) ? 1 : 0;
                    }
                }

                // =
                if (bitSet.get(rangeMap.get(RANGE_ + "="))) {
                    if (prefix.equals(url)) {
                        contain = true;
                        perm = bitSet.get(rangeMap.get(PERM_ + "=")) ? 1 : 0;
                    }
                }
            }
            prefix = prefix.substring(0, prefix.length() -1);
        }

        return perm;
    }
}
