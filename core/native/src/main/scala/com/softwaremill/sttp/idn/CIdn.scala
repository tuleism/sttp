package com.softwaremill.sttp.idn

import scala.scalanative.native._
@link("idn")
@extern
private[idn] object CIdn {

  @name("idna_to_ascii_8z")
  def toAscii(input: CString, output: Ptr[CString], flags: CInt): CInt = extern

  @name("idna_strerror")
  def errorMsg(rc: CInt): CString = extern

  @name("idn_free")
  def free(ptr: Ptr[_]): Unit = extern
}
