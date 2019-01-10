import me.ivmg.telegram.bot
import me.ivmg.telegram.dispatch
import me.ivmg.telegram.dispatcher.callbackQuery
import me.ivmg.telegram.dispatcher.command
import me.ivmg.telegram.dispatcher.telegramError
import me.ivmg.telegram.entities.InlineKeyboardButton
import me.ivmg.telegram.entities.InlineKeyboardMarkup
import me.ivmg.telegram.entities.User
import me.ivmg.telegram.network.fold
import java.lang.Exception
import java.time.LocalDate

object Main {

    class VotingNotStartedException(message: String) : Exception(message)

    data class BathVotingDay(val users: MutableSet<User>, val booker: User?) {
        val decided get() = booker != null
    }

    private val votingDays = mutableMapOf<LocalDate, BathVotingDay>()
    private val today: LocalDate = LocalDate.now()

    private fun getTodayVoting(): BathVotingDay {
        return votingDays[today] ?: throw VotingNotStartedException("Сначала запусти голосовалку, глупый")
    }

    private val users get() = getTodayVoting().users

    @JvmStatic
    fun main(args: Array<String>) {

        val bot = bot {
            token = "692920526:AAFvu__xE9cqrGsgu1WQXldYJhBXQbCqzA0"

            dispatch {
                command("start") { bot, update ->
                    bot.sendMessage(chatId = update.message!!.chat.id, text = "Я готов!")
                }

                command("go") { bot, update ->
                    val chatId = update.message?.chat?.id ?: return@command

                    val inlineKeyboardMarkup = InlineKeyboardMarkup(
                        listOf(
                            listOf(InlineKeyboardButton(text = "Иду", callbackData = "accept")),
                            listOf(InlineKeyboardButton(text = "Не иду", callbackData = "decline"))
                        )
                    )

                    val result = bot.sendMessage(
                        chatId = chatId,
                        text = "Пацаны! Идём в баньку?",
                        replyMarkup = inlineKeyboardMarkup
                    )

                    result.fold({
                        if (votingDays.containsKey(today)) {
                            if (getTodayVoting().decided) {
                                bot.sendMessage(
                                    chatId = chatId,
                                    text = "Вы уже проголосовали, чего выпендриваешься?"
                                )
                            }
                        } else {
                            resetTodayVoting()
                        }
                    }, {
                        // do something with the error
                    })
                }

                command("booking") { bot, update ->
                    val chatId = update.message?.chat?.id ?: return@command
                    val message = try {
                        val bookManager = users.shuffled().firstOrNull()

                        if (bookManager != null) {
                            "Поздравляем! Бронирует: ${bookManager.asString()}"
                        } else {
                            "Никто не идёт :("
                        }
                    } catch (ex: VotingNotStartedException) {
                        ex.message!!
                    }


                    bot.sendMessage(chatId = chatId, text = message)
                }

                command("reset") { bot, update ->
                    val chatId = update.message?.chat?.id ?: return@command
                    bot.sendMessage(chatId, "Я всё обнулил, запускайте голосовалку сначала.")
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

    private fun resetTodayVoting() {
        votingDays[today] = BathVotingDay(mutableSetOf(), null)
    }

    private fun User.asString(): String = "$firstName ${if (lastName.isNullOrEmpty()) "" else lastName}"
}
