package raid.linkkeeper

import me.ivmg.telegram.bot
import me.ivmg.telegram.dispatch
import me.ivmg.telegram.dispatcher.command
import me.ivmg.telegram.dispatcher.telegramError
import me.ivmg.telegram.dispatcher.text
import me.ivmg.telegram.entities.Message
import me.ivmg.telegram.entities.ParseMode
import okhttp3.logging.HttpLoggingInterceptor
import org.slf4j.LoggerFactory
import raid.linkkeeper.data.LinkSearchRequest
import raid.linkkeeper.data.LinkSearchResult
import raid.linkkeeper.db.ChatState
import raid.linkkeeper.db.Db
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.net.Proxy


private val Message.urls: List<String>
    get() = (entities ?: emptyList()).mapNotNull {
        when {
            it.type == "text_link" -> it.url!!
            it.type == "url" -> text!!.substring(it.offset, it.offset + it.length)
            else -> null
        }
    }

private val Message.tags: List<String>
    get() = (entities ?: emptyList())
        .filter { it.type == "hashtag" }
        .map {
            text!!.substring(it.offset, it.offset + it.length)
        }


internal class LinkKeeperBot(tgToken: String, private val db: Db = Db(), proxy: Proxy = Proxy.NO_PROXY) {
    private val logger = LoggerFactory.getLogger(LinkKeeperBot::class.java)

    private val bot = bot {
        token = tgToken
        this.proxy = proxy
        logLevel = HttpLoggingInterceptor.Level.BASIC

        dispatch {
            text { _, update ->
                update.message?.apply {
                    logger.info("Got update: ${update.updateId}")

                    if (text?.startsWith("/") != false)
                        return@apply

                    when (db.getChatState(chat.id)) {
                        ChatState.COMMON -> {
                            if (urls.isNotEmpty()) {
                                saveTags(this)
                                saveLinks(this)
                            } else {
                                bot.deleteMessage(chat.id, messageId)
                            }
                        }
                        ChatState.SEARCH -> {
                            text?.let { searchImpl(chat.id, it) }
                            db.setChatState(chat.id, ChatState.COMMON)
                        }
                    }
                }
            }

            command("delete") { _, update ->
                update.message?.apply {
                    deleteImpl(this)
                    db.setChatState(chat.id, ChatState.COMMON)
                }
            }

            command("search") { _, update, args ->
                update.message?.apply {
                    val text = args.joinToString(" ").trim()
                    if (text.isEmpty()) {
                        db.setChatState(chat.id, ChatState.SEARCH)
                        sendSearchPhraseRequest(chat.id)
                    } else {
                        searchImpl(chat.id, text)
                        db.setChatState(chat.id, ChatState.COMMON)
                    }
                }
            }

            command("links") { _, update ->
                update.message?.apply {
                    val links = db.getUserLinks(chat.id)
                    sendLinksList(chat.id, links)
                    db.setChatState(chat.id, ChatState.COMMON)
                }
            }

            command("tags") { _, update ->
                update.message?.apply {
                    val tags = db.getUserTags(chat.id)
                    sendTagsList(chat.id, tags)
                    db.setChatState(chat.id, ChatState.COMMON)
                }
            }

            command("help") { _, update ->
                update.message?.apply {
                    sendHelp(chat.id)
                    db.setChatState(chat.id, ChatState.COMMON)
                }
            }
            command("start") { _, update ->
                update.message?.apply {
                    sendHelp(chat.id)
                    db.setChatState(chat.id, ChatState.COMMON)
                }
            }

            command("stats") { _, update ->
                update.message?.apply {
                    sendStats(chat.id)
                }
            }

            telegramError { _, err ->
                logger.error("Telegram error: ${err.getErrorMessage()}")
            }
        }
    }

    fun startPolling() {
        bot.startPolling()
    }

    private fun sendStats(chatId: Long) {
        bot.sendMessage(
            chatId,
            """
            Bot usage statistics:

            Chats: %d
            Links: %d
            Tags: %d
            """.trimIndent().format(
                db.countChats(),
                db.countLinks(),
                db.countTags()
            )
        )
    }

    private fun saveLinks(msg: Message) {
        msg.urls.forEach { db.addLink(msg.chat.id, it) }
    }

    private fun saveTags(msg: Message) {
        msg.tags.forEach { db.addTag(msg.chat.id, it) }
    }

    private fun sendSearchPhraseRequest(chatId: Long) {
        bot.sendMessage(chatId, "Enter search keywords:")
    }

    private fun sendTagsList(chatId: Long, tags: List<String>) {
        val msg = if (tags.isEmpty()) {
            "No tags"
        } else {
            tags.joinToString("\n")
        }
        bot.sendMessage(chatId, msg, disableWebPagePreview = true)
    }

    private fun sendLinksList(chatId: Long, links: List<String>) {
        val msg = if (links.isEmpty()) {
            "No links"
        } else {
            links.joinToString("\n")
        }
        bot.sendMessage(chatId, msg, disableWebPagePreview = true)
    }

    private fun sendHelp(chatId: Long) {
        bot.sendMessage(
            chatId,
            """
            Hi, I can keep your links and search through them.

            Just send me a url and I'll save it.
            Reply to sent link with /delete to remove the url.
            Use /search *some text* to look for *some text* on all saved pages.

            Use /links to see the list of saved links.

            You can attach hashtags to your messages with links to take advantage of Telegram search.
            I can show you the list of all your hashtags. Just type /tags.
            """.trimIndent(), parseMode = ParseMode.MARKDOWN
        )
    }

    private fun sendSearchError(chatId: Long) {
        bot.sendMessage(chatId, "Service temporary unavailable")
    }

    private fun sendSearchResult(chatId: Long, res: List<LinkSearchResult>) {
        var msg = res.joinToString("\n\n") {
            "${it.link}\n" + it.contexts.joinToString("\n")
        }
        if (msg.trim().isEmpty())
            msg = "Not found"

        bot.sendMessage(
            chatId, msg,
            disableWebPagePreview = true, parseMode = ParseMode.MARKDOWN
        )
    }

    private fun sendDeleteExplanation(chatId: Long) {
        bot.sendMessage(
            chatId,
            """
                /delete without attached message does nothing.
                Reply with /delete to your message with a link to remove it.
            """.trimIndent()
        )
    }

    private fun deleteImpl(request: Message) {
        request.apply {
            bot.deleteMessage(chat.id, messageId)
            replyToMessage?.apply {
                bot.deleteMessage(chat.id, messageId)
                urls.forEach { db.removeLink(chat.id, it) }
            } ?: sendDeleteExplanation(chat.id)
        }
    }

    private fun searchImpl(chatId: Long, text: String) {
        val links = db.getUserLinks(chatId)
        val req = LinkSearchRequest(links, text)

        searchService.search(req).enqueue(object :
            Callback<List<LinkSearchResult>> {
            override fun onResponse(
                call: Call<List<LinkSearchResult>>,
                response: Response<List<LinkSearchResult>>
            ) {
                sendSearchResult(chatId, response.body() ?: emptyList())
            }

            override fun onFailure(call: Call<List<LinkSearchResult>>, t: Throwable) {
                logger.error("SearchService failed", t)
                sendSearchError(chatId)
            }
        })
    }
}
