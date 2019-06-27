package raid.linkkeeper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import kotlinx.serialization.list
import raid.linkkeeper.data.LinkSearchRequest
import raid.linkkeeper.data.LinkSearchResult
import spark.kotlin.ipAddress
import spark.kotlin.port
import spark.kotlin.post
import spark.kotlin.threadPool


@UnstableDefault
fun main() {
    port(8080)
    ipAddress("0.0.0.0")
    threadPool(16, 2, 1000)

    post("/search") {
        val req = Json.parse(LinkSearchRequest.serializer(), request.body())
        val resp = runBlocking {
            req.links
                .map { async(Dispatchers.IO) { doSearch(it, req.text) } }
                .map { it.await() }
                .filter { it.contexts.isNotEmpty() }
        }

        Json.stringify(LinkSearchResult.serializer().list, resp)
    }
}
