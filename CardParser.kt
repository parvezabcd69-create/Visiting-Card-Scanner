package com.example.visitingcardscanner

object CardParser {

    private val emailRegex = Regex(
        """[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}""",
        RegexOption.IGNORE_CASE
    )

    private val urlRegex = Regex(
        """(?i)\b(?:https?://)?(?:www\.)?[a-z0-9-]+(?:\.[a-z0-9-]+)+\.[a-z]{2,}(?:/[^\s,;]*)?|\bwww\.[a-z0-9.-]+\.[a-z]{2,}(?:/[^\s,;]*)?"""
    )

    private val phoneRegex = Regex(
        """(?<!\d)\+?\d[\d\s().-]{6,}\d(?!\d)"""
    )

    private val jobWords = listOf(
        "ceo", "cto", "cfo", "coo", "founder", "director",
        "manager", "engineer", "developer", "consultant",
        "executive", "chairman", "president", "officer",
        "ডিরেক্টর", "পরিচালক", "ব্যবস্থাপক", "ম্যানেজার",
        "প্রকৌশলী", "চেয়ারম্যান", "চেয়ারম্যান", "কর্মকর্তা"
    )

    private val addressWords = listOf(
        "address", "road", "street", "avenue", "lane",
        "floor", "building", "house", "village", "district",
        "postal", "postcode", "zip", "ঠিকানা", "রোড", "সড়ক",
        "সড়ক", "গ্রাম", "উপজেলা", "জেলা", "বাড়ি", "বাড়ি",
        "ভবন", "ডাকঘর"
    )

    private val companyWords = listOf(
        "ltd", "limited", "inc", "corporation", "corp",
        "company", "group", "industries", "solutions",
        "technologies", "enterprise", "কোম্পানি", "লিমিটেড",
        "গ্রুপ", "ইন্ডাস্ট্রিজ"
    )

    fun parse(raw: String): CardData {
        val lines = raw.lines()
            .map { it.trim().replace(Regex("""\s+"""), " ") }
            .filter { it.isNotBlank() }
            .distinct()

        val email = emailRegex.find(raw)?.value.orEmpty()

        val website = urlRegex.findAll(raw)
            .map { it.value.trim().trimEnd('.', ',', ';', ')', ']') }
            .firstOrNull { !it.contains("@") }
            .orEmpty()

        val phone = phoneRegex.findAll(raw)
            .map { it.value.trim() }
            .filter { it.filter(Char::isDigit).length in 8..15 }
            .maxByOrNull { it.filter(Char::isDigit).length }
            .orEmpty()

        val remaining = lines.filterNot { line ->
            (email.isNotBlank() && line.contains(email, true)) ||
            (website.isNotBlank() && line.contains(website, true)) ||
            (phone.isNotBlank() &&
                line.filter(Char::isDigit).contains(
                    phone.filter(Char::isDigit)
                ))
        }

        val addressLines = remaining.filter { line ->
            addressWords.any { line.contains(it, true) } ||
            Regex("""\b\d{4,6}\b""").containsMatchIn(line)
        }

        val others = remaining.filterNot { it in addressLines }

        val job = others.firstOrNull { line ->
            jobWords.any { line.contains(it, true) }
        }.orEmpty()

        val company = others.firstOrNull { line ->
            line != job &&
                companyWords.any { line.contains(it, true) }
        }.orEmpty()

        val name = others.firstOrNull { line ->
            line != job &&
                line != company &&
                line.count { it.isLetter() } >= 3 &&
                !line.contains("@") &&
                !urlRegex.containsMatchIn(line) &&
                !addressWords.any { line.contains(it, true) }
        }.orEmpty()

        val notes = others.filter {
            it != name && it != job && it != company
        }.joinToString("\n")

        return CardData(
            name = name,
            phone = phone,
            email = email,
            company = company,
            jobTitle = job,
            website = website,
            address = addressLines.joinToString("\n"),
            notes = notes
        )
    }
}