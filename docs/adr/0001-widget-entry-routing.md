# ADR-0001: 通过主 Activity 统一承接桌面小组件入口

## Status
Accepted（部分修订 —— "小组件不直接操作数据库"一条已由 ADR-0002 放松为"只读可以、写入仍不行"；"录入入口统一由 MainActivity 承接"继续有效）

## Context

暨存需要提供桌面小组件的“拍照”和“相册”入口，同时继续使用系统相机和现有录入页。照片导入、Uri 暂存和失败提示已经由 `MainActivity`、`CaptureScreen` 与 `AppViewModel` 协作完成。若小组件自行复制一套拍照和相册逻辑，会产生两套照片生命周期和错误处理，容易出现小组件入口与 App 内入口行为不一致。

## Decision

小组件只发送两个明确的 Intent action：拍照或选择相册。`MainActivity` 使用 `singleTop` 接收冷启动和已打开状态下的入口请求，再交给 `CaptureScreen` 启动系统相机或系统图片选择器。小组件不直接操作数据库、不保存照片、不复制录入流程。

液态玻璃导航使用 Compose 原生组件实现半透明背景、圆角、细描边、阴影和选中态，不为单一视觉效果引入大型动效框架；需要系统级模糊时再按 Android 版本能力增加增强实现，低版本保持可用的半透明降级。

## Consequences

### Positive

- 系统相机入口和相册入口复用现有稳定流程。
- 小组件只负责启动入口，业务边界清晰，维护成本低。
- Android 低版本仍有可用的玻璃风格降级效果。

### Negative

- 小组件点击后需要先唤起主 Activity，再启动系统选择器。
- 真正的背景实时模糊效果受 Android 版本和厂商渲染能力影响。

### Neutral

- 小组件需要通过 Manifest 注册，并依赖 Android Glance 的 AppWidget 适配层。

## Alternatives Considered

**在小组件中直接实现拍照和照片导入**：拒绝。会复制 Activity Result、Uri 暂存和失败处理，造成行为分叉。

**引入大型动效库实现底部导航**：拒绝。当前液态玻璃效果可以由 Compose 原生透明度、描边、阴影和状态动画完成，先保持依赖简单。
