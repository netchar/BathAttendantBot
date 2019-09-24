import me.ivmg.telegram.Bot
import me.ivmg.telegram.entities.ChatMember
import me.ivmg.telegram.entities.Update
import me.ivmg.telegram.entities.User
import me.ivmg.telegram.network.Response
import me.ivmg.telegram.network.ResponseError

inline fun Bot.runIfAdmin(update: Update, voting: Voting, message: String, func: Bot.() -> Unit) {
    if (voting.isAdmin(update.user())) {
        func()
    } else {
        sendMessage(update.chatId(), message)
    }
}

fun Update.chatId(): Long = this.message!!.chat.id

fun Update.user(): User = this.message!!.from!!

fun List<ChatMember>.printMembers() = this.joinToString("\n") { it.user.asString() }

fun List<User>.printUsers() = this.joinToString("\n") { it.asString() }

fun User.asString(): String = "$firstName ${lastName ?: ""} (@${username ?: "Позорище без username"})"

@Throws(ApiException::class)
fun <T> Pair<retrofit2.Response<Response<T>?>?, Exception?>.get(): T {
    if (first?.isSuccessful == true && first?.body() != null) {
        return first!!.body()!!.result!!
    } else {
        throw ApiException(ResponseError(first?.errorBody(), second))
    }
}