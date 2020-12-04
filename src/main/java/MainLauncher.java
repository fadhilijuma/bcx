import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import channel.ChannelManager;
import io.vertx.core.Vertx;
import org.jpos.iso.ISOUtil;
import org.jpos.q2.Q2;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

public class MainLauncher {
    public static final ActorSystem system = ActorSystem.create("httpBcx");
    public static ChannelManager channelManager;

    public static boolean SIGNED_ON = false;
    public static String KEY = "";
    public static boolean ECHO = false;
    static void start() {
        Q2 q2 = new Q2();
        q2.start();
    }

    public static void main(String[] args) {
        start();
        ISOUtil.sleep(10000L);
        (new Thread(new SignOnEchoManager())).start();
        ISOUtil.sleep(10000L);
        ActorRef tickActor = system.actorOf(EchoActor.props(channelManager).withDispatcher("thread-pool-dispatcher"), "echo");
        system.scheduler().schedule(Duration.create(6L, TimeUnit.SECONDS),
                Duration.create(1L, TimeUnit.HOURS), tickActor, "Tack", system
                        .dispatcher(), null);

        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new Deployer());
    }
}

