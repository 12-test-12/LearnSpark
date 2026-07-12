package com.learnspark.gamification.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 每日鼓励语服务。
 *
 * <p>用精选激励语按天轮换（dayOfYear % 列表长度），无需调用 AI。
 * 保证用户即使未配置 DeepSeek Key 也能在首页看到鼓励语。
 */
@Service
public class DailyQuoteService {

    /** 精选鼓励语列表（按天轮换，每天一句） */
    private static final String[] QUOTES = {
            "日拱一卒无有尽，功不唐捐终入海。",
            "学习的本质不是记住答案，而是学会提问。",
            "今天比昨天多懂一点，就是进步。",
            "知识就像复利，每天积累一点点，时间会给你惊喜。",
            "不要追求完美，要追求持续。完成比完美更重要。",
            "每一个你掌握的概念，都是未来解决问题的工具。",
            "学习不是百米冲刺，而是马拉松。节奏比速度重要。",
            "当你觉得难的时候，说明你在突破舒适区。坚持下去。",
            "把大目标拆成小任务，每完成一个就离成功更近一步。",
            "理解优先于记忆，思考优先于练习，复盘优先于重复。",
            "今天学的东西，也许明天就用到。保持输入，静待输出。",
            "不要和别人比进度，和昨天的自己比就好。"
    };

    /**
     * 获取今日鼓励语。
     * 按 dayOfYear 取模轮换，同一天返回同一句。
     */
    public String getTodayQuote() {
        int dayOfYear = LocalDate.now().getDayOfYear();
        return QUOTES[dayOfYear % QUOTES.length];
    }
}
