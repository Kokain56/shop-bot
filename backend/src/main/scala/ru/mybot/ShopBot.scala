package ru.mybot

import cats.Parallel
import cats.effect.{Async, IO}
import io.circe.Json
import io.circe.generic.auto._
import io.circe.parser.parse
import telegramium.bots.high.implicits._
import telegramium.bots.high.{Api, LongPollBot}

class ShopBot(implicit
              bot: Api[IO],
              asyncF: Async[IO],
              parallel: Parallel[IO],
              dao: Dao
             ) extends LongPollBot[IO](bot) {

  import telegramium.bots._

  override def onMessage(msg: Message): IO[Unit] = {
    msg.text match {
      case Some("/start") => ioGroup(msg)
      case _ => IO.unit
    }

  }

  private def ioGroup(msg: Message): IO[Unit] = for {
    list <- dao.getProductGroup
    listK <- IO(InlineKeyboardMarkup(list.map(v => InlineKeyboardButton(v, callbackData = Some(v))).sliding(3, 3).toList
      ::: List(List(InlineKeyboardButton("В корзину", callbackData = Some("В корзину")))))).option
    _ <- dao.writeState(msg.chat.id, "0")
    mes <- sendMessage(
      chatId = ChatIntId(msg.chat.id),
      text = "Выберите категорию товара",
      replyMarkup = listK
    ).exec.void >> deleteMessage(ChatIntId(msg.chat.id), msg.messageId).exec
  } yield (mes)

  override def onCallbackQuery(query: CallbackQuery): IO[Unit] = {
    def onMsg(message: Option[MaybeInaccessibleMessage])(f: Message => IO[Unit]): IO[Unit] =
      message.collect { case m: Message => m }.fold(asyncF.unit)(f)

    def ioBin(msg: Message): IO[Unit] = for {
      str <- dao.readJson(msg.chat.id)
      json <- IO.fromEither(parse(str))
      _ <- dao.writeState(msg.chat.id, "2")
      listP <- IO(InlineKeyboardMarkup(List(List(InlineKeyboardButton("Оформить", callbackData = Some("Оформить"))))
        ::: List(List(InlineKeyboardButton("На главную", callbackData = Some("На главную")))))).option
      text <- formatJson(json)
      mes <- sendMessage(
        chatId = ChatIntId(msg.chat.id),
        text = text,
        parseMode = Some(Markdown),
        replyMarkup = listP).exec >> deleteMessage(ChatIntId(msg.chat.id), msg.messageId).exec >> answerCallbackQuery(callbackQueryId = query.id).exec
    } yield (mes)

    def formatJson(json: Json) = for {
      array <- IO.fromEither(json.as[Array[Bin]])
      str <- IO(array.foldLeft("")((acc, x) => acc + x.name + " : " + x.count.toString + "\n"))
    } yield (str)


    def startMethodByState(command: String, msg: Message): IO[Unit] = for {
      state <- dao.readState(msg.chat.id)
      _ <- command match {
        case x if x.startsWith("На главную") => ioGroup(msg)
        case x if x.startsWith("В корзину") => ioBin(msg)
        case x if x.startsWith("Оформить") => ioOrder(msg)
        case _ => ioState(state, command, msg)
      }
    } yield ()

    def ioState(state: String, command: String, msg: Message): IO[Unit] = state match {
      case "0" => ioProduct(command, msg)
      case "1" => putToBin(command, msg)
    }

    def ioOrder(msg: Message) = for {
      _ <- dao.writeState(msg.chat.id, "3")
      str <- dao.readJson(msg.chat.id)
      json <- IO.fromEither(parse(str))
      array <- IO.fromEither(json.as[Array[Bin]])
      mapO <- IO(array.map(_.unapply).toMap)
      _ <- dao.order(mapO)
      _ <- sendMessage(
        chatId = ChatIntId(msg.chat.id),
        text = "Заказ отправлен"
      ).exec >> deleteMessage(ChatIntId(msg.chat.id), msg.messageId).exec
    } yield ()

    def putToBin(command: String, msg: Message) = for {
      _ <- dao.putInBin(msg.chat.id, command)
    } yield ()

    def ioProduct(command: String, msg: Message) = for {
      list <- dao.getProductsByGroup(command)
      listP <- IO(InlineKeyboardMarkup(list.map(v => InlineKeyboardButton(v.name, callbackData = Some(v.name))).sliding(3, 3).toList
        ::: List(List(InlineKeyboardButton("На главную", callbackData = Some("На главную"))))
        ::: List(List(InlineKeyboardButton("В корзину", callbackData = Some("В корзину"))))))
      _ <- dao.writeState(msg.chat.id, "1")
      _ <- sendMessage(
        chatId = ChatIntId(msg.chat.id),
        text = "Выберите товар",
        replyMarkup = Some(listP)).exec >> deleteMessage(ChatIntId(msg.chat.id), msg.messageId).exec
    } yield ()

    query.data
      .map { x =>
        onMsg(query.message)(m => startMethodByState(x, m))
      }.getOrElse(asyncF.unit)

  }

}