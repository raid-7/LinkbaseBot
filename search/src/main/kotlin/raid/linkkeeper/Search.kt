package raid.linkkeeper

import org.jsoup.Jsoup
import raid.linkkeeper.data.LinkSearchResult
import java.io.IOException


internal fun prepareLink(l: String) = if (l.startsWith("http")) {
    l
} else {
    "http://$l"
}

internal fun doSearch(link: String, text: String): LinkSearchResult {
    val url = prepareLink(link)

    println("Scrapping: $url")
    val doc = try {
        Jsoup.connect(url).get()
    } catch (exc: IOException) {
        exc.printStackTrace()
        return LinkSearchResult(url, emptyList())
    }
    val explorer = PageExplorer(doc)
    return LinkSearchResult(url, explorer.find(text))
}
