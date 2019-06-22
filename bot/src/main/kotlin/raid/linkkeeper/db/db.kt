package raid.linkkeeper.db

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.DEFAULT_REPETITION_ATTEMPTS
import org.jetbrains.exposed.sql.transactions.transaction
import java.lang.System.getenv
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

object Links : Table("links") {
    val chatId = long("chat_id").primaryKey()
    val link = text("link").primaryKey()
}

object Tags : Table("tags") {
    val chatId = Links.long("chat_id").primaryKey()
    val tag = Links.text("tag").primaryKey()
}

class Db(url: String? = null) {

    val conn: Database

    init {
        val finalUrl = url ?: "jdbc:sqlite:bot-test.db"
        val driver = try {
            DriverManager.getDriver(finalUrl).javaClass.name
        } catch (_: SQLException) {
            "org.sqlite.JDBC"
        }
        conn = Database.connect(finalUrl, driver)

        transaction {
            SchemaUtils.create(Links)
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

    private fun <T> transaction(statement: Transaction.() -> T): T = transaction(
        Connection.TRANSACTION_SERIALIZABLE, DEFAULT_REPETITION_ATTEMPTS, conn, statement
    )

}
