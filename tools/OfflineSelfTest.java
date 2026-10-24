import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import cn.jikuai.ocleuninstall.core.Artifact;
import cn.jikuai.ocleuninstall.core.ArtifactQuarantine;
import cn.jikuai.ocleuninstall.core.ArtifactScanner;
import cn.jikuai.ocleuninstall.core.GuardConst;
import cn.jikuai.ocleuninstall.core.GunpackProbe;
import cn.jikuai.ocleuninstall.core.Lang;
import cn.jikuai.ocleuninstall.core.LangTable;
import cn.jikuai.ocleuninstall.core.NameMatch;
import cn.jikuai.ocleuninstall.core.RecoveryGuide;

/**
 * 离线自测：不依赖 Forge，只编译 core 包 + lang 资源，在真实文件系统上验证
 * 「指纹识别 / 目录名相似 / 语言切换 / 软删除与恢复 / 误伤护栏 / 静默放行」。
 *
 * 运行：tools\run-selftest.bat
 */
public final class OfflineSelfTest {

    private static int passed;
    private static int failed;
    private static final List<String> FAILURES = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        installTee(System.getProperty("ocle.report"));
        Path work = Files.createTempDirectory("ocle-guard-selftest-");
        System.out.println("提示语常量: " + GuardConst.BANNER);
        System.out.println("临时工作目录: " + work);
        System.out.println();

        testNameMatch();
        testLanguage(work);
        testRealGunpack(work);
        testQuarantine(work);
        testSilentPass(work);
        testGuards(work);

        System.out.println();
        System.out.println("================ 结果 ================");
        System.out.println("通过: " + passed + "    失败: " + failed);
        if (failed > 0) {
            for (String f : FAILURES) {
                System.out.println("  FAIL -> " + f);
            }
            System.exit(1);
        }
        System.out.println("全部通过。");
    }

    // ------------------------------------------------------------------ 用例

    private static void testNameMatch() {
        section("目录名相似度");
        expectTrue("TaCZ 大小写变体", NameMatch.looksLikeTacz("TaCZ"));
        expectTrue("tacz2 带序号", NameMatch.looksLikeTacz("tacz2"));
        expectTrue("tacz - 副本（噪声词+符号）", NameMatch.looksLikeTacz("tacz - 副本"));
        expectTrue("tacz_old 后缀", NameMatch.looksLikeTacz("tacz_old"));
        expectTrue("mytaczpack 子串", NameMatch.looksLikeTacz("mytaczpack"));
        expectTrue("tazc 打字颠倒", NameMatch.looksLikeTacz("tazc"));
        expectTrue("tac 少一个字母", NameMatch.looksLikeTacz("tac"));
        expectFalse("config 不应命中", NameMatch.looksLikeTacz("config"));
        expectFalse("saves 不应命中", NameMatch.looksLikeTacz("saves"));
        expectTrue("中文目录名线索", NameMatch.hitsHint("绝对彼方备份"));
        expectTrue("ocle 线索", NameMatch.hitsHint("ocle"));
        expectFalse("普通目录名不命中", NameMatch.isCandidateName("random_dir"));
    }

    private static void testLanguage(Path work) throws IOException {
        section("语言检测与语言表");
        Path zhGame = work.resolve("lang-zh/.minecraft");
        Files.createDirectories(zhGame);
        Files.writeString(zhGame.resolve("options.txt"), "version:3465\nlang:zh_cn\n", StandardCharsets.UTF_8);
        expectTrue("options.txt lang:zh_cn -> ZH_CN", Lang.detect(zhGame) == Lang.ZH_CN);

        Path enGame = work.resolve("lang-en/.minecraft");
        Files.createDirectories(enGame);
        Files.writeString(enGame.resolve("options.txt"), "version:3465\nlang:en_us\nfov:0.0\n",
                StandardCharsets.UTF_8);
        expectTrue("options.txt lang:en_us -> EN_US", Lang.detect(enGame) == Lang.EN_US);

        Path jaGame = work.resolve("lang-ja/.minecraft");
        Files.createDirectories(jaGame);
        Files.writeString(jaGame.resolve("options.txt"), "lang:ja_jp\n", StandardCharsets.UTF_8);
        expectTrue("不支持的语言 ja_jp -> 回退 ZH_CN", Lang.detect(jaGame) == Lang.ZH_CN);

        Path noFile = work.resolve("lang-none/.minecraft");
        Files.createDirectories(noFile);
        expectTrue("没有 options.txt -> 回退 ZH_CN", Lang.detect(noFile) == Lang.ZH_CN);

        LangTable zh = LangTable.of(Lang.ZH_CN);
        LangTable en = LangTable.of(Lang.EN_US);
        expectTrue("zh 的 banner 就是契约句", GuardConst.BANNER.equals(zh.get("banner")));
        expectTrue("en 的 banner 是英文", en.get("banner").contains("Absolute Opponent"));
        expectTrue("en 的 banner.en 是中文契约句", GuardConst.BANNER.equals(en.get("banner.en")));
        expectTrue("zh 的 banner.en 是英文", zh.get("banner.en").contains("Absolute Opponent"));

        Set<String> zhKeys = new LinkedHashSet<>(zh.keys());
        Set<String> enKeys = new LinkedHashSet<>(en.keys());
        expectTrue("两种语言的键集合一致（zh=" + zhKeys.size() + ", en=" + enKeys.size() + "）",
                zhKeys.equals(enKeys));
        zhKeys.removeAll(enKeys);
        expectTrue("zh 没有多余键: " + zhKeys, zhKeys.isEmpty());

        expectTrue("zh 日志用中文", zh.format("log.clean", "DIR").contains("未发现"));
        expectTrue("en 日志用英文", en.format("log.clean", "DIR").contains("no Absolute Opponent"));
        expectTrue("占位符被正确替换", en.format("log.hit_count", 3).contains("3"));
        expectTrue("未知键回退为键名", "no.such.key".equals(zh.get("no.such.key")));
    }

    private static void testRealGunpack(Path work) throws IOException {
        section("真实枪包指纹（只读）");
        Path real = Path.of("F:\\Download\\[TaCZ1.1.5+][绝对彼方] Absolute opponent OCLE v1.11.0.zip");
        if (!Files.isRegularFile(real)) {
            System.out.println("  跳过：未找到参考枪包 " + real);
            return;
        }
        long begin = System.currentTimeMillis();
        var evidence = GunpackProbe.probeZip(real);
        long cost = System.currentTimeMillis() - begin;
        expectTrue("真实枪包命中指纹（耗时 " + cost + " ms）", evidence != null);
        if (evidence != null) {
            System.out.println("  证据: " + evidence);
            expectTrue("证据指向 ocle", evidence.toString().contains("ocle"));
        }
    }

    private static void testQuarantine(Path work) throws IOException {
        section("软删除：移入回收站 + 可恢复");
        Path game = work.resolve("quarantine/.minecraft");
        writeGunpackZip(game.resolve("tacz/绝对彼方 v1.11.0.zip"), "ocle");
        writeGunpackZip(game.resolve("tacz/SomeOtherPack.zip"), "cgm");
        writeGunpackDir(game.resolve("versions/1.20.1-forge/tacz/extracted-ocle"), "ocle");
        writeGunpackZip(game.resolve("TaCZ - 副本/ocle-copy.zip"), "ocle");
        writeGunpackZip(game.resolve("mytaczpack/Other.zip"), "cgm");

        List<Artifact> hits = ArtifactScanner.scan(game);
        expectTrue("扫描命中 3 处（实际 " + hits.size() + "）", hits.size() == 3);

        long zipBytesBefore = Files.size(game.resolve("tacz/绝对彼方 v1.11.0.zip"));

        Path batch = ArtifactQuarantine.newBatch(game);
        List<ArtifactQuarantine.Report> reports = new ArrayList<>();
        for (Artifact hit : hits) {
            ArtifactQuarantine.Report report = ArtifactQuarantine.quarantine(game, hit.path(), batch);
            reports.add(report);
            ArtifactQuarantine.appendManifest(batch, report, hit.evidence());
            System.out.println("  " + report.summary(LangTable.of(Lang.ZH_CN)));
        }

        expectTrue("3 处全部移动成功", reports.stream().allMatch(ArtifactQuarantine.Report::success));
        expectTrue("原位置已不存在 ocle zip", !Files.exists(game.resolve("tacz/绝对彼方 v1.11.0.zip")));
        expectTrue("原位置已不存在 ocle 目录", !Files.exists(game.resolve("versions/1.20.1-forge/tacz/extracted-ocle")));
        expectTrue("回收站里有该 zip", Files.exists(batch.resolve("绝对彼方 v1.11.0.zip")));
        expectTrue("回收站里有该目录", Files.exists(batch.resolve("extracted-ocle")));
        expectTrue("回收站里 zip 字节数不变（数据未丢）",
                Files.size(batch.resolve("绝对彼方 v1.11.0.zip")) == zipBytesBefore);
        expectTrue("manifest.txt 已生成", Files.exists(batch.resolve(GuardConst.TRASH_MANIFEST)));
        String manifest = Files.readString(batch.resolve(GuardConst.TRASH_MANIFEST), StandardCharsets.UTF_8);
        expectTrue("manifest 记录了原始路径", manifest.contains("original=") && manifest.contains("绝对彼方"));
        expectTrue("manifest 记录了证据", manifest.contains("evidence="));

        Path guide = RecoveryGuide.write(batch, reports, Lang.EN_US);
        expectTrue("RESTORE.txt 已生成", Files.exists(guide));
        String guideText = Files.readString(guide, StandardCharsets.UTF_8);
        expectTrue("恢复说明含原始路径", guideText.contains("绝对彼方"));
        expectTrue("恢复说明双语", guideText.contains("restore") && guideText.contains("恢复"));

        // 关键：回收站必须被扫描跳过，否则下次启动会无限套娃
        expectTrue("再次扫描不再命中回收站内容", ArtifactScanner.scan(game).isEmpty());

        // 模拟恢复：把 zip 搬回原位
        Path back = game.resolve("tacz/绝对彼方 v1.11.0.zip");
        Files.createDirectories(back.getParent());
        Files.copy(batch.resolve("绝对彼方 v1.11.0.zip"), back, StandardCopyOption.REPLACE_EXISTING);
        expectTrue("从回收站复制回原位成功，内容一致",
                Files.exists(back) && Files.size(back) == Files.size(batch.resolve("绝对彼方 v1.11.0.zip")));

        expectTrue("其他枪包仍在（未误伤）", Files.exists(game.resolve("tacz/SomeOtherPack.zip")));
        expectTrue("mytaczpack 里的其他枪包仍在", Files.exists(game.resolve("mytaczpack/Other.zip")));
    }

    private static void testSilentPass(Path work) throws IOException {
        section("静默放行");
        Path game = work.resolve("clean/.minecraft");
        Files.createDirectories(game.resolve("tacz"));
        writeGunpackZip(game.resolve("tacz/SomeOtherPack.zip"), "cgm");
        expectTrue("只有 tacz 目录 + 其他枪包 -> 不触发", ArtifactScanner.scan(game).isEmpty());

        Path empty = work.resolve("empty/.minecraft");
        Files.createDirectories(empty);
        expectTrue("空游戏目录 -> 不触发", ArtifactScanner.scan(empty).isEmpty());
    }

    private static void testGuards(Path work) throws IOException {
        section("软删除护栏");
        Path game = work.resolve("guard/.minecraft");
        Files.createDirectories(game.resolve("mods"));
        Path modJar = game.resolve("mods/important.jar");
        Files.writeString(modJar, "not a gunpack", StandardCharsets.UTF_8);
        Path batch = ArtifactQuarantine.newBatch(game);

        ArtifactQuarantine.Report inMods = ArtifactQuarantine.quarantine(game, modJar, batch);
        expectTrue("拒绝移动 mods 目录内的文件", !inMods.success() && Files.exists(modJar));

        ArtifactQuarantine.Report root = ArtifactQuarantine.quarantine(game, game, batch);
        expectTrue("拒绝移动游戏目录本身", !root.success() && Files.isDirectory(game));

        Path outside = work.resolve("outside.txt");
        Files.writeString(outside, "x", StandardCharsets.UTF_8);
        ArtifactQuarantine.Report escape = ArtifactQuarantine.quarantine(game, outside, batch);
        expectTrue("拒绝移动游戏目录之外的文件", !escape.success() && Files.exists(outside));

        Path trashFile = game.resolve(GuardConst.TRASH_DIR).resolve("keep.zip");
        Files.writeString(trashFile, "x", StandardCharsets.UTF_8);
        ArtifactQuarantine.Report trash = ArtifactQuarantine.quarantine(game, trashFile, batch);
        expectTrue("拒绝把回收站里的东西再移一次", !trash.success() && Files.exists(trashFile));
    }

    // ------------------------------------------------------------------ 工具

    private static void installTee(String reportPath) throws IOException {
        if (reportPath == null || reportPath.isBlank()) {
            return;
        }
        Path target = Path.of(reportPath).toAbsolutePath();
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        PrintStream console = System.out;
        OutputStream file = new BufferedOutputStream(Files.newOutputStream(target));
        OutputStream tee = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                console.write(b);
                file.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                console.write(b, off, len);
                file.write(b, off, len);
            }

            @Override
            public void flush() throws IOException {
                console.flush();
                file.flush();
            }
        };
        System.setOut(new PrintStream(tee, true, StandardCharsets.UTF_8));
    }

    private static void writeGunpackZip(Path target, String namespace) throws IOException {
        Files.createDirectories(target.getParent());
        try (OutputStream out = Files.newOutputStream(target);
             ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("gunpack.meta.json"));
            zip.write(("{\n  \"namespace\": \"" + namespace + "\"\n}\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("assets/" + namespace + "/lang/zh_cn.json"));
            zip.write("{}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    private static void writeGunpackDir(Path dir, String namespace) throws IOException {
        Files.createDirectories(dir.resolve("assets").resolve(namespace));
        Files.createDirectories(dir.resolve("data").resolve(namespace));
        Files.writeString(dir.resolve("gunpack.meta.json"),
                "{\n  \"namespace\": \"" + namespace + "\"\n}\n", StandardCharsets.UTF_8);
    }

    private static void section(String name) {
        System.out.println("---- " + name + " ----");
    }

    private static void expectTrue(String what, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  [PASS] " + what);
        } else {
            failed++;
            FAILURES.add(what);
            System.out.println("  [FAIL] " + what);
        }
    }

    private static void expectFalse(String what, boolean condition) {
        expectTrue(what, !condition);
    }
}
