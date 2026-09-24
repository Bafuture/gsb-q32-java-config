package com.example.config.source;

/**
 * 配置键与环境变量名的转换规则。
 *
 * <ul>
 *   <li>Java 字段名（camelCase）与配置键（kebab-case）互换：serverPort &lt;-&gt; server.port</li>
 *   <li>环境变量名：把 kebab-case 键中的 '.', '-' 转为 '_'，列表下标 '[0]' 转为 '_0'，整体大写。
 *       例如 server.port -&gt; SERVER_PORT；servers[0].host -&gt; SERVERS_0_HOST</li>
 * </ul>
 */
public final class Keys {

    private Keys() {
    }

    /**
     * camelCase 字段名转为 kebab-case：
     * {@code name -&gt; name}、{@code serverPort -&gt; server-port}、
     * {@code URLPath -&gt; url-path}、{@code connectTimeoutMs -&gt; connect-timeout-ms}。
     */
    public static String camelToKebab(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                char prev = name.charAt(i - 1);
                boolean wordBoundary = Character.isLowerCase(prev)
                        || (i + 1 < name.length() && Character.isLowerCase(name.charAt(i + 1)));
                if (wordBoundary) {
                    sb.append('-');
                }
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    /** 拼接父子键：prefix 为空时直接返回 child。 */
    public static String join(String prefix, String child) {
        if (prefix == null || prefix.isEmpty()) {
            return child;
        }
        return prefix + "." + child;
    }

    /** 规范化配置键：camelCase 段转 kebab-case，其余字符（. - [ ] 数字）保持不变。 */
    public static String normalize(String key) {
        if (key == null || key.isEmpty()) {
            return key;
        }
        StringBuilder sb = new StringBuilder(key.length());
        int start = 0;
        for (int i = 0; i <= key.length(); i++) {
            if (i == key.length() || key.charAt(i) == '.' || key.charAt(i) == '[' || key.charAt(i) == ']') {
                appendSegment(sb, key.substring(start, i));
                if (i < key.length()) {
                    sb.append(key.charAt(i));
                }
                start = i + 1;
            }
        }
        return sb.toString();
    }

    private static void appendSegment(StringBuilder sb, String segment) {
        if (segment.isEmpty() || segment.equals("-")) {
            sb.append(segment);
            return;
        }
        sb.append(camelToKebab(segment));
    }

    /** 配置键转为环境变量名，例如 servers[0].host -&gt; SERVERS_0_HOST。 */
    public static String envName(String key) {
        String normalized = normalize(key);
        StringBuilder sb = new StringBuilder(normalized.length() + 2);
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '.' || c == '-' || c == '[') {
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '_') {
                    sb.append('_');
                }
            } else if (c == ']') {
                // 跳过
            } else {
                sb.append(Character.toUpperCase(c));
            }
        }
        return sb.toString();
    }
}
