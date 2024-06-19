package ru.mybot

import cats.effect.IO

trait Dao {

  def order(mapOrder: Map[String, Int]): IO[Int]

  def putInBin(id_chat: Long, product: String): IO[Unit]

  def readJson(id: Long): IO[String]

  def readState(id: Long): IO[String]

  def writeState(id: Long, state: String): IO[Unit]

  def isRowState(id: Long): IO[Boolean]

  def getProductsByGroup(group: String): IO[List[Product]]

  def getProductGroup: IO[List[String]]

}
