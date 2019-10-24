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
    private HashMap<String, Node>[] portMaps = new HashMap[MAP_NUM];
    private HashMap<String, Node>[] noPortmaps = new HashMap[MAP_NUM];

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

    private HashMap<String, Node> getRuleMap(String url, boolean withPort) {
        int hash = url.hashCode();
        int position = Math.abs(hash % MAP_NUM);
        HashMap<String, Node>[] curMaps = withPort ? portMaps : noPortmaps;
        if (curMaps[position] == null) {
            curMaps[position] = new HashMap<>();
        }
        return curMaps[position];
    }

    private Node getAddNode(String root, boolean withPort) {
        HashMap<String, Node> rootMap = getRuleMap(root, withPort);
        Node rootNode = rootMap.get(root);
        if (rootNode == null) {
            rootNode = new Node();
            rootMap.put(root, rootNode);
        }

        return rootNode;
    }

    private Node getNode(String root, boolean withPort) {
        HashMap<String, Node> rootMap = getRuleMap(root, withPort);
        return rootMap.get(root);
    }

    private void addRule(String url, int isPosition, int permPosition, boolean perm) {
        String[] strs = url.split("\\.");
        Node rootNode = getAddNode(strs[strs.length-1], url.contains(":"));
        Short value = 0;
        if (strs.length == 1) {
            value = rootNode.value;
            value = setBitInt(value, isPosition, permPosition, perm);
            rootNode.value = value;
        } else {
            rootNode.isEmpty = false;
            String path = strs.length == 2 ? "" : url.substring(0, url.lastIndexOf(".", url.lastIndexOf(".")-1));
            CenterNode centerNode = rootNode.children.get(strs[strs.length-2]);
            if (centerNode == null) {
                centerNode = new CenterNode();
                rootNode.children.put(strs[strs.length-2], centerNode);
            }
            if (strs.length == 2) {
                value = centerNode.value;
            } else {
                value = centerNode.children.get(path);
            }
            if (value == null) {
                value = 0;
            }
            var bitInt = setBitInt(value, isPosition, permPosition, perm);
            if (strs.length == 2) {
                centerNode.value = bitInt;
            } else {
                centerNode.position |= (1 << path.length());
                centerNode.isEmpty = false;
                centerNode.children.put(path, bitInt);
            }
        }
    }

    private short setBitInt(short bitInt, int isPosition, int permPosition, boolean perm) {
        bitInt |= isPosition;
        if (!(check(bitInt, permPosition))) {
            if (perm) {
                bitInt |= permPosition;
            }
        }
        return bitInt;
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

        String[] strs = url.split("\\.");
        Node rootNode = getNode(strs[strs.length-1], withPort);
        if (rootNode == null) {
            return perm;
        }
        if (rootNode.isEmpty) {
            if (rootNode.value != 0) {
                perm = checkUrl(true, rootNode.value);
            }
            return perm;
        }
        String path = strs.length == 2 ? "" : url.substring(0, url.lastIndexOf(".", url.lastIndexOf(".")-1));
        CenterNode node = rootNode.children.get(strs[strs.length-2]);
        if (node == null) {
            perm = checkUrl(true, rootNode.value);
            return perm;
        }
        if (node.isEmpty) {
            if (node.value != 0) {
                perm = checkUrl(path.length() != 0, node.value);
            }
            if (perm == -1) {
                perm = checkUrl(true, rootNode.value);
            }
            return perm;
        }
        while (true) {
            if ("".equals(path)) {
                if (node.value != 0) {
                    perm = checkUrl(havaPoint, node.value);
                }
                break;
            }
            var bitInt = node.children.get(path);
            if (bitInt != null) {
                perm = checkUrl(havaPoint, bitInt);
                if (perm != -1) {
                    return perm;
                }
            }

            path = path.contains(".") ? path.substring(path.indexOf(".") + 1) : "";
            havaPoint = true;
        }
        if (perm == -1) {
            perm = checkUrl(true, rootNode.value);
        }

        return perm;
    }

    private int checkUrl(boolean havaPoint, short bitInt) {
        var perm = -1;
        if (havaPoint && (check(bitInt, POSITION_IS_POINT))) {
            perm = check(bitInt, POSITION_POINT_PERM) ? 1 : 0;
        }
        if (perm == -1) {
            if (check(bitInt, POSITION_IS_URL)) {
                perm = check(bitInt, POSITION_URL_PERM) ? 1 : 0;
            }
        }
        return perm;
    }

    private boolean check(short value, int position){
        return (value & position) == position;
    }

    class Node {
        HashMap<String, CenterNode> children = new HashMap<>();
        short value = 0;
        boolean isEmpty = true;
    }

    class CenterNode {
        HashMap<String, Short> children = new HashMap<>();
        short value = 0;
        boolean isEmpty = true;
        int position = 0;
    }
}
