package cn.jikuai.ocleuninstall.core;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 扫描游戏目录，定位所有绝对彼方产物。
 * <p>
 * 三条发现通道：
 * <ol>
 *   <li><b>标准路径</b>：{@code .minecraft/tacz}、{@code .minecraft/versions/&#42;/tacz}；</li>
 *   <li><b>相似目录</b>：深度受限遍历，目录名像 tacz / 写着绝对彼方，就纳入检测；</li>
 *   <li><b>内容指纹</b>：名字完全无关，但目录里有 ocle 枪包指纹，也纳入检测。</li>
 * </ol>
 * 通道只负责「扩大搜索范围」，最终是否命中一律由 {@link GunpackProbe} 决定：
 * 扫不到 ocle 指纹就静默放行，绝不因为「有个叫 tacz 的空目录」就误伤别人。
 */
public final class ArtifactScanner {

    private ArtifactScanner() {
    }

    public static List<Artifact> scan(Path gameDir) {
        Path root = gameDir.toAbsolutePath().normalize();
        Map<Path, String> candidates = new LinkedHashMap<>();

        // 通道 1：规格里点名的两条标准路径
        addCandidate(candidates, root.resolve(GuardConst.TARGET_DIR), "标准路径 .minecraft/tacz");
        Path versions = root.resolve(GuardConst.VERSIONS_DIR);
        if (Files.isDirectory(versions)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(versions)) {
                for (Path version : stream) {
                    if (Files.isDirectory(version)) {
                        addCandidate(candidates, version.resolve(GuardConst.TARGET_DIR), "标准路径 versions/*/tacz");
                    }
                }
            } catch (IOException ignored) {
                // versions 目录读不了就只跳过这条通道，不影响其它通道
            }
        }

        // 通道 2 + 3：深度受限遍历
        collectByWalk(root, candidates);

        List<Artifact> found = new ArrayList<>();
        collectFromCandidates(candidates, found);
        collectRootZips(root, found);
        return dedupeByAncestor(found);
    }

    private static void addCandidate(Map<Path, String> candidates, Path dir, String source) {
        if (Files.isDirectory(dir) && !candidates.containsKey(dir)) {
            candidates.put(dir, source);
        }
    }

    private static void collectByWalk(Path root, Map<Path, String> candidates) {
        try {
            Files.walkFileTree(root, Set.of(), GuardConst.MAX_DEPTH, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (dir.equals(root)) {
                        return FileVisitResult.CONTINUE;
                    }
                    String name = dir.getFileName().toString();
                    String lower = name.toLowerCase(Locale.ROOT);
                    if (name.startsWith(".") && !".minecraft".equals(lower)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (GuardConst.SKIP_DIRS.contains(lower)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (!candidates.containsKey(dir)) {
                        if (NameMatch.isCandidateName(name)) {
                            candidates.put(dir, "相似目录扫描");
                        } else if (GunpackProbe.probeDirectory(dir) != null) {
                            candidates.put(dir, "内容指纹扫描");
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // 遍历中断不影响已发现的结果
        }
    }

    private static void collectFromCandidates(Map<Path, String> candidates, List<Artifact> out) {
        for (Map.Entry<Path, String> entry : candidates.entrySet()) {
            Path dir = entry.getKey();
            String source = entry.getValue();

            Evidence self = GunpackProbe.probeDirectory(dir);
            if (self != null) {
                out.add(new Artifact(dir, self, source + " · 目录本体"));
                continue;
            }
            try (DirectoryStream<Path> children = Files.newDirectoryStream(dir)) {
                for (Path child : children) {
                    if (Files.isDirectory(child)) {
                        Evidence evidence = GunpackProbe.probeDirectory(child);
                        if (evidence != null) {
                            out.add(new Artifact(child, evidence, source + " · 子目录"));
                        }
                    } else {
                        Evidence evidence = GunpackProbe.probeZip(child);
                        if (evidence != null) {
                            out.add(new Artifact(child, evidence, source + " · 子文件"));
                        }
                    }
                }
            } catch (IOException ignored) {
                // 单个候选目录读不了就跳过
            }
        }
    }

    /** 玩家有时会把枪包 zip 直接丢在 .minecraft 根目录，这里补一刀。 */
    private static void collectRootZips(Path root, List<Artifact> out) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path path : stream) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                Evidence evidence = GunpackProbe.probeZip(path);
                if (evidence != null) {
                    out.add(new Artifact(path, evidence, "游戏根目录散落文件"));
                }
            }
        } catch (IOException ignored) {
            // 根目录读不了就算了
        }
    }

    /** 去重：一个产物被多个通道重复发现时只留一份；祖先已被整体命中时丢弃其后代。 */
    private static List<Artifact> dedupeByAncestor(List<Artifact> raw) {
        List<Artifact> sorted = new ArrayList<>(raw);
        sorted.sort(Comparator
                .comparingInt((Artifact a) -> a.path().getNameCount())
                .thenComparing(a -> a.path().toString()));
        List<Artifact> kept = new ArrayList<>();
        for (Artifact artifact : sorted) {
            boolean covered = false;
            for (Artifact existing : kept) {
                if (artifact.path().startsWith(existing.path())) {
                    covered = true;
                    break;
                }
            }
            if (!covered) {
                kept.add(artifact);
            }
        }
        return kept;
    }
}
