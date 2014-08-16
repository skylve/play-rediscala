# Redisccala Plugin for Play! Framework [![Build Status](https://travis-ci.org/skylve/play-rediscala.png?branch=master)](https://travis-ci.org/skylve/play-rediscala) [![Coverage Status](https://coveralls.io/repos/skylve/play-rediscala/badge.png)](https://coveralls.io/r/skylve/play-rediscala)

This is plugin is designed for Play 2.3.

Use [Rediscala](https://github.com/etaty/rediscala) (1.3.1) - A Redis client for Scala (2.10+) and (AKKA 2.2+) with non-blocking and asynchronous I/O operations.

## Set up the project dependencies

Edit your `build.sbt` or `Build.scala` and add the following :

```scala
"net.skylve" %% "play-rediscala" % "0.1"
```

And register the plugin in conf/play.plugins

```
200:play.modules.redis.RedisPlugin
```

## Configuration

### Simple

```
redis {
  default: "localhost"
}
```

Will connect the default db to the localhost redis instance with the default port (6379)

### Config formats

You can write server informations in different formats.

With every informations :

```
default {
  host: "localhost"
  port: 6379
  password: "fuu"
  db: 1
}
```

Or you can also use an URI and mix with previous config parameters :

```
default {
  uri: "localhost:6379"
  password: "fuu"
  db: 1
}
```

URI accept those formats : `redis://password@host:port` | `password@host:port` | `host:port` | `host` (will use default port (6379))

### Multi DB

Rediscala Plugin can handle multiple databases connections like so :

```
redis {
  default: "localhost"

  cache {
    host: "redis.cache"
    port: 6379
    password: "fuu"
  }
}
```

### Client type

Rediscala offer different client types : 
    [RedisClient](http://etaty.github.io/rediscala/latest/api/index.html#redis.RedisClient)
    [RedisClientPool](http://etaty.github.io/rediscala/latest/api/index.html#redis.RedisClientPool)
    [RedisClientMasterSlaves](http://etaty.github.io/rediscala/latest/api/index.html#redis.RedisClientMasterSlaves)
    [SentinelMonitoredRedisClient](http://etaty.github.io/rediscala/latest/api/index.html#redis.SentinelMonitoredRedisClient)

If you want to configure a db to use a specific client type you can add `type` parameter as so :

```
redis {
  default: "localhost"   # will use the default type "RedisClient"

  pool {
    type: "RedisClientPool"
    servers: ["redis.one", "redis.two"]  # work with server object too
    password: "123" # will be used if a server does not specify one
  }

  ms {
    type: "RedisClientMasterSlaves"
    
    master: {
      host: "localhost"   # could be an URI too
      password: "fuu"
    }

    slaves: ["redis.slave.one", "redis.slave.two"]

    password: "123" # will be used if the master or a slave configuraton does not specify one
  }

  monitored {
    type: "SentinelMonitoredRedisClient"
    
    # only uri accepted due to current limitation with format (host:port) and auth
    master: "localhost:3000"
    sentinels: ["sentinels.redis.one:6379", "sentinels.redis.two:6379"]
  }
}
```

### How to use

If you only need to acces method in trait [RedisCommands](http://etaty.github.io/rediscala/latest/api/index.html#redis.RedisCommands), you can simply :

```scala
import play.api.Play.current

val client = RedisPlugin() // select the default db
client.ping()
```

or if you need specific client type (e.g `RedisClient`, `RedisClientPool`, `RedisClientMasterSlaves`, `SentinelMonitoredRedisClient`), do as so :

```scala
import play.api.Play.current

val simpleClient = RedisPlugin.client("db_name")
val poolClient = RedisPlugin.pool("db_name")
val masterSlavesClient = RedisPlugin.mSlaves("db_name")
val monitoredClient = RedisPlugin.monitored("db_name")
```

If you do not specify a db name, `default` will be used.
