package com.reservation.redis.config

import org.redisson.config.ClusterServersConfig
import org.redisson.config.MasterSlaveServersConfig
import org.redisson.config.ReplicatedServersConfig
import org.redisson.config.SentinelServersConfig
import org.redisson.config.SingleServerConfig
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("redisson")
data class RedissonProperties(
    val mode: RedissonMode,
    val threads: Int,
    val nettyThreads: Int,
    val replicatedServersConfig: ReplicatedServersConfig? = null,
    val sentinelServersConfig: SentinelServersConfig? = null,
    val singleServerConfig: SingleServerConfig? = null,
    val masterSlaveServersConfig: MasterSlaveServersConfig? = null,
    val clusterServersConfig: ClusterServersConfig? = null,
) {
    enum class RedissonMode {
        REPLICATED,
        SENTINEL,
        SINGLES,
        MASTER_SLAVE,
        CLUSTER,
    }
}
