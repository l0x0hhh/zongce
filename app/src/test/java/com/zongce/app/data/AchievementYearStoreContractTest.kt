// 学年偏好的**兼容性契约**测试。
//
// 为什么要用反射而不是直接调方法：本项目的 JVM 单测是纯 JUnit4、没有 Robolectric，
// SharedPreferences 在 JVM 上拿不到真 Context，current()/set() 跑不起来。
// 但"老用户升级后学年不能丢"这条约束完全由两个字符串常量决定 ——
// 常量改一个字，老用户升级后成果页就会跳回当前学年，而且**不会报错、不会崩溃**，
// 是最难被发现的那种回归。所以用反射把常量钉住：能测的部分就必须测。
package com.zongce.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AchievementYearStoreContractTest {

    @Test
    fun sharedPreferencesNameIsFrozenForUpgradingUsers() {
        assertEquals("jicun_achievement_widget", readPrivateConstant("PREFS"))
    }

    @Test
    fun preferenceKeyIsFrozenForUpgradingUsers() {
        assertEquals("selected_year", readPrivateConstant("KEY"))
    }

    @Test
    fun publicSurfaceStaysTwoMethods() {
        // 签名也是契约：current() 必须接收"当前存在的学年列表"才能做回落，
        // 少了这个参数就退化成"永远返回存过的值"，删空学年后成果页会停在空学年上。
        assertNotNull(
            AchievementYearStore::class.java.getDeclaredMethod(
                "current", android.content.Context::class.java, List::class.java
            )
        )
        assertNotNull(
            AchievementYearStore::class.java.getDeclaredMethod(
                "set", android.content.Context::class.java, String::class.java
            )
        )
    }

    /**
     * 读 [AchievementYearStore] 的 private const val。
     *
     * 加载这个类本身不触发任何 Android 调用（object 的初始化只是建一个空实例），
     * 所以纯 JVM 上可以安全反射；真正需要 Context 的只有 current()/set() 两个方法体。
     */
    private fun readPrivateConstant(name: String): Any? {
        val field = AchievementYearStore::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(null)
    }
}
