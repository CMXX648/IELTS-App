package com.voxcoach.core.domain.part1

/**
 * Seeded IELTS Speaking Part 1 question bank.
 *
 * Questions are original paraphrases inspired by common Part 1 themes
 * (docs/02 §4 T1 personal topics), not Cambridge verbatim items.
 */
data class Part1Question(
    val id: String,
    val themeId: String,
    val themeTitle: String,
    val themeTitleZh: String,
    val text: String,
)

object Part1QuestionBank {
    const val DEFAULT_QUESTION_COUNT = 5

    val all: List<Part1Question> = listOf(
        // Theme: Hometown (T1)
        Part1Question(
            id = "P1-hometown-01",
            themeId = "hometown",
            themeTitle = "Hometown",
            themeTitleZh = "家乡",
            text = "Let's talk about your hometown. Where do you live now, and how long have you lived there?",
        ),
        Part1Question(
            id = "P1-hometown-02",
            themeId = "hometown",
            themeTitle = "Hometown",
            themeTitleZh = "家乡",
            text = "What do you like most about the area where you grew up?",
        ),
        Part1Question(
            id = "P1-hometown-03",
            themeId = "hometown",
            themeTitle = "Hometown",
            themeTitleZh = "家乡",
            text = "Has your hometown changed much in recent years?",
        ),
        Part1Question(
            id = "P1-hometown-04",
            themeId = "hometown",
            themeTitle = "Hometown",
            themeTitleZh = "家乡",
            text = "Would you recommend your hometown to a visitor? Why or why not?",
        ),
        // Theme: Daily routine (T1)
        Part1Question(
            id = "P1-routine-01",
            themeId = "daily_routine",
            themeTitle = "Daily routine",
            themeTitleZh = "日常生活",
            text = "Now, about your daily life. What does a typical weekday look like for you?",
        ),
        Part1Question(
            id = "P1-routine-02",
            themeId = "daily_routine",
            themeTitle = "Daily routine",
            themeTitleZh = "日常生活",
            text = "Do you prefer mornings or evenings for studying or working? Why?",
        ),
        Part1Question(
            id = "P1-routine-03",
            themeId = "daily_routine",
            themeTitle = "Daily routine",
            themeTitleZh = "日常生活",
            text = "How do you usually spend your weekends?",
        ),
        Part1Question(
            id = "P1-routine-04",
            themeId = "daily_routine",
            themeTitle = "Daily routine",
            themeTitleZh = "日常生活",
            text = "Is there anything you would like to change about your daily routine?",
        ),
        // Theme: Friends (T1) — optional third theme for variety
        Part1Question(
            id = "P1-friends-01",
            themeId = "friends",
            themeTitle = "Friends",
            themeTitleZh = "朋友",
            text = "Let's move on. How often do you meet your friends in person?",
        ),
        Part1Question(
            id = "P1-friends-02",
            themeId = "friends",
            themeTitle = "Friends",
            themeTitleZh = "朋友",
            text = "What do you usually do when you spend time with close friends?",
        ),
        Part1Question(
            id = "P1-friends-03",
            themeId = "friends",
            themeTitle = "Friends",
            themeTitleZh = "朋友",
            text = "Do you think it's easy to make new friends as an adult?",
        ),
    )

    /**
     * Builds a fixed-length Part 1 paper: 1–2 themes, [count] bank questions (default 5).
     * Deterministic when [seed] is fixed; bank-only (no AI follow-ups).
     */
    fun pickSession(
        count: Int = DEFAULT_QUESTION_COUNT,
        seed: Long = System.currentTimeMillis(),
    ): List<Part1Question> {
        require(count in 4..5) { "Part 1 mock expects 4–5 questions, got $count" }
        val byTheme = all.groupBy { it.themeId }
        val themeIds = byTheme.keys.toList().sorted()
        val rng = kotlin.random.Random(seed)
        // Prefer 1–2 themes; if a single thin theme cannot fill [count], add a second.
        val preferTwo = rng.nextBoolean()
        val shuffledThemes = themeIds.shuffled(rng)
        val primary = shuffledThemes.first()
        val chosenThemes = buildList {
            add(primary)
            val needSecond = preferTwo || byTheme.getValue(primary).size < count
            if (needSecond) {
                shuffledThemes.drop(1).firstOrNull()?.let { add(it) }
            }
        }
        return if (chosenThemes.size == 2) {
            val t0 = byTheme.getValue(chosenThemes[0]).shuffled(rng)
            val t1 = byTheme.getValue(chosenThemes[1]).shuffled(rng)
            val firstHalf = (count + 1) / 2
            val secondHalf = count - firstHalf
            val mixed = (t0.take(firstHalf) + t1.take(secondHalf)).toMutableList()
            if (mixed.size < count) {
                val used = mixed.map { it.id }.toHashSet()
                val filler = (t0 + t1).filter { it.id !in used }
                mixed += filler.take(count - mixed.size)
            }
            mixed.take(count)
        } else {
            byTheme.getValue(chosenThemes[0]).shuffled(rng).take(count).let { picked ->
                if (picked.size >= count) picked
                else {
                    // Should be rare after needSecond check; fill from remaining bank.
                    val used = picked.map { it.id }.toHashSet()
                    picked + all.filter { it.id !in used }.shuffled(rng).take(count - picked.size)
                }
            }
        }
    }

    fun themeLabel(questions: List<Part1Question>): String {
        val titles = questions.map { it.themeTitleZh }.distinct()
        return titles.joinToString(" · ")
    }
}
