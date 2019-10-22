import java.util.HashMap;
import java.util.Map;

/**
 * @author gaoshuo
 * @date 2019-09-29
 */
public class DomainFilter implements UrlFilter.Filter {
    private static final Object LOCK = new Object();

    private static final int POSITION_IS_URL = 1;
    private static final int POSITION_URL_PERM = 1 << 1;
    private static final int POSITION_IS_POINT = 1 << 2;
    private static final int POSITION_POINT_PERM = 1 << 3;

    private static final int MAP_NUM = 100;
    private Map<String, Short>[] portMaps = new HashMap[MAP_NUM];
    private Map<String, Short>[] noPortmaps = new HashMap[MAP_NUM];

    @Override
    public void load(String l) {
        var i = l.indexOf('\t');
        var url = l.substring(0, i);
        var perm = l.charAt(i+1) == '-';

        if (url.startsWith(".")) {
            addRule(url.substring(1), POSITION_IS_POINT, POSITION_POINT_PERM, perm);
        } else {
            addRule(url, POSITION_IS_URL, POSITION_URL_PERM, perm);
        }
    }

    private Map<String, Short> getRulesMap(String url, boolean withPort) {
        int hash = url.length();
        int position = Math.abs(hash % MAP_NUM);
        Map<String, Short>[] curMaps = withPort ? portMaps : noPortmaps;
        if (curMaps[position] == null) {
            curMaps[position] = new HashMap<>();
        }
        return curMaps[position];
    }

    private void addRule(String url, int isPosition, int permPosition, boolean perm) {
        Map<String, Short> rulesMap = getRulesMap(url, url.contains(":"));
        Short value = rulesMap.get(url);
        if (value == null) {
            value =  0;
        }
        short bitInt = value;
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
            perm = checkUrl(domainPort, true);
        }

        if (perm != -1) {
            return perm;
        }

        // then without port
        perm = checkUrl(domain, false);

        return perm;
    }

    private int checkUrl(String url, boolean withPort) {
        var havaPoint = false;
        var perm = -1;
        while (true) {
            Map<String, Short> rulesMap = getRulesMap(url, withPort);
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

    private boolean check(short value, int position){
        return (value & position) == position;
    }
}
