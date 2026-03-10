package com.ycf.gateway.config.model;

public class NacosProperties {

    private final String serverAddr;
    private final String namespace;
    private final String username;
    private final String password;
    private final NacosRegisterProperties register;
    private final NacosConfigProperties config;

    public NacosProperties(String serverAddr,
                           String namespace,
                           String username,
                           String password,
                           NacosRegisterProperties register,
                           NacosConfigProperties config) {
        this.serverAddr = serverAddr;
        this.namespace = namespace;
        this.username = username;
        this.password = password;
        this.register = register;
        this.config = config;
    }

    public String getServerAddr() {
        return serverAddr;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public NacosRegisterProperties getRegister() {
        return register;
    }

    public NacosConfigProperties getConfig() {
        return config;
    }

    public boolean hasServerAddress() {
        return serverAddr != null && !serverAddr.isBlank();
    }
}
