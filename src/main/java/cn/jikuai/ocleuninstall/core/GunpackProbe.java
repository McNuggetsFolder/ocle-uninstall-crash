package cn.jikuai.ocleuninstall.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 绝对彼方枪包指纹探测。
 * <p>
 * 只看三件事，全部基于 TaCZ 枪包的标准结构：
 * <ol>
 *   <li>目录/压缩包根部存在 {@code gunpack.meta.json} 且 {@code namespace == ocle}；</li>
 *   <li>存在 {@code assets/ocle/} 资源目录（目录型或 zip 条目）；</li>
 *   <li>以上都没有 -> 不是绝对彼方，一律不判定。</li>
 * </ol>
 * 不使用 Gson，改用正则匹配 namespace 字段，这样枪包里夹带 {@code //} 注释也不会解析失败。
 */
public final class GunpackProbe {

    private static final Pattern NAMESPACE = Pattern.compile("\"namespace\"\\s*:\\s*\"([^\"]{1,64})\"");
    private static final long MAX_META_BYTES = 8L * 1024L;
    private static final int MAX_ENTRIES = 50_000;
    private static final String META_NAME = "gunpack.meta.json";
    private static final String ASSETS_PREFIX = "assets/" + GuardConst.TARGET_NAMESPACE + "/";

    private GunpackProbe() {
    }

    /** 探测一个目录本身是否为绝对彼方枪包目录。命中返回证据，否则返回 {@code null}。 */
    public static Evidence probeDirectory(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return null;
        }
        try {
            Path meta = dir.resolve(META_NAME);
            if (Files.isRegularFile(meta)) {
                String namespace;
                try (InputStream in = Files.newInputStream(meta)) {
                    namespace = readNamespace(in);
                }
                if (isTarget(namespace)) {
                    return new Evidence(META_NAME, "namespace=" + namespace);
                }
            }
            if (Files.isDirectory(dir.resolve("assets").resolve(GuardConst.TARGET_NAMESPACE))) {
                return new Evidence("assets/" + GuardConst.TARGET_NAMESPACE, "资源目录存在");
            }
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
        return null;
    }

    /** 探测一个 zip 是否为绝对彼方枪包（只读压缩包中央目录，不解压全包）。 */
    public static Evidence probeZip(Path zip) {
        if (zip == null || !Files.isRegularFile(zip)) {
            return null;
        }
        if (!zip.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            return null;
        }
        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            int seen = 0;
            Evidence assetsEvidence = null;
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements() && seen++ < MAX_ENTRIES) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith(ASSETS_PREFIX)) {
                    // 先记下，继续找更有说服力的 gunpack.meta.json
                    if (assetsEvidence == null) {
                        assetsEvidence = new Evidence("zip:" + ASSETS_PREFIX, "包内资源目录存在");
                    }
                    continue;
                }
                if (!entry.isDirectory() && isMetaEntry(name)) {
                    String namespace;
                    try (InputStream in = zipFile.getInputStream(entry)) {
                        namespace = readNamespace(in);
                    }
                    if (isTarget(namespace)) {
                        return new Evidence("zip:" + META_NAME, "namespace=" + namespace);
                    }
                }
            }
            return assetsEvidence;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isMetaEntry(String entryName) {
        if (entryName.equals(META_NAME)) {
            return true;
        }
        return entryName.endsWith("/" + META_NAME) && entryName.indexOf('/') == entryName.length() - META_NAME.length() - 1;
    }

    private static boolean isTarget(String namespace) {
        return namespace != null && GuardConst.TARGET_NAMESPACE.equalsIgnoreCase(namespace.trim());
    }

    /** 只读前 8KB，正则抓 namespace；顺手吃掉 UTF-8 BOM。 */
    private static String readNamespace(InputStream in) throws IOException {
        byte[] buffer = new byte[(int) MAX_META_BYTES];
        int length = 0;
        int read;
        while (length < buffer.length && (read = in.read(buffer, length, buffer.length - length)) > 0) {
            length += read;
        }
        String text = new String(buffer, 0, length, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        Matcher matcher = NAMESPACE.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}
