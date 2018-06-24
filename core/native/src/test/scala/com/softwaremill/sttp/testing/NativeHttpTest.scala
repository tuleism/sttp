package com.softwaremill.sttp.testing

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

import com.softwaremill.sttp.internal.SttpFile
import com.softwaremill.sttp._
import org.scalatest._

import scala.concurrent.duration._
import scala.concurrent.Future
import scala.language.higherKinds

trait NativeHttpTest
    extends FreeSpec
    with Matchers
    with ToFutureWrapper
    with OptionValues
    with BeforeAndAfterAll
    with NativeHttpTestExtensions {

  protected def endpoint: String

  protected val binaryFileMD5Hash = "565370873a38d91f34a3091082e63933"
  protected val textFileMD5Hash = "b048a88ece8e4ec5eb386b8fc5006d13"

  implicit val backend: SttpBackend[Id, Nothing]

  private def postEcho = sttp.post(uri"$endpoint/echo")

  protected val testBody = "this is the body"
  protected val testBodyBytes = testBody.getBytes("UTF-8")
  private val expectedPostEchoResponse = "POST /echo this is the body"

  protected val sttpIgnore = com.softwaremill.sttp.ignore

  "parse response" - {
    "as string" in {
      val response = postEcho.body(testBody).send()
      response.unsafeBody should be(expectedPostEchoResponse)
    }

    "as string with mapping using map" in {
      val response = postEcho
        .body(testBody)
        .response(asString.map(_.length))
        .send()
      response.unsafeBody should be(expectedPostEchoResponse.length)

    }

    "as string with mapping using mapResponse" in {
      val response = postEcho
        .body(testBody)
        .mapResponse(_.length)
        .send()
      response.unsafeBody should be(expectedPostEchoResponse.length)
    }

    "as a byte array" in {
      val response = postEcho
        .body(testBody)
        .response(asByteArray)
        .send()
      val fc = new String(response.unsafeBody, "UTF-8")
      fc should be(expectedPostEchoResponse)

    }

    "as parameters" in {
      val params = List("a" -> "b", "c" -> "d", "e=" -> "&f")
      val response = sttp
        .post(uri"$endpoint/echo/form_params/as_params")
        .body(params: _*)
        .response(asParams)
        .send()
      response.unsafeBody.toList should be(params)

    }
  }

  "parameters" - {
    "make a get request with parameters" in {
      val response = sttp
        .get(uri"$endpoint/echo?p2=v2&p1=v1")
        .send()
      response.unsafeBody should be("GET /echo p1=v1 p2=v2")
    }
  }

  "body" - {
    "post a string" in {
      val response = postEcho
        .body(testBody)
        .send()
      response.unsafeBody should be(expectedPostEchoResponse)

    }

    "post a byte array" in {
      val response = postEcho.body(testBodyBytes).send()
      response.unsafeBody should be(expectedPostEchoResponse)

    }

    "post an input stream" in {
      val response = postEcho
        .body(new ByteArrayInputStream(testBodyBytes))
        .send()
      response.unsafeBody should be(expectedPostEchoResponse)
    }

    "post a byte buffer" in {
      val response = postEcho
        .body(ByteBuffer.wrap(testBodyBytes))
        .send()
      response.unsafeBody should be(expectedPostEchoResponse)
    }

//    "post a file" in {
//      withTemporaryFile(Some(testBodyBytes)) { f =>
//        val response = postEcho.body(f).send()
//        response.unsafeBody should be(expectedPostEchoResponse)
//      }
//    }

    "post form data" in {
      val response = sttp
        .post(uri"$endpoint/echo/form_params/as_string")
        .body("a" -> "b", "c" -> "d")
        .send()
      response.unsafeBody should be("a=b c=d")

    }

    "post form data with special characters" in {
      val response = sttp
        .post(uri"$endpoint/echo/form_params/as_string")
        .body("a=" -> "/b", "c:" -> "/d")
        .send()
      response.unsafeBody should be("a==/b c:=/d")
    }

    "post without a body" in {
      val response = postEcho.send()
      response.unsafeBody should be("POST /echo")

    }
  }

  protected def cacheControlHeaders = Set("no-cache", "max-age=1000")

  "headers" - {
    def getHeaders = sttp.get(uri"$endpoint/set_headers")
    "read response headers" in {
      val response = getHeaders.response(sttpIgnore).send()
      response.headers should have length (4 + cacheControlHeaders.size).toLong
      response.headers("Cache-Control").toSet should be(cacheControlHeaders)
      response.header("Server").exists(_.startsWith("akka-http")) should be(true)
      response.contentType should be(Some("text/plain; charset=UTF-8"))
      response.contentLength should be(Some(2L))

    }
  }

  "errors" - {
    def getHeaders = sttp.post(uri"$endpoint/set_headers")

    "return 405 when method not allowed" in {
      val response = getHeaders.response(sttpIgnore).send()
      response.code should be(405)
      response.isClientError should be(true)
      response.body.isLeft should be(true)

    }
  }

  "auth" - {
    def secureBasic = sttp.get(uri"$endpoint/secure_basic")

    "return a 401 when authorization fails" in {
      val req = secureBasic
      val resp = req.send()
      resp.code should be(401)
      resp.header("WWW-Authenticate") should be(Some("""Basic realm="test realm",charset=UTF-8"""))
    }

    "perform basic authorization" in {
      val req = secureBasic.auth.basic("adam", "1234")
      val resp = req.send()
      resp.code should be(200)
      resp.unsafeBody should be("Hello, adam!")
    }
  }

//  "compression" - {
//    def compress = sttp.get(uri"$endpoint/compress")
//    val decompressedBody = "I'm compressed!"
//
//    "decompress using the default accept encoding header" in {
//      val req = compress
//      val resp = req.send()
//      resp.unsafeBody should be(decompressedBody)
//
//    }
//
//    "decompress using gzip" in {
//      val req = compress.header("Accept-Encoding", "gzip", replaceExisting = true)
//      val resp = req.send()
//      resp.unsafeBody should be(decompressedBody)
//    }
//
//    "decompress using deflate" in {
//      val req = compress.header("Accept-Encoding", "deflate", replaceExisting = true)
//      val resp = req.send()
//      resp.unsafeBody should be(decompressedBody)
//    }
//
//    "work despite providing an unsupported encoding" in {
//      val req = compress.header("Accept-Encoding", "br", replaceExisting = true)
//      val resp = req.send()
//      resp.unsafeBody should be(decompressedBody)
//    }
//  }

  protected def withTemporaryFile[T](content: Option[Array[Byte]])(f: SttpFile => Future[T]): Future[T]

  private def withTemporaryNonExistentFile[T](f: SttpFile => Future[T]): Future[T] = withTemporaryFile(None)(f)

  protected def md5Hash(bytes: Array[Byte]): String

  protected def md5FileHash(file: SttpFile): Future[String]

  /*
  "download file" - {

    "download a binary file using asFile" in {
      withTemporaryNonExistentFile { file =>
        val req = sttp.get(uri"$endpoint/download/binary").response(asSttpFile(file))
        req.send().toFuture().flatMap { resp =>
          md5FileHash(resp.unsafeBody).map { _ shouldBe binaryFileMD5Hash }
        }
      }
    }

    "download a text file using asFile" in {
      withTemporaryNonExistentFile { file =>
        val req = sttp.get(uri"$endpoint/download/text").response(asSttpFile(file))
        req.send().toFuture().flatMap { resp =>
          md5FileHash(resp.unsafeBody).map { _ shouldBe textFileMD5Hash }
        }
      }
    }
  }
   */
  // in JavaScript the only way to set the content type is to use a Blob which defaults the filename to 'blob'
  protected def multipartStringDefaultFileName: Option[String] = None

  private def defaultFileName = multipartStringDefaultFileName match {
    case None       => ""
    case Some(name) => s" ($name)"
  }

  /*
  "multipart" - {
    def mp = sttp.post(uri"$endpoint/multipart")

    "send a multipart message" in {
      val req = mp.multipartBody(multipart("p1", "v1"), multipart("p2", "v2"))
      req.send().toFuture().map { resp =>
        resp.unsafeBody should be(s"p1=v1$defaultFileName, p2=v2$defaultFileName")
      }
    }

    "send a multipart message with filenames" in {
      val req = mp.multipartBody(multipart("p1", "v1").fileName("f1"), multipart("p2", "v2").fileName("f2"))
      req.send().toFuture().map { resp =>
        resp.unsafeBody should be("p1=v1 (f1), p2=v2 (f2)")
      }
    }

    "send a multipart message with a file" in {
      withTemporaryFile(Some(testBodyBytes)) { f =>
        val req = mp.multipartBody(multipartSttpFile("p1", f), multipart("p2", "v2"))
        req.send().toFuture().map { resp =>
          resp.unsafeBody should be(s"p1=$testBody (${f.name}), p2=v2$defaultFileName")
        }
      }
    }
  }
   */

  "redirect" - {
    def r1 = sttp.post(uri"$endpoint/redirect/r1")
    def r2 = sttp.post(uri"$endpoint/redirect/r2")
    val r4response = "819"
    def loop = sttp.post(uri"$endpoint/redirect/loop")

//    "not redirect when redirects shouldn't be followed (temporary)" in {
//      val resp = r1.followRedirects(false).send()
//      resp.code should be(307)
//      resp.body should be('left)
//      resp.history should be('empty)
//    }
//
//    "not redirect when redirects shouldn't be followed (permanent)" in {
//      val resp = r2.followRedirects(false).send()
//      resp.code should be(308)
//      resp.body should be('left)
//      resp.history should be('empty)
//    }

    "redirect when redirects should be followed" in {
      val resp = r2.send()
      resp.code should be(200)
      resp.unsafeBody should be(r4response)

    }

    "redirect twice when redirects should be followed" in {
      val resp = r1.send()
      resp.code should be(200)
      resp.unsafeBody should be(r4response)

    }

    "redirect when redirects should be followed, and the response is parsed" in {
      val resp = r2.response(asString.map(_.toInt)).send()
      resp.code should be(200)
      resp.unsafeBody should be(r4response.toInt)

    }

//    "not redirect when maxRedirects is less than or equal to 0" in {
//      val resp = loop.maxRedirects(-1).send()
//      resp.code should be(302)
//      resp.body should be('left)
//      resp.history should be('empty)
//    }
  }

  "timeout" - {
    "fail if read timeout is not big enough" in {
      val request = sttp
        .get(uri"$endpoint/timeout")
        .readTimeout(200.milliseconds)
        .response(asString)
      request.send()
    }

    "not fail if read timeout is big enough" in {
      val request = sttp
        .get(uri"$endpoint/timeout")
        .readTimeout(5.seconds)
        .response(asString)

      val response = request.send()
      response.unsafeBody should be("Done")
    }
  }

  "empty response" - {
    def postEmptyResponse =
      sttp
        .post(uri"$endpoint/empty_unauthorized_response")
        .body("{}")
        .contentType("application/json")

//    "parse an empty error response as empty string" in {
//      postEmptyResponse.send().body should be(Left(""))
//    }
  }

  override protected def afterAll(): Unit = {
    backend.close()
    super.afterAll()
  }

}
