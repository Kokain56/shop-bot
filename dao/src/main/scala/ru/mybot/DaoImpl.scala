package ru.mybot

import cats.effect._
import com.zaxxer.hikari.HikariConfig
import doobie._
import doobie.hikari._
import doobie.implicits._
import io.circe.generic.auto._
import io.circe.parser.parse
import io.circe.syntax._

object DaoImpl extends Dao{

  val transactor: Resource[IO, HikariTransactor[IO]] =
    for {
      hikariConfig <- Resource.pure(new HikariConfig("/datasource.properties"))
      ce <- ExecutionContexts.fixedThreadPool[IO](5)
      xa <- HikariTransactor.fromHikariConfig[IO](hikariConfig, ce)
    } yield xa

  private def createState(id: Long, state: String): IO[Int] = transactor
    .use(
      sql"""insert into chat_state (id, state, bin) values ($id, $state, '[]')"""
        .update
        .run
        .transact(_)
    )

  private def updateState(id: Long, state: String): IO[Int] = transactor
    .use(
      sql"""update chat_state set state = $state where id = $id"""
        .update
        .run
        .transact(_)
    )

  private def writeJson(id: Long, bin: String): IO[Int] = transactor
    .use(
      sql"""update chat_state set bin = $bin::jsonb where id = $id"""
        .update
        .run
        .transact(_)
    )

  def getProductGroup: IO[List[String]] = transactor
    .use(sql"select name from product_group"
      .query[String]
      .to[List]
      .transact(_))

  def getProductsByGroup(group: String): IO[List[Product]] = transactor
    .use(sql"""select s.name, s.count, s.group_id
           from store s
           join product_group pg on s.group_id = pg.id
           where pg.name = $group """
      .query[Product]
      .to[List]
      .transact(_))

  def isRowState(id: Long): IO[Boolean] = transactor
    .use(
      sql"""select exists(
           select 1
           from chat_state c
           where c.id = $id
           limit 1
          )"""
        .query[Boolean]
        .unique
        .transact(_)
    )

  def writeState(id: Long, state: String): IO[Unit] = for {
    cond <- isRowState(id)
    _ <- if (cond) updateState(id, state) else createState(id, state)
  } yield ()

  def readState(id: Long): IO[String] = transactor
    .use(sql"""select state from chat_state where id = $id"""
      .query[String]
      .unique
      .transact(_))

  def readJson(id: Long): IO[String] = transactor
    .use(sql"""select bin from chat_state where id = $id"""
      .query[String]
      .unique
      .transact(_))

  def putInBin(id_chat: Long, product: String): IO[Unit] = for {
    s <- readJson(id_chat)
    json <- IO.fromEither(parse(s))
    array <- IO.fromEither(json.as[Array[Bin]])
    arrayBin <- IO(array.map(x => (x.name -> x.count)).toMap)
    p <- IO((if (arrayBin.contains(product)) arrayBin.updated(product, arrayBin(product) + 1) else arrayBin ++ Map((product, 1))).map(x => Bin(x._1, x._2)).asJson)
    _ <- writeJson(id_chat, p.noSpaces)
  } yield ()

  def order(mapOrder: Map[String, Int]): IO[Int] = {
    val setN = mapOrder.map(_._1).toSet
    val result = setN.map { x =>
      val v = mapOrder(x)
      s"""update store set count = count - $v  where name = '$x';"""
    }.foldLeft("begin;")((acc, v) => acc + v) + "end;"
    transactor.use(
      Fragment.const(result).update.run.transact(_)
    )
  }
}