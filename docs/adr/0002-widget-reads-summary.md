# ADR-0002: 新增只读的「成果概览」小组件

## Status

> **Deprecated（2026-09-23）**：本 ADR 描述的「成果概览」小组件已在 **v1.4.0 整体移除**。
>
> 移除范围包括 `widget/AchievementListWidget.kt`、`jicun_achievement_widget_info.xml`、`widget_achievement_list.xml`、`widget_preview_achievement.*`、`widget_achievement_bg.xml`、Manifest 里的 receiver 与两条 string；学年偏好降级为成果页自己的 UI 偏好 `data/AchievementYearStore.kt`（键名 `jicun_achievement_widget` / `selected_year` 保持不变，老用户升级不丢学年）。
>
> 决策依据：`docs/prd/prd-删除与组件精简-v1.4.0-2026-09-23.md`（P0-6 要求 100% 移除）；实现方案见 `docs/design/system_design.md`（T01）。
>
> 因此本 ADR 中「以后新增任何写路径都要记得调 `refreshWidget()`」这条隐性约束**已随组件一并消失**；「快速录入」组件（`JicunWidget`）仍受 ADR-0001 约束，不碰数据库。
>
> 以下为原始决策正文，保留备查，不再指导后续开发。

Accepted（历史状态，见上方 Deprecated 说明）

## Context

ADR-0001 定下"小组件只发 Intent、不碰数据库"，前提是小组件只承担拍照/相册入口——没有数据要展示，自然不需要读库。

后来出现了新需求：**在保留原有录入入口的同时**，再提供一个"成果概览"小组件——本学年攒了多少条、覆盖几育、最近一条是什么。这带来两个新要求：

1. 新组件必须能读到记录数据；
2. 数据变化后桌面上的数字要跟着变，且不能靠定时轮询（既费电，又和 App 内数据存在时间差）。

同时必须守住 ADR-0001 真正有价值的部分：**录入流程只有一套**，且原组件的行为完全不变。

## Decision

**新增**一个独立的 `JicunAchievementWidget`，与原有的 `JicunWidget` 并列存在。两个组件职责分开，用户在桌面组件选择器里会看到两个条目，可分别添加：

| 组件 | 职责 | 数据访问 |
|---|---|---|
| `JicunWidget` | 快速录入（拍照 / 相册两个入口） | 不碰数据库（继续遵守 ADR-0001） |
| `JicunAchievementWidget` | 本学年成果概览 | **只读** Room，绝不写入 |

新组件的具体约定：

- 数据在 `provideGlance`（挂起函数）里直接查库，按本学年口径过滤，与成果页共用 `AcademicYear.belongsTo` —— 学年归属只有一处实现。
- 刷新由 `AppViewModel.refreshWidget()` 在保存/删除记录后**主动推送**（`GlanceAppWidget.updateAll`）；`updatePeriodMillis` 保持 0，不做轮询。
- 查库失败时降级为空档案视图，不影响整块卡片点击进入 App。
- 点击整块卡片走 `MainActivity`（action = `WidgetActions.OPEN_ACHIEVEMENT`），路由到成果页。

**原组件（`JicunWidget`）的代码、布局、行为一律不变。**

## Consequences

### Positive

- 两个组件各自只做一件事：想快速录入用前者，想看一眼攒了多少用后者。
- 桌面上的数字与 App 内一致——推送刷新，没有轮询延迟。
- 学年口径只有一处实现，不会出现"桌面和成果页对不上"。
- 成果组件仍不承担写入与照片生命周期，边界比"小组件自己管数据"清晰。

### Negative

- 成果组件与 Room 产生耦合，`provideGlance` 的耗时会影响首次渲染速度。
- 新增了一条隐性约束：**以后新增任何写路径都要记得调 `refreshWidget()`**（例如批量导入），漏掉就会让桌面数字停在旧值，而且不会报错。
- 两个组件各有一套色值常量（RemoteViews 拿不到 App 的主题），改配色时要记得同步两处。

### Neutral

- ADR-0001 关于"录入入口统一由 MainActivity 承接"的决策继续有效，本 ADR 只新增了一个只读组件，没有修改原组件的约束。
- Manifest 里需要注册两个独立的 receiver，各自指向自己的 `*_widget_info.xml`。

## Alternatives Considered

**把展示和入口合并进同一个 3×2 组件**：拒绝。180×110dp 塞不下"大数字 + 覆盖五育 + 最近一条 + 两个按钮"，两边都会变得局促；而且合并会让用户没法只要其中一个。

**保持纯入口，不做展示**：拒绝。桌面上一个只能点两下的快捷方式，价值低于"一眼看到这学年攒了什么"——两者并列才都成立。

**小组件定时轮询刷新（`updatePeriodMillis > 0`）**：拒绝。Android 的最小轮询间隔是 30 分钟，且每次都唤醒进程；开销远大于一次推送，还会有最长半小时的数据延迟。

**单独为小组件维护一张缓存表**：拒绝。多一份数据就多一处不一致，而记录量级（几十到几百条）完全可以直接查。
