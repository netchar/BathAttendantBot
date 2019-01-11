import me.ivmg.telegram.Bot
import me.ivmg.telegram.bot
import me.ivmg.telegram.dispatch
import me.ivmg.telegram.dispatcher.callbackQuery
import me.ivmg.telegram.dispatcher.command
import me.ivmg.telegram.dispatcher.telegramError
import me.ivmg.telegram.entities.*
import me.ivmg.telegram.network.fold
import java.time.LocalDate

object Main {

    class VotingNotStartedException(message: String) : Exception(message)

    data class Voting(private val totalVoters: Int, val admins: List<ChatMember>) {
        private var users: MutableSet<User> = mutableSetOf()
        private var booker: User? = null
        val isDecided get() = booker != null
        val remainingVoters: Int get() = (totalVoters - 1) - users.count()
        fun addParticipant(user: User) = users.add(user)
        fun removeParticipant(user: User) = users.remove(user)
        fun getParticipants(): List<User> = users.toList()
        fun chooseBookManager(): User? {
            booker = users.shuffled().firstOrNull()
            return booker
        }

        fun isAdmin(user: User): Boolean {
            return admins.any { chatMember -> chatMember.user.id == user.id }
        }
    }

    private val votingDays = mutableMapOf<LocalDate, Voting>()
    private val today: LocalDate = LocalDate.now()

    private fun getTodayVoting(): Voting {
        return votingDays[today] ?: throw VotingNotStartedException("Сначала запусти голосовалку, глупый")
    }

    private lateinit var myBot: Bot

    @JvmStatic
    fun main(args: Array<String>) {
        myBot = bot {
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

                    bot.sendMessage(
                        chatId = chatId,
                        text = "Ну что, идём в баньку?",
                        replyMarkup = inlineKeyboardMarkup
                    )

                    if (votingDays.containsKey(today)) {
                        if (getTodayVoting().isDecided) {
                            bot.sendMessage(
                                chatId = chatId,
                                text = "Вы уже проголосовали, чего выпендриваешься?"
                            )
                        }
                    } else {
                        resetVoting(chatId)
                    }
                }

                command("booking") { bot, update ->
                    val chatId = update.message?.chat?.id ?: return@command
                    val message = try {
                        val voting = getTodayVoting()
                        val bookManager = voting.chooseBookManager()
                        if (bookManager != null) {
                            "Поздравляем! Бронирует: ${bookManager.asString()}\n" +
                                    "Идут: ${voting.getParticipants().joinToString { it.asString() }}"
                        } else {
                            "Никто не идёт :("
                        }
                    } catch (ex: VotingNotStartedException) {
                        ex.message!!
                    }

                    bot.sendMessage(chatId = chatId, text = message)
                }

                command("reset") { bot, update ->
                    runIfAdmin(update) {
                        val chatId = update.message?.chat?.id ?: return@runIfAdmin
                        resetVoting(chatId)
                        bot.sendMessage(chatId, "Я всё обнулил, запускайте голосовалку сначала.")
                    }
                }

                callbackQuery("accept") { bot, update ->
                    update.callbackQuery?.let { callback ->
                        val chatId = callback.message?.chat?.id ?: return@callbackQuery
                        val voting = getTodayVoting()
                        voting.addParticipant(callback.from)
                        val participants = voting.getParticipants()
                        val text = participants.joinToString(postfix = if (participants.count() < 2) " идёт" else " идут") { user -> user.asString() }

                        bot.sendMessage(chatId = chatId, text = "$text. Не проголосовало: ${voting.remainingVoters}")
                    }
                }

                callbackQuery("decline") { bot, update ->
                    update.callbackQuery?.let {
                        val chatId = it.message?.chat?.id ?: return@callbackQuery
                        val voting = getTodayVoting()
                        voting.removeParticipant(it.from)
                        bot.sendMessage(chatId = chatId, text = "${it.from.asString()} не идет")
                    }
                }

                telegramError { _, telegramError ->
                    println(telegramError.getErrorMessage())
                }
            }
        }

        myBot.startPolling()
    }

    private fun resetVoting(chatId: Long) {
        val chatAdminsResponse = myBot.getChatAdministrators(chatId)
        val membersResponse = myBot.getChatMembersCount(chatId)

        chatAdminsResponse.fold(response = { chat ->
            val admins = chat!!.result!!
            membersResponse.fold(response = { members ->
                val totalVoters = members!!.result!!
                votingDays[today] = Voting(totalVoters, admins)
            }, error = {
                myBot.sendMessage(chatId = chatId, text = "Не получилось сбросить голосовалку :(")
            })
        }, error = {
            myBot.sendMessage(chatId = chatId, text = "Не получилось сбросить голосовалку :(")
        })
    }

    private fun User.asString(): String = "$firstName ${lastName ?: ""} (@${username ?: "Позорище, сделай себе UserName уже!"})"

    private fun runIfAdmin(update: Update, unit: () -> Unit) {
        val voting = getTodayVoting()
        if (voting.isAdmin(update.message!!.from!!)) {
            unit.invoke()
        } else {
            myBot.sendMessage(
                update.message!!.chat.id,
                "Хрена себе ты захотел! Сбросить голосование может только ${voting.admins.joinToString { chatMember -> chatMember.user.asString() }}"
            )
        }
    }
}
