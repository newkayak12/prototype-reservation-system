package com.reservation.redis.config

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.codec.JsonJacksonCodec
import org.redisson.config.ClusterServersConfig
import org.redisson.config.Config
import org.redisson.config.MasterSlaveServersConfig
import org.redisson.config.ReplicatedServersConfig
import org.redisson.config.SentinelServersConfig
import org.redisson.config.SingleServerConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class RedissonConfig(
    val properties: RedissonProperties,
) {
    @Bean
    fun redissonClient(): RedissonClient {
        val config = config()
        return Redisson.create(config)
    }

    private fun config() =
        Config().apply {
            setUp(this, properties)

            threads = properties.threads
            nettyThreads = properties.nettyThreads
            codec = JsonJacksonCodec()
        }

    private fun setUp(
        config: Config,
        properties: RedissonProperties,
    ) {
        when (properties.mode) {
            RedissonProperties.RedissonMode.REPLICATED -> {
                config.setReplicated(properties.replicatedServersConfig!!)
            }

            RedissonProperties.RedissonMode.SENTINEL -> {
                config.setupSentinel(properties.sentinelServersConfig!!)
            }

            RedissonProperties.RedissonMode.SINGLES -> {
                config.setupSingle(properties.singleServerConfig!!)
            }

            RedissonProperties.RedissonMode.MASTER_SLAVE -> {
                config.setupMasterSlave(properties.masterSlaveServersConfig!!)
            }

            RedissonProperties.RedissonMode.CLUSTER -> {
                config.setupCluster(properties.clusterServersConfig!!)
            }
        }
    }

    private fun Config.setupCluster(properties: ClusterServersConfig) =
        useClusterServers()
            .setNatMapper(properties.natMapper)
            .addNodeAddress(*properties.nodeAddresses.toTypedArray())
            .setScanInterval(properties.scanInterval)
            .setCheckSlotsCoverage(properties.isCheckSlotsCoverage)
            .setCheckMasterLinkStatus(properties.isCheckSlotsCoverage)
            .setShardedSubscriptionMode(properties.shardedSubscriptionMode)

    private fun Config.setupMasterSlave(properties: MasterSlaveServersConfig) =
        useMasterSlaveServers()
            .addSlaveAddress(*properties.slaveAddresses.toTypedArray())
            .setMasterAddress(properties.masterAddress)
            .setDatabase(properties.database)

    private fun Config.setReplicated(properties: ReplicatedServersConfig) =
        useReplicatedServers()
            .addNodeAddress(*properties.nodeAddresses.toTypedArray())
            .setScanInterval(properties.scanInterval)
            .setDatabase(properties.database)
            .setMonitorIPChanges(properties.isMonitorIPChanges)

    private fun Config.setupSentinel(properties: SentinelServersConfig) =
        useSentinelServers()
            .setMasterName(properties.masterName)
            .setSentinelUsername(properties.sentinelUsername)
            .setSentinelPassword(properties.sentinelPassword)
            .setDatabase(properties.database)
            .setScanInterval(properties.scanInterval)
            .setCheckSentinelsList(properties.isCheckSentinelsList)
            .setCheckSlaveStatusWithSyncing(properties.isCheckSlaveStatusWithSyncing)
            .setSentinelsDiscovery(properties.isSentinelsDiscovery)
            .setNatMapper(properties.natMapper)
            .addSentinelAddress(*properties.sentinelAddresses.toTypedArray())

    private fun Config.setupSingle(properties: SingleServerConfig) =
        useSingleServer()
            .setAddress(properties.address)
            .setConnectionPoolSize(properties.connectionPoolSize)
            .setConnectionMinimumIdleSize(properties.connectionMinimumIdleSize)
            .setIdleConnectionTimeout(properties.idleConnectionTimeout)
            .setTimeout(properties.timeout)
            .setRetryAttempts(properties.retryAttempts)
            .setRetryInterval(properties.retryInterval)
}
