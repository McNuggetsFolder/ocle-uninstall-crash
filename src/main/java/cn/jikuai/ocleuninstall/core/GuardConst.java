package cn.jikuai.ocleuninstall.core;

import java.util.List;
import java.util.Set;

/**
 * 全部硬编码常量。
 * <p>
 * 本模组刻意不提供任何配置文件：一旦允许玩家改配置，玩家就能把检测关掉，
 * 「加载绝对彼方就卸载崩溃」也就失去意义了。
 */
public final class GuardConst {

    /** 必须原样出现在 MC 日志与崩溃报告里的提示语。 */
    public static final String BANNER = "请勿同时加载此mod和绝对彼方";

    /** 绝对彼方枪包在 TaCZ 里的命名空间（gunpack.meta.json 的 namespace 字段）。 */
    public static final String TARGET_NAMESPACE = "ocle";

    /** 绝对彼方在 TaCZ 里的标准枪包目录名。 */
    public static final String TARGET_DIR = "tacz";

    /** 版本隔离目录名。 */
    public static final String VERSIONS_DIR = "versions";

    /** 回收站目录（相对游戏目录），软删除的产物都移到这里。 */
    public static final String TRASH_DIR = "ocle_guard_trash";

    /** 每批移动的记录文件。 */
    public static final String TRASH_MANIFEST = "manifest.txt";

    /** 回收站里的恢复说明。 */
    public static final String TRASH_RESTORE_GUIDE = "RESTORE.txt";

    /**
     * 目录名线索：命中任意一条，该目录就会被纳入指纹检测范围。
     * 注意这只是「扩大搜索范围」，不是判定依据——真正的判定永远看内容指纹。
     */
    public static final List<String> NAME_HINTS = List.of(
            "tacz", "绝对彼方", "jueduibifang", "absoluteopponent", "ocle");

    /** 归一化目录名时剔除的噪声词（小写）。 */
    public static final String[] NOISE_WORDS = {
            "副本", "备份", "拷贝", "修改版", "汉化版", "汉化", "modified",
            "copy", "backup", "bak", "old", "new"
    };

    /** 遍历游戏目录时的最大深度（.minecraft 自身记为 0，versions/x/tacz 记为 3）。 */
    public static final int MAX_DEPTH = 4;

    /** 遍历时整棵跳过的目录名（小写），避免在存档、模组、资源包上浪费时间。 */
    public static final Set<String> SKIP_DIRS = Set.of(
            "mods", "config", "saves", "logs", "crash-reports", "libraries",
            "resourcepacks", "shaderpacks", "screenshots", "cache",
            "assets", "data", "meta-inf", ".git", "downloads",
            // 回收站必须跳过：否则下次启动会把隔离区里的枪包再抓一遍，形成无限套娃
            "ocle_guard_trash");

    /** 删除保护：目标路径里只要出现这些目录名，一律拒绝移动。 */
    public static final Set<String> PROTECTED_SEGMENTS = Set.of(
            "mods", "config", "saves", "logs", "crash-reports", "libraries",
            "resourcepacks", "shaderpacks", "screenshots", "coremods",
            "ocle_guard_trash");

    private GuardConst() {
    }
}
