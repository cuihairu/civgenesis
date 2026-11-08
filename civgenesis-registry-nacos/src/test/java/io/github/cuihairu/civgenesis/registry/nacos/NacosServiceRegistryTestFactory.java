package io.github.cuihairu.civgenesis.registry.nacos;

import com.alibaba.nacos.api.naming.NamingService;

import java.lang.reflect.Constructor;

final class NacosServiceRegistryTestFactory {
    private NacosServiceRegistryTestFactory() {}

    static NacosServiceRegistry create(NamingService namingService) throws Exception {
        return create(namingService, new NacosRegistryConfig("127.0.0.1:8848", "", "", "", "GROUP"));
    }

    static NacosServiceRegistry create(NamingService namingService, NacosRegistryConfig config) throws Exception {
        Constructor<NacosServiceRegistry> constructor = NacosServiceRegistry.class.getDeclaredConstructor(
                NacosRegistryConfig.class, NamingService.class);
        constructor.setAccessible(true);
        return constructor.newInstance(config, namingService);
    }
}
