import me.ivmg.telegram.network.ResponseError

class ApiException(val error: ResponseError?) : Exception()