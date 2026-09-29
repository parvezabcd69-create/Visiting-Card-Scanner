package com.example.visitingcardscanner

object CardParser {
    private val emailRegex = Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE)
    private val phoneRegex = Regex("""(?<!\d)(?:\+?[\d][\d\s().-]{7,}\d)(?!\d)""")
    private val urlRegex = Regex("""(?i)\b(?:https?://)?(?:www\.)?[a-z0-9.-]+\.[a-z]{2,}(?:/[^\s]*)?""")

    fun parse(raw: String): CardData {
        val lines = raw.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val email = emailRegex.find(raw)?.value.orEmpty()
        val phone = phoneRegex.findAll(raw)
            .map { it.value.trim() }
            .filter { it.count(Char::isDigit) >= 8 }
            .maxByOrNull { it.count(Char::isDigit) }
            ?.trim()
            .orEmpty()

        val url = urlRegex.find(raw)?.value.orEmpty()

        val remaining = lines.filterNot {
            it.contains(email, true) ||
            (phone.isNotBlank() && it.contains(phone)) ||
            (url.isNotBlank() && it.contains(url, true))
        }

        val jobWords = listOf(
            "ceo", "founder", "director", "manager", "engineer", "sales",
            "marketing", "consultant", "executive", "owner", "md", "chairman",
            "প্রকৌশলী", "ম্যানেজার", "পরিচালক", "ব্যবস্থাপক"
        )
        val jobLine = remaining.firstOrNull { line ->
            jobWords.any { line.contains(it, ignoreCase = true) }
        }.orEmpty()

        val companyLine = remaining
            .filterNot { it == jobLine }
            .getOrNull(1).orEmpty()

        val name = remaining
            .filterNot { it == jobLine || it == companyLine }
            .firstOrNull().orEmpty()

        val notes = buildString {
            if (url.isNotBlank()) append("Website: $url")
            val other = remaining.drop(3).joinToString(" | ")
            if (other.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append(other)
            }
        }

        return CardData(
            name = name,
            phone = phone,
            email = email,
            company = companyLine,
            jobTitle = jobLine,
            notes = notes
        )
    }
}
