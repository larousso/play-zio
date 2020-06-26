import controllers.{AssetsComponents, UserController}
import play.api.ApplicationLoader.Context
import play.api.mvc.EssentialFilter
import router.Routes
import play.api.routing.Router
import play.api.{Application, ApplicationLoader, BuiltInComponentsFromContext, LoggerConfigurator}
import commons.AppContext
import zio.ZManaged.ReleaseMap
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
    import zio.interop.catz._

    implicit val runtime: Runtime[ZEnv] = Runtime.default

    type Eff[A] = ZIO[ZEnv, Throwable, A]

    private val memoize: ZManaged[Any, Nothing, ZLayer[ZEnv, Throwable, AppContext]] = AppContext.live.memoize

    private implicit val (appContext: ZLayer[zio.ZEnv, Throwable, AppContext], release: Eff[Unit]) =
      Runtime.default.unsafeRun(memoize.toResource[Eff].allocated)

    applicationLifecycle.addStopHook(() => Runtime.default.unsafeRunToFuture(release))

    override def router: Router = new Routes(httpErrorHandler, new UserController(controllerComponents), assets)

    override def httpFilters: Seq[EssentialFilter] = Seq()

  }

}
