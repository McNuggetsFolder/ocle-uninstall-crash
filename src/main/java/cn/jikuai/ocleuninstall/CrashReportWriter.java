package cn.jikuai.ocleuninstall;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import cn.jikuai.ocleuninstall.core.Artifact;
import cn.jikuai.ocleuninstall.core.ArtifactQuarantine;
import cn.jikuai.ocleuninstall.core.Lang;
import cn.jikuai.ocleuninstall.core.LangTable;

/**
 * 自己写一份崩溃报告（纯 JDK 拼文本，不碰 {@code net.minecraft.CrashReport} 单例）。
 * <p>
 * 正文语言跟随 {@code options.txt} 里的语言设置；契约句「请勿同时加载此mod和绝对彼方」
 * 永远原样出现在报告开头，另一语言紧随其后。
 */
public final class CrashReportWriter {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");
    private static final DateTimeFormatter TIME_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private CrashReportWriter() {
    }

    public static Path write(Path gameDir, Lang lang, LangTable table, List<Artifact> hits,
                             List<ArtifactQuarantine.Report> reports, int purgedListeners,
                             Path trashBatch, Path restoreGuide) {
        Path dir = gameDir.resolve("crash-reports");
        Path file = dir.resolve("crash-" + LocalDateTime.now().format(FILE_STAMP) + "-ocle-guard.txt");
        try {
            Files.createDirectories(dir);
            Files.writeString(file,
                    build(gameDir, lang, table, hits, reports, purgedListeners, trashBatch, restoreGuide),
                    StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            return null;
        }
    }

    private static String build(Path gameDir, Lang lang, LangTable table, List<Artifact> hits,
                                List<ArtifactQuarantine.Report> reports, int purgedListeners,
                                Path trashBatch, Path restoreGuide) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("---- Minecraft Crash Report ----\n");
        sb.append("// ").append(table.get("mod.name")).append('\n').append('\n');
        sb.append("Time: ").append(LocalDateTime.now().format(TIME_STAMP)).append('\n');
        sb.append("Description: ").append(table.get("crash.description")).append('\n').append('\n');
        sb.append(LangTable.of(Lang.ZH_CN).get("banner")).append('\n');
        sb.append(LangTable.of(Lang.EN_US).get("banner")).append('\n').append('\n');

        sb.append("-- ").append(table.get("crash.section.hits")).append(" --\n");
        sb.append(table.format("crash.gamedir", gameDir)).append('\n');
        sb.append(table.format("crash.trash", trashBatch == null ? "-" : trashBatch)).append('\n');
        for (int i = 0; i < hits.size(); i++) {
            Artifact hit = hits.get(i);
            sb.append(table.format("crash.item", i + 1, hit.path())).append('\n');
            sb.append(table.format("crash.item.source", hit.source())).append('\n');
            sb.append(table.format("crash.item.evidence", hit.evidence())).append('\n');
            if (i < reports.size()) {
                sb.append(table.format("crash.item.result", reports.get(i).summary(table))).append('\n');
            }
        }
        sb.append('\n');

        sb.append("-- ").append(table.get("crash.section.restore")).append(" --\n");
        sb.append(table.get("crash.advice")).append('\n');
        sb.append(table.format("crash.restore_entry", restoreGuide == null ? "-" : restoreGuide)).append('\n');
        sb.append(table.get("crash.advice.tail")).append('\n').append('\n');

        sb.append("-- ").append(table.get("crash.section.sysinfo")).append(" --\n");
        sb.append("  Java: ").append(System.getProperty("java.version", "?"))
                .append(" (").append(System.getProperty("java.vendor", "?")).append(")\n");
        sb.append("  OS: ").append(System.getProperty("os.name", "?"))
                .append(' ').append(System.getProperty("os.version", "?")).append('\n');
        sb.append("  Language: ").append(lang.code()).append(" (").append(lang.display()).append(")\n");
        sb.append("  Max heap: ").append(Runtime.getRuntime().maxMemory() / 1024L / 1024L).append(" MB\n");
        sb.append("  Listeners unregistered: ").append(purgedListeners).append('\n').append('\n');

        sb.append("-- ").append(table.get("crash.section.stack")).append(" --\n");
        for (StackTraceElement element : new Throwable("OCLE-Guard abort").getStackTrace()) {
            sb.append("  at ").append(element).append('\n');
        }
        return sb.toString();
    }
}
