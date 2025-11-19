package utils

import redis.clients.jedis.Jedis
import redis.clients.jedis.exceptions.JedisConnectionException
import org.apache.pekko.actor.ActorSystem
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Failure}

class RedisCache(implicit system: ActorSystem, ec: ExecutionContext) {
  private val jedis = new Jedis("127.0.0.1", 6379)

  def set(key: String, value: String, expirationSeconds: Int = 60): Unit = {
    try {
      jedis.setex(key, expirationSeconds, value)
    } catch {
      case e: JedisConnectionException => 
        println(s"Redis connection error: ${e.getMessage}")
    }
  }

  def get(key: String): Option[String] = {
    try {
      Option(jedis.get(key))
    } catch {
      case e: JedisConnectionException => 
        println(s"Redis connection error: ${e.getMessage}")
        None
    }
  }

  def delete(key: String): Unit = {
    try {
      jedis.del(key)
    } catch {
      case e: JedisConnectionException => 
        println(s"Redis connection error: ${e.getMessage}")
    }
  }

  system.registerOnTermination {
    jedis.close()
  }
}
