package com.example.microservicios.persistence.routing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "routing")
public class RoutingProperties {

    private boolean enabled = true;
    private Nodes nodes = new Nodes();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Nodes getNodes() {
        return nodes;
    }

    public void setNodes(Nodes nodes) {
        this.nodes = nodes;
    }

    public static class Nodes {
        private NodeProperties primary = new NodeProperties();
        private NodeProperties replica1 = new NodeProperties();
        private NodeProperties replica2 = new NodeProperties();

        public NodeProperties getPrimary() {
            return primary;
        }

        public void setPrimary(NodeProperties primary) {
            this.primary = primary;
        }

        public NodeProperties getReplica1() {
            return replica1;
        }

        public void setReplica1(NodeProperties replica1) {
            this.replica1 = replica1;
        }

        public NodeProperties getReplica2() {
            return replica2;
        }

        public void setReplica2(NodeProperties replica2) {
            this.replica2 = replica2;
        }
    }

    public static class NodeProperties {
        private String jdbcUrl;
        private String username;
        private String password;
        private int poolSize = 10;

        public String getJdbcUrl() {
            return jdbcUrl;
        }

        public void setJdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getPoolSize() {
            return poolSize;
        }

        public void setPoolSize(int poolSize) {
            this.poolSize = poolSize;
        }
    }
}
