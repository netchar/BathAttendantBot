import me.ivmg.telegram.Bot
import me.ivmg.telegram.bot
import me.ivmg.telegram.dispatch
import me.ivmg.telegram.dispatcher.callbackQuery
import me.ivmg.telegram.dispatcher.command
import me.ivmg.telegram.dispatcher.telegramError
import me.ivmg.telegram.entities.*
import me.ivmg.telegram.network.fold
import java.time.LocalDate

private const val COMMAND_START = "start"
private const val COMMAND_HELP = "help"
private const val COMMAND_BOOK = "book"
private const val COMMAND_VOTE = "vote"
private const val COMMAND_RESET = "reset"
private const val COMMAND_STOP = "stop"

private const val START_TEXT = """
Ну что парни, я помогу вам определиться кто идет в баньку.

Список комманд:
/start - начинает общение и выводит информацию о себе
/help - показывает доступные комманды и описание
/vote - запускает голосование
/book - выбирает красавчика, бронирующего баньку
/reset - обнуляет текущее голосование
/stop - отменяет голосование
"""

private const val HELP_TEXT = """
Создатель нарёк меня Банщик, научил проводить голосование и выбирать того, кто будет бронировать баньку.
А если будете себя плохо вести, научусь банить в чате.

Комманды:
/start - начинает общение и выводит информацию о себе
/help - показывает доступные комманды и описание
/vote - запускает голосование
/book - выбирает красавчика, который будет бронировать баню
/reset - обнуляет текущее голосование
/stop - отменяет голосование
"""

const val MESSAGE_RESET = "Пацаны, я всё обнулил. Запускаю все сначала."
const val MESSAGE_GO_TO_BATH = "Ну что, идём в баньку?"
const val MESSAGE_VOTING_ONGOING = "Голосовалка уже запущена!"
const val MESSAGE_WE_HAVE_WINNER = "У нас уже есть победитель"
const val MESSAGE_CONGRAC_WINNER = "Поздравляем! Бронирует"
const val MESSAGE_NOBODY_COME = "Никто не идёт :("
const val MESSAGE_DIDNT_VOTED = "Не проголосовало"
const val MESSAGE_FAIL_TO_RESET = "Не получилось сбросить голосовалку :("

const val BOT_API_TOKEN = "706074071:AAG99X02_Uk9Tn0TVTO5fAk50dRwzPcE7p4"

object Main {

    class VotingNotStartedException(message: String) : Exception(message)

    data class Voting(private val totalVoters: Int, val admins: List<ChatMember>, val messageId: Long) {
        private var users: MutableSet<User> = mutableSetOf()
        var booker: User? = null
            private set

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

    private val inlineKeyboardMarkup = InlineKeyboardMarkup(
        listOf(
            listOf(InlineKeyboardButton(text = "Иду", callbackData = "accept")),
            listOf(InlineKeyboardButton(text = "Не иду", callbackData = "decline"))
        )
    )

    private fun Update.chatId(): Long = this.message!!.chat.id
    private fun User.asString(): String = "$firstName ${lastName ?: ""} (@${username ?: "Позорище, сделай себе UserName уже!"})"

    @JvmStatic
    fun main(args: Array<String>) {
        myBot = bot {
            token = BOT_API_TOKEN

            dispatch {
                command(COMMAND_START) { bot, update ->
                    bot.sendMessage(chatId = update.chatId(), text = START_TEXT)
                }

                command(COMMAND_HELP) { bot, update ->
                    bot.sendMessage(chatId = update.chatId(), text = HELP_TEXT)
                }

                command(COMMAND_STOP) { bot, update ->
                    runIfAdmin(update) {
                        resetVoting(update.chatId(), 0)
                        bot.sendMessage(chatId = update.chatId(), text = "Голосование отменено.")
                    }
                }

                command(COMMAND_VOTE) { bot, update ->
                    if (votingDays.containsKey(today)) {
                        val voting = getTodayVoting()
                        if (voting.isDecided) {

                            bot.sendMessage(
                                chatId = update.chatId(),
                                text = "$MESSAGE_WE_HAVE_WINNER: ${voting.booker!!.asString()}"
                            )
                        } else {

                            bot.sendMessage(
                                chatId = update.chatId(),
                                text = MESSAGE_VOTING_ONGOING
                            )
                        }
                    } else {

                        val voteResponse = bot.sendMessage(
                            chatId = update.chatId(),
                            text = MESSAGE_GO_TO_BATH,
                            replyMarkup = inlineKeyboardMarkup
                        )

                        voteResponse.fold(response = { response ->
                            resetVoting(update.chatId(), response!!.result!!.messageId)
                        })
                    }
                }

                command(COMMAND_BOOK) { bot, update ->
                    val message = try {
                        val voting = getTodayVoting()
                        val bookManager = voting.chooseBookManager()
                        if (bookManager != null) {
                            val participants = voting.getParticipants()
                            "$MESSAGE_CONGRAC_WINNER: ${bookManager.asString()}\n" +
                                    participants.joinToString(prefix = if (participants.count() > 1) "Идут: " else "Идёт: ") { it.asString() }
                        } else {
                            MESSAGE_NOBODY_COME
                        }
                    } catch (ex: VotingNotStartedException) {
                        ex.message!!
                    }

                    bot.sendMessage(chatId = update.chatId(), text = message)
                }

                command(COMMAND_RESET) { bot, update ->
                    runIfAdmin(update) {
                        val currVoting = getTodayVoting()
                        bot.deleteMessage(update.chatId(), currVoting.messageId)
                        bot.sendMessage(update.chatId(), MESSAGE_RESET, replyMarkup = inlineKeyboardMarkup).fold(response = { response ->
                            resetVoting(update.chatId(), response!!.result!!.messageId)
                        })
                    }
                }

                callbackQuery("accept") { bot, update ->
                    update.callbackQuery?.let { callback ->
                        val chatId = callback.message?.chat?.id ?: return@callbackQuery
                        val voting = getTodayVoting()
                        voting.addParticipant(callback.from)
                        val participants = voting.getParticipants()
                        val text = participants.joinToString(postfix = if (participants.count() < 2) " идёт" else " идут") { it.asString() }
                        bot.sendMessage(chatId = chatId, text = "$text.\n$MESSAGE_DIDNT_VOTED: ${voting.remainingVoters}")
                    }
                }

                callbackQuery("decline") { bot, update ->
                    update.callbackQuery?.let { it ->
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


    private fun resetVoting(chatId: Long, messageId: Long) {
        myBot.getChatAdministrators(chatId).fold(response = { chat ->
            val admins = chat!!.result!!
            myBot.getChatMembersCount(chatId).fold(response = { members ->
                val totalVoters = members!!.result!!
                votingDays[today] = Voting(totalVoters, admins, messageId)
            }, error = {
                myBot.sendMessage(chatId = chatId, text = MESSAGE_FAIL_TO_RESET)
            })
        }, error = {
            myBot.sendMessage(chatId = chatId, text = "Ты не в чате")
        })
    }

    private fun runIfAdmin(update: Update, unit: () -> Unit) {
        val voting = getTodayVoting()
        if (voting.isAdmin(update.message!!.from!!)) {
            unit.invoke()
        } else {
            myBot.sendMessage(
                update.chatId(),
                "Хрена себе ты захотел! Сбросить голосование может только ${voting.admins.joinToString { it.user.asString() }}"
            )
        }
    }
}
