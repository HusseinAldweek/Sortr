package com.sortr.domain.analysis

import com.sortr.domain.model.ScreenshotRules

/** Kinds of photos Sortr recognises on the phone. [bit] is the flag stored per photo. */
enum class SmartCategory(val bit: Int) {
    DOCUMENTS(1),
    RECEIPTS(2),
    MEMES(4),
    FOOD(8),
    PETS(16),
}

/** What the on-device models saw in a photo. */
data class ImageSignals(
    /** Image labels with their confidence, e.g. "Dog" to 0.92. */
    val labels: Map<String, Float>,
    /** Text read from the photo, lines separated by newlines. */
    val text: String,
    val relativePath: String?,
    val displayName: String,
)

/**
 * Turns model output into categories. These are best guesses: labels come from a general image
 * model, and documents, receipts and memes are recognised by the text in them.
 */
object SmartCategories {

    fun of(mask: Int): Set<SmartCategory> = SmartCategory.entries.filterTo(LinkedHashSet()) { mask and it.bit != 0 }

    fun classify(signals: ImageSignals): Int {
        var mask = 0
        if (signals.labels.any { (label, confidence) -> label in FOOD_LABELS && confidence >= LABEL_CONFIDENCE }) mask = mask or SmartCategory.FOOD.bit
        if (signals.labels.any { (label, confidence) -> label in PET_LABELS && confidence >= LABEL_CONFIDENCE }) mask = mask or SmartCategory.PETS.bit

        // Screenshots are their own thing: they're full of text but aren't documents or memes.
        if (ScreenshotRules.isScreenshot(signals.relativePath, signals.displayName)) return mask
        val lines = signals.text.lines().filter { it.isNotBlank() }
        val words = WORD.findAll(signals.text).count()
        when {
            isReceipt(signals.text) -> mask = mask or SmartCategory.RECEIPTS.bit
            words >= DOCUMENT_MIN_WORDS || lines.size >= DOCUMENT_MIN_LINES -> mask = mask or SmartCategory.DOCUMENTS.bit
            words in MEME_WORDS && lines.size <= MEME_MAX_LINES && isFromChatOrSocial(signals.relativePath) ->
                mask = mask or SmartCategory.MEMES.bit
        }
        return mask
    }

    private fun isReceipt(text: String): Boolean =
        RECEIPT_WORDS.containsMatchIn(text) && PRICE.findAll(text).count() >= RECEIPT_MIN_PRICES

    private fun isFromChatOrSocial(relativePath: String?): Boolean =
        relativePath != null && CHAT_AND_SOCIAL.any { relativePath.contains(it, ignoreCase = true) }

    private const val LABEL_CONFIDENCE = 0.7f
    private const val DOCUMENT_MIN_WORDS = 40
    private const val DOCUMENT_MIN_LINES = 10
    private val MEME_WORDS = 2..40
    private const val MEME_MAX_LINES = 8
    private const val RECEIPT_MIN_PRICES = 3

    private val WORD = Regex("""\p{L}{2,}""")
    private val PRICE = Regex("""\d+[.,]\d{2}\b""")
    private val RECEIPT_WORDS = Regex(
        """\b(total|subtotal|sub-total|tax|vat|cash|change|amount due|balance|receipt|invoice|qty|tip|visa|mastercard)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val CHAT_AND_SOCIAL = listOf(
        "WhatsApp", "Telegram", "Instagram", "Messenger", "Facebook", "Snapchat", "TikTok", "Reddit", "Twitter", "Download",
    )

    private val FOOD_LABELS = setOf(
        "Food", "Dessert", "Cake", "Cookie", "Pizza", "Bread", "Fruit", "Vegetable", "Sushi", "Hot dog", "Cheeseburger",
        "Hamburger", "Bento", "Pasta", "Salad", "Sandwich", "Noodle", "Soup", "Dish", "Cuisine", "Meal", "Breakfast",
        "Lunch", "Dinner", "Baked goods", "Fast food", "Ice cream", "Chocolate", "Candy", "Donut", "Pie", "Burrito",
        "Taco", "Steak", "Coffee", "Juice", "Cocktail", "Wine", "Beer",
    )

    private val PET_LABELS = setOf("Dog", "Cat", "Pet", "Puppy", "Kitten", "Hamster", "Rabbit", "Goldfish", "Parrot")
}
