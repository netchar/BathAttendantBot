import me.ivmg.telegram.entities.ChatMember
import me.ivmg.telegram.entities.User

data class Voting(
    private val totalVoters: Int,
    val admins: List<ChatMember>,
    val votingMessageId: Long
) {
    private var users: MutableSet<User> = mutableSetOf()

    var booker: User? = null
        private set

    fun isOngoing() = booker == null

    fun isFinished() = !isOngoing()

    fun getRemainingVoters(): Int {
        return (totalVoters - users.count { it.isBot }) - users.count()
    }

    fun addParticipant(user: User) = users.add(user)

    fun removeParticipant(user: User) = users.remove(user)

    fun getParticipants(): List<User> = users.toList()

    fun chooseBookManager(): User? {
        if (isOngoing()) {
            booker = users.shuffled().firstOrNull()
        }
        return booker
    }

    fun isAdmin(user: User): Boolean {
        return admins.any { chatMember -> chatMember.user.id == user.id }
    }
}

fun Voting?.uninitialized() = this == null