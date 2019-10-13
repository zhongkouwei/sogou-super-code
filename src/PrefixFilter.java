import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author gaoshuo
 * @date 2019-09-29
 */
public class PrefixFilter {
    private static final Object lock = new Object();

    /**
     * rules map
     */
    private Map<String, String> httpMap = new ConcurrentHashMap<>();
    private Map<String, String> httpsMap = new ConcurrentHashMap<>();

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
                    addRule(httpMap, "http:" + prefix, value);
                    addRule(httpsMap, "https:" + prefix, value);
                } else {
                    if (prefix.startsWith("https")) {
                        addRule(httpsMap, prefix, value);
                    } else {
                        addRule(httpMap, prefix, value);
                    }
                }
            }
        }
    }

    private void addRule(Map<String, String> map, String prefix, String value) {
        if (map.containsKey(prefix)) {
            String oldValue = map.get(prefix);
            String newValue = getBestValue(Arrays.asList((oldValue + "," + value).split(","))).stream().collect(Collectors.joining(","));
            map.put(prefix, newValue);
        } else {
            map.put(prefix, value);
        }
    }

    public int match(UrlFilter.Url u) {
        var url = u.url;
        boolean contain = false;
        var perm = -1;

        while (!contain && url.length() > 1) {
            Map<String, String> curMap;
            if (url.startsWith("https")) {
                curMap = httpsMap;
            } else {
                curMap = httpMap;
            }

            if (curMap.containsKey(url)) {
                var values = curMap.get(url);
                var valueList = values.split(",");
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
