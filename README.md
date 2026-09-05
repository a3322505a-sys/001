# 吉他 · 一小步（001）

Android 电吉他学习 App，使用 Kotlin / Compose。当前 v2 通过指板示范、独立练习、旧点复习与知识树组成可恢复的学习路径；学习档案保存在本机，无需注册。

## 当前交接基线

所属项目：01；仓库：`a3322505a-sys/001`。本次功能范围为未来规划方案的 R1。

- 当前应用版本：**2.0.0-alpha04（versionCode 21）**，定义见 [app/build.gradle.kts](app/build.gradle.kts)。
- alpha04 从 `c1b06f6`（PR #40 后的 main）接续，实现 [R1 区域折叠与四套主题](docs/r1-regions-themes.md)。alpha03 的 PR #37–#39 与 legacy 边界整理已完成；接手时仍需获取远端最新 `main`。
- 已完成 Draft 0.4 的 A+B：统一判题、首个学习闭环、Room 学习档案、知识树、备份恢复与长期签名；alpha02/03 已继续修正指板和页面。
- 首页为「吉他入门／指板训练／进阶应用／知识树」四入口；吉他入门收纳认识吉他、基础认识和读谱入门。课程前置条件由节点决定，首页分类顺序不等于整类课程的通关顺序。
- 指板训练按低把位 0–4 品、中把位 5–8 品、全指板 0–12 品折叠；默认全收起、一次展开一个，返回保留位置。低把位进度按 P01–P09 共九个节点计数。
- 设置 → 外观提供清爽青白、暖纸森林、午夜蓝、石墨紫；主题保存后立即生效。已掌握、可学习、未解锁、需复习与规划中采用一致的颜色、符号和文字。
- 训练以题目和指板为主，必要说明及错误反馈集中在上方；答对自动前进，答错纠正后手动下一题。训练页横屏沉浸，退出恢复系统栏。

## 已开放与规划中的课程

课程事实见 [learning/Curriculum.kt](app/src/main/java/com/a3322505a/guitarlearning/learning/Curriculum.kt)。

| 节点 | 内容 | 直接前置 | 当前状态 |
| --- | --- | --- | --- |
| g00 | 认识弦、品格、定位圆点 | 无 | 已实现 |
| n00 | 认识音名 E / F | g00 | 已实现 |
| p01 | 1弦 E / F | n00 | 已实现 |
| tab01 | 用已知位置认识 TAB | p01 | 已实现 |
| p02 | 加入 G / B | p01 | 已实现 |
| p03 | 2弦 C / D | p02 | 已实现 |
| p04–p09 | 继续扩展低把位 | 从 p03 逐节点递进 | 规划中 |
| mapping | 唱名与级数 | p03 | 规划中，`implemented=false` |
| staff | 五线谱入门 | tab01 | 规划中 |
| middle / full | 中把位／全指板 | p09 / middle | 规划中 |
| structure | 音程、音阶与和弦 | p03 | 规划中 |

默认推荐按 g00 → n00 → p01 → tab01 → p02 → p03 推进；`tab01` 与 `p02` 的直接前置都是 `p01`，TAB 不是 P02 的硬性门槛。已实现节点也需满足各自前置才能进入；规划节点可查看详情，不能开始训练。

`mapping` 尚未接入新版，完成 P03 也不会开放它。`p03` 是当前规划的排课条件，不是理解音名、唱名在乐理上的必需条件；`n00` 目前只介绍 E/F。旧版独立映射入口不再从当前 App 导航可达。后续映射复用及级数语境见 [legacy 边界](docs/legacy-v1.md#映射的两种规则)。

## 修改当前 App 应从哪里开始

下表路径相对于 `app/src/main/java/com/a3322505a/guitarlearning/`。

| 需求 | 当前实现 |
| --- | --- |
| 启动、沉浸系统栏 | `MainActivity.kt`；Manifest 只声明这一个 Activity |
| 首页、训练页、知识树、历史、设置 | `learning/LearningApp.kt` |
| 区域展示分组及节点视觉状态 | `learning/LearningPresentation.kt`；不改课程依赖 |
| 课程开放及先修关系 | `learning/Curriculum.kt` |
| 出题、复习、预学习 | `learning/LessonScheduler.kt` |
| 判题、纠正、推进、掌握度 | `learning/AnswerEvaluator.kt`、`learning/LearningCoordinator.kt`、`learning/MasteryPolicy.kt` |
| 当前教学指板与点击几何 | `learning/TeachingFretboard.kt`、`learning/TeachingGeometry.kt` |
| 页面状态、学习档案与备份恢复 | `learning/TrainingViewModel.kt`、`learning/LearningRepository.kt` |
| 标准调弦、实际音高、播放、主题 | `core/MusicFacts.kt`、`audio/`、`ui/theme/` |

**`training/`、`storage/` 与旧 `GuitarLearningApp.kt` 属于 legacy v1。** 它们仍在源码和现有测试中，保留供素材复用；新版入口没有调用旧会话、旧存储或旧页面。改动旧 `FirstFretboardModule` 不会给当前 App 增加课程。完整范围、复用限制和删除判断见 [docs/legacy-v1.md](docs/legacy-v1.md)。

新版只使用 `learning-v2.db`（Room schema 1）。用户已批准 v1 进度不迁移；该决定仅针对 v1→v2，不允许清空后续 v2 学习档案。暂停、重启恢复、备份和覆盖升级继续维护同一档案。R1 仅在原 JSON 快照增加可缺省的 `themeId`，不改 Room 表结构、数据库版本或学习证据。旧档案缺少该字段时默认清爽青白；未知主题 ID 可读取，显示回退到默认主题。

## 资料索引与状态

| 资料 | 用途及状态 |
| --- | --- |
| 本 README | 当前能力、接手位置和资料入口；随功能变化维护 |
| [AGENTS.md](AGENTS.md) | 当前源码修改入口与数据、签名边界 |
| [legacy-v1.md](docs/legacy-v1.md) | 新旧路径判定、映射差异、复用与后续删除条件 |
| [rebuild-draft04.md](docs/rebuild-draft04.md) | 已实现 A+B 的历史交付记录；不是重做任务清单 |
| [fretboard-alpha02.md](docs/fretboard-alpha02.md) | 已合并的指板修正记录，效果由真机反馈继续迭代 |
| [interface-alpha03.md](docs/interface-alpha03.md) | 已合并的首页和训练页修正记录 |
| [r1-regions-themes.md](docs/r1-regions-themes.md) | alpha04 的 R1 范围、主题兼容与验证说明 |
| [release-signing.md](docs/release-signing.md) | 持续生效的长期签名说明；公开证书用于核验 |

项目中的《App重构初版设计与复用审计_20260905_2323.md》是 Draft 0.4 的设计与 **v1 基线审计**：A+B 已实现，C–E 仍属后续规划；旧进度迁移由用户后续指令取消，首页安排由 alpha03 更新。其中的旧源码发现和“当前未实现”描述需按其基线理解。更早的《第一指板与映射训练整合修正方案_20260904_0522.md》保留为需求历史，旧 P0–P17 不再作为新版施工顺序。

核查时仍开放的 [PR #2](https://github.com/a3322505a-sys/001/pull/2) 针对旧 UI，不属于未完成的 v2 重构。文档整理未关闭或合并它。

## 构建与验证

既有 [Android CI](.github/workflows/android.yml) 执行单元测试、Debug/Release 构建，以及 API 35 上递增版本覆盖安装保留学习档案检查。R1 延用这些检查，并在原档案测试中覆盖主题重启、旧备份兼容及事务失败保护。具体结果以本次 PR 对应 SHA 的 [Android CI](https://github.com/a3322505a-sys/001/actions/workflows/android.yml) 为准。用户已确认 P01–P03 的真机学习验证；R1 的新布局、主题和大字号效果仍需交付后真机确认。

本地基础命令（JDK 17、Android SDK 按工程配置）：

```sh
./gradlew test assembleDebug assembleRelease
```

CI 的 Release 产物是未签名包；正式交付按 [长期签名说明](docs/release-signing.md) 使用既有密钥，保持包名和递增的版本编号。CI 临时 Debug 证书不能代替正式升级签名。alpha04 属功能版本，正式包使用原长期签名，versionCode 为 21；不交付 CI 为升级检查临时生成的更高版本 Debug 包。
