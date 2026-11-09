package io.github.cuihairu.civgenesis.spring.observability;

import io.github.cuihairu.civgenesis.spring.observability.CivgenesisObservabilityAutoConfiguration.MetricsImplCondition;
import io.github.cuihairu.civgenesis.spring.observability.CivgenesisObservabilityAutoConfiguration.RegistryCondition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservabilityConditionsTest {
    private static final AnnotatedTypeMetadata METADATA =
            AnnotationMetadata.introspect(CivgenesisObservabilityProperties.class);

    @Test
    void registryConditionMatchesWhenMetricsOrPrometheusEnabled() {
        assertTrue(new RegistryCondition().matches(context("civgenesis.observability.metrics.enabled", "true"), METADATA));
        assertTrue(new RegistryCondition().matches(context("civgenesis.observability.prometheus.enabled", "true"), METADATA));
        assertFalse(new RegistryCondition().matches(context(), METADATA));
    }

    @Test
    void metricsImplConditionMatchesWhenMetricsOrPrometheusEnabled() {
        assertTrue(new MetricsImplCondition().matches(context("civgenesis.observability.prometheus.enabled", "true"), METADATA));
        assertTrue(new MetricsImplCondition().matches(context("civgenesis.observability.metrics.enabled", "true"), METADATA));
        assertFalse(new MetricsImplCondition().matches(context(), METADATA));
    }

    @Test
    void nestedConditionClassesCarryTheirPropertyAnnotations() {
        assertTrue(hasProperty(RegistryCondition.MetricsEnabled.class, "civgenesis.observability.metrics"));
        assertTrue(hasProperty(RegistryCondition.PrometheusEnabled.class, "civgenesis.observability.prometheus"));
        assertTrue(hasProperty(MetricsImplCondition.MetricsEnabled.class, "civgenesis.observability.metrics"));
        assertTrue(hasProperty(MetricsImplCondition.PrometheusEnabled.class, "civgenesis.observability.prometheus"));
    }

    private static boolean hasProperty(Class<?> type, String prefix) {
        org.springframework.boot.autoconfigure.condition.ConditionalOnProperty annotation =
                type.getAnnotation(org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class);
        return annotation != null && prefix.equals(annotation.prefix()) && "true".equals(annotation.havingValue());
    }

    private static ConditionContext context(String... props) {
        StandardEnvironment environment = new StandardEnvironment();
        if (props.length > 0) {
            java.util.Map<String, Object> values = new java.util.HashMap<>();
            for (int i = 0; i < props.length; i += 2) {
                values.put(props[i], props[i + 1]);
            }
            environment.getPropertySources().addFirst(new MapPropertySource("test", values));
        }
        return new ConditionContext() {
            @Override
            public org.springframework.beans.factory.support.BeanDefinitionRegistry getRegistry() {
                return null;
            }

            @Override
            public org.springframework.beans.factory.config.ConfigurableListableBeanFactory getBeanFactory() {
                return null;
            }

            @Override
            public Environment getEnvironment() {
                return environment;
            }

            @Override
            public ResourceLoader getResourceLoader() {
                return null;
            }

            @Override
            public ClassLoader getClassLoader() {
                return environment.getClass().getClassLoader();
            }
        };
    }
}
