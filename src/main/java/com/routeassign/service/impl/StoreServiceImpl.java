package com.routeassign.service.impl;

import com.routeassign.domain.entity.Item;
import com.routeassign.domain.entity.Store;
import com.routeassign.domain.entity.VendorDetails;
import com.routeassign.dto.request.StoreRequest;
import com.routeassign.dto.response.ItemResponse;
import com.routeassign.dto.response.StoreResponse;
import com.routeassign.exception.ResourceNotFoundException;
import com.routeassign.repository.ItemRepository;
import com.routeassign.repository.StoreRepository;
import com.routeassign.repository.VendorDetailsRepository;
import com.routeassign.service.StoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreServiceImpl implements StoreService {

    private final StoreRepository         storeRepository;
    private final VendorDetailsRepository vendorDetailsRepository;
    private final ItemRepository          itemRepository;

    @Override
    @Transactional
    public StoreResponse addOrUpdateStock(Long vendorId, StoreRequest request) {
        VendorDetails vendor = findVendor(vendorId);
        Item          item   = findItem(request.getItemId());

        Optional<Store> existing = storeRepository.findByVendorAndItem(vendor, item);

        Store store;
        if (existing.isPresent()) {
            // Update quantity
            store = existing.get();
            store.setQuantity(request.getQuantity());
            log.info("Updated stock for vendorId={} itemId={} qty={}", vendorId, item.getId(), request.getQuantity());
        } else {
            // Create new entry
            store = Store.builder()
                    .vendor(vendor)
                    .item(item)
                    .quantity(request.getQuantity())
                    .build();
            log.info("Added new stock for vendorId={} itemId={} qty={}", vendorId, item.getId(), request.getQuantity());
        }

        return toResponse(storeRepository.save(store));
    }

    @Override
    public StoreResponse getById(Long id) {
        Store store = storeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Store", "id", id));
        return toResponse(store);
    }

    @Override
    public List<StoreResponse> getByVendorId(Long vendorId) {
        return storeRepository.findAllByVendor_Id(vendorId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void removeStock(Long vendorId, Long itemId) {
        VendorDetails vendor = findVendor(vendorId);
        Item          item   = findItem(itemId);
        Store store = storeRepository.findByVendorAndItem(vendor, item)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Store entry", "vendorId+itemId", vendorId + "+" + itemId));
        storeRepository.delete(store);
        log.info("Removed stock entry for vendorId={} itemId={}", vendorId, itemId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private VendorDetails findVendor(Long vendorId) {
        return vendorDetailsRepository.findById(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("VendorDetails", "id", vendorId));
    }

    private Item findItem(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item", "id", itemId));
    }

    private StoreResponse toResponse(Store s) {
        ItemResponse itemResponse = ItemResponse.builder()
                .id(s.getItem().getId())
                .name(s.getItem().getName())
                .price(s.getItem().getPrice())
                .weight(s.getItem().getWeight())
                .description(s.getItem().getDescription())
                .build();

        return StoreResponse.builder()
                .id(s.getId())
                .vendorId(s.getVendor().getId())
                .vendorName(s.getVendor().getAuth().getUsername())
                .item(itemResponse)
                .quantity(s.getQuantity())
                .build();
    }
}
