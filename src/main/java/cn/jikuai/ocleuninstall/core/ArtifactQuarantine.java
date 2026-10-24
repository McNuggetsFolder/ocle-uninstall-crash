package cn.jikuai.ocleuninstall.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 软删除：把绝对彼方产物<b>移动</b>到 {@code .minecraft/ocle_guard_trash/<时间戳>/}，而不是抹掉。
 * <p>
 * 三项保证：
 * <ol>
 *   <li><b>数据零丢失</b>：同一分区内 {@code Files.move} 只改目录项，不改文件内容，原字节、原时间戳全保留；</li>
 *   <li><b>可追溯</b>：每个批次目录写 {@code manifest.txt}，记录原始路径、文件数、字节数、判定证据；</li>
 *   <li><b>可恢复</b>：同目录写 {@code RESTORE.txt}，照着一行命令搬回去即可。</li>
 * </ol>
 * 移动前仍要过护栏：目标须在游戏目录内、不能是游戏目录本身、路径不得命中保护目录、不得是符号链接。
 */
public final class ArtifactQuarantine {

    /**
     * 一次软删除的结果。
     *
     * @param original 原始路径
     * @param movedTo  回收站中的新路径；失败时为 {@code null}
     * @param success  是否成功移动
     * @param files    文件数
     * @param dirs     目录数
     * @param bytes    字节数
     * @param millis   耗时
     * @param errors   失败原因
     */
    public record Report(Path original, Path movedTo, boolean success, int files, int dirs, long bytes,
                         long millis, List<String> errors) {

        public String summary(LangTable table) {
            if (!success) {
                return table.format("log.move_failed", original, String.join("; ", errors));
            }
            return table.format("log.moved", movedTo, files, dirs, bytes, millis);
        }
    }

    private static final DateTimeFormatter BATCH_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private ArtifactQuarantine() {
    }

    /** 新建一个批次目录，用于同一轮的所有产物。 */
    public static Path newBatch(Path gameDir) throws IOException {
        Path trash = gameDir.resolve(GuardConst.TRASH_DIR);
        Files.createDirectories(trash);
        String stamp = LocalDateTime.now().format(BATCH_STAMP);
        Path batch = trash.resolve(stamp);
        int suffix = 1;
        while (Files.exists(batch, LinkOption.NOFOLLOW_LINKS)) {
            batch = trash.resolve(stamp + "-" + suffix++);
        }
        Files.createDirectories(batch);
        return batch;
    }

    /** 是否存在历史回收站内容（用于启动时提示「还有 N 个批次可恢复」）。 */
    public static int countBatches(Path gameDir) {
        Path trash = gameDir.resolve(GuardConst.TRASH_DIR);
        if (!Files.isDirectory(trash)) {
            return 0;
        }
        try (var stream = Files.newDirectoryStream(trash)) {
            int count = 0;
            for (Path ignored : stream) {
                count++;
            }
            return count;
        } catch (IOException e) {
            return 0;
        }
    }

    public static Report quarantine(Path gameDir, Path target, Path batch) {
        long start = System.currentTimeMillis();
        Path root = gameDir.toAbsolutePath().normalize();
        Path source = target.toAbsolutePath().normalize();
        List<String> errors = new ArrayList<>();

        String refusal = guard(root, source);
        if (refusal != null) {
            errors.add(refusal);
            return new Report(source, null, false, 0, 0, 0L, System.currentTimeMillis() - start, errors);
        }
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
            errors.add("not found: " + source);
            return new Report(source, null, false, 0, 0, 0L, System.currentTimeMillis() - start, errors);
        }
        if (Files.isSymbolicLink(source)) {
            errors.add("refused symlink: " + source);
            return new Report(source, null, false, 0, 0, 0L, System.currentTimeMillis() - start, errors);
        }

        Counter counter = new Counter();
        try {
            measure(source, counter);
        } catch (IOException e) {
            errors.add("measure failed: " + e);
        }

        Path destination;
        try {
            Files.createDirectories(batch);
            destination = uniqueTarget(batch, source);
            move(source, destination);
        } catch (IOException | RuntimeException e) {
            errors.add(String.valueOf(e));
            return new Report(source, null, false, counter.files, counter.dirs, counter.bytes,
                    System.currentTimeMillis() - start, errors);
        }

        return new Report(source, destination, true, counter.files, counter.dirs, counter.bytes,
                System.currentTimeMillis() - start, errors);
    }

    /** 把一次移动记录追加到批次目录的 manifest.txt。 */
    public static void appendManifest(Path batch, Report report, Evidence evidence) {
        StringBuilder sb = new StringBuilder();
        sb.append("time=").append(LocalDateTime.now()).append('\n');
        sb.append("original=").append(report.original()).append('\n');
        sb.append("moved_to=").append(report.movedTo()).append('\n');
        sb.append("success=").append(report.success()).append('\n');
        sb.append("files=").append(report.files()).append('\n');
        sb.append("dirs=").append(report.dirs()).append('\n');
        sb.append("bytes=").append(report.bytes()).append('\n');
        if (evidence != null) {
            sb.append("evidence=").append(evidence).append('\n');
        }
        if (!report.errors().isEmpty()) {
            sb.append("errors=").append(String.join("; ", report.errors())).append('\n');
        }
        sb.append("----\n");
        try {
            Files.writeString(batch.resolve(GuardConst.TRASH_MANIFEST), sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // 记录失败不影响主流程
        }
    }

    private static void move(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, destination);
        }
    }

    private static Path uniqueTarget(Path batch, Path source) {
        Path name = source.getFileName();
        Path candidate = batch.resolve(name);
        int suffix = 1;
        while (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            candidate = batch.resolve(name.toString() + "-" + suffix++);
        }
        return candidate;
    }

    private static void measure(Path target, Counter counter) throws IOException {
        if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            counter.files++;
            counter.bytes += Files.size(target);
            return;
        }
        Files.walkFileTree(target, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                counter.dirs++;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                counter.files++;
                counter.bytes += attrs.size();
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String guard(Path root, Path target) {
        if (!target.startsWith(root)) {
            return "refused outside game dir: " + target;
        }
        if (target.equals(root)) {
            return "refused game dir itself";
        }
        Path relative = root.relativize(target);
        if (relative.getNameCount() < 1) {
            return "refused: path too shallow";
        }
        for (Path part : relative) {
            String segment = part.toString().toLowerCase(Locale.ROOT);
            if (GuardConst.PROTECTED_SEGMENTS.contains(segment)) {
                return "refused protected segment: " + segment;
            }
        }
        return null;
    }

    /** 统计容器：匿名内部类要改计数。 */
    private static final class Counter {
        private int files;
        private int dirs;
        private long bytes;
    }
}
