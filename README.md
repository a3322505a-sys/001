# 吉他 · 一小步（001）

Android 电吉他学习 App，使用 Kotlin / Compose。当前 v2 通过指板示范、独立练习、旧点复习与知识树组成可恢复的学习路径；学习档案保存在本机，无需注册。

## 当前交接基线

2026-09-12 从远端 main `ac8404f`（PR #62–#66 已合并，alpha28/45）接续训练减负方案 P1–P4。当前 feature 版本 **2.0.0-alpha29 / versionCode 46**，PR #67；合并、CI、签名 APK 和真机状态以 [本批施工记录](docs/training-alpha29.md) 为准。

普通课程最多12个完整任务，当前课程达标后结算；选择继续学习/练习、进入低把位或返回。任务计数从原持久化记录推导，不修改档案格式；专项和整段短谱保持原边界。声音状态在固定控件中更新，普通播放失败按需查看，小横屏作答与指板并排。

固定唱名与 C 大调级数先看短示范再穿插四方向；不等待全组熟练。映射直接前置改为音名概念 n00，位置课程依赖不变。补练按本次诊断后的已完成任务安排原条件复测，其他已具备条件的方向保持可学。TAB 单音复用已教低把位，短句为12条原创同弦/跨弦样例，含同难度陌生组合；8段短谱与记录保持，P5新增连续短谱依赖P4真人反馈。

已完成[全项目架构地图](docs/architecture-map.md)与[布局/架构分批重构计划](docs/architecture-refactor-plan.md)。P1 新增 `BoardTeachingPolicy`，让显示、知识暴露与点击资格共同读取业务事实，移除业务对 UI 投影的反向依赖。P2 工作分支提取纯显示页面框架与题型布局，课程与数据格式保持。当前仍是单一 `:app` 模块。验证和阶段状态见重构计划。

所属项目：01；仓库：`a3322505a-sys/001`。R2–R7 按用户连续授权分批推进，状态见 [执行记录](docs/roadmap-progress.md)。

- 历史 alpha28/45 普通页面视觉整理已合并于本轮基线；历史整合边界见 [整合记录](docs/p3-p6-alpha21.md)。
- alpha28 统一普通页面的文字层级、16dp 页边距、12dp 圆角卡片和按钮主次；首页与课程行在窄屏或大字号下将操作下移，首页说明完整换行。四套配色与学习状态保留，训练页与图形主题不变。范围和验证记录见 [普通页面视觉整理](docs/ordinary-page-polish-alpha28.md)。
- 指板首错可即时触发局部补教：自然音认音固定七项，补练集中目标并提供必要示范，找位缩小答题范围，撤辅助后穿插复测；读谱、和弦、关系、听辨与转换保留 P6 的逐成员证据和各自配置。历史交付分别见 [P6 记录](docs/p6-alpha19.md) 与 [P3 返工记录](docs/p3-downgrade-alpha20.md)。
- alpha04 从 `c1b06f6`（PR #40 后的 main）接续，实现 [R1 区域折叠与四套主题](docs/r1-regions-themes.md)。alpha03 的 PR #37–#39 与 legacy 边界整理已完成；接手时仍需获取远端最新 `main`。
- 已完成 Draft 0.4 的 A+B：统一判题、首个学习闭环、Room 学习档案、知识树、备份恢复与长期签名；alpha02/03 已继续修正指板和页面。
- 首页为「吉他入门／指板训练／进阶应用／知识树」四入口；吉他入门收纳认识吉他、基础认识和读谱入门。课程前置条件由节点决定，首页分类顺序不等于整类课程的通关顺序。
- 指板训练分为低把位 0–4 品、中把位 5–8 品、全指板 0–12 品三个入口；区域主状态按近期双向独立首答显示音位熟练度；历史课程通过在详情保留。
- 设置 → 外观提供清爽青白、暖纸森林、午夜蓝、石墨紫；主题保存后立即生效。已掌握、可学习、未解锁、需复习与规划中采用一致的颜色、符号和文字。
- 指板训练只显示低／中／全三个区域与开始／继续；每轮最多12题，以3题热身、7题主练、2题收尾为默认框架，快准上探与局部补练可替换题位，按近期证据诊断、局部补教、恢复试练，并引入满足先修的新点。点区域立即进入音位训练；返回保存并结束本轮，重开热身并保留降级恢复进度。后台中断与区域间切换保留各自暂停题。读谱、和弦／关系、听辨与转换也按实际首答证据在本题型内补教和恢复；各专项保留原范围与先修。
- 进阶应用提供四个和弦形态示例，局部和弦图以固定颜色和1–4数字标手指，支持横竖切换并保存偏好，音名视图附加音名与根音白环；图面跟随主题，读屏提供逐位置选择/试听动作。形态示范、发音及逐弦任务共用数据，O/X 和横按按实际发音处理。
- P2/P3 训练页按文字、指板（含谱面）、和弦分派布局：纯选项题集中显示；指板按顶栏测量后的空间分配，短窗口滚动；和弦图与操作并排，小窗口或大字时回流。普通谱面与短谱共用内容宽度滚动策略，原生文字和谱面几何随辅助字号成套缩放。普通题答对自动前进，答错纠正后手动下一题；普通课程和区域末题纠正后均结算，普通课程达标后也在当前题反馈结束时结算；创作题完成后保留回听和手动下一题。训练页横屏沉浸，退出恢复系统栏。

- alpha16 增加 K1–K5 的 25 个课程节点及实琴自评，复用 R7 的判题与逐项证据；详情见 [增量课程](docs/k1-k5-alpha16.md)。短谱仍限 8 段试用，K6 未开放。

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
| p04–p09 | 继续扩展低把位，共覆盖 18 个自然音位 | 从 p03 逐节点递进 | 已实现 |
| mapping | 固定唱名双向、C 大调级数双向 | n00 | 已实现，各方向分别留证据 |
| tab02 / staff / staff02 | TAB 短句、五线谱单音与短句 | tab01+p03 / tab01+p03 / staff | 已实现，逐项记录 |
| middle、m02–m08 | 中把位 5–8 品，16 个自然音位 | p09 起逐节点递进 | 已实现，8 个两点节点 |
| full、h02–h07 | 高把位 9–12 品，14 个自然音位；全指板专项 | m08 起逐节点递进 | 已实现，7 个两点节点 |
| chord-am / chord-g5 / chord-f | Am 开放、G5 两音/三音、F 横按形态 | p07 / p09 / chord-am+chord-g5 | 已实现，逐弦手机定位 |
| structure / pitch-relations | 半音全音、同音名/同音高/八度 | p03 / p09+structure | 已实现 |
| intervals | 一八度内音程及上下行方向 | structure+p03 | 已实现，按实际音高与明确拼写 |
| scale-major / scale-minor | C 大调、A 自然小调步距与上下行 | intervals+mapping+p09 / scale-major | 已实现，等价位置接受 |
| triads / power-structure | 大小减增三和弦、强力和弦构成 | intervals+mapping / chord-g5+intervals | 已实现 |
| cross-position | 低把位参照到 5–12 品同音高 | h07+pitch-relations | 已实现 |
| ear-intervals / ear-triads | 常用音程与四类三和弦听辨 | intervals / triads | 已实现，须完整播放参照后回答 |

默认推荐按 g00 → n00 → p01 → tab01 → p02 → … → p09 推进；`tab01` 与 `p02` 的直接前置都是 `p01`，TAB 不是 P02 的硬性门槛。已实现节点也需满足各自前置才能进入；规划节点可查看详情，不能开始训练。

`mapping` 完成音名概念 n00 后即可学习，每个新对应关系先示范，再逐步穿插唱名和 C 大调级数双向练习。C/D/E 是起始试用分组，不是新熟练门槛。各方向证据分别保存；示范和辅助成功不算独立掌握。旧版独立映射入口仍不从当前 App 导航可达。

## 修改当前 App 应从哪里开始

下表路径相对于 `app/src/main/java/com/a3322505a/guitarlearning/`。

完整职责、事件/保存链路、导航可达性和验证选择见 [架构地图](docs/architecture-map.md)。先按实际入口定位，再查对应历史记录。

| 需求 | 当前实现 |
| --- | --- |
| 启动、沉浸系统栏 | `MainActivity.kt`；Manifest 只声明这一个 Activity |
| 页面连接、导航、生命周期、文件选择器 | `learning/LearningApp.kt` |
| 首页、训练页、知识树、历史、设置布局 | `learning/LearningPages.kt`、`TrainingScreen.kt`；显示契约及适配见 [alpha11](docs/interface-audio-alpha11.md) |
| 区域展示分组及节点视觉状态 | `learning/LearningPresentation.kt`；不改课程依赖 |
| 课程开放及先修关系 | `learning/Curriculum.kt` |
| 出题、复习、预学习 | `learning/LessonScheduler.kt` |
| 专项范围、方向与入口 | `learning/PracticeLessons.kt`、`PracticeContent.kt`；共用协调器与 Room |
| 判题、纠正、推进、掌握度 | `learning/AnswerEvaluator.kt`、`learning/LearningCoordinator.kt`、`learning/MasteryPolicy.kt` |
| 和弦形态、绘制与逐项证据 | `learning/ChordShapes.kt`、`ChordLessons.kt`、`ChordContent.kt`、`MemberEvidencePolicy.kt` |
| 读谱、短句与实际音高 | `learning/ReadingLessons.kt`、`NotationView.kt`；TAB 坐标与五线谱音高判题分开 |
| 关系、结构、参照听辨 | `learning/MusicRelations.kt`、`StructureLessons.kt`、`RelationContent.kt`；与音位/形态证据分开 |
| 当前教学指板与点击几何 | 普通指板为 `learning/TeachingFretboard.kt`、`learning/TeachingGeometry.kt`；和弦图为 `learning/ChordDiagram.kt` |
| 页面状态、学习档案与备份恢复 | `learning/TrainingViewModel.kt`、`learning/LearningRepository.kt` |
| 标准调弦、实际音高、播放、主题 | `core/MusicFacts.kt`、`audio/`、`ui/theme/` |

**`training/`、`storage/` 与旧 `GuitarLearningApp.kt` 属于 legacy v1。** 它们仍在源码和现有测试中，保留供素材复用；新版入口没有调用旧会话、旧存储或旧页面。改动旧 `FirstFretboardModule` 不会给当前 App 增加课程。完整范围、复用限制和删除判断见 [docs/legacy-v1.md](docs/legacy-v1.md)。

新版只使用 `learning-v2.db`（Room schema 1）。用户已批准 v1 进度不迁移；该决定仅针对 v1→v2，不允许清空后续 v2 学习档案。暂停、重启恢复、备份和覆盖升级继续维护同一档案。R1 仅在原 JSON 快照增加可缺省的 `themeId`，不改 Room 表结构、数据库版本或学习证据。旧档案缺少该字段时默认清爽青白；未知主题 ID 可读取，显示回退到默认主题。

alpha11 将页面、指板、音频与训练逻辑改为显示状态和事件契约；普通题首播、标题重听与实际坐标试听已接入。修复静态音轨在写入前误判初始化失败的问题，按实际播放帧处理完成、失败与取消。开发边界和本批验证见 [接口与声音交接](docs/interface-audio-alpha11.md)；手机听感需单独确认。

alpha12 将当前课程释义改为“具体音名与位置 → 距离或对应关系 → 本题结论”，覆盖音位、读谱、唱名与级数、和弦、音程与音阶。低把位示例：第4弦 D（空弦）→ E（2品）→ F（3品），先全音、再半音；跨弦和八度用实际音高比较。释义仍通过原示范、提示和纠错时机显示；已有任务快照保留，新题使用新版文案。播放提示引起指板伸缩、音量偏小和吉他音色是用户另行记录的待办，本批只修改教学释义。

## 资料索引与状态

| 资料 | 用途及状态 |
| --- | --- |
| 本 README | 当前能力、接手位置和资料入口；随功能变化维护 |
| [AGENTS.md](AGENTS.md) | 当前源码修改入口与数据、签名边界 |
| [architecture-map.md](docs/architecture-map.md) | 当前架构、模块职责、修改入口、实际导航和存储/音频链路 |
| [architecture-refactor-plan.md](docs/architecture-refactor-plan.md) | 已核实问题、审查校准及尚未实施的 P1–P5 重构阶段 |
| [legacy-v1.md](docs/legacy-v1.md) | 新旧路径判定、映射差异、复用与后续删除条件 |
| [rebuild-draft04.md](docs/rebuild-draft04.md) | 已实现 A+B 的历史交付记录；不是重做任务清单 |
| [fretboard-alpha02.md](docs/fretboard-alpha02.md) | 已合并的指板修正记录，效果由真机反馈继续迭代 |
| [interface-alpha03.md](docs/interface-alpha03.md) | 已合并的首页和训练页修正记录 |
| [roadmap-progress.md](docs/roadmap-progress.md) | R2–R7 分批范围、实现边界和交接 |
| [r1-regions-themes.md](docs/r1-regions-themes.md) | alpha04 的 R1 范围、主题兼容与验证说明 |
| [interface-audio-alpha11.md](docs/interface-audio-alpha11.md) | 页面/指板/音频边界、无声缺陷与本批验证 |
| [release-signing.md](docs/release-signing.md) | 持续生效的长期签名说明；公开证书用于核验 |

项目中的《App重构初版设计与复用审计_20260905_2323.md》是 Draft 0.4 的设计与 **v1 基线审计**：A+B 已实现，C–E 的后续范围已由 R2–R7 分批实施，当前能力以本 README 为准；旧进度迁移由用户后续指令取消，首页安排由 alpha03 更新。其中的旧源码发现和“当前未实现”描述需按其基线理解。更早的《第一指板与映射训练整合修正方案_20260904_0522.md》保留为需求历史，旧 P0–P17 不再作为新版施工顺序。

核查时仍开放的 [PR #2](https://github.com/a3322505a-sys/001/pull/2) 针对旧 UI，不属于未完成的 v2 重构。文档整理未关闭或合并它。

## 构建与验证

既有 [Android CI](.github/workflows/android.yml) 执行单元测试、Debug/Release 构建，以及 API 35 上递增版本覆盖安装保留学习档案检查。R1 延用这些检查，并在原档案测试中覆盖主题重启、旧备份兼容及事务失败保护。具体结果以本次 PR 对应 SHA 的 [Android CI](https://github.com/a3322505a-sys/001/actions/workflows/android.yml) 为准。用户已确认 P01–P03 的真机学习验证；R1 的新布局、主题和大字号效果仍需交付后真机确认。

本地基础命令（JDK 17、Android SDK 按工程配置）：

```sh
./gradlew test assembleDebug assembleRelease
```

CI 的 Release 产物是未签名包；正式交付按 [长期签名说明](docs/release-signing.md) 使用既有密钥，保持包名和递增的版本编号。CI 临时 Debug 证书不能代替正式升级签名。alpha11 属功能版本，正式包使用原长期签名，versionCode 为 28；不交付 CI 为升级检查临时生成的更高版本 Debug 包。

R7 的可用范围明确为 C 大调、A 自然小调，C/A 根音下四类三和弦；音程识别覆盖 0–12 半音，听辨覆盖同度、大二度、小三度、大三度、纯四度、纯五度和八度。后续调性、完整节奏与实琴识别不在本批完成声明中。R7 历史交付过程中执行环境曾离线，现已从远端仓库与原长期签名备份恢复。正式 APK 继续核验包名、版本和原证书；交付结果见 PR。

当前增量：指板连续展开、稳定板区、训练音频去噪和离线吉他采样，见 [统一施工记录](docs/unified-training-progress.md)。真实听感与小屏显示待用户验收。

alpha15：吉他入门内增加八段原创短谱试用，五线谱/TAB/播放共用时值事件。基线、练习、陌生复测依次开放；慢读首答与实琴自评分开，40–80 BPM、休止、暂停恢复与练习段对照/循环。真人完成3–5次试用后再扩展读谱。

alpha17：训练页布局与指板入口修复，见 [本批说明](docs/training-layout-alpha17.md)。代码、CI、模拟器截图与真机验收分别记录。

alpha18：界面减负、双向近期熟练度、诊断与动态回退、能力树聚合、音名/唱名/级数混合，以及显示后 400ms 首播。复用同一 Room 快照；旧条件不明记录保留且不计作新独立样本。本批变更在 [PR #55](https://github.com/a3322505a-sys/001/pull/55) 中审阅；对应提交的 CI、APK 与合并状态在 PR 中分别记录。真人指板外观、听感和学习效果仍需试用；该轮未执行 P6。详见 [分 P 施工记录](docs/p-plan-alpha18.md)。

alpha19：按本轮明确指令执行 P6，逐成员统一证据、题型内补教与恢复，接入范围见 [P6 记录](docs/p6-alpha19.md)。
