package com.campus.shared.api;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.campus.testsupport.PostgresApplicationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@PostgresApplicationTest
class OpenApiOwnerContractIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlers;

    @Test
    void productionRecordDtosKeepTheirOwnFieldsAndNestedOwnerReferences() throws Exception {
        JsonNode document = mapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Map<String, Class<?>> owners = new HashMap<>();
        Set<String> checked = new HashSet<>();
        for (var entry : handlers.getHandlerMethods().entrySet()) {
            var handler = entry.getValue();
            if (!handler.getBeanType().getPackageName().startsWith("com.campus.")) continue;
            for (String path : entry.getKey().getPatternValues()) {
                // OpenAPI removes Spring's regex constraint from a path parameter name.
                String documentedPath = path.replaceAll("\\{([^}:]+):[^}]+}", "{$1}");
                for (var verb : entry.getKey().getMethodsCondition().getMethods()) {
                    JsonNode operation = document.path("paths").path(documentedPath).path(verb.name().toLowerCase(java.util.Locale.ROOT));
                    assertThat(operation.isMissingNode()).as("Documented %s %s", verb, path).isFalse();
                    for (var parameter : handler.getMethod().getParameters()) {
                        if (parameter.isAnnotationPresent(RequestBody.class)) {
                            verifyRecord(parameter.getParameterizedType(), operation.path("requestBody")
                                    .path("content").path("application/json").path("schema"), document, owners, checked, Map.of());
                        }
                    }
                    Type result = handler.getMethod().getGenericReturnType();
                    if (result instanceof ParameterizedType generic && generic.getRawType() == ResponseEntity.class) {
                        result = generic.getActualTypeArguments()[0];
                    }
                    for (var response : operation.path("responses")) {
                        for (var mediaType : response.path("content")) {
                            JsonNode schema = mediaType.path("schema");
                            if (!schema.isMissingNode()) verifyRecord(result, schema, document, owners, checked, Map.of());
                        }
                    }
                }
            }
        }
        assertThat(owners.size()).as("Production record types covered across modules").isGreaterThan(40);
        assertThat(owners.values().stream().anyMatch(type -> type.getName().contains("AdminOrganizationUnitController$Request"))).isTrue();
        assertThat(owners.values().stream().anyMatch(type -> type.getName().contains("AdminStudentController$Request"))).isTrue();
        assertThat(owners.values().stream().anyMatch(type -> type.getName().contains("AdminFacultyStaffController$Request"))).isTrue();
        System.out.printf("Verified %d distinct production DTO schema references.%n", owners.size());
        Files.writeString(Path.of("target", "production-openapi.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(document));
    }

    private void verifyRecord(Type type, JsonNode schema, JsonNode document,
            Map<String, Class<?>> owners, Set<String> checked, Map<TypeVariable<?>, Type> bindings) {
        if (type instanceof TypeVariable<?> variable) type = bindings.getOrDefault(variable, variable);
        if (type instanceof ParameterizedType generic
                && generic.getRawType() instanceof Class<?> raw && java.util.Collection.class.isAssignableFrom(raw)) {
            verifyRecord(generic.getActualTypeArguments()[0], schema.path("items"), document, owners, checked, bindings);
            return;
        }
        if (type instanceof ParameterizedType generic && generic.getRawType() instanceof Class<?> raw && raw.isRecord()) {
            bindings = new HashMap<>(bindings);
            for (int index = 0; index < raw.getTypeParameters().length; index++) {
                bindings.put(raw.getTypeParameters()[index], generic.getActualTypeArguments()[index]);
            }
            type = raw;
        }
        if (!(type instanceof Class<?> record) || !record.isRecord()) return;
        String reference = schema.path("$ref").asText();
        assertThat(reference).as("Schema reference for %s", record.getName()).startsWith("#/components/schemas/");
        Class<?> prior = owners.putIfAbsent(reference, record);
        assertThat(prior == null || prior == record).as("%s must not share %s with %s", record.getName(), reference, prior).isTrue();
        if (!checked.add(reference)) return;
        JsonNode resolved = document.at(reference.substring(1));
        Set<String> actual = new HashSet<>();
        resolved.path("properties").fieldNames().forEachRemaining(actual::add);
        String[] expected = Arrays.stream(record.getRecordComponents()).map(component -> component.getName()).toArray(String[]::new);
        assertThat(actual).as("Fields of %s", record.getName()).containsExactlyInAnyOrder(expected);
        for (var component : record.getRecordComponents()) {
            verifyRecord(component.getGenericType(), resolved.path("properties").path(component.getName()), document, owners, checked, bindings);
        }
    }
}
