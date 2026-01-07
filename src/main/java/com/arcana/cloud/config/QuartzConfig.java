package com.arcana.cloud.config;

import lombok.extern.slf4j.Slf4j;
import org.quartz.spi.JobFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

import javax.sql.DataSource;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

/**
 * Quartz Scheduler Configuration.
 * Supports distributed job scheduling across all deployment modes:
 * - Monolithic: Single instance with JDBC JobStore for persistence
 * - Layered: Multiple instances sharing the same database for clustering
 * - Microservices (K8s): Distributed pods with JDBC clustering and instance awareness
 */
@Configuration
@Slf4j
@ConditionalOnProperty(name = "quartz.enabled", havingValue = "true", matchIfMissing = false)
public class QuartzConfig {

    @Value("${deployment.mode:monolithic}")
    private String deploymentMode;

    @Value("${quartz.scheduler.instance-name:arcanaScheduler}")
    private String schedulerName;

    @Value("${quartz.scheduler.instance-id:AUTO}")
    private String instanceId;

    @Value("${quartz.cluster.enabled:true}")
    private boolean clusterEnabled;

    @Value("${quartz.cluster.checkin-interval:15000}")
    private long clusterCheckinInterval;

    @Value("${quartz.thread-pool.size:10}")
    private int threadPoolSize;

    @Value("${quartz.job-store.misfire-threshold:60000}")
    private long misfireThreshold;

    @Value("${HOSTNAME:#{null}}")
    private String hostname;

    @Value("${POD_NAME:#{null}}")
    private String podName;

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * Custom JobFactory that enables Spring dependency injection in Quartz jobs.
     */
    @Bean
    public JobFactory jobFactory() {
        AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        return jobFactory;
    }

    /**
     * Main Scheduler Factory Bean configuration when Flyway is enabled.
     * Injects Flyway bean as parameter to force Spring to create and run migrations first.
     * Uses @DependsOn as additional guarantee for initialization order.
     */
    @Bean
    @DependsOn("flyway")
    @ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "true")
    public SchedulerFactoryBean schedulerFactoryBean(DataSource dataSource, JobFactory jobFactory,
                                                     @SuppressWarnings("unused") Flyway flyway) {
        log.info("Creating Quartz SchedulerFactoryBean with Flyway dependency (migrations guaranteed complete)");
        return createSchedulerFactoryBean(dataSource, jobFactory);
    }

    /**
     * Fallback Scheduler Factory Bean configuration when Flyway is not enabled.
     * Assumes Quartz tables already exist in the database.
     */
    @Bean
    @ConditionalOnMissingBean(SchedulerFactoryBean.class)
    public SchedulerFactoryBean schedulerFactoryBeanWithoutFlyway(DataSource dataSource, JobFactory jobFactory) {
        log.info("Creating Quartz SchedulerFactoryBean without Flyway (assumes tables exist)");
        return createSchedulerFactoryBean(dataSource, jobFactory);
    }

    /**
     * Common SchedulerFactoryBean creation logic.
     * Configures Quartz for JDBC-based clustering to support distributed scheduling.
     */
    private SchedulerFactoryBean createSchedulerFactoryBean(DataSource dataSource, JobFactory jobFactory) {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setJobFactory(jobFactory);
        factory.setSchedulerName(schedulerName);
        factory.setQuartzProperties(quartzProperties());
        factory.setOverwriteExistingJobs(true);
        factory.setWaitForJobsToCompleteOnShutdown(true);
        factory.setAutoStartup(true);

        log.info("Configuring Quartz Scheduler for deployment mode: {}", deploymentMode);
        log.info("Cluster enabled: {}, Instance ID: {}", clusterEnabled, getInstanceId());

        return factory;
    }

    /**
     * Quartz properties configuration.
     * Adapts settings based on deployment mode.
     * Note: Job store class is NOT set here because SchedulerFactoryBean.setDataSource()
     * handles this automatically. Setting it manually causes "DataSource name not set" error.
     */
    private Properties quartzProperties() {
        Properties properties = new Properties();

        // Scheduler identification
        properties.setProperty("org.quartz.scheduler.instanceName", schedulerName);
        properties.setProperty("org.quartz.scheduler.instanceId", getInstanceId());

        // Thread pool configuration
        properties.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        properties.setProperty("org.quartz.threadPool.threadCount", String.valueOf(getThreadPoolSize()));
        properties.setProperty("org.quartz.threadPool.threadPriority", "5");

        // Job store configuration - table prefix and driver delegate
        // Note: Don't set org.quartz.jobStore.class - Spring's SchedulerFactoryBean handles this
        properties.setProperty("org.quartz.jobStore.driverDelegateClass", getDriverDelegateClass());
        properties.setProperty("org.quartz.jobStore.useProperties", "false");
        properties.setProperty("org.quartz.jobStore.tablePrefix", "QRTZ_");
        properties.setProperty("org.quartz.jobStore.misfireThreshold", String.valueOf(misfireThreshold));

        // Clustering configuration
        if (clusterEnabled) {
            properties.setProperty("org.quartz.jobStore.isClustered", "true");
            properties.setProperty("org.quartz.jobStore.clusterCheckinInterval", String.valueOf(clusterCheckinInterval));
            properties.setProperty("org.quartz.jobStore.acquireTriggersWithinLock", "true");
        } else {
            properties.setProperty("org.quartz.jobStore.isClustered", "false");
        }

        // Skip update check
        properties.setProperty("org.quartz.scheduler.skipUpdateCheck", "true");

        // Batch trigger acquisition for better performance in clustered mode
        if (clusterEnabled) {
            properties.setProperty("org.quartz.scheduler.batchTriggerAcquisitionMaxCount", "10");
            properties.setProperty("org.quartz.scheduler.batchTriggerAcquisitionFireAheadTimeWindow", "0");
        }

        return properties;
    }

    /**
     * Determines the instance ID based on deployment mode.
     * - Monolithic: AUTO (Quartz generates unique ID)
     * - Layered: Hostname-based
     * - K8s: Pod name or hostname
     */
    private String getInstanceId() {
        if ("AUTO".equalsIgnoreCase(instanceId)) {
            return switch (deploymentMode.toLowerCase()) {
                case "microservices", "k8s" -> {
                    // Use pod name if available (K8s environment)
                    if (podName != null && !podName.isEmpty()) {
                        yield podName;
                    }
                    yield getHostname();
                }
                case "layered" -> getHostname() + "-" + System.currentTimeMillis();
                default -> "AUTO"; // Monolithic - let Quartz auto-generate
            };
        }
        return instanceId;
    }

    /**
     * Gets the hostname for instance identification.
     */
    private String getHostname() {
        if (hostname != null && !hostname.isEmpty()) {
            return hostname;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.warn("Could not determine hostname, using 'unknown'", e);
            return "unknown-" + System.currentTimeMillis();
        }
    }

    /**
     * Determines thread pool size based on deployment mode.
     */
    private int getThreadPoolSize() {
        return switch (deploymentMode.toLowerCase()) {
            case "microservices", "k8s" -> Math.max(5, threadPoolSize / 2); // Smaller pool for distributed
            case "layered" -> threadPoolSize;
            default -> threadPoolSize; // Monolithic - full pool
        };
    }

    /**
     * Gets the appropriate JDBC driver delegate class based on database type.
     */
    private String getDriverDelegateClass() {
        // Default to standard JDBC delegate, can be overridden via properties
        return "org.quartz.impl.jdbcjobstore.StdJDBCDelegate";
    }

    /**
     * Custom SpringBeanJobFactory that enables autowiring in Quartz jobs.
     */
    public static class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory {
        private ApplicationContext applicationContext;

        public void setApplicationContext(ApplicationContext applicationContext) {
            this.applicationContext = applicationContext;
        }

        @Override
        protected Object createJobInstance(org.quartz.spi.TriggerFiredBundle bundle) throws Exception {
            Object job = super.createJobInstance(bundle);
            applicationContext.getAutowireCapableBeanFactory().autowireBean(job);
            return job;
        }
    }
}
