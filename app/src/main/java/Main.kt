import me.ivmg.telegram.Bot
import me.ivmg.telegram.bot
import me.ivmg.telegram.dispatch
import me.ivmg.telegram.dispatcher.callbackQuery
import me.ivmg.telegram.dispatcher.command
import me.ivmg.telegram.dispatcher.telegramError
import me.ivmg.telegram.entities.InlineKeyboardButton
import me.ivmg.telegram.entities.InlineKeyboardMarkup
import me.ivmg.telegram.entities.Update
import me.ivmg.telegram.network.fold
import okhttp3.logging.HttpLoggingInterceptor

private const val COMMAND_START = "start"
private const val COMMAND_HELP = "help"
private const val COMMAND_BOOK = "book"
private const val COMMAND_RESET = "reset"
private const val COMMAND_STOP = "stop"
private const val QUERY_ACCEPT = "accept"
private const val QUERY_DECLINE = "decline"

private const val HELP_TEXT = """
Создатель нарёк меня Банщик, научил проводить голосование и выбирать того, кто будет бронировать баньку.
А если будете себя плохо вести, научусь банить в чате.

Команды:
/start - начинает общение и выводит информацию о себе
/help - показывает доступные комманды и описание
/book - выбирает красавчика, который будет бронировать баню
/reset - обнуляет текущее голосование
/stop - отменяет голосование
"""

const val MESSAGE_VOTING_UNINITIALIZED = "Голосование не запущено"
const val MESSAGE_VOTING_ONGOING = "Голосование уже запущено."
const val MESSAGE_VOTING_FINISHED = "Голосование завершено."
const val MESSAGE_READY_TO_BATH = "Ну что, идём в баньку?"
const val MESSAGE_NO_ONE_IS_COMING = "Никто не идёт :("
const val MESSAGE_VOTING_RESET = "Пацаны, я всё обнулил. Запускаю все сначала."

const val MESSAGE_ERROR_UNABLE_TO_START_VOTING = "Не могу запустить голосование."

const val BOT_API_TOKEN = "706074071:AAEn6vo9DmEFEjYd8IcbC2boMslsxdpXJMQ"

private val inlineKeyboardMarkup = InlineKeyboardMarkup(
    listOf(
        listOf(InlineKeyboardButton(text = "Иду", callbackData = QUERY_ACCEPT)),
        listOf(InlineKeyboardButton(text = "Не иду", callbackData = QUERY_DECLINE))
    )
)

private var voting: Voting? = null

fun main(args: Array<String>) {
    bot {
        token = BOT_API_TOKEN
        logLevel = HttpLoggingInterceptor.Level.BASIC

        dispatch {
            command(COMMAND_START, ::onStart)
            command(COMMAND_HELP, ::onHelp)
            command(COMMAND_STOP, ::onStop)
            command(COMMAND_BOOK, ::onBook)
            command(COMMAND_RESET, ::onReset)

            callbackQuery(QUERY_ACCEPT, ::onAccept)
            callbackQuery(QUERY_DECLINE, ::onDecline)

            telegramError { _, error -> println(error.getErrorMessage()) }
        }
    }.startPolling()
}

private fun onStart(bot: Bot, update: Update) {
    val currentVoting = voting
    when {
        currentVoting == null -> {
            bot.sendMessage(update.chatId(), MESSAGE_READY_TO_BATH, replyMarkup = inlineKeyboardMarkup).fold({
                initVoting(bot, update, it?.result?.messageId!!)
            })
        }
        currentVoting.isOngoing() -> bot.sendMessage(update.chatId(), MESSAGE_VOTING_ONGOING)
        currentVoting.isFinished() -> bot.informVotingFinished(update, currentVoting)
    }
}

private fun onHelp(bot: Bot, update: Update) {
    bot.sendMessage(update.chatId(), text = HELP_TEXT)
}

private fun onStop(bot: Bot, update: Update) {
    val chatId = update.chatId()

    try {
        val currentVoting = voting

        if (currentVoting == null) {
            bot.sendMessage(chatId, MESSAGE_VOTING_UNINITIALIZED)
        } else {
            bot.runIfAdmin(update, currentVoting, "Завершить голосование может только:\n ${currentVoting.admins.printMembers()}") {
                deleteVotingMessage(chatId, currentVoting)
                sendMessage(chatId, MESSAGE_VOTING_FINISHED).fold({
                    resetVoting()
                })
            }
        }
    } catch (ex: ApiException) {
        println(ex.localizedMessage)
    }
}

private fun onBook(bot: Bot, update: Update) {
    val chatId = update.chatId()

    val currentVoting = voting

    when {
        currentVoting == null -> bot.sendMessage(chatId, MESSAGE_VOTING_UNINITIALIZED)
        currentVoting.isFinished() -> bot.informVotingFinished(update, currentVoting)
        else -> {
            val booker = currentVoting.chooseBookManager()

            if (booker == null) {
                bot.sendMessage(chatId, MESSAGE_NO_ONE_IS_COMING)
            } else {
                bot.deleteVotingMessage(chatId, currentVoting)

                val participants = currentVoting.getParticipants()
                bot.sendMessage(chatId, buildString {
                    appendln("Поздравляем! Бронирует:")
                    appendln(booker.asString())
                    appendln()
                    appendln(if (participants.count() > 1) "Идут: " else "Идёт: ")
                    appendln(participants.joinToString(separator = "\n") { it.asString() })
                })
            }
        }
    }
}

private fun onReset(bot: Bot, update: Update) {
    val currentVoting = voting
    val chatId = update.chatId()

    if (currentVoting == null) {
        bot.sendMessage(chatId, MESSAGE_VOTING_UNINITIALIZED)
    } else {
        bot.runIfAdmin(update, currentVoting, "Хрена себе ты захотел! Сбросить голосование может только:\n ${currentVoting.admins.printMembers()}") {
            deleteMessage(chatId, currentVoting.votingMessageId)
            sendMessage(chatId, MESSAGE_VOTING_RESET, replyMarkup = inlineKeyboardMarkup).fold({
                initVoting(this, update, it?.result?.messageId!!)
            })
        }
    }
}

private fun onAccept(bot: Bot, update: Update) {
    val query = update.callbackQuery
    val currentVoting = voting

    if (query != null && currentVoting != null) {
        currentVoting.acceptInvitation(query.from)

        val participants = currentVoting.getParticipants()

        val message = buildString {
            appendln(if (participants.count() < 2) " Идёт:" else " Идут:")
            appendln(participants.printUsers())
            appendln()
            appendln(getRemainingVotersMessage(currentVoting))
        }

        val chatId = query.message?.chat?.id!!
        bot.sendMessage(chatId, message)
    }
}

private fun onDecline(bot: Bot, update: Update) {
    val query = update.callbackQuery
    val currentVoting = voting

    if (query != null && currentVoting != null) {
        val user = query.from

        currentVoting.declineInvitation(user)

        val message = buildString {
            appendln("${user.asString()} не идёт")
            appendln()
            appendln(getRemainingVotersMessage(currentVoting))
        }

        val chatId = query.message?.chat?.id!!
        bot.sendMessage(chatId, message)
    }
}

private fun Bot.deleteVotingMessage(chatId: Long, currentVoting: Voting) {
    deleteMessage(chatId, currentVoting.votingMessageId)
}

private fun Bot.informVotingFinished(update: Update, currentVoting: Voting) {
    sendMessage(update.chatId(), buildString {
        appendln("У нас уже есть бронирующий красавчик:")
        appendln(currentVoting.booker!!.asString())
    })
}

private fun getRemainingVotersMessage(currentVoting: Voting): String {
    val remainingVoters = currentVoting.getRemainingVoters()
    return if (remainingVoters > 0) {
        "Не проголосовало: $remainingVoters"
    } else {
        "Все проголосовали"
    }
}

private fun initVoting(bot: Bot, update: Update, newMessageId: Long) {
    val chatId = update.chatId()
    try {
        val admins = bot.getChatAdministrators(chatId).get()
        val votersCount = bot.getChatMembersCount(chatId).get()
        voting = Voting(votersCount, admins, newMessageId)
    } catch (ex: ApiException) {
        bot.sendMessage(chatId, MESSAGE_ERROR_UNABLE_TO_START_VOTING)
    }
}

private fun resetVoting() {
    voting = null
}
