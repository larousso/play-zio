import java.io.File

import org.iq80.leveldb.{DB, DBIterator, Options}
import org.iq80.leveldb.impl.Iq80DBFactory.{asString, bytes, factory}
import play.api.MarkerContext
import play.api.libs.json.{JsError, Json}
import play.api.mvc.{Action, ActionBuilder, BodyParser, Result}
import zio.{Has, Layer, Managed, RIO, Runtime, Task, UIO, URIO, ZEnv, ZIO, ZLayer, ZManaged}

import scala.concurrent.Future

package object commons {
  import users._

  type AppContext     = UserRepository with AppLogger
  type HttpContext[A] = ZLayer[ZEnv, Throwable, A]

  object AppContext {
    val live: ZLayer[ZEnv, Throwable, AppContext] = AppLogger.live >>> UserRepository.live.passthrough
  }

  implicit class ActionBuilderOps[+R[_], B](ab: ActionBuilder[R, B]) {

    case class AsyncTaskBuilder[Ctx <: zio.Has[_]](dummy: Boolean = false) {

      def apply(cb: R[B] => RIO[Ctx, Result])(implicit r: HttpContext[Ctx]): Action[B] =
        ab.async { c =>
          val value: ZIO[ZEnv, Throwable, Result] = cb(c).provideLayer(r)
          val future: Future[Result]              = Runtime.default.unsafeRunToFuture(value)
          future
        }

      def apply[A](
          bp: BodyParser[A]
      )(cb: R[A] => RIO[Ctx, Result])(implicit r: HttpContext[Ctx]): Action[A] =
        ab.async[A](bp) { c =>
          val value: ZIO[ZEnv, Throwable, Result] = cb(c).provideLayer(r)
          val future: Future[Result]              = Runtime.default.unsafeRunToFuture(value)
          future
        }
    }

    case class AsyncZioBuilder[Ctx <: zio.Has[_]](dummy: Boolean = false) {

      def apply(cb: R[B] => ZIO[Ctx, Result, Result])(implicit r: HttpContext[Ctx]): Action[B] =
        ab.async { c =>
          val value: ZIO[ZEnv, Throwable, Result] = cb(c).either.map(_.merge).provideLayer(r)
          val future: Future[Result]              = Runtime.default.unsafeRunToFuture(value)
          future
        }

      def apply[A](
          bp: BodyParser[A]
      )(cb: R[A] => ZIO[Ctx, Result, Result])(implicit r: HttpContext[Ctx]): Action[A] =
        ab.async[A](bp) { c =>
          val value: ZIO[ZEnv, Throwable, Result] = cb(c).either.map(_.merge).provideLayer(r)
          val future: Future[Result]              = Runtime.default.unsafeRunToFuture(value)
          future
        }
    }

    def asyncTask[Ctx <: zio.Has[_]] = AsyncTaskBuilder[Ctx]()

    def asyncZio[Ctx <: zio.Has[_]] = AsyncZioBuilder[Ctx]()
  }

  type AppLogger = Has[AppLogger.Service]

  object AppLogger {
    import play.api.Logger

    trait Service {
      def info(message: => String)(implicit mc: MarkerContext): UIO[Unit]
      def debug(message: => String)(implicit mc: MarkerContext): UIO[Unit]
    }

    def info(message: => String)(implicit mc: MarkerContext): URIO[AppLogger, Unit] =
      ZIO.accessM(_.get.info(message))

    def debug(message: => String)(implicit mc: MarkerContext): URIO[AppLogger, Unit] =
      ZIO.accessM(_.get.debug(message))

    val live: ZLayer[Any, Nothing, AppLogger] = ZLayer.succeed(new ProdLogger())

    class ProdLogger(logger: Logger = Logger("application")) extends AppLogger.Service {
      override def info(message: => String)(implicit mc: MarkerContext): UIO[Unit]  = UIO(logger.info(message))
      override def debug(message: => String)(implicit mc: MarkerContext): UIO[Unit] = UIO(logger.debug(message))
    }
  }

}

package object users {
  import commons._

  object User {
    implicit val format = Json.format[User]
  }

  case class User(id: String, name: String)

  type UserRepository = Has[UserRepository.Service]

  object UserRepository {

    trait Service {
      def list(): Task[List[User]]
      def getById(id: String): Task[Option[User]]
      def save(user: User): Task[Unit]
      def delete(id: String): Task[Unit]
    }

    def list(): RIO[UserRepository, List[User]]                = ZIO.accessM(_.get.list())
    def getById(id: String): RIO[UserRepository, Option[User]] = ZIO.accessM(_.get.getById(id))
    def save(user: User): RIO[UserRepository, Unit]            = ZIO.accessM(_.get.save(user))
    def delete(id: String): RIO[UserRepository, Unit]          = ZIO.accessM(_.get.delete(id))

    val live: ZLayer[AppLogger, Throwable, UserRepository] =
      ZLayer.fromFunctionManaged(
        mix =>
          ZManaged
            .make(
              AppLogger.info("Opening level DB at target/leveldb") *>
              Task {
                factory.open(
                  new File("target/leveldb").getAbsoluteFile,
                  new Options().createIfMissing(true)
                )
              }
            )(db => AppLogger.info("Closing level DB at target/leveldb") *> UIO(db.close()))
            .map { db =>
              new LevelDbUserRepository(db)
            }
            .provide(mix)
      )

    class LevelDbUserRepository(db: DB) extends Service {

      def parseJson(str: String): Task[User] =
        Task(Json.parse(str)).flatMap { json =>
          json
            .validate[User]
            .fold(
              err => Task.fail(new RuntimeException(s"Error parsing user: ${Json.stringify(JsError.toJson(err))}")),
              ok => Task.succeed(ok)
            )
        }

      override def list(): Task[List[User]] =
        listAll(db.iterator())

      def listAll(iterator: DBIterator): Task[List[User]] =
        for {
          hasNext <- Task(iterator.hasNext)
          value <- if (hasNext) {
                    for {
                      nextValue <- Task(iterator.next())
                      user      <- parseJson(asString(nextValue.getValue))
                      n         <- listAll(iterator)
                    } yield user :: n
                  } else {
                    Task(List.empty[User])
                  }
        } yield value

      override def getById(id: String): Task[Option[User]] =
        for {
          stringValue <- Task { asString(db.get(bytes(id))) }
          user <- if (stringValue != null) {
                   parseJson(stringValue).map(Option.apply)
                 } else Task.succeed(Option.empty[User])
        } yield user

      override def save(user: User): Task[Unit] =
        Task {
          val stringUser = Json.stringify(Json.toJson(user))
          db.put(bytes(user.id), bytes(stringUser))
        }

      override def delete(id: String): Task[Unit] = Task(db.delete(bytes(id)))
    }
  }

}
