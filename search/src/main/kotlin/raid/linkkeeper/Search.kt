package raid.linkkeeper

import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import raid.linkkeeper.data.LinkSearchResult
import java.io.IOException


private fun prepareLink(l: String) = if (l.startsWith("http")) {
    l
} else {
    "http://$l"
}

private val logger = LoggerFactory.getLogger("SearchService")

internal fun doSearch(link: String, text: String): LinkSearchResult {
    val url = prepareLink(link)
    logger.info("Scrapping: $url")

    val doc = try {
        Jsoup.connect(url).get()
    } catch (exc: IOException) {
        logger.error("Scrapping failed", exc)
        return LinkSearchResult(url, emptyList())
    }
    val explorer = PageExplorer(doc)
    return LinkSearchResult(url, explorer.find(text))
}
