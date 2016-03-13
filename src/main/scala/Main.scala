

import model.{Reg, ApiRes, From}
import org.http4s
import org.json4s._
import org.json4s.native.JsonMethods._
import scala.annotation.tailrec
import scala.concurrent.stm._
import org.http4s.Http4s._
import org.http4s._
import org.json4s.JsonDSL._
import scala.concurrent.stm.{Ref, TMap}
import scala.util.parsing.json.{JSONArray, JSON, JSONObject, JSONType}
import scalaz.-\/
import scalaz.concurrent.{Strategy, Task}
import scalaz.stream._
import scala.concurrent.duration._

object Main {

  case class TelegramBot(token: String) {
    val url = s"https://api.telegram.org/bot$token"
    val getUpdates = "getUpdates"
    val sendMessage = "sendMessage"
    def getUpdatesUri = Uri.fromString(s"$url/$getUpdates").toOption.get
    def sendMessageUri = Uri.fromString(s"$url/$sendMessage").toOption.get
  }

  trait Repo {
    type Sub = From

    def addSub(sub: Reg): Task[Unit]

    def getSubs(string: String): Task[Seq[Sub]]
  }
  val repps = new Repo {
    val subs = TMap.empty[String, Seq[Sub]]

    override def addSub(reg: Reg): Task[Unit] = Task[Unit] {
      println("New sub is " + reg)
      atomic {
        implicit tx =>
          subs.get(reg.streamer) match {
            case Some(chats) => subs.put(reg.streamer, reg.chat +: chats)
            case None => subs.put(reg.streamer, Seq(reg.chat))
          }
      }
    }

    override def getSubs(streamer: String): Task[Seq[Sub]] = {
      Task({
        subs.single.get(streamer) match {
          case Some(x) => x
          case None => Seq()
        }
      })
    }
  }
  def main(args: Array[String]): Unit = {
    implicit val S = Strategy.DefaultStrategy
    implicit val scheduler = scalaz.stream.DefaultScheduler
    val client = org.http4s.client.blaze.defaultClient
    val token = "" // api key
    val bot = TelegramBot(token)
    println(bot.getUpdatesUri.withQueryParam("offset", 55.toString))

    def z(s: Long): Task[(List[ApiRes], Long)] = {
      client
        .prepare(Request(uri = bot.getUpdatesUri.withQueryParam("offset", s.toString).withQueryParam("timeout", 100)))
        .as[String]
        .map { json =>
        implicit val formats = DefaultFormats
        val msgs = (parse(json) \\ "result").camelizeKeys.extractOpt[List[model.ApiRes]].getOrElse(List())
        val update_id = if (msgs.nonEmpty) msgs.map(_.updateId).max else -1l
        if (update_id != -1l) (msgs, update_id + 1) else (msgs, s)
      }
    }

    import Process._
    import model._

    val aa: Task[Unit] = iterateEval((List.empty[ApiRes], 0l))(x => z(x._2))
      .map(_._1)
      .map(_.map { res => res.message.toReg })
      .map(_.collect { case Some(x) => x })
      .flatMap(emitAll(_))
      .zip(Process.constant(repps))
      .to(sink.lift { case (reg, repo) => repo.addSub(reg) }).run

    client.prepare(Request())

    sealed trait AState
    case object NotAccept extends AState
    case object Accept extends AState

    sealed trait SState
    case object Stream extends SState
    case object NoStream extends SState

    def fsm(st: (AState, SState), json: Option[JValue]): (AState, SState) = {
      println(json + "---" + st)
      val a = json match {
        case Some(a: JObject) if Option(a.values("stream")).isDefined => Stream
        case _ => NoStream
      }
      val zzz = st match {
        case _ if a == NoStream => (NotAccept, NoStream)
        case (Accept, Stream) if a == Stream => (NotAccept, Stream)
        case (NotAccept, Stream) if a == NoStream => (NotAccept, NoStream)
        case (NotAccept, NoStream) if a == Stream => (Accept, Stream)
        case st@(Accept, NoStream) => st
        case id => id
      }
      println(json + "---" + zzz)
      zzz
    }
    val init: (AState, SState) = (NotAccept, NoStream)
    val task: Task[String] = client(uri("https://api.twitch.tv/kraken/streams/lightofheaven")).as[String]
    val streamer = "evilarthas"
//        time
//          .awakeEvery(1.seconds)
//          .zip(Process.repeatEval(task))
//    .map(_._2)
    val bb: Task[Unit] = io.stdInLines
      .map(json => parseOpt(json))
      .scan(init)(fsm)
      .collect { case (Accept, _) => Accept }
      .zip(Process.constant(repps))
      .map(x => x._2.getSubs(streamer))
      .flatMap(subs => await(subs)(emitAll))
      .zip(Process.constant(bot))
      .zip(Process.constant(client))
      .to(sink.lift(tpl => {
      Task[Unit] {
        println("sending")
        tpl._2.prepare(
          tpl._1._2.sendMessageUri
            .withQueryParam("text", "фывыфв")
            .withQueryParam("chat_id", tpl._1._1.id.toString)).as[String].runAsync { ignored => }
      }
    })).run
    aa.runAsync { ignored => println("bot listener end") }
    bb.runAsync {
      case x: -\/[Throwable] =>
        throw x.a
      case a => println("fetch ended " + a)
    }
    Thread.sleep(Long.MaxValue)
  }

}
