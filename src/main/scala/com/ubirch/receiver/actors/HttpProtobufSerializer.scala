package com.ubirch.receiver.actors

import akka.serialization.SerializerWithStringManifest

class HttpProtobufSerializer extends SerializerWithStringManifest{

  def identifier: Int = 101110116

  override def manifest(o: AnyRef): String = o.getClass.getName
  final val ResponseDataManifest = classOf[ResponseData].getName


  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {

    println("inside fromBinary"+manifest)

    manifest match {
      case ResponseDataManifest => ResponseData.parseFrom(bytes)
    }
  }

  override def toBinary(o: AnyRef): Array[Byte] = {

    println("inside toBinary ")
    o match {
      case a: ResponseData => a.toByteArray
    }
  }
}