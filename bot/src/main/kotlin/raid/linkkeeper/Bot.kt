package raid.linkkeeper

import raid.linkkeeper.db.Db
import java.lang.System.getenv
import java.net.InetSocketAddress
import java.net.Proxy


private fun getEnvProxy(): Proxy {
    val url: String = getenv("PROXY_URL") ?: return Proxy.NO_PROXY
    val parts = url.split(':')
    return Proxy(Proxy.Type.SOCKS, InetSocketAddress(parts[0], parts[1].toInt()))
}

fun main() {
    println(retrofitUrl)

    val db = Db(getenv("DB_URL"), getenv("DB_USER"), getenv("DB_PASSWORD"))
    val bot = LinkKeeperBot(getenv("TELEGRAM_TOKEN"), db, getEnvProxy())
    bot.startPolling()
}
