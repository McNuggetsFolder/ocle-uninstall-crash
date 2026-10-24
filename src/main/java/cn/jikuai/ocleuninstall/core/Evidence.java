package cn.jikuai.ocleuninstall.core;

/**
 * 一次命中的「证据」：说明凭什么判定这个目录/压缩包属于绝对彼方。
 *
 * @param kind   证据类型，例如 {@code gunpack.meta.json}、{@code zip:assets/ocle}
 * @param detail 证据细节，例如 {@code namespace=ocle}
 */
public record Evidence(String kind, String detail) {

    public Evidence {
        kind = kind == null || kind.isBlank() ? "unknown" : kind;
        detail = detail == null ? "" : detail;
    }

    @Override
    public String toString() {
        return detail.isEmpty() ? kind : kind + " -> " + detail;
    }
}
