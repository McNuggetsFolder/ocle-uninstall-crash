package cn.jikuai.ocleuninstall;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.eventbus.api.IEventBus;

/**
 * 自身产物清理。
 * <p>
 * 本模组不注册方块、物品、方块实体、配方，唯一的「产物」就是挂在 mod 事件总线上的监听器
 * 和它在 Forge 里的 ModContainer。崩溃前把它从事件总线上摘掉并记录条数，做到「先自我卸载，再崩」。
 */
public final class SelfPurge {

    private static final List<IEventBus> BUSES = new ArrayList<>();
    private static final List<Object> LISTENERS = new ArrayList<>();

    private SelfPurge() {
    }

    /** 登记一个待注销的监听器。 */
    public static synchronized void track(IEventBus bus, Object listener) {
        if (bus != null && listener != null) {
            BUSES.add(bus);
            LISTENERS.add(listener);
        }
    }

    /** 注销全部已登记监听器，返回成功注销的数量。 */
    public static synchronized int purge() {
        int purged = 0;
        for (int i = 0; i < BUSES.size(); i++) {
            try {
                // IEventBus.unregister(Object) 返回 void，没有 isRegistered 可查；
                // 这里按「登记过的都尝试注销，未抛异常即计一次」统计。
                BUSES.get(i).unregister(LISTENERS.get(i));
                purged++;
            } catch (RuntimeException ignored) {
                // 注销失败不影响后续崩溃，吞掉即可
            }
        }
        BUSES.clear();
        LISTENERS.clear();
        return purged;
    }
}
