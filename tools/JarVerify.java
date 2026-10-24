import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Jar 链接自检：在真实的 Forge 库上用 URLClassLoader 加载模组类，
 * 但用 initialize=false，确保不会触发构造函数里的扫描/删除逻辑。
 * <p>
 * 目的：证明这个 jar 在真实 Forge 环境下「类能加载、注解能读到、监听方法签名对得上」，
 * 而不是只有 Gradle 能编过。
 * <p>
 * 用法：java -cp . JarVerify &lt;mod.jar&gt; &lt;forge库1.jar&gt; &lt;forge库2.jar&gt; ...
 */
public final class JarVerify {

    private static int failed;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("usage: JarVerify <mod.jar> <lib1.jar> [lib2.jar ...]");
            System.exit(2);
        }
        List<URL> urls = new ArrayList<>();
        for (String a : args) {
            urls.add(new File(a).toURI().toURL());
        }
        try (URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]),
                ClassLoader.getPlatformClassLoader())) {

            String modClassName = "cn.jikuai.ocleuninstall.OcleUninstallCrash";
            Class<?> modClass = Class.forName(modClassName, false, loader);
            check("模组类可加载: " + modClass.getName(), true);

            String modId = readModAnnotation(modClass);
            check("@Mod 注解可读，value=" + modId, "ocle_uninstall_crash".equals(modId));

            check("存在无参构造器（Forge 实例化入口）",
                    modClass.getDeclaredConstructor() != null);

            Class<?> eventClass = Class.forName(
                    "net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent", false, loader);
            Method listener = modClass.getMethod("onLoadComplete", eventClass);
            check("监听方法签名正确: onLoadComplete(" + eventClass.getSimpleName() + ")", listener != null);

            Class<?> subscribe = Class.forName(
                    "net.minecraftforge.eventbus.api.SubscribeEvent", false, loader);
            check("监听方法带 @SubscribeEvent", listener.isAnnotationPresent(asAnnotation(subscribe)));

            Class<?> paths = Class.forName("net.minecraftforge.fml.loading.FMLPaths", false, loader);
            check("FMLPaths 可解析（游戏目录入口）", paths.isEnum());

            Class<?> scanner = Class.forName(
                    "cn.jikuai.ocleuninstall.core.ArtifactScanner", false, loader);
            check("core 包随 jar 一起发布: " + scanner.getName(), scanner != null);

            check("语言资源 zh_cn 在 jar 内",
                    modClass.getResource("/assets/ocle_uninstall_crash/lang/zh_cn.json") != null);
            check("语言资源 en_us 在 jar 内",
                    modClass.getResource("/assets/ocle_uninstall_crash/lang/en_us.json") != null);
        }

        System.out.println();
        System.out.println(failed == 0 ? "JAR VERIFY OK" : "JAR VERIFY FAILED (" + failed + ")");
        System.exit(failed == 0 ? 0 : 1);
    }

    /** 用反射读 @Mod 的 value，避免编译期依赖 Forge。 */
    private static String readModAnnotation(Class<?> modClass) throws Exception {
        for (Annotation annotation : modClass.getAnnotations()) {
            if ("net.minecraftforge.fml.common.Mod".equals(annotation.annotationType().getName())) {
                Method value = annotation.annotationType().getMethod("value");
                return String.valueOf(value.invoke(annotation));
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Class<? extends Annotation> asAnnotation(Class<?> type) {
        return (Class<? extends Annotation>) (Class) type;
    }

    private static void check(String what, boolean ok) {
        if (!ok) {
            failed++;
        }
        System.out.println((ok ? "  [PASS] " : "  [FAIL] ") + what);
    }

    private JarVerify() {
    }
}
