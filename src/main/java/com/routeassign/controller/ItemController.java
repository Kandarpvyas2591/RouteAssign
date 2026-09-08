package com.routeassign.controller;

import com.routeassign.dto.request.ItemRequest;
import com.routeassign.dto.response.ApiResponse;
import com.routeassign.dto.response.ItemResponse;
import com.routeassign.service.ItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    /**
     * POST /api/items
     * Create a new item in the catalogue.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ItemResponse>> create(
            @Valid @RequestBody ItemRequest request) {
        ItemResponse response = itemService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Item created", response));
    }

    /**
     * GET /api/items/{id}
     * Get an item by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ItemResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(itemService.getById(id)));
    }

    /**
     * GET /api/items
     * Get all items. Optionally filter by name via ?name=query.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ItemResponse>>> getAll(
            @RequestParam(required = false) String name) {
        List<ItemResponse> items = (name != null && !name.isBlank())
                ? itemService.searchByName(name)
                : itemService.getAll();
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    /**
     * PUT /api/items/{id}
     * Update an existing item.
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ItemResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody ItemRequest request) {
        return ResponseEntity.ok(ApiResponse.success(itemService.update(id, request)));
    }

    /**
     * DELETE /api/items/{id}
     * Delete an item from the catalogue.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        itemService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Item deleted", null));
    }
}
