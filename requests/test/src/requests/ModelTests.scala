package requests

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.{FileSystems, Path}

import utest._

object ModelTests extends TestSuite {
  val tests = Tests {
    test(
      "multipart file uploads should contain application/octet-stream content type"
    ) {
      val path = getClass.getResource("/license.zip").getPath
      val file = new File(path)
      val nioPath = FileSystems.getDefault.getPath(path)
      val fileKey = "fileKey"
      val fileName = "fileName"

      val javaFileMultipart = MultiPart(
        MultiItem(
          fileKey,
          file,
          fileName
        )
      )
      val nioPathMultipart = MultiPart(
        MultiItem(
          fileKey,
          nioPath,
          fileName
        )
      )

      val javaFileOutputStream = new ByteArrayOutputStream()
      val nioPathOutputStream = new ByteArrayOutputStream()

      javaFileMultipart.write(javaFileOutputStream)
      nioPathMultipart.write(nioPathOutputStream)

      val javaFileString = new String(javaFileOutputStream.toByteArray)
      val nioPathString = new String(nioPathOutputStream.toByteArray)

      assert(javaFileString.contains("Content-Type: application/octet-stream"))
      assert(nioPathString.contains("Content-Type: application/octet-stream"))
    }
  }
}
