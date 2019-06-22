package raid.linkkeeper.db

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.DEFAULT_REPETITION_ATTEMPTS
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

enum class ChatState {
    COMMON, SEARCH
}

object Links : Table("links") {
    val chatId = long("chat_id").primaryKey()
    val link = text("link").primaryKey()
}

object Tags : Table("tags") {
    val chatId = long("chat_id").primaryKey()
    val tag = text("tag").primaryKey()
}

object ChatStates : Table("chat_states") {
    val chatId = long("chat_id").primaryKey()
    val state = enumeration("state", ChatState::class)
}

class Db(url: String? = null, user: String? = null, password: String? = null) {
    private val conn: Database

    init {
        val finalUrl = url ?: "jdbc:sqlite:bot-test.db"
        val driver = try {
            DriverManager.getDriver(finalUrl).javaClass.name
        } catch (_: SQLException) {
            if (finalUrl.contains("sqlite")) {
                "org.sqlite.JDBC"
            } else if (finalUrl.contains("postgres")) {
                "org.postgresql.Driver"
            } else {
                throw RuntimeException("Cannot determine jdbc driver")
            }
        }
        conn = Database.connect(finalUrl, driver, user = user ?: "", password = password ?: "")

        transaction {
            SchemaUtils.create(Links, Tags, ChatStates)
        }
    }

    fun addLink(chat: Long, url: String) {
        transaction {
            val isNew = Links.select {
                Links.chatId.eq(chat) and Links.link.eq(url)
            }.empty()

            if (isNew)
                Links.insert {
                    it[chatId] = chat
                    it[link] = url
                }
        }
    }

    fun addTag(chat: Long, tag: String) {
        transaction {
            val isNew = Tags.select {
                Tags.chatId.eq(chat) and Tags.tag.eq(tag)
            }.empty()

            if (isNew)
                Tags.insert {
                    it[chatId] = chat
                    it[Tags.tag] = tag
                }
        }
    }

    fun removeLink(chat: Long, url: String) {
        transaction {
            Links.deleteWhere {
                Links.chatId.eq(chat) and Links.link.eq(url)
            }
        }
    }

    fun removeTag(chat: Long, tag: String) {
        transaction {
            Tags.deleteWhere {
                Tags.chatId.eq(chat) and Tags.tag.eq(tag)
            }
        }
    }

    fun getUserLinks(chat: Long): List<String> =
        transaction {
            Links.select {
                Links.chatId.eq(chat)
            }.map {
                it[Links.link]
            }
        }

    fun getUserTags(chat: Long): List<String> =
        transaction {
            Tags.select {
                Tags.chatId.eq(chat)
            }.map {
                it[Tags.tag]
            }
        }

    fun withState(chat: Long, callback: (ChatState) -> ChatState) {
        transaction {
            setChatState(chat, callback(getChatState(chat)))
        }
    }

    fun withState(chat: Long, fromState: ChatState, callback: () -> ChatState) {
        withState(chat) {
            if (it == fromState) {
                callback()
            } else {
                it
            }
        }
    }

    fun getChatState(chat: Long): ChatState = transaction {
        val row = ChatStates.select {
            ChatStates.chatId.eq(chat)
        }.firstOrNull()
        row?.let { it[ChatStates.state] } ?: ChatState.values()[0]
    }

    fun setChatState(chat: Long, state: ChatState): Unit = transaction {
        val row = ChatStates.update({ ChatStates.chatId.eq(chat) }) {
            it[ChatStates.state] = state
        }
        if (row != 0) {
            ChatStates.insert {
                it[chatId] = chat
                it[ChatStates.state] = state
            }
        }
    }

    private fun <T> transaction(statement: Transaction.() -> T): T = transaction(
        Connection.TRANSACTION_SERIALIZABLE, DEFAULT_REPETITION_ATTEMPTS, conn, statement
    )
}
