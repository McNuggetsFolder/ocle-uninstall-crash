package cn.jikuai.ocleuninstall;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.Logger;

import cn.jikuai.ocleuninstall.core.Artifact;
import cn.jikuai.ocleuninstall.core.ArtifactQuarantine;
import cn.jikuai.ocleuninstall.core.ArtifactScanner;
import cn.jikuai.ocleuninstall.core.GuardConst;
import cn.jikuai.ocleuninstall.core.Lang;
import cn.jikuai.ocleuninstall.core.LangTable;
import cn.jikuai.ocleuninstall.core.RecoveryGuide;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * 执行体：读语言 -> 扫描 -> 软删除（移入回收站）-> 写恢复说明 -> 自清 -> 写崩溃报告 -> 抛异常。
 * <p>
 * 与旧版的区别：产物不再被删除，而是整体移动到 {@code .minecraft/ocle_guard_trash/<时间戳>/}，
 * 原路径、字节数、时间戳全部保留，玩家随时能搬回去。
 * <p>
 * 语言：崩溃发生在 mod 构造阶段，Minecraft 的语言系统尚未加载，所以这里读
 * {@code options.txt} 的 {@code lang:} 值来决定输出语言；契约句「请勿同时加载此mod和绝对彼方」
 * 无论什么语言都会原样输出（另一语言紧随其后）。
 */
public final class ConflictReactor {

    private static final Logger LOG = OcleUninstallCrash.LOGGER;
    private static final String RULE = "==================================================";

    private ConflictReactor() {
    }

    public static void react() {
        Path gameDir = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        Lang lang = Lang.detect(gameDir);
        LangTable table = LangTable.of(lang);

        int history = ArtifactQuarantine.countBatches(gameDir);
        if (history > 0) {
            LOG.warn("[OCLE-Guard] {}", table.format("log.trash_history", history));
        }

        List<Artifact> hits = ArtifactScanner.scan(gameDir);
        if (hits.isEmpty()) {
            LOG.info("[OCLE-Guard] {}", table.format("log.clean", gameDir));
            return;
        }

        printBanner(lang);
        LOG.error("[OCLE-Guard] {}", table.format("log.hit_count", hits.size()));

        Path batch = null;
        try {
            batch = ArtifactQuarantine.newBatch(gameDir);
        } catch (IOException e) {
            LOG.error("[OCLE-Guard] 无法创建回收站目录: {}", e.toString());
        }

        List<ArtifactQuarantine.Report> reports = new ArrayList<>(hits.size());
        for (Artifact hit : hits) {
            LOG.error("[OCLE-Guard] {}", table.format("log.hit", hit.path()));
            LOG.error("[OCLE-Guard] {}", table.format("log.source", hit.source(), hit.evidence()));
            if (batch == null) {
                continue;
            }
            ArtifactQuarantine.Report report = ArtifactQuarantine.quarantine(gameDir, hit.path(), batch);
            reports.add(report);
            ArtifactQuarantine.appendManifest(batch, report, hit.evidence());
            LOG.error("[OCLE-Guard] {}", report.summary(table));
        }

        Path restoreGuide = null;
        if (batch != null) {
            try {
                restoreGuide = RecoveryGuide.write(batch, reports, lang);
                LOG.error("[OCLE-Guard] {}", table.format("log.restore_guide", restoreGuide));
            } catch (IOException e) {
                LOG.error("[OCLE-Guard] 恢复说明写入失败: {}", e.toString());
            }
        }

        int purged = SelfPurge.purge();
        LOG.error("[OCLE-Guard] {}", table.format("log.purged", purged));

        Path reportFile = CrashReportWriter.write(gameDir, lang, table, hits, reports, purged, batch, restoreGuide);
        LOG.error("[OCLE-Guard] {}",
                table.format("log.report", reportFile == null ? "-" : String.valueOf(reportFile)));
        printBanner(lang);

        String message = GuardConst.BANNER + " / " + LangTable.of(lang.other()).get("banner")
                + " | " + table.format("crash.exception", hits.size(), batch == null ? "-" : batch);
        throw new IllegalStateException(message);
    }

    /** 契约句必定输出：主语言在前，另一语言紧随其后。 */
    private static void printBanner(Lang lang) {
        String primary = LangTable.of(lang).get("banner");
        String secondary = LangTable.of(lang.other()).get("banner");
        LOG.error(RULE);
        LOG.error("  {}", primary);
        LOG.error("  {}", secondary);
        LOG.error(RULE);
        System.err.println(RULE);
        System.err.println("  " + primary);
        System.err.println("  " + secondary);
        System.err.println(RULE);
        System.err.flush();
    }
}
