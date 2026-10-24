package cn.jikuai.ocleuninstall.core;

import java.nio.file.Path;

/**
 * 一处待卸载的绝对彼方产物：可能是一个枪包目录、一个枪包 zip，或整个被改名成枪包的目录。
 *
 * @param path     产物绝对路径
 * @param evidence 判定证据
 * @param source   发现途径（标准路径 / 相似目录扫描 / 内容指纹扫描 …）
 */
public record Artifact(Path path, Evidence evidence, String source) {

    @Override
    public String toString() {
        return path + " [" + source + " | " + evidence + "]";
    }
}
