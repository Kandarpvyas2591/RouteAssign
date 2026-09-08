package com.routeassign.service.impl;

import com.routeassign.domain.entity.Item;
import com.routeassign.dto.request.ItemRequest;
import com.routeassign.dto.response.ItemResponse;
import com.routeassign.exception.ResourceNotFoundException;
import com.routeassign.repository.ItemRepository;
import com.routeassign.service.ItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {

    private final ItemRepository itemRepository;

    @Override
    @Transactional
    public ItemResponse create(ItemRequest request) {
        Item item = Item.builder()
                .name(request.getName())
                .price(request.getPrice())
                .weight(request.getWeight())
                .description(request.getDescription())
                .build();
        return toResponse(itemRepository.save(item));
    }

    @Override
    public ItemResponse getById(Long id) {
        return toResponse(findById(id));
    }

    @Override
    public List<ItemResponse> getAll() {
        return itemRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    public List<ItemResponse> searchByName(String name) {
        return itemRepository.findByNameContainingIgnoreCase(name)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public ItemResponse update(Long id, ItemRequest request) {
        Item item = findById(id);
        item.setName(request.getName());
        item.setPrice(request.getPrice());
        item.setWeight(request.getWeight());
        item.setDescription(request.getDescription());
        return toResponse(itemRepository.save(item));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Item item = findById(id);
        itemRepository.delete(item);
        log.info("Deleted item id={}", id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Item findById(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Item", "id", id));
    }

    ItemResponse toResponse(Item i) {
        return ItemResponse.builder()
                .id(i.getId())
                .name(i.getName())
                .price(i.getPrice())
                .weight(i.getWeight())
                .description(i.getDescription())
                .build();
    }
}
