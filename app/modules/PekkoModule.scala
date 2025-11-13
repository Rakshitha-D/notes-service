package modules

import com.google.inject.{AbstractModule, Provides, Singleton}
import org.apache.pekko.actor.ActorSystem
import play.api.inject.ApplicationLifecycle

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class PekkoModule extends AbstractModule {
  override def configure(): Unit = {
    // no explicit bindings here; we use a @Provides method
  }

  @Provides
  @Singleton
  def provideActorSystem(lifecycle: ApplicationLifecycle, ec: ExecutionContext): ActorSystem = {
    // create a Pekko ActorSystem
    val system = ActorSystem("pekko-system")
    // ensure it's terminated when the Play application stops
    lifecycle.addStopHook { () =>
      system.terminate().map(_ => ())(ec)
    }
    system
  }
}