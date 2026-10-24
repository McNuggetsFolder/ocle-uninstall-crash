package cn.jikuai.ocleuninstall.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * 模组支持的语言（第二语言即 {@link #EN_US}）。
 * <p>
 * <b>为什么不用 I18n</b>：本模组的判定发生在 mod 构造阶段，那时 Minecraft 客户端还没创建，
 * 语言管理器、资源包都未加载，{@code I18n.get()} 拿不到任何东西。
 * 但玩家的语言选择早就写在 {@code .minecraft/options.txt} 的 {@code lang:} 行里了，
 * 直接读文件反而是这个阶段唯一可靠的办法。
 * <p>
 * 识别顺序：{@code options.txt} 的 {@code lang:} -> 语言前缀匹配 -> 默认简体中文。
 */
public enum Lang {

    /** 简体中文，默认语言。 */
    ZH_CN("zh_cn", "简体中文"),

    /** 英语，第二语言。 */
    EN_US("en_us", "English");

    private final String code;
    private final String display;

    Lang(String code, String display) {
        this.code = code;
        this.display = display;
    }

    /** 语言代码，与 lang 文件名一致（{@code zh_cn} / {@code en_us}）。 */
    public String code() {
        return code;
    }

    /** 用于日志展示的语言名。 */
    public String display() {
        return display;
    }

    /** 另一种语言，用于「契约句 + 第二语言」双语输出。 */
    public Lang other() {
        return this == ZH_CN ? EN_US : ZH_CN;
    }

    /** 把 {@code en_US} / {@code en-us} / {@code en} 之类的写法统一到支持的语言。 */
    public static Lang of(String code) {
        if (code == null) {
            return ZH_CN;
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (normalized.startsWith("en")) {
            return EN_US;
        }
        return ZH_CN;
    }

    /** 读取玩家的语言设置；文件不存在或读不到时回退到简体中文。 */
    public static Lang detect(Path gameDir) {
        if (gameDir == null) {
            return ZH_CN;
        }
        Path options = gameDir.resolve("options.txt");
        if (!Files.isRegularFile(options)) {
            return ZH_CN;
        }
        try {
            List<String> lines = Files.readAllLines(options, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.startsWith("lang:")) {
                    return of(line.substring("lang:".length()));
                }
            }
        } catch (IOException ignored) {
            // options.txt 读不了就按默认语言处理，不能因此让启动失败
        }
        return ZH_CN;
    }
}
