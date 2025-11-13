package modules

import com.google.inject.{AbstractModule, Provides, Singleton}
import org.apache.pekko.actor.ActorSystem
import play.api.inject.ApplicationLifecycle

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PekkoModule extends AbstractModule {
  override def configure(): Unit = {
  }

  @Provides
  @Singleton
  def provideActorSystem(lifecycle: ApplicationLifecycle, ec: ExecutionContext): ActorSystem = {
    val system = ActorSystem("pekko-system")
    lifecycle.addStopHook { () =>
      system.terminate().map(_ => ())(ec)
    }
    system
  }
}