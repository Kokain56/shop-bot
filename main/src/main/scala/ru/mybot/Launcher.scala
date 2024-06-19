package ru.mybot

import cats.effect.{ExitCode, IO, IOApp}
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client
import org.http4s.client.middleware.Logger
import telegramium.bots.high.{Api, BotApi}

object Launcher extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    BlazeClientBuilder[IO].resource.use {
      httpClient =>
        val http = Logger(logBody = false, logHeaders = false)(httpClient)
        implicit val api: Api[IO] = createBotBackend(http, botToken)
        implicit val dao = DaoImpl
        val echoBot = new ShopBot()
        echoBot.start().as(ExitCode.Success)
    }
  }

  private def createBotBackend(http: Client[IO], token: String) =
    BotApi(http, baseUrl = s"https://api.telegram.org/bot$token")
}
