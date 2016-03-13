
object model {

  case class From(id: Long,
                  firstName: String,
                  lastName: String)

  case class Message(messageId: Long,
                     from: From,
                     chat: From,
                     date: Long,
                     text: String)

  case class ApiRes(updateId: Long,
                    message: Message)


  sealed trait Command

  case class Reg(chat: From, streamer: String)

  implicit class MessageOps(message: Message) {
    val pattern = "reg (.+)".r

    def toReg = {
      val iter = pattern.findAllIn(message.text).matchData
      if (iter.hasNext)
        Some(Reg(message.chat, iter.next().group(1)))
      else
        None
    }
  }

}
