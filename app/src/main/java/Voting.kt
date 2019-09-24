import me.ivmg.telegram.entities.ChatMember
import me.ivmg.telegram.entities.User

data class Voting(
    private val totalVoters: Int,
    val admins: List<ChatMember>,
    val votingMessageId: Long
) {
    private var answeredUsers: MutableMap<User, Boolean> = mutableMapOf()

    var booker: User? = null
        private set

    fun isOngoing() = booker == null

    fun isFinished() = !isOngoing()

    fun getRemainingVoters(): Int {
        val botsCount = admins.count { it.user.isBot }
        return totalVoters - botsCount - answeredUsers.count()
    }

    fun acceptInvitation(user: User) {
        answeredUsers[user] = true
    }

    fun declineInvitation(user: User) {
        answeredUsers[user] = false
    }

    fun getParticipants(): List<User> = answeredUsers.filter { it.hasAcceptedInvitation() }.keys.toList()

    fun chooseBookManager(): User? {
        if (isOngoing()) {
            booker = getParticipants().shuffled().firstOrNull()
        }
        return booker
    }

    fun isAdmin(user: User): Boolean {
        return admins.any { chatMember -> chatMember.user.id == user.id }
    }

    private fun Map.Entry<User, Boolean>.hasAcceptedInvitation() = value
    private fun Map.Entry<User, Boolean>.hasDeclinedInvitation() = !value
}