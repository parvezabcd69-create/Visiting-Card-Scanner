package com.example.visitingcardscanner

object CardParser {

    private val emailRegex = Regex(
        """[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}""",
        RegexOption.IGNORE_CASE
    )

    private val urlRegex = Regex(
        """(?i)\b(?:https?://)?(?:www\.)?[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+(?:/[^\s,;]*)?"""
    )

    private val phoneRegex = Regex(
        """(?<!\d)(?:\+?\d[\d\s().-]{6,}\d)(?!\d)"""
    )

    private val jobWords = listOf(
        "ceo", "cto", "cfo", "coo", "founder", "co-founder",
        "director", "managing director", "manager", "engineer",
        "developer", "consultant", "executive", "chairman",
        "president", "sales", "marketing", "officer", "owner",
        "ডিরেক্টর", "পরিচালক", "ব্যবস্থাপক", "ম্যানেজার",
        "প্রকৌশলী", "প্রধান নির্বাহী", "চেয়ারম্যান", "চেয়ারম্যান",
        "স্বত্বাধিকারী", "কর্মকর্তা"
    )

    private val addressWords = listOf(
        "address", "road", "rd.", "street", "st.", "avenue",
        "ave.", "lane", "floor", "building", "house", "village",
        "district", "postal", "postcode", "zip",
        "ঠিকানা", "রোড", "সড়ক", "সড়ক", "গ্রাম", "উপজেলা",
        "জেলা", "বাড়ি", "বাড়ি", "ভবন", "ডাকঘর", "পোস্ট কোড"
    )

    private val companyWords = listOf(
        "ltd", "limited", "inc.", "incorporated", "corporation",
        "corp.", "company", "co.", "group", "industries",
        "solutions", "technologies", "technology", "enterprise",
        "কোম্পানি", "লিমিটেড", "গ্রুপ", "ইন্ডাস্ট্রিজ"
    )

    fun parse(raw: String): CardData {
        val lines = raw.lines()
            .map { it.trim().replace(Regex("""\s+"""), " ") }
            .filter { it.isNotBlank() }
            .distinct()

        val email = emailRegex.find(raw)?.value.orEmpty()

        val url = urlRegex.findAll(raw)
            .map { it.value.trim().trimEnd('.', ',', ';', ')', ']') }
            .firstOrNull { candidate ->
                !candidate.contains("@") &&
                    candidate.substringAfterLast('.')
                        .takeWhile { it.isLetter() }
                        .length >= 2
            }.orEmpty()

        val phone = phoneRegex.findAll(raw)
            .map { it.value.trim() }
            .filter { match ->
                val digits = match.filter(Char::isDigit)
                digits.length in 8..15 &&
                    !(match.contains("/") && digits.length < 10)
            }
            .maxByOrNull { it.filter(Char::isDigit).length }
            .orEmpty()

        val remaining = lines.filterNot { line ->
            (email.isNotBlank() && line.contains(email, ignoreCase = true)) ||
                (phone.isNotBlank() &&
                    line.filter(Char::isDigit).contains(phone.filter(Char::isDigit))) ||
                (url.isNotBlank() && line.contains(url, ignoreCase = true))
        }

        val addressLines = remaining.filter { line ->
            addressWords.any { word ->
                line.contains(word, ignoreCase = true)
            } || Regex("""\b\d{4,6}\b""").containsMatchIn(line)
        }

        val nonAddress = remaining - addressLines.toSet()

        val jobTitle = nonAddress.firstOrNull { line ->
            jobWords.any { word -> line.contains(word, ignoreCase = true) }
        }.orEmpty()

        val company = nonAddress.firstOrNull { line ->
            line != jobTitle &&
                companyWords.any { word ->
                    line.contains(word, ignoreCase = true)
                }
        }.orEmpty()

        val candidates = nonAddress.filterNot {
            it == jobTitle || it == company
        }

        // Avoid treating an entire address, email, or URL as a person's name.
        val name = candidates.firstOrNull { line ->
            line.count { it.isLetter() } >= 3 &&
                !line.contains("@") &&
                !urlRegex.containsMatchIn(line) &&
                !addressWords.any { word ->
                    line.contains(word, ignoreCase = true)
                } &&
                !companyWords.any { word ->
                    line.contains(word, ignoreCase = true)
                }
        }.orEmpty()

        val notes = addressLines.joinToString("\n")

        return CardData(
            name = name,
            phone = phone,
            email = email,
            company = company,
            jobTitle = jobTitle,
            notes = notes
        )
    }
}