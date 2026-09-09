package com.voxcoach.core.domain.part2

/**
 * Seeded IELTS Speaking Part 2 cue cards (CV-02 P2 slice).
 *
 * Original paraphrases covering the five cue-card types in docs/03 §2.2
 * (人物/地点/物品/经历/观点). Not Cambridge verbatim items.
 */
enum class Part2Category(val id: String, val title: String, val titleZh: String) {
    PERSON("person", "Person", "人物"),
    PLACE("place", "Place", "地点"),
    OBJECT("object", "Object", "物品"),
    EXPERIENCE("experience", "Experience", "经历"),
    OPINION("opinion", "Opinion", "观点"),
}

data class Part2CueCard(
    val id: String,
    val category: Part2Category,
    /** Main prompt shown at the top of the cue card. */
    val title: String,
    /** Bullet prompts under "You should say:". */
    val bullets: List<String>,
    /** Optional closing line ("and explain…"). */
    val explain: String,
) {
    fun topicId(): String = "T-p2-${category.id}"
}

object Part2CueCardBank {
    val all: List<Part2CueCard> = listOf(
        Part2CueCard(
            id = "P2-person-01",
            category = Part2Category.PERSON,
            title = "Describe a person who has inspired you to learn something new.",
            bullets = listOf(
                "who this person is",
                "what you learned from them",
                "how you met or first heard about them",
            ),
            explain = "and explain why this person inspired you.",
        ),
        Part2CueCard(
            id = "P2-place-01",
            category = Part2Category.PLACE,
            title = "Describe a quiet place you like to go when you need to think.",
            bullets = listOf(
                "where this place is",
                "how often you go there",
                "what you usually do there",
            ),
            explain = "and explain why this place helps you think.",
        ),
        Part2CueCard(
            id = "P2-object-01",
            category = Part2Category.OBJECT,
            title = "Describe a useful app or digital tool you use regularly.",
            bullets = listOf(
                "what the app or tool is",
                "how you discovered it",
                "what you use it for",
            ),
            explain = "and explain why it is useful to you.",
        ),
        Part2CueCard(
            id = "P2-experience-01",
            category = Part2Category.EXPERIENCE,
            title = "Describe a time when you had to work under pressure.",
            bullets = listOf(
                "what the situation was",
                "why you felt under pressure",
                "how you handled it",
            ),
            explain = "and explain what you learned from that experience.",
        ),
        Part2CueCard(
            id = "P2-opinion-01",
            category = Part2Category.OPINION,
            title = "Describe an important skill that young people should learn today.",
            bullets = listOf(
                "what the skill is",
                "how people can learn it",
                "who might benefit most from it",
            ),
            explain = "and explain why you think this skill is important.",
        ),
        Part2CueCard(
            id = "P2-person-02",
            category = Part2Category.PERSON,
            title = "Describe a teacher or mentor who helped you improve.",
            bullets = listOf(
                "who this person is",
                "what they taught you",
                "how their teaching style was different",
            ),
            explain = "and explain how they helped you improve.",
        ),
        Part2CueCard(
            id = "P2-place-02",
            category = Part2Category.PLACE,
            title = "Describe a public place in your city that you enjoy visiting.",
            bullets = listOf(
                "where it is",
                "what people usually do there",
                "when you last went there",
            ),
            explain = "and explain why you enjoy visiting this place.",
        ),
        Part2CueCard(
            id = "P2-experience-02",
            category = Part2Category.EXPERIENCE,
            title = "Describe a memorable journey you took by yourself or with others.",
            bullets = listOf(
                "where you went",
                "who you travelled with (if anyone)",
                "what happened during the journey",
            ),
            explain = "and explain why this journey was memorable.",
        ),
    )

    const val INTRO_TTS =
        "Now I'm going to give you a topic, and I'd like you to talk about it for one to two minutes. " +
            "Before you talk, you will have one minute to think about what you are going to say. " +
            "You can make some notes if you wish. Here is your topic."

    const val SPEAK_PROMPT_TTS =
        "Alright. Remember you have one to two minutes for this, so don't worry if I stop you. " +
            "Please start speaking now."

    /** Prep countdown in seconds (docs: 1 minute). */
    const val PREP_SECONDS = 60

    /** Long-turn cap in seconds (docs: 1–2 minutes). */
    const val SPEAK_SECONDS = 120

    fun pick(seed: Long = System.currentTimeMillis()): Part2CueCard {
        val rng = kotlin.random.Random(seed)
        return all.random(rng)
    }

    fun pickByCategory(
        category: Part2Category,
        seed: Long = System.currentTimeMillis(),
    ): Part2CueCard {
        val pool = all.filter { it.category == category }
        require(pool.isNotEmpty()) { "No cue cards for $category" }
        return pool.random(kotlin.random.Random(seed))
    }
}
