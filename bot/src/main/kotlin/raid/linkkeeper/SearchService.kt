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

val retrofitUrl = getenv("SEARCH_SERVICE_URL") ?: "http://localhost:8080"
val retrofit: Retrofit = Retrofit.Builder()
    .baseUrl(retrofitUrl)
    .addConverterFactory(GsonConverterFactory.create())
    .build()
val searchService: SearchService = retrofit.create(SearchService::class.java)
