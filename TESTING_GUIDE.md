# RouteAssign — Postman Testing Guide

> **Base URL:** `http://localhost:8080`  
> **Collection variable:** `{{baseUrl}}` = `http://localhost:8080`  
> **Auth variable:** `{{token}}` — auto-set by the Login request test script  
> **Start the app first**, then run scenarios in order top-to-bottom.

---

## Prerequisites

1. MySQL running, database `routeassign_db` created.
2. Application started (`mvn spring-boot:run`).
3. Postman collection imported (`RouteAssign.postman_collection.json`).

---

## Scenario 1 — Register All Actors

Register one of each role so everything downstream has data to work with.

---

### 1.1 Register a Vendor

**Request:** `POST /api/auth/register` → *Register - Vendor*

```json
{
  "email": "vendor1@example.com",
  "username": "vendor1",
  "password": "password123",
  "role": "VENDOR",
  "mobileNo": "9000000001",
  "latitude": 12.9500,
  "longitude": 77.6100
}
```

**Expected:** `201 Created`

```json
{
  "success": true,
  "message": "Registration successful",
  "data": {
    "token": "eyJ...",
    "tokenType": "Bearer",
    "userId": 1,
    "username": "vendor1",
    "role": "VENDOR"
  }
}
```

**What to check:** `userId` is returned and `role` is `VENDOR`.

---

### 1.2 Register a Delivery Partner

**Request:** `POST /api/auth/register` → *Register - Delivery Partner*

```json
{
  "email": "partner1@example.com",
  "username": "partner1",
  "password": "password123",
  "role": "DELIVERY_PARTNER",
  "mobileNo": "9123456780",
  "latitude": 12.9352,
  "longitude": 77.6245
}
```

**Expected:** `201 Created` with `role: DELIVERY_PARTNER`

> Note the returned `userId`. The delivery partner's `UserDetails.id` will be `1` (first partner profile created).

---

### 1.3 Register a Second Delivery Partner

Same request, different details — needed for tie-breaking scenarios later.

```json
{
  "email": "partner2@example.com",
  "username": "partner2",
  "password": "password123",
  "role": "DELIVERY_PARTNER",
  "mobileNo": "9123456781",
  "latitude": 12.9360,
  "longitude": 77.6250
}
```

---

### 1.4 Register a Customer

**Request:** `POST /api/auth/register` → *Register - Customer*

```json
{
  "email": "customer1@example.com",
  "username": "customer1",
  "password": "password123",
  "role": "CUSTOMER",
  "mobileNo": "9876543210",
  "latitude": 12.9716,
  "longitude": 77.5946
}
```

---

## Scenario 2 — Login and Token

### 2.1 Login as Customer

**Request:** `POST /api/auth/login` → *Login*

```json
{
  "email": "customer1@example.com",
  "password": "password123"
}
```

**Expected:** `200 OK` with a JWT token.

> The Postman test script on this request automatically saves the token to `{{token}}`. All subsequent requests will use it via `Authorization: Bearer {{token}}`.

---

### 2.2 Wrong Password (Negative Test)

```json
{
  "email": "customer1@example.com",
  "password": "wrongpassword"
}
```

**Expected:** `400 Bad Request`

```json
{
  "success": false,
  "status": 400,
  "message": "Invalid email or password."
}
```

---

### 2.3 Non-Existent Email (Negative Test)

```json
{
  "email": "nobody@example.com",
  "password": "password123"
}
```

**Expected:** `404 Not Found`

---

## Scenario 3 — Vendor Profile

### 3.1 Get All Active Vendors

**Request:** `GET /api/vendors` → *Get All Active Vendors*

**Expected:** `200 OK` — list containing `vendor1`.

---

### 3.2 Get Vendor by ID

**Request:** `GET /api/vendors/1` → *Get Vendor by ID*

**Expected:** `200 OK` with vendor latitude/longitude.

---

### 3.3 Update Vendor Location

**Request:** `PATCH /api/vendors/1/location`

```json
{
  "latitude": 12.9510,
  "longitude": 77.6110
}
```

**Expected:** `200 OK` — response shows updated coordinates.

---

## Scenario 4 — Delivery Partner Profile

### 4.1 Get All Active Delivery Partners

**Request:** `GET /api/users/delivery-partners`

**Expected:** `200 OK` — list with `partner1` and `partner2`.

---

### 4.2 Update Partner Location

**Request:** `PATCH /api/users/1/location`

```json
{
  "latitude": 12.9360,
  "longitude": 77.6240
}
```

**Expected:** `200 OK` with updated coordinates.

---

### 4.3 Set Partner Unavailable

**Request:** `PATCH /api/users/1/availability`

```json
{ "isAvailable": false }
```

**Expected:** `200 OK` — `isAvailable: false`

---

### 4.4 Set Partner Available Again

```json
{ "isAvailable": true }
```

**Expected:** `200 OK` — `isAvailable: true`

---

## Scenario 5 — Catalogue Setup (Items + Store)

### 5.1 Create Item A

**Request:** `POST /api/items` → *Create Item*

```json
{
  "name": "Fresh Apples",
  "price": 120.00,
  "weight": 2.5,
  "description": "Fresh red apples, 2.5 kg box"
}
```

**Expected:** `201 Created` — note the returned `id` (Item A = `1`).

---

### 5.2 Create Item B

```json
{
  "name": "Orange Juice",
  "price": 80.00,
  "weight": 1.5,
  "description": "1L orange juice"
}
```

**Expected:** `201 Created` — Item B = `id: 2`.

---

### 5.3 Create Item C (heavy — for capacity testing)

```json
{
  "name": "Rice Bag",
  "price": 350.00,
  "weight": 10.0,
  "description": "10 kg basmati rice"
}
```

**Expected:** `201 Created` — Item C = `id: 3`.

---

### 5.4 Search Items by Name

**Request:** `GET /api/items?name=apple`

**Expected:** `200 OK` — returns list containing `Fresh Apples`.

---

### 5.5 Add Stock for Vendor 1 — Item A

**Request:** `POST /api/vendors/1/store`

```json
{
  "itemId": 1,
  "quantity": 50
}
```

**Expected:** `200 OK` — store entry created.

---

### 5.6 Add Stock for Vendor 1 — Item B

```json
{
  "itemId": 2,
  "quantity": 30
}
```

---

### 5.7 Add Stock for Vendor 1 — Item C

```json
{
  "itemId": 3,
  "quantity": 10
}
```

---

### 5.8 Get All Stock for Vendor 1

**Request:** `GET /api/vendors/1/store`

**Expected:** `200 OK` — 3 entries with correct quantities.

---

### 5.9 Update Stock Quantity

Re-send `POST /api/vendors/1/store` with the same `itemId: 1` but new quantity:

```json
{
  "itemId": 1,
  "quantity": 100
}
```

**Expected:** `200 OK` — quantity updated to `100` (not duplicated).

---

## Scenario 6 — Place Order and Auto-Assignment (Core Feature)

This is the most important scenario. It tests the full assignment pipeline.

---

### 6.1 Place Order — Normal Case

**Request:** `POST /api/orders?customerId=1`

```json
{
  "vendorId": 1,
  "deliveryLocationLatitude": 12.9716,
  "deliveryLocationLongitude": 77.5946,
  "items": [
    { "itemId": 1, "quantity": 2 },
    { "itemId": 2, "quantity": 1 }
  ]
}
```

**What happens internally:**
1. Order created with `totalWeight = (2.5 × 2) + (1.5 × 1) = 6.5 kg`
2. Stock deducted: Item A → 98, Item B → 29
3. Haversine distances calculated for both partners
4. Partner with shorter Home→Vendor + Vendor→Customer distance selected
5. ETA calculated using working-hour rules
6. `DeliveryAssignment` persisted, partner's `currentAssignedWeight` updated

**Expected:** `201 Created`

```json
{
  "success": true,
  "message": "Order placed and partner assigned",
  "data": {
    "id": 1,
    "orderId": 1,
    "deliveryPartnerId": 1,
    "deliveryPartnerName": "partner1",
    "vendorId": 1,
    "vendorName": "vendor1",
    "assignedAt": "2026-09-06T14:30:00",
    "expectedDeliveryTime": "2026-09-06T15:02:00",
    "deliveryStatus": "ASSIGNED",
    "distancePartnerToVendor": 2.41,
    "distanceVendorToCustomer": 4.87,
    "totalDistance": 7.28
  }
}
```

**What to verify:**
- `deliveryStatus` is `ASSIGNED`
- `totalDistance` is the Haversine sum
- `expectedDeliveryTime` is after `assignedAt` and within working hours (10:00–20:00)
- Stock for Item A and Item B has decreased (check via `GET /api/vendors/1/store`)

---

### 6.2 Verify Stock was Deducted

**Request:** `GET /api/vendors/1/store`

**Expected:** Item A quantity = `98`, Item B = `29`.

---

### 6.3 Verify Order Status

**Request:** `GET /api/orders/1`

**Expected:** `orderStatus: ASSIGNED`

---

### 6.4 Verify Partner's Assigned Weight Increased

**Request:** `GET /api/users/1`

**Expected:** `currentAssignedWeight: 6.5`

---

### 6.5 Get Assignment by Order ID

**Request:** `GET /api/assignments/order/1`

**Expected:** Same assignment data as 6.1 response.

---

## Scenario 7 — Same-Vendor Reuse

Place a second order from the same vendor while the first partner is still assigned.

### 7.1 Place Second Order from Same Vendor

**Request:** `POST /api/orders?customerId=1`

```json
{
  "vendorId": 1,
  "deliveryLocationLatitude": 12.9720,
  "deliveryLocationLongitude": 77.5950,
  "items": [
    { "itemId": 2, "quantity": 1 }
  ]
}
```

**What to check:**
- The same partner (`partner1`) should be reused if the extra distance to the new delivery location is ≤ `MAX_ADDITIONAL_DELIVERY_DISTANCE` (10 km — see `application.properties`).
- `currentAssignedWeight` on the partner increases further.

---

## Scenario 8 — Delivery Lifecycle (Status Progression)

Walk the assignment for Order 1 through all statuses.

### 8.1 Start Travel to Vendor

**Request:** `PATCH /api/assignments/1/status?newStatus=EN_ROUTE_TO_VENDOR`

**Expected:** `200 OK` — `deliveryStatus: EN_ROUTE_TO_VENDOR`

---

### 8.2 Collected from Vendor

**Request:** `PATCH /api/assignments/1/status?newStatus=COLLECTED`

**Expected:** `deliveryStatus: COLLECTED`

---

### 8.3 Travelling to Customer

**Request:** `PATCH /api/assignments/1/status?newStatus=EN_ROUTE_TO_CUSTOMER`

**Expected:** `deliveryStatus: EN_ROUTE_TO_CUSTOMER`

---

### 8.4 Mark Delivered

**Request:** `PATCH /api/assignments/1/status?newStatus=DELIVERED`

**Expected:** `200 OK` — `deliveryStatus: DELIVERED`

**What happens internally when DELIVERED:**
- `HistoryDeliveryPartner` record created with `completedAt = now`
- `HistoryVendor` record created
- Partner's `currentAssignedWeight` reduced by order weight
- If no more active assignments, partner's `isAvailable` set back to `true`

---

### 8.5 Verify History Created

**Request:** `GET /api/history/partners/1`

**Expected:** List with one record, `status: COMPLETED`, `completedAt` populated.

---

### 8.6 Verify Vendor History Created

**Request:** `GET /api/history/vendors/1`

**Expected:** List with one record, `status: COMPLETED`.

---

### 8.7 Verify Partner Weight Reduced

**Request:** `GET /api/users/1`

**Expected:** `currentAssignedWeight` decreased (back to `1.5` from the second order, or `0.0` if only one order).

---

### 8.8 Illegal Status Transition (Negative Test)

Try to move the **already DELIVERED** assignment backward:

**Request:** `PATCH /api/assignments/1/status?newStatus=COLLECTED`

**Expected:** `400 Bad Request`

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid status transition for DeliveryAssignment: cannot move from 'DELIVERED' to 'COLLECTED'."
}
```

---

## Scenario 9 — Rating

### 9.1 Rate the Delivery

Get the history record ID first from Scenario 8.5, then:

**Request:** `POST /api/history/partners/records/1/rate`

```json
{ "rating": 4.5 }
```

**Expected:** `200 OK` — history record now has `rating: 4.5`

---

### 9.2 Verify Partner's Aggregated Rating Updated

**Request:** `GET /api/users/1`

**Expected:** `rating: 4.5` (or the running average if rated multiple times)

---

### 9.3 Rate the Vendor

**Request:** `POST /api/history/vendors/records/1/rate`

```json
{ "rating": 4.0 }
```

**Expected:** `200 OK`

---

### 9.4 Rate Again (Negative Test — duplicate rating)

Re-send the same request above.

**Expected:** `400 Bad Request`

```json
{
  "message": "This delivery has already been rated."
}
```

---

### 9.5 Rating Out of Range (Negative Test)

```json
{ "rating": 6.0 }
```

**Expected:** `400 Bad Request` — validation error on `rating` field

```json
{
  "status": 400,
  "message": "Validation failed",
  "fieldErrors": [
    { "field": "rating", "message": "Rating must be at most 5.0" }
  ]
}
```

---

## Scenario 10 — Capacity Limit (Negative Test)

Test that a partner cannot be assigned beyond their max capacity (default 20 kg).

### 10.1 Fill Partner's Capacity

Place orders totalling ≥ 20 kg so the partner is near or at capacity.

**Request:** `POST /api/orders?customerId=1` (place multiple orders with Item C — 10 kg each)

After 2 orders (20 kg assigned), place another:

```json
{
  "vendorId": 1,
  "deliveryLocationLatitude": 12.9716,
  "deliveryLocationLongitude": 77.5946,
  "items": [
    { "itemId": 3, "quantity": 1 }
  ]
}
```

**Expected (if both partners are at capacity):** `422 Unprocessable Entity`

```json
{
  "status": 422,
  "message": "No eligible delivery partner is available to fulfil this order at this time."
}
```

---

## Scenario 11 — Insufficient Stock (Negative Test)

### 11.1 Order More Than Available Stock

```json
{
  "vendorId": 1,
  "deliveryLocationLatitude": 12.9716,
  "deliveryLocationLongitude": 77.5946,
  "items": [
    { "itemId": 1, "quantity": 9999 }
  ]
}
```

**Expected:** `422 Unprocessable Entity`

```json
{
  "status": 422,
  "message": "Insufficient stock for item 'Fresh Apples': requested 9999 but only 98 available."
}
```

---

## Scenario 12 — Cancelled Delivery

### 12.1 Place a New Order

Place any valid order (use customer 1, vendor 1, available items).

---

### 12.2 Cancel the Assignment

**Request:** `PATCH /api/assignments/{id}/status?newStatus=CANCELLED`

**Expected:** `200 OK` — `deliveryStatus: CANCELLED`

**What happens internally:**
- `HistoryDeliveryPartner` created with `status: CANCELLED`
- `HistoryVendor` created with `status: CANCELLED`
- Partner's assigned weight reduced back
- Partner availability restored if no more active assignments

---

### 12.3 Verify History Status is CANCELLED

**Request:** `GET /api/history/partners/{partnerId}`

**Expected:** A record with `status: CANCELLED` and `completedAt` set.

---

## Scenario 13 — Validation Errors

### 13.1 Register with Invalid Email

```json
{
  "email": "not-an-email",
  "username": "user1",
  "password": "password123",
  "role": "CUSTOMER",
  "mobileNo": "9000000000"
}
```

**Expected:** `400 Bad Request`

```json
{
  "status": 400,
  "message": "Validation failed",
  "fieldErrors": [
    { "field": "email", "message": "Email must be valid" }
  ]
}
```

---

### 13.2 Register with Short Password

```json
{
  "email": "test@example.com",
  "username": "testuser",
  "password": "short",
  "role": "CUSTOMER",
  "mobileNo": "9000000000"
}
```

**Expected:** `400 Bad Request` — `fieldErrors[0].field = "password"`

---

### 13.3 Place Order with Empty Items List

```json
{
  "vendorId": 1,
  "deliveryLocationLatitude": 12.9716,
  "deliveryLocationLongitude": 77.5946,
  "items": []
}
```

**Expected:** `400 Bad Request` — `fieldErrors[0].field = "items"`

---

### 13.4 Place Order for Inactive Vendor

First deactivate vendor 1: `DELETE /api/vendors/1`

Then try placing an order for vendorId 1.

**Expected:** `400 Bad Request` — `"Vendor id=1 is not active."`

---

## Scenario 14 — ETA and Working-Hour Rules

These scenarios verify the delivery time algorithm. Run them by changing your system time or observing the `expectedDeliveryTime` field carefully.

### 14.1 Assignment Before 5 PM

Place an order when system time is before 17:00. The `expectedDeliveryTime` should be on the **same day** within 10:00–20:00.

**Check:** `expectedDeliveryTime` date == `assignedAt` date, time between 10:00 and 20:00.

---

### 14.2 Assignment After 5 PM, Partner Close to Vendor (≤ 30 km)

Partner home is at most 30 km from vendor. Assignment placed after 17:00.

**Check:** `expectedDeliveryTime` should still be the same day if enough working hours remain, or next day at 10:00 if not.

---

### 14.3 Assignment After 5 PM, Partner Far from Vendor (> 30 km)

Set partner location far from vendor (> 30 km Haversine distance). Place order after 17:00.

Update partner1 location to somewhere far:

**Request:** `PATCH /api/users/1/location`

```json
{
  "latitude": 13.2000,
  "longitude": 77.7000
}
```

Then place an order after 5 PM.

**Check:** `expectedDeliveryTime` should be **next day starting at 10:00**.

---

## Scenario 15 — Soft Delete / Deactivate

### 15.1 Deactivate a Delivery Partner

**Request:** `DELETE /api/users/2`

**Expected:** `200 OK` — `"User deactivated"`

---

### 15.2 Verify Deactivated Partner Not Assigned

Place a new order. The deactivated `partner2` must not appear in assignments.

**Expected:** `partner1` is assigned (or `NoEligiblePartnerException` if also unavailable).

---

### 15.3 Deactivated User Cannot Login

```json
{
  "email": "partner2@example.com",
  "password": "password123"
}
```

**Expected:** `400 Bad Request` — `"This account has been deactivated."`

---

## Quick Reference — Endpoint Summary

| Scenario | Method | Endpoint |
|---|---|---|
| Register | POST | `/api/auth/register` |
| Login | POST | `/api/auth/login` |
| Get all vendors | GET | `/api/vendors` |
| Update vendor location | PATCH | `/api/vendors/{id}/location` |
| Get all partners | GET | `/api/users/delivery-partners` |
| Toggle availability | PATCH | `/api/users/{id}/availability` |
| Create item | POST | `/api/items` |
| Search items | GET | `/api/items?name=...` |
| Add stock | POST | `/api/vendors/{id}/store` |
| Get vendor stock | GET | `/api/vendors/{id}/store` |
| **Place order** | **POST** | **`/api/orders?customerId={id}`** |
| Get order | GET | `/api/orders/{id}` |
| Get assignment | GET | `/api/assignments/order/{orderId}` |
| Advance status | PATCH | `/api/assignments/{id}/status?newStatus=...` |
| Partner history | GET | `/api/history/partners/{partnerId}` |
| Rate delivery | POST | `/api/history/partners/records/{id}/rate` |
| Rate vendor | POST | `/api/history/vendors/records/{id}/rate` |

---

## Recommended Test Run Order

```
Scenario 1  → Register all actors
Scenario 2  → Login (sets {{token}})
Scenario 3  → Vendor profile checks
Scenario 4  → Partner profile + availability toggle
Scenario 5  → Create items + stock setup
Scenario 6  → Place order → CORE FEATURE (auto-assignment)
Scenario 7  → Same-vendor reuse
Scenario 8  → Full delivery lifecycle → DELIVERED
Scenario 9  → Rating (partner + vendor)
Scenario 10 → Capacity limit (negative)
Scenario 11 → Insufficient stock (negative)
Scenario 12 → Cancelled delivery
Scenario 13 → Validation errors (negative)
Scenario 14 → ETA / working-hour rules
Scenario 15 → Deactivation
```
