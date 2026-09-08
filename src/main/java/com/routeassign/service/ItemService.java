package com.routeassign.service;

import com.routeassign.dto.request.ItemRequest;
import com.routeassign.dto.response.ItemResponse;

import java.util.List;

public interface ItemService {

    /**
     * Creates a new item in the catalogue.
     */
    ItemResponse create(ItemRequest request);

    /**
     * Returns an item by its ID.
     */
    ItemResponse getById(Long id);

    /**
     * Returns all items in the catalogue.
     */
    List<ItemResponse> getAll();

    /**
     * Searches items by name (case-insensitive partial match).
     */
    List<ItemResponse> searchByName(String name);

    /**
     * Updates an existing item.
     */
    ItemResponse update(Long id, ItemRequest request);

    /**
     * Deletes an item from the catalogue.
     */
    void delete(Long id);
}
