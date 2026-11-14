package com.locnguyen.ecommerce.domains.admin.controller;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.exception.GlobalExceptionHandler;
import com.locnguyen.ecommerce.domains.category.dto.CategoryResponse;
import com.locnguyen.ecommerce.domains.category.enums.CategoryStatus;
import com.locnguyen.ecommerce.domains.category.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminCategoryControllerTest {

    @Mock
    private CategoryService categoryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminCategoryController(categoryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getCategoryById_returnsCategory() throws Exception {
        UUID categoryId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(categoryService.getCategoryById(categoryId)).thenReturn(CategoryResponse.builder()
                .id(categoryId)
                .name("Women")
                .slug("women")
                .status(CategoryStatus.INACTIVE)
                .build());

        mockMvc.perform(get("/api/v1/admin/categories/{id}", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(categoryId.toString()))
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    @Test
    void getCategoryById_whenMissing_returnsNotFound() throws Exception {
        UUID categoryId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        when(categoryService.getCategoryById(categoryId))
                .thenThrow(new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        mockMvc.perform(get("/api/v1/admin/categories/{id}", categoryId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }
}
