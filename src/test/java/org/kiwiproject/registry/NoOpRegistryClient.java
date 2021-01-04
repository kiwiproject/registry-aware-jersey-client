package org.kiwiproject.registry;

import org.kiwiproject.registry.client.RegistryClient;
import org.kiwiproject.registry.model.ServiceInstance;

import java.util.List;
import java.util.Optional;

/**
 * Dummy {@link RegistryClient} for testing.
 */
public class NoOpRegistryClient implements RegistryClient {
    @Override
    public Optional<ServiceInstance> findServiceInstanceBy(String serviceName, String instanceId) {
        return Optional.empty();
    }

    @Override
    public List<ServiceInstance> findAllServiceInstancesBy(InstanceQuery query) {
        return List.of();
    }
}
