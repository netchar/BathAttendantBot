import me.ivmg.telegram.bot
import me.ivmg.telegram.dispatch
import me.ivmg.telegram.dispatcher.command
import me.ivmg.telegram.network.fold

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val bot = bot {
            token = "692920526:AAFvu__xE9cqrGsgu1WQXldYJhBXQbCqzA0"
            dispatch {
                command("start") { bot, update->
                    val result = bot.sendMessage(chatId = update.message!!.chat.id, text = "Hi there!")
                    result.fold({
                        // do something here with the response
                    },{
                        // do something with the error
                    })
                }
            }
        }
        bot.startPolling()
    }
}
