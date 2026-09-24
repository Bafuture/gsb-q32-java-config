package com.example.config.parse;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.config.exception.ConfigParseException;
import com.example.config.source.Keys;

/**
 * 自研的、带精确行号的 Properties 解析器（不依赖 java.util.Properties）。
 *
 * <p>语法规则：</p>
 * <ul>
 *   <li>空行、以 {@code #} 或 {@code !} 开头的行为注释；</li>
 *   <li>行尾以单个 {@code \} 结尾表示续行；错误行号精确定位到转义字符所在的物理行；</li>
 *   <li>键值分隔符为第一个未转义的 {@code =}、{@code :} 或空白（空格/Tab）；</li>
 *   <li>支持转义：{@code \n \t \r \\ \= \: \ } 与「反斜杠+uXXXX」Unicode 转义；</li>
 *   <li>键不能为空；同一键重复出现时后者覆盖前者。</li>
 * </ul>
 */
public final class PropertiesParser {

    private PropertiesParser() {
    }

    /** 解析文件；文件不存在时返回空 Map（配置文件是可选的）。 */
    public static Map<String, String> parse(Path file, Charset charset) {
        if (!Files.exists(file)) {
            return new LinkedHashMap<>();
        }
        try (Reader reader = Files.newBufferedReader(file, charset)) {
            return parse(reader, file);
        } catch (IOException e) {
            throw new ConfigParseException(file, -1, "无法读取文件: " + e.getMessage(), e);
        }
    }

    /** 解析字符串内容，便于测试；location 仅用于错误信息展示。 */
    public static Map<String, String> parse(String content, String location) {
        try {
            return parse(new StringReader(content), Paths.get(location));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, String> parse(Reader reader, Path file) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        String[] lines = readAllLines(reader);

        int physical = 0;
        while (physical < lines.length) {
            int entryLine = physical + 1;
            String current = stripComment(lines[physical]);
            physical++;
            if (current == null) {
                continue;
            }

            // 逻辑行 + 每个字符对应物理行号的并行缓冲（续行时后续片段使用新行号）
            StringBuilder logical = new StringBuilder(removeContinuationSlash(current));
            int[] lineMap = new int[current.length()];
            java.util.Arrays.fill(lineMap, 0, logical.length(), entryLine);

            while (endsWithOddBackslashes(current) && physical < lines.length) {
                int nextLine = physical + 1;
                current = stripComment(lines[physical]);
                physical++;
                if (current == null) {
                    current = "";
                }
                String fragment = removeContinuationSlash(current);
                lineMap = appendLineMap(lineMap, logical.length(), fragment.length(), nextLine);
                logical.append(fragment);
            }

            parseEntry(logical.toString(), lineMap, file, entryLine, result);
        }
        return result;
    }

    private static int[] appendLineMap(int[] existing, int oldLength, int added, int line) {
        int[] grown = java.util.Arrays.copyOf(existing, oldLength + added);
        java.util.Arrays.fill(grown, oldLength, grown.length, line);
        return grown;
    }

    private static String[] readAllLines(Reader reader) throws IOException {
        StringBuilder all = new StringBuilder();
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) != -1) {
            all.append(buffer, 0, read);
        }
        String normalized = all.toString().replace("\r\n", "\n").replace('\r', '\n');
        return normalized.split("\n", -1);
    }

    /** 去掉前导空白后判断是否为注释/空行；返回去掉前导空白后的内容，空行/注释返回 null。 */
    private static String stripComment(String line) {
        int i = 0;
        while (i < line.length() && isSpace(line.charAt(i))) {
            i++;
        }
        if (i == line.length()) {
            return null;
        }
        char first = line.charAt(i);
        if (first == '#' || first == '!') {
            return null;
        }
        return line.substring(i);
    }

    private static String removeContinuationSlash(String line) {
        return endsWithOddBackslashes(line) ? line.substring(0, line.length() - 1) : line;
    }

    private static boolean endsWithOddBackslashes(String s) {
        int count = 0;
        for (int i = s.length() - 1; i >= 0 && s.charAt(i) == '\\'; i--) {
            count++;
        }
        return count % 2 == 1;
    }

    private static void parseEntry(String logical, int[] lineMap, Path file, int entryLine,
                                   Map<String, String> result) {
        int pos = 0;
        StringBuilder key = new StringBuilder();

        while (pos < logical.length()) {
            char c = logical.charAt(pos);
            if (c == '\\' && pos + 1 < logical.length()) {
                key.append(readEscape(logical, pos, file, lineMap[pos]));
                pos += escapeLength(logical, pos);
            } else if (c == '=' || c == ':' || isSpace(c)) {
                break;
            } else {
                key.append(c);
                pos++;
            }
        }

        String keyText = key.toString().trim();
        if (keyText.isEmpty()) {
            throw new ConfigParseException(file, entryLine, "缺少配置键（键不能为空）");
        }

        while (pos < logical.length() && isSpace(logical.charAt(pos))) {
            pos++;
        }
        if (pos < logical.length() && (logical.charAt(pos) == '=' || logical.charAt(pos) == ':')) {
            pos++;
        }
        while (pos < logical.length() && isSpace(logical.charAt(pos))) {
            pos++;
        }

        String rawValue = stripTrailingSpaces(logical.substring(pos));
        int[] valueLineMap = java.util.Arrays.copyOfRange(lineMap, pos, pos + rawValue.length());
        String value = unescape(rawValue, valueLineMap, file);
        result.put(Keys.normalize(keyText), value);
    }

    /** 去掉行尾未转义的空白；用反斜杠转义的空格（反斜杠数为奇数）保留。 */
    private static String stripTrailingSpaces(String s) {
        int end = s.length();
        while (end > 0 && isSpace(s.charAt(end - 1))) {
            int slashCount = 0;
            for (int i = end - 2; i >= 0 && s.charAt(i) == '\\'; i--) {
                slashCount++;
            }
            if (slashCount % 2 == 1) {
                break;
            }
            end--;
        }
        return s.substring(0, end);
    }

    private static int escapeLength(String s, int pos) {
        return s.charAt(pos + 1) == 'u' ? 6 : 2;
    }

    /** 读取 pos 处的转义序列（{@code \} 所在位置）并返回其代表的字符。 */
    private static char readEscape(String s, int pos, Path file, int line) {
        char next = s.charAt(pos + 1);
        switch (next) {
            case 'n': return '\n';
            case 't': return '\t';
            case 'r': return '\r';
            case '\\': return '\\';
            case '=': return '=';
            case ':': return ':';
            case '#': return '#';
            case '!': return '!';
            case ' ': return ' ';
            case 'u':
                if (pos + 6 > s.length()) {
                    throw new ConfigParseException(file, line, "非法的 Unicode 转义（反斜杠+u），需要 4 位十六进制数");
                }
                String hex = s.substring(pos + 2, pos + 6);
                try {
                    return (char) Integer.parseInt(hex, 16);
                } catch (NumberFormatException e) {
                    throw new ConfigParseException(file, line, "非法的 Unicode 转义（反斜杠+u）" + hex + "，不是十六进制数");
                }
            default:
                throw new ConfigParseException(file, line, "非法的转义序列: \\" + next);
        }
    }

    private static String unescape(String s, int[] lineMap, Path file) {
        StringBuilder sb = new StringBuilder(s.length());
        int pos = 0;
        while (pos < s.length()) {
            char c = s.charAt(pos);
            if (c == '\\' && pos + 1 < s.length()) {
                sb.append(readEscape(s, pos, file, lineMap[pos]));
                pos += escapeLength(s, pos);
            } else {
                sb.append(c);
                pos++;
            }
        }
        return sb.toString();
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\f';
    }
}
