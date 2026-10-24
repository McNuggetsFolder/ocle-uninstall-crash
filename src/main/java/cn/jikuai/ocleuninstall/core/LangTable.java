package cn.jikuai.ocleuninstall.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语言表：从 jar 里的 {@code /assets/ocle_uninstall_crash/lang/<lang>.json} 读取键值。
 * <p>
 * 故意不依赖 Gson：这里只需要「扁平字符串 map」，一个正则加转义还原就够了，
 * 这样 core 包依旧是纯 JDK，可以脱离 Forge 编译与自测。
 * <p>
 * 找不到键时返回键本身（而不是抛异常），保证任何情况下都不会因为缺翻译而二次崩溃。
 */
public final class LangTable {

    private static final Pattern PAIR = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\}");
    private static final String RESOURCE_ROOT = "/assets/ocle_uninstall_crash/lang/";

    private final Lang lang;
    private final Map<String, String> entries;

    private LangTable(Lang lang, Map<String, String> entries) {
        this.lang = lang;
        this.entries = Collections.unmodifiableMap(entries);
    }

    /** 加载指定语言；资源缺失时退化为「键即值」。 */
    public static LangTable of(Lang lang) {
        Lang resolved = lang == null ? Lang.ZH_CN : lang;
        Map<String, String> entries = new HashMap<>();
        String resource = RESOURCE_ROOT + resolved.code() + ".json";
        try (InputStream in = LangTable.class.getResourceAsStream(resource)) {
            if (in != null) {
                entries.putAll(parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            }
        } catch (IOException ignored) {
            // 读不到就用键名兜底，绝不让日志/崩溃报告阶段抛异常
        }
        if (entries.isEmpty()) {
            // 极端情况：资源没打进 jar。至少保证契约句还在。
            entries.put("banner", GuardConst.BANNER);
        }
        return new LangTable(resolved, entries);
    }

    public Lang lang() {
        return lang;
    }

    public String get(String key) {
        return entries.getOrDefault(key, key);
    }

    /** 按顺序替换 {@code {}} 占位符。 */
    public String format(String key, Object... args) {
        String text = get(key);
        for (Object arg : args) {
            text = PLACEHOLDER.matcher(text).replaceFirst(Matcher.quoteReplacement(String.valueOf(arg)));
        }
        return text;
    }

    public boolean has(String key) {
        return entries.containsKey(key);
    }

    /** 全部键名，用于自测时校验两种语言键集合是否一致。 */
    public java.util.Set<String> keys() {
        return entries.keySet();
    }

    /** 解析扁平的 {@code {"k":"v"}} JSON，容忍注释外的任意空白与换行。 */
    public static Map<String, String> parse(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.isEmpty()) {
            return map;
        }
        Matcher matcher = PAIR.matcher(json);
        while (matcher.find()) {
            map.put(matcher.group(1), unescape(matcher.group(2)));
        }
        return map;
    }

    private static String unescape(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '\\' || i + 1 >= raw.length()) {
                sb.append(c);
                continue;
            }
            char next = raw.charAt(++i);
            switch (next) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case 'u' -> {
                    if (i + 4 < raw.length()) {
                        try {
                            sb.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException e) {
                            sb.append(next);
                        }
                    } else {
                        sb.append(next);
                    }
                }
                default -> sb.append(next);
            }
        }
        return sb.toString();
    }
}
