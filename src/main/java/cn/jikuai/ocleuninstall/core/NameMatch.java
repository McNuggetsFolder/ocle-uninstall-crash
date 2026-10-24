package cn.jikuai.ocleuninstall.core;

import java.util.Locale;

/**
 * 目录名相似度判定。
 * <p>
 * 目标：玩家把 {@code tacz} 改成 {@code TaCZ}、{@code tacz2}、{@code tacz - 副本}、
 * {@code tacz_old}、{@code mytaczpack}、{@code tac}、{@code tazc} 之后，仍然要能命中，
 * 从而把这些目录纳入指纹检测范围。
 */
public final class NameMatch {

    private static final String TACZ = "tacz";

    private NameMatch() {
    }

    /**
     * 归一化：转小写 -> 剔除「副本 / 备份 / copy / old」之类噪声词 -> 只保留 a-z0-9。
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.toLowerCase(Locale.ROOT);
        for (String noise : GuardConst.NOISE_WORDS) {
            s = s.replace(noise, "");
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 归一化后是否「像 tacz」：包含 tacz、或四字符全同、或编辑距离 1 以内。 */
    public static boolean looksLikeTacz(String rawName) {
        String n = normalize(rawName);
        if (n.isEmpty()) {
            return false;
        }
        if (n.contains(TACZ)) {
            return true;
        }
        if (n.length() == TACZ.length() && sameChars(n, TACZ)) {
            return true;
        }
        return n.length() >= 3 && n.length() <= 6 && distance(n, TACZ) <= 1;
    }

    /** 目录名是否直接写着绝对彼方的名字（含中文、拼音、英文、命名空间）。 */
    public static boolean hitsHint(String rawName) {
        if (rawName == null) {
            return false;
        }
        String lower = rawName.toLowerCase(Locale.ROOT);
        String normalized = normalize(rawName);
        for (String hint : GuardConst.NAME_HINTS) {
            if (lower.contains(hint) || normalized.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    /** 该目录名是否值得进一步做指纹检测。 */
    public static boolean isCandidateName(String rawName) {
        return looksLikeTacz(rawName) || hitsHint(rawName);
    }

    /** 标准 Levenshtein 距离（两行滚动数组）。 */
    public static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] swap = prev;
            prev = cur;
            cur = swap;
        }
        return prev[b.length()];
    }

    /** 字符多重集是否完全相同（用来抓 tazc 这类打字颠倒）。 */
    private static boolean sameChars(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int[] counts = new int[128];
        for (int i = 0; i < a.length(); i++) {
            counts[a.charAt(i) & 0x7F]++;
            counts[b.charAt(i) & 0x7F]--;
        }
        for (int c : counts) {
            if (c != 0) {
                return false;
            }
        }
        return true;
    }
}
