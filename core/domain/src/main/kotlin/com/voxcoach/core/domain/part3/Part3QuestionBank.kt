package com.voxcoach.core.domain.part3

import com.voxcoach.core.domain.part2.Part2Category
import com.voxcoach.core.domain.part2.Part2CueCard
import com.voxcoach.core.domain.part2.Part2CueCardBank

/**
 * Seeded IELTS Speaking Part 3 discussion follow-ups (CV-02 P3 slice).
 *
 * Questions are original paraphrases linked to Part 2 cue-card themes
 * (docs/03 §2.2). Not Cambridge verbatim items.
 */
data class Part3Question(
    val id: String,
    val category: Part2Category,
    val text: String,
) {
    fun topicId(): String = "T-p3-${category.id}"
}

object Part3QuestionBank {
    const val DEFAULT_QUESTION_COUNT = 5

    const val INTRO_TTS =
        "We've been talking about the topic on your cue card, and I'd like to ask you " +
            "some more general questions related to this. Let's discuss."

    val all: List<Part3Question> = listOf(
        // Person theme
        Part3Question(
            id = "P3-person-01",
            category = Part2Category.PERSON,
            text = "Why do some people become role models for others in society?",
        ),
        Part3Question(
            id = "P3-person-02",
            category = Part2Category.PERSON,
            text = "Do you think celebrities have a responsibility to set a good example? Why or why not?",
        ),
        Part3Question(
            id = "P3-person-03",
            category = Part2Category.PERSON,
            text = "How has the way people find mentors changed with the internet?",
        ),
        Part3Question(
            id = "P3-person-04",
            category = Part2Category.PERSON,
            text = "In what ways can teachers influence a student's long-term choices?",
        ),
        Part3Question(
            id = "P3-person-05",
            category = Part2Category.PERSON,
            text = "Should schools invite more guest speakers from different professions? Why?",
        ),
        // Place theme
        Part3Question(
            id = "P3-place-01",
            category = Part2Category.PLACE,
            text = "Why do cities need quiet public spaces for residents?",
        ),
        Part3Question(
            id = "P3-place-02",
            category = Part2Category.PLACE,
            text = "How can urban planners balance tourism with the daily needs of locals?",
        ),
        Part3Question(
            id = "P3-place-03",
            category = Part2Category.PLACE,
            text = "Do you think people today spend enough time outdoors? Why or why not?",
        ),
        Part3Question(
            id = "P3-place-04",
            category = Part2Category.PLACE,
            text = "What makes a public place feel welcoming to people of different ages?",
        ),
        Part3Question(
            id = "P3-place-05",
            category = Part2Category.PLACE,
            text = "How might technology change the way we experience public spaces in the future?",
        ),
        // Object / digital tool theme
        Part3Question(
            id = "P3-object-01",
            category = Part2Category.OBJECT,
            text = "How has digital technology changed everyday problem-solving?",
        ),
        Part3Question(
            id = "P3-object-02",
            category = Part2Category.OBJECT,
            text = "Do you think people rely too much on apps for simple tasks? Why?",
        ),
        Part3Question(
            id = "P3-object-03",
            category = Part2Category.OBJECT,
            text = "What skills should young people learn so they are not dependent on one tool?",
        ),
        Part3Question(
            id = "P3-object-04",
            category = Part2Category.OBJECT,
            text = "How can schools teach students to evaluate whether a new app is trustworthy?",
        ),
        Part3Question(
            id = "P3-object-05",
            category = Part2Category.OBJECT,
            text = "Will physical tools still matter as digital tools become more common? Why?",
        ),
        // Experience theme
        Part3Question(
            id = "P3-experience-01",
            category = Part2Category.EXPERIENCE,
            text = "Why do some people perform better under pressure than others?",
        ),
        Part3Question(
            id = "P3-experience-02",
            category = Part2Category.EXPERIENCE,
            text = "Should workplaces reduce unnecessary pressure on employees? How?",
        ),
        Part3Question(
            id = "P3-experience-03",
            category = Part2Category.EXPERIENCE,
            text = "What can young people learn from travelling independently?",
        ),
        Part3Question(
            id = "P3-experience-04",
            category = Part2Category.EXPERIENCE,
            text = "Do memorable journeys always need to be expensive or far away? Why or why not?",
        ),
        Part3Question(
            id = "P3-experience-05",
            category = Part2Category.EXPERIENCE,
            text = "How can reflecting on past challenges help people make better decisions later?",
        ),
        // Opinion / skills theme
        Part3Question(
            id = "P3-opinion-01",
            category = Part2Category.OPINION,
            text = "Which skills will be most valuable for young people in the next decade? Why?",
        ),
        Part3Question(
            id = "P3-opinion-02",
            category = Part2Category.OPINION,
            text = "Should soft skills like communication be taught as seriously as academic subjects?",
        ),
        Part3Question(
            id = "P3-opinion-03",
            category = Part2Category.OPINION,
            text = "How can governments encourage lifelong learning among adults?",
        ),
        Part3Question(
            id = "P3-opinion-04",
            category = Part2Category.OPINION,
            text = "Do online courses replace traditional classrooms, or only complement them?",
        ),
        Part3Question(
            id = "P3-opinion-05",
            category = Part2Category.OPINION,
            text = "What role should parents play in helping teenagers choose which skills to develop?",
        ),
    )

    fun forCategory(category: Part2Category): List<Part3Question> =
        all.filter { it.category == category }

    fun pickForCategory(
        category: Part2Category,
        count: Int = DEFAULT_QUESTION_COUNT,
        seed: Long = System.currentTimeMillis(),
    ): List<Part3Question> {
        val pool = forCategory(category)
        require(pool.isNotEmpty()) { "No Part 3 questions for $category" }
        val rng = kotlin.random.Random(seed)
        val shuffled = pool.shuffled(rng)
        if (shuffled.size >= count) return shuffled.take(count)
        // Rare: pad by cycling if bank ever shrinks below count.
        val out = shuffled.toMutableList()
        var i = 0
        while (out.size < count) {
            out += shuffled[i % shuffled.size]
            i++
        }
        return out
    }

    fun pickForCueCard(
        card: Part2CueCard,
        count: Int = DEFAULT_QUESTION_COUNT,
        seed: Long = System.currentTimeMillis(),
    ): List<Part3Question> = pickForCategory(card.category, count, seed)

    /**
     * Standalone Part 3: pick a Part 2 cue as theme anchor, then 4–5 follow-ups.
     */
    fun pickSession(
        count: Int = DEFAULT_QUESTION_COUNT,
        seed: Long = System.currentTimeMillis(),
    ): Pair<Part2CueCard, List<Part3Question>> {
        val card = Part2CueCardBank.pick(seed)
        val questions = pickForCueCard(card, count, seed xor 0x503L)
        return card to questions
    }

    fun themeLabel(card: Part2CueCard): String =
        "${card.category.titleZh} · ${card.category.title}"
}
