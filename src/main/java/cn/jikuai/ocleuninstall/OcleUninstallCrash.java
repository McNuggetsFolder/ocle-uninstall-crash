package cn.jikuai.ocleuninstall;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 「加载绝对彼方就卸载崩溃」。
 * <p>
 * 生命周期只有一句话：<b>构造即决定命运</b>。
 * 模组在构造阶段（Forge 初始化最早期，早于方块/物品注册、早于 TaCZ 读取枪包）就扫描游戏目录；
 * 一旦扫到绝对彼方（TaCZ namespace=ocle）的产物，立刻删除、注销自身监听、写崩溃报告并抛异常，
 * 由 Forge 的模组加载异常通道把游戏打死在启动阶段——绝不会进入主菜单，更不会读档。
 * <p>
 * 扫不到就只登记一个「加载完成」监听器，什么都不做，静默放行。
 */
@Mod(OcleUninstallCrash.MOD_ID)
public final class OcleUninstallCrash {

    /** 模组 ID。 */
    public static final String MOD_ID = "ocle_uninstall_crash";

    /** 独立 logger 名，方便在 latest.log 里 grep。 */
    public static final Logger LOGGER = LogManager.getLogger("OCLE-Guard");

    // 1.20.1 的 Forge 47.x 只有这一个入口能拿到 mod 事件总线（47.4 起标记了 removal，属正常告警）
    @SuppressWarnings("removal")
    public OcleUninstallCrash() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        // register(this) 会扫描本类里带 @SubscribeEvent 的方法
        modBus.register(this);
        SelfPurge.track(modBus, this);
        ConflictReactor.react();
    }

    /**
     * 只有「没检测到绝对彼方」时这个方法才会被执行到。
     * 它同时是 {@link SelfPurge} 的注销目标：冲突走的是另一条路，在这里被摘掉。
     */
    @SubscribeEvent
    public void onLoadComplete(FMLLoadCompleteEvent event) {
        LOGGER.info("[OCLE-Guard] 启动自检完成：未发现绝对彼方枪包，放行。");
    }
}
