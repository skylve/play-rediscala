package play.modules.redis

import java.net.URI

import akka.actor.ActorSystem
import play.api._
import redis._
import scala.collection.JavaConversions._
import scala.util.Try

class RedisPlugin(implicit app: Application) extends Plugin {
  lazy val conf = app.configuration.getConfig("redis")
      .getOrElse(throw new PlayException("RedisPlugin Error", "Could not find redis configuration"))

  implicit lazy val system = ActorSystem("play-redis", conf.getConfig("system").map(_.underlying))

  lazy val clients = RedisClients.parseCfg(conf)

  private[this] def retrieveClient[A](db: String, c: Map[String, A]): A = c.getOrElse(db, throw new PlayException("RedisPlugin Error", s"Could not access redis '$db'"))

  def apply(db: String = "default"): RedisCommands = clients(db)

  def client(db: String = "default"): RedisClient = retrieveClient(db, clients.basics)

  def pool(db: String = "default"): RedisClientPool = retrieveClient(db, clients.pools)

  def mSlaves(db: String = "default"): RedisClientMasterSlaves = retrieveClient(db, clients.mSlaves)

  def monitored(db: String = "default"): SentinelMonitoredRedisClient = retrieveClient(db, clients.monitoreds)

  override def onStart() = {
    clients
  }

  override def onStop() = {
    system.shutdown()
  }

  override def enabled =
    !Try(app.configuration.getString("redis")).toOption.flatten.contains("disabled")
}

object RedisPlugin {
  def current(implicit app: Application): RedisPlugin =
    app.plugin[RedisPlugin]
      .getOrElse(throw new PlayException("RedisPlugin Error", "RedisPlugin has not been initialized! Please edit your conf/play.plugins file and add the following line: '200:play.modules.redis.RedisPlugin'."))

  def apply(db: String = "default")(implicit app: Application): RedisCommands = current.apply(db)

  def client(db: String = "default")(implicit app: Application): RedisClient = current.client(db)

  def pool(db: String = "default")(implicit app: Application): RedisClientPool = current.pool(db)

  def mSlaves(db: String = "default")(implicit app: Application): RedisClientMasterSlaves = current.mSlaves(db)

  def monitored(db: String = "default")(implicit app: Application): SentinelMonitoredRedisClient = current.monitored(db)
}

private[redis] case class RedisClients(
  basics: Map[String, RedisClient] = Map.empty,
  pools: Map[String, RedisClientPool] = Map.empty,
  mSlaves: Map[String, RedisClientMasterSlaves] = Map.empty,
  monitoreds: Map[String, SentinelMonitoredRedisClient] = Map.empty
) {
  def apply(key: String): RedisCommands = {
    basics.get(key).orElse(pools.get(key)).orElse(mSlaves.get(key)).orElse(monitoreds.get(key))
      .getOrElse(throw new PlayException("RedisPlugin Error", s"Could not access redis '$key'"))
  }
}

private[redis] object RedisClients {
  val DEFAULT_PORT = 6379

  def parseServerCfg(cfg: Configuration): RedisServer = {
    val uri = cfg.getString("uri").flatMap(x => Try(new URI(x)).toOption)
    val simpleUri = cfg.getString("uri").flatMap(parseSimpleURIOpt)
    val host = uri.flatMap(x => Option(x.getHost)).orElse(simpleUri.map(_._1)).orElse(cfg.getString("host"))
    val port = uri.map(_.getPort).filter(_ > 0).orElse(simpleUri.map(_._2)).orElse(cfg.getInt("port")).getOrElse(DEFAULT_PORT)
    val password = uri.flatMap(x => Option(x.getUserInfo)).flatMap(parseUserInfo).orElse(simpleUri.flatMap(_._3)).orElse(cfg.getString("password"))
    val db = cfg.getInt("db")
    if (host.isEmpty)
      throw new PlayException("RedisPlugin Error", "Missing host")
    RedisServer(host.get, port, password, db)
  }

  def parseServer(server: String): Option[(String, Int)] = server.split(":").toList match {
    case host :: port :: Nil => Some((host, port.toInt))
    case host :: Nil => Some((host, DEFAULT_PORT))
    case _ => None
  }

  def parseUserInfo(userInfo: String): Option[String] = userInfo.split(":").toList match {
    case username :: pwd :: Nil => Some(pwd)
    case pwd :: Nil if !pwd.isEmpty => Some(pwd)
    case _ => None
  }

  def parseSimpleURIOpt(uri: String): Option[(String, Int, Option[String])] = {
    uri.split("@").toList match {
      case userInfo :: server :: Nil => parseServer(server).map(x => (x._1, x._2, parseUserInfo(userInfo)))
      case server :: Nil => parseServer(server).map(x => (x._1, x._2, None))
      case _ => None
    }
  }

  def parseSimpleURI(uri: String) =
    parseSimpleURIOpt(uri).getOrElse(throw new PlayException("RedisPlugin Error", "Wrong uri format (must be 'password@host:port' / 'host:port' / 'host')"))


  def parseClientCfg(cfg: Configuration) = {
    val server = parseServerCfg(cfg)
    val name = cfg.getString("name").getOrElse("RedisClient")
    (server.host, server.port, server.password, server.db, name)
  }

  def parsePoolCfg(cfg: Configuration) = {
    val name = cfg.getString("name").getOrElse("RedisClientPool")
    val globalPwd = cfg.getString("password")
    val servers = Try(cfg.getConfigSeq("servers")).toOption.flatten.map(_.map(parseServerCfg).map(c => c.copy(password = c.password.orElse(globalPwd))))
      .orElse {
        cfg.getStringList("servers").map(_.map(parseSimpleURI).map {
          case (host, port, pwd) => RedisServer(host, port, pwd.orElse(globalPwd), None)
        })
    }.getOrElse(throw new PlayException("RedisPlugin Error", "Missing 'servers' for pool configuration"))

    (servers, name)
  }

  def parseMSlavesCfg(cfg: Configuration) = {
    val globalPwd = cfg.getString("password")
    val master = Try(cfg.getConfig("master")).toOption.flatten.map(parseServerCfg).map(c => c.copy(password = c.password.orElse(globalPwd)))
      .orElse {
      cfg.getString("master").map(parseSimpleURI).map {
        case (host, port, pwd) => RedisServer(host, port, pwd.orElse(globalPwd), None)
      }
    }.getOrElse(throw new PlayException("RedisPlugin Error", "Missing 'master' for master_slaves configuration"))
    val slaves = Try(cfg.getConfigSeq("slaves")).toOption.flatten.map(_.map(parseServerCfg).map(c => c.copy(password = c.password.orElse(globalPwd))))
      .orElse {
      cfg.getStringList("slaves").map(_.map(parseSimpleURI).map {
        case (host, port, pwd) => RedisServer(host, port, pwd.orElse(globalPwd), None)
      })
    }.getOrElse(throw new PlayException("RedisPlugin Error", "Missing 'slaves' for master_slaves configuration"))
    (master, slaves)
  }

  def parseMonitoredCfg(cfg: Configuration) = {
    val sentinels = cfg.getStringList("sentinels").map(_.map(parseSimpleURI))
      .getOrElse(throw new PlayException("RedisPlugin Error", "Missing 'sentinels' for monitored configuration"))
    val master = cfg.getString("master")
      .getOrElse(throw new PlayException("RedisPlugin Error", "Missing 'master' for monitored configuration"))
    (sentinels.toSeq.map(x => (x._1, x._2)), master)
  }

  def parseCfg(cfg: Configuration)(implicit system: ActorSystem): RedisClients = {
    cfg.subKeys.foldLeft(RedisClients()) { (clients, key) =>
      Try(cfg.getConfig(key)).toOption.flatten.map { c =>
        try {
          c.getString("type") match {
            case Some("pool") =>
              val (servers, name) = parsePoolCfg(c)
              clients.copy(pools = clients.pools + (key -> RedisClientPool(servers, name)))
            case Some("master_slaves") =>
              val (master, slaves) = parseMSlavesCfg(c)
              clients.copy(mSlaves = clients.mSlaves + (key -> RedisClientMasterSlaves(master, slaves)))
            case Some("monitored") =>
              val (sentinels, master) = parseMonitoredCfg(c)
              clients.copy(monitoreds = clients.monitoreds + (key -> SentinelMonitoredRedisClient(sentinels, master)))
            case _ =>
              val (host, port, password, db, name) = parseClientCfg(c)
              clients.copy(basics = clients.basics + (key -> RedisClient(host, port, password, db, name)))
          }
        } catch {
          case e: Throwable => throw new PlayException("RedisPlugin Error", s"Error while loading $key configuration", e)
        }
      }.orElse(Try(cfg.getString(key)).toOption.flatten.flatMap { x =>
        val uri = Try(new URI(x)).toOption
        val simpleUri = parseSimpleURIOpt(x)
        val host = uri.flatMap(x => Option(x.getHost)).orElse(simpleUri.map(_._1))
        val port = uri.map(_.getPort).filter(_ > 0).orElse(simpleUri.map(_._2))
        val password = uri.flatMap(x => Option(x.getUserInfo)).flatMap(parseUserInfo).orElse(simpleUri.flatMap(_._3))
        if (host.isEmpty || port.isEmpty) None
        else Some(clients.copy(basics = clients.basics + (key -> RedisClient(host.get, port.get, password))))
      }).getOrElse(clients)
    }
  }
}
