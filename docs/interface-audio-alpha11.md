# alpha11：显示接口解耦与声音修复

方案：用户于 2026-09-06 授权执行《吉他App接口解耦与声音修复联合方案_20260906_1604》。开工基线 main `79f7796`（alpha10 / 27），R7 PR #47 已合并，CI 34002839386 成功。此前只有 legacy UI PR #2 开放，没有本批实现。

## 已确认的无声缺陷

alpha10 的 `AndroidPitchPlayer` 使用 `MODE_STATIC`，却在第一次写 PCM 前要求 `STATE_INITIALIZED`。正常的静态音轨此时是 `STATE_NO_STATIC_DATA`，因此旧检查会在写入和播放前抛异常。普通训练页又没有显示这条错误，用户看到点击但没有声音。

修复：写前只拒绝 `STATE_UNINITIALIZED`，完整写入后再验证 `STATE_INITIALIZED`。Android 官方定义见 [STATE_NO_STATIC_DATA](https://developer.android.com/reference/android/media/AudioTrack#STATE_NO_STATIC_DATA)。新增实际 Android 音轨检查覆盖该状态转换；不能以源码推断代替特定手机的实际听感。

另修复固定等待 360ms 就算完成、取消和工作线程都释放同一音轨的生命周期问题。现在按所有预期帧的实际播放进度完成；轮询仅用于读取进度，有界超时只判失败，不能判成功。每请求发出 Started / Completed / Cancelled / Failed，带 requestId。取消立即暂停，音轨只由所属工作线程释放。新请求替换旧请求，不重叠播放。

使用媒体/音乐输出属性，媒体音量为零给就地提示，不修改系统音量或切换用户输出设备。开发日志仅含请求、MIDI、帧进度和路由类型，无学习档案或上传遥测。

## 开发边界

以下路径相对于 `app/src/main/java/com/a3322505a/guitarlearning/`。

| 修改内容 | 文件 | 约束 |
| --- | --- | --- |
| 页面布局 | `learning/LearningPages.kt`、`TrainingScreen.kt`、`PracticeContent.kt`、`RelationContent.kt`、`ChordContent.kt` 的页面控件 | 只接显示状态及事件；不传 LearnerState、ActiveTask 或 ViewModel |
| 指板外观、命中、横按和 O/X | `learning/TeachingFretboard.kt`、`TeachingGeometry.kt`、`ChordContent.kt` 的 ChordOverlay | FretboardUiState → PositionTapped；不判题、不播放、不查询和弦事实；绘制、覆盖及命中共用几何 |
| 公开显示契约 | `learning/TrainingUiState.kt`、`LearningPageUiState.kt` | 轻量不可变值；普通独立题不带隐藏答案集合；先扩展兼容字段再迁移调用方 |
| 课程到显示的转换 | `learning/TrainingUiAdapter.kt`、`LearningPageAdapter.kt` | 复用原判题、解锁、掌握和 ChordShapes；不另算一份课程事实 |
| 页面连接、导航、文件选择器、自动推进计时 | `learning/LearningApp.kt` | 收集 ViewModel 状态；转发事件；逻辑侧提供推进许可和延迟 |
| 训练交互、请求归属、首播策略 | `learning/TrainingViewModel.kt`、`TaskAudioPolicy.kt` | 验证当前任务、页面、请求和用途；普通试听不占 Room 保存锁 |
| 音频输出契约与设备实现 | `audio/PlaybackOutput.kt`、`AndroidPitchPlayer.kt` | MIDI、顺序/和弦、时序、带 ID 的事件；不解释课程或修改证据 |

`ContractPreviews.kt` 包含固定状态的指板、训练页和首页预览，无真实 ViewModel、数据库或播放器。视觉开发可以从这里开始。目录并未拆成多个 Gradle 模块，也没有添加账号或插件框架。

## 本批交互

- 普通单音：按任务明确坐标/音高首播一次；点标题重听；点错也播放实际点击音高。3 弦 4 品和 2 弦空弦均为 B3 / MIDI 59，6 弦 7 品为 B2 / MIDI 47。
- 看位置认音名：指板只试听，音名选项作答；保存期间的普通试听不生成答案或学习记录。
- TAB/五线谱：沿用实际音高及原合法位置，不用谱面高八度当发声音高。无明确音区的纯唱名/级数题不凭字母自选八度。
- 独立多音/结构题只自动播已有参照；无参照不播完整答案。引导示范可播公开完整素材。理论题主动试听完整素材前先保存辅助状态，失败也不回退成独立尝试。
- 听辨首播完整参照与目标；播放中不能重播或答题。只有当前页面、当前任务、当前请求完整成功，才提交原协调器的完成证据。失败、取消、过期回调不放行。
- 同一运行会话按 taskId 去重；重组、菜单、主题、反馈、顺序成员变化和前后台恢复不再次首播。进程重新启动可对恢复题首播。不增加持久化字段。
- 关闭声音可见且能就地开启；尚未首播的当前题开启后可播一次。失败在当前训练页显示并可手动重试，无自动重试循环。
- 离开训练、切题、后台、结束和释放取消音频；标题和普通指板试听采用最新请求优先。

## 验证及交付边界

定向单元测试覆盖 B3/跨八度音高、独立答案隐藏、反向试听、和弦 O/X/横按、短句及结构的保守首播、任务去重、过期请求、帧进度。使用假输出和可阻塞/失败的仓储验证 ViewModel 真实连接：保存中试听不写答案，取消/失败/旧完成不产生听辨证据，完整示范先登记辅助，保存失败不泄露完整音频。

沿用现有 Android CI 的单元测试、Debug/Release 构建和 API 35 覆盖升级保留档案检查。复用该模拟器增加本批音轨状态/播放帧/请求替换回归，不新增 CI job、覆盖率或全 App 截图门槛。本地无 Gradle 缓存且下载网络不可达，实际构建结果以本 PR 对应 CI 为准。

功能版本为 `2.0.0-alpha11` / `28`。包名、长期签名、Room schema 1、备份格式和既有学习记录未变。保留 legacy v1 的编译适配，新版只使用请求/事件播放链路。发布时记录 PR、CI、构建 SHA、合并 SHA、证书和 APK SHA-256。

真机验收仍需确认：进入普通题响一次，点标题可重听，正确/错误坐标按实际音高发声；快速点按、返回和后台无积压；听辨完整后能答题；颜色框、O/X、横按和触点在用户屏幕上保持可见。

本批完成后再调整其他规划的依赖与顺序，不把 R2–R7 已实现功能重新施工，也不顺带实施后续课程或全局外观改版。
