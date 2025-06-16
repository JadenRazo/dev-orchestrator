package com.devorchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@ConfigurationProperties(prefix = "app")
@Validated
public class AppProperties {

    @NotBlank
    private String name = "Development Environment Orchestrator";

    @NotBlank
    private String version = "1.0.0";

    @NotBlank
    private String description = "Manages and monitors local development environments";

    @Valid
    @NotNull
    private Docker docker = new Docker();

    @Valid
    @NotNull
    private Environment environment = new Environment();

    @Valid
    @NotNull
    private Resources resources = new Resources();

    @Valid
    @NotNull
    private Security security = new Security();

    @Valid
    @NotNull
    private WebSocket websocket = new WebSocket();

    public static class Docker {
        @NotBlank
        private String host = "unix:///var/run/docker.sock";

        private boolean tlsVerify = false;

        private String certPath = "";

        @NotBlank
        private String apiVersion = "1.41";

        @Min(1000)
        @Max(300000)
        private int connectionTimeout = 30000;

        @Min(1000)
        @Max(600000)
        private int readTimeout = 60000;

        @Valid
        @NotNull
        private PortRange portRange = new PortRange();

        public static class PortRange {
            @Min(1024)
            @Max(65535)
            private int start = 8000;

            @Min(1024)
            @Max(65535)
            private int end = 9000;

            public int getStart() { return start; }
            public void setStart(int start) { this.start = start; }
            public int getEnd() { return end; }
            public void setEnd(int end) { this.end = end; }
        }

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public boolean isTlsVerify() { return tlsVerify; }
        public void setTlsVerify(boolean tlsVerify) { this.tlsVerify = tlsVerify; }
        public String getCertPath() { return certPath; }
        public void setCertPath(String certPath) { this.certPath = certPath; }
        public String getApiVersion() { return apiVersion; }
        public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }
        public int getConnectionTimeout() { return connectionTimeout; }
        public void setConnectionTimeout(int connectionTimeout) { this.connectionTimeout = connectionTimeout; }
        public int getReadTimeout() { return readTimeout; }
        public void setReadTimeout(int readTimeout) { this.readTimeout = readTimeout; }
        public PortRange getPortRange() { return portRange; }
        public void setPortRange(PortRange portRange) { this.portRange = portRange; }
    }

    public static class Environment {
        @Min(1)
        @Max(50)
        private int maxEnvironmentsPerUser = 5;

        @Min(30)
        @Max(3600)
        private int defaultTimeout = 300;

        @Min(60)
        @Max(86400)
        private int cleanupInterval = 3600;

        @Min(10)
        @Max(300)
        private int resourceCheckInterval = 30;

        @Min(1)
        @Max(168)
        private int staleEnvironmentHours = 24;

        @Min(1)
        @Max(72)
        private int idleEnvironmentHours = 8;

        public int getMaxEnvironmentsPerUser() { return maxEnvironmentsPerUser; }
        public void setMaxEnvironmentsPerUser(int maxEnvironmentsPerUser) { this.maxEnvironmentsPerUser = maxEnvironmentsPerUser; }
        public int getDefaultTimeout() { return defaultTimeout; }
        public void setDefaultTimeout(int defaultTimeout) { this.defaultTimeout = defaultTimeout; }
        public int getCleanupInterval() { return cleanupInterval; }
        public void setCleanupInterval(int cleanupInterval) { this.cleanupInterval = cleanupInterval; }
        public int getResourceCheckInterval() { return resourceCheckInterval; }
        public void setResourceCheckInterval(int resourceCheckInterval) { this.resourceCheckInterval = resourceCheckInterval; }
        public int getStaleEnvironmentHours() { return staleEnvironmentHours; }
        public void setStaleEnvironmentHours(int staleEnvironmentHours) { this.staleEnvironmentHours = staleEnvironmentHours; }
        public int getIdleEnvironmentHours() { return idleEnvironmentHours; }
        public void setIdleEnvironmentHours(int idleEnvironmentHours) { this.idleEnvironmentHours = idleEnvironmentHours; }
    }

    public static class Resources {
        @Min(50)
        @Max(95)
        private int cpuLimitPercent = 80;

        @Min(50)
        @Max(95)
        private int memoryLimitPercent = 80;

        @Min(50)
        @Max(95)
        private int diskLimitPercent = 85;

        public int getCpuLimitPercent() { return cpuLimitPercent; }
        public void setCpuLimitPercent(int cpuLimitPercent) { this.cpuLimitPercent = cpuLimitPercent; }
        public int getMemoryLimitPercent() { return memoryLimitPercent; }
        public void setMemoryLimitPercent(int memoryLimitPercent) { this.memoryLimitPercent = memoryLimitPercent; }
        public int getDiskLimitPercent() { return diskLimitPercent; }
        public void setDiskLimitPercent(int diskLimitPercent) { this.diskLimitPercent = diskLimitPercent; }
    }

    public static class Security {
        @Valid
        @NotNull
        private Jwt jwt = new Jwt();

        @Valid
        @NotNull
        private Cors cors = new Cors();

        @Valid
        @NotNull
        private RateLimit rateLimit = new RateLimit();

        public static class Jwt {
            @NotBlank
            private String secret;

            @Min(300)
            @Max(86400)
            private int expiration = 3600;

            @Min(3600)
            @Max(604800)
            private int refreshExpiration = 86400;

            public String getSecret() { return secret; }
            public void setSecret(String secret) { this.secret = secret; }
            public int getExpiration() { return expiration; }
            public void setExpiration(int expiration) { this.expiration = expiration; }
            public int getRefreshExpiration() { return refreshExpiration; }
            public void setRefreshExpiration(int refreshExpiration) { this.refreshExpiration = refreshExpiration; }
        }

        public static class Cors {
            private String allowedOrigins = "http://localhost:3000";
            private String allowedMethods = "GET,POST,PUT,DELETE,OPTIONS";
            private String allowedHeaders = "*";
            private boolean allowCredentials = true;

            public String getAllowedOrigins() { return allowedOrigins; }
            public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }
            public String getAllowedMethods() { return allowedMethods; }
            public void setAllowedMethods(String allowedMethods) { this.allowedMethods = allowedMethods; }
            public String getAllowedHeaders() { return allowedHeaders; }
            public void setAllowedHeaders(String allowedHeaders) { this.allowedHeaders = allowedHeaders; }
            public boolean isAllowCredentials() { return allowCredentials; }
            public void setAllowCredentials(boolean allowCredentials) { this.allowCredentials = allowCredentials; }
        }

        public static class RateLimit {
            @Min(1)
            @Max(10000)
            private int requestsPerMinute = 100;

            @Min(1)
            @Max(1000)
            private int requestsPerSecond = 10;

            public int getRequestsPerMinute() { return requestsPerMinute; }
            public void setRequestsPerMinute(int requestsPerMinute) { this.requestsPerMinute = requestsPerMinute; }
            public int getRequestsPerSecond() { return requestsPerSecond; }
            public void setRequestsPerSecond(int requestsPerSecond) { this.requestsPerSecond = requestsPerSecond; }
        }

        public Jwt getJwt() { return jwt; }
        public void setJwt(Jwt jwt) { this.jwt = jwt; }
        public Cors getCors() { return cors; }
        public void setCors(Cors cors) { this.cors = cors; }
        public RateLimit getRateLimit() { return rateLimit; }
        public void setRateLimit(RateLimit rateLimit) { this.rateLimit = rateLimit; }
    }

    public static class WebSocket {
        @Min(1024)
        @Max(65536)
        private int messageBufferSize = 8192;

        @Min(10000)
        @Max(300000)
        private int heartbeatInterval = 30000;

        public int getMessageBufferSize() { return messageBufferSize; }
        public void setMessageBufferSize(int messageBufferSize) { this.messageBufferSize = messageBufferSize; }
        public int getHeartbeatInterval() { return heartbeatInterval; }
        public void setHeartbeatInterval(int heartbeatInterval) { this.heartbeatInterval = heartbeatInterval; }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Docker getDocker() { return docker; }
    public void setDocker(Docker docker) { this.docker = docker; }
    public Environment getEnvironment() { return environment; }
    public void setEnvironment(Environment environment) { this.environment = environment; }
    public Resources getResources() { return resources; }
    public void setResources(Resources resources) { this.resources = resources; }
    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }
    public WebSocket getWebsocket() { return websocket; }
    public void setWebsocket(WebSocket websocket) { this.websocket = websocket; }
}