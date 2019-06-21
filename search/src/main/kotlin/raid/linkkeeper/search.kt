package raid.linkkeeper

import org.jsoup.Jsoup
import raid.linkkeeper.data.LinkSearchResult
import java.io.IOException
import kotlin.math.max
import kotlin.math.min


fun prepareLink(l: String) = if (l.startsWith("http")) {
    l
} else {
    "http://$l"
}

fun doSearch(link: String, text: String): LinkSearchResult {
    val url = prepareLink(link)

    println("Scrapping: $url")
    val doc = try {
        Jsoup.connect(url).get()
    } catch (exc: IOException) {
        exc.printStackTrace()
        return LinkSearchResult(url, emptyList())
    }
    return LinkSearchResult(url, searchInText(text, doc.text()))
}

fun searchInText(pattern: String, text: String): List<String> {
    val re = getRegex(pattern)
    println(re.pattern)
    return re.findAll(text)
        .map {
            getOccurrenceContext(text, it)
        }.toList()
}

fun getOccurrenceContext(text: String, match: MatchResult): String {
    val offset = 25
    val re = Regex("""^\S*\s(.+)\s\S*$""")

    val l = max(0, match.range.first - offset)
    val r = min(match.range.last + offset, text.length - 1)

    var s =  text.substring(l..r)
    s = re.find(s)?.groupValues?.getOrNull(1) ?: match.value
    return s.trim()
}

fun getRegex(pattern: String): Regex {
    val s = Regex("""\s+""").split(pattern.trim())
        .map { Regex("""[^\p{L}\d]""").replace(it, ".?") }
        .map { "[^\\p{L}\\d]$it[^\\p{L}\\d]" }
        .joinToString("|")
    return Regex(s, RegexOption.IGNORE_CASE)
}
