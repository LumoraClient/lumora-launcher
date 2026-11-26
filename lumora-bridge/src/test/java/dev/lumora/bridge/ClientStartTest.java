package dev.lumora.bridge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientStartTest {
    @Test
    void entrypointsAndVersionAreReadFromTheLoaderDescriptor() {
        String json = "{\n  \"schema\": 1,\n  \"id\": \"lumora\",\n  \"minecraft\": \"1.21.11\",\n"
                + "  \"entrypoints\": [\"dev.lumora.client.LumoraClient\", \"a.b.C$D\"],\n  \"mixins\": [\"lumora.mixins.json\"]\n}";
        assertEquals(List.of("dev.lumora.client.LumoraClient", "a.b.C$D"), ClientStart.entrypoints(json));
        assertEquals("1.21.11", ClientStart.minecraft(json));
        assertEquals(List.of(), ClientStart.entrypoints("{\"entrypoints\": []}"));
        assertEquals("", ClientStart.minecraft("{}"));
        assertThrows(IllegalStateException.class, () -> ClientStart.entrypoints("{\"id\":\"x\"}"));
    }
}
