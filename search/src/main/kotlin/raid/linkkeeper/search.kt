package raid.linkkeeper

import org.jsoup.Jsoup
import raid.linkkeeper.data.LinkSearchResult


fun prepareLink(l: String) = if (l.startsWith("http")) {
    l
} else {
    "http://$l"
}

fun doSearch(link: String, text: String): LinkSearchResult {
    val url = prepareLink(link)

    println("Scrapping: $url")
    val doc = Jsoup.connect(url).get()
    Jsoup.connect(url)
    return LinkSearchResult(url, searchInText(text, doc.text()))
}

fun searchInText(pattern: String, text: String): List<String> {
    val re = getRegex(pattern)
    println(re.pattern)
    return re.findAll(text)
        .map { it.value }
        .toList()
}

fun getRegex(pattern: String): Regex {
    val s = Regex("""\s+""").split(pattern.trim())
        .map { Regex("""[^\p{L}\d]""").replace(it, ".?") }
        .map { "[^\\p{L}\\d]$it[^\\p{L}\\d]" }
        .joinToString("|")
    return Regex(s, RegexOption.IGNORE_CASE)
}
