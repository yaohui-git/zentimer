package com.yaohui.zentimer

import android.app.Application

/**
 * 两个「只在进程启动时做一次」的动作集中在这里：
 *
 *  - [SeasonTheme.init]：四时纸色取一次快照，之后整个进程不再读当前日期。这样「静坐中
 *    绝不换色」「切后台不换」「深浅模式切换只管亮暗」不需要额外判断就天然成立；
 *  - [DndGuard.reconcileOnStartup]：无条件把本应用那条勿扰规则停用一次。进程被强杀、
 *    手机重启后用户重新打开 App、升级后首次启动，都从这里收干净；停用是幂等的，
 *    不会替用户动系统原本的勿扰状态。
 *
 * Lang.init 先走一步，规则名要按当前语言写进系统「模式」列表。
 * 这里刻意不调 TimerEngine.init：计时状态的恢复仍归界面 / 服务各自的入口管，
 * 免得冷启动时多出一个写状态机的人。
 */
class ZenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Lang.init(this)
        SeasonTheme.init(this)
        DndGuard.reconcileOnStartup(this)
    }
}
