package com.learnspark.shared.components

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.learnspark.data.db.KnowledgeFolderRepository
import com.learnspark.data.db.ProjectRepository
import com.learnspark.features.gamification.GamificationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Dashboard 数据 ViewModel。
 *
 * 把项目/知识库/积分/连续打卡的实时数据聚合成一个 [DashboardState]，
 * 供 UI 一次性订阅，避免在 Composable 里散落多个 collectAsState。
 */
class DashboardViewModel(
    private val projectRepository: ProjectRepository,
    private val knowledgeRepository: KnowledgeFolderRepository,
    private val gamification: GamificationService = GamificationService(),
) : ScreenModel {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        // 实时观察本地项目 / 知识库变化
        screenModelScope.launch {
            projectRepository.observeActiveProjects().collect { projects ->
                _state.update { it.copy(
                    pendingTaskCount = projects.count { p -> p.status == "active" },
                    totalTaskCount = projects.size,
                    todayTasks = projects.take(5).map { p ->
                        TodayTask(
                            title = p.name,
                            project = p.goal,
                            statusLabel = when (p.status) {
                                "active" -> "进行"
                                "completed" -> "完成"
                                "paused" -> "暂停"
                                else -> p.status
                            },
                        )
                    },
                ) }
            }
        }
        screenModelScope.launch {
            knowledgeRepository.observeAll().collect { folders ->
                _state.update { it.copy(knowledgeEntryCount = folders.size) }
            }
        }
        // 初始一次快照（保证未 collect 完前也有数据）
        load()
    }

    fun load() {
        screenModelScope.launch {
            val projects = projectRepository.getActiveProjects()
            val folders = knowledgeRepository.getAll()
            val streak = gamification.currentStreak()
            val account = gamification.account()
            _state.update {
                it.copy(
                    pendingTaskCount = projects.count { p -> p.status == "active" },
                    totalTaskCount = projects.size,
                    todayTasks = projects.take(5).map { p ->
                        TodayTask(
                            title = p.name,
                            project = p.goal,
                            statusLabel = when (p.status) {
                                "active" -> "进行"
                                "completed" -> "完成"
                                "paused" -> "暂停"
                                else -> p.status
                            },
                        )
                    },
                    knowledgeEntryCount = folders.size,
                    streakDays = streak.currentDays,
                    totalPoints = account.total,
                    dailyQuote = pickQuote(),
                )
            }
        }
    }

    fun refreshQuote() {
        _state.update { it.copy(dailyQuote = pickQuote()) }
    }

    private fun pickQuote(): DailyQuote {
        val quotes = listOf(
            DailyQuote("坚持是最美的姿态。", "每日灵感"),
            DailyQuote("把大目标拆成 7 天能完成的小块。", "学习法"),
            DailyQuote("写下来，比记在脑子里更可靠。", "知识沉淀"),
            DailyQuote("先完成，再完美。", "拖延症克星"),
            DailyQuote("今天不打卡，明天就更不想。", "连续打卡"),
        )
        // 基于当天日期选一条（保证每天稳定不刷新跳动）
        val dayOfYear = java.time.LocalDate.now().dayOfYear
        return quotes[dayOfYear % quotes.size]
    }
}

/** Dashboard 单一来源的 UI 状态。 */
@Immutable
data class DashboardState(
    val dailyQuote: DailyQuote = DailyQuote("坚持是最美的姿态。", "每日灵感"),
    val pendingTaskCount: Int = 0,
    val totalTaskCount: Int = 0,
    val streakDays: Int = 0,
    val totalPoints: Int = 0,
    val knowledgeEntryCount: Int = 0,
    val todayTasks: List<TodayTask> = emptyList(),
)

@Immutable
data class DailyQuote(
    val text: String,
    val source: String,
)

@Immutable
data class TodayTask(
    val title: String,
    val project: String?,
    val statusLabel: String,
)
