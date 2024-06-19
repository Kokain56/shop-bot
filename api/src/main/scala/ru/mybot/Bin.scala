package ru.mybot

case class Bin(name: String, count: Int){
  def unapply = {
    (this.name, this.count)
  }
}
