package com.duka.service;

import com.duka.domain.Category;
import com.duka.repo.CategoryRepository;
import com.duka.security.UserPrincipal;
import com.duka.web.ApiException;
import com.duka.web.Dto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryTemplateService {
    private final CategoryRepository categories;
    private final ObjectMapper json;
    private final AuditService audit;

    public JsonNode templateCatalog() {
        return load();
    }

    public JsonNode template(String key) {
        JsonNode node = load().get(key);
        if (node == null) {
            throw new ApiException(404, "Unknown category template");
        }
        return node;
    }

    @Transactional
    public List<Dto.CategoryView> apply(String key, UserPrincipal user) {
        JsonNode template = template(key);
        JsonNode nodes = template.get("nodes");
        if (nodes != null && nodes.isArray()) {
            for (JsonNode node : nodes) {
                Category parent = ensure(user.getBusinessId(), node.path("name").asText(), null);
                JsonNode children = node.get("children");
                if (children != null && children.isArray()) {
                    for (JsonNode child : children) {
                        ensure(user.getBusinessId(), child.asText(), parent.getId());
                    }
                }
            }
        }
        audit.log(user.getBusinessId(), user.getName(), "applied the " + key + " category template", "Existing categories were kept", "CATEGORY", key, "");
        return categories.findByBusinessIdOrderByNameAsc(user.getBusinessId()).stream()
                .map(category -> new Dto.CategoryView(category.getId(), category.getName(), category.getParentId()))
                .toList();
    }

    private Category ensure(Long businessId, String name, Long parentId) {
        if (name == null || name.isBlank()) {
            throw new ApiException(400, "Category template is incomplete");
        }
        if (parentId == null) {
            return categories.findByBusinessIdAndNameIgnoreCaseAndParentIdIsNull(businessId, name).orElseGet(() -> save(businessId, name, null));
        }
        return categories.findByBusinessIdAndParentIdAndNameIgnoreCase(businessId, parentId, name).orElseGet(() -> save(businessId, name, parentId));
    }

    private Category save(Long businessId, String name, Long parentId) {
        Category category = new Category();
        category.setBusinessId(businessId);
        category.setName(name.trim());
        category.setParentId(parentId);
        return categories.save(category);
    }

    private JsonNode load() {
        try {
            return json.readTree(new ClassPathResource("category-templates.json").getInputStream());
        } catch (Exception ex) {
            throw new ApiException(500, "Category templates could not be loaded");
        }
    }
}
