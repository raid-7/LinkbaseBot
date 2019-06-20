package raid.linkkeeper

import raid.linkkeeper.data.LinkSearchRequest
import raid.linkkeeper.data.LinkSearchResult
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.lang.System.getenv


interface SearchService {
    @POST("search")
    fun search(@Body user: LinkSearchRequest): Call<List<LinkSearchResult>>
}

val retrofit = Retrofit.Builder()
    .baseUrl(getenv("SEARCH_SERVICE_URL") ?: "http://localhost:8080")
    .addConverterFactory(GsonConverterFactory.create())
    .build()
val searchService = retrofit.create(SearchService::class.java)
