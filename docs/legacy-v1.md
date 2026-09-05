# legacy v1 边界与复用说明

所属项目：01；仓库：`a3322505a-sys/001`。核查时间：2026-09-06 03:36（UTC+8）。功能基线：`027d369ecaf1861586df18df4bda02dd7b0b8766`，2.0.0-alpha03（20）。本文是现状核验和维护边界，更新入口见 [README](../README.md)。

## 两套源码，当前只启用一套学习系统

Manifest 只有 `MainActivity`。它创建 `TrainingViewModel` 并显示 `learning/LearningApp`；新版页面统一通过 `LearningCoordinator`、`LessonScheduler` 和 `RoomLearningRepository` 处理课程与记录。`learning/` 没有调用旧 `TrainingSession`、`PersistentTrainingStore` 或旧页面；当前入口不会把同次作答写入两种进度系统。

旧实现仍位于 `app/src/main/java`，参与现有编译和旧测试。**legacy 标记是维护边界，尚未做物理目录搬迁、移出编译或删除。** 当前要处理的是新旧命名接近造成的误改风险；不能仅凭两套文件存在就认定正在同时运行或数据冲突。

下表路径相对于 `app/src/main/java/com/a3322505a/guitarlearning/`。

| legacy 范围 | 旧职责 | 当前替代位置／复用方式 |
| --- | --- | --- |
| `GuitarLearningApp.kt` | 旧首页、`AppDestination` 和独立专项导航 | 当前导航在 `learning/LearningApp.kt` |
| `NoteNameTrainingScreen.kt`、`CombinedMappingTrainingScreen.kt`、`SolfeggioNoteMappingScreen.kt`、`ReadingTrainingScreen.kt`、`IntervalTrainingScreen.kt` | 旧训练页面 | 可参考题面素材；不要重新接上旧入口 |
| `training/FirstFretboardModule.kt`、`TrainingEngine.kt`、`TrainingSession.kt`、`TrainingStateMachine.kt`、其余 `training/` 文件 | 旧课程、题型、调度、等级与临时会话 | 音乐素材按需适配新版任务、判题、证据和课程；新课程写进 `learning/` |
| `storage/` | SharedPreferences 下的 `v01.storage` 及旧模型 | 当前存储在 `learning/LearningRepository.kt`，Room `learning-v2.db` |
| `ui/fretboard/Fretboard.kt`、旧 `ui/choices/`、`ui/components/`、`ui/feedback/` | 旧指板、旧页面组件 | 当前指板为 `learning/TeachingFretboard.kt`；几何为 `TeachingGeometry.kt` |

`core/MusicFacts.kt`、声音播放器/音高类型及 `ui/theme/` 仍用于新版。`core/GuitarCore.kt` 中的 `FretPosition` 也被声音兼容接口引用，其他纯音乐素材有复用价值。不能把 `core/`、`audio/`、`ui/` 整目录视为废弃。

本轮保留旧代码与已有测试，增加入口说明和关键文件的 legacy 注释。后续迁入某项能力时，只复用适用素材；等相应素材和必要兼容接口已处理，再按依赖删除旧页面、旧状态机和其专属测试。课程规则与用户数据用途仍由当轮目标决定。

## 映射的两种规则

| 项目 | v1 legacy | 当前 v2 |
| --- | --- | --- |
| 入口 | 旧首页直接打开 `CombinedMappingTrainingScreen` | 吉他入门分类中可查看 `mapping` 节点详情 |
| 开始条件 | 独立页面，不经过新版课程图 | `Curriculum.available` 要求已实现且前置全部掌握；`mapping` 目前 `implemented=false`，所以通过 P03 仍不可开始 |
| 前置关系 | 没有新版 P03 门槛 | 当前规划前置为 `p03` |
| 记录 | `remember` 内的 `CombinedMappingStateMachine` 和临时计数 | 映射任务尚未接入；后续需使用统一档案与方向证据 |
| 符号语境 | `GuitarCore.fixedMappings/fixedDegrees` 固定 C–B 对应 1–7 | 固定唱名与调性中的级数需分别定义，级数题注明主音／调性 |

**当前没有两个映射入口同时生效的冲突。** P03 前置是当前课程编排，不是乐理定律，也不表示所有符号解释都必须等到 P03。现在 `n00` 只教 E/F；未来若提前引入音名与固定唱名的基础对应，应在新版课程中明确它与综合映射、带调性级数的区别。

旧表中的 C→1 仅表示 C 大调语境，不能推广到任意调；`MusicFacts.majorDegree(midi, tonicPitchClass)` 已提供带主音的大调级数计算。复用旧选项、序列、缺失项和和弦集合时，必须同时确认调性和实际考察方向，C→Do 答对不等于 C→级数也已作答。

本轮明确资料与代码注释，保留现有 P03 前置和“规划中”状态。接入新版映射需要任务生成、判题、学习证据和恢复路径完整支持，不能只把 `implemented` 改为 `true`；当前调度器没有独立的映射任务分支。

## 旧进度与历史资料

- 用户批准的是 **v1→v2 不导入旧进度**，不是以后每次升级都清空。v2 的学习档案、schema 和长期签名继续保留。
- 旧代码留存是素材与回归参考，不意味着旧等级、旧算法参数或旧独立入口继续约束新版。
- 旧重构前审计、旧 P0–P17 方案按其时间和基线保存。已完成 A+B 和 alpha02/03 的交付记录见 README 资料索引；后续课程规划不因文档整理自动变成已实现。

核验依据：[Manifest](../app/src/main/AndroidManifest.xml)、[MainActivity](../app/src/main/java/com/a3322505a/guitarlearning/MainActivity.kt)、[新版导航](../app/src/main/java/com/a3322505a/guitarlearning/learning/LearningApp.kt)、[课程条件](../app/src/main/java/com/a3322505a/guitarlearning/learning/Curriculum.kt)、[旧导航](../app/src/main/java/com/a3322505a/guitarlearning/GuitarLearningApp.kt)、[旧映射页面](../app/src/main/java/com/a3322505a/guitarlearning/CombinedMappingTrainingScreen.kt)。
