package io.github.cuihairu.civgenesis.registry.nacos;

import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import io.github.cuihairu.civgenesis.registry.Endpoint;
import io.github.cuihairu.civgenesis.registry.ServiceRegistration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NacosServiceRegistryTest {
    private static NacosServiceRegistry registry(NamingService naming) throws Exception {
        return NacosServiceRegistryTestFactory.create(naming);
    }

    @Test
    void registersDeregistersAndListsMetadata() throws Exception {
        NamingService naming = mock(NamingService.class);
        NacosServiceRegistry registry = registry(naming);
        ServiceRegistration registration = new ServiceRegistration("svc", "GROUP", "127.0.0.1", 1, "id", 9, 5,
                List.of(new Endpoint("tcp://127.0.0.1:1")), Map.of("custom", "value"));
        registry.register(registration);
        verify(naming).registerInstance(eq("svc"), eq("GROUP"), any(Instance.class));

        Instance remote = new Instance();
        remote.setInstanceId("remote");
        remote.setIp("10.0.0.1");
        remote.setPort(2);
        remote.setMetadata(io.github.cuihairu.civgenesis.registry.RegistryMetadata.encode(
                123, 456, List.of(new Endpoint("uds:/tmp/a"))));
        doReturn(List.of(remote)).when(naming).getAllInstances("svc", "GROUP", true);
        List<io.github.cuihairu.civgenesis.registry.ServiceInstance> instances = registry.list("svc");
        assertEquals(1, instances.size());
        assertEquals(123, instances.get(0).instanceIdLong());

        remote.setMetadata(null);
        doReturn(List.of(remote)).when(naming).getAllInstances("svc", "GROUP", true);
        instances = registry.list("svc");
        assertEquals(0, instances.get(0).instanceIdLong());
        assertTrue(instances.get(0).metadata().isEmpty());

        registry.deregister(registration);
        verify(naming).deregisterInstance("svc", "GROUP", "127.0.0.1", 1, "id");
        registry.close();
        verify(naming).shutDown();
        assertNotNull(registry.namingService());
    }

    @Test
    void validatesRegistrationAndShutdownFailureIsLogged() throws Exception {
        NamingService naming = mock(NamingService.class);
        NacosServiceRegistry registry = registry(naming);
        assertThrows(NullPointerException.class, () -> registry.register(null));
        assertThrows(NullPointerException.class, () -> registry.deregister(null));
        doThrow(new com.alibaba.nacos.api.exception.NacosException(-1, "shutdown failed")).when(naming).shutDown();
        registry.close();
    }

    @Test
    void configRequiresCoreValuesAndProvidesDefaults() {
        assertThrows(NullPointerException.class, () -> new NacosRegistryConfig(null, "", "", "", "GROUP"));
        assertThrows(NullPointerException.class, () -> new NacosRegistryConfig("addr", "", "", "", null));
        assertEquals("DEFAULT_GROUP", NacosRegistryConfig.defaults().group());
        assertEquals("127.0.0.1:8848", NacosRegistryConfig.defaults().serverAddr());
        assertEquals("addr", new NacosRegistryConfig("addr", "", null, "", "GROUP").serverAddr());
    }
}
