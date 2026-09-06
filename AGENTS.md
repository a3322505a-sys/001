# 001 当前源码入口

本仓库为项目 01 的 Android 吉他学习 App。用户当前指令和生效项目规范决定任务范围；先读取 [README.md](README.md) 的当前状态，并获取远端最新 main、核对相关 PR 后接手。

## 修改目标

- 当前唯一 App 入口是 `MainActivity` → `learning/LearningApp`。路径前缀为 `app/src/main/java/com/a3322505a/guitarlearning/`。
- 页面布局改 `learning/LearningPages.kt` / `TrainingScreen.kt` 及纯显示子组件；`LearningApp.kt` 仅负责状态连接、导航、生命周期与文件选择器。不要重新把 ViewModel 或 LearnerState 传进显示组件。
- 指板改 `learning/TeachingFretboard.kt`、`TeachingGeometry.kt` 和 `ChordContent.kt` 的 ChordOverlay；只接 `FretboardUiState` 并回传 `PositionTapped`，不得依赖 ActiveTask、AnswerEvaluator、MusicFacts 或播放器。答案显隐与和弦事实转换在 `TrainingUiAdapter.kt`。
- 显示契约在 `TrainingUiState.kt` / `LearningPageUiState.kt`；其他页面的课程与证据投影在 `LearningPageAdapter.kt`。固定状态预览在 `ContractPreviews.kt`，不需要真实数据或音频。完整分工见 [alpha11 接口边界](docs/interface-audio-alpha11.md)。
- 播放策略与请求有效性在 `TaskAudioPolicy.kt` / `TrainingViewModel.kt`；设备边界是 `audio/PlaybackOutput.kt`。保留任务首播去重和听辨完成门槛，不把普通试听写入学习证据。静态音轨必须写完数据后才要求 STATE_INITIALIZED；取消不得与工作线程重复 release。
- 课程/出题改 `learning/Curriculum.kt` 和 `LessonScheduler.kt`；学习记录改 `learning/LearningRepository.kt` 及 `TrainingViewModel.kt`。
- `training/`、`storage/`、根包旧 `GuitarLearningApp.kt` 与旧训练 Screen 文件是 legacy v1。新增当前 App 功能应接入 `learning/`。复用旧素材前阅读 [docs/legacy-v1.md](docs/legacy-v1.md)，把任务和证据接入新版，不能用修改旧模块冒充新版已实现。
- 和弦形态共用 `ChordShapes` 驱动显示/发音/判题；集合或顺序题不能把整体结果复制给未回答成员，逐目标证据在 `MemberEvidencePolicy`。
- 读谱用 `ReadingLessons` / `NotationView`，吉他谱面高于实际发声八度；纯音高题接受范围内的等价位置，TAB 指定弦品。中高把位按两点节点单独掌握，不能以区域进入状态替代证据。
- 关系与听辨使用 `MusicRelations` / `StructureLessons`，先修按节点目标配置；播放完成前不能记录听辨作答。无参照绝对音高不作门槛，试听理论题的完整答案按辅助处理。
- `core/`、`audio/`、`ui/theme/` 含共享能力，不按整目录删除。测试中的旧模块用例也不代表新版入口正在使用它。
- `mapping` 已接入当前任务与方向证据，直接前置为 `p03`；旧独立映射入口不是新版需求依据。级数必须有调性语境，旧 `GuitarCore.fixedDegrees` 仅表示 C 大调。

## 数据与交付

- v1 进度不迁移已经获得用户授权；v2 的 Room 档案必须保留。不得添加破坏性迁移、静默清空或新旧进度双写。
- 正式升级使用 [现有长期签名](docs/release-signing.md)，不得重建替代密钥；公开仓库不存私钥或密码。
- 走 feature 分支、PR 和仓库现有 CI。必要验证按改动风险执行，不新增统一覆盖率、截图或其他无关门槛。
- 文档/注释整理无需增加 App 版本或交付新 APK；包含功能变化的 APK 按项目规范递增版本并追踪源码。
- 更新能力或导航时同步 README；历史 alpha 记录保留原交付事实，避免将已完成记录再次当作待办。
