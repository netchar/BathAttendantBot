import me.ivmg.telegram.bot
import me.ivmg.telegram.dispatch
import me.ivmg.telegram.dispatcher.callbackQuery
import me.ivmg.telegram.dispatcher.command
import me.ivmg.telegram.dispatcher.telegramError
import me.ivmg.telegram.entities.InlineKeyboardButton
import me.ivmg.telegram.entities.InlineKeyboardMarkup
import me.ivmg.telegram.entities.User
import me.ivmg.telegram.network.fold
import java.time.LocalDate

object Main {

    data class BathVotingDay(val users: MutableSet<User>, val booker: User?) {
        val decided get() = booker != null
    }

    private val votingDays = mutableMapOf<LocalDate, BathVotingDay>()
    private val today: LocalDate = LocalDate.now()
    private fun getTodaysVoting(): BathVotingDay {
        TODO()
    }

    @JvmStatic
    fun main(args: Array<String>) {

        val bot = bot {
            token = "692920526:AAFvu__xE9cqrGsgu1WQXldYJhBXQbCqzA0"

            dispatch {
                command("start") { bot, update ->

                    val result = bot.sendMessage(chatId = update.message!!.chat.id, text = "Bot started")

                    result.fold({
                        // do something here with the response
                    }, {
                        // do something with the error
                    })
                }

                command("go") { bot, update ->
                    val chatId = update.message?.chat?.id ?: return@command

                    val inlineKeyboardMarkup = InlineKeyboardMarkup(generateButtons())
                    val result = bot.sendMessage(
                        chatId = chatId,
                        text = "Пацаны! Идём в баньку?",
                        replyMarkup = inlineKeyboardMarkup
                    )


                    result.fold({
                        if (!votingDays.containsKey(today)) {
                            votingDays[today] = BathVotingDay(mutableSetOf(), null)
                        }
                    }, {
                        // do something with the error
                    })
                }

                command("whoWillBook") { bot, update ->
                    val chatId = update.message?.chat?.id ?: return@command

                    val bookManager = users.shuffled().firstOrNull()
                    val message = if (bookManager != null) {
                        "Поздравляем! Бронирует: ${bookManager.asString()}"
                    } else {
                        "Никто не идёт :("
                    }

                    bot.sendMessage(
                        chatId = chatId,
                        text = message
                    )
                }

                callbackQuery("accept") { bot, update ->
                    update.callbackQuery?.let {
                        val chatId = it.message?.chat?.id ?: return@callbackQuery
                        users.add(it.from)
                        val text =
                            users.joinToString(postfix = if (users.count() < 2) " идёт" else " идут") { user -> user.asString() }

                        bot.sendMessage(chatId = chatId, text = text)
                    }
                }

                callbackQuery("decline") { bot, update ->
                    update.callbackQuery?.let {
                        val chatId = it.message?.chat?.id ?: return@callbackQuery
                        users.remove(it.from)
                        bot.sendMessage(chatId = chatId, text = "${it.from.asString()} не идет")
                    }
                }

                telegramError { _, telegramError ->
                    println(telegramError.getErrorMessage())
                }
            }
        }

        bot.startPolling()
    }

    private fun generateButtons(): List<List<InlineKeyboardButton>> {
        return listOf(
            listOf(InlineKeyboardButton(text = "Иду", callbackData = "accept")),
            listOf(InlineKeyboardButton(text = "Не иду", callbackData = "decline"))
        )
    }

    private fun User.asString(): String = "$firstName ${if (lastName.isNullOrEmpty()) "" else lastName}"
}
