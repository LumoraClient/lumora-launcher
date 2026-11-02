package dev.lumora.loader.impl;

import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Mixin's global "blackboard": a plain map in this process. */
public final class LumoraGlobalPropertyService implements IGlobalPropertyService {
    private record Key(String name) implements IPropertyKey {
    }

    private final Map<String, Object> values = new ConcurrentHashMap<>();

    @Override
    public IPropertyKey resolveKey(String name) {
        return new Key(name);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(IPropertyKey key) {
        return (T) values.get(((Key) key).name());
    }

    @Override
    public void setProperty(IPropertyKey key, Object value) {
        if (value == null) {
            values.remove(((Key) key).name());
        } else {
            values.put(((Key) key).name(), value);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProperty(IPropertyKey key, T defaultValue) {
        Object v = values.get(((Key) key).name());
        return v != null ? (T) v : defaultValue;
    }

    @Override
    public String getPropertyString(IPropertyKey key, String defaultValue) {
        Object v = values.get(((Key) key).name());
        return v != null ? v.toString() : defaultValue;
    }
}
