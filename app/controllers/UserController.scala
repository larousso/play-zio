package controllers

import java.util.UUID

import play.api.libs.json.{JsError, JsResult, Json}
import play.api.mvc.{BaseController, ControllerComponents}
import zio.{Task, ZIO}
import commons._
import users._

class UserController(val controllerComponents: ControllerComponents)(implicit c: HttpContext[AppContext])
    extends BaseController {

  def list() = Action.asyncZio[AppContext] { _ =>
    for {
      _     <- AppLogger.debug(s"Listing all users")
      users <- UserRepository.list().mapError(e => InternalServerError(""))
    } yield Ok(Json.toJson(users))
  }

  def getById(id: String) = Action.asyncZio[AppContext] { _ =>
    for {
      _         <- AppLogger.debug(s"Looking for user $id")
      mayBeUser <- UserRepository.getById(id).mapError(e => InternalServerError(""))
      user      <- ZIO.fromOption(mayBeUser).mapError(_ => NotFound(Json.obj("message" -> s"No user for $id")))
    } yield Ok(Json.toJson(user))
  }

  case class UserDto(name: String)
  object UserDto {
    implicit val format = Json.format[UserDto]
  }

  def create() = Action.asyncZio[AppContext](parse.json) { req =>
    val userParsed: JsResult[UserDto] = req.body.validate[UserDto]
    for {
      _    <- AppLogger.debug(s"Creating user")
      user <- ZIO.fromEither(userParsed.asEither).mapError(err => BadRequest(JsError.toJson(err)))
      id   <- Task(UUID.randomUUID().toString).mapError(e => InternalServerError(""))
      _    <- UserRepository.save(User(id, user.name)).mapError(e => InternalServerError(""))
    } yield Ok(Json.toJson(user))
  }

  def update(id: String) = Action.asyncZio[AppContext](parse.json) { req =>
    val userParsed: JsResult[User] = req.body.validate[User]
    for {
      _         <- AppLogger.debug(s"Updating user $id")
      user      <- ZIO.fromEither(userParsed.asEither).mapError(err => BadRequest(JsError.toJson(err)))
      mayBeUser <- UserRepository.getById(id).mapError(_ => InternalServerError(""))
      _         <- ZIO.fromOption(mayBeUser).mapError(_ => BadRequest(Json.obj("message" -> s"User $id should exists")))
      _         <- UserRepository.delete(id).mapError(_ => InternalServerError(""))
      _         <- UserRepository.save(User(id, user.name)).mapError(e => InternalServerError(""))
    } yield Ok(Json.toJson(user))
  }

}
