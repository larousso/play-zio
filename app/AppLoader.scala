import controllers.{AssetsComponents, UserController}
import play.api.ApplicationLoader.Context
import play.api.mvc.EssentialFilter
import router.Routes
import play.api.routing.Router
import play.api.{Application, ApplicationLoader, BuiltInComponentsFromContext, LoggerConfigurator}
import commons.AppContext
import zio._

class AppLoader extends ApplicationLoader {

  def load(context: Context): Application = {
    LoggerConfigurator(context.environment.classLoader).foreach {
      _.configure(context.environment, context.initialConfiguration, Map.empty)
    }
    new modules.AppComponentsInstances(context).application
  }
}

package object modules {

  class AppComponentsInstances(context: Context) extends BuiltInComponentsFromContext(context) with AssetsComponents {
    import users._

    private val reservation: Reservation[Any, Nothing, ZLayer[ZEnv, Throwable, AppContext]] =
      Runtime.default.unsafeRun(AppContext.live.memoize.reserve)

    private implicit val appContext: ZLayer[zio.ZEnv, Throwable, AppContext] =
      Runtime.default.unsafeRun(reservation.acquire)

    applicationLifecycle.addStopHook(() => Runtime.default.unsafeRunToFuture(reservation.release(Exit.unit)))

    override def router: Router = new Routes(httpErrorHandler, new UserController(controllerComponents), assets)

    override def httpFilters: Seq[EssentialFilter] = Seq()

  }

}
