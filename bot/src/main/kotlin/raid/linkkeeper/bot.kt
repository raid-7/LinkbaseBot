package raid.linkkeeper

import me.ivmg.telegram.HandleUpdate
import me.ivmg.telegram.bot
import me.ivmg.telegram.dispatch
import me.ivmg.telegram.dispatcher.command
import me.ivmg.telegram.dispatcher.text
import me.ivmg.telegram.dispatcher.handlers.Handler
import me.ivmg.telegram.dispatcher.Dispatcher
import me.ivmg.telegram.entities.Update
import me.ivmg.telegram.entities.Message
import me.ivmg.telegram.entities.ParseMode
import okhttp3.logging.HttpLoggingInterceptor
import raid.linkkeeper.data.LinkSearchRequest
import retrofit2.await
import java.lang.System.getenv
import java.net.InetSocketAddress
import java.net.Proxy


class DefaultHandler(func: HandleUpdate) : Handler(func) {
    override val groupIdentifier = "DefaultHandler"

    override fun checkUpdate(update: Update): Boolean {
        return true
    }
}

fun Dispatcher.addDefault(func: HandleUpdate) {
    addHandler(DefaultHandler(func))
}

val Message.urls: List<String>
    get() = (entities ?: emptyList()).mapNotNull {
        when {
            it.type == "text_link" -> it.url!!
            it.type == "url" -> text!!.substring(it.offset, it.offset + it.length)
            else -> null
        }
    }

fun getEnvProxy(): Proxy {
    val url: String = getenv("PROXY_URL") ?: return Proxy.NO_PROXY
    val parts = url.split(':')
    return Proxy(Proxy.Type.SOCKS, InetSocketAddress(parts[0], parts[1].toInt()))
}

//val links = mutableMapOf<Long, MutableSet<String>>()
val db = Db()

fun main() {
    val bot = bot {
        token = getenv("TELEGRAM_TOKEN")
        proxy = getEnvProxy()
        logLevel = HttpLoggingInterceptor.Level.BASIC

        dispatch {
            command("delete") { bot, update ->
                update.message?.replyToMessage?.apply {
                    bot.deleteMessage(chat.id, messageId)
                    urls.forEach { db.removeLink(chat.id, it) }
                }
            }

            text { bot, update ->
                update.message?.apply {
                    urls.forEach { db.addLink(chat.id, it) }
                }
            }

            command("search") { bot, update, args ->
                update.message?.apply {
                    val text = args.joinToString(" ")
                    val links = db.getUserLinks(chat.id)
                    println(links)

                    val req = LinkSearchRequest(links, text)
                    searchService.search(req).execute().apply {
                        if (!isSuccessful) {
                            bot.sendMessage(chat.id, "Service temporary unavailable")
                        } else {
                            val msg = (body() ?: emptyList()).joinToString("\n\n") {
                                "${it.link}\n" + it.contexts.joinToString("\n")
                            }
                            bot.sendMessage(
                                chat.id, msg,
                                disableWebPagePreview = true, parseMode = ParseMode.MARKDOWN
                            )
                        }
                    }
                }
            }

            command("help") { bot, update ->
                update.message?.apply {
                    bot.sendMessage(chat.id, """
                        Hi, I can keep your links and search through them.
                        
                        Just send me a url and I'll save it.
                        Reply to sent link with */delete* to remove the url.
                        Use */search some text* to look for *some text* on all saved pages.
                    """.trimIndent(), parseMode = ParseMode.MARKDOWN)
                }
            }
        }
    }
    bot.startPolling()
}
