import java.util.HashMap;
import java.util.Map;

/**
 * @author gaoshuo
 * @date 2019-09-29
 */
class PrefixFilter implements UrlFilter.Filter {
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
    private static final int POSITION_CONTINUE = 1 << 14;

    private static final int MAP_NUM = 100;
    private Map<String, Integer>[] rootMaps = new Map[MAP_NUM];
    private Map<String, Integer>[] notRootmaps = new Map[MAP_NUM];

    private int minLength = Integer.MAX_VALUE;
    private int maxLength = Integer.MIN_VALUE;

    @Override
    public void load(String l) {
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

    private Map<String, Integer> getRulesMap(String url, boolean isRoot) {
        int hash = url.length();
        int position = Math.abs(hash % MAP_NUM);
        Map<String, Integer>[] curMaps = isRoot ? rootMaps : notRootmaps;
        if (curMaps[position] == null) {
            curMaps[position] = new HashMap<>();
        }

        return curMaps[position];
    }

    private void addRule(String prefix, char range, boolean perm, boolean http, boolean https) {
        if (prefix.length() > maxLength) {
            maxLength = prefix.length();
        }
        if (prefix.length() < minLength) {
            minLength = prefix.length();
        }

        boolean isRoot = prefix.indexOf("/") == prefix.length() - 1 && prefix.endsWith("/");

        Map<String, Integer> rulesMap = getRulesMap(prefix, isRoot);
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

        /*
            when is not root, mark rootMap
         */
        if (!isRoot) {
            var rootUrl = prefix.substring(0, prefix.indexOf("/") + 1);
            rulesMap = getRulesMap(rootUrl, true);
            bitInt = rulesMap.get(rootUrl);
            if (bitInt == null) {
                bitInt = 0;
            }
            bitInt |= POSITION_CONTINUE;
            rulesMap.put(rootUrl, bitInt);
        }
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

//        /*
//            first check rootUrl
//         */
        var chekedRoot = false;
        var rootPerm = -1;
        var rootUrl = prefix.substring(0, prefix.indexOf("/") + 1);
        int rootLen = rootUrl.length();
        Map<String, Integer> rulesMap = getRulesMap(rootUrl, true);
        var bitInt = rulesMap.get(rootUrl);
        if (bitInt != null) {
            rootPerm = checkUrl(url, rootUrl, isHttp, isHttps, bitInt);
            chekedRoot = true;
            if (!check(bitInt, POSITION_CONTINUE)) {
                return rootPerm;
            }
        }

        /*
            then check path
         */
        int curLen = prefix.length();
        while (perm == -1 && curLen >= rootLen) {
            if (curLen == rootLen && chekedRoot) {
                return rootPerm;
            }
            if (curLen > maxLength) {
                prefix = prefix.substring(0, curLen -1);
                curLen = prefix.length();
                continue;
            }

            if (curLen < minLength) {
                break;
            }

            rulesMap = getRulesMap(prefix, curLen == rootLen);
            bitInt = rulesMap.get(prefix);
            if (bitInt != null) {
                perm = checkUrl(url, prefix, isHttp, isHttps, bitInt);
            }

            prefix = prefix.substring(0, curLen -1);
            curLen = prefix.length();
        }
        return perm;
    }

    private int checkUrl(String url, String prefix, boolean isHttp, boolean isHttps, int bitInt) {
        var perm = -1;
        Map<String, Integer> rangeMap = POSITION_HTTP_RANGE_MAP ;

        if (isHttp) {
            if (!check(bitInt, POSITION_HTTP)) {
                return -1;
            }
            rangeMap = POSITION_HTTP_RANGE_MAP;
        }
        if (isHttps) {
            if (!check(bitInt, POSITION_HTTPS)) {
                return -1;
            }
            rangeMap = POSITION_HTTPS_RANGE_MAP;
        }

        // *
        if (check(bitInt, rangeMap.get(RANGE_ + "*"))) {
            perm = check(bitInt, rangeMap.get(PERM_ + "*")) ? 1 : 0;
        }

        // +
        if (check(bitInt, rangeMap.get(RANGE_ + "+"))) {
            if (url.length() > prefix.length()) {
                perm = check(bitInt, rangeMap.get(PERM_ + "+")) ? 1 : 0;
            }
        }

        // =
        if (check(bitInt, rangeMap.get(RANGE_ + "="))) {
            if (prefix.equals(url)) {
                perm = check(bitInt, rangeMap.get(PERM_ + "=")) ? 1 : 0;
            }
        }
        return perm;
    }

    private boolean check(int value, int position){
        return (value & position) == position;
    }
}
