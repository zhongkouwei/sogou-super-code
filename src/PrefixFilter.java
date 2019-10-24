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

    private static final int MAP_NUM = 100;
    private HashMap<String, Node>[] rootMaps = new HashMap[MAP_NUM];

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

    private HashMap<String, Node> getRootMap(String url) {
        int hash = url.hashCode();
        int position = Math.abs(hash % MAP_NUM);
        if (rootMaps[position] == null) {
            rootMaps[position] = new HashMap<>();
        }

        return rootMaps[position];
    }

    private Node getAddNode(String rootUrl) {
        HashMap<String, Node> rootMap = getRootMap(rootUrl);
        Node rootNode = rootMap.get(rootUrl);
        if (rootNode == null) {
            rootNode = new Node();
            rootMap.put(rootUrl, rootNode);
        }

        return rootNode;
    }

    private Node getNode(String rootUrl) {
        HashMap<String, Node> rootMap = getRootMap(rootUrl);
        return rootMap.get(rootUrl);
    }

    private void addRule(String prefix, char range, boolean perm, boolean http, boolean https) {
        String rootUrl = prefix.substring(0, prefix.indexOf("/") + 1);
        String path = prefix.substring(prefix.indexOf("/") + 1);
        Node node = getAddNode(rootUrl);

        boolean isRoot = prefix.indexOf("/") == prefix.length() - 1 && prefix.endsWith("/");

        Short value = 0;
        if (isRoot) {
            value = node.value;
        } else  {
            value = node.children.get(path);
            if (value == null) {
                value = 0;
            }
        }
        short bitInt = value;
        if (http) {
            bitInt |= POSITION_HTTP;
            bitInt = setBitSet(bitInt, POSITION_HTTP_RANGE_MAP, range, perm);
        }
        if (https) {
            bitInt |= POSITION_HTTPS;
            bitInt = setBitSet(bitInt, POSITION_HTTPS_RANGE_MAP, range, perm);
        }

        /*
            if is root, mark the value and isEmpty. else put children
         */
        if (isRoot) {
            node.value = bitInt;
        } else {
            node.position |= (1 << path.length());
            node.isEmpty = false;
            node.children.put(path, bitInt);
        }
    }

    private short setBitSet(short bitInt, Map<String, Integer> rangeMap, char range, boolean perm) {
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

        /*
            first check rootUrl, if no path
         */
        var rootUrl = url.substring(0, url.indexOf("/") + 1);
        var path = url.substring(url.indexOf("/") + 1);
        Node node = getNode(rootUrl);
        if (node == null) {
            return perm;
        }
        if (node.isEmpty) {
            return checkUrl(url, rootUrl, isHttp, isHttps, node.value);
        }

        var tempPath = path;

        /*
            then check path
         */
        while (perm == -1) {
            if (tempPath.length() == 0) {
                if (node.value != 0) {
                    perm =  checkUrl(path, tempPath, isHttp, isHttps, node.value);
                }
                return perm;
            } else {
                if (!check(node.position, 1 << tempPath.length())) {
                    tempPath = tempPath.substring(0, tempPath.length() -1);
                    continue;
                }
            }

            var bitInt = node.children.get(tempPath);
            if (bitInt != null) {
                perm = checkUrl(path, tempPath, isHttp, isHttps, bitInt);
            }

            tempPath = tempPath.substring(0, tempPath.length() -1);
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

    class Node {
        HashMap<String, Short> children = new HashMap<>();
        short value = 0;
        boolean isEmpty = true;
        int position = 0;
    }
}
