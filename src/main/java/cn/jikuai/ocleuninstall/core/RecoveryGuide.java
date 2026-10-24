package cn.jikuai.ocleuninstall.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 在回收站批次目录里写一份 {@code RESTORE.txt}：告诉玩家东西没丢、怎么搬回去。
 * <p>
 * 双语输出（主语言在前、另一语言在后），因为这份文件是给「已经懵了的玩家」看的，
 * 不能假设他还记得自己设的是哪个语言。
 */
public final class RecoveryGuide {

    private RecoveryGuide() {
    }

    public static Path write(Path batch, List<ArtifactQuarantine.Report> reports, Lang lang) throws IOException {
        StringBuilder sb = new StringBuilder(1024);
        for (Lang each : new Lang[] {lang, lang.other()}) {
            appendSection(sb, LangTable.of(each), reports);
        }
        Path file = batch.resolve(GuardConst.TRASH_RESTORE_GUIDE);
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        return file;
    }

    private static void appendSection(StringBuilder sb, LangTable table, List<ArtifactQuarantine.Report> reports) {
        sb.append("==================================================\n");
        sb.append(table.get("restore.title")).append('\n');
        sb.append("==================================================\n\n");
        sb.append(table.get("restore.intro")).append("\n\n");

        int index = 1;
        for (ArtifactQuarantine.Report report : reports) {
            if (!report.success()) {
                continue;
            }
            sb.append(index++).append(". ").append(table.get("restore.from")).append('\n')
                    .append("   ").append(report.movedTo()).append('\n')
                    .append("   ").append(table.get("restore.to")).append('\n')
                    .append("   ").append(report.original()).append("\n\n");
        }

        sb.append(table.get("restore.steps")).append("\n\n");
        sb.append(table.get("restore.note")).append("\n\n");
    }
}
