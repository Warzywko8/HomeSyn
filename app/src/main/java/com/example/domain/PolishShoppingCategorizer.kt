package com.example.domain

import com.example.data.model.ShoppingAisle

data class ParsedShoppingInput(
    val name: String,
    val quantityText: String,
    val category: ShoppingAisle
)

object PolishShoppingCategorizer {

    private val trailingQuantityRegex = Regex(
        """^(.*?)\s+(\d+(?:[.,]\d+)?\s*(?:kg|g|dkg|dag|l|ml|szt\.?|opak\.?|op\.?|pacz\.?|pusz\.?|but\.?|x)?)$""",
        RegexOption.IGNORE_CASE
    )

    private val leadingQuantityRegex = Regex(
        """^(\d+(?:[.,]\d+)?\s*(?:kg|g|dkg|dag|l|ml|szt\.?|opak\.?|op\.?|pacz\.?|pusz\.?|but\.?|x))\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )

    private val keywordMap: Map<ShoppingAisle, List<String>> = mapOf(
        ShoppingAisle.VEG_FRUIT to listOf(
            "banan", "jablk", "gruszk", "pomarancz", "mandarynk", "cytryn", "limonk",
            "truskawk", "malin", "borowk", "jagod", "winogron", "arbuz", "melon", "ananas",
            "kiwi", "mango", "awokado", "brzoskwini", "sliwk", "czere", "wisni",
            "pomidor", "ogork", "ogorek", "papryk", "cebul", "czosn", "ziemniak", "pyr",
            "marchew", "pietruszk", "seler", "por", "burak", "kapust", "salat", "rukola",
            "szpinak", "brokul", "kalafior", "cukini", "baklazan", "rzodkiew", "koper",
            "natk", "szczypior", "bazyli", "mieta", "pieczark", "grzyb", "fasolk", "grosz",
            "kukurydz", "dyni", "batat", "imbir", "warzyw", "owoc"
        ),
        ShoppingAisle.BAKERY to listOf(
            "chleb", "bulk", "bagietk", "rogal", "kajzerk", "grahamk", "tost", "pieczyw",
            "paczek", "paczki", "drozdzowk", "croissant", "ciabatt", "tortill", "pita",
            "lawasz", "brioszk", "chałk", "chalk", "placek", "ciasto", "sernik", "szarlotk"
        ),
        ShoppingAisle.DAIRY to listOf(
            "mlek", "masl", "margaryn", "ser", "twarog", "twaroz", "jogurt", "kefir",
            "maslank", "smietan", "serek", "mozzarell", "parmezan", "feta", "ricott",
            "mascarpone", "camembert", "brie", "cheddar", "gouda", "jajk", "jaja", "jajo",
            "napoj owsian", "napoj migdal", "napoj sojow", "nabial"
        ),
        ShoppingAisle.MEAT_FISH to listOf(
            "kurczak", "indyk", "piers", "udk", "skrzydelk", "schab", "karkowk", "mielon",
            "wolowin", "wieprzowin", "cielecin", "boczek", "boczku", "szynk", "kielbas",
            "parowk", "kabanos", "salami", "pasztet", "wedlin", "poledwic", "zelatyn",
            "losos", "dorsz", "tunczyk", "makrel", "sledz", "pstrag", "krewetk", "ryb",
            "filet", "mieso", "miesa"
        ),
        ShoppingAisle.DRY_GOODS to listOf(
            "makaron", "spaghetti", "penne", "ryz", "kasz", "jaglana", "gryczan", "bulgur",
            "kuskus", "platki", "owsian", "musli", "granola", "mak", "maka", "cukier",
            "cukru", "sol", "soli", "pieprz", "przypraw", "oregano", "papryka slodk",
            "cynamon", "wanili", "proszek do pieczenia", "drozdze", "olej", "oliw", "ocet",
            "sos", "ketchup", "majonez", "musztard", "koncentrat", "passat", "fasol",
            "ciecierzyc", "soczewic", "orzech", "migdal", "rodzynk", "pestk", "ziarn",
            "kaw", "herbat", "kakao", "czekolad", "ciastk", "herbatnik", "wafle", "chips",
            "dzem", "miod", "nutell", "maslo orzechow", "woda", "sok", "piwo", "wino",
            "cola", "napoj", "konserw", "bulion", "kostk"
        ),
        ShoppingAisle.HOME_CHEM to listOf(
            "papier toalet", "recznik papier", "chusteczk", "mydl", "szampon", "zel pod prysznic",
            "odzywk", "pasta do zeb", "szczoteczk", "dezodorant", "krem", "balsam",
            "plyn do naczyn", "kapsu", "tabletki do zmywarki", "sol do zmywarki", "nablyszcz",
            "proszek do prania", "plyn do prania", "plyn do plukania", "plyn do podlog",
            "plyn do szyb", "domestos", "wybielacz", "odkamieniacz", "gabk", "scierk",
            "zmywak", "worki na smieci", "worek", "folia", "papier do pieczenia",
            "rekawicz", "karma", "zwirek", "swieczk", "bateri", "zarowk", "chemia"
        )
    )

    fun parseAndCategorize(rawInput: String): ParsedShoppingInput {
        val cleaned = rawInput.trim().replace(Regex("\\s+"), " ")
        if (cleaned.isEmpty()) {
            return ParsedShoppingInput(name = "", quantityText = "", category = ShoppingAisle.OTHER)
        }

        val leadingMatch = leadingQuantityRegex.matchEntire(cleaned)
        val trailingMatch = trailingQuantityRegex.matchEntire(cleaned)

        val (rawName, rawQty) = when {
            trailingMatch != null && trailingMatch.groupValues[1].isNotBlank() -> {
                trailingMatch.groupValues[1].trim() to trailingMatch.groupValues[2].trim()
            }
            leadingMatch != null && leadingMatch.groupValues[2].isNotBlank() -> {
                leadingMatch.groupValues[2].trim() to leadingMatch.groupValues[1].trim()
            }
            else -> cleaned to ""
        }

        val formattedName = rawName.replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase() else ch.toString()
        }.take(80)

        val category = categorize(rawName)
        return ParsedShoppingInput(
            name = formattedName,
            quantityText = rawQty.take(30),
            category = category
        )
    }

    fun categorize(productName: String): ShoppingAisle {
        val normalized = normalizePolish(productName)
        if (normalized.isBlank()) return ShoppingAisle.OTHER

        // Check HOME_CHEM multi-word / specific phrases first (e.g., "papier do pieczenia", "pasta do zebow")
        val orderedSearch = listOf(
            ShoppingAisle.HOME_CHEM,
            ShoppingAisle.DAIRY,
            ShoppingAisle.BAKERY,
            ShoppingAisle.MEAT_FISH,
            ShoppingAisle.VEG_FRUIT,
            ShoppingAisle.DRY_GOODS
        )

        for (aisle in orderedSearch) {
            val stems = keywordMap[aisle].orEmpty()
            for (stem in stems) {
                val normStem = normalizePolish(stem)
                if (normStem.contains(" ")) {
                    if (normalized.contains(normStem)) return aisle
                } else {
                    val words = normalized.split(" ")
                    if (words.any { word -> word.startsWith(normStem) || (normStem.length >= 4 && word.contains(normStem)) }) {
                        return aisle
                    }
                }
            }
        }
        return ShoppingAisle.OTHER
    }

    fun normalizePolish(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text.lowercase()) {
            sb.append(
                when (ch) {
                    'ą' -> 'a'
                    'ć' -> 'c'
                    'ę' -> 'e'
                    'ł' -> 'l'
                    'ń' -> 'n'
                    'ó' -> 'o'
                    'ś' -> 's'
                    'ź', 'ż' -> 'z'
                    else -> ch
                }
            )
        }
        return sb.toString().replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
    }
}
