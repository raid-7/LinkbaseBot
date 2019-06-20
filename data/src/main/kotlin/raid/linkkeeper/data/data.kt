package raid.linkkeeper.data

import kotlinx.serialization.Serializable

@Serializable
data class LinkSearchResult(
    val link: String,
    val contexts: List<String>
)

@Serializable
data class LinkSearchRequest(
    val links: List<String>,
    val text: String
)
