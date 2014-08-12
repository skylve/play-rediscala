package play.modules.redis

import com.typesafe.config.{ConfigFactory}
import org.scalatest._
import play.api.{PlayException, Configuration}
import redis.RedisServer
import play.api.test.Helpers._
import play.api.test.FakeApplication
import scala.concurrent.duration._

import scala.concurrent.Await


class RedisPluginSpec extends FunSpec with Matchers {
  describe("RedisPlugin") {
    describe("parse config") {
      it("parse simple uri format (host:port)") {
        val uri = "redis.host:4242"
        RedisClients.parseSimpleURI(uri) should equal (("redis.host", 4242, None))
      }

      it("parse config object (host, port, password, db)") {
        val conf = Configuration.from(Map("host" -> "redis.host", "port" -> 4242, "password" -> "123", "db" -> 1))
        RedisClients.parseServerCfg(conf) should equal (RedisServer("redis.host", 4242, Some("123"), Some(1)))
      }

      it("parse config object (uri, db) [redis://password@host:port]") {
        val conf = Configuration.from(Map("uri" -> "redis://123@redis.host:4242", "db" -> 1))
        RedisClients.parseServerCfg(conf) should equal (RedisServer("redis.host", 4242, Some("123"), Some(1)))
      }

      it("parse config object (uri, db) [redis://username:password@host:port]") {
        val conf = Configuration.from(Map("uri" -> "redis://user:123@redis.host:4242", "db" -> 1))
        RedisClients.parseServerCfg(conf) should equal (RedisServer("redis.host", 4242, Some("123"), Some(1)))
      }

      it("parse config object (uri, db) [password@host:port]") {
        val conf = Configuration.from(Map("uri" -> "123@redis.host:4242", "db" -> 1))
        RedisClients.parseServerCfg(conf) should equal (RedisServer("redis.host", 4242, Some("123"), Some(1)))
      }

      it("parse config object (uri, db) [username:password@host:port]") {
        val conf = Configuration.from(Map("uri" -> "user:123@redis.host:4242", "db" -> 1))
        RedisClients.parseServerCfg(conf) should equal (RedisServer("redis.host", 4242, Some("123"), Some(1)))
      }

      it("parse config object with simple uri (uri) [host:port]") {
        val conf = Configuration.from(Map("uri" -> "redis.host:4242"))
        RedisClients.parseServerCfg(conf) should equal (RedisServer("redis.host", 4242, None, None))
      }

      it("parse config object (uri, password, db) [host:port]") {
        val conf = Configuration.from(Map("uri" -> "redis.host:4242", "password" -> "123", "db" -> 1))
        RedisClients.parseServerCfg(conf) should equal (RedisServer("redis.host", 4242, Some("123"), Some(1)))
      }
    }

    describe("load config") {
      it("load simple client config") {
        val cfg = Configuration(ConfigFactory.parseString(
          """
            | host: "redis.host"
            | port: 4242
            | password: "123"
            | db: 1
          """.stripMargin))
        RedisClients.parseClientCfg(cfg) should equal ("redis.host", 4242, Some("123"), Some(1), "RedisClient")
      }

      it("load pool client config with urls ['password@host:port']") {
        val cfg = Configuration(ConfigFactory.parseString(
          """
            | servers: ["123@redis.host:4242", "456@redis.host.sec:4242"]
          """.stripMargin))
        RedisClients.parsePoolCfg(cfg) should equal ((
          Seq(RedisServer("redis.host", 4242, Some("123")), RedisServer("redis.host.sec", 4242, Some("456"))),
          "RedisClientPool"
        ))
      }

      it("load pool client config with urls ['host:port'] and default password") {
        val cfg = Configuration(ConfigFactory.parseString(
          """
            | servers: ["redis.host:4242", "redis.host.sec:4242"]
            | password: "123"
          """.stripMargin))
        RedisClients.parsePoolCfg(cfg) should equal ((
          Seq(RedisServer("redis.host", 4242, Some("123")), RedisServer("redis.host.sec", 4242, Some("123"))),
          "RedisClientPool"
        ))
      }

      it("load pool client config with objects (uri [, host, port], db") {
        val cfg = Configuration(ConfigFactory.parseString(
          """
            | servers: [{
            |   uri: "123@redis.host:4242"
            |   db: 1
            | }, {
            |   host: "redis.host.sec"
            |   port: 4242
            |   db: 2
            | }]
            | password: "456"
          """.stripMargin))
        RedisClients.parsePoolCfg(cfg) should equal ((
          Seq(RedisServer("redis.host", 4242, Some("123"), Some(1)), RedisServer("redis.host.sec", 4242, Some("456"), Some(2))),
          "RedisClientPool"
        ))
      }

      it("load master_slaves config with urls ['password@host:port']") {
        val cfg = Configuration(ConfigFactory.parseString(
          """
            | master: "123@redis.host.master:4242"
            | slaves: ["123@redis.host:4242", "456@redis.host.sec:4242"]
          """.stripMargin))
        RedisClients.parseMSlavesCfg(cfg) should equal ((
          RedisServer("redis.host.master", 4242, Some("123")),
          Seq(RedisServer("redis.host", 4242, Some("123")), RedisServer("redis.host.sec", 4242, Some("456")))
          ))
      }

      it("load master_slaves config with urls ['password@host:port'] with default password") {
        val cfg = Configuration(ConfigFactory.parseString(
          """
            | master: "123@redis.host.master:4242"
            | slaves: ["redis.host:4242", "redis.host.sec:4242"]
            | password: "456"
          """.stripMargin))
        RedisClients.parseMSlavesCfg(cfg) should equal ((
          RedisServer("redis.host.master", 4242, Some("123")),
          Seq(RedisServer("redis.host", 4242, Some("456")), RedisServer("redis.host.sec", 4242, Some("456")))
          ))
      }

      it("load master_slaves config with objects (uri [, host, port], db") {
        val cfg = Configuration(ConfigFactory.parseString(
          """
            | master: {
            |   host: "redis.host.master"
            |   port: 4242
            |   password: "123"
            |   db: 1
            | }
            | slaves: [{
            |   uri: "123@redis.host:4242"
            |   db: 1
            | }, {
            |   host: "redis.host.sec"
            |   port: 4242
            |   db: 2
            | }]
            | password: "456"
          """.stripMargin))
        RedisClients.parseMSlavesCfg(cfg) should equal ((
          RedisServer("redis.host.master", 4242, Some("123"), Some(1)),
          Seq(RedisServer("redis.host", 4242, Some("123"), Some(1)), RedisServer("redis.host.sec", 4242, Some("456"), Some(2)))
          ))
      }

      it("load monitored client config") {
        val cfg = Configuration(ConfigFactory.parseString(
          """
            | master: "redis.host.master:4242"
            | sentinels: ["redis.sentinel.one:4242", "redis.sentinel.two:4242"]
          """.stripMargin))
        RedisClients.parseMonitoredCfg(cfg) should equal ((
          Seq(("redis.sentinel.one", 4242), ("redis.sentinel.two", 4242)),
          "redis.host.master:4242"
          ))
      }
    }

    describe("load plugin") {
      it("throw an exception if the plugin is not registered") {
        an [PlayException] should be thrownBy running(FakeApplication()) {
          import play.api.Play.current
          val client = RedisPlugin("default")
        }
      }

      it("fail to load the plugin without a proper configuration") {
        an [PlayException] should be thrownBy running(FakeApplication(additionalPlugins = Seq("play.modules.redis.RedisPlugin"))) {
          fail
        }
      }

      it("load the plugin with simple default configuration") {
        running(FakeApplication(
          additionalPlugins = Seq("play.modules.redis.RedisPlugin"),
          additionalConfiguration = Map("redis.default" -> "localhost"))) {
          import play.api.Play.current
          val client = RedisPlugin.client()
          client.stop()
          (client.host, client.port) should equal (("localhost", 6379))
        }
      }

      it("load the plugin with simple multi db configuration") {
        running(FakeApplication(
          additionalPlugins = Seq("play.modules.redis.RedisPlugin"),
          additionalConfiguration = Map("redis.default" -> "localhost", "redis.cache.host" -> "redis.cache", "redis.cache.port" -> 4242))) {
          import play.api.Play.current
          val client = RedisPlugin.client()
          client.stop()
          (client.host, client.port) should equal (("localhost", 6379))
          val cache = RedisPlugin.client("cache")
          cache.stop()
          (cache.host, cache.port) should equal (("redis.cache", 4242))
        }
      }

      it("correctly connect to redis") {
        running(FakeApplication(
          additionalPlugins = Seq("play.modules.redis.RedisPlugin"),
          additionalConfiguration = Map("redis.default" -> "localhost"))) {
          import play.api.Play.current
          val client = RedisPlugin.client()
          val pong = Await.result(client.ping(), 100 millis)
          assert(pong == "PONG")
        }
      }
    }
  }
}
