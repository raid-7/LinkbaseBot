package raid.linkkeeper

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.math.max
import kotlin.math.min

class PageExplorer(soup: Document) {
    private val root: Node = Node(soup.body())

    fun find(pattern: String): List<String> {
        val re = getRegex(pattern)
        return root.search { searchInText(re, it) }
    }

    private fun searchInText(re: Regex, text: String): List<String> {
        return re.findAll(text)
            .map {
                getOccurrenceContext(text, it)
            }.toList()
    }

    @Suppress("SimplifiableCallChain")
    private fun getRegex(pattern: String): Regex {
        val s = Regex("""\s+""").split(pattern.trim())
            .map { Regex("""[^\p{L}\d]""").replace(it, ".?") }
            .map { "[^\\p{L}\\d]$it[^\\p{L}\\d]" }
            .joinToString("|")
        return Regex(s, RegexOption.IGNORE_CASE)
    }

    private fun getOccurrenceContext(text: String, match: MatchResult): String {
        val offset = 25
        val re = Regex("""^\S*\s(.+)\s\S*$""")

        val l = max(0, match.range.first - offset)
        val r = min(match.range.last + offset, text.length - 1)

        var s = text.substring(l..r)
        s = re.find(s)?.groupValues?.getOrNull(1) ?: match.value
        s = s.replace(Regex("""[`*_]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .replace(match.value, "*${match.value}*", ignoreCase = true)

        return s.trim()
    }

    private class Node(el: Element) {
        private val text: String = el.text()
        private val children: List<Node> = el.children()
            .filter { it.isBlock }
            .map { Node(it) }

        fun search(strategy: (String) -> List<String>): List<String> {
            val childRes = children.flatMap { it.search(strategy) }
            if (childRes.isEmpty())
                return strategy(text)
            return childRes
        }
    }
}
